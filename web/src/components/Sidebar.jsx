import ScrollArea from './ScrollArea.jsx'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useRef } from 'react'
import { TARGET, addDays, addMonths, fmt, fromIso, isWeekend, longDate, monthLabel } from '../lib/time.js'
import { isLeaveDay, loggedOn, pendingOn, useStore } from '../store.js'
import { Icon } from './ui.jsx'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export default function Sidebar({ onClose }) {
  const month = useStore((s) => s.month)
  const setMonth = useStore((s) => s.setMonth)
  const selectPreset = useStore((s) => s.selectPreset)
  const selected = useStore((s) => s.selected)
  const setShortcuts = useStore((s) => s.setShortcuts)
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
    <ScrollArea hostClassName="side-host" className="sidebar">
      <div className="sheet-grip" aria-hidden="true" />
      <div className="cal-head">
        <AnimatePresence mode="popLayout" initial={false} custom={dir.current}>
          <motion.div key={month} className="cal-title" custom={dir.current}
            initial={{ opacity: 0, x: dir.current * 18 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -dir.current * 18 }}
            transition={{ duration: 0.22 }}>
            {monthLabel(month)}
          </motion.div>
        </AnimatePresence>
        <div className="cal-nav">
          <motion.button className="icon-btn small" whileTap={{ scale: 0.88 }} onClick={() => go(-1)} title="Previous month" aria-label="Previous month"><Icon name="left" /></motion.button>
          <motion.button className="icon-btn small" whileTap={{ scale: 0.88 }} onClick={() => go(1)} title="Next month" aria-label="Next month"><Icon name="right" /></motion.button>
          {onClose && <button className="icon-btn small cal-close" onClick={onClose} title="Close calendar" aria-label="Close calendar"><Icon name="close" /></button>}
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

      <div className="side-foot">
        <button className="kbd-btn" onClick={() => setShortcuts(true)} title="Keyboard shortcuts (?)" aria-label="Keyboard shortcuts">
          <Icon name="keyboard" size={16} /> <kbd>?</kbd>
        </button>
        <div className="built-by">Built by Vignesh Kodiswaran</div>
      </div>
    </ScrollArea>
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
      aria-pressed={selected}
      aria-label={dayLabel(d, total, leave, d === today)}
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

const dayLabel = (d, total, leave, today) =>
  [longDate(d), today && 'today', leave ? 'leave' : total ? `${fmt(total)} logged${total > TARGET ? ', over 8 hours' : total === TARGET ? ', complete' : ''}` : 'nothing logged', isWeekend(d) && 'weekend']
    .filter(Boolean).join(', ')

/**
 * Phone layout: a horizontally scrolling strip of recent days instead of the month grid.
 * Tap picks one day; press and hold adds or removes a day from the selection.
 */
export function WeekStrip({ onOpenCalendar }) {
  const today = useStore((s) => s.today)
  const selected = useStore((s) => s.selected)
  const selectPreset = useStore((s) => s.selectPreset)
  const ref = useRef(null)
  const first = [addDays(today, -27), selected[0]].sort()[0]
  const lastSel = selected[selected.length - 1]
  const last = [addDays(today, 6), lastSel].sort()[1]
  const days = []
  for (let d = first; d <= last; d = addDays(d, 1)) days.push(d)

  useEffect(() => {
    const el = ref.current?.querySelector(`[data-day="${lastSel}"]`)
    el?.scrollIntoView({ inline: 'center', block: 'nearest' })
  }, [lastSel])

  return (
    <div className="strip">
      <div className="strip-head">
        <button className="strip-month" onClick={onOpenCalendar} aria-label="Open month calendar">
          <Icon name="calendar" size={16} /> {fromIso(lastSel).toLocaleDateString(undefined, { month: 'short', year: 'numeric' })} <Icon name="right" size={14} className="strip-caret" />
        </button>
        <span className="muted tiny strip-hint">Hold a day to add more</span>
        <button className="chip strip-chip" onClick={() => selectPreset('week')}>This week</button>
      </div>
      <ScrollArea hostClassName="strip-host" className="strip-days" innerRef={ref}>
        {days.map((d) => <StripDay key={d} d={d} />)}
      </ScrollArea>
    </div>
  )
}

function StripDay({ d }) {
  const today = useStore((s) => s.today)
  const selected = useStore((s) => s.selected.includes(d))
  const total = useStore((s) => loggedOn(s, d) + pendingOn(s, d))
  const leave = useStore((s) => isLeaveDay(s, d))
  const clickDay = useStore((s) => s.clickDay)
  const selectOnly = useStore((s) => s.selectOnly)
  const hold = useRef({ timer: null, fired: false })
  const start = () => {
    hold.current.fired = false
    clearTimeout(hold.current.timer)
    hold.current.timer = setTimeout(() => {
      hold.current.fired = true
      navigator.vibrate?.(12)
      clickDay(d)
    }, 450)
  }
  const cancel = () => clearTimeout(hold.current.timer)
  const date = fromIso(d)
  const cls = ['sday', isWeekend(d) && 'weekend', leave && 'leave', d === today && 'today', selected && 'selected', total >= TARGET && 'full', total > TARGET && 'over', d > today && 'future']
    .filter(Boolean).join(' ')
  return (
    <motion.button data-day={d} className={cls} whileTap={{ scale: 0.92 }}
      onPointerDown={start} onPointerUp={cancel} onPointerLeave={cancel} onPointerCancel={cancel}
      onContextMenu={(e) => e.preventDefault()}
      onClick={() => { if (!hold.current.fired) selectOnly(d) }}
      aria-pressed={selected} aria-label={dayLabel(d, total, leave, d === today)}>
      <span className="sday-wd">{date.toLocaleDateString(undefined, { weekday: 'narrow' })}</span>
      <span className="sday-num">{date.getDate()}</span>
      <span className="mini-bar"><motion.span animate={{ width: `${Math.min(1, total / TARGET) * 100}%` }} transition={{ type: 'spring', stiffness: 120, damping: 20 }} /></span>
    </motion.button>
  )
}
