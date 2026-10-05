import { useEffect, useRef, useState } from 'react'
import { Upload } from 'lucide-react'
import { Button } from './components/ui/button'
import { translate, type Language } from './i18n'

type Input = { id: string; name: string; size: number; inspection: { patients?: number; bundlesWithoutPatient?: number; sheets: { name: string; rows: number }[] } }

export function InputSelection({ language, source, onChange, onBusy }: {
  language: Language; source: string; onChange: (source: string) => void; onBusy: (busy: boolean) => void
}) {
  const t = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) => translate(language, key, params)
  const [items, setItems] = useState<Input[]>([])
  const [uploading, setUploading] = useState(false)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const refreshSequence = useRef(0)
  const fileInput = useRef<HTMLInputElement>(null)
  useEffect(() => {
    let active = true
    async function refresh() {
      const sequence = ++refreshSequence.current
      try {
        const response = await fetch('/api/inputs')
        if (!response.ok) throw new Error()
        const next = await response.json()
        if (active && sequence === refreshSequence.current) { setItems(next); setFailed(false) }
      } catch { if (active && sequence === refreshSequence.current) setFailed(true) }
    }
    void refresh()
    const focus = () => { void refresh() }
    window.addEventListener('focus', focus)
    return () => { active = false; window.removeEventListener('focus', focus) }
  }, [])
  async function upload(file?: File) {
    if (!file) return
    setUploading(true); onBusy(true); setError(null)
    try {
      if (!/\.(xlsx|zip|json)$/i.test(file.name) || file.size > 64 * 1024 * 1024) {
        setError(t('app.uploadWorkbookLimit')); return
      }
      const response = await fetch('/api/inputs?filename=' + encodeURIComponent(file.name), {
        method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: file
      })
      if (!response.ok) {
        const result = await response.json().catch(() => null)
        throw new Error(typeof result?.detail === 'string' ? result.detail : t('app.uploadWorkbookFailed'))
      }
      const item: Input = await response.json()
      refreshSequence.current++
      setFailed(false)
      setItems(old => [item, ...old.filter(value => value.id !== item.id)])
      onChange(item.id)
    } catch (e) { setError(e instanceof Error ? e.message : t('app.uploadWorkbookFailed')) }
    finally { setUploading(false); onBusy(false); if (fileInput.current) fileInput.current.value = '' }
  }
  const selected = items.find(item => item.id === source)
  return <div className="min-w-0 w-full sm:w-80">
    <label className="flex flex-col gap-2 text-sm font-medium">{t('app.input')}
      <select className="rounded-lg border border-slate-300 p-2.5" disabled={uploading} value={source} onChange={e => onChange(e.target.value)}>
        <option value="synthea-generation">{t('app.generation.title')}</option><option value="starter">{t('app.starter')}</option><option value="demo">{t('app.demo')}</option>
        {items.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
      </select>
    </label>
    <Button className="mt-2" variant="outline" disabled={uploading} onClick={() => fileInput.current?.click()}><Upload size={16}/>{t(uploading ? 'app.inspectingWorkbook' : 'app.uploadWorkbook')}</Button>
    <input ref={fileInput} hidden type="file" accept=".xlsx,.zip,.json" aria-label={t('app.uploadWorkbook')} onChange={e => void upload(e.target.files?.[0])}/>
    <p className="mt-2 text-xs text-slate-500">{t('app.uploadWorkbookLimit')}</p>
    {failed && <p role="alert" className="mt-2 text-sm text-red-700">{t('app.inputListFailed')}</p>}
    {error && <p role="alert" className="mt-2 break-words text-sm text-red-700">{error}</p>}
    {selected && <details className="mt-2 text-sm text-slate-600">
      <summary className="cursor-pointer">{t('app.workbookStructureChecked', { count: selected.inspection.sheets.length })}</summary>
      <p className="mt-2 text-xs">{t(selected.inspection.patients === undefined ? 'app.workbookInspectionHint' : 'app.syntheaInspectionHint')}</p>
      {selected.inspection.patients !== undefined && <p className="mt-2">{t('app.syntheaInputPatients', { count: selected.inspection.patients, skipped: selected.inspection.bundlesWithoutPatient ?? 0 })}</p>}
      <ul className="mt-2 space-y-1">{selected.inspection.sheets.map(sheet => <li key={sheet.name} className="break-words">{sheet.name}: {t(selected.inspection.patients === undefined ? 'app.inputRows' : 'app.inputResources', { count: sheet.rows })}</li>)}</ul>
    </details>}
  </div>
}
