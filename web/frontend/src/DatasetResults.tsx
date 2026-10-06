import { datasetLabel, type DatasetContext } from './dataset-label'
import { useEffect, useState } from 'react'
import { Download } from 'lucide-react'
import { Button } from './components/ui/button'
import { translate, type Language } from './i18n'

type Report = { validationErrors: number; validationWarnings: number; status: string | null; issues: number; omissions: number; failures: number; derivations: number; selections: number; reasons: Record<string, number> }
type Artifact = { id: string; path: string; size: number; report?: Report | null }
type Dataset = { id: string; name: string; error?: string; size?: number; inspection?: { patients: number; uniqueResources: number; resourceInstances: number; repeatedIds: number; missingIds: number; resourceCounts: Record<string, number>; inspectedFormats: string[] } }
type Manifest = { pending?: boolean; error?: string; datasets: Dataset[]; artifacts: Artifact[] }
export function DatasetResults({ jobId, state, language, context, onUpload }: { jobId: string; state: string; language: Language; context: DatasetContext; onUpload: (ids: string[]) => void }) {
  const t = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) => translate(language, key, params)
  const [manifest, setManifest] = useState<Manifest | null>(null)
  const [failed, setFailed] = useState(false)
  const [search, setSearch] = useState('')
  useEffect(() => {
    let active = true
    setManifest(null); setFailed(false); setSearch('')
    if (!['succeeded', 'failed'].includes(state)) return
    let timer: ReturnType<typeof setTimeout> | undefined
    async function refresh() {
      try {
        const response = await fetch(`/api/jobs/${jobId}/artifacts`)
        if (!response.ok) throw new Error()
        const value: Manifest = await response.json()
        if (!active) return
        setManifest(value); setFailed(false)
        if (value.pending) timer = setTimeout(() => void refresh(), 3000)
      } catch { if (active) { setFailed(true); timer = setTimeout(() => void refresh(), 5000) } }
    }
    void refresh()
    return () => { active = false; clearTimeout(timer) }
  }, [jobId, state])
  if (failed) return <p role="alert" className="mb-4 text-sm text-red-700">{t('app.datasets.failed')}</p>
  if (manifest?.error) return <p role="alert" className="mb-4 text-sm text-red-700">{manifest.error}</p>
  if (manifest?.pending) return <p className="mb-4 text-sm text-slate-500">{t('app.datasets.pending')}</p>
  if (!manifest || (manifest.datasets.length === 0 && manifest.artifacts.length === 0)) return null
  const artifacts = manifest.artifacts.filter(file => file.path.toLowerCase().includes(search.toLowerCase()))
  const download = (file: Artifact) => <a className="break-all text-teal-800 underline" href={`/api/jobs/${jobId}/artifacts/${file.id}`}>{file.path}</a>
  return <div className="mb-6 space-y-4">
    <h3 className="font-semibold">{t('app.datasets.title')}</h3>
    {state === 'succeeded' && manifest.datasets.some(dataset => dataset.size !== undefined && !dataset.error) && <Button variant="outline" onClick={() => onUpload(manifest.datasets.filter(dataset => dataset.size !== undefined && !dataset.error).map(dataset => dataset.id))}>{t('app.upload.open')}</Button>}
    {state === 'failed' && <p className="text-sm text-amber-800">{t('app.datasets.incomplete')}</p>}
    {manifest.datasets.map(dataset => <section key={dataset.id} className="rounded-xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3"><h4 className="font-semibold">{datasetLabel({ ...dataset, ...context }, language)}</h4>{dataset.size !== undefined && <Button variant="outline" asChild><a href={`/api/datasets/${dataset.id}/download`}><Download size={14}/>{t('app.datasets.download')}</a></Button>}</div>
      {dataset.error && <p role="alert" className="mt-2 text-sm text-red-700">{dataset.error}</p>}
      {dataset.inspection && <>
        <p className="mt-2 text-sm">{t('app.datasets.counts', { patients: dataset.inspection.patients, resources: dataset.inspection.uniqueResources })}</p>
        <p className="mt-2 text-xs text-slate-500">{t('app.datasets.countHint', { formats: dataset.inspection.inspectedFormats.join(', '), repeated: dataset.inspection.repeatedIds, missing: dataset.inspection.missingIds })}</p>
        <details className="mt-3 text-sm"><summary className="cursor-pointer">{t('app.datasets.resources')}</summary><table className="mt-2 w-full"><tbody>{Object.entries(dataset.inspection.resourceCounts).map(([type, count]) => <tr key={type} className="border-t"><th className="py-1 text-left font-normal">{type}</th><td className="text-right">{count}</td></tr>)}</tbody></table></details>
      </>}
    </section>)}
    <details className="text-sm"><summary className="cursor-pointer font-medium">{t('app.datasets.reports')}</summary>
      <div className="mt-3 space-y-3">{manifest.artifacts.filter(file => file.report).map(file => <div key={file.id} className="rounded-lg border p-3">
        {download(file)}
        <p className="mt-2">{file.report!.status ? `${file.report!.status} · ` : ''}{t('app.datasets.reportCounts', { issues: file.report!.issues, omissions: file.report!.omissions, failures: file.report!.failures, derivations: file.report!.derivations, selections: file.report!.selections, errors: file.report!.validationErrors, warnings: file.report!.validationWarnings })}</p>
        {Object.keys(file.report!.reasons).length > 0 && <ul className="mt-2 space-y-1 text-slate-600">{Object.entries(file.report!.reasons).map(([reason, count]) => <li key={reason}>{reason}: {count}</li>)}</ul>}
      </div>)}</div>
    </details>
    <details className="text-sm"><summary className="cursor-pointer font-medium">{t('app.datasets.files', { count: manifest.artifacts.length })}</summary>
      <p className="mt-2 text-xs text-slate-500">{t('app.datasets.fileHint')}</p>
      <input className="mt-3 w-full rounded-lg border border-slate-300 p-2" aria-label={t('app.datasets.search')} placeholder={t('app.datasets.search')} value={search} onChange={e => setSearch(e.target.value)}/>
      <ul className="mt-3 max-h-72 space-y-2 overflow-auto">{artifacts.slice(0, 200).map(file => <li key={file.id}>{download(file)} <span className="whitespace-nowrap text-xs text-slate-500">{(file.size / 1024).toFixed(1)} KiB</span></li>)}</ul>
      {artifacts.length > 200 && <p className="mt-2 text-xs text-slate-500">{t('app.datasets.moreFiles')}</p>}
    </details>
  </div>
}
