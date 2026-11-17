import ScrollArea from './ScrollArea.jsx'
import { AnimatePresence, motion } from 'motion/react'
import { useMemo, useState } from 'react'
import { TARGET, fmt, fmtTime, fromIso } from '../lib/time.js'
import { dayPhrase, lastWorkdayPlan, loggedOn, pendingOn, useStore } from '../store.js'
import { Count, Icon, KeyChip, Roll, Spinner, Tally } from './ui.jsx'

const ease = [0.2, 0.8, 0.2, 1]

export default function ReviewPanel({ open, onClose }) {
  const drafts = useStore((s) => s.drafts)
  const submitting = useStore((s) => s.submitting)
  const progress = useStore((s) => s.progress)
  const submitDone = useStore((s) => s.submitDone)
  const submitTotal = useStore((s) => s.submitTotal)
  const retrying = useStore((s) => s.retrying)
  const submitAll = useStore((s) => s.submitAll)
  const submitOne = useStore((s) => s.submitOne)
  const removeDraft = useStore((s) => s.removeDraft)
  const openPeek = useStore((s) => s.openPeek)
  const clearDrafts = useStore((s) => s.clearDrafts)
  const [confirmClear, setConfirmClear] = useState(false)

  const groups = useMemo(() => {
    const g = {}
    for (const d of drafts) (g[d.date] ||= []).push(d)
    return Object.entries(g)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, items]) => [date, items.sort((a, b) => a.start.localeCompare(b.start))])
  }, [drafts])
  const total = drafts.reduce((a, d) => a + d.seconds, 0)
  const count = drafts.length
  const failed = drafts.filter((d) => d.error).length

  return (
    <aside className={'review' + (open ? ' open' : '')} aria-label="Ready to log">
      <div className="review-head">
        <div>
          <h2>Ready to log</h2>
          <div className="muted small">
            <Roll>{count ? `${count} entr${count === 1 ? 'y' : 'ies'} · ${fmt(total)}` : 'Nothing yet'}</Roll>
          </div>
        </div>
        <div className="review-head-actions">
          {count > 0 && (
            <button className={'ghost-btn small' + (confirmClear ? ' danger' : '')}
              onClick={() => {
                if (confirmClear) { clearDrafts(); setConfirmClear(false) } else { setConfirmClear(true); setTimeout(() => setConfirmClear(false), 3000) }
              }}>
              {confirmClear ? 'Clear all?' : 'Clear'}
            </button>
          )}
          <button className="icon-btn small review-close" onClick={onClose} title="Close" aria-label="Close review"><Icon name="close" /></button>
        </div>
      </div>

      <ScrollArea hostClassName="review-host" className="review-list">
        <AnimatePresence initial={false}>
          {groups.map(([date, items]) => (
            <motion.section key={date} layout className="rv-group"
              initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, transition: { duration: 0.15 } }}
              transition={{ duration: 0.3, ease }}>
              <div className="rv-date">
                <span>{fromIso(date).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}</span>
                <Roll className="muted">{fmt(items.reduce((a, d) => a + d.seconds, 0))}</Roll>
              </div>
              <AnimatePresence initial={false}>
                {items.map((x) => (
                  <motion.div key={x.id} layout className={'rv-item' + (x.error ? ' err' : '') + (retrying[x.id] ? ' busy' : '')}
                    initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -18, transition: { duration: 0.18 } }} transition={{ duration: 0.28, ease }}>
                    <div className="rv-main peekable" onClick={() => openPeek(x.issue)} title="Show ticket details">
                      <div className="rv-line">
                        <KeyChip k={x.issue.key} />
                        {x.meetingKey && <span className="rv-badge"><Icon name="calendar" size={12} /> {fmtTime(x.start)}{x.auto ? ' · auto' : ''}</span>}
                      </div>
                      <div className="rv-summary">{x.issue.summary}</div>
                      {x.comment && <div className="rv-comment">{x.comment}</div>}
                    </div>
                    <Roll className="rv-time">{fmt(x.seconds)}</Roll>
                    <motion.button className="icon-btn tiny rv-del" whileTap={{ scale: 0.85 }} onClick={() => removeDraft(x.id)} title="Remove" aria-label={`Remove ${fmt(x.seconds)} on ${x.issue.key}`}>
                      <Icon name="close" size={15} />
                    </motion.button>
                    <AnimatePresence initial={false}>
                      {x.error && (
                        <motion.div className="rv-error" role="alert" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>
                          <Icon name="alert" size={15} />
                          <span>Not logged. {x.error}</span>
                          <motion.button className="retry-btn" whileTap={{ scale: 0.94 }} disabled={retrying[x.id] || submitting} onClick={() => submitOne(x.id)}>
                            {retrying[x.id] ? <><Spinner size={13} /> Retrying…</> : <><Icon name="refresh" size={14} /> Retry</>}
                          </motion.button>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </motion.div>
                ))}
              </AnimatePresence>
            </motion.section>
          ))}
        </AnimatePresence>
        {!count && <EmptyState />}
        <LoggedSection />
      </ScrollArea>

      <div className="review-foot">
        <AnimatePresence>
          {submitting && (
            <motion.div className="bar thin" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <motion.div className="bar-fill" animate={{ width: `${progress * 100}%` }} transition={{ duration: 0.25 }} />
            </motion.div>
          )}
        </AnimatePresence>
        <motion.button className={'btn primary big log-all' + (count && !submitting ? ' ready' : '') + (!count ? ' idle' : '')} disabled={!count || submitting}
          whileTap={{ scale: 0.97 }} onClick={submitAll} aria-live="polite">
          {submitting ? (
            <><Spinner size={18} /> Logging {Math.min(submitDone + 1, submitTotal)} of {submitTotal}…</>
          ) : count ? (
            <><Icon name="send" size={18} /> {failed ? `Retry all · ${fmt(total)}` : <>Log all · <Count value={total} /></>}</>
          ) : (
            <><Icon name="check" size={18} /> Nothing to log yet</>
          )}
        </motion.button>
        <div className="muted tiny center"><kbd>Ctrl</kbd> + <kbd>Enter</kbd></div>
      </div>
    </aside>
  )
}

/** Empty review: a tally of today's progress plus a one-tap "repeat last workday" suggestion. */
function EmptyState() {
  const today = useStore((s) => s.today)
  const selected = useStore((s) => s.selected)
  const worklogs = useStore((s) => s.worklogs)
  const drafts = useStore((s) => s.drafts)
  const timeOff = useStore((s) => s.timeOff)
  const doneToday = useStore((s) => loggedOn(s, s.today) + pendingOn(s, s.today))
  const targetFull = useStore((s) => loggedOn(s, selected[selected.length - 1] || s.today) >= TARGET)
  const copyLastWorkday = useStore((s) => s.copyLastWorkday)
  const target = selected[selected.length - 1] || today
  const plan = useMemo(
    () => lastWorkdayPlan({ worklogs, drafts, timeOff }, target),
    [worklogs, drafts, timeOff, target],
  )
  const frac = doneToday / TARGET
  return (
    <motion.div className="rv-empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
      <Tally fraction={frac} size={64} />
      <div className="strong">{frac >= 1 ? "Today's 8h is in" : doneToday ? `${fmt(doneToday)} of 8h today` : 'Nothing pending'}</div>
      <div className="muted small">
        {frac >= 1 ? 'Nice work. Nothing left to send.' : "Add time in the grid and it'll show up here, ready to send to Jira in one go."}
      </div>
      {plan && plan.items.length > 0 && !targetFull && (
        <motion.button className="suggest" whileTap={{ scale: 0.97 }} onClick={() => copyLastWorkday(selected)}
          initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
          <Icon name="copy" size={16} />
          <span>
            {dayPhrase(plan.from, target)} you logged <strong>{fmt(plan.total)}</strong> across {plan.count} ticket{plan.count === 1 ? '' : 's'}. Repeat it
            {selected.length > 1 ? ` on ${selected.length} days` : ''}?
          </span>
        </motion.button>
      )}
    </motion.div>
  )
}

function LoggedSection() {
  const selected = useStore((s) => s.selected)
  const worklogs = useStore((s) => s.worklogs)
  const deleteWorklog = useStore((s) => s.deleteWorklog)
  const [open, setOpen] = useState(false)
  const logs = selected.flatMap((d) => worklogs[d] || [])
  if (!logs.length) return null
  return (
    <div className="logged">
      <button className="logged-toggle" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <span>Already in Jira · {logs.length}</span>
        <motion.span animate={{ rotate: open ? 90 : 0 }}><Icon name="right" size={16} /></motion.span>
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease }} style={{ overflow: 'hidden' }}>
            <AnimatePresence initial={false}>
            {logs.map((w) => (
              <motion.div key={w.id} className="lg-item" layout initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0, x: -24, transition: { duration: 0.2 } }} transition={{ duration: 0.25, ease }}>
                <div className="lg-main">
                  <div className="rv-line"><KeyChip k={w.key} /><span className="muted tiny">{fromIso(w.date).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })} · {fmtTime(w.start)}</span></div>
                  <div className="rv-summary">{w.comment || w.summary}</div>
                </div>
                <span className="rv-time">{fmt(w.seconds)}</span>
                <button className="ghost-btn tiny" title="Delete from Jira (you can undo)" aria-label={`Delete ${fmt(w.seconds)} on ${w.key} from Jira`}
                  onClick={() => deleteWorklog(w)}>
                  <Icon name="trash" size={15} />
                </button>
              </motion.div>
            ))}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
