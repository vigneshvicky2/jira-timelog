import { useEffect, useState } from 'react'

const STROKES = ['M33 30.5 L32 77', 'M45.5 28 L45 79', 'M58 30 L58.5 77.5', 'M70.5 28.5 L70 79']
const SEEN = 'stint.splashSeen'
const TOTAL = 3950
const EXIT = 3450

function shouldPlay() {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return false
  try {
    if (sessionStorage.getItem(SEEN)) return false
    sessionStorage.setItem(SEEN, '1')
  } catch {
    /* storage blocked: play anyway */
  }
  return true
}

/**
 * Launch splash, once per browser session: the tally tile springs in, its strokes draw,
 * the orange slash swipes across, then "Stint" rises and the credit is revealed behind a
 * caret. Click or press any key to skip. Mirrors the Android app's splash.
 */
export default function Splash() {
  const [state, setState] = useState(() => (shouldPlay() ? 'play' : 'done'))

  useEffect(() => {
    if (state !== 'play') return
    const exit = setTimeout(() => setState('exit'), EXIT)
    const skip = () => setState('exit')
    window.addEventListener('keydown', skip, { once: true })
    return () => {
      clearTimeout(exit)
      window.removeEventListener('keydown', skip)
    }
  }, [state])

  useEffect(() => {
    if (state !== 'exit') return
    const t = setTimeout(() => setState('done'), TOTAL - EXIT)
    return () => clearTimeout(t)
  }, [state])

  if (state === 'done') return null
  return (
    <div className={`splash${state === 'exit' ? ' out' : ''}`} onClick={() => setState('exit')} role="presentation">
      <div className="splash-glow" />
      <div className="splash-stack">
        <div className="splash-lift">
          <svg className="splash-tile" viewBox="0 0 108 108" width="116" height="116" aria-label="Stint" role="img">
            <rect width="108" height="108" rx="30" fill="#1E1E24" />
            <g fill="none" stroke="#F4EFE6" strokeWidth="7.5" strokeLinecap="round">
              {STROKES.map((d, i) => (
                <path key={d} d={d} pathLength="1" className="splash-stroke" style={{ animationDelay: `${630 + 110 * i}ms` }} />
              ))}
            </g>
            <path d="M22 66 C42 58 62 47 86 36" pathLength="1" className="splash-slash" fill="none" stroke="#FF6B35" strokeWidth="8.5" strokeLinecap="round" />
          </svg>
        </div>
        <div className="splash-words">
          <div className="splash-slot">
            <div className="splash-name">Stint</div>
          </div>
          <div className="splash-credit">
            <span>Built by Vignesh Kodiswaran</span>
            <i className="splash-caret" />
          </div>
        </div>
      </div>
    </div>
  )
}
