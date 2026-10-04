import { parsePropertiesConfiguration } from './configuration-properties.ts'
import Ajv2020 from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import contractData from '../../catalog/options/contract.json' with { type: 'json' }
import catalogueData from '../../catalog/dar/generated/catalog.json' with { type: 'json' }
import schema from '../../catalog/options/configuration.schema.json' with { type: 'json' }

export type Value = string | number | boolean | string[] | null
export type Dependency = { option: string; equals: string | number | boolean }
export type Option = {
  id: string
  propertyName: string
  section: string
  type: string
  control: string
  default: Value
  labelKey: string
  helpKey: string
  fhirPath?: string
  minimum?: number
  choices?: (string | number)[]
  choiceLabelKeys?: Record<string, string>
  enabledWhen?: Dependency[]
  choiceDependencies?: Record<string, Dependency[]>
}
export type DarField = {
  id: string
  resourceType: string
  semanticGroup: string
  allowedCodes: string[]
  codeConditions: Record<string, string>
}
export type Rule = {
  id: string
  enabled: boolean
  resources: string[]
  system: string
  pattern: string
}
export type Configuration = {
  schemaVersion: 1
  values: Record<string, Value>
  dar: Record<
    string,
    { mode: 'unchanged' } | { mode: 'overwrite'; code: string }
  >
  identifierRules: Rule[]
}
export type Problem = { key: string; params?: Record<string, string | number> }
export const contract = contractData
export const options = contract.options as Option[]
export const darFields = catalogueData.fields as DarField[]
export const identifierResources = [
  ...new Set(
    contract.resources
      .filter((r) => r.identifierEligible)
      .map((r) => r.resourceType)
  )
].sort()
const byId = new Map(options.map((o) => [o.id, o]))
const ajv = new Ajv2020({ allErrors: true, strict: false })
addFormats(ajv)
const validateSchema = ajv.compile(schema)
export function defaults(): Configuration {
  return {
    schemaVersion: 1,
    values: Object.fromEntries(
      options.map((o) => [o.id, structuredClone(o.default)])
    ),
    dar: {},
    identifierRules: []
  }
}
export function normalize(config: Configuration): Configuration {
  return {
    ...defaults(),
    ...config,
    values: { ...defaults().values, ...config.values },
    dar: config.dar ?? {},
    identifierRules: config.identifierRules ?? []
  }
}
export function optionEnabled(
  id: string,
  values: Configuration['values'],
  visiting = new Set<string>()
): boolean {
  const option = byId.get(id)
  if (!option || visiting.has(id)) return false
  const next = new Set(visiting).add(id)
  return (option.enabledWhen ?? []).every(
    (d) =>
      values[d.option] === d.equals && optionEnabled(d.option, values, next)
  )
}
export function unmetDependencies(
  dependencies: Dependency[] = [],
  values: Configuration['values']
): Dependency[] {
  return dependencies.filter(
    (d) => values[d.option] !== d.equals || !optionEnabled(d.option, values)
  )
}
export function resourceEnabled(
  resource: string,
  values: Configuration['values']
): boolean {
  if (['Patient', 'Location', 'Medication'].includes(resource))
    return values[`resource.${resource}.mode`] === 'generate-reference'
  const names =
    resource === 'Laboratory'
      ? ['Observation.laboratory']
      : resource === 'VitalSigns'
        ? ['Observation.vitalSigns']
        : resource === 'Observation'
          ? ['Observation.laboratory', 'Observation.vitalSigns']
          : [resource]
  return names.some((name) => values[`resource.${name}.enabled`] === true)
}
// Validate raw JSON first, then reject duplicate members before JSON.parse can discard them.
export function parseUniqueJson(text: string): unknown {
  const result: unknown = JSON.parse(text)
  const tokens =
    text.match(/"(?:[^"\\]|\\.)*"|[{}\[\]:,]|[^\s{}\[\]:,]+/g) ?? []
  let index = 0
  function value(): void {
    const token = tokens[index++]
    if (token === '{') {
      const keys = new Set<string>()
      while (tokens[index] !== '}') {
        const key = JSON.parse(tokens[index++]) as string
        if (keys.has(key)) throw new Error('duplicate:' + key)
        keys.add(key)
        index++
        value()
        if (tokens[index] === ',') index++
      }
      index++
    } else if (token === '[') {
      while (tokens[index] !== ']') {
        value()
        if (tokens[index] === ',') index++
      }
      index++
    }
  }
  value()
  return result
}
type Token = { literal: string } | { name: string; width?: number }
export function patternTokens(pattern: string): Token[] {
  const tokens: Token[] = []
  for (let i = 0; i < pattern.length; ) {
    if (pattern.startsWith('{{', i) || pattern.startsWith('}}', i)) {
      tokens.push({ literal: pattern[i] })
      i += 2
    } else if (pattern[i] === '{') {
      const end = pattern.indexOf('}', i)
      if (end < 0) throw new Error('pattern')
      const name = pattern.slice(i + 1, end)
      if (
        [
          'count',
          'patientId',
          'resourceId',
          'resourceType',
          'iteration',
          'hash'
        ].includes(name)
      )
        tokens.push({ name })
      else if (/^count:0[1-9]\d*$/.test(name)) {
        const width = Number(name.slice(7))
        if (!Number.isSafeInteger(width)) throw new Error('pattern')
        tokens.push({ name: 'count', width })
      } else throw new Error('pattern')
      i = end + 1
    } else if (pattern[i] === '}') throw new Error('pattern')
    else {
      tokens.push({ literal: pattern[i++] })
    }
  }
  return tokens
}
export async function previewIdentifier(rule: Rule): Promise<string> {
  const resourceType = rule.resources[0] ?? 'Patient'
  const context = [rule.id.toLowerCase(), resourceType, 'example-1', '0']
  const encoder = new TextEncoder()
  const input = context
    .map((part) => `${encoder.encode(part).length}:${part}`)
    .join('')
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(input))
  const hash = [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
    .slice(0, 32)
  const sample: Record<string, string> = {
    count: '1',
    patientId: 'patient-1',
    resourceId: 'example-1',
    resourceType,
    iteration: '0',
    hash
  }
  return patternTokens(rule.pattern)
    .map((token) =>
      'literal' in token
        ? token.literal
        : sample[token.name].padStart(Math.min(token.width ?? 0, 256), '0')
    )
    .join('')
}
export function problems(input: unknown): Problem[] {
  if (!validateSchema(input)) {
    const error = validateSchema.errors![0]
    return [
      {
        key: 'app.config.invalid',
        params: { path: error.instancePath || '/', detail: error.keyword }
      }
    ]
  }
  const config = normalize(input as Configuration)
  const issues: Problem[] = []
  const ids = new Set<string>()
  for (const rule of config.identifierRules) {
    if (ids.has(rule.id.toLowerCase()))
      issues.push({ key: 'app.config.duplicateRule', params: { id: rule.id } })
    ids.add(rule.id.toLowerCase())
    try {
      patternTokens(rule.pattern)
    } catch {
      issues.push({
        key: 'config.error.invalidPattern',
        params: { rule: rule.id }
      })
    }
  }
  return issues
}
export function importConfiguration(text: string): Configuration {
  const parsed = text.trimStart().startsWith('{')
    ? parseUniqueJson(text)
    : parsePropertiesConfiguration(text)
  // Preserve saved drafts from the editor that included a duplicate procedure control.
  // The remaining Procedure assignment is authoritative.
  if (parsed && typeof parsed === 'object' && 'values' in parsed) {
    const values = (parsed as Configuration).values
    if (values && typeof values === 'object' && 'reference.procedureDiagnosis.encounter' in values) {
      if (!['facility', 'department', 'ward-service', 'none'].includes(String(values['reference.procedureDiagnosis.encounter'])))
        throw new Error('invalid')
      delete values['reference.procedureDiagnosis.encounter']
    }
    if (values && typeof values === 'object' && values['medication.requestTreatment'] === 'omit') {
      values['medication.requestTreatment'] = 'retain'
      values['resource.MedicationRequest.enabled'] = false
    }
    if (values && typeof values === 'object') {
      const oldContactKey = 'reference.DocumentReference.knownInputContact'
      if (oldContactKey in values) {
        if (typeof values[oldContactKey] !== 'boolean') throw new Error('invalid')
        const strategyKey = 'reference.DocumentReference.assignmentStrategy'
        if (!(strategyKey in values))
          values[strategyKey] = values[oldContactKey] ? 'fill-missing' : 'timestamp-only'
        delete values[oldContactKey]
      }
      for (const [source, target, action] of [
        ['MedicationAdministration', 'MedicationStatement', 'add-statement'],
        ['MedicationStatement', 'MedicationAdministration', 'add-administration']
      ]) {
        const oldKey = `medication.${source}.add${target}`
        if (oldKey in values) {
          if (typeof values[oldKey] !== 'boolean') throw new Error('invalid')
          const newKey = `medication.${source}.treatment`
          if (!(newKey in values)) values[newKey] = values[oldKey] ? action : 'retain'
          delete values[oldKey]
        }
      }
    }
  }
  if (problems(parsed).length) throw new Error('invalid')
  return normalize(parsed as Configuration)
}

export function resourceOptions(id: string): Option[] {
  return options.filter(
    (o) =>
      o.section === 'resources' &&
      (o.id.startsWith(`resource.${id}.`) ||
        o.id.startsWith(`reference.${id}.`) ||
        (id === 'Encounter' && o.id.startsWith('contact.')) ||
        (id === 'MedicationRequest' &&
          o.id === 'medication.requestTreatment') ||
        o.id.startsWith(`medication.${id}.`))
  )
}

export function darResource(field: DarField): string {
  if (field.id.startsWith('Laboratory.')) return 'Laboratory'
  if (field.id.startsWith('VitalSigns.')) return 'VitalSigns'
  return field.resourceType
}

export function insertPatternToken(
  value: string,
  token: string,
  selection: { start: number; end: number } | null
): { value: string; cursor: number } {
  const start = Math.max(
    0,
    Math.min(selection?.start ?? value.length, value.length)
  )
  const end = Math.max(start, Math.min(selection?.end ?? start, value.length))
  return {
    value: value.slice(0, start) + token + value.slice(end),
    cursor: start + token.length
  }
}
