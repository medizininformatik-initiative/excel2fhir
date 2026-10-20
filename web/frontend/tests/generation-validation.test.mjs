import test from 'node:test'
import assert from 'node:assert/strict'
import { generationErrors } from '../src/generation-validation.ts'

const valid = {
  executionMode: 'bulk', outputMode: 'kds', population: 200, patientsPerRun: 100,
  minAge: 18, maxAge: 120, yearsOfHistory: 10, timestepDays: 7, maxAttempts: 1000,
  referenceDate: '2026-09-12', endDate: '2026-09-12',
  patientSeed: '9223372036854775807', clinicianSeed: '-9223372036854775808', singlePersonSeed: '',
  state: 'Massachusetts', city: '', inpatientOnly: false, inpatientMinDays: 2,
  gender: '', patientFilter: 'alive', overflow: false, modules: [], keepModule: '', veteranPopulation: false
}
const check = patch => generationErrors({ ...valid, ...patch }, { Massachusetts: ['Boston'] }, '2026-10-16')
const code = value => `app.generation.error.${value}`

test('subrun errors identify the field and distinguish range, step and missing values', () => {
  for (const [patientsPerRun, reason] of [[99, 'minimum'], [10001, 'maximum'], [150, 'step'], [100.5, 'integer'], [NaN, 'required']]) {
    const errors = check({ patientsPerRun })
    assert.deepEqual(Object.keys(errors), ['patientsPerRun'])
    assert.equal(errors.patientsPerRun.key, code(reason))
  }
  assert.deepEqual(check({ patientsPerRun: 10000 }), {})
  assert.deepEqual(check({ executionMode: 'standard', patientsPerRun: 99 }), {})
})
test('patient limit follows execution mode and zero-valued fields may not be blank', () => {
  assert.deepEqual(check({ population: 100000 }), {})
  assert.equal(check({ executionMode: 'standard', population: 1001 }).population.params.max, 1000)
  assert.equal(check({ minAge: NaN }).minAge.key, code('required'))
  assert.deepEqual(check({ minAge: 0, yearsOfHistory: 0 }), {})
})
test('calendar validity, future end and reference order remain independent', () => {
  assert.equal(check({ referenceDate: '2026-02-30' }).referenceDate.key, code('date'))
  assert.equal(check({ endDate: '' }).endDate.key, code('date'))
  assert.equal(check({ endDate: '2026-10-17' }).endDate.key, code('futureDate'))
  assert.deepEqual(check({ referenceDate: '2026-09-13' }), { referenceDate: { key: code('dateOrder'), params: undefined } })
  assert.deepEqual(check({ referenceDate: '2024-02-29' }), {})
})
test('age conflicts mark both fields; disabled inpatient settings do not block submission', () => {
  const errors = check({ minAge: 90, maxAge: 18 })
  assert.equal(errors.minAge.key, code('minAgeOrder'))
  assert.equal(errors.maxAge.key, code('maxAgeOrder'))
  assert.deepEqual(check({ inpatientMinDays: 0 }), {})
  assert.equal(check({ inpatientOnly: true, inpatientMinDays: 0 }).inpatientMinDays.key, code('minimum'))
})
test('64-bit seeds are exact and timestamp mode bypasses manual seed errors', () => {
  assert.deepEqual(check({}), {})
  assert.equal(check({ patientSeed: '9223372036854775808' }).patientSeed.key, code('seedRange'))
  assert.equal(check({ clinicianSeed: '1.5' }).clinicianSeed.key, code('seedInteger'))
  assert.equal(check({ singlePersonSeed: '1' }).singlePersonSeed.key, code('singlePatient'))
  assert.deepEqual(check({ timestampSeeds: true, patientSeed: '', clinicianSeed: 'x', singlePersonSeed: '1' }), {})
})
test('unknown locations point to the relevant select without throwing', () => {
  assert.equal(check({ state: 'unknown' }).state.key, code('state'))
  assert.equal(check({ city: 'unknown' }).city.key, code('city'))
})
