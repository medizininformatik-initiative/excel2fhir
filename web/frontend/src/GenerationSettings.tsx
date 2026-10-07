import { useEffect, useState } from 'react'
import { Help } from './Help'
import { generationHelp, selectionRule, moduleHelp, type HelpKey, type ModuleDescription } from './generation-help'
import { translate, type Language, type TextKey } from './i18n'

export type Generation = {
  outputMode: 'kds' | 'synthea'; population: number; minAge: number; maxAge: number; gender: string;
  timestampSeeds?: boolean; patientSeed: string; clinicianSeed: string; singlePersonSeed: string;
  referenceDate: string; endDate: string; state: string; city: string;
  yearsOfHistory: number; patientFilter: string; overflow: boolean;
  modules: string[]; keepModule: string; timestepDays: number; maxAttempts: number; veteranPopulation: boolean
}
type Catalogue = { revision: string; defaults: Generation; locations: Record<string, string[]>; modules: ModuleDescription[]; keepModules: ModuleDescription[] }
export function GenerationSettings({ language, onChange, initialValue }: { language: Language; onChange: (value: Generation | null) => void; initialValue?: Generation }) {
  const t = (key: TextKey) => translate(language, key)
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null)
  const [value, setValue] = useState<Generation | null>(null)
  const [failed, setFailed] = useState(false)
  const [moduleSearch, setModuleSearch] = useState('')
  useEffect(() => {
    let active = true
    fetch('/api/generation-catalogue').then(async response => {
      if (!response.ok) throw new Error()
      const next: Catalogue = await response.json()
      if (!active) return
      setCatalogue(next)
      let saved = next.defaults
      try { const stored = JSON.parse(localStorage.getItem('syntheaGeneration') ?? 'null'); if (stored && Object.entries(next.defaults).every(([key, initial]) => stored[key] === undefined || (Array.isArray(initial) ? Array.isArray(stored[key]) && stored[key].every((item: unknown) => typeof item === 'string') : typeof stored[key] === typeof initial))) saved = { ...next.defaults, ...stored } } catch { /* Use the pinned defaults. */ }
      setValue(initialValue ? { ...initialValue, timestampSeeds: false } : { ...saved, timestampSeeds: saved.timestampSeeds === true })
    }).catch(() => { if (active) setFailed(true) })
    return () => { active = false; onChange(null) }
  }, [onChange, initialValue])
  const seedValid = (seed: string) => /^-?\d{1,19}$/.test(seed) && BigInt(seed) >= -(2n ** 63n) && BigInt(seed) < 2n ** 63n
  const valid = value !== null && catalogue !== null &&
    [value.population, value.minAge, value.maxAge, value.yearsOfHistory, value.timestepDays, value.maxAttempts].every(Number.isInteger) &&
    value.population >= 1 && value.population <= 1000 && value.minAge >= 0 && value.minAge <= value.maxAge && value.maxAge <= 140 &&
    value.yearsOfHistory >= 0 && value.yearsOfHistory <= 140 && value.timestepDays >= 1 && value.timestepDays <= 365 && value.maxAttempts >= 1 && value.maxAttempts <= 10000 &&
    /^\d{4}-\d{2}-\d{2}$/.test(value.referenceDate) && value.referenceDate <= value.endDate && value.endDate <= new Date().toISOString().slice(0, 10) &&
    (value.timestampSeeds || ([value.patientSeed, value.clinicianSeed].every(seedValid) &&
    (value.singlePersonSeed === '' || (value.population === 1 && seedValid(value.singlePersonSeed))))) &&
    value.state in catalogue.locations && (!value.city || catalogue.locations[value.state].includes(value.city))
  useEffect(() => {
    if (value) { try { localStorage.setItem('syntheaGeneration', JSON.stringify(value)) } catch { /* Submission still works. */ } }
    onChange(valid ? value : null)
  }, [value, valid, onChange])
  if (failed) return <p role="alert" className="mt-4 text-red-700">{t('app.generation.loadFailed')}</p>
  if (!value || !catalogue) return <p className="mt-4 text-sm">{t('app.generation.loading')}</p>
  const update = <K extends keyof Generation>(key: K, next: Generation[K]) => setValue(old => old && ({ ...old, [key]: next }))
  const info = (key: HelpKey, detail?: string) => <Help text={detail ?? generationHelp(language, key)} t={key => t(key as TextKey)}/>
  const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 bg-white p-2 disabled:cursor-not-allowed disabled:border-neutral-200 disabled:bg-neutral-50 disabled:text-neutral-400 disabled:shadow-none'
  const number = (key: keyof Generation, label: TextKey, min: number, max: number) => <label className="text-sm">{t(label)}{info(key as HelpKey)}<input className={inputClass} type="number" min={min} max={max} step="1" value={String(value[key])} onChange={e => update(key, Number(e.target.value))}/></label>
  const text = (key: keyof Generation, label: TextKey, type = 'text') => {
    const disabled = value.timestampSeeds === true && ['patientSeed', 'clinicianSeed', 'singlePersonSeed'].includes(key)
    return <label className={`text-sm ${disabled ? 'text-neutral-400' : ''}`}>{t(label)}{info(key as HelpKey)}<input className={inputClass} type={type} disabled={disabled} value={String(value[key])} onChange={e => update(key, e.target.value)}/></label>
  }
  return <section className="mt-6 border-t border-slate-200 pt-5">
    <h3 className="font-semibold">{t('app.generation.title')}</h3>
    <p className="mt-2 text-sm text-slate-600">{t('app.generation.hint')}</p>
    <label className="mt-4 block text-sm">{t('app.generation.outputMode')}{info('outputMode')}<select className={inputClass} value={value.outputMode} onChange={e => update('outputMode', e.target.value as Generation['outputMode'])}><option value="kds">{t('app.generation.kdsOutput')}</option><option value="synthea">{t('app.generation.nativeOutput')}</option></select></label>
    <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {number('population', 'app.generation.population', 1, 1000)}
      {number('minAge', 'app.generation.minAge', 0, 140)}{number('maxAge', 'app.generation.maxAge', 0, 140)}
      <label className="text-sm">{t('app.generation.gender')}{info('gender')}<select className={inputClass} value={value.gender} onChange={e => update('gender', e.target.value)}><option value="">{t('app.generation.anyGender')}</option><option value="F">{t('app.generation.female')}</option><option value="M">{t('app.generation.male')}</option></select></label>
      <div className="flex items-center gap-1 text-sm sm:col-span-2 lg:col-span-4">
        <label className="flex items-center gap-2"><input type="checkbox" checked={value.timestampSeeds === true} onChange={e => update('timestampSeeds', e.target.checked)}/>{t('app.generation.timestampSeeds')}</label>
        <Help text={t('app.generation.timestampSeedsHint')} t={key => t(key as TextKey)}/>
      </div>
      {text('patientSeed', 'app.generation.patientSeed')}{text('clinicianSeed', 'app.generation.clinicianSeed')}
      {text('referenceDate', 'app.generation.referenceDate', 'date')}{text('endDate', 'app.generation.endDate', 'date')}
      <label className="text-sm">{t('app.generation.state')}{info('state')}<select className={inputClass} value={value.state} onChange={e => setValue({ ...value, state: e.target.value, city: '' })}>{Object.keys(catalogue.locations).map(state => <option key={state}>{state}</option>)}</select></label>
      <label className="text-sm">{t('app.generation.city')}{info('city')}<select className={inputClass} value={value.city} onChange={e => update('city', e.target.value)}><option value="">{t('app.generation.anyCity')}</option>{(catalogue.locations[value.state] ?? []).map(city => <option key={city}>{city}</option>)}</select></label>
      {number('yearsOfHistory', 'app.generation.history', 0, 140)}
    </div>
    <details className="mt-5"><summary className="cursor-pointer font-medium">{t('app.generation.advanced')}</summary>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <label className="text-sm">{t('app.generation.patientFilter')}{info('patientFilter')}<select className={inputClass} value={value.patientFilter} onChange={e => update('patientFilter', e.target.value)}><option value="all">{t('app.generation.allPatients')}</option><option value="alive">{t('app.generation.alive')}</option><option value="dead">{t('app.generation.dead')}</option></select></label>
        <label className="text-sm">{t('app.generation.keepModule')}{info('keepModule')}<select className={inputClass} value={value.keepModule} onChange={e => update('keepModule', e.target.value)}><option value="">{t('app.generation.noKeepModule')}</option>{catalogue.keepModules.map(module => <option key={module.id} value={module.id}>{selectionRule(language, module.id, module.name).name}</option>)}</select>{value.keepModule && <span className="mt-2 block text-xs text-slate-600">{selectionRule(language, value.keepModule, value.keepModule).description}</span>}</label>
        {text('singlePersonSeed', 'app.generation.singleSeed')}
        {number('timestepDays', 'app.generation.timestep', 1, 365)}{number('maxAttempts', 'app.generation.attempts', 1, 10000)}
        <label className={`flex items-center gap-2 text-sm ${value.patientFilter !== 'all' ? 'text-slate-400' : ''}`}><input type="checkbox" disabled={value.patientFilter !== 'all'} checked={value.overflow} onChange={e => update('overflow', e.target.checked)}/>{t('app.generation.overflow')}{info('overflow')}</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={value.veteranPopulation} onChange={e => update('veteranPopulation', e.target.checked)}/>{t('app.generation.veterans')}{info('veteranPopulation')}</label>
      </div>
      <p className="mt-4 text-sm text-slate-600">{t('app.generation.selectionHint')}</p>
      <fieldset className="mt-5"><legend className="font-medium">{t('app.generation.modules')}{info('modules')}</legend>
        <p className="mt-2 text-sm text-slate-600">{t('app.generation.modulesHint')}</p>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" className="rounded-lg border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50" onClick={() => update('modules', catalogue.modules.map(module => module.id))}>{t('app.generation.selectAllModules')}</button>
          <button type="button" className="rounded-lg border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50" onClick={() => update('modules', [])}>{t('app.generation.clearModules')}</button>
        </div>
        <input className={inputClass} aria-label={t('app.generation.moduleSearch')} placeholder={t('app.generation.moduleSearch')} value={moduleSearch} onChange={e => setModuleSearch(e.target.value)}/>
        <div className="mt-2 grid max-h-64 gap-2 overflow-auto rounded-lg border p-3 sm:grid-cols-2">{catalogue.modules.filter(module => (module.name + module.id).toLowerCase().includes(moduleSearch.toLowerCase())).map(module => <label key={module.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={value.modules.includes(module.id)} onChange={e => update('modules', e.target.checked ? [...value.modules, module.id] : value.modules.filter(id => id !== module.id))}/>{module.name}{moduleHelp(language, module) && info('modules', moduleHelp(language, module))}</label>)}</div>
      </fieldset>
    </details>
    {!valid && <p role="alert" className="mt-4 text-sm text-red-700">{t('app.generation.invalid')}</p>}
    <p className="mt-4 break-all text-xs text-slate-500">Synthea: {catalogue.revision}</p>
  </section>
}
