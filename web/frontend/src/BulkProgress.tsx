import { useEffect, useRef, useState } from 'react'
import type { Language } from './i18n'

export type BulkStatus = {
  completed: number; total: number; chunk: number; phase: string;
  run?: number; runs?: number; finishedRuns?: number; stageCompleted?: number; stageTotal?: number;
  workCompleted?: number; workTotal?: number;
  attempts?: number; generatedBytes?: number; archiveBytes?: number;
  estimatedTotalBytes?: number | null; remainingSeconds?: number | null; updatedAt?: number;
}
export type BulkSubrun = { number: number; start: number; total: number; completed: number; state: string; firstSeed: string; lastSeed: string }

function ProgressBar({ value, max, label, compact = false }: { value: number; max: number; label: string; compact?: boolean }) {
  const percent = Number.isFinite(value) && Number.isFinite(max) && max > 0
    ? Math.min(100, Math.max(0, 100 * value / max)) : 0
  return <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}
    className={`w-full overflow-hidden rounded-full bg-slate-200 ${compact ? 'h-2' : 'h-3'}`}>
    <div className={`h-full ${compact ? 'bg-teal-500' : 'bg-teal-700'}`} style={{ width: `${percent}%` }}/>
  </div>
}

export function BulkProgress({ progress, subruns, state, language }: { progress?: BulkStatus; subruns?: BulkSubrun[]; state: string; language: Language }) {
  const [limit, setLimit] = useState(10)
  const de = language === 'de', locale = de ? 'de-DE' : 'en-GB'
  const number = (n: number) => n.toLocaleString(locale)
  const bytes = (n: number) => n >= 1e9 ? `${(n / 1e9).toLocaleString(locale, { maximumFractionDigits: 2 })} GB` : `${(n / 1e6).toLocaleString(locale, { maximumFractionDigits: 1 })} MB`
  const duration = (seconds: number) => {
    const minutes = Math.ceil(seconds / 60)
    const days = Math.floor(minutes / 1440), hours = Math.floor(minutes % 1440 / 60)
    return [days ? `${days} ${de ? 'Tage' : 'days'}` : '', hours ? `${hours} h` : '', `${minutes % 60} min`].filter(Boolean).join(' ')
  }
  const phases: Record<string, string> = de
    ? { starting: 'Teillauf wird vorbereitet', generating: 'Patienten werden generiert', compressing: 'Synthea-Dateien werden komprimiert', mapping: 'Daten werden aufbereitet', converting: 'FHIR-Dateien werden erzeugt', packaging: 'ZIP-Paket wird geprüft und gespeichert', 'between-chunks': 'Nächste Teilmenge wird vorbereitet', 'between-runs': 'Nächster Teillauf wird vorbereitet', complete: 'Abgeschlossen' }
    : { starting: 'Preparing subrun', generating: 'Generating patients', compressing: 'Compressing Synthea files', mapping: 'Mapping data', converting: 'Writing FHIR files', packaging: 'Verifying and saving ZIP package', 'between-chunks': 'Preparing next chunk', 'between-runs': 'Preparing next subrun', complete: 'Complete' }
  const states: Record<string, string> = de
    ? { queued: 'Ausstehend', running: 'Läuft', succeeded: 'Abgeschlossen', failed: 'Fehlgeschlagen', cancelled: 'Abgebrochen', interrupted: 'Unterbrochen' }
    : { queued: 'Pending', running: 'Running', succeeded: 'Complete', failed: 'Failed', cancelled: 'Cancelled', interrupted: 'Interrupted' }
  const p = progress
  const workDone = p?.workCompleted ?? p?.completed ?? 0
  const workTotal = p?.workTotal ?? p?.total ?? 1
  const activeRun = state === 'running' ? subruns?.find(run => run.state === 'running') : undefined
  const finishedRuns = subruns?.filter(run => run.state === 'succeeded').length ?? p?.finishedRuns ?? 0
  const totalRuns = subruns?.length ?? p?.runs ?? 0
  const currentRun = activeRun?.number ?? (state === 'running' ? p?.run : undefined)
  const visibleRuns = subruns?.slice(0, limit) ?? []
  const extraActiveRun = activeRun && !visibleRuns.some(run => run.number === activeRun.number) ? activeRun : undefined
  const tableContainer = useRef<HTMLDivElement>(null)
  const activeRow = useRef<HTMLTableRowElement>(null)
  useEffect(() => {
    const container = tableContainer.current, row = activeRow.current
    if (container && row) {
      const headerHeight = container.querySelector('thead')?.getBoundingClientRect().height ?? 0
      container.scrollTop += row.getBoundingClientRect().top - container.getBoundingClientRect().top - headerHeight
    }
  }, [activeRun?.number])
  const subrunLabel = currentRun
    ? `${de ? 'Teillauf' : 'Subrun'} ${number(currentRun)} ${de ? 'von' : 'of'} ${number(totalRuns)} · ${number(finishedRuns)} ${de ? 'abgeschlossen' : 'complete'}`
    : `${de ? 'Teilläufe' : 'Subruns'} · ${number(finishedRuns)} ${de ? 'von' : 'of'} ${number(totalRuns)} ${de ? 'abgeschlossen' : 'complete'}`
  return <div className="space-y-4">
    {!p && subruns && <p>{number(subruns.reduce((sum, run) => sum + run.total, 0))} {de ? 'Patienten insgesamt' : 'patients in total'}</p>}
    {p && <div className="space-y-2" role="status" aria-live="polite">
      <p className="font-medium">{(100 * workDone / workTotal).toLocaleString(locale, { maximumFractionDigits: 0 })} % {de ? 'Gesamtfortschritt' : 'overall progress'}</p>
      <ProgressBar max={workTotal} value={workDone} label={de ? 'Gesamtfortschritt' : 'Overall progress'}/>
      <p>{number(p.completed)} / {number(p.total)} {de ? 'Patienten abgeschlossen' : 'patients complete'}</p>
      {currentRun && <p className="font-medium">{subrunLabel}</p>}
      {state === 'running' && <p>{de ? 'Aktueller Schritt: ' : 'Current stage: '}{phases[p.phase] ?? p.phase}{!!p.stageTotal && ` · ${number(p.stageCompleted ?? 0)} / ${number(p.stageTotal)}`}{p.phase === 'generating' && !!p.attempts && ` · ${number(p.attempts)} ${de ? 'Kandidaten geprüft' : 'candidates checked'}`}</p>}
      {state === 'running' && !!p.stageTotal && <ProgressBar compact max={p.stageTotal} value={p.stageCompleted ?? 0} label={de ? 'Fortschritt des aktuellen Arbeitsschritts' : 'Current stage progress'}/>}
      {p.generatedBytes !== undefined && <p>{de ? 'FHIR-Daten erzeugt' : 'FHIR data generated'}: <strong>{bytes(p.generatedBytes)}</strong>{p.estimatedTotalBytes != null && state !== 'succeeded' && ` / ${de ? 'voraussichtlich ca.' : 'estimated approx.'} ${bytes(p.estimatedTotalBytes)}`}</p>}
      {state === 'running' && <p>{p.remainingSeconds == null ? (de ? 'Schätzung wird nach der ersten abgeschlossenen 100er-Teilmenge ermittelt.' : 'Estimates become available after the first completed 100-patient chunk.') : `${de ? 'Geschätzte Restzeit: ca.' : 'Estimated time remaining: approx.'} ${duration(p.remainingSeconds)}`}</p>}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
        {!!p.archiveBytes && <p>{de ? 'In ZIP-Paketen gespeichert' : 'Stored in ZIP packages'}: {bytes(p.archiveBytes)}</p>}
        {state === 'running' && p.updatedAt && <p>{de ? 'Letzte Statusmeldung' : 'Last status update'}: {new Date(p.updatedAt * 1000).toLocaleTimeString(locale)}</p>}
      </div>
    </div>}
    {subruns && <div>
      <h4 className="mb-2 font-semibold">{subrunLabel}</h4>
      <div ref={tableContainer} className="max-h-96 overflow-auto rounded-lg border border-teal-200 bg-white">
        <table className="w-full text-left text-sm"><thead className="sticky top-0 bg-teal-100"><tr>{[de ? 'Teillauf' : 'Subrun', 'Status', de ? 'Patienten' : 'Patients', 'Seeds'].map(title => <th key={title} className="p-2">{title}</th>)}</tr></thead>
          <tbody>{[...visibleRuns, ...(extraActiveRun ? [extraActiveRun] : [])].map(run => <tr ref={run.number === activeRun?.number ? activeRow : undefined} key={run.number} className={`border-t border-slate-100 ${run.state === 'running' ? 'bg-teal-50' : ''}`}><td className="p-2">{run.number}</td><td className="p-2">{states[run.state] ?? run.state}</td><td className="whitespace-nowrap p-2">{number(run.completed)} / {number(run.total)}</td><td className="break-all p-2 font-mono text-xs">{run.firstSeed === run.lastSeed ? run.firstSeed : `${run.firstSeed}–${run.lastSeed}`}</td></tr>)}</tbody>
        </table>
      </div>
      {limit < subruns.length && <button className="mt-2 text-sm underline" onClick={() => setLimit(old => old + 20)}>{de ? 'Weitere Teilläufe anzeigen' : 'Show more subruns'}</button>}
    </div>}
  </div>
}
