import { useEffect, useState } from 'react'
import { translate, type Language } from './i18n'

export type ConfigurationSelection = { id: string; revision: number }
type Entry = ConfigurationSelection & { name: string }

export function SavedConfigurationSelection({ language, onChange }: {
  language: Language; onChange: (selection: ConfigurationSelection[]) => void
}) {
  const t = (key: Parameters<typeof translate>[1]) => translate(language, key)
  const [items, setItems] = useState<Entry[]>([])
  const [selected, setSelected] = useState<string[]>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('runConfigurations') ?? '[]')
      return Array.isArray(saved) ? saved.filter(id => typeof id === 'string') : []
    } catch { return [] }
  })
  const [failed, setFailed] = useState(false)
  const [loaded, setLoaded] = useState(false)
  useEffect(() => {
    let active = true
    let sequence = 0
    async function refresh() {
      const current = ++sequence
      try {
        const response = await fetch('/api/configurations')
        if (!response.ok) throw new Error()
        const next = await response.json()
        if (active && current === sequence) { setItems(next); setFailed(false); setLoaded(true) }
      } catch { if (active && current === sequence) setFailed(true) }
    }
    const visible = () => { if (!document.hidden) void refresh() }
    visible()
    const timer = window.setInterval(visible, 5000)
    window.addEventListener('focus', visible)
    document.addEventListener('visibilitychange', visible)
    return () => {
      active = false; window.clearInterval(timer)
      window.removeEventListener('focus', visible)
      document.removeEventListener('visibilitychange', visible)
    }
  }, [])
  const missing = selected.some(id => !items.some(item => item.id === id))
  useEffect(() => {
    localStorage.setItem('runConfigurations', JSON.stringify(selected))
    onChange(failed || missing ? [] : selected.flatMap(id => {
      const item = items.find(item => item.id === id)
      return item ? [{ id: item.id, revision: item.revision }] : []
    }))
  }, [selected, items, failed, missing, onChange])
  return <fieldset className="mt-5 rounded-xl border border-slate-200 p-4">
    <legend className="px-2 text-sm font-semibold">{t('app.runConfigurations')}</legend>
    <p className="mb-3 text-sm text-slate-600">{t('app.runConfigurationsHint')}</p>
    {failed && <p role="alert" className="mb-3 text-sm text-red-700">{t('app.saved.failed')}</p>}
    {!loaded && !failed && <p className="text-sm text-slate-500">{t('app.loadingConfigurations')}</p>}
    {loaded && items.length === 0 && <p className="text-sm text-slate-600">{t('app.noRunConfigurations')}</p>}
    <div className="flex flex-col gap-2">
      {items.map(item => <label key={item.id} className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={selected.includes(item.id)} className="accent-teal-700"
          onChange={e => setSelected(old => e.target.checked ? [...old, item.id] : old.filter(id => id !== item.id))}/>
        <span className="break-words">{item.name}</span>
      </label>)}
    </div>
    {loaded && missing && <p role="alert" className="mt-3 text-sm text-red-700">{t('app.runConfigurationsMissing')}
      <button type="button" className="ml-2 underline" onClick={() => setSelected(old => old.filter(id => items.some(item => item.id === id)))}>{t('app.removeMissingSelections')}</button>
    </p>}
  </fieldset>
}
