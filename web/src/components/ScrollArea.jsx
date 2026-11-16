import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Scroll container with the native scrollbar hidden. A slim pill shows where you are while
 * scrolling (or hovering), then fades away. Works on both axes.
 */
export default function ScrollArea({ className = '', hostClassName = '', innerRef, children, ...rest }) {
  const ref = useRef(null)
  const setRef = useCallback((el) => {
    ref.current = el
    if (typeof innerRef === 'function') innerRef(el)
    else if (innerRef) innerRef.current = el
  }, [innerRef])
  const [m, setM] = useState({ y: null, x: null })
  const [active, setActive] = useState(false)
  const idle = useRef()

  const measure = useCallback(() => {
    const el = ref.current
    if (!el) return
    const axis = (size, scroll, pos) => {
      if (scroll <= size + 1) return null
      const len = Math.max(28, (size / scroll) * size)
      return { len, at: (pos / (scroll - size)) * (size - len) }
    }
    setM({
      y: axis(el.clientHeight, el.scrollHeight, el.scrollTop),
      x: axis(el.clientWidth, el.scrollWidth, el.scrollLeft),
    })
  }, [])

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const onScroll = () => {
      measure()
      setActive(true)
      clearTimeout(idle.current)
      idle.current = setTimeout(() => setActive(false), 900)
    }
    el.addEventListener('scroll', onScroll, { passive: true })
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    const mo = new MutationObserver(measure)
    mo.observe(el, { childList: true, subtree: true })
    measure()
    return () => {
      el.removeEventListener('scroll', onScroll)
      ro.disconnect()
      mo.disconnect()
      clearTimeout(idle.current)
    }
  }, [measure])

  return (
    <div className={'scroll-host ' + hostClassName + (active ? ' scrolling' : '')}>
      <div ref={setRef} className={'no-scrollbar ' + className} {...rest}>{children}</div>
      {m.y && <span className="scroll-thumb y" style={{ height: m.y.len, transform: `translateY(${m.y.at}px)` }} />}
      {m.x && <span className="scroll-thumb x" style={{ width: m.x.len, transform: `translateX(${m.x.at}px)` }} />}
      {m.y && m.y.at > 2 && <span className="scroll-fade top" />}
      {m.y && m.y.at + m.y.len < (ref.current?.clientHeight || 0) - 2 && <span className="scroll-fade bottom" />}
    </div>
  )
}
