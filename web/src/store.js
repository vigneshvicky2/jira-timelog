import { create } from 'zustand'
import { createDemoJira, demoMeetings } from './lib/demo.js'
import { loadCalendars, meetingsBetween } from './lib/ics.js'
import { createJira } from './lib/jira.js'
import { TARGET, addDays, addMonths, fmt, isWeekend, monthStart, todayIso } from './lib/time.js'

const LS = {
  get(k, d) {
    try {
      const v = localStorage.getItem(k)
      return v ? JSON.parse(v) : d
    } catch {
      return d
    }
  },
  set(k, v) {
    try {
      localStorage.setItem(k, JSON.stringify(v))
    } catch {
      /* storage unavailable */
    }
  },
  del(k) {
    try {
      localStorage.removeItem(k)
    } catch {
      /* ignore */
    }
  },
}

export const DEMO = new URLSearchParams(location.search).has('demo')
const P = DEMO ? 'tl.demo.' : 'tl.'
const DEFAULT_SETTINGS = { icsUrls: [], reminder: { enabled: false, time: '18:00', weekdaysOnly: true } }

let api = null
let calendars = null
const detailsCache = new Map()
let nextId = Date.now()
const norm = (t) => t.toLowerCase().trim().replace(/\s+/g, ' ')

// ---------- derived helpers (pure) ----------
export const loggedOn = (s, d) => (s.worklogs[d] || []).reduce((a, w) => a + w.seconds, 0)
export const pendingOn = (s, d) => s.drafts.reduce((a, x) => (x.date === d ? a + x.seconds : a), 0)
export const remainingOn = (s, d) => TARGET - loggedOn(s, d) - pendingOn(s, d)
export function isLeaveDay(s, d) {
  const key = s.timeOff?.key
  if (key && s.drafts.some((x) => x.date === d && x.issue.key === key)) return true
  return (s.worklogs[d] || []).some((w) => w.key === key || /time\s*off/i.test(w.summary))
}
export const ticketDraft = (s, d, key) => s.drafts.find((x) => x.date === d && x.issue.key === key && !x.meetingKey)

export const useStore = create((set, get) => ({
  demo: DEMO,
  config: DEMO ? { site: 'demo.atlassian.net', email: 'alex@example.com', token: 'demo' } : LS.get('tl.config', null),
  me: DEMO ? null : LS.get('tl.me', null),
  settings: { ...DEFAULT_SETTINGS, ...LS.get(P + 'settings', {}) },
  today: todayIso(),
  month: monthStart(todayIso()),
  selected: [todayIso()],
  anchor: todayIso(),
  worklogs: {},
  loadedMonths: {},
  loading: false,
  sprintIssues: [],
  sprintName: null,
  timeOff: null,
  pinned: LS.get(P + 'pinned', []),
  drafts: LS.get(P + 'drafts', []),
  meetingMemory: LS.get(P + 'meetingMemory', {}),
  meetings: {},
  meetingRange: null,
  calendarState: 'none',
  calendarError: null,
  submitting: false,
  progress: 0,
  toasts: [],
  celebrate: 0,
  peek: null,

  // ---------- session ----------
  async init() {
    const { config } = get()
    if (!config) return
    api = DEMO ? createDemoJira() : createJira(config)
    if (!get().me) {
      try {
        const me = await api.myself()
        set({ me })
        if (!DEMO) LS.set('tl.me', me)
      } catch (e) {
        get().toast(e.message, 'error')
        return
      }
    }
    get().refresh()
    get().loadCalendar()
  },

  async connect(site, email, token) {
    site = site.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '').toLowerCase()
    email = email.trim()
    token = token.trim()
    if (!site || !email) throw new Error('Enter your Jira site and email.')
    if (!token) throw new Error('Paste your Jira API key.')
    const a = createJira({ site, email, token })
    const me = await a.myself()
    const config = { site, email, token }
    LS.set('tl.config', config)
    LS.set('tl.me', me)
    api = a
    set({ config, me })
    get().refresh()
    get().loadCalendar()
  },

  disconnect() {
    LS.del('tl.config')
    LS.del('tl.me')
    api = null
    set({ config: null, me: null, worklogs: {}, loadedMonths: {}, sprintIssues: [], sprintName: null, timeOff: null })
  },

  // ---------- loading ----------
  async refresh() {
    set({ loadedMonths: {} })
    await Promise.all([get().loadSprint(), get().ensureMonths(true)])
  },

  async loadSprint() {
    if (!api) return
    try {
      const issues = await api.sprintIssues()
      const names = [...new Set(issues.map((i) => i.sprint).filter(Boolean))]
      set({ sprintIssues: issues, sprintName: names.join(' · ') || null })
      const projects = [...new Set(issues.map((i) => i.key.split('-')[0]))]
      set({ timeOff: await api.timeOff(projects) })
    } catch (e) {
      get().toast(`Couldn't load sprint tickets: ${e.message}`, 'error')
    }
  },

  /** Loads worklogs for the visible month and the months of all selected days. */
  async ensureMonths(force = false) {
    const { month, selected, loadedMonths, me } = get()
    if (!api || !me) return
    const months = [...new Set([month, ...selected.map(monthStart)])].filter((m) => force || !loadedMonths[m])
    if (!months.length) return
    set((s) => ({ loading: true, loadedMonths: { ...s.loadedMonths, ...Object.fromEntries(months.map((m) => [m, true])) } }))
    try {
      for (const m of months) {
        const from = m
        const to = addDays(addMonths(m, 1), -1)
        const logs = await api.worklogs(me.accountId, from, to)
        set((s) => {
          const w = { ...s.worklogs }
          for (let d = from; d <= to; d = addDays(d, 1)) delete w[d]
          for (const l of logs) (w[l.date] ||= []).push(l)
          for (let d = from; d <= to; d = addDays(d, 1)) w[d]?.sort((a, b) => a.start.localeCompare(b.start))
          return { worklogs: w }
        })
      }
    } catch (e) {
      get().toast(e.message, 'error')
      set((s) => ({ loadedMonths: Object.fromEntries(Object.entries(s.loadedMonths).filter(([k]) => !months.includes(k))) }))
    } finally {
      set({ loading: false })
    }
  },

  async loadCalendar() {
    if (DEMO) {
      set({ calendarState: 'ok', meetingRange: null })
      get().computeMeetings()
      return
    }
    const urls = get().settings.icsUrls.filter((u) => u.trim())
    if (!urls.length) {
      calendars = null
      set({ calendarState: 'none', meetings: {}, meetingRange: null })
      return
    }
    set({ calendarState: 'loading', calendarError: null })
    try {
      calendars = await loadCalendars(urls)
      set({ calendarState: 'ok', meetingRange: null })
      get().computeMeetings()
    } catch (e) {
      calendars = null
      set({ calendarState: 'error', calendarError: e.message, meetings: {} })
    }
  },

  /** Expands meetings for the visible month and selection (cached by range). */
  computeMeetings() {
    const { month, selected, config, meetingRange, calendarState } = get()
    if (calendarState !== 'ok') return
    const dates = [...selected, month, addDays(addMonths(month, 1), -1)].sort()
    const need = { from: dates[0], to: dates[dates.length - 1] }
    if (meetingRange && need.from >= meetingRange.from && need.to <= meetingRange.to) return
    const from = addDays(need.from, -14)
    const to = addDays(need.to, 14)
    const meetings = DEMO ? demoMeetings(from, to) : calendars ? meetingsBetween(calendars, from, to, config?.email) : {}
    set({ meetings, meetingRange: { from, to } })
  },

  // ---------- selection ----------
  afterSelect() {
    get().ensureMonths()
    get().computeMeetings()
  },
  setMonth(m) {
    set({ month: m })
    get().afterSelect()
  },
  clickDay(d, { shift = false } = {}) {
    const { selected, anchor } = get()
    let next
    if (shift && anchor) {
      const [a, b] = anchor < d ? [anchor, d] : [d, anchor]
      const range = []
      for (let x = a; x <= b; x = addDays(x, 1)) range.push(x)
      next = [...new Set([...selected, ...range])]
    } else if (selected.includes(d)) {
      next = selected.length > 1 ? selected.filter((x) => x !== d) : selected
    } else {
      next = [...selected, d]
    }
    set({ selected: next.sort(), anchor: d })
    get().afterSelect()
  },
  selectOnly(d) {
    set({ selected: [d], anchor: d, month: monthStart(d) })
    get().afterSelect()
  },
  selectPreset(kind) {
    const { today } = get()
    let days = [today]
    if (kind === 'last3') {
      days = []
      for (let d = today; days.length < 3; d = addDays(d, -1)) if (!isWeekend(d)) days.push(d)
    } else if (kind === 'week' || kind === 'lastweek') {
      const offset = (new Date(today + 'T00:00').getDay() + 6) % 7
      let monday = addDays(today, -offset)
      if (kind === 'lastweek') monday = addDays(monday, -7)
      days = [0, 1, 2, 3, 4].map((i) => addDays(monday, i)).filter((d) => d <= today)
    }
    set({ selected: days.sort(), anchor: days[days.length - 1], month: monthStart(days[days.length - 1]) })
    get().afterSelect()
  },

  // ---------- drafts ----------
  setCell(date, issue, seconds, comment) {
    set((s) => {
      const drafts = [...s.drafts]
      const i = drafts.findIndex((x) => x.date === date && x.issue.key === issue.key && !x.meetingKey)
      if (!seconds || seconds <= 0) {
        if (i >= 0) drafts.splice(i, 1)
      } else if (i >= 0) {
        drafts[i] = { ...drafts[i], seconds, comment: comment ?? drafts[i].comment, error: null }
      } else {
        drafts.push({
          id: nextId++,
          date,
          issue: { key: issue.key, summary: issue.summary },
          seconds,
          start: '10:00',
          comment: comment || '',
          meetingKey: null,
        })
      }
      return { drafts }
    })
  },
  setCellComment(date, key, comment) {
    set((s) => ({ drafts: s.drafts.map((x) => (x.date === date && x.issue.key === key && !x.meetingKey ? { ...x, comment } : x)) }))
  },
  /** Copies one row's value on [date] to every other selected day. */
  copyCellToSelected(date, key) {
    const s = get()
    const src = ticketDraft(s, date, key)
    if (!src) return
    for (const d of s.selected) if (d !== date) get().setCell(d, src.issue, src.seconds, src.comment)
    get().toast(`Copied ${fmt(src.seconds)} on ${key} to ${s.selected.length - 1} more day${s.selected.length > 2 ? 's' : ''}`)
  },
  /** Replaces every other selected day's ticket entries with this day's. */
  copyDayToSelected(date) {
    const s = get()
    const src = s.drafts.filter((x) => x.date === date && !x.meetingKey)
    const targets = s.selected.filter((d) => d !== date)
    set((st) => ({
      drafts: [
        ...st.drafts.filter((x) => !(targets.includes(x.date) && !x.meetingKey)),
        ...targets.flatMap((d) => src.map((x) => ({ ...x, id: nextId++, date: d, error: null }))),
      ],
    }))
    get().toast(`Copied ${src.length} ticket${src.length === 1 ? '' : 's'} to ${targets.length} more day${targets.length === 1 ? '' : 's'}`)
  },
  setLeave(days, seconds, comment) {
    const t = get().timeOff
    if (!t) return get().toast("Couldn't find this sprint's Time off ticket.", 'error')
    for (const d of days) get().setCell(d, t, seconds, comment)
  },
  setMeeting(m, issue, seconds, comment) {
    set((s) => ({
      drafts: [
        ...s.drafts.filter((x) => x.meetingKey !== m.key),
        { id: nextId++, date: m.date, issue: { key: issue.key, summary: issue.summary }, seconds, start: m.start, comment, meetingKey: m.key },
      ],
      meetingMemory: { ...s.meetingMemory, [norm(m.title)]: { key: issue.key, summary: issue.summary } },
    }))
  },
  removeMeeting(m) {
    set((s) => ({ drafts: s.drafts.filter((x) => x.meetingKey !== m.key) }))
  },
  lastTicketFor: (title) => get().meetingMemory[norm(title)] || null,
  removeDraft(id) {
    set((s) => ({ drafts: s.drafts.filter((x) => x.id !== id) }))
  },
  clearDrafts() {
    set({ drafts: [] })
  },
  pin(issue) {
    set((s) => (s.pinned.some((p) => p.key === issue.key) ? s : { pinned: [...s.pinned, { key: issue.key, summary: issue.summary }] }))
  },
  unpin(key) {
    set((s) => ({ pinned: s.pinned.filter((p) => p.key !== key) }))
  },
  searchAll: (q) => (api ? api.searchAll(q) : Promise.resolve([])),

  // ---------- submit ----------
  async submitAll() {
    const { drafts, submitting } = get()
    if (!api || submitting || !drafts.length) return
    set({ submitting: true, progress: 0 })
    const batch = [...drafts].sort((a, b) => (a.date + a.start).localeCompare(b.date + b.start))
    let ok = 0
    let total = 0
    for (let i = 0; i < batch.length; i++) {
      const d = batch[i]
      try {
        await api.addWorklog(d)
        ok++
        total += d.seconds
        set((s) => ({ drafts: s.drafts.filter((x) => x.id !== d.id) }))
      } catch (e) {
        set((s) => ({ drafts: s.drafts.map((x) => (x.id === d.id ? { ...x, error: e.message } : x)) }))
      }
      set({ progress: (i + 1) / batch.length })
    }
    set({ submitting: false })
    if (ok === batch.length) {
      set((s) => ({ celebrate: s.celebrate + 1 }))
      get().toast(`Logged ${ok} entr${ok === 1 ? 'y' : 'ies'} · ${fmt(total)}`, 'success')
    } else {
      get().toast(`${ok} of ${batch.length} logged. Check the ones marked in red.`, 'error')
    }
    const touched = new Set(batch.map((d) => monthStart(d.date)))
    set((s) => ({ loadedMonths: Object.fromEntries(Object.entries(s.loadedMonths).filter(([m]) => !touched.has(m))) }))
    get().ensureMonths()
  },

  async deleteWorklog(w) {
    try {
      await api.deleteWorklog(w.key, w.id)
      set((s) => ({ worklogs: { ...s.worklogs, [w.date]: (s.worklogs[w.date] || []).filter((x) => x.id !== w.id) } }))
      get().toast(`Removed ${fmt(w.seconds)} from ${w.key}`)
    } catch (e) {
      get().toast(e.message, 'error')
    }
  },

  // ---------- ticket preview ----------
  openPeek(issue) {
    set({ peek: { key: issue.key, summary: issue.summary } })
  },
  closePeek() {
    set({ peek: null })
  },
  async issueDetails(key) {
    if (detailsCache.has(key)) return detailsCache.get(key)
    if (!api) throw new Error('Not connected')
    const d = await api.issueDetails(key)
    detailsCache.set(key, d)
    return d
  },

  // ---------- settings / toasts ----------
  saveSettings(patch) {
    set((s) => ({ settings: { ...s.settings, ...patch } }))
  },
  toast(text, kind = 'info') {
    const id = nextId++
    set((s) => ({ toasts: [...s.toasts.slice(-3), { id, text, kind }] }))
    setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), kind === 'error' ? 6000 : 3800)
  },
}))

// persist what should survive a reload
useStore.subscribe((s, prev) => {
  if (s.drafts !== prev.drafts) LS.set(P + 'drafts', s.drafts.map(({ error, ...d }) => ({ ...d, error: error || null })))
  if (s.pinned !== prev.pinned) LS.set(P + 'pinned', s.pinned)
  if (s.meetingMemory !== prev.meetingMemory) LS.set(P + 'meetingMemory', s.meetingMemory)
  if (s.settings !== prev.settings) LS.set(P + 'settings', s.settings)
})

export const lastFiredKey = P + 'reminderLastFired'
export { LS }
