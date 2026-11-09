import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { reminderFor } from '../lib/messages.js'
import { DEMO, useStore } from '../store.js'
import { Icon, Modal, Spinner } from './ui.jsx'

export default function Settings({ onClose }) {
  const config = useStore((s) => s.config)
  const me = useStore((s) => s.me)
  const settings = useStore((s) => s.settings)
  const saveSettings = useStore((s) => s.saveSettings)
  const loadCalendar = useStore((s) => s.loadCalendar)
  const calendarState = useStore((s) => s.calendarState)
  const calendarError = useStore((s) => s.calendarError)
  const meetingCount = useStore((s) => Object.values(s.meetings).reduce((a, l) => a + l.length, 0))
  const disconnect = useStore((s) => s.disconnect)
  const toast = useStore((s) => s.toast)
  const [urls, setUrls] = useState(settings.icsUrls.length ? settings.icsUrls : [''])
  const [confirm, setConfirm] = useState(false)
  const rem = settings.reminder

  const saveCalendars = async () => {
    saveSettings({ icsUrls: urls.map((u) => u.trim()).filter(Boolean) })
    await loadCalendar()
  }

  const setReminder = async (patch) => {
    if (patch.enabled) {
      if (!('Notification' in window)) return toast('This browser does not support notifications.', 'error')
      const perm = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission()
      if (perm !== 'granted') return toast('Allow notifications for this site to get reminders.', 'error')
    }
    saveSettings({ reminder: { ...rem, ...patch } })
  }

  const test = () => {
    if (!('Notification' in window) || Notification.permission !== 'granted') return toast('Turn on the reminder first.', 'error')
    const [title, body] = reminderFor(new Date(Date.now() + Math.floor(Math.random() * 30) * 86400000))
    new Notification(title, { body, icon: '/favicon.svg' })
  }

  return (
    <Modal onClose={onClose} width={600}>
      <div className="modal-head">
        <h2>Settings</h2>
        <button className="icon-btn small" onClick={onClose}><Icon name="close" /></button>
      </div>

      <section className="set-sec">
        <div className="set-title">Account</div>
        <div className="account">
          <div className="avatar big">{(me?.displayName || '?').slice(0, 1)}</div>
          <div>
            <div className="strong">{me?.displayName || '—'}</div>
            <div className="muted small">{config?.email} · {config?.site}</div>
          </div>
          <button className={'ghost-btn danger' + (confirm ? ' solid' : '')} style={{ marginLeft: 'auto' }}
            onClick={() => {
              if (DEMO) { location.href = '/'; return }
              if (confirm) { disconnect(); onClose() } else setConfirm(true)
            }}>
            <Icon name="logout" size={15} /> {DEMO ? 'Leave demo' : confirm ? 'Remove key?' : 'Disconnect'}
          </button>
        </div>
      </section>

      <section className="set-sec">
        <div className="set-title">Calendar meetings</div>
        <p className="muted small">
          In Google Calendar open <strong>Settings → your calendar → Integrate calendar</strong> and copy
          the <strong>Secret address in iCal format</strong> (not the public one). Outlook “ICS” publish links work too. No Google sign-in needed.
        </p>
        {DEMO ? (
          <div className="muted small">The demo uses sample meetings.</div>
        ) : (
          <>
            <AnimatePresence initial={false}>
              {urls.map((u, i) => (
                <motion.div key={i} className="url-row" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>
                  <div className="search-box">
                    <Icon name="link" size={16} className="muted" />
                    <input value={u} onChange={(e) => setUrls(urls.map((x, j) => (j === i ? e.target.value : x)))}
                      placeholder="https://calendar.google.com/calendar/ical/…/basic.ics" />
                  </div>
                  {urls.length > 1 && (
                    <button className="icon-btn small" onClick={() => setUrls(urls.filter((_, j) => j !== i))}><Icon name="close" size={16} /></button>
                  )}
                </motion.div>
              ))}
            </AnimatePresence>
            {urls.some(isPublicGoogle) && (
              <motion.div className="cal-warn" initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }}>
                This looks like the <strong>public</strong> address. For your own calendar use the <strong>Secret address in iCal format</strong>; it contains <code>/private-…/</code>.
              </motion.div>
            )}
            <div className="row-actions">
              <button className="ghost-btn small" onClick={() => setUrls([...urls, ''])}><Icon name="plus" size={15} /> Add another calendar</button>
              <motion.button className="btn primary small" whileTap={{ scale: 0.96 }} onClick={saveCalendars} disabled={calendarState === 'loading'}>
                {calendarState === 'loading' ? <Spinner size={14} /> : 'Save & load'}
              </motion.button>
            </div>
            <div className={'cal-status ' + calendarState}>
              {calendarState === 'ok' && <><Icon name="check" size={15} /> Connected · {meetingCount} meetings around the selected days</>}
              {calendarState === 'error' && <>{calendarError}</>}
              {calendarState === 'none' && <>No calendar connected</>}
            </div>
          </>
        )}
      </section>

      <section className="set-sec">
        <div className="set-title">Daily reminder</div>
        <label className="switch-row">
          <div>
            <div className="strong"><Icon name="bell" size={15} /> Remind me to log time</div>
            <div className="muted small">A browser notification with a different fun message each day (while Timelog is open in a tab).</div>
          </div>
          <Switch on={rem.enabled} onChange={(v) => setReminder({ enabled: v })} />
        </label>
        <AnimatePresence initial={false}>
          {rem.enabled && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} style={{ overflow: 'hidden' }}>
              <div className="rem-grid">
                <label className="field inline">
                  <span>Time</span>
                  <input type="time" value={rem.time} onChange={(e) => setReminder({ time: e.target.value })} />
                </label>
                <label className="switch-row compact">
                  <span>Weekdays only</span>
                  <Switch on={rem.weekdaysOnly} onChange={(v) => setReminder({ weekdaysOnly: v })} />
                </label>
                <button className="ghost-btn small" onClick={test}>Send a test</button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </section>
    </Modal>
  )
}

// Google "public" iCal links only work for calendars shared publicly (holidays etc. are fine).
const isPublicGoogle = (u) => /calendar\.google\.com\/calendar\/ical\/[^/]+\/public\//.test(u) && !/group\.v\.calendar\.google\.com/.test(decodeURIComponent(u))

function Switch({ on, onChange }) {
  return (
    <button type="button" role="switch" aria-checked={on} className={'switch' + (on ? ' on' : '')} onClick={() => onChange(!on)}>
      <motion.span className="knob" layout transition={{ type: 'spring', stiffness: 700, damping: 35 }} />
    </button>
  )
}
