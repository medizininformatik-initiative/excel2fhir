import { Button } from './components/ui/button'
import { translate, type Language } from './i18n'

export function ListExpansion({ language, total, limit, onChange, countKey }: {
  language: Language
  total: number
  limit: number | null
  onChange: (limit: number | null) => void
  countKey: 'app.list.runsCount' | 'app.list.datasetsCount'
}) {
  const shown = Math.min(limit ?? total, total)
  return <div className="mt-3 space-y-2">
    <p className="text-sm text-slate-500" aria-live="polite">{translate(language, countKey, { shown, total })}</p>
    <div className="flex flex-wrap gap-2">
      {shown < total && <>
        <Button type="button" variant="outline" onClick={() => onChange(shown + 10)}>{translate(language, 'app.list.more')}</Button>
        <Button type="button" variant="outline" onClick={() => onChange(null)}>{translate(language, 'app.list.all')}</Button>
      </>}
      {(limit === null || limit > 10) && <Button type="button" variant="outline" onClick={() => onChange(10)}>{translate(language, 'app.list.less')}</Button>}
    </div>
  </div>
}
