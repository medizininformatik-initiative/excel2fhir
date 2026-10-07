import { useEffect, useId, useRef, useState } from 'react'
import type { Message } from './i18n'
import { Help } from './Help'
import { insertPatternToken } from './configuration'

const tokens: { value: string; key: string; label?: string }[] = [
  { value: '{count}', key: 'app.config.token.count' },
  { value: '{count:08}', key: 'app.config.token.paddedCount' },
  { value: '{patientId}', key: 'app.config.token.patientId' },
  { value: '{resourceId}', key: 'app.config.token.resourceId' },
  { value: '{resourceType}', key: 'app.config.token.resourceType' },
  { value: '{iteration}', key: 'app.config.token.iteration' },
  { value: '{hash}', key: 'app.config.token.hash' },
  { value: '{{', label: '{', key: 'app.config.token.openBrace' },
  { value: '}}', label: '}', key: 'app.config.token.closeBrace' }
]
export function IdentifierPatternControl({
  value,
  onChange,
  t
}: {
  value: string
  onChange: (value: string) => void
  t: (key: string, params?: Message['params']) => string
}) {
  const tooltipId = useId()
  const [hoveredToken, setHoveredToken] = useState<string | null>(null)
  const [focusedToken, setFocusedToken] = useState<string | null>(null)
  const activeToken = tokens.find(
    (token) => token.value === (focusedToken ?? hoveredToken)
  )
  const input = useRef<HTMLInputElement>(null)
  const selection = useRef<{ start: number; end: number } | null>(null)
  const pendingCursor = useRef<number | null>(null)
  useEffect(() => {
    if (pendingCursor.current !== null && input.current) {
      input.current.focus()
      input.current.setSelectionRange(
        pendingCursor.current,
        pendingCursor.current
      )
      pendingCursor.current = null
    }
  }, [value])
  function insert(token: string) {
    const next = insertPatternToken(value, token, selection.current)
    selection.current = { start: next.cursor, end: next.cursor }
    pendingCursor.current = next.cursor
    onChange(next.value)
  }
  return (
    <div className="min-w-0">
      <div className="flex flex-col gap-2 text-sm">
        <span className="flex min-h-7 items-center gap-1">
          <label htmlFor={tooltipId + '-pattern'}>
            {t('identifier.pattern')}
          </label>
          <Help
            text={
              t('app.config.patternInstructions') +
              '\n' +
              t('app.config.literalBraces')
            }
            t={t}
          />
        </span>
        <input
          id={tooltipId + '-pattern'}
          ref={input}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onSelect={(e) => {
            selection.current = {
              start: e.currentTarget.selectionStart ?? value.length,
              end: e.currentTarget.selectionEnd ?? value.length
            }
          }}
          className="h-10 w-full min-w-0 rounded-lg border border-slate-300 p-2 font-mono"
        />
      </div>
      <div
        className="relative mt-2 flex flex-wrap gap-1.5"
        role="group"
        aria-label={t('app.config.patternTokens')}
      >
        {activeToken && (
          <span
            id={tooltipId}
            role="tooltip"
            className="pointer-events-none absolute left-0 top-full z-30 mt-2 w-64 max-w-full md:left-auto md:right-full md:top-0 md:mr-3 md:mt-0 rounded-lg bg-slate-900 p-3 text-xs leading-5 text-white shadow-lg"
          >
            <code className="font-semibold">{activeToken.value}</code>
            <span className="mt-1 block">{t(activeToken.key)}</span>
          </span>
        )}
        {tokens.map((token) => (
          <button
            key={token.value}
            type="button"
            onMouseEnter={() => setHoveredToken(token.value)}
            onMouseLeave={() => setHoveredToken(null)}
            onFocus={() => setFocusedToken(token.value)}
            onBlur={() => setFocusedToken(null)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                setHoveredToken(null)
                setFocusedToken(null)
              }
            }}
            aria-describedby={
              activeToken?.value === token.value ? tooltipId : undefined
            }
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => insert(token.value)}
            aria-label={t('app.config.insertToken', { token: token.value })}
            className="rounded-md border border-slate-200 bg-slate-50 px-2 py-1.5 text-left hover:border-teal-600 hover:bg-teal-50 focus-visible:outline-2 focus-visible:outline-teal-700"
          >
            <code className="block text-xs font-semibold text-teal-800">
              {token.label ?? token.value}
            </code>
          </button>
        ))}
      </div>
    </div>
  )
}
