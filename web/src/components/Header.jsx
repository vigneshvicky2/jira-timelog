import { motion } from 'motion/react'
import { TARGET, fmt } from '../lib/time.js'
import { useShallow } from 'zustand/react/shallow'
import { DEMO, loggedOn, pendingOn, useStore } from '../store.js'
import { Icon, Roll } from './ui.jsx'

export default function Header({ onSettings, onToggleReview, reviewCount }) {
  const me = useStore((s) => s.me)
  const sprintName = useStore((s) => s.sprintName)
  const sprintCount = useStore((s) => s.sprintIssues.length)
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
          <div className="brand-name">Timelog{DEMO && <span className="demo-tag">Demo</span>}</div>
          <div className="muted small">{greeting}{first ? `, ${first}` : ''}</div>
        </div>
      </div>

      {sprintName && (
        <motion.div className="sprint-pill" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }}>
          <Icon name="bolt" size={15} />
          <span>{sprintName}</span>
          <span className="muted">· {sprintCount} ticket{sprintCount === 1 ? '' : 's'}</span>
        </motion.div>
      )}

      <div className="top-progress">
        <div className="top-progress-text">
          <Roll className="strong">{fmt(totals.total)}</Roll>
          <span className="muted"> of {fmt(totals.target)} · {totals.days} day{totals.days === 1 ? '' : 's'}</span>
        </div>
        <div className="bar">
          <motion.div className="bar-fill" animate={{ width: `${pct * 100}%` }} transition={{ type: 'spring', stiffness: 90, damping: 20 }} />
        </div>
      </div>

      <div className="top-actions">
        <motion.button className={'icon-btn' + (loading ? ' spinning' : '')} title="Refresh (R)" whileTap={{ scale: 0.9 }}
          onClick={() => { refresh(); loadCalendar() }}>
          <Icon name="refresh" />
        </motion.button>
        <motion.button className="icon-btn review-toggle" title="Review" whileTap={{ scale: 0.9 }} onClick={onToggleReview}>
          <Icon name="panel" />
          {reviewCount > 0 && <span className="badge">{reviewCount}</span>}
        </motion.button>
        <motion.button className="icon-btn" title="Settings" whileTap={{ scale: 0.9 }} onClick={onSettings}>
          <Icon name="sliders" />
        </motion.button>
        <div className="avatar" title={me?.displayName}>{(first || '?').slice(0, 1).toUpperCase()}</div>
      </div>
    </header>
  )
}
