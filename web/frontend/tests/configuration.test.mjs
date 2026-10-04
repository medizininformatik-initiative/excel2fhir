import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import {
  defaults,
  contract,
  resourceOptions,
  options,
  darFields,
  darResource,
  optionEnabled,
  resourceEnabled,
  unmetDependencies,
  importConfiguration,
  problems,
  patternTokens,
  previewIdentifier
} from '../src/configuration.ts'

test('defaults cover every option and survive a configuration roundtrip', () => {
  const config = defaults()
  assert.equal(problems(config).length, 0)
  assert.equal(Object.keys(config.values).length, options.length)
  assert.deepEqual(importConfiguration(JSON.stringify(config)), config)
  assert.deepEqual(importConfiguration('{"schemaVersion":1}'), config)
})
test('dependency chains disable controls and choices without losing stored selections', () => {
  const config = defaults()
  config.values['contact.department.enabled'] = false
  assert.equal(
    unmetDependencies(
      [{ option: 'contact.department.enabled', equals: true }],
      config.values
    ).length,
    1
  )
  assert.equal(config.values['reference.Procedure.encounter'], 'department')
  config.values['resource.Encounter.enabled'] = false
  assert.equal(optionEnabled('contact.facility.enabled', config.values), false)
  assert.equal(optionEnabled('contact.diagnoses.levels', config.values), false)
  assert.equal(config.values['contact.facility.enabled'], true)
  config.values['resource.Encounter.enabled'] = true
  assert.equal(optionEnabled('contact.facility.enabled', config.values), true)
  config.values['resource.Medication.mode'] = 'reference-only'
  assert.equal(
    optionEnabled('resource.Medication.existingReference', config.values),
    true
  )
  assert.equal(resourceEnabled('Medication', config.values), false)
})
test('DAR codes are field-specific; incomplete overwrite and unknown fields fail', () => {
  const config = defaults()
  const field = darFields.find((f) => !f.allowedCodes.includes('as-text'))
  config.dar[field.id] = { mode: 'overwrite', code: field.allowedCodes[0] }
  assert.equal(problems(config).length, 0)
  config.dar[field.id].code = 'as-text'
  assert.ok(problems(config).length)
  config.dar[field.id].code = ''
  assert.ok(problems(config).length)
  assert.throws(() =>
    importConfiguration(
      '{"schemaVersion":1,"dar":{"no-such-field":{"mode":"unchanged"}}}'
    )
  )
})
test('imports reject unsupported versions, wrong types, unknown and duplicate members', () => {
  for (const text of [
    '{"schemaVersion":2}',
    '{"schemaVersion":1,"values":{"no-such-option":true}}',
    '{"schemaVersion":1,"values":{"checks.fhirValidation":"false"}}',
    '{"schemaVersion":1,"schemaVersion":1}',
    '{"schemaVersion":1,"values":{"checks.fhirValidation":true,"checks.\\u0066hirValidation":false}}',
    '{"schemaVersion":1,"values":{"output.patientsPerFile":0}}'
  ])
    assert.throws(() => importConfiguration(text))
  const config = defaults()
  config.values['ids.patient.prefix'] = 'Braces {[]} and escaped "quotation"'
  assert.deepEqual(importConfiguration(JSON.stringify(config)), config)
})
const rule = {
  id: 'a152e771-3d5a-4cb1-9866-35fa6d91fd83',
  enabled: true,
  resources: ['Patient'],
  system: 'urn:example:generated',
  pattern:
    '{{id}}-{count:08}-{patientId}-{resourceType}-{resourceId}-{iteration}-{hash}'
}
test('identifier preview uses agreed padding, escaped braces and fixed hash encoding', async () => {
  const input = [rule.id, 'Patient', 'example-1', '0']
    .map((part) => `${Buffer.byteLength(part)}:${part}`)
    .join('')
  const hash = createHash('sha256').update(input).digest('hex').slice(0, 32)
  assert.equal(
    await previewIdentifier(rule),
    `{id}-00000001-patient-1-Patient-example-1-0-${hash}`
  )
  assert.notEqual(
    await previewIdentifier({
      ...rule,
      id: 'da0363a7-c29e-4308-b5df-e9bfc30c95da'
    }),
    await previewIdentifier(rule)
  )
  for (const pattern of ['{unknown}', '{count:8}', '{count:00}', '{count', '}'])
    assert.throws(() => patternTokens(pattern))
})
test('rule IDs are unique and identifier rules validate before saving', () => {
  const config = defaults()
  config.identifierRules = [rule]
  assert.equal(problems(config).length, 0)
  assert.deepEqual(
    importConfiguration(JSON.stringify(config)).identifierRules,
    [rule]
  )
  config.identifierRules.push({ ...rule, id: rule.id.toUpperCase() })
  assert.ok(problems(config).some((p) => p.key === 'app.config.duplicateRule'))
  config.identifierRules = [{ ...rule, pattern: '{bad}' }]
  assert.ok(
    problems(config).some((p) => p.key === 'config.error.invalidPattern')
  )
  config.identifierRules = [{ ...rule, resources: [], enabled: false }]
  assert.ok(problems(config).length)
})

test('resource groups cover each resource option exactly once', () => {
  const ids = contract.resources.flatMap((r) =>
    resourceOptions(r.id).map((o) => o.id)
  )
  assert.equal(new Set(ids).size, ids.length)
  assert.deepEqual(
    ids.sort(),
    options
      .filter((o) => o.section === 'resources')
      .map((o) => o.id)
      .sort()
  )
})

test('DAR distinguishes laboratory and vital-sign Observation selections', () => {
  const config = defaults()
  config.values['resource.Observation.laboratory.enabled'] = false
  config.values['resource.Observation.vitalSigns.enabled'] = true
  const laboratory = darFields.find((f) => f.id.startsWith('Laboratory.'))
  const vitalSigns = darFields.find((f) => f.id.startsWith('VitalSigns.'))
  assert.equal(resourceEnabled(darResource(laboratory), config.values), false)
  assert.equal(resourceEnabled(darResource(vitalSigns), config.values), true)
})
