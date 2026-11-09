import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { TARGET } from '../lib/time.js'
import { useStore } from '../store.js'

// ---------- icons (stroke style) ----------
const P = {
  plus: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6L6 18',
  check: 'M5 12.5l4.5 4.5L19 7',
  left: 'M15 18l-6-6 6-6',
  right: 'M9 6l6 6-6 6',
  search: 'M20 20l-3.6-3.6M18 11a7 7 0 11-14 0 7 7 0 0114 0z',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 002 2h6a2 2 0 002-2l1-12M9 7V4h6v3',
  refresh: 'M20 12a8 8 0 11-2.34-5.66M20 4v5h-5',
  sliders: 'M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1M15 4v4M9 10v4M17 16v4',
  calendar: 'M4 9h16M8 3v4M16 3v4M6 5h12a2 2 0 012 2v12a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2z',
  clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  beach: 'M12 4a8 8 0 018 8H4a8 8 0 018-8zM12 12v8M9 20h6',
  copy: 'M9 9h10a1 1 0 011 1v10a1 1 0 01-1 1H9a1 1 0 01-1-1V10a1 1 0 011-1zM5 15V5a1 1 0 011-1h10',
  send: 'M4 12l16-8-6 16-3-7-7-1z',
  more: 'M12 6h.01M12 12h.01M12 18h.01',
  bolt: 'M13 3L5 14h6l-1 7 8-11h-6l1-7z',
  bell: 'M6 16v-5a6 6 0 1112 0v5l2 2H4l2-2zM10 21a2 2 0 004 0',
  logout: 'M15 4h3a2 2 0 012 2v12a2 2 0 01-2 2h-3M10 17l5-5-5-5M15 12H3',
  link: 'M10 14a4 4 0 005.66 0l3-3a4 4 0 10-5.66-5.66l-1 1M14 10a4 4 0 00-5.66 0l-3 3a4 4 0 105.66 5.66l1-1',
  keyboard: 'M4 6h16a2 2 0 012 2v8a2 2 0 01-2 2H4a2 2 0 01-2-2V8a2 2 0 012-2zM7 10h.01M11 10h.01M15 10h.01M8 14h8',
  sparkle: 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z',
  edit: 'M4 20h4l10-10-4-4L4 16v4zM14 6l4 4',
  panel: 'M4 5h16v14H4zM15 5v14',
  info: 'M12 16v-4M12 8h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  external: 'M14 4h6v6M20 4l-9 9M18 14v4a2 2 0 01-2 2H6a2 2 0 01-2-2V8a2 2 0 012-2h4',
  person: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0',
  flag: 'M5 21V4M5 4h11l-2 4 2 4H5',
}
export function Icon({ name, size = 18, className, style }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" className={className} style={style} aria-hidden="true">
      <path d={P[name]} />
    </svg>
  )
}

// ---------- rolling text ----------
export function Roll({ children, className = '' }) {
  return (
    <span className={'roll ' + className}>
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={String(children)}
          initial={{ y: '55%', opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: '-55%', opacity: 0 }}
          transition={{ duration: 0.26, ease: [0.2, 0.8, 0.2, 1] }}
          style={{ display: 'inline-block', whiteSpace: 'nowrap' }}
        >
          {children}
        </motion.span>
      </AnimatePresence>
    </span>
  )
}

// ---------- 8h ring ----------
export function Ring({ logged, pending, size = 60, stroke = 7, children }) {
  const total = logged + pending
  const over = total > TARGET
  const done = total === TARGET
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const lf = Math.min(logged / TARGET, 1)
  const tf = Math.min(total / TARGET, 1)
  const main = over ? 'var(--over)' : done ? 'var(--done)' : 'var(--primary)'
  const spring = { type: 'spring', stiffness: 90, damping: 18 }
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--track)" strokeWidth={stroke} />
        <motion.circle
          cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} transform={`rotate(-90 ${size / 2} ${size / 2})`}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c * (1 - tf), stroke: over ? 'var(--over-soft)' : 'var(--tertiary)' }}
          transition={spring}
          style={{ opacity: tf > 0 ? 1 : 0 }}
        />
        <motion.circle
          cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} transform={`rotate(-90 ${size / 2} ${size / 2})`}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c * (1 - lf), stroke: main }}
          transition={spring}
          style={{ opacity: lf > 0 ? 1 : 0 }}
        />
      </svg>
      <div className="ring-center">{children}</div>
    </div>
  )
}

export const KeyChip = ({ k, className = '' }) => <span className={'keychip ' + className}>{k}</span>

export const Spinner = ({ size = 16 }) => <span className="spinner" style={{ width: size, height: size }} />

// ---------- popover anchored to an element rect ----------
export function Popover({ anchor, onClose, width = 340, children, className = '' }) {
  const ref = useRef(null)
  const [pos, setPos] = useState(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el || !anchor) return
    const place = () => {
      const h = el.offsetHeight
      let top = anchor.bottom + 8
      if (top + h > window.innerHeight - 12) top = Math.max(12, anchor.top - h - 8)
      const left = Math.min(Math.max(12, anchor.left + anchor.width / 2 - width / 2), window.innerWidth - width - 12)
      setPos({ top, left, origin: top < anchor.top ? 'bottom center' : 'top center' })
    }
    place()
    const ro = new ResizeObserver(place)
    ro.observe(el)
    return () => ro.disconnect()
  }, [anchor, width])
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    const onDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose()
    }
    window.addEventListener('keydown', onKey)
    const t = setTimeout(() => window.addEventListener('mousedown', onDown), 0)
    return () => {
      clearTimeout(t)
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('mousedown', onDown)
    }
  }, [onClose])
  return createPortal(
    <motion.div
      ref={ref}
      className={'popover ' + className}
      style={{ width, top: pos?.top ?? -9999, left: pos?.left ?? -9999, transformOrigin: pos?.origin }}
      initial={{ opacity: 0, scale: 0.95, y: -6 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.97, y: -4, transition: { duration: 0.12 } }}
      transition={{ type: 'spring', stiffness: 520, damping: 36 }}
    >
      {children}
    </motion.div>,
    document.body,
  )
}

// ---------- modal ----------
export function Modal({ onClose, children, width = 560 }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return createPortal(
    <motion.div className="scrim" onMouseDown={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.18 }}>
      <motion.div className="modal" style={{ width }} onMouseDown={(e) => e.stopPropagation()}
        initial={{ opacity: 0, y: 24, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 12, scale: 0.98 }} transition={{ type: 'spring', stiffness: 420, damping: 34 }}>
        {children}
      </motion.div>
    </motion.div>,
    document.body,
  )
}

// ---------- toasts ----------
export function Toasts() {
  const toasts = useStore((s) => s.toasts)
  return createPortal(
    <div className="toasts">
      <AnimatePresence initial={false}>
        {toasts.map((t) => (
          <motion.div key={t.id} layout className={'toast ' + t.kind}
            initial={{ opacity: 0, y: 16, scale: 0.96 }} animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 0.96, transition: { duration: 0.15 } }}
            transition={{ type: 'spring', stiffness: 500, damping: 34 }}>
            {t.kind === 'success' && <Icon name="check" size={16} />}
            {t.text}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>,
    document.body,
  )
}

// ---------- confetti ----------
export function Confetti() {
  const celebrate = useStore((s) => s.celebrate)
  const ref = useRef(null)
  useEffect(() => {
    if (!celebrate) return
    const canvas = ref.current
    const ctx = canvas.getContext('2d')
    const dpr = window.devicePixelRatio || 1
    canvas.width = innerWidth * dpr
    canvas.height = innerHeight * dpr
    ctx.scale(dpr, dpr)
    const colors = ['#6C5CE7', '#00B4D8', '#FF8A3D', '#2EBD85', '#FFD60A', '#FF5D8F']
    const parts = Array.from({ length: 220 }, () => {
      const a = Math.random() * Math.PI * 2
      const v = 4 + Math.random() * 10
      return {
        x: innerWidth / 2, y: innerHeight * 0.4, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 7,
        r: Math.random() * 360, vr: (Math.random() - 0.5) * 18, w: 7 + Math.random() * 8, h: 4 + Math.random() * 6,
        c: colors[(Math.random() * colors.length) | 0],
      }
    })
    let raf
    const start = performance.now()
    const tick = (t) => {
      const el = (t - start) / 1000
      ctx.clearRect(0, 0, innerWidth, innerHeight)
      for (const p of parts) {
        p.vy += 0.32
        p.vx *= 0.992
        p.x += p.vx
        p.y += p.vy
        p.r += p.vr
        ctx.save()
        ctx.globalAlpha = Math.max(0, 1 - Math.max(0, el - 2) / 1)
        ctx.translate(p.x, p.y)
        ctx.rotate((p.r * Math.PI) / 180)
        ctx.fillStyle = p.c
        ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h)
        ctx.restore()
      }
      if (el < 3) raf = requestAnimationFrame(tick)
      else ctx.clearRect(0, 0, innerWidth, innerHeight)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [celebrate])
  return createPortal(<canvas ref={ref} className="confetti" />, document.body)
}
