import { AnimatePresence, MotionConfig, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import Header from './components/Header.jsx'
import IssuePeek from './components/IssuePeek.jsx'
import ReviewPanel from './components/ReviewPanel.jsx'
import Settings from './components/Settings.jsx'
import Setup from './components/Setup.jsx'
import Sidebar from './components/Sidebar.jsx'
import Timesheet from './components/Timesheet.jsx'
import { Confetti, Toasts } from './components/ui.jsx'
import { reminderFor } from './lib/messages.js'
import { iso, isWeekend } from './lib/time.js'
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
      <Confetti />
    </MotionConfig>
  )
}

function Main() {
  const [settings, setSettings] = useState(false)
  const [review, setReview] = useState(false)
  const count = useStore((s) => s.drafts.length)

  useEffect(() => {
    const onKey = (e) => {
      const typing = ['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault()
        useStore.getState().submitAll()
      } else if (!typing && !e.ctrlKey && !e.metaKey && e.key.toLowerCase() === 'r' && !document.querySelector('.popover, .modal')) {
        useStore.getState().refresh()
        useStore.getState().loadCalendar()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <motion.div className="app" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.35 }}>
      <Header onSettings={() => setSettings(true)} onToggleReview={() => setReview((v) => !v)} reviewCount={count} />
      <Sidebar />
      <main className="main">
        <Timesheet onOpenSettings={() => setSettings(true)} />
      </main>
      <ReviewPanel open={review} onClose={() => setReview(false)} />
      <AnimatePresence>{review && <motion.div className="review-scrim" onClick={() => setReview(false)} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} />}</AnimatePresence>
      <AnimatePresence>{settings && <Settings onClose={() => setSettings(false)} />}</AnimatePresence>
      <PeekHost />
    </motion.div>
  )
}

function PeekHost() {
  const peek = useStore((s) => s.peek)
  return <AnimatePresence>{peek && <IssuePeek key={peek.key} issue={peek} />}</AnimatePresence>
}

/** Fires the daily reminder while a Timelog tab is open. */
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
