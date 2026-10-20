import { selectionProgress } from './selection-progress'
import type { Language } from './i18n'

export function SelectionProgress({ log, language, running }: { log: string; language: Language; running: boolean }) {
  const progress = selectionProgress(log)
  if (!progress) return null
  const p = progress, de = language === 'de'
  return <section className="my-4 rounded-xl border border-slate-200 bg-slate-50 p-4" aria-live="polite">
    <p className="font-semibold">{de ? 'Passende Patienten' : 'Matching patients'}: {p.matched} / {p.requested}</p>
    <p>{de ? `${p.attempts} Kandidaten geprüft; ${p.attempts - p.matched} erfüllen die Gesamtauswahl nicht.` : `${p.attempts} candidates checked; ${p.attempts - p.matched} do not meet the combined criteria.`}</p>
    <p className="text-sm">{de ? `Stationärer Aufenthalt: mindestens ${p.days} Tage. History: ${p.history === 0 ? 'gesamte Historie' : p.history + ' Jahre'}.` : `Inpatient stay: at least ${p.days} days. History: ${p.history === 0 ? 'all history' : p.history + ' years'}.`}</p>
    <p className="text-sm">{de ? `Versuche für den aktuellen Patienten: ${p.slotAttempts} / ${p.limit}.` : `Attempts for the current patient: ${p.slotAttempts} / ${p.limit}.`}</p>
    {p.exhausted ? <p role="alert" className="mt-2 text-red-800">{de ? 'Versuchsgrenze erreicht. Die Suche wurde beendet. Verringere die Mindestdauer oder lockere die Auswahl für einen neuen Lauf.' : 'Attempt limit reached. The search stopped. Reduce the minimum duration or relax the criteria for a new run.'}</p>
      : running && p.sinceMatch >= 100 && <p className="mt-2 text-amber-800">{de ? `Seit ${p.sinceMatch} Versuchen kein weiterer Treffer. Die Auswahl ist möglicherweise zu streng. Du kannst den Lauf abbrechen und die Auswahl lockern.` : `No new match for ${p.sinceMatch} attempts. The criteria may be too strict. You can cancel and relax the selection.`}</p>}
  </section>
}
