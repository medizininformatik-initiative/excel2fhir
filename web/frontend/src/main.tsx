import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Download, Play, Square, Activity } from 'lucide-react'
import { Button } from './components/ui/button'
import './index.css'

type Job = { id: string; state: string; created: number; cancel: number; exit_code: number | null }
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch('/api' + path, init)
  if (!response.ok) throw new Error((await response.text()) || `Request failed (HTTP ${response.status})`)
  return response.json()
}
function App() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [selected, setSelected] = useState<string | null>(localStorage.getItem('selectedJob'))
  const [source, setSource] = useState('starter')
  const [logs, setLogs] = useState('Select a run to inspect its logs.')
  const [error, setError] = useState('')
  const [connectionError, setConnectionError] = useState('')
  const [busy, setBusy] = useState(false)
  const job = jobs.find(j => j.id === selected)
  useEffect(() => {
    let active = true
    async function refresh() {
      try {
        const next = await request<Job[]>('/jobs')
        if (active) { setJobs(next); setConnectionError('') }
        if (selected) {
          const response = await fetch(`/api/jobs/${selected}/logs`)
          if (!response.ok) throw new Error('Could not load logs')
          const text = await response.text()
          if (active) setLogs(text)
        }
      } catch (e) { if (active) setConnectionError(String(e)) }
    }
    void refresh()
    const timer = setInterval(() => void refresh(), 1500)
    return () => { active = false; clearInterval(timer) }
  }, [selected])
  function select(id: string) { setSelected(id); localStorage.setItem('selectedJob', id) }
  async function start() {
    setBusy(true); setError('')
    try {
      const next = await request<Job>('/jobs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ source, profile: 'default' }) })
      setJobs(old => [next, ...old]); select(next.id)
    } catch (e) { setError(String(e)) } finally { setBusy(false) }
  }
  async function cancel() {
    try { await request(`/jobs/${selected}/cancel`, { method: 'POST' }) } catch (e) { setError(String(e)) }
  }
  return <main className="mx-auto max-w-6xl px-6 py-12">
    <header className="mb-10 flex items-center gap-4"><div className="rounded-xl bg-teal-800 p-3 text-white"><Activity size={28}/></div><div><p className="text-xs font-semibold uppercase tracking-widest text-teal-800">Technical prototype · Local workspace</p><h1 className="mt-1 text-3xl font-semibold tracking-tight">Excel2FHIR Workbench</h1></div></header>
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"><h2 className="text-lg font-semibold">Create a dataset</h2><p className="mt-1 text-sm text-slate-500">Run the existing converter with a saved input and configuration snapshot.</p><div className="mt-6 flex flex-wrap items-end gap-5"><label className="flex min-w-0 w-full sm:w-60 flex-col gap-2 text-sm font-medium">Input workbook<select className="rounded-lg border border-slate-300 p-2.5" value={source} onChange={e => setSource(e.target.value)}><option value="starter">Bundled starter workbook</option><option value="demo">INTERPOLAR demo workbook</option></select></label><label className="flex min-w-0 w-full sm:w-60 flex-col gap-2 text-sm font-medium">Configuration profile<select className="rounded-lg border border-slate-300 p-2.5"><option>Existing converter defaults</option></select></label><Button onClick={() => void start()} disabled={busy}><Play size={16}/>Start conversion</Button></div><p className="mt-4 text-xs text-slate-500">JSON + NDJSON · FHIR validation disabled · Runs continue when you reload this page</p></section>
    {(error || connectionError) && <p role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-800">{error || connectionError}</p>}
    <div className="mt-8 grid gap-6 md:grid-cols-[300px_1fr]"><section><h2 className="mb-3 text-lg font-semibold">Runs <span className="text-slate-400">{jobs.length}</span></h2><div className="space-y-2">{jobs.length === 0 && <p className="text-sm text-slate-500">Your conversion runs will appear here.</p>}{jobs.map(j => <button key={j.id} onClick={() => select(j.id)} className={`w-full rounded-xl border p-4 text-left ${selected === j.id ? 'border-teal-700 bg-teal-50' : 'border-slate-200 bg-white'}`}><div className="flex justify-between text-sm font-semibold"><span>{new Date(j.created * 1000).toLocaleTimeString()}</span><span>{j.state}</span></div><p className="mt-2 font-mono text-xs text-slate-500">{j.id.slice(0, 8)} · {new Date(j.created * 1000).toLocaleDateString()}</p></button>)}</div></section><section className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5"><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">Run details</h2>{job && <div className="flex flex-wrap gap-2"><Button asChild variant="outline"><a href={`/api/jobs/${job.id}/snapshot`}>Snapshot</a></Button>{['queued','running'].includes(job.state) && <Button variant="outline" onClick={() => void cancel()} disabled={!!job.cancel}><Square size={14}/>{job.cancel ? 'Cancelling…' : 'Cancel'}</Button>}{job.state === 'succeeded' && <Button asChild><a href={`/api/jobs/${job.id}/download`}><Download size={16}/>Download</a></Button>}</div>}</div>{job && <p className="mb-3 text-sm text-slate-500">Status: {job.state}{job.exit_code !== null ? ` · Exit code: ${job.exit_code}` : ''}{job.state === 'interrupted' ? ' · Start a new conversion to retry.' : ''}</p>}<pre aria-label="Converter logs" className="h-96 overflow-auto rounded-xl bg-slate-950 p-4 font-mono text-xs leading-5 whitespace-pre-wrap text-slate-200">{logs}</pre></section></div>
  </main>
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>)
