import { resolveGenerationSeeds } from './generation-seeds'
import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Download, Play, Square, Activity } from 'lucide-react'
import { Button } from './components/ui/button'
import { errorMessage, initialLanguage, InterfaceError, translate, type Language, type Message, type TextKey } from './i18n'
import './index.css'
import { ListExpansion } from './ListExpansion'
import { ServiceLinks } from './ServiceLinks'
import { FhirUploads } from './FhirUploads'
import { Help } from './Help'
import { DatasetResults } from './DatasetResults'
import { GenerationSettings, type Generation } from './GenerationSettings'
import { InputSelection } from './InputSelection'
import { ConfigurationEditor } from './ConfigurationEditor'
import { problems, type Configuration } from './configuration'
import { exportPropertiesConfiguration, importRunProperties } from './configuration-properties'

type Job = { duration_seconds?: number | null; failure?: { kind: 'memory' | 'killed'; evidence: string }; dataset_name?: string; id: string; state: string; created: number; cancel: number; exit_code: number | null; download_available: boolean; source?: string; source_name?: string; configuration?: { id: string; name: string; revision: number | null }; batch_id?: string | null; repeated_from?: string | null; generation?: Generation; generation_result?: { generatedPatients: number; importedPatients: number; failedPatients: number } }
async function fetchResponse(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try { response = await fetch('/api' + path, init) }
  catch { throw new InterfaceError('app.error.network') }
  if (!response.ok) {
    if (response.status === 422 || response.status === 409) {
      const body = await response.json().catch(() => null)
      if (typeof body?.detail === 'string') throw new InterfaceError('app.error.configuration', { detail: body.detail })
    }
    const key = response.status === 404 ? 'app.error.notFound'
      : response.status === 422 ? 'app.error.invalidInput'
      : response.status === 403 ? 'app.error.crossOrigin' : 'app.error.http'
    throw new InterfaceError(key, { status: response.status })
  }
  return response
}
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetchResponse(path, init)
  try { return await response.json() }
  catch { throw new InterfaceError('app.error.unexpected') }
}
async function submitRuns<T>(path: string, payload: { generation?: Generation | null; [key: string]: unknown }): Promise<T> {
  const key = 'pendingSubmission:' + path
  const signature = JSON.stringify(payload)
  let previous: { signature: string; id: string; timestamp?: number } | null = null
  try { previous = JSON.parse(sessionStorage.getItem(key) ?? 'null') } catch { /* Start a fresh submission. */ }
  const id = previous?.signature === signature ? previous.id : crypto.randomUUID()
  const timestamp = previous?.signature === signature ? previous.timestamp ?? Date.now() : Date.now()
  const resolved = payload.generation ? { ...payload, generation: resolveGenerationSeeds(payload.generation, timestamp) } : payload
  sessionStorage.setItem(key, JSON.stringify({ signature, id, timestamp }))
  const result = await request<T>(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...resolved, requestId: id }) })
  sessionStorage.removeItem(key)
  return result
}
function stateKey(state: string): TextKey {
  switch (state) {
    case 'queued': return 'app.state.queued'
    case 'running': return 'app.state.running'
    case 'succeeded': return 'app.state.succeeded'
    case 'failed': return 'app.state.failed'
    case 'cancelled': return 'app.state.cancelled'
    case 'interrupted': return 'app.state.interrupted'
    default: return 'app.state.unknown'
  }
}
function App() {
  const [activeTab, setActiveTab] = useState<'generate' | 'runs' | 'services'>('generate')
  const [uploadSelection, setUploadSelection] = useState<{ ids: string[] } | null>(null)
  function openUpload(ids: string[]) { setUploadSelection({ ids }); setActiveTab('services') }
  const [language, setLanguage] = useState<Language>(initialLanguage)
  const t = (key: TextKey, params?: Message['params']) => translate(language, key, params)
  const locale = language === 'de' ? 'de-DE' : 'en-GB'
  const [runSearch, setRunSearch] = useState('')
  const [runLimit, setRunLimit] = useState<number | null>(10)
  const [jobs, setJobs] = useState<Job[]>([])
  const [selected, setSelected] = useState<string | null>(localStorage.getItem('selectedJob'))
  const [datasetName, setDatasetName] = useState('')
  const [source, setSource] = useState('starter')
  const [generation, setGeneration] = useState<Generation | null>(null)
  const [uploadingInput, setUploadingInput] = useState(false)
  const [editorLoad, setEditorLoad] = useState<{ configuration: Configuration; generation?: Generation; key: number } | null>(null)
  const [configuration, setConfiguration] = useState<Configuration | null>(null)
  const configurationReady = configuration !== null && problems(configuration).length === 0
  const nativeOutput = source === 'synthea-generation' && generation?.outputMode === 'synthea'
  const canStart = (nativeOutput || configurationReady) && (source !== 'synthea-generation' || generation !== null)
  const generationPayload = { ...(source === 'synthea-generation' ? { generation } : {}), datasetName: datasetName.trim() || undefined }
  const [logs, setLogs] = useState<string | null>(null)
  const [error, setError] = useState<Message | null>(null)
  const [connectionError, setConnectionError] = useState<Message | null>(null)
  const [busy, setBusy] = useState(false)
  const job = jobs.find(j => j.id === selected)
  useEffect(() => {
    document.documentElement.lang = language
    document.title = translate(language, 'app.title')
    localStorage.setItem('workbenchLanguage', language)
  }, [language])
  useEffect(() => {
    let active = true
    async function refresh() {
      try {
        const next = await request<Job[]>('/jobs')
        if (active) { setJobs(next); setConnectionError(null) }
        if (selected && next.some(item => item.id === selected)) {
          const response = await fetchResponse(`/jobs/${selected}/logs`)
          const text = await response.text()
          if (active) setLogs(text)
        }
      } catch (e) { if (active) setConnectionError(errorMessage(e)) }
    }
    void refresh()
    const timer = setInterval(() => void refresh(), 1500)
    return () => { active = false; clearInterval(timer) }
  }, [selected])
  function select(id: string) { setLogs(null); setSelected(id); localStorage.setItem('selectedJob', id) }
  async function start() {
    if (!canStart) return
    setBusy(true); setError(null)
    try {
      const next = [await submitRuns<Job>('/jobs', {
        source, ...generationPayload,
        ...(nativeOutput ? {} : { configurationProperties: exportPropertiesConfiguration(configuration!, language) })
      })]
      setJobs(old => [...next, ...old.filter(job => !next.some(value => value.id === job.id))]); select(next[0].id); setRunSearch(''); setRunLimit(10); setActiveTab('runs')
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  async function loadRun() {
    if (!job) return
    setBusy(true); setError(null)
    try {
      const loaded = await request<{ source: string; datasetName: string; generation?: Generation; configurationProperties: string }>(`/jobs/${job.id}/editor`, { method: 'POST' })
      const config = importRunProperties(loaded.configurationProperties)
      setEditorLoad({ configuration: config, generation: loaded.generation, key: Date.now() })
      setConfiguration(config); setGeneration(loaded.generation ?? null)
      setSource(loaded.source); setDatasetName(loaded.datasetName); setActiveTab('generate')
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  async function deleteRun() {
    if (!job || !window.confirm(t('app.deleteRunConfirm'))) return
    setBusy(true); setError(null)
    try {
      await fetchResponse(`/jobs/${job.id}`, { method: 'DELETE' })
      setJobs(old => old.filter(item => item.id !== job.id))
      setSelected(null); setLogs(null); localStorage.removeItem('selectedJob')
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  function duration(job: Job) {
    if (job.duration_seconds == null) return null
    const seconds = Math.floor(job.duration_seconds)
    return t('app.duration', { time: `${Math.floor(seconds / 3600)}:${String(Math.floor(seconds / 60) % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` })
  }
  function configurationName(job: Job) {
    const config = job.configuration
    return !config ? '' : config.id === 'workbook' ? t('app.workbookConfiguration')
      : config.id === 'synthea' ? t('app.generation.nativeOutput') : config.id === 'editor' ? t('app.defaults') : config.id === 'default' ? t('app.converterDefaults') : config.name
  }
  async function cancel() {
    try { await request(`/jobs/${selected}/cancel`, { method: 'POST' }) } catch (e) { setError(errorMessage(e)) }
  }
  const filteredJobs = jobs.filter(j => [j.id, j.dataset_name ?? '', configurationName(j), j.source_name ?? j.source ?? '', t(stateKey(j.state))].join(' ').toLowerCase().includes(runSearch.toLowerCase())).sort((a, b) => b.created - a.created)
  const alert = error || connectionError
  // This is the API's queue placeholder, not converter-produced log content.
  const logText = !selected ? t('app.selectRun') : logs === null || logs === 'Waiting for worker…' ? t('app.waiting') : logs
  return <main className="mx-auto max-w-6xl px-6 py-12">
    <header className="mb-10 flex flex-wrap items-center gap-4">
      <div className="rounded-xl bg-teal-800 p-3 text-white"><Activity size={28}/></div>
      <div><p className="text-xs font-semibold uppercase tracking-widest text-teal-800">{t('app.subtitle')}</p><h1 className="mt-1 text-3xl font-semibold tracking-tight">{t('app.title')}</h1></div>
      <label className="ml-auto flex flex-col gap-1 text-sm">{t('app.language')}<select className="rounded-lg border border-slate-300 bg-white p-2" value={language} onChange={e => setLanguage(e.target.value as Language)}><option value="de">{t('app.language.de')}</option><option value="en">{t('app.language.en')}</option></select></label>
    </header>
    <div role="tablist" aria-label={t('app.navigation')} className="mb-6 flex flex-wrap gap-2">
      {(['generate', 'runs', 'services'] as const).map((tab, index, tabs) => <button key={tab} type="button" role="tab" id={`main-tab-${tab}`} aria-controls={`main-panel-${tab}`} aria-selected={activeTab === tab} tabIndex={activeTab === tab ? 0 : -1} onClick={() => setActiveTab(tab)} onKeyDown={event => {
        const next = event.key === 'ArrowRight' ? (index + 1) % tabs.length : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : null
        if (next !== null) { event.preventDefault(); setActiveTab(tabs[next]); document.getElementById(`main-tab-${tabs[next]}`)?.focus() }
      }} className={`rounded-lg border px-4 py-3 text-sm font-medium focus-visible:outline-2 focus-visible:outline-teal-600 ${activeTab === tab ? 'border-teal-800 bg-teal-800 text-white' : 'border-slate-300 bg-white text-slate-700'}`}>{t(tab === 'generate' ? 'app.tabs.generate' : tab === 'runs' ? 'app.runs' : 'app.tabs.services')}</button>)}
    </div>
    {alert && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-red-800">{t(alert.key, alert.params)}</p>}
    <div role="tabpanel" id="main-panel-generate" aria-labelledby="main-tab-generate" hidden={activeTab !== 'generate'}>
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold">{t('app.create')}</h2><p className="mt-1 text-sm text-slate-500">{t('app.intro')}</p>
      <div className="mt-6 flex flex-wrap items-end gap-5">
        <InputSelection key={editorLoad?.key} language={language} source={source} onChange={setSource} onBusy={setUploadingInput}/>
        <label className="flex w-full sm:w-80 flex-col gap-2 text-sm font-medium">{t('app.datasetName')}<input className="rounded-lg border border-slate-300 p-2.5" maxLength={200} value={datasetName} onChange={e => setDatasetName(e.target.value)} placeholder={t('app.datasetNameHint')}/></label>
        <Button onClick={() => void start()} disabled={busy || uploadingInput || !canStart}><Play size={16}/>{t(source === 'synthea-generation' ? 'app.generation.start' : 'app.start')}</Button>
      </div>{source === 'synthea-generation' && <GenerationSettings key={editorLoad?.key} initialValue={editorLoad?.generation} language={language} onChange={setGeneration}/>}
      <p className="mt-4 text-xs text-slate-500">{t(nativeOutput ? 'app.generation.nativeHint' : 'app.outputHint')}</p>
    </section>
    <div hidden={nativeOutput}>
      <ConfigurationEditor key={editorLoad?.key} initialConfiguration={editorLoad?.configuration} language={language} onChange={setConfiguration}/>
    </div>
    </div>
    <div role="tabpanel" id="main-panel-services" aria-labelledby="main-tab-services" hidden={activeTab !== 'services'}>
      <FhirUploads language={language} requestedSelection={uploadSelection}/>
      <ServiceLinks language={language}/>
    </div>
    <div role="tabpanel" id="main-panel-runs" aria-labelledby="main-tab-runs" hidden={activeTab !== 'runs'}>
    <div className="mt-8 grid gap-6 md:grid-cols-[300px_1fr]">
      <section><h2 className="mb-3 text-lg font-semibold">{t('app.runs')} <span className="text-slate-400">{jobs.length}</span></h2><input className="mb-3 w-full rounded-lg border border-slate-300 p-2 text-sm" aria-label={t('app.datasets.searchRuns')} placeholder={t('app.datasets.searchRuns')} value={runSearch} onChange={e => { setRunSearch(e.target.value); setRunLimit(10) }}/><div className="space-y-2">
        {jobs.length === 0 && <p className="text-sm text-slate-500">{t('app.empty')}</p>}
        {filteredJobs.slice(0, runLimit ?? filteredJobs.length).map(j => <button key={j.id} onClick={() => select(j.id)} className={`w-full rounded-xl border p-4 text-left ${selected === j.id ? 'border-teal-700 bg-teal-50' : 'border-slate-200 bg-white'}`}><div className="flex justify-between gap-2 text-sm font-semibold"><span>{new Date(j.created * 1000).toLocaleTimeString(locale)}</span><span>{t(j.failure?.kind === 'memory' ? 'app.failure.memoryTitle' : stateKey(j.state))}</span></div><p className="mt-2 font-mono text-xs text-slate-500">{j.id.slice(0, 8)} · {new Date(j.created * 1000).toLocaleDateString(locale)}</p><p className="mt-2 break-words text-sm">{j.dataset_name && <strong className="block">{j.dataset_name}</strong>}{configurationName(j)}</p>{duration(j) && <p className="mt-1 text-xs text-slate-500">{duration(j)}</p>}{j.source && <p className="mt-1 text-xs text-slate-500">{j.source === 'synthea-generation' ? t('app.generation.title') : j.source === 'starter' ? t('app.starter') : j.source === 'demo' ? t('app.demo') : j.source_name ?? j.source}</p>}</button>)}
      </div><ListExpansion language={language} total={filteredJobs.length} limit={runLimit} onChange={setRunLimit} countKey="app.list.runsCount"/></section>
      <section className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">{t('app.details')}</h2>{job && <div className="flex flex-wrap gap-2">{['queued','running'].includes(job.state) && <Button variant="outline" onClick={() => void cancel()} disabled={!!job.cancel}><Square size={14}/>{t(job.cancel ? 'app.cancelling' : 'app.cancel')}</Button>}{!['queued','running'].includes(job.state) && <Button variant="outline" disabled={busy} onClick={() => void loadRun()}>{t('app.loadRun')}</Button>}{!['queued','running'].includes(job.state) && <><Help text={t('app.loadRunHelp')} t={key => t(key as TextKey)}/><Button variant="outline" disabled={busy} onClick={() => void deleteRun()}>{t('app.deleteRun')}</Button></>}{job.download_available && <Button asChild><a href={`/api/jobs/${job.id}/download`}><Download size={16}/>{t('app.download')}</a></Button>}{job.download_available && <Help text={t('app.downloadHelp')} t={key => t(key as TextKey)}/>}</div>}</div>
        {job && <p className="mb-3 text-sm text-slate-500">{t('app.status')}: {t(stateKey(job.state))}{job.exit_code !== null ? ` · ${t('app.exitCode')}: ${job.exit_code}` : ''}{job.state === 'interrupted' ? ` · ${t('app.retry')}` : ''}</p>}
        {job?.failure && <p role="alert" className="mb-3 rounded-xl bg-red-50 p-3 text-sm text-red-800">{t(job.failure.kind === 'memory' ? 'app.failure.memory' : 'app.failure.killed')}</p>}
        {job?.generation && <p className="mb-3 text-sm">{t('app.generation.requested', { count: job.generation.population })}{job.generation_result && ` · ${t(job.generation.outputMode === 'synthea' ? 'app.generation.nativeActual' : 'app.generation.actual', { generated: job.generation_result.generatedPatients, imported: job.generation_result.importedPatients, failed: job.generation_result.failedPatients })}`}</p>}
        {job && <p className="mb-3 break-words text-sm">{job.dataset_name && <strong>{job.dataset_name} · </strong>}{configurationName(job)}{job.batch_id ? ` · ${t('app.runGroup')}: ${job.batch_id.slice(0, 8)}` : ''}{job.repeated_from ? ` · ${t('app.repeatedFrom')}: ${job.repeated_from.slice(0, 8)}` : ''}</p>}
        {job && duration(job) && <p className="mb-4 text-sm">{duration(job)}</p>}
        {job && <DatasetResults onUpload={openUpload} context={{ datasetName: job.dataset_name, source: job.source, sourceName: job.source_name, configuration: job.configuration }} jobId={job.id} state={job.state} language={language}/>}
        <pre aria-label={t('app.logs')} className="h-96 overflow-auto rounded-xl bg-slate-950 p-4 font-mono text-xs leading-5 whitespace-pre-wrap text-slate-200">{logText}</pre>
      </section>
    </div>
    </div>
  </main>
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>)
