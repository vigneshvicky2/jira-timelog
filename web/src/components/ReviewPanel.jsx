import { AnimatePresence, motion } from 'motion/react'
import { useMemo, useState } from 'react'
import { fmt, fmtTime, fromIso } from '../lib/time.js'
import { useStore } from '../store.js'
import { Icon, KeyChip, Roll, Spinner } from './ui.jsx'

const ease = [0.2, 0.8, 0.2, 1]

export default function ReviewPanel({ open, onClose }) {
  const drafts = useStore((s) => s.drafts)
  const submitting = useStore((s) => s.submitting)
  const progress = useStore((s) => s.progress)
  const submitAll = useStore((s) => s.submitAll)
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

  return (
    <aside className={'review' + (open ? ' open' : '')}>
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
          <button className="icon-btn small review-close" onClick={onClose} title="Close"><Icon name="close" /></button>
        </div>
      </div>

      <div className="review-list">
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
                  <motion.div key={x.id} layout className={'rv-item' + (x.error ? ' err' : '')}
                    initial={{ opacity: 0, x: 18 }} animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -18, transition: { duration: 0.18 } }} transition={{ duration: 0.28, ease }}>
                    <div className="rv-main peekable" onClick={() => openPeek(x.issue)} title="Show ticket details">
                      <div className="rv-line">
                        <KeyChip k={x.issue.key} />
                        {x.meetingKey && <span className="rv-badge"><Icon name="calendar" size={12} /> {fmtTime(x.start)}</span>}
                      </div>
                      <div className="rv-summary">{x.issue.summary}</div>
                      {x.comment && <div className="rv-comment">{x.comment}</div>}
                      {x.error && <div className="rv-error">{x.error}</div>}
                    </div>
                    <Roll className="rv-time">{fmt(x.seconds)}</Roll>
                    <motion.button className="icon-btn tiny rv-del" whileTap={{ scale: 0.85 }} onClick={() => removeDraft(x.id)} title="Remove">
                      <Icon name="close" size={15} />
                    </motion.button>
                  </motion.div>
                ))}
              </AnimatePresence>
            </motion.section>
          ))}
        </AnimatePresence>
        {!count && (
          <motion.div className="rv-empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
            <div className="rv-empty-icon"><Icon name="sparkle" size={26} /></div>
            <div className="strong">All caught up</div>
            <div className="muted small">Add time in the grid and it'll show up here, ready to send to Jira in one go.</div>
          </motion.div>
        )}
        <LoggedSection />
      </div>

      <div className="review-foot">
        <AnimatePresence>
          {submitting && (
            <motion.div className="bar thin" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <motion.div className="bar-fill" animate={{ width: `${progress * 100}%` }} transition={{ duration: 0.25 }} />
            </motion.div>
          )}
        </AnimatePresence>
        <motion.button className={'btn primary big log-all' + (count && !submitting ? ' ready' : '')} disabled={!count || submitting}
          whileTap={{ scale: 0.97 }} onClick={submitAll}>
          {submitting ? <><Spinner size={18} /> Logging…</> : <><Icon name="send" size={18} /> Log all{count ? ` · ${fmt(total)}` : ''}</>}
        </motion.button>
        <div className="muted tiny center"><kbd>Ctrl</kbd> + <kbd>Enter</kbd></div>
      </div>
    </aside>
  )
}

function LoggedSection() {
  const selected = useStore((s) => s.selected)
  const worklogs = useStore((s) => s.worklogs)
  const deleteWorklog = useStore((s) => s.deleteWorklog)
  const [open, setOpen] = useState(false)
  const [confirm, setConfirm] = useState(null)
  const logs = selected.flatMap((d) => worklogs[d] || [])
  if (!logs.length) return null
  return (
    <div className="logged">
      <button className="logged-toggle" onClick={() => setOpen((v) => !v)}>
        <span>Already in Jira · {logs.length}</span>
        <motion.span animate={{ rotate: open ? 90 : 0 }}><Icon name="right" size={16} /></motion.span>
      </button>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease }} style={{ overflow: 'hidden' }}>
            {logs.map((w) => (
              <div key={w.id} className="lg-item">
                <div className="lg-main">
                  <div className="rv-line"><KeyChip k={w.key} /><span className="muted tiny">{fromIso(w.date).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })} · {fmtTime(w.start)}</span></div>
                  <div className="rv-summary">{w.comment || w.summary}</div>
                </div>
                <span className="rv-time">{fmt(w.seconds)}</span>
                <button className={'ghost-btn tiny' + (confirm === w.id ? ' danger solid' : '')} title="Delete from Jira"
                  onClick={() => {
                    if (confirm === w.id) { deleteWorklog(w); setConfirm(null) } else { setConfirm(w.id); setTimeout(() => setConfirm((c) => (c === w.id ? null : c)), 3000) }
                  }}>
                  {confirm === w.id ? 'Delete?' : <Icon name="trash" size={15} />}
                </button>
              </div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
