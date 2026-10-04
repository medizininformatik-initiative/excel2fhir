import { useId, useState } from 'react'
import { Info } from 'lucide-react'
import type { Message } from './i18n'

type Translator = (key: string, params?: Message['params']) => string
export function Help({ text, t }: { text: string; t: Translator }) {
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [pinned, setPinned] = useState(false)
  const open = hovered || focused || pinned
  const id = useId()
  return (
    <span
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
        onFocus={() => setFocused(true)}
        onBlur={() => {
          setFocused(false)
          setPinned(false)
        }}
        onClick={() => setPinned(!pinned)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setPinned(false)
            setFocused(false)
            setHovered(false)
          }
        }}
        className="rounded p-1 text-slate-500 focus-visible:outline-2 focus-visible:outline-teal-700"
      >
        <Info size={16} />
      </button>
      {open && (
        <span
          id={id}
          role="tooltip"
          className="absolute left-0 top-full z-30 w-96 max-w-[calc(100vw-3rem)] max-h-[60vh] overflow-y-auto whitespace-pre-line rounded-lg bg-slate-900 p-3 text-sm font-normal leading-relaxed text-white shadow-lg"
        >
          {text}
        </span>
      )}
    </span>
  )
}
