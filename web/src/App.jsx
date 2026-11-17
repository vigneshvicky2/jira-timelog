import { AnimatePresence, MotionConfig, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import Header from './components/Header.jsx'
import IssuePeek from './components/IssuePeek.jsx'
import ReviewPanel from './components/ReviewPanel.jsx'
import Settings from './components/Settings.jsx'
import Setup from './components/Setup.jsx'
import Shortcuts from './components/Shortcuts.jsx'
import Splash from './components/Splash.jsx'
import SprintChange from './components/SprintChange.jsx'
import Sidebar, { WeekStrip } from './components/Sidebar.jsx'
import Timesheet from './components/Timesheet.jsx'
import { Confetti, Icon, Spinner, Toasts } from './components/ui.jsx'
import { reminderFor } from './lib/messages.js'
import { fmt, iso, isWeekend } from './lib/time.js'
import { LS, lastFiredKey, useStore } from './store.js'

export default function App() {
  const connected = useStore((s) => !!s.config)
  useEffect(() => {
    useStore.getState().init()
  }, [])
  useReminders()
  return (
    <MotionConfig reducedMotion="user">
      <AnimatePresence mode="wait">{connected ? <Main key="main" /> : <Setup key="setup" />}</AnimatePresence>
      <Toasts />
      <SprintChange />
      <Confetti />
      <Splash />
    </MotionConfig>
  )
}

function Main() {
  const [settings, setSettings] = useState(false)
  const [review, setReview] = useState(false)
  const [calOpen, setCalOpen] = useState(false)
  const count = useStore((s) => s.drafts.length)
  const shortcuts = useStore((s) => s.shortcuts)

  useEffect(() => {
    const onKey = (e) => {
      const typing = ['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault()
        useStore.getState().submitAll()
      } else if (!typing && e.key === '?' && !document.querySelector('.popover, .modal')) {
        e.preventDefault()
        useStore.getState().setShortcuts(true)
      } else if (!typing && !e.ctrlKey && !e.metaKey && e.key.toLowerCase() === 'r' && !document.querySelector('.popover, .modal')) {
        useStore.getState().refresh()
        useStore.getState().loadCalendar()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <motion.div className={'app' + (calOpen ? ' cal-open' : '')} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.35 }}>
      <Header onSettings={() => setSettings(true)} onToggleReview={() => setReview((v) => !v)} reviewCount={count} />
      <WeekStrip onOpenCalendar={() => setCalOpen(true)} />
      <Sidebar onClose={() => setCalOpen(false)} />
      <AnimatePresence>{calOpen && <motion.div className="cal-scrim" onClick={() => setCalOpen(false)} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} />}</AnimatePresence>
      <main className="main">
        <Timesheet onOpenSettings={() => setSettings(true)} />
      </main>
      <BottomBar onOpen={() => setReview(true)} />
      <ReviewPanel open={review} onClose={() => setReview(false)} />
      <AnimatePresence>{review && <motion.div className="review-scrim" onClick={() => setReview(false)} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} />}</AnimatePresence>
      <AnimatePresence>{settings && <Settings onClose={() => setSettings(false)} />}</AnimatePresence>
      <AnimatePresence>{shortcuts && <Shortcuts />}</AnimatePresence>
      <PeekHost />
    </motion.div>
  )
}

/** Phone layout: pending count and a Log all button that opens the review panel. */
function BottomBar({ onOpen }) {
  const count = useStore((s) => s.drafts.length)
  const total = useStore((s) => s.drafts.reduce((a, d) => a + d.seconds, 0))
  const failed = useStore((s) => s.drafts.filter((d) => d.error).length)
  const submitting = useStore((s) => s.submitting)
  const done = useStore((s) => s.submitDone)
  const of = useStore((s) => s.submitTotal)
  return (
    <div className="bottombar">
      <div className="bb-text">
        <div className="strong">{count ? `${count} pending · ${fmt(total)}` : 'Nothing pending'}</div>
        <div className={'tiny ' + (failed ? 'bb-err' : 'muted')}>{failed ? `${failed} failed, tap to retry` : count ? 'Review before sending to Jira' : 'Tap a ticket cell to add time'}</div>
      </div>
      <motion.button className={'btn primary bb-btn' + (count ? '' : ' idle')} whileTap={{ scale: 0.96 }} onClick={onOpen} disabled={submitting}>
        {submitting ? <><Spinner size={16} /> {Math.min(done + 1, of)} of {of}</> : <><Icon name="send" size={16} /> Log all</>}
      </motion.button>
    </div>
  )
}

function PeekHost() {
  const peek = useStore((s) => s.peek)
  return <AnimatePresence>{peek && <IssuePeek key={peek.key} issue={peek} />}</AnimatePresence>
}

/** Fires the daily reminder while a Stint tab is open. */
function useReminders() {
  const { enabled, time, weekdaysOnly } = useStore((s) => s.settings.reminder)
  useEffect(() => {
    if (!enabled || !('Notification' in window)) return
    const check = () => {
      if (Notification.permission !== 'granted') return
      const now = new Date()
      const today = iso(now)
      if (weekdaysOnly && isWeekend(today)) return
      const [h, m] = time.split(':').map(Number)
      if (now.getHours() * 60 + now.getMinutes() < h * 60 + m) return
      if (LS.get(lastFiredKey, null) === today) return
      LS.set(lastFiredKey, today)
      const [title, body] = reminderFor(now)
      const n = new Notification(title, { body, icon: '/favicon.svg', tag: 'timelog-reminder' })
      n.onclick = () => window.focus()
    }
    check()
    const t = setInterval(check, 30000)
    return () => clearInterval(t)
  }, [enabled, time, weekdaysOnly])
}
