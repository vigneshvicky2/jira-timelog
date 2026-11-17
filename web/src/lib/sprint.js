// Countdown helpers for the active sprint ({ name, start, end } with ISO dates or null).

const dayStart = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x }
const isWorkday = (d) => d.getDay() !== 0 && d.getDay() !== 6

/**
 * Monday-Friday days left in the sprint, counting today; null without an end date.
 * Sprints usually end on the morning the next one starts (Wed -> Wed), so the end day only
 * counts when the sprint ends in the afternoon; a 2-week sprint is 10 workdays, not 11.
 */
export function workdaysLeft(sprint, now = new Date()) {
  if (!sprint?.end) return null
  const endAt = new Date(sprint.end)
  const last = dayStart(endAt)
  if (endAt.getHours() < 12) last.setDate(last.getDate() - 1)
  let d = dayStart(now)
  let n = 0
  while (d <= last) { if (isWorkday(d)) n++; d.setDate(d.getDate() + 1) }
  return n
}

/** How far through the sprint we are, 0..1; null without both dates. */
export function elapsed(sprint, now = Date.now()) {
  if (!sprint?.start || !sprint?.end) return null
  const s = Date.parse(sprint.start), e = Date.parse(sprint.end)
  if (!(e > s)) return null
  return Math.min(1, Math.max(0, (now - s) / (e - s)))
}

export const ended = (sprint, now = Date.now()) => !!sprint?.end && Date.parse(sprint.end) < now

const fmtDay = (iso) => new Date(iso).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })

/** "4 workdays left · ends Fri 28 Nov"; null without an end date. */
export function detail(sprint) {
  if (!sprint?.end) return null
  const left = workdaysLeft(sprint)
  const ends = fmtDay(sprint.end)
  if (ended(sprint)) return `Ended ${ends}`
  if (left === 0) return 'Ends today'
  return `${left} workday${left === 1 ? '' : 's'} left · ends ${ends}`
}

/** Short label for pills: "4 days left", "Last day", "Ended". */
export function shortLeft(sprint) {
  const left = workdaysLeft(sprint)
  if (left == null) return null
  if (ended(sprint)) return 'Ended'
  if (left <= 1) return 'Last day'
  return `${left} days left`
}
