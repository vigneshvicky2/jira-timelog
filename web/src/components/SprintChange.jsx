import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useRef } from 'react'
import { detail } from '../lib/sprint.js'
import { useStore } from '../store.js'
import { Icon } from './ui.jsx'

const OUT = [0.16, 1, 0.3, 1]
const INOUT = [0.65, 0, 0.35, 1]

/**
 * Shown once when a new sprint has started since the last visit. The old sprint's name is
 * crossed out with the logo's orange slash (one tally closed), it steps back, and the new
 * sprint rises in with confetti. Matches the Android app. Click, Esc or "Let's go" to close.
 */
export default function SprintChange() {
  const change = useStore((s) => s.sprintChange)
  const splashDone = useStore((s) => s.splashDone)
  const sprint = useStore((s) => s.sprint)
  return (
    <AnimatePresence>
      {change && splashDone && <Overlay key={change.to} from={change.from} to={change.to} sub={detail(sprint)} />}
    </AnimatePresence>
  )
}

function Overlay({ from, to, sub }) {
  const close = () => useStore.getState().dismissSprintChange()
  const btn = useRef(null)
  useEffect(() => {
    const confetti = setTimeout(() => useStore.setState((s) => ({ celebrate: s.celebrate + 1 })), 2400)
    const auto = setTimeout(close, 9500)
    const onKey = (e) => { if (e.key === 'Escape' || e.key === 'Enter') close() }
    window.addEventListener('keydown', onKey)
    return () => { clearTimeout(confetti); clearTimeout(auto); window.removeEventListener('keydown', onKey) }
  }, [])

  return (
    <motion.div
      className="sprint-change" role="dialog" aria-modal="true" aria-label={`Sprint ${from} completed. New sprint ${to} started.`}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.35 }}
      onClick={close}
    >
      <div className="sc-stack">
        {/* the sprint that just finished: rises in, gets struck through, steps back */}
        <motion.div
          className="sc-old"
          initial={{ opacity: 0, y: 24, scale: 1 }}
          animate={{ opacity: [0, 1, 1, 0.55], y: [24, 0, 0, -10], scale: [1, 1, 0.965, 0.88] }}
          transition={{ duration: 2.15, times: [0, 0.23, 0.6, 1], ease: OUT, delay: 0 }}
        >
          <motion.span className="sc-pill done" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.25, duration: 0.35 }}>
            <Icon name="check" size={14} /> Sprint complete
          </motion.span>
          <motion.span
            className="sc-old-name"
            initial={{ backgroundSize: '0% 100%' }}
            animate={{ backgroundSize: '100% 100%' }}
            transition={{ delay: 0.75, duration: 0.5, ease: INOUT }}
          >
            {from}
          </motion.span>
        </motion.div>

        {/* the sprint that just started */}
        <motion.span className="sc-pill new" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 2.0, duration: 0.45, ease: OUT }}>
          <Icon name="flag" size={14} /> New sprint started
        </motion.span>
        <div className="sc-slot">
          <motion.div className="sc-new-name" initial={{ y: '105%' }} animate={{ y: 0 }} transition={{ delay: 2.15, duration: 0.65, ease: OUT }}>
            {to}
          </motion.div>
        </div>
        {sub && (
          <motion.div className="sc-sub" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 2.7, duration: 0.4 }}>
            {sub}
          </motion.div>
        )}
        <motion.button
          ref={btn} className="sc-go" onClick={close}
          initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 2.9, duration: 0.5, ease: OUT }}
        >
          Let's go
        </motion.button>
      </div>
    </motion.div>
  )
}
