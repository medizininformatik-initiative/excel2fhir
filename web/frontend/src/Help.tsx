import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { tooltipPosition } from './tooltip-position'
import { Info } from 'lucide-react'
import type { Message } from './i18n'

type Translator = (key: string, params?: Message['params']) => string
export function Help({ text, t }: { text: string; t: Translator }) {
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [pinned, setPinned] = useState(false)
  const open = hovered || focused || pinned
  const id = useId()
  const anchor = useRef<HTMLSpanElement>(null)
  const tooltip = useRef<HTMLSpanElement>(null)
  const [position, setPosition] = useState<CSSProperties>({})
  useEffect(() => {
    if (!open) return
    const close = () => {
      setHovered(false)
      setFocused(false)
      setPinned(false)
    }
    const outside = (event: PointerEvent) => {
      const target = event.target
      if (target instanceof Node && !anchor.current?.contains(target) && !tooltip.current?.contains(target)) close()
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('pointerdown', outside, true)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', outside, true)
      document.removeEventListener('keydown', escape)
    }
  }, [open])
  useLayoutEffect(() => {
    if (!open) return
    const update = () => {
      if (!anchor.current || !tooltip.current) return
      const viewport = window.visualViewport
      const bounds = {
        left: viewport?.offsetLeft ?? 0, top: viewport?.offsetTop ?? 0,
        width: viewport?.width ?? document.documentElement.clientWidth,
        height: viewport?.height ?? document.documentElement.clientHeight
      }
      const origin = anchor.current.getBoundingClientRect()
      const size = tooltip.current.getBoundingClientRect()
      const next = tooltipPosition(origin, size, bounds)
      setPosition({ left: next.left, top: next.top,
        maxWidth: Math.max(0, bounds.width - 24), maxHeight: Math.max(0, Math.min(bounds.height * 0.6, bounds.height - 24)) })
    }
    update()
    const observer = new ResizeObserver(update)
    if (tooltip.current) observer.observe(tooltip.current)
    if (anchor.current) observer.observe(anchor.current)
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    window.visualViewport?.addEventListener('resize', update)
    window.visualViewport?.addEventListener('scroll', update)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', update)
      window.removeEventListener('scroll', update, true)
      window.visualViewport?.removeEventListener('resize', update)
      window.visualViewport?.removeEventListener('scroll', update)
    }
  }, [open, text])
  return (
    <span
      ref={anchor}
      className="help relative inline-flex"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <button
        type="button"
        aria-label={t('app.config.help')}
        aria-expanded={open}
        aria-controls={id}
        aria-describedby={open ? id : undefined}
        onFocus={event => setFocused(event.currentTarget.matches(':focus-visible'))}
        onBlur={() => {
          setFocused(false)
          setPinned(false)
        }}
        onClick={() => {
          if (pinned) {
            setPinned(false)
            setFocused(false)
            setHovered(false)
          } else {
            setPinned(true)
          }
        }}
        className="rounded p-1 text-slate-500 focus-visible:outline-2 focus-visible:outline-teal-700"
      >
        <Info size={16} />
      </button>
      {open && createPortal(
        <span
          id={id}
          ref={tooltip}
          style={position}
          role="tooltip"
          className="fixed left-0 top-0 z-50 w-96 max-w-[calc(100vw-3rem)] max-h-[60vh] overflow-y-auto whitespace-pre-line break-words rounded-lg bg-slate-900 p-3 text-sm font-normal leading-relaxed text-white shadow-lg"
        >
          {text}
        </span>, document.body
      )}
    </span>
  )
}
