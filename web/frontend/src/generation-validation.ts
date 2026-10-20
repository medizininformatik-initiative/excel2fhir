import type { Generation } from './GenerationSettings'
import type { Message } from './i18n'

export type GenerationErrors = Partial<Record<keyof Generation, Message>>
export const seedValid = (seed: string) => /^-?\d{1,19}$/.test(seed) && BigInt(seed) >= -(2n ** 63n) && BigInt(seed) < 2n ** 63n
const dateValid = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number(value.slice(0, 4)) > 0 &&
  Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value

export function generationErrors(value: Generation, locations: Record<string, string[]>, today: string): GenerationErrors {
  const errors: GenerationErrors = {}
  const error = (field: keyof Generation, code: string, params?: Message['params']) => {
    errors[field] = { key: `app.generation.error.${code}` as Message['key'], params }
  }
  const number = (field: keyof Generation, amount: number, min: number, max: number, step = 1) => {
    if (!Number.isFinite(amount)) error(field, 'required')
    else if (!Number.isInteger(amount)) error(field, 'integer')
    else if (amount < min) error(field, 'minimum', { min })
    else if (amount > max) error(field, 'maximum', { max })
    else if (amount % step !== 0) error(field, 'step', { step })
  }
  number('population', value.population, 1, value.executionMode === 'bulk' ? 100000 : 1000)
  if (value.executionMode === 'bulk') number('patientsPerRun', value.patientsPerRun ?? 10000, 100, 10000, 100)
  number('minAge', value.minAge, 0, 140)
  number('maxAge', value.maxAge, 0, 140)
  if (!errors.minAge && !errors.maxAge && value.minAge > value.maxAge) {
    error('minAge', 'minAgeOrder'); error('maxAge', 'maxAgeOrder')
  }
  number('yearsOfHistory', value.yearsOfHistory, 0, 140)
  number('timestepDays', value.timestepDays, 1, 365)
  number('maxAttempts', value.maxAttempts, 1, 10000)
  if (value.inpatientOnly) number('inpatientMinDays', value.inpatientMinDays ?? 2, 1, 36500)
  for (const field of ['referenceDate', 'endDate'] as const) {
    if (!dateValid(value[field])) error(field, 'date')
  }
  if (!errors.endDate && value.endDate > today) error('endDate', 'futureDate')
  if (dateValid(value.referenceDate) && dateValid(value.endDate) && value.referenceDate > value.endDate) {
    error('referenceDate', 'dateOrder')
  }
  if (!value.timestampSeeds) {
    for (const field of ['patientSeed', 'clinicianSeed', 'singlePersonSeed'] as const) {
      const seed = value[field]
      if (field === 'singlePersonSeed' && seed === '') continue
      if (!/^-?\d{1,19}$/.test(seed)) error(field, 'seedInteger')
      else if (!seedValid(seed)) error(field, 'seedRange')
      else if (field === 'singlePersonSeed' && value.population !== 1) error(field, 'singlePatient')
    }
  }
  if (!(value.state in locations)) error('state', 'state')
  if (value.city && !(locations[value.state] ?? []).includes(value.city)) error('city', 'city')
  return errors
}
