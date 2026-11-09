export const TARGET = 8 * 3600

const pad = (n) => String(n).padStart(2, '0')

/** Local date -> 'YYYY-MM-DD'. */
export const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
/** 'YYYY-MM-DD' -> local Date at midnight. */
export const fromIso = (s) => {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}
export const addDays = (s, n) => {
  const d = fromIso(s)
  d.setDate(d.getDate() + n)
  return iso(d)
}
export const todayIso = () => iso(new Date())
export const isWeekend = (s) => {
  const w = fromIso(s).getDay()
  return w === 0 || w === 6
}
export const monthStart = (s) => s.slice(0, 8) + '01'
export const addMonths = (s, n) => {
  const d = fromIso(monthStart(s))
  d.setMonth(d.getMonth() + n)
  return iso(d)
}
export const daysBetween = (a, b) => Math.round((fromIso(b) - fromIso(a)) / 86400000)

export const fmtDay = (s, opts) => fromIso(s).toLocaleDateString(undefined, opts)
export const weekdayShort = (s) => fmtDay(s, { weekday: 'short' })
export const longDate = (s) => fmtDay(s, { weekday: 'long', day: 'numeric', month: 'short' })
export const shortDate = (s) => fmtDay(s, { weekday: 'short', day: 'numeric' })
export const monthLabel = (s) => fmtDay(s, { month: 'long', year: 'numeric' })

export function relativeLabel(s, today) {
  const diff = daysBetween(s, today)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Yesterday'
  if (diff === -1) return 'Tomorrow'
  return diff > 1 ? `${diff} days ago` : `In ${-diff} days`
}

/** 5400 -> "1h 30m" */
export function fmt(seconds) {
  const m = Math.floor(Math.abs(seconds) / 60)
  const h = Math.floor(m / 60)
  const mm = m % 60
  if (h === 0) return `${mm}m`
  if (mm === 0) return `${h}h`
  return `${h}h ${mm}m`
}

/** "13:05" -> "1:05 PM" */
export function fmtTime(hhmm) {
  const [h, m] = hhmm.split(':').map(Number)
  const d = new Date(2000, 0, 1, h, m)
  return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

/**
 * Parses what people type into a time cell: "1.5", "2", "45", "1h 30m", "90m", "1:30".
 * Bare numbers up to 12 are hours, larger ones are minutes. Returns seconds or null.
 */
export function parseDuration(input) {
  const s = String(input).trim().toLowerCase().replace(',', '.')
  if (!s) return null
  if (/^\d+(\.\d+)?$/.test(s)) {
    const n = parseFloat(s)
    return Math.round(n <= 12 ? n * 3600 : n * 60)
  }
  const hm = s.match(/^(\d{1,2}):(\d{1,2})$/)
  if (hm) return Number(hm[1]) * 3600 + Number(hm[2]) * 60
  let total = 0
  let matched = false
  const re = /(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m)/g
  let x
  while ((x = re.exec(s))) {
    matched = true
    total += parseFloat(x[1]) * (x[2].startsWith('h') ? 3600 : 60)
  }
  if (!matched) return null
  return Math.round(total / 60) * 60
}

/** Jira "started" timestamp in local time: 2026-11-09T10:00:00.000+0530 */
export function jiraStarted(date, hhmm) {
  const [h, m] = hhmm.split(':').map(Number)
  const d = fromIso(date)
  d.setHours(h, m, 0, 0)
  const off = -d.getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  const a = Math.abs(off)
  return `${date}T${pad(h)}:${pad(m)}:00.000${sign}${pad(Math.floor(a / 60))}${pad(a % 60)}`
}

/** Parses Jira's started value into local { date, time }. */
export function parseStarted(s) {
  const d = new Date(s.replace(/([+-]\d\d)(\d\d)$/, '$1:$2'))
  return { date: iso(d), time: `${pad(d.getHours())}:${pad(d.getMinutes())}` }
}

export const hhmm = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`
