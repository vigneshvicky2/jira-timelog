import { AnimatePresence, motion } from 'motion/react'
import { useRef } from 'react'
import { TARGET, addDays, addMonths, fmt, fromIso, isWeekend, monthLabel } from '../lib/time.js'
import { isLeaveDay, loggedOn, pendingOn, useStore } from '../store.js'
import { Icon } from './ui.jsx'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export default function Sidebar() {
  const month = useStore((s) => s.month)
  const setMonth = useStore((s) => s.setMonth)
  const selectPreset = useStore((s) => s.selectPreset)
  const selected = useStore((s) => s.selected)
  const dir = useRef(1)

  const first = month
  const offset = (fromIso(first).getDay() + 6) % 7
  const gridStart = addDays(first, -offset)
  const cells = Array.from({ length: 42 }, (_, i) => addDays(gridStart, i))

  const go = (n) => {
    dir.current = n
    setMonth(addMonths(month, n))
  }

  return (
    <aside className="sidebar">
      <div className="cal-head">
        <AnimatePresence mode="popLayout" initial={false} custom={dir.current}>
          <motion.div key={month} className="cal-title" custom={dir.current}
            initial={{ opacity: 0, x: dir.current * 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -dir.current * 18 }}
            transition={{ duration: 0.22 }}>
            {monthLabel(month)}
          </motion.div>
        </AnimatePresence>
        <div className="cal-nav">
          <motion.button className="icon-btn small" whileTap={{ scale: 0.88 }} onClick={() => go(-1)} title="Previous month"><Icon name="left" /></motion.button>
          <motion.button className="icon-btn small" whileTap={{ scale: 0.88 }} onClick={() => go(1)} title="Next month"><Icon name="right" /></motion.button>
        </div>
      </div>

      <div className="cal-grid weekdays">
        {WEEKDAYS.map((w, i) => <div key={w} className={'wd' + (i >= 5 ? ' we' : '')}>{w}</div>)}
      </div>
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.div key={month} className="cal-grid"
          initial={{ opacity: 0, x: dir.current * 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -dir.current * 24 }}
          transition={{ duration: 0.25, ease: [0.2, 0.8, 0.2, 1] }}>
          {cells.map((d) => <DayCell key={d} d={d} inMonth={d.slice(0, 7) === month.slice(0, 7)} />)}
        </motion.div>
      </AnimatePresence>

      <div className="legend">
        <span><i className="dot weekend" /> Weekend</span>
        <span><i className="dot leave" /> Leave</span>
        <span><i className="dot full" /> 8h done</span>
      </div>

      <div className="presets">
        {[['today', 'Today'], ['last3', 'Last 3 workdays'], ['week', 'This week'], ['lastweek', 'Last week']].map(([k, label]) => (
          <motion.button key={k} className="chip" whileTap={{ scale: 0.94 }} onClick={() => selectPreset(k)}>{label}</motion.button>
        ))}
      </div>

      <SelectionSummary selected={selected} />

      <div className="tips">
        <div className="tips-title"><Icon name="keyboard" size={15} /> Shortcuts</div>
        <div><kbd>Click</kbd> toggle a day · <kbd>Shift</kbd>+click range · double-click just one</div>
        <div><kbd>↑↓←→</kbd> move in the grid · type <kbd>1.5</kbd> or <kbd>45m</kbd> to set time</div>
        <div><kbd>Del</kbd> clear cell · <kbd>Ctrl</kbd>+<kbd>Enter</kbd> log all</div>
      </div>
    </aside>
  )
}

function DayCell({ d, inMonth }) {
  const today = useStore((s) => s.today)
  const selected = useStore((s) => s.selected.includes(d))
  const total = useStore((s) => loggedOn(s, d) + pendingOn(s, d))
  const leave = useStore((s) => isLeaveDay(s, d))
  const clickDay = useStore((s) => s.clickDay)
  const selectOnly = useStore((s) => s.selectOnly)
  const pct = Math.min(1, total / TARGET)
  const cls = [
    'day',
    !inMonth && 'out',
    isWeekend(d) && 'weekend',
    leave && 'leave',
    d === today && 'today',
    selected && 'selected',
    total >= TARGET && 'full',
    total > TARGET && 'over',
    d > today && 'future',
  ].filter(Boolean).join(' ')
  return (
    <motion.button
      className={cls}
      whileTap={{ scale: 0.9 }}
      onClick={(e) => clickDay(d, { shift: e.shiftKey })}
      onDoubleClick={() => selectOnly(d)}
      title={`${d}${total ? ` · ${fmt(total)}` : ''}${leave ? ' · leave' : ''}`}
    >
      {selected && <motion.span layoutId={undefined} className="sel-bg" initial={{ scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: 'spring', stiffness: 500, damping: 30 }} />}
      <span className="num">{Number(d.slice(8))}</span>
      <span className="mini-bar"><motion.span animate={{ width: `${pct * 100}%` }} transition={{ type: 'spring', stiffness: 120, damping: 20 }} /></span>
    </motion.button>
  )
}

function SelectionSummary({ selected }) {
  const left = useStore((s) => selected.reduce((a, d) => a + Math.max(0, TARGET - loggedOn(s, d) - pendingOn(s, d)), 0))
  return (
    <div className="sel-summary">
      <div className="sel-title">{selected.length} day{selected.length === 1 ? '' : 's'} selected</div>
      <div className="muted small">{left > 0 ? `${fmt(left)} left to fill` : 'All selected days are complete ✓'}</div>
    </div>
  )
}
