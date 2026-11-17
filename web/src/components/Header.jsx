import { motion } from 'motion/react'
import { TARGET, fmt } from '../lib/time.js'
import { useShallow } from 'zustand/react/shallow'
import { DEMO, loggedOn, pendingOn, useStore } from '../store.js'
import { Count, Icon } from './ui.jsx'
import { ended, shortLeft, workdaysLeft } from '../lib/sprint.js'

export default function Header({ onSettings, onToggleReview, reviewCount }) {
  const me = useStore((s) => s.me)
  const sprintName = useStore((s) => s.sprintName)
  const sprintCount = useStore((s) => s.sprintIssues.length)
  const sprint = useStore((s) => s.sprint)
  const sprintLeft = shortLeft(sprint)
  const sprintUrgent = ended(sprint) || (workdaysLeft(sprint) ?? 99) <= 2
  const loading = useStore((s) => s.loading)
  const refresh = useStore((s) => s.refresh)
  const loadCalendar = useStore((s) => s.loadCalendar)
  const totals = useStore(useShallow((s) => {
    let total = 0
    for (const d of s.selected) total += loggedOn(s, d) + pendingOn(s, d)
    return { total, target: TARGET * s.selected.length, days: s.selected.length }
  }))
  const hour = new Date().getHours()
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
  const first = (me?.displayName || '').split(' ')[0]
  const pct = Math.min(1, totals.total / totals.target)

  return (
    <header className="topbar">
      <div className="brand">
        <img src="/favicon.svg" alt="" />
        <div>
          <div className="brand-name">Stint{DEMO && <span className="demo-tag" title="Sample data, nothing is sent to Jira">Demo</span>}</div>
          <div className="muted small greeting">{greeting}{first ? `, ${first}` : ''}</div>
        </div>
      </div>

      {sprintName && (
        <motion.div className="sprint-pill" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }}>
          <Icon name="bolt" size={15} />
          <span>{sprintName}</span>
          <span className="muted">· {sprintCount} ticket{sprintCount === 1 ? '' : 's'}</span>
          {sprintLeft && <span className={'sprint-left' + (sprintUrgent ? ' urgent' : '')}>{sprintLeft}</span>}
        </motion.div>
      )}

      <div className="top-progress">
        <div className="top-progress-text">
          <Count className="strong" value={totals.total} />
          <span className="muted"> of {fmt(totals.target)} · {totals.days} day{totals.days === 1 ? '' : 's'}</span>
        </div>
        <div className="bar" role="progressbar" aria-label="Logged on the selected days" aria-valuemin={0} aria-valuemax={totals.target} aria-valuenow={totals.total}
          aria-valuetext={`${fmt(totals.total)} of ${fmt(totals.target)}`}>
          <motion.div className="bar-fill" animate={{ width: `${pct * 100}%` }} transition={{ type: 'spring', stiffness: 90, damping: 20 }} />
        </div>
      </div>

      <div className="top-actions">
        <motion.button className={'icon-btn' + (loading ? ' spinning' : '')} title="Refresh (R)" aria-label="Refresh" whileTap={{ scale: 0.9 }}
          onClick={() => { refresh(); loadCalendar() }}>
          <Icon name="refresh" />
        </motion.button>
        <motion.button className="icon-btn review-toggle" title="Review" aria-label={`Review ${reviewCount} pending entries`} whileTap={{ scale: 0.9 }} onClick={onToggleReview}>
          <Icon name="panel" />
          {reviewCount > 0 && <span className="badge">{reviewCount}</span>}
        </motion.button>
        <motion.button className="icon-btn" title="Settings" aria-label="Settings" whileTap={{ scale: 0.9 }} onClick={onSettings}>
          <Icon name="sliders" />
        </motion.button>
        <button className="avatar" title={me?.displayName ? `${me.displayName} · Settings` : 'Settings'} aria-label="Account settings" onClick={onSettings}>
          {(first || '?').slice(0, 1).toUpperCase()}
        </button>
      </div>
    </header>
  )
}
