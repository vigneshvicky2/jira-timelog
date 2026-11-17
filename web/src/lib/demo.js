import { JiraError, NOT_FOUND, OFFLINE } from './jira.js'
import { addDays, isWeekend, todayIso } from './time.js'

// Fake data for ?demo, so the UI can be explored and screenshotted without a Jira account.
const SPRINT = 'Sprint 42'
const H = 3600
const st = (status, statusCategory, estimate, spent, remaining) => ({
  status, statusCategory, tracking: { estimate, spent, remaining: remaining ?? (estimate != null ? Math.max(0, estimate - spent) : null) },
})
const ISSUES = [
  { key: 'WEB-214', summary: 'Checkout page redesign', sprint: SPRINT, ...st('In Progress', 'indeterminate', 16 * H, 9.5 * H) },
  { key: 'WEB-221', summary: 'Fix flaky payment webhook tests', sprint: SPRINT, ...st('In Progress', 'indeterminate', 6 * H, 8 * H, 0) },
  { key: 'WEB-230', summary: 'Accessibility audit fixes', sprint: SPRINT, ...st('To Do', 'new', 8 * H, 2 * H) },
  { key: 'WEB-198', summary: 'Team ceremonies', sprint: SPRINT, ...st('In Progress', 'indeterminate', null, 12 * H, null) },
  { key: 'WEB-233', summary: 'Code reviews', sprint: SPRINT, ...st('In Review', 'indeterminate', 10 * H, 4 * H) },
]
const TIME_OFF = { key: 'WEB-200', summary: 'Time off' }
// a two-week sprint that started last Wednesday
const SPRINT_INFO = (() => {
  const start = new Date(); start.setDate(start.getDate() - ((start.getDay() + 4) % 7)); start.setHours(9, 0, 0, 0)
  const end = new Date(start); end.setDate(end.getDate() + 13); end.setHours(18, 0, 0, 0)
  return { id: 42, name: SPRINT, start: start.toISOString(), end: end.toISOString() }
})()
const OTHER = [
  { key: 'OPS-77', summary: 'On-call handover notes' },
  { key: 'OPS-81', summary: 'Upgrade staging database' },
  { key: 'DOC-12', summary: 'API onboarding guide' },
]

const wait = (ms) => new Promise((r) => setTimeout(r, ms))

// ?demo&flaky makes every other new entry fail once (and "Move to In Progress" fail), to show errors, Retry and rollback.
const FLAKY = new URLSearchParams(location.search).has('flaky')
const FAILS = [
  () => new JiraError(0, OFFLINE),
  () => new JiraError(404, NOT_FOUND),
  () => new JiraError(400, 'Worklog must not be logged on a ticket in a closed sprint.'),
]

export function createDemoJira() {
  const today = todayIso()
  let nextId = 1000
  const logs = []
  const tried = new Set()
  // keep the sample tickets' "time left" honest as entries are logged or deleted
  const track = (key, secs) => {
    const i = ISSUES.find((x) => x.key === key)
    if (!i?.tracking) return
    const t = i.tracking
    const spent = Math.max(0, t.spent + secs)
    i.tracking = { ...t, spent, remaining: t.estimate != null ? Math.max(0, t.estimate - spent) : t.remaining }
  }
  let attempts = 0
  // a believable past: most weekdays mostly filled, one leave day
  for (let i = 1; i <= 20; i++) {
    const d = addDays(today, -i)
    if (isWeekend(d)) continue
    if (i === 6) {
      logs.push({ id: String(nextId++), key: TIME_OFF.key, summary: TIME_OFF.summary, date: d, start: '09:00', seconds: 8 * 3600, comment: 'Personal leave' })
      continue
    }
    if (i <= 2) continue // leave the last two days for the user to fill
    const plan = [
      [ISSUES[3], 1800, '09:45'],
      [ISSUES[i % 3], 4 * 3600, '10:15'],
      [ISSUES[4], 3600, '14:30'],
      [ISSUES[(i + 1) % 3], i % 2 ? 2.5 * 3600 : 2 * 3600, '15:30'],
    ]
    for (const [iss, s, t] of plan) logs.push({ id: String(nextId++), key: iss.key, summary: iss.summary, date: d, start: t, seconds: s, comment: '' })
  }
  return {
    demo: true,
    async myself() {
      await wait(300)
      return { accountId: 'demo', displayName: 'Alex Morgan' }
    },
    async sprintIssues() {
      await wait(250)
      return ISSUES.map((i) => ({ ...i, sprintInfo: SPRINT_INFO }))
    },
    async timeOff() {
      return TIME_OFF
    },
    async worklogs(_a, from, to) {
      await wait(400)
      return logs.filter((l) => l.date >= from && l.date <= to)
    },
    async issueDetails(key) {
      await wait(450)
      const i = [...ISSUES, ...OTHER, TIME_OFF].find((x) => x.key === key) || { key, summary: 'Ticket' }
      const t = (text, marks) => ({ type: 'text', text, ...(marks ? { marks } : {}) })
      const p = (...content) => ({ type: 'paragraph', content })
      return {
        key, summary: i.summary, type: key === TIME_OFF.key ? 'Task' : 'Story', status: i.status || 'In Progress',
        statusCategory: i.statusCategory || 'indeterminate', tracking: i.tracking || { estimate: null, spent: 0, remaining: null }, priority: 'Medium', assignee: 'Alex Morgan', reporter: 'Sam Lee',
        labels: ['frontend', 'q4'], url: `https://demo.atlassian.net/browse/${key}`,
        description: {
          type: 'doc', version: 1, content: [
            { type: 'heading', attrs: { level: 3 }, content: [t('Goal')] },
            p(t('Make the '), t('checkout flow', [{ type: 'strong' }]), t(' faster and clearer on mobile and desktop.')),
            { type: 'heading', attrs: { level: 3 }, content: [t('Acceptance criteria')] },
            { type: 'bulletList', content: [
              { type: 'listItem', content: [p(t('Single-page checkout with inline validation'))] },
              { type: 'listItem', content: [p(t('Saved cards shown first'))] },
              { type: 'listItem', content: [p(t('Lighthouse performance score '), t('≥ 90', [{ type: 'code' }]))] },
            ] },
            { type: 'blockquote', content: [p(t('Design: see the Figma file linked in the epic.', [{ type: 'em' }]))] },
            p(t('Spec: '), t('internal wiki', [{ type: 'link', attrs: { href: 'https://example.com' } }])),
          ],
        },
      }
    },
    async searchAll(q) {
      await wait(300)
      const s = q.toLowerCase()
      return [...ISSUES, ...OTHER, TIME_OFF].filter((i) => `${i.key} ${i.summary}`.toLowerCase().includes(s))
    },
    async addWorklog(d) {
      await wait(380 + Math.random() * 260)
      if (FLAKY && !tried.has(d.id)) {
        tried.add(d.id)
        if (attempts++ % 2 === 1) throw FAILS[(Math.floor(attempts / 2) - 1) % FAILS.length]()
      }
      logs.push({ id: String(nextId++), key: d.issue.key, summary: d.issue.summary, date: d.date, start: d.start, seconds: d.seconds, comment: d.comment })
      track(d.issue.key, d.seconds)
    },
    async deleteWorklog(_k, id) {
      await wait(200)
      const i = logs.findIndex((l) => l.id === id)
      if (i >= 0) track(logs[i].key, -logs.splice(i, 1)[0].seconds)
    },
    async startProgress(key) {
      await wait(900)
      if (FLAKY) throw new JiraError(400, "You don't have permission to transition this ticket.")
      const i = ISSUES.find((x) => x.key === key)
      if (i) Object.assign(i, { status: 'In Progress', statusCategory: 'indeterminate' })
      return { status: 'In Progress', statusCategory: 'indeterminate' }
    },
  }
}

export function demoMeetings(from, to) {
  const out = {}
  for (let d = from; d <= to; d = addDays(d, 1)) {
    if (isWeekend(d)) continue
    const day = []
    const add = (title, start, end, minutes) => day.push({ title, date: d, start, end, minutes, key: `${d}|${start}|${title}` })
    add('Daily standup', '09:45', '10:00', 15)
    if (new Date(d).getDay() === 2) add('Sprint planning', '11:00', '12:00', 60)
    if (new Date(d).getDay() === 4) add('Design review', '15:00', '15:30', 30)
    out[d] = day
  }
  return out
}
