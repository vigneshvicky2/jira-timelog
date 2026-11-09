import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { TARGET, fmt, fmtTime, fromIso, isWeekend, parseDuration, relativeLabel } from '../lib/time.js'
import { isLeaveDay, loggedOn, pendingOn, remainingOn, useStore } from '../store.js'
import { Icon, KeyChip, Popover, Ring, Roll, Spinner } from './ui.jsx'

const PRESETS = [15, 30, 45, 60, 90, 120, 180, 240]
const ease = [0.2, 0.8, 0.2, 1]

export default function Timesheet({ onOpenSettings }) {
  const selected = useStore((s) => s.selected)
  const drafts = useStore((s) => s.drafts)
  const worklogs = useStore((s) => s.worklogs)
  const sprintIssues = useStore((s) => s.sprintIssues)
  const sprintName = useStore((s) => s.sprintName)
  const timeOff = useStore((s) => s.timeOff)
  const pinned = useStore((s) => s.pinned)
  const meetings = useStore((s) => s.meetings)
  const calendarState = useStore((s) => s.calendarState)
  const loading = useStore((s) => s.loading)
  const setCell = useStore((s) => s.setCell)
  const unpin = useStore((s) => s.unpin)
  const openPeek = useStore((s) => s.openPeek)

  const sheetRef = useRef(null)
  const [editor, setEditor] = useState(null)
  const [meetingEd, setMeetingEd] = useState(null)
  const [dayMenu, setDayMenu] = useState(null)
  const days = selected

  // per-cell lookups
  const index = useMemo(() => {
    const pending = {}
    const meet = {}
    const logged = {}
    for (const d of drafts) {
      const k = d.date + '|' + d.issue.key
      if (d.meetingKey) meet[k] = (meet[k] || 0) + d.seconds
      else pending[k] = d
    }
    for (const day of days) for (const w of worklogs[day] || []) logged[day + '|' + w.key] = (logged[day + '|' + w.key] || 0) + w.seconds
    return { pending, meet, logged }
  }, [drafts, worklogs, days])

  // rows: leave, sprint tickets, then anything else with time on the selected days
  const sections = useMemo(() => {
    const seen = new Set()
    const take = (list) => list.filter((i) => i && !seen.has(i.key) && seen.add(i.key))
    const leave = take(timeOff ? [timeOff] : [])
    const sprint = take(sprintIssues)
    const others = [...pinned.map((p) => ({ ...p, pinned: true }))]
    for (const d of drafts) if (days.includes(d.date)) others.push(d.issue)
    for (const day of days) for (const w of worklogs[day] || []) others.push({ key: w.key, summary: w.summary })
    const other = take(others)
    return [
      leave.length && { id: 'leave', title: 'Leave', sub: 'Shared time-off ticket', icon: 'beach', rows: leave, leave: true },
      { id: 'sprint', title: 'Sprint tickets', sub: sprintName || 'Assigned to you in the active sprint', icon: 'bolt', rows: sprint },
      other.length && { id: 'other', title: 'Other tickets', sub: 'Searched, pinned or already logged', icon: 'search', rows: other },
    ].filter(Boolean)
  }, [timeOff, sprintIssues, pinned, drafts, days, worklogs, sprintName])

  const flat = useMemo(() => sections.flatMap((s) => s.rows.map((r) => ({ ...r, leave: !!s.leave }))), [sections])
  const rowIndex = useMemo(() => Object.fromEntries(flat.map((r, i) => [r.key, i])), [flat])

  const cols = `minmax(210px, 250px) repeat(${days.length}, minmax(156px, 1fr))`
  const minWidth = 220 + days.length * 158

  const focusCell = useCallback((r, c) => {
    const el = sheetRef.current?.querySelector(`[data-cell][data-r="${r}"][data-c="${c}"]`)
    if (el) {
      el.focus({ preventScroll: true })
      el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
    }
  }, [])

  const openCell = (el, initial) => {
    const row = flat[+el.dataset.r]
    if (!row) return
    setEditor({ date: el.dataset.date, issue: row, leave: row.leave, rect: el.getBoundingClientRect(), initial, r: +el.dataset.r, c: +el.dataset.c })
  }

  const onKeyDown = (e) => {
    const el = e.target.closest?.('[data-cell]')
    if (!el || editor) return
    const r = +el.dataset.r
    const c = +el.dataset.c
    const moves = { ArrowDown: [1, 0], ArrowUp: [-1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }
    if (moves[e.key]) {
      e.preventDefault()
      focusCell(r + moves[e.key][0], c + moves[e.key][1])
    } else if (e.key === 'Enter' || e.key === 'F2') {
      e.preventDefault()
      openCell(el)
    } else if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault()
      setCell(el.dataset.date, flat[r], 0)
    } else if (/^[0-9.]$/.test(e.key) && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault()
      openCell(el, e.key)
    }
  }

  const closeEditor = (move) => {
    const ed = editor
    setEditor(null)
    if (ed) setTimeout(() => focusCell(move ? ed.r + 1 : ed.r, ed.c), 0)
  }

  return (
    <div className="sheet-wrap" ref={sheetRef} onKeyDown={onKeyDown}>
      <div className="sheet" style={{ '--cols': cols, minWidth }}>
        {/* day headers */}
        <div className="row head">
          <div className="rowhead corner">
            <div className="corner-title">Tickets</div>
            <div className="muted small">Click a cell or type a time</div>
          </div>
          <AnimatePresence initial={false} mode="popLayout">
            {days.map((d) => (
              <DayHeader key={d} d={d} onMenu={(rect) => setDayMenu({ d, rect })} />
            ))}
          </AnimatePresence>
        </div>

        {/* meetings */}
        <div className="row meetings">
          <div className="rowhead">
            <div className="rh-title"><Icon name="calendar" size={16} /> Meetings</div>
            {calendarState === 'ok' && <div className="muted small">Click to log against a ticket</div>}
            {calendarState === 'loading' && <div className="muted small"><Spinner size={12} /> Loading calendar…</div>}
            {(calendarState === 'none' || calendarState === 'error') && (
              <button className="link-btn" onClick={onOpenSettings}>
                {calendarState === 'error' ? 'Calendar error. Fix in settings' : 'Connect your calendar'}
              </button>
            )}
          </div>
          {days.map((d) => (
            <div key={d} className={'mcell' + (isWeekend(d) ? ' weekend' : '')}>
              <AnimatePresence initial={false}>
                {(meetings[d] || []).map((m, i) => (
                  <MeetingChip key={m.key} m={m} i={i} onOpen={(rect) => setMeetingEd({ meeting: m, rect })} />
                ))}
              </AnimatePresence>
              {calendarState === 'ok' && !(meetings[d] || []).length && <div className="mempty">No meetings</div>}
            </div>
          ))}
        </div>

        {/* ticket sections */}
        {sections.map((sec) => (
          <div key={sec.id} className="section">
            <div className="row section-label">
              <div className="rowhead">
                <span className={'sec-icon ' + sec.id}><Icon name={sec.icon} size={14} /></span>
                <span className="sec-title">{sec.title}</span>
                <span className="muted small sec-sub">{sec.sub}</span>
              </div>
            </div>
            {sec.id === 'sprint' && !sec.rows.length && (
              <div className="row"><div className="rowhead empty-row">{loading ? <><Spinner size={14} /> Loading your sprint…</> : 'No tickets assigned to you in the active sprint.'}</div></div>
            )}
            <AnimatePresence initial={false}>
              {sec.rows.map((row) => {
                const r = rowIndex[row.key]
                const rowTotal = days.reduce((a, d) => a + (index.pending[d + '|' + row.key]?.seconds || 0), 0)
                return (
                  <motion.div key={row.key} className={'row ticket' + (sec.leave ? ' leave-row' : '')} layout="position"
                    initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: { duration: 0.15 } }}
                    transition={{ duration: 0.28, ease }}>
                    <div className="rowhead peekable" role="button" tabIndex={-1} title="Show ticket details"
                      onClick={(e) => { if (!e.target.closest('button')) openPeek(row) }}>
                      <div className="rh-line">
                        <KeyChip k={row.key} className={sec.leave ? 'leave' : ''} />
                        <Icon name="info" size={14} className="rh-info" />
                        {rowTotal > 0 && <Roll className="rh-total">{fmt(rowTotal)}</Roll>}
                        {row.pinned && (
                          <button className="icon-btn tiny rh-unpin" title="Remove row" onClick={() => unpin(row.key)}>
                            <Icon name="close" size={14} />
                          </button>
                        )}
                      </div>
                      <div className="rh-summary" title={row.summary}>{row.summary}</div>
                    </div>
                    {days.map((d, c) => (
                      <Cell key={d} r={r} c={c} date={d} leave={sec.leave}
                        draft={index.pending[d + '|' + row.key]}
                        logged={index.logged[d + '|' + row.key] || 0}
                        meeting={index.meet[d + '|' + row.key] || 0}
                        active={editor && editor.r === r && editor.c === c}
                        onOpen={openCell} />
                    ))}
                  </motion.div>
                )
              })}
            </AnimatePresence>
          </div>
        ))}

        <AddTicketRow />

        {/* totals */}
        <div className="row foot">
          <div className="rowhead"><div className="rh-title">Day total</div><div className="muted small">Target 8h per day</div></div>
          {days.map((d) => <DayFoot key={d} d={d} />)}
        </div>
      </div>

      <AnimatePresence>
        {editor && <CellEditor key={editor.date + editor.issue.key} ed={editor} onClose={closeEditor} />}
      </AnimatePresence>
      <AnimatePresence>
        {meetingEd && <MeetingEditor key={meetingEd.meeting.key} ed={meetingEd} onClose={() => setMeetingEd(null)} />}
      </AnimatePresence>
      <AnimatePresence>
        {dayMenu && <DayMenu key={dayMenu.d} {...dayMenu} onClose={() => setDayMenu(null)} />}
      </AnimatePresence>
    </div>
  )
}

// ------------------------------------------------------------------ headers / footers

function DayHeader({ d, onMenu }) {
  const today = useStore((s) => s.today)
  const logged = useStore((s) => loggedOn(s, d))
  const pending = useStore((s) => pendingOn(s, d))
  const leave = useStore((s) => isLeaveDay(s, d))
  const rem = TARGET - logged - pending
  const date = fromIso(d)
  return (
    <motion.div className={'dayhead' + (isWeekend(d) ? ' weekend' : '') + (leave ? ' leave' : '') + (d === today ? ' today' : '')}
      initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: 0.96 }} transition={{ duration: 0.3, ease }}>
      <div className="dh-top">
        <div className="dh-titles">
          <div className="dh-rel">{relativeLabel(d, today)}{leave && <span className="dh-tag"><Icon name="beach" size={12} /> Leave</span>}</div>
          <div className="dh-date">{date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}</div>
        </div>
        <motion.button className="icon-btn tiny" whileTap={{ scale: 0.88 }} title="Day actions"
          onClick={(e) => onMenu(e.currentTarget.getBoundingClientRect())}>
          <Icon name="more" size={18} />
        </motion.button>
      </div>
      <div className="dh-body">
        <Ring logged={logged} pending={pending} size={40} stroke={5} />
        <div className="dh-stats">
          <Roll className={'dh-rem ' + (rem < 0 ? 'over' : rem === 0 ? 'done' : '')}>
            {rem > 0 ? `${fmt(rem)} left` : rem === 0 ? '8h done ✓' : `+${fmt(-rem)} over`}
          </Roll>
          <div className="dh-split" title={`In Jira ${fmt(logged)} · New ${fmt(pending)}`}>
            {fmt(logged + pending)} of 8h{pending > 0 ? ` · +${fmt(pending)} new` : ''}
          </div>
        </div>
      </div>
    </motion.div>
  )
}

function DayFoot({ d }) {
  const logged = useStore((s) => loggedOn(s, d))
  const pending = useStore((s) => pendingOn(s, d))
  const total = logged + pending
  const over = total > TARGET
  const spring = { type: 'spring', stiffness: 110, damping: 20 }
  return (
    <div className="dayfoot">
      <div className="df-text">
        <Roll className={'strong ' + (over ? 'over' : total === TARGET ? 'done' : '')}>{fmt(total)}</Roll>
        <span className="muted"> / 8h</span>
      </div>
      <div className="bar thin">
        <motion.div className="bar-fill pending" animate={{ width: `${Math.min(1, total / TARGET) * 100}%` }} transition={spring}
          style={over ? { background: 'var(--over-soft)' } : undefined} />
        <motion.div className="bar-fill" animate={{ width: `${Math.min(1, logged / TARGET) * 100}%` }} transition={spring}
          style={over ? { background: 'var(--over)' } : total === TARGET ? { background: 'var(--done)' } : undefined} />
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ cells

function Cell({ r, c, date, leave, draft, logged, meeting, active, onOpen }) {
  const has = !!draft
  return (
    <motion.button
      data-cell="" data-r={r} data-c={c} data-date={date}
      className={'cell' + (has ? ' has' : '') + (draft?.error ? ' err' : '') + (leave ? ' leave' : '') + (active ? ' active' : '') + (isWeekend(date) ? ' weekend' : '')}
      onClick={(e) => onOpen(e.currentTarget)}
      whileTap={{ scale: 0.97 }}
      title={draft?.error || draft?.comment || ''}
    >
      {has && (
        <span className="cell-main">
          <Roll className="cell-val">{fmt(draft.seconds)}</Roll>
        </span>
      )}
      {!has && <span className="cell-add"><Icon name="plus" size={15} /></span>}
      {(logged > 0 || meeting > 0 || draft?.comment) && (
        <span className="cell-sub">
          {logged > 0 && <span className="tag jira">Jira {fmt(logged)}</span>}
          {meeting > 0 && <span className="tag mtg">Mtg {fmt(meeting)}</span>}
          {draft?.comment && <span className="tag note" title={draft.comment}>✎</span>}
        </span>
      )}
    </motion.button>
  )
}

function CellEditor({ ed, onClose }) {
  const { date, issue, leave } = ed
  const draft = useStore((s) => s.drafts.find((x) => x.date === date && x.issue.key === issue.key && !x.meetingKey))
  const remaining = useStore((s) => remainingOn(s, date))
  const selectedCount = useStore((s) => s.selected.length)
  const setCell = useStore((s) => s.setCell)
  const setCellComment = useStore((s) => s.setCellComment)
  const copyCellToSelected = useStore((s) => s.copyCellToSelected)
  const [text, setText] = useState(ed.initial ?? (draft ? fmt(draft.seconds) : ''))
  const [comment, setComment] = useState(draft?.comment ?? '')
  const inputRef = useRef(null)
  const parsed = parseDuration(text)
  const fill = Math.max(0, remaining + (draft?.seconds || 0))

  useEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.focus()
    if (ed.initial) el.setSelectionRange(el.value.length, el.value.length)
    else el.select()
  }, [ed.initial])

  const apply = (secs, move = true) => {
    setCell(date, issue, secs, comment)
    onClose(move)
  }

  return (
    <Popover anchor={ed.rect} onClose={() => onClose(false)} width={leave ? 340 : 380}>
      <div className="pop-head">
        <KeyChip k={issue.key} className={leave ? 'leave' : ''} />
        <div className="pop-title" title={issue.summary}>{issue.summary}</div>
        <div className="muted small">{fromIso(date).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })}</div>
      </div>

      {leave && (
        <div className="leave-opts">
          {[['Full day', 8 * 3600], ['Half day', 4 * 3600]].map(([label, s]) => (
            <motion.button key={label} className={'leave-opt' + (draft?.seconds === s ? ' on' : '')} whileTap={{ scale: 0.96 }}
              onClick={() => apply(draft?.seconds === s ? 0 : s)}>
              <strong>{label}</strong><span>{fmt(s)}</span>
            </motion.button>
          ))}
        </div>
      )}

      <div className="time-input">
        <Icon name="clock" size={17} className="muted" />
        <input ref={inputRef} value={text} onChange={(e) => setText(e.target.value)}
          placeholder={leave ? 'Custom: 2h, 6.5…' : 'Type 1.5, 1h 30m, 45…'}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              if (parsed) apply(parsed)
              else if (!text.trim()) apply(0)
            }
          }} />
        <AnimatePresence>
          {parsed != null && (
            <motion.span className="parsed" initial={{ opacity: 0, x: 6 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }}>
              = {fmt(parsed)}
            </motion.span>
          )}
        </AnimatePresence>
        <motion.button className="btn primary small" disabled={parsed == null} whileTap={{ scale: 0.95 }} onClick={() => apply(parsed)}>Set</motion.button>
      </div>

      {!leave && (
        <div className="presets-grid">
          {PRESETS.map((m) => (
            <motion.button key={m} className={'chip' + (draft?.seconds === m * 60 ? ' on' : '')} whileTap={{ scale: 0.92 }} onClick={() => apply(m * 60)}>
              {fmt(m * 60)}
            </motion.button>
          ))}
          <motion.button className="chip accent wide" disabled={fill <= 0} whileTap={{ scale: 0.95 }} onClick={() => apply(fill)}>
            Fill day {fill > 0 ? `· ${fmt(fill)}` : ''}
          </motion.button>
        </div>
      )}

      <textarea className="note" rows={2} value={comment} placeholder={leave ? 'Reason / description (optional)' : 'What did you work on? (optional)'}
        onChange={(e) => {
          setComment(e.target.value)
          if (draft) setCellComment(date, issue.key, e.target.value)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && parsed) {
            e.preventDefault()
            apply(parsed)
          }
        }} />

      {draft && (
        <div className="pop-foot">
          <button className="ghost-btn danger" onClick={() => apply(0, false)}><Icon name="trash" size={15} /> Clear</button>
          {selectedCount > 1 && (
            <button className="ghost-btn" onClick={() => { setCellComment(date, issue.key, comment); copyCellToSelected(date, issue.key); onClose(false) }}>
              <Icon name="copy" size={15} /> Same on all {selectedCount} days
            </button>
          )}
        </div>
      )}
    </Popover>
  )
}

// ------------------------------------------------------------------ meetings

function MeetingChip({ m, i, onOpen }) {
  const d = useStore((s) => s.drafts.find((x) => x.meetingKey === m.key))
  return (
    <motion.button layout className={'mchip' + (d ? ' on' : '')} whileTap={{ scale: 0.96 }}
      initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
      transition={{ delay: Math.min(i, 6) * 0.03, duration: 0.25, ease }}
      onClick={(e) => onOpen(e.currentTarget.getBoundingClientRect())} title={m.title}>
      <span className="mchip-icon">
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.span key={d ? 'on' : 'off'} initial={{ scale: 0.4, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.4, opacity: 0 }}>
            <Icon name={d ? 'check' : 'plus'} size={13} />
          </motion.span>
        </AnimatePresence>
      </span>
      <span className="mchip-body">
        <span className="mchip-title">{m.title}</span>
        <span className="mchip-meta">{fmtTime(m.start)} · {fmt(d?.seconds ?? m.minutes * 60)}{d ? ` · ${d.issue.key}` : ''}</span>
      </span>
    </motion.button>
  )
}

function MeetingEditor({ ed, onClose }) {
  const m = ed.meeting
  const existing = useStore((s) => s.drafts.find((x) => x.meetingKey === m.key))
  const sprintIssues = useStore((s) => s.sprintIssues)
  const pinned = useStore((s) => s.pinned)
  const memory = useStore((s) => s.meetingMemory)
  const setMeeting = useStore((s) => s.setMeeting)
  const removeMeeting = useStore((s) => s.removeMeeting)
  const searchAll = useStore((s) => s.searchAll)
  const last = memory[m.title.toLowerCase().trim().replace(/\s+/g, ' ')] || null

  const [issue, setIssue] = useState(existing?.issue ?? null)
  const [secs, setSecs] = useState(existing?.seconds ?? m.minutes * 60)
  const [text, setText] = useState(fmt(existing?.seconds ?? m.minutes * 60))
  const [comment, setComment] = useState(existing?.comment ?? m.title)
  const [q, setQ] = useState('')
  const [remote, setRemote] = useState([])
  const [searching, setSearching] = useState(false)

  useEffect(() => {
    const t = q.trim()
    setRemote([])
    if (t.length < 2) return
    setSearching(true)
    let live = true
    const h = setTimeout(async () => {
      const r = await searchAll(t)
      if (live) {
        setRemote(r)
        setSearching(false)
      }
    }, 350)
    return () => {
      live = false
      clearTimeout(h)
      setSearching(false)
    }
  }, [q, searchAll])

  const options = useMemo(() => {
    const seen = new Set()
    const base = [last, ...sprintIssues, ...pinned].filter((i) => i && !seen.has(i.key) && seen.add(i.key))
    const words = q.trim().toLowerCase().split(/\s+/).filter(Boolean)
    const local = words.length ? base.filter((i) => words.every((w) => `${i.key} ${i.summary}`.toLowerCase().includes(w))) : base
    return [...local, ...remote.filter((i) => !seen.has(i.key))]
  }, [last, sprintIssues, pinned, q, remote])

  return (
    <Popover anchor={ed.rect} onClose={onClose} width={400}>
      <div className="pop-head">
        <div className="pop-title strong">{m.title}</div>
        <div className="muted small">{fmtTime(m.start)} – {fmtTime(m.end)} · {fmt(m.minutes * 60)}</div>
      </div>

      <div className="search-box">
        <Icon name="search" size={16} className="muted" />
        <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="Choose a ticket, or search all of Jira" />
        {searching && <Spinner size={14} />}
      </div>
      <div className="ticket-list">
        {options.map((i) => (
          <motion.button key={i.key} layout="position" className={'ticket-opt' + (issue?.key === i.key ? ' on' : '')}
            whileTap={{ scale: 0.98 }} onClick={() => setIssue(i)}>
            <KeyChip k={i.key} />
            <span className="to-summary">{i.summary}</span>
            {last?.key === i.key && <span className="last-used">Last used</span>}
            {issue?.key === i.key && <Icon name="check" size={16} className="to-check" />}
          </motion.button>
        ))}
        {!options.length && <div className="muted small pad">{searching ? 'Searching…' : 'No tickets found.'}</div>}
      </div>

      <div className="presets-grid compact">
        {PRESETS.slice(0, 6).map((mm) => (
          <motion.button key={mm} className={'chip' + (secs === mm * 60 ? ' on' : '')} whileTap={{ scale: 0.92 }}
            onClick={() => { setSecs(mm * 60); setText(fmt(mm * 60)) }}>
            {fmt(mm * 60)}
          </motion.button>
        ))}
      </div>
      <div className="time-input">
        <Icon name="clock" size={17} className="muted" />
        <input value={text} onChange={(e) => { setText(e.target.value); const p = parseDuration(e.target.value); if (p) setSecs(p) }} placeholder="Custom time" />
        <span className="parsed">= {fmt(secs)}</span>
      </div>
      <textarea className="note" rows={2} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Description (optional)" />

      <div className="pop-foot">
        {existing && (
          <button className="ghost-btn danger" onClick={() => { removeMeeting(m); onClose() }}><Icon name="trash" size={15} /> Remove</button>
        )}
        <motion.button className="btn primary" style={{ marginLeft: 'auto' }} disabled={!issue || !secs} whileTap={{ scale: 0.96 }}
          onClick={() => { setMeeting(m, issue, secs, comment); onClose() }}>
          {existing ? 'Update' : issue ? `Add to ${issue.key}` : 'Pick a ticket'}
        </motion.button>
      </div>
    </Popover>
  )
}

// ------------------------------------------------------------------ day menu + add row

function DayMenu({ d, rect, onClose }) {
  const selected = useStore((s) => s.selected)
  const timeOff = useStore((s) => s.timeOff)
  const hasTickets = useStore((s) => s.drafts.some((x) => x.date === d && !x.meetingKey))
  const setLeave = useStore((s) => s.setLeave)
  const copyDay = useStore((s) => s.copyDayToSelected)
  const clickDay = useStore((s) => s.clickDay)
  const others = selected.length - 1
  const act = (fn) => () => { fn(); onClose() }
  return (
    <Popover anchor={rect} onClose={onClose} width={280} className="menu">
      {timeOff && (
        <>
          <button className="menu-item" onClick={act(() => setLeave([d], 8 * 3600, ''))}><Icon name="beach" size={16} /> Full-day leave</button>
          <button className="menu-item" onClick={act(() => setLeave([d], 4 * 3600, ''))}><Icon name="beach" size={16} /> Half-day leave</button>
          {others > 0 && (
            <button className="menu-item" onClick={act(() => setLeave(selected, 8 * 3600, ''))}><Icon name="beach" size={16} /> Full-day leave on all {selected.length} days</button>
          )}
          <div className="menu-sep" />
        </>
      )}
      {others > 0 && (
        <button className="menu-item" disabled={!hasTickets} onClick={act(() => copyDay(d))}>
          <Icon name="copy" size={16} /> Copy this day's tickets to the other {others} day{others === 1 ? '' : 's'}
        </button>
      )}
      {others > 0 && (
        <button className="menu-item" onClick={act(() => clickDay(d))}><Icon name="close" size={16} /> Hide this day</button>
      )}
      {!others && !timeOff && <div className="muted small pad">Select more days to copy entries across them.</div>}
    </Popover>
  )
}

function AddTicketRow() {
  const searchAll = useStore((s) => s.searchAll)
  const pin = useStore((s) => s.pin)
  const [q, setQ] = useState('')
  const [results, setResults] = useState([])
  const [busy, setBusy] = useState(false)
  const [rect, setRect] = useState(null)
  const inputRef = useRef(null)

  useEffect(() => {
    const t = q.trim()
    setResults([])
    if (t.length < 2) return
    setBusy(true)
    let live = true
    const h = setTimeout(async () => {
      const r = await searchAll(t)
      if (live) {
        setResults(r)
        setBusy(false)
        setRect(inputRef.current?.getBoundingClientRect() || null)
      }
    }, 350)
    return () => {
      live = false
      clearTimeout(h)
      setBusy(false)
    }
  }, [q, searchAll])

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === '/' && !['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) {
        e.preventDefault()
        inputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const choose = (i) => {
    pin(i)
    setQ('')
    setResults([])
  }

  return (
    <div className="row add-row">
      <div className="rowhead">
        <div className="search-box">
          <Icon name="search" size={16} className="muted" />
          <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="Add any Jira ticket…  ( / )"
            onFocus={() => q.trim().length >= 2 && setRect(inputRef.current?.getBoundingClientRect() || null)}
            onKeyDown={(e) => { if (e.key === 'Enter' && results[0]) choose(results[0]); if (e.key === 'Escape') setQ('') }} />
          {busy && <Spinner size={14} />}
        </div>
      </div>
      <AnimatePresence>
        {q.trim().length >= 2 && !busy && rect && (
          <Popover anchor={rect} onClose={() => setRect(null)} width={Math.max(380, rect.width)}>
            <div className="ticket-list tall">
              {results.map((i) => (
                <motion.button key={i.key} className="ticket-opt" whileTap={{ scale: 0.98 }} onClick={() => choose(i)}>
                  <KeyChip k={i.key} />
                  <span className="to-summary">{i.summary}</span>
                  <Icon name="plus" size={16} className="to-check" />
                </motion.button>
              ))}
              {!results.length && <div className="muted small pad">No tickets match “{q.trim()}”.</div>}
            </div>
          </Popover>
        )}
      </AnimatePresence>
    </div>
  )
}
