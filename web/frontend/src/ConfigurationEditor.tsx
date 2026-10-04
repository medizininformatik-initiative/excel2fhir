import { useEffect, useId, useRef, useState } from 'react'
import {
  Info,
  Plus,
  Trash2,
  Download,
  Upload,
  Save,
  RotateCcw
} from 'lucide-react'
import { Button } from './components/ui/button'
import { translate, type Language, type TextKey, type Message } from './i18n'
import {
  contract,
  options,
  darFields,
  darResource,
  identifierResources,
  defaults,
  importConfiguration,
  problems,
  optionEnabled,
  unmetDependencies,
  resourceEnabled,
  resourceOptions,
  previewIdentifier,
  type Configuration,
  type Option,
  type Value,
  type Rule
} from './configuration'

const storageKey = 'workbenchConfiguration.v1'
const groups = [
  { id: 'demographics', key: 'app.config.group.demographics' },
  { id: 'contacts', key: 'app.config.group.contacts' },
  { id: 'clinical', key: 'app.config.group.clinical' },
  { id: 'medication', key: 'app.config.group.medication' }
]
const resources = groups.flatMap((group) =>
  contract.resources.filter((r) => r.group === group.id)
)
type Translator = (key: string, params?: Message['params']) => string
function Help({ text, t }: { text: string; t: Translator }) {
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const [pinned, setPinned] = useState(false)
  const open = hovered || focused || pinned
  const id = useId()
  return (
    <span
      className="help relative inline-flex"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <button
        type="button"
        aria-label={t('app.config.help')}
        aria-expanded={open}
        aria-controls={id}
        aria-describedby={open ? id : undefined}
        onFocus={() => setFocused(true)}
        onBlur={() => {
          setFocused(false)
          setPinned(false)
        }}
        onClick={() => setPinned(!pinned)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setPinned(false)
            setFocused(false)
            setHovered(false)
          }
        }}
        className="rounded p-1 text-slate-500 focus-visible:outline-2 focus-visible:outline-teal-700"
      >
        <Info size={16} />
      </button>
      {open && (
        <span
          id={id}
          role="tooltip"
          className="absolute left-0 top-full z-30 w-64 rounded-lg bg-slate-900 p-3 text-sm font-normal leading-relaxed text-white shadow-lg"
        >
          {text}
        </span>
      )}
    </span>
  )
}
function OptionControl({
  option,
  config,
  update,
  t
}: {
  option: Option
  config: Configuration
  update: (id: string, value: Value) => void
  t: Translator
}) {
  const enabled = optionEnabled(option.id, config.values)
  const value = config.values[option.id]
  const dependencies = unmetDependencies(option.enabledWhen, config.values)
  const explain = (deps: typeof dependencies) =>
    t('app.config.requires', {
      labels: deps
        .map((d) => {
          const source = options.find((o) => o.id === d.option)!
          const choice = source.choiceLabelKeys?.[String(d.equals)]
          return `${t(source.labelKey)}: ${choice ? t(choice) : t(d.equals ? 'app.config.on' : 'app.config.off')}`
        })
        .join(' · ')
    })
  return (
    <div
      className="option-row border-t border-slate-100 py-4 first:border-t-0"
      data-option={option.id}
    >
      <div className="mb-2 flex items-center gap-1">
        <label htmlFor={option.id} className="font-medium text-sm">
          {t(option.labelKey)}
        </label>
        <Help text={t(option.helpKey)} t={t} />
      </div>
      {option.type === 'boolean' ? (
        <input
          id={option.id}
          aria-label={t(option.labelKey)}
          type="checkbox"
          disabled={!enabled}
          checked={value === true}
          onChange={(e) => update(option.id, e.target.checked)}
          className="h-4 w-4 accent-teal-700"
        />
      ) : option.type === 'enum' || option.type === 'set' ? (
        <div
          role="group"
          aria-label={t(option.labelKey)}
          className="flex flex-wrap gap-x-5 gap-y-3"
        >
          {option.choices?.map((choice) => {
            const unmet = unmetDependencies(
              option.choiceDependencies?.[String(choice)],
              config.values
            )
            const available = enabled && !unmet.length
            const label = t(option.choiceLabelKeys![String(choice)])
            return (
              <div key={String(choice)} className="text-sm">
                <label
                  className={`flex items-center gap-2 ${available ? '' : 'text-slate-400'}`}
                >
                  <input
                    type={option.type === 'set' ? 'checkbox' : 'radio'}
                    name={option.id}
                    disabled={!available}
                    checked={
                      option.type === 'set'
                        ? Array.isArray(value) && value.includes(String(choice))
                        : value === choice
                    }
                    onChange={(e) =>
                      update(
                        option.id,
                        option.type === 'set'
                          ? e.target.checked
                            ? [...(value as string[]), String(choice)]
                            : (value as string[]).filter((v) => v !== choice)
                          : choice
                      )
                    }
                    className="h-4 w-4 accent-teal-700"
                  />
                  {label}
                </label>
                {enabled && unmet.length > 0 && (
                  <p className="mt-1 max-w-64 text-xs text-slate-500">
                    {explain(unmet)}
                  </p>
                )}
              </div>
            )
          })}
        </div>
      ) : (
        <input
          id={option.id}
          type={option.type === 'integer' ? 'number' : 'text'}
          step={option.type === 'integer' ? 1 : undefined}
          min={option.minimum}
          disabled={!enabled}
          value={
            typeof value === 'string' || typeof value === 'number' ? value : ''
          }
          onChange={(e) =>
            update(
              option.id,
              option.type === 'integer'
                ? e.target.value === ''
                  ? null
                  : Number(e.target.value)
                : e.target.value
            )
          }
          className="w-full max-w-sm rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-400"
        />
      )}
      {!enabled && (
        <p className="mt-2 text-xs text-slate-500">{explain(dependencies)}</p>
      )}
    </div>
  )
}
function RulePreview({ rule, t }: { rule: Rule; t: Translator }) {
  const [preview, setPreview] = useState('')
  useEffect(() => {
    let active = true
    setPreview('')
    void previewIdentifier(rule)
      .then((text) => {
        if (active) setPreview(text)
      })
      .catch(() => {
        if (active) setPreview(t('app.config.previewInvalid'))
      })
    return () => {
      active = false
    }
  }, [rule, t])
  return (
    <div className="mt-4 rounded-lg bg-slate-50 p-3">
      <p className="text-xs text-slate-500">{t('app.config.previewHint')}</p>
      <output className="mt-1 block break-all font-mono text-sm">
        {preview}
      </output>
    </div>
  )
}
export function ConfigurationEditor({ language }: { language: Language }) {
  const t: Translator = (key, params) =>
    translate(language, key as TextKey, params)
  const [initial] = useState(() => {
    try {
      const saved = localStorage.getItem(storageKey)
      return {
        config: saved ? importConfiguration(saved) : defaults(),
        failed: false
      }
    } catch {
      return { config: defaults(), failed: true }
    }
  })
  const [config, setConfig] = useState<Configuration>(initial.config)
  const [tab, setTab] = useState('resources')
  const [message, setMessage] = useState<Message | null>(
    initial.failed ? { key: 'app.config.restoreFailed' } : null
  )
  const [dirty, setDirty] = useState(false)
  const [darCodes, setDarCodes] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      Object.entries(config.dar).flatMap(([id, rule]) =>
        rule.mode === 'overwrite' ? [[id, rule.code]] : []
      )
    )
  )
  const [darFilter, setDarFilter] = useState('all')
  const importRef = useRef<HTMLInputElement>(null)
  const issues = problems(config)
  const update = (id: string, value: Value) => {
    setConfig((c) => ({ ...c, values: { ...c.values, [id]: value } }))
    setDirty(true)
    setMessage(null)
  }
  const updateRule = (id: string, update: Partial<Rule>) => {
    setConfig((c) => ({
      ...c,
      identifierRules: c.identifierRules.map((rule) =>
        rule.id === id ? { ...rule, ...update } : rule
      )
    }))
    setDirty(true)
    setMessage(null)
  }
  function save() {
    try {
      localStorage.setItem(storageKey, JSON.stringify(config))
      setDirty(false)
      setMessage({ key: 'app.config.saved' })
    } catch {
      setMessage({ key: 'app.config.storageError' })
    }
  }
  async function load(file?: File) {
    if (!file) return
    try {
      const next = importConfiguration(await file.text())
      setConfig(next)
      setDarCodes(
        Object.fromEntries(
          Object.entries(next.dar).flatMap(([id, value]) =>
            value.mode === 'overwrite' ? [[id, value.code]] : []
          )
        )
      )
      setDirty(true)
      setMessage({ key: 'app.config.imported' })
    } catch {
      setMessage({ key: 'app.config.importFailed' })
    }
    if (importRef.current) importRef.current.value = ''
  }
  const exportHref =
    'data:application/json;charset=utf-8,' +
    encodeURIComponent(JSON.stringify(config, null, 2) + '\n')
  return (
    <section
      className="mt-8 rounded-2xl border border-slate-200 bg-white shadow-sm"
      aria-label={t('app.config.title')}
    >
      <div className="p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-teal-700">
              {t('app.config.draft')}
            </p>
            <h2 className="mt-1 text-xl font-semibold">
              {t('app.config.title')}
            </h2>
          </div>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">
            {t(dirty ? 'app.config.unsaved' : 'app.config.ready')}
          </span>
        </div>
        <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
          {t('app.config.integrationHint')}
        </p>
        <div className="mt-5 flex flex-wrap gap-2">
          <Button onClick={save} disabled={issues.length > 0}>
            <Save size={15} />
            {t('app.config.save')}
          </Button>
          {issues.length > 0 ? (
            <Button variant="outline" disabled>
              <Download size={15} />
              {t('app.config.export')}
            </Button>
          ) : (
            <Button asChild variant="outline">
              <a href={exportHref} download="converter-configuration.json">
                <Download size={15} />
                {t('app.config.export')}
              </a>
            </Button>
          )}
          <Button variant="outline" onClick={() => importRef.current?.click()}>
            <Upload size={15} />
            {t('app.config.import')}
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setConfig(defaults())
              setDarCodes({})
              setDirty(true)
              setMessage(null)
            }}
          >
            <RotateCcw size={15} />
            {t('app.config.reset')}
          </Button>
          <input
            ref={importRef}
            type="file"
            accept="application/json,.json"
            aria-label={t('app.config.import')}
            hidden
            onChange={(e) => void load(e.target.files?.[0])}
          />
        </div>
        {message && (
          <p role="status" className="mt-4 text-sm">
            {t(message.key, message.params)}
          </p>
        )}
        {issues.length > 0 && (
          <div
            role="alert"
            className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-800"
          >
            {issues.map((issue, i) => (
              <p key={i}>{t(issue.key, issue.params)}</p>
            ))}
          </div>
        )}
      </div>
      <div
        role="tablist"
        aria-label={t('app.config.title')}
        className="flex flex-wrap border-y border-slate-200 bg-slate-50 px-4"
      >
        {contract.sections.map((section, index) => (
          <button
            key={section.id}
            id={`tab-${section.id}`}
            role="tab"
            aria-selected={tab === section.id}
            aria-controls={`panel-${section.id}`}
            tabIndex={tab === section.id ? 0 : -1}
            onClick={() => setTab(section.id)}
            onKeyDown={(e) => {
              let next = index
              if (e.key === 'ArrowRight')
                next = (index + 1) % contract.sections.length
              else if (e.key === 'ArrowLeft')
                next =
                  (index - 1 + contract.sections.length) %
                  contract.sections.length
              else if (e.key === 'Home') next = 0
              else if (e.key === 'End') next = contract.sections.length - 1
              else return
              e.preventDefault()
              setTab(contract.sections[next].id)
              document
                .getElementById(`tab-${contract.sections[next].id}`)
                ?.focus()
            }}
            className={`border-b-2 px-3 py-4 text-sm font-medium ${tab === section.id ? 'border-teal-700 text-teal-800' : 'border-transparent text-slate-600 hover:text-teal-800'}`}
          >
            {t(section.labelKey)}
          </button>
        ))}
      </div>
      <div
        id={`panel-${tab}`}
        role="tabpanel"
        aria-labelledby={`tab-${tab}`}
        className="p-6"
      >
        {tab === 'resources' && (
          <div className="grid items-start gap-8 lg:grid-cols-[180px_1fr]">
            <nav
              aria-label={t('app.config.anchors')}
              className="lg:sticky lg:top-4"
            >
              <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-500">
                {t('app.config.anchors')}
              </p>
              <div className="flex flex-wrap gap-2 lg:flex-col">
                {resources.map((r) => (
                  <a
                    key={r.id}
                    href={`#resource-${r.id}`}
                    className="rounded px-2 py-1 text-sm text-teal-800 hover:bg-teal-50"
                  >
                    {t(r.labelKey)}
                  </a>
                ))}
              </div>
            </nav>
            <div>
              {resources.map((r, index) => (
                <div key={r.id}>
                  {(index === 0 || resources[index - 1].group !== r.group) && (
                    <h3 className="mb-4 text-sm font-semibold uppercase tracking-wider text-teal-800">
                      {t(groups.find((g) => g.id === r.group)!.key)}
                    </h3>
                  )}
                  <section
                    key={r.id}
                    id={`resource-${r.id}`}
                    className="mb-6 scroll-mt-6 rounded-xl border border-slate-200 p-5"
                  >
                    <h3 className="mb-3 flex items-center gap-3 text-lg font-semibold">
                      <span className="text-xs font-normal text-slate-400">
                        {String(index + 1).padStart(2, '0')}
                      </span>
                      {t(r.labelKey)}
                    </h3>
                    {resourceOptions(r.id).map((option) => (
                      <OptionControl
                        key={option.id}
                        option={option}
                        config={config}
                        update={update}
                        t={t}
                      />
                    ))}
                  </section>
                </div>
              ))}
            </div>
          </div>
        )}
        {['ids', 'terminology', 'output'].includes(tab) && (
          <div className="max-w-3xl">
            {tab === 'terminology' && (
              <p className="mb-4 rounded-lg bg-teal-50 p-4 text-sm">
                {t('app.config.baseline', contract.profileBaseline)}
              </p>
            )}
            {options
              .filter((o) => o.section === tab)
              .map((o) => (
                <OptionControl
                  key={o.id}
                  option={o}
                  config={config}
                  update={update}
                  t={t}
                />
              ))}
          </div>
        )}
        {tab === 'dar' && (
          <>
            <p className="mb-4 text-sm text-slate-600">
              {t('app.config.darHint')}
            </p>
            <label className="mb-5 flex max-w-sm flex-col gap-2 text-sm">
              {t('app.config.filter')}
              <select
                value={darFilter}
                onChange={(e) => setDarFilter(e.target.value)}
                className="rounded-lg border border-slate-300 p-2"
              >
                <option value="all">{t('app.config.allResources')}</option>
                {[...new Set(darFields.map(darResource))].map((r) => (
                  <option key={r} value={r}>
                    {r === 'Laboratory'
                      ? t('resource.Observation.laboratory.label')
                      : r === 'VitalSigns'
                        ? t('resource.Observation.vitalSigns.label')
                        : t(`resource.${r}.label`)}
                  </option>
                ))}
              </select>
            </label>
            <div className="space-y-3">
              {darFields
                .filter(
                  (f) => darFilter === 'all' || darResource(f) === darFilter
                )
                .map((field) => {
                  const current = config.dar[field.id] ?? { mode: 'unchanged' }
                  const enabled = resourceEnabled(
                    field.resourceType,
                    config.values
                  )
                  const code =
                    current.mode === 'overwrite'
                      ? current.code
                      : (darCodes[field.id] ?? '')
                  const set = (
                    mode: 'unchanged' | 'overwrite',
                    code: string
                  ) => {
                    setConfig((c) => ({
                      ...c,
                      dar: {
                        ...c.dar,
                        [field.id]:
                          mode === 'unchanged' ? { mode } : { mode, code }
                      }
                    }))
                    setDirty(true)
                    setMessage(null)
                  }
                  return (
                    <div
                      key={field.id}
                      className="rounded-xl border border-slate-200 p-4"
                    >
                      <div className="mb-3 flex items-center gap-1">
                        <h3 className="text-sm font-medium">
                          {t(`dar.field.${field.id}.label`)}
                        </h3>
                        <Help
                          text={t(`dar.help.${field.semanticGroup}`)}
                          t={t}
                        />
                      </div>
                      <div className="flex flex-wrap gap-4">
                        <label className="flex items-center gap-2 text-sm">
                          <input
                            type="checkbox"
                            disabled={!enabled}
                            checked={current.mode === 'overwrite'}
                            onChange={(e) =>
                              set(
                                e.target.checked ? 'overwrite' : 'unchanged',
                                code
                              )
                            }
                            className="accent-teal-700"
                          />
                          {t('dar.mode.overwrite')}
                        </label>
                        <select
                          aria-label={t(`dar.field.${field.id}.label`)}
                          value={code}
                          disabled={!enabled || current.mode !== 'overwrite'}
                          onChange={(e) => {
                            setDarCodes((old) => ({
                              ...old,
                              [field.id]: e.target.value
                            }))
                            set('overwrite', e.target.value)
                          }}
                          className="max-w-full rounded-lg border border-slate-300 p-2 text-sm disabled:bg-slate-100"
                        >
                          <option value="">{t('app.config.chooseCode')}</option>
                          {field.allowedCodes.map((c) => (
                            <option key={c} value={c}>
                              {t(`dar.code.${c}`)}
                            </option>
                          ))}
                        </select>
                      </div>
                      {!enabled ? (
                        <p className="mt-2 text-xs text-slate-500">
                          {t('app.config.resourceRequired')}
                        </p>
                      ) : current.mode !== 'overwrite' ? (
                        <p className="mt-2 text-xs text-slate-500">
                          {t('disabled.darMode')}
                        </p>
                      ) : null}
                      {field.codeConditions[code] && (
                        <p className="mt-2 text-xs text-amber-800">
                          {t(
                            code === 'as-text'
                              ? 'app.config.narrativeCondition'
                              : 'app.config.notPerformedCondition'
                          )}
                        </p>
                      )}
                    </div>
                  )
                })}
            </div>
          </>
        )}
        {tab === 'identifiers' && (
          <>
            <p className="mb-4 text-sm leading-6 text-slate-600">
              {t('identifier.help')}
            </p>
            <p className="mb-4 text-xs text-slate-500">
              {t('app.config.hashSecurity')}
            </p>
            <Button
              variant="outline"
              onClick={() => {
                setConfig((c) => ({
                  ...c,
                  identifierRules: [
                    ...c.identifierRules,
                    {
                      id: crypto.randomUUID(),
                      enabled: true,
                      resources: ['Patient'],
                      system: '',
                      pattern: '{count:08}'
                    }
                  ]
                }))
                setDirty(true)
                setMessage(null)
              }}
            >
              <Plus size={16} />
              {t('app.config.addRule')}
            </Button>
            {config.identifierRules.length === 0 && (
              <p className="mt-6 text-sm text-slate-500">
                {t('app.config.noRules')}
              </p>
            )}
            {config.identifierRules.map((rule, index) => (
              <section
                key={rule.id}
                className="mt-5 rounded-xl border border-slate-200 p-5"
              >
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="font-semibold">
                    {t('app.config.rule', { number: index + 1 })}
                  </h3>
                  <Button
                    variant="outline"
                    aria-label={t('app.config.removeRule', {
                      number: index + 1
                    })}
                    onClick={() => {
                      setConfig((c) => ({
                        ...c,
                        identifierRules: c.identifierRules.filter(
                          (r) => r.id !== rule.id
                        )
                      }))
                      setDirty(true)
                      setMessage(null)
                    }}
                  >
                    <Trash2 size={15} />
                    {t('app.config.remove')}
                  </Button>
                </div>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={rule.enabled}
                    onChange={(e) =>
                      updateRule(rule.id, { enabled: e.target.checked })
                    }
                    className="accent-teal-700"
                  />
                  {t('identifier.enabled')}
                </label>
                <fieldset className="mt-4">
                  <legend className="mb-2 text-sm font-medium">
                    {t('identifier.resources')}
                  </legend>
                  <div className="flex flex-wrap gap-x-5 gap-y-2">
                    {identifierResources.map((resource) => (
                      <label
                        key={resource}
                        className="flex items-center gap-2 text-sm"
                      >
                        <input
                          type="checkbox"
                          checked={rule.resources.includes(resource)}
                          onChange={(e) =>
                            updateRule(rule.id, {
                              resources: e.target.checked
                                ? [...rule.resources, resource]
                                : rule.resources.filter((r) => r !== resource)
                            })
                          }
                          className="accent-teal-700"
                        />
                        {resource === 'Observation'
                          ? t('app.config.observation')
                          : t(`resource.${resource}.label`)}
                      </label>
                    ))}
                  </div>
                </fieldset>
                <div className="mt-4 grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2 text-sm">
                    {t('identifier.system')}
                    <input
                      value={rule.system}
                      onChange={(e) =>
                        updateRule(rule.id, { system: e.target.value })
                      }
                      className="rounded-lg border border-slate-300 p-2"
                    />
                  </label>
                  <label className="flex flex-col gap-2 text-sm">
                    {t('identifier.pattern')}
                    <input
                      value={rule.pattern}
                      onChange={(e) =>
                        updateRule(rule.id, { pattern: e.target.value })
                      }
                      className="rounded-lg border border-slate-300 p-2 font-mono"
                    />
                  </label>
                </div>
                <RulePreview rule={rule} t={t} />
                <p className="mt-3 break-all font-mono text-xs text-slate-400">
                  {rule.id}
                </p>
              </section>
            ))}
          </>
        )}
      </div>
    </section>
  )
}
