import { fmt } from './time.js'

/** Jira time tracking fields -> seconds (null when Jira has no value). */
export function trackingOf(f = {}) {
  const t = f.timetracking || {}
  const num = (...v) => {
    const x = v.find((n) => typeof n === 'number')
    return x === undefined ? null : x
  }
  return {
    estimate: num(t.originalEstimateSeconds, f.timeoriginalestimate),
    remaining: num(t.remainingEstimateSeconds, f.timeestimate),
    spent: num(t.timeSpentSeconds, f.timespent) || 0,
  }
}

export const TRACKING_FIELDS = ['status', 'timetracking', 'timeoriginalestimate', 'timeestimate', 'timespent']

/**
 * Progress of a ticket against its estimate, plus warnings for the two easy mistakes:
 * logging more than the estimate, and logging on a ticket that is still To Do.
 * `pending` is time queued in the app but not yet sent to Jira.
 */
export function progressOf(tracking, statusCategory, pending = 0) {
  const t = tracking || { estimate: null, remaining: null, spent: 0 }
  const spent = t.spent || 0
  const estimate = t.estimate || null
  const remaining = t.remaining ?? (estimate != null ? Math.max(0, estimate - spent) : null)
  const total = estimate || (remaining != null ? spent + remaining : null)
  const over = estimate ? Math.max(0, spent - estimate) : 0
  const afterSpent = spent + pending
  const afterRemaining = remaining != null ? Math.max(0, remaining - pending) : null
  const afterOver = estimate ? Math.max(0, afterSpent - estimate) : 0
  const pct = total ? Math.min(1, spent / total) : 0
  const pendingPct = total ? Math.min(1 - pct, pending / total) : 0
  const todo = statusCategory === 'new'
  const done = statusCategory === 'done'

  const warnings = []
  if (todo && spent > 0) warnings.push({ kind: 'todo', text: `${fmt(spent)} is logged but the ticket is still To Do. Move it to In Progress or check those entries.` })
  if (todo && pending > 0) warnings.push({ kind: 'todo', text: `You're about to log ${fmt(pending)} on a ticket that is still To Do.` })
  if (done && pending > 0) warnings.push({ kind: 'done', text: `This ticket is already Done; you're about to log ${fmt(pending)} more.` })
  if (over > 0) warnings.push({ kind: 'over', text: `Over-logged by ${fmt(over)}: ${fmt(spent)} logged against a ${fmt(estimate)} estimate.` })
  if (afterOver > over) warnings.push({ kind: 'over', text: `Your pending ${fmt(pending)} would take it ${fmt(afterOver)} over the estimate.` })
  if (!estimate && remaining == null) warnings.push({ kind: 'info', text: 'No estimate on this ticket, so remaining time is unknown.' })

  return { spent, estimate, remaining, total, over, pending, afterRemaining, afterOver, pct, pendingPct, todo, done, warnings }
}
