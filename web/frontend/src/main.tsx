import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Download, Play, Square, Activity } from 'lucide-react'
import { Button } from './components/ui/button'
import { errorMessage, initialLanguage, InterfaceError, translate, type Language, type Message, type TextKey } from './i18n'
import './index.css'

type Job = { id: string; state: string; created: number; cancel: number; exit_code: number | null }
async function fetchResponse(path: string, init?: RequestInit): Promise<Response> {
  let response: Response
  try { response = await fetch('/api' + path, init) }
  catch { throw new InterfaceError('app.error.network') }
  if (!response.ok) {
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
  const [language, setLanguage] = useState<Language>(initialLanguage)
  const t = (key: TextKey, params?: Message['params']) => translate(language, key, params)
  const locale = language === 'de' ? 'de-DE' : 'en-GB'
  const [jobs, setJobs] = useState<Job[]>([])
  const [selected, setSelected] = useState<string | null>(localStorage.getItem('selectedJob'))
  const [source, setSource] = useState('starter')
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
        if (selected) {
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
  function select(id: string) { setSelected(id); localStorage.setItem('selectedJob', id) }
  async function start() {
    setBusy(true); setError(null)
    try {
      const next = await request<Job>('/jobs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ source, profile: 'default' }) })
      setJobs(old => [next, ...old]); select(next.id)
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  async function cancel() {
    try { await request(`/jobs/${selected}/cancel`, { method: 'POST' }) } catch (e) { setError(errorMessage(e)) }
  }
  const alert = error || connectionError
  // This is the API's queue placeholder, not converter-produced log content.
  const logText = !selected ? t('app.selectRun') : logs === null || logs === 'Waiting for worker…' ? t('app.waiting') : logs
  return <main className="mx-auto max-w-6xl px-6 py-12">
    <header className="mb-10 flex flex-wrap items-center gap-4">
      <div className="rounded-xl bg-teal-800 p-3 text-white"><Activity size={28}/></div>
      <div><p className="text-xs font-semibold uppercase tracking-widest text-teal-800">{t('app.subtitle')}</p><h1 className="mt-1 text-3xl font-semibold tracking-tight">{t('app.title')}</h1></div>
      <label className="ml-auto flex flex-col gap-1 text-sm">{t('app.language')}<select className="rounded-lg border border-slate-300 bg-white p-2" value={language} onChange={e => setLanguage(e.target.value as Language)}><option value="de">{t('app.language.de')}</option><option value="en">{t('app.language.en')}</option></select></label>
    </header>
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold">{t('app.create')}</h2><p className="mt-1 text-sm text-slate-500">{t('app.intro')}</p>
      <div className="mt-6 flex flex-wrap items-end gap-5">
        <label className="flex min-w-0 w-full sm:w-80 flex-col gap-2 text-sm font-medium">{t('app.input')}<select className="rounded-lg border border-slate-300 p-2.5" value={source} onChange={e => setSource(e.target.value)}><option value="starter">{t('app.starter')}</option><option value="demo">{t('app.demo')}</option></select></label>
        <label className="flex min-w-0 w-full sm:w-80 flex-col gap-2 text-sm font-medium">{t('app.profile')}<select className="rounded-lg border border-slate-300 p-2.5"><option>{t('app.defaults')}</option></select></label>
        <Button onClick={() => void start()} disabled={busy}><Play size={16}/>{t('app.start')}</Button>
      </div><p className="mt-4 text-xs text-slate-500">{t('app.outputHint')}</p>
    </section>
    {alert && <p role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-800">{t(alert.key, alert.params)}</p>}
    <div className="mt-8 grid gap-6 md:grid-cols-[300px_1fr]">
      <section><h2 className="mb-3 text-lg font-semibold">{t('app.runs')} <span className="text-slate-400">{jobs.length}</span></h2><div className="space-y-2">
        {jobs.length === 0 && <p className="text-sm text-slate-500">{t('app.empty')}</p>}
        {jobs.map(j => <button key={j.id} onClick={() => select(j.id)} className={`w-full rounded-xl border p-4 text-left ${selected === j.id ? 'border-teal-700 bg-teal-50' : 'border-slate-200 bg-white'}`}><div className="flex justify-between gap-2 text-sm font-semibold"><span>{new Date(j.created * 1000).toLocaleTimeString(locale)}</span><span>{t(stateKey(j.state))}</span></div><p className="mt-2 font-mono text-xs text-slate-500">{j.id.slice(0, 8)} · {new Date(j.created * 1000).toLocaleDateString(locale)}</p></button>)}
      </div></section>
      <section className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">{t('app.details')}</h2>{job && <div className="flex flex-wrap gap-2"><Button asChild variant="outline"><a href={`/api/jobs/${job.id}/snapshot`}>{t('app.snapshot')}</a></Button>{['queued','running'].includes(job.state) && <Button variant="outline" onClick={() => void cancel()} disabled={!!job.cancel}><Square size={14}/>{t(job.cancel ? 'app.cancelling' : 'app.cancel')}</Button>}{job.state === 'succeeded' && <Button asChild><a href={`/api/jobs/${job.id}/download`}><Download size={16}/>{t('app.download')}</a></Button>}</div>}</div>
        {job && <p className="mb-3 text-sm text-slate-500">{t('app.status')}: {t(stateKey(job.state))}{job.exit_code !== null ? ` · ${t('app.exitCode')}: ${job.exit_code}` : ''}{job.state === 'interrupted' ? ` · ${t('app.retry')}` : ''}</p>}
        <pre aria-label={t('app.logs')} className="h-96 overflow-auto rounded-xl bg-slate-950 p-4 font-mono text-xs leading-5 whitespace-pre-wrap text-slate-200">{logText}</pre>
      </section>
    </div>
  </main>
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>)
