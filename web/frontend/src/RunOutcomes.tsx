import { translate, type Language, type TextKey } from './i18n'

export type Outcomes = {
  generation: string
  validation: { status: string; reports: number; findings: number; incomplete: number }
  transfer: { status: string; checked: number; differences: number; unavailable: number; importNotes: number; configuredOmissions: number
    notes: { resourceType: string; path: string; reason: string; count: number }[]
    details: { source: string; variant: string; status: string; message: string }[] }
}

export function RunOutcomes({ outcomes, language }: { outcomes: Outcomes; language: Language }) {
  const t = (key: TextKey, params?: Record<string, string | number>) => translate(language, key, params)
  const label = (status: string) => t(`app.outcomes.${status}` as TextKey)
  const { transfer, validation } = outcomes
  return <div className="mb-4 space-y-3">
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="rounded-lg border border-slate-200 p-3"><h3 className="text-sm font-semibold">{t('app.outcomes.generation')}</h3><p className="mt-1 text-sm">{label(outcomes.generation)}</p></div>
      <div className="rounded-lg border border-slate-200 p-3"><h3 className="text-sm font-semibold">{t('app.outcomes.validation')}</h3><p className="mt-1 text-sm">{label(validation.status)}</p>{validation.reports > 0 && <p className="mt-1 text-xs text-slate-600">{t('app.outcomes.validationCounts', validation)}</p>}</div>
    </div>
    {transfer.status !== 'not-applicable' && <div className="rounded-lg border border-slate-200 p-3">
      <h3 className="text-sm font-semibold">{t('app.outcomes.transfer')}: {label(transfer.status)}</h3>
      <p className="mt-1 text-sm">{t('app.outcomes.transferCounts', { checked: transfer.checked, differences: transfer.differences, notes: transfer.importNotes, unavailable: transfer.unavailable })}</p>
      {(transfer.notes.length > 0 || transfer.details.length > 0 || transfer.configuredOmissions > 0) && <details className="mt-2 text-sm">
        <summary className="cursor-pointer text-teal-800">{t('app.outcomes.details')}</summary>
        <p className="mt-2 text-xs text-slate-600">{t('app.outcomes.explanation', { count: transfer.configuredOmissions })}</p>
        <div className="mt-2 max-h-80 overflow-auto">
          {transfer.notes.length > 0 && <ul className="space-y-2">{transfer.notes.map((note, index) => <li key={index} className="break-words"><span className="font-medium">{note.count} × {note.resourceType} · {note.path}</span><p>{note.reason}</p></li>)}</ul>}
          {transfer.details.map((detail, index) => <div key={index} className="mt-3 break-words border-t border-slate-200 pt-2"><p className="font-medium">{detail.source} · {detail.variant} · {label(detail.status)}</p><p className="whitespace-pre-wrap">{detail.message}</p></div>)}
        </div>
      </details>}
    </div>}
  </div>
}
