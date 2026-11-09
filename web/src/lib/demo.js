import { addDays, isWeekend, todayIso } from './time.js'

// Fake data for ?demo, so the UI can be explored and screenshotted without a Jira account.
const SPRINT = 'Sprint 42'
const ISSUES = [
  { key: 'WEB-214', summary: 'Checkout page redesign', sprint: SPRINT },
  { key: 'WEB-221', summary: 'Fix flaky payment webhook tests', sprint: SPRINT },
  { key: 'WEB-230', summary: 'Accessibility audit fixes', sprint: SPRINT },
  { key: 'WEB-198', summary: 'Team ceremonies', sprint: SPRINT },
  { key: 'WEB-233', summary: 'Code reviews', sprint: SPRINT },
]
const TIME_OFF = { key: 'WEB-200', summary: 'Time off' }
const OTHER = [
  { key: 'OPS-77', summary: 'On-call handover notes' },
  { key: 'OPS-81', summary: 'Upgrade staging database' },
  { key: 'DOC-12', summary: 'API onboarding guide' },
]

const wait = (ms) => new Promise((r) => setTimeout(r, ms))

export function createDemoJira() {
  const today = todayIso()
  let nextId = 1000
  const logs = []
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
      return ISSUES
    },
    async timeOff() {
      return TIME_OFF
    },
    async worklogs(_a, from, to) {
      await wait(400)
      return logs.filter((l) => l.date >= from && l.date <= to)
    },
    async searchAll(q) {
      await wait(300)
      const s = q.toLowerCase()
      return [...ISSUES, ...OTHER, TIME_OFF].filter((i) => `${i.key} ${i.summary}`.toLowerCase().includes(s))
    },
    async addWorklog(d) {
      await wait(180 + Math.random() * 200)
      logs.push({ id: String(nextId++), key: d.issue.key, summary: d.issue.summary, date: d.date, start: d.start, seconds: d.seconds, comment: d.comment })
    },
    async deleteWorklog(_k, id) {
      await wait(200)
      const i = logs.findIndex((l) => l.id === id)
      if (i >= 0) logs.splice(i, 1)
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
