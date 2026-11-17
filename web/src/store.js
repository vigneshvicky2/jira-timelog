import { create } from 'zustand'
import { createDemoJira, demoMeetings } from './lib/demo.js'
import { loadCalendars, meetingsBetween } from './lib/ics.js'
import { createJira, friendlyError } from './lib/jira.js'
import { progressOf } from './lib/progress.js'
import { TARGET, addDays, addMonths, fmt, fromIso, isWeekend, monthStart, todayIso } from './lib/time.js'

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
// ?demo&newsprint replays the new-sprint celebration
if (DEMO && new URLSearchParams(location.search).has('newsprint')) LS.set(P + 'seenSprint', 'Sprint 41 · Payments polish')
const DEFAULT_SETTINGS = { icsUrls: [], reminder: { enabled: false, time: '18:00', weekdaysOnly: true } }

let api = null
let calendars = null
const detailsCache = new Map()
/** Worklogs removed in the UI whose Jira delete is waiting out the Undo window. */
const pendingDeletes = new Map()
let nextId = Date.now()
const norm = (t) => t.toLowerCase().trim().replace(/\s+/g, ' ')
const STEP = 15 * 60
const byStart = (a, b) => a.start.localeCompare(b.start)

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
const isLeaveLog = (s, w) => w.key === s.timeOff?.key || /time\s*off/i.test(w.summary || '')

/**
 * What "Copy last workday" would add on day [d]: the tickets logged on the most recent earlier
 * workday that has (non-leave) worklogs, minus tickets that already have time or drafts on [d].
 */
export function lastWorkdayPlan(s, d) {
  if (isLeaveDay(s, d)) return null
  for (let i = 1; i <= 31; i++) {
    const from = addDays(d, -i)
    if (isWeekend(from)) continue
    const logs = (s.worklogs[from] || []).filter((w) => !isLeaveLog(s, w))
    if (!logs.length) continue
    const byKey = new Map()
    for (const w of logs) {
      const e = byKey.get(w.key) || { key: w.key, summary: w.summary, seconds: 0 }
      e.seconds += w.seconds
      byKey.set(w.key, e)
    }
    const have = new Set([...(s.worklogs[d] || []).map((w) => w.key), ...s.drafts.filter((x) => x.date === d).map((x) => x.issue.key)])
    const all = [...byKey.values()]
    return {
      from,
      total: all.reduce((a, e) => a + e.seconds, 0),
      count: all.length,
      items: all.filter((e) => !have.has(e.key)),
    }
  }
  return null
}

/** "Yesterday" / "On Friday" / "On 3 Nov" for the source day of a copy. */
export function dayPhrase(from, d) {
  const diff = Math.round((fromIso(d) - fromIso(from)) / 86400000)
  if (diff === 1 && d === todayIso()) return 'Yesterday'
  if (diff < 7) return 'On ' + fromIso(from).toLocaleDateString(undefined, { weekday: 'long' })
  return 'On ' + fromIso(from).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

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
  /** The active sprint with its dates (the one ending soonest when there are several). */
  sprint: null,
  /** { from, to } when a new sprint started since the last visit; cleared once celebrated. */
  sprintChange: null,
  /** False while the launch splash plays, so the sprint celebration waits for it. */
  splashDone: false,
  timeOff: null,
  pinned: LS.get(P + 'pinned', []),
  drafts: LS.get(P + 'drafts', []),
  meetingMemory: LS.get(P + 'meetingMemory', {}),
  meetingSkip: LS.get(P + 'meetingSkip', {}),
  meetings: {},
  meetingRange: null,
  calendarState: 'none',
  calendarError: null,
  submitting: false,
  progress: 0,
  submitDone: 0,
  submitTotal: 0,
  retrying: {},
  shortcuts: false,
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
        get().toast(friendlyError(e), 'error')
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
    let me
    try {
      me = await a.myself()
    } catch (e) {
      if (e.status === 401 || e.status === 403) throw new Error("That API key didn't work. Check your email and key.")
      if (e.status === 404) throw new Error("Couldn't find that Jira site. Check the address.")
      throw new Error(friendlyError(e))
    }
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
    detailsCache.clear()
    await Promise.all([get().loadSprint(), get().ensureMonths(true)])
  },

  async loadSprint() {
    if (!api) return
    try {
      const issues = await api.sprintIssues()
      const names = [...new Set(issues.map((i) => i.sprint).filter(Boolean))]
      const infos = [...new Map(issues.filter((i) => i.sprintInfo).map((i) => [i.sprintInfo.id, i.sprintInfo])).values()]
      const sprint = infos.sort((a, b) => (a.end ? Date.parse(a.end) : Infinity) - (b.end ? Date.parse(b.end) : Infinity))[0] || null
      set({ sprintIssues: issues, sprintName: names.join(' · ') || null, sprint })
      get().noteSprint(sprint?.name)
      const projects = [...new Set(issues.map((i) => i.key.split('-')[0]))]
      set({ timeOff: await api.timeOff(projects) })
    } catch (e) {
      get().toast(`Couldn't load sprint tickets. ${friendlyError(e)}`, 'error')
    }
  },

  /** Loads worklogs for the visible month and the months of all selected days. */
  async ensureMonths(force = false, extra = []) {
    const { month, selected, loadedMonths, me } = get()
    if (!api || !me) return
    const months = [...new Set([month, ...selected.map(monthStart), ...extra.map(monthStart)])].filter((m) => force || !loadedMonths[m])
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
          for (const l of logs) if (!pendingDeletes.has(l.id)) (w[l.date] ||= []).push(l)
          for (let d = from; d <= to; d = addDays(d, 1)) w[d]?.sort((a, b) => a.start.localeCompare(b.start))
          return { worklogs: w }
        })
      }
    } catch (e) {
      get().toast(friendlyError(e), 'error')
      set((s) => ({ loadedMonths: Object.fromEntries(Object.entries(s.loadedMonths).filter(([k]) => !months.includes(k))) }))
    } finally {
      set({ loading: false })
    }
    get().applyAlwaysMeetings()
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
    get().applyAlwaysMeetings()
  },

  /**
   * Meetings marked "Always log to <KEY>" become drafts on the shown days (up to today) once
   * that day's worklogs are loaded, unless already in Jira, already drafted or removed by hand.
   */
  applyAlwaysMeetings() {
    const s = get()
    if (s.loading || s.calendarState !== 'ok') return
    const add = []
    for (const d of s.selected) {
      if (d > s.today || isWeekend(d) || !s.loadedMonths[monthStart(d)] || isLeaveDay(s, d)) continue
      for (const m of s.meetings[d] || []) {
        const mem = s.meetingMemory[norm(m.title)]
        if (!mem?.always || s.meetingSkip[m.key] || s.drafts.some((x) => x.meetingKey === m.key)) continue
        if ((s.worklogs[d] || []).some((w) => w.key === mem.key && w.start === m.start)) continue
        add.push({
          id: nextId++, date: d, issue: { key: mem.key, summary: mem.summary }, seconds: mem.seconds || m.minutes * 60,
          start: m.start, comment: m.title, meetingKey: m.key, auto: true,
        })
      }
    }
    if (!add.length) return
    set((st) => ({ drafts: [...st.drafts, ...add] }))
    get().toast(`Added ${add.length} recurring meeting${add.length === 1 ? ' as a draft' : 's as drafts'}`)
  },

  // ---------- selection ----------
  afterSelect() {
    get().ensureMonths()
    get().computeMeetings()
    get().applyAlwaysMeetings()
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
  setMeeting(m, issue, seconds, comment, always) {
    set((s) => {
      const { [m.key]: _, ...meetingSkip } = s.meetingSkip
      const prev = s.meetingMemory[norm(m.title)]
      return {
        drafts: [
          ...s.drafts.filter((x) => x.meetingKey !== m.key),
          { id: nextId++, date: m.date, issue: { key: issue.key, summary: issue.summary }, seconds, start: m.start, comment, meetingKey: m.key },
        ],
        meetingMemory: {
          ...s.meetingMemory,
          [norm(m.title)]: { key: issue.key, summary: issue.summary, always: always ?? prev?.always ?? false, seconds },
        },
        meetingSkip,
      }
    })
    if (always) get().applyAlwaysMeetings()
  },
  removeMeeting(m) {
    set((s) => ({ drafts: s.drafts.filter((x) => x.meetingKey !== m.key), meetingSkip: { ...s.meetingSkip, [m.key]: true } }))
  },
  lastTicketFor: (title) => get().meetingMemory[norm(title)] || null,
  removeDraft(id) {
    set((s) => {
      const d = s.drafts.find((x) => x.id === id)
      return {
        drafts: s.drafts.filter((x) => x.id !== id),
        ...(d?.meetingKey ? { meetingSkip: { ...s.meetingSkip, [d.meetingKey]: true } } : {}),
      }
    })
  },

  // ---------- smart logging (only ever creates drafts) ----------
  /** Repeats the most recent earlier workday's tickets on each of [days]. */
  async copyLastWorkday(days) {
    await get().ensureMonths(false, days.flatMap((d) => [d, addDays(d, -31)]))
    let n = 0
    let secs = 0
    let from = null
    for (const d of days) {
      const plan = lastWorkdayPlan(get(), d)
      if (!plan) continue
      from ||= plan.from
      for (const e of plan.items) {
        get().setCell(d, e, e.seconds, '')
        n++
        secs += e.seconds
      }
    }
    if (!n) return get().toast(from ? 'Nothing new to copy: those tickets already have time on that day.' : 'No earlier workday with logged time to copy.')
    const src = days.length === 1 ? ` from ${fromIso(from).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}` : ''
    get().toast(`Added ${n} draft${n === 1 ? '' : 's'} · ${fmt(secs)}${src}. Review, then Log all.`, 'success')
  },

  /**
   * Splits what's left of day [d] (up to 8h) across in-progress sprint tickets in 15-minute steps.
   * Tickets with estimate time left are filled first; To Do, Done and over-estimate tickets are never used.
   */
  fillRest(d) {
    const s = get()
    let units = Math.floor(remainingOn(s, d) / STEP)
    if (units <= 0) return get().toast('That day already has 8h.')
    const pendingBy = (key) => s.drafts.reduce((a, x) => (x.issue.key === key ? a + x.seconds : a), 0)
    const cands = s.sprintIssues
      .filter((i) => i.statusCategory === 'indeterminate' && i.key !== s.timeOff?.key)
      .map((i) => {
        const p = progressOf(i.tracking, i.statusCategory, pendingBy(i.key))
        const cap = p.over > 0 || p.afterOver > 0 ? 0 : p.afterRemaining != null ? Math.floor(p.afterRemaining / STEP) : Infinity
        return { issue: i, cap, units: 0 }
      })
      .filter((c) => c.cap > 0)
      .sort((a, b) => (a.cap === Infinity) - (b.cap === Infinity) || b.cap - a.cap)
    if (!cands.length) return get().toast('No in-progress tickets with time left on their estimate to fill with.')
    for (const group of [cands.filter((c) => c.cap !== Infinity), cands.filter((c) => c.cap === Infinity)]) {
      let progressed = true
      while (units > 0 && progressed) {
        progressed = false
        for (const c of group) {
          if (units > 0 && c.units < c.cap) {
            c.units++
            units--
            progressed = true
          }
        }
      }
    }
    const used = cands.filter((c) => c.units > 0)
    for (const c of used) {
      const existing = ticketDraft(get(), d, c.issue.key)
      get().setCell(d, c.issue, (existing?.seconds || 0) + c.units * STEP)
    }
    const total = used.reduce((a, c) => a + c.units * STEP, 0)
    get().toast(`Split ${fmt(total)} across ${used.length} ticket${used.length === 1 ? '' : 's'}. Review, then Log all.`, 'success')
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
    const batch = [...drafts].sort((a, b) => (a.date + a.start).localeCompare(b.date + b.start))
    set({ submitting: true, progress: 0, submitDone: 0, submitTotal: batch.length })
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
        set((s) => ({ drafts: s.drafts.map((x) => (x.id === d.id ? { ...x, error: friendlyError(e) } : x)) }))
      }
      set({ progress: (i + 1) / batch.length, submitDone: i + 1 })
    }
    set({ submitting: false })
    get().afterLogged(batch.filter((d) => !get().drafts.some((x) => x.id === d.id)))
    if (ok === batch.length) {
      set((s) => ({ celebrate: s.celebrate + 1 }))
      get().toast(`Logged ${ok} entr${ok === 1 ? 'y' : 'ies'} · ${fmt(total)}`, 'success')
    } else {
      get().toast(`${ok} of ${batch.length} logged. Retry the ones marked in red.`, 'error')
    }
  },

  /** Sends one draft again (the Retry button on a failed entry). */
  async submitOne(id) {
    const d = get().drafts.find((x) => x.id === id)
    if (!api || !d || get().retrying[id] || get().submitting) return
    set((s) => ({ retrying: { ...s.retrying, [id]: true } }))
    try {
      await api.addWorklog(d)
      set((s) => ({ drafts: s.drafts.filter((x) => x.id !== id) }))
      get().afterLogged([d])
      get().toast(`Logged ${fmt(d.seconds)} on ${d.issue.key}`, 'success')
      if (!get().drafts.length) set((s) => ({ celebrate: s.celebrate + 1 }))
    } catch (e) {
      set((s) => ({ drafts: s.drafts.map((x) => (x.id === id ? { ...x, error: friendlyError(e) } : x)) }))
    } finally {
      set((s) => {
        const { [id]: _, ...retrying } = s.retrying
        return { retrying }
      })
    }
  },

  /** Refreshes estimates and the touched months after entries reach Jira. */
  afterLogged(sent) {
    if (!sent.length) return
    detailsCache.clear()
    get().loadSprint()
    const touched = new Set(sent.map((d) => monthStart(d.date)))
    set((s) => ({ loadedMonths: Object.fromEntries(Object.entries(s.loadedMonths).filter(([m]) => !touched.has(m))) }))
    get().ensureMonths()
  },

  /**
   * Removes a Jira worklog from the UI at once and offers Undo for 5 seconds; only then is
   * it deleted in Jira. A failed delete puts the entry back.
   */
  deleteWorklog(w) {
    const remove = (s) => ({ worklogs: { ...s.worklogs, [w.date]: (s.worklogs[w.date] || []).filter((x) => x.id !== w.id) } })
    const restore = () =>
      set((s) => ({ worklogs: { ...s.worklogs, [w.date]: [...(s.worklogs[w.date] || []).filter((x) => x.id !== w.id), w].sort(byStart) } }))
    const commit = async () => {
      if (!pendingDeletes.has(w.id)) return
      pendingDeletes.delete(w.id)
      try {
        await api.deleteWorklog(w.key, w.id)
        detailsCache.delete(w.key)
        get().loadSprint()
      } catch (e) {
        restore()
        get().toast(`Couldn't delete ${fmt(w.seconds)} on ${w.key}. ${friendlyError(e)}`, 'error')
      }
    }
    set(remove)
    const timer = setTimeout(commit, 5000)
    pendingDeletes.set(w.id, { timer, commit })
    get().toast(`Deleted ${fmt(w.seconds)} from ${w.key}`, 'info', {
      duration: 5000,
      action: {
        label: 'Undo',
        run: () => {
          clearTimeout(timer)
          pendingDeletes.delete(w.id)
          restore()
        },
      },
    })
  },

  /** Optimistically moves a ticket to In Progress; rolls back with an error toast on failure. */
  async startProgress(issue) {
    const key = issue.key
    const before = { status: issue.status, statusCategory: issue.statusCategory }
    const patchAll = (patch) => {
      set((s) => ({ sprintIssues: s.sprintIssues.map((i) => (i.key === key ? { ...i, ...patch } : i)) }))
      if (detailsCache.has(key)) detailsCache.set(key, { ...detailsCache.get(key), ...patch })
    }
    patchAll({ status: 'In Progress', statusCategory: 'indeterminate' })
    try {
      const r = await api.startProgress(key)
      patchAll(r)
      get().toast(`${key} is now ${r.status}`, 'success')
      return r
    } catch (e) {
      patchAll(before)
      get().toast(`Couldn't move ${key} to In Progress. ${friendlyError(e)}`, 'error')
      throw e
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
  /** Shows a toast; `action: { label, run }` adds a button (e.g. Undo) for `duration` ms. */
  toast(text, kind = 'info', { action = null, duration } = {}) {
    const id = nextId++
    const ms = duration ?? (kind === 'error' ? 6000 : 3800)
    set((s) => ({ toasts: [...s.toasts.slice(-3), { id, text, kind, action, duration: ms }] }))
    setTimeout(() => get().dismissToast(id), ms)
    return id
  },
  dismissToast(id) {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
  },
  /** Remembers the sprint; if it differs from the last one seen, queues the celebration once. */
  noteSprint(name) {
    if (!name) return
    const seen = LS.get(P + 'seenSprint', null)
    if (seen && seen !== name) set({ sprintChange: { from: seen, to: name } })
    LS.set(P + 'seenSprint', name)
  },
  dismissSprintChange() {
    set({ sprintChange: null })
  },
  setSplashDone() {
    set({ splashDone: true })
  },

  setShortcuts(v) {
    set({ shortcuts: v })
  },
}))

// a pending delete still goes through if the tab is closed during the Undo window
if (typeof window !== 'undefined') {
  window.addEventListener('pagehide', () => {
    for (const { timer, commit } of [...pendingDeletes.values()]) {
      clearTimeout(timer)
      commit()
    }
  })
}

// persist what should survive a reload
useStore.subscribe((s, prev) => {
  if (s.drafts !== prev.drafts) LS.set(P + 'drafts', s.drafts.map(({ error, ...d }) => ({ ...d, error: error || null })))
  if (s.pinned !== prev.pinned) LS.set(P + 'pinned', s.pinned)
  if (s.meetingMemory !== prev.meetingMemory) LS.set(P + 'meetingMemory', s.meetingMemory)
  if (s.meetingSkip !== prev.meetingSkip) LS.set(P + 'meetingSkip', s.meetingSkip)
  if (s.settings !== prev.settings) LS.set(P + 'settings', s.settings)
})

export const lastFiredKey = P + 'reminderLastFired'
export { LS }
