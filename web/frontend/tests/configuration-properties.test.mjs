import test from 'node:test'
import assert from 'node:assert/strict'
import { defaults, importConfiguration, options, darFields } from '../src/configuration.ts'
import { exportPropertiesConfiguration } from '../src/configuration-properties.ts'

const rule = { id: 'a152e771-3d5a-4cb1-9866-35fa6d91fd83', enabled: true,
  resources: ['Patient'], system: 'urn:example:ä', pattern: '{{id}}-{count:08}-{hash}' }
const exported = (config, lang = 'en') => exportPropertiesConfiguration(config, lang)

test('both export languages preserve every option and have identical assignments', () => {
  const config = defaults()
  const de = exported(config, 'de'), en = exported(config)
  assert.deepEqual(importConfiguration(de), config)
  assert.deepEqual(importConfiguration(en), config)
  assert.match(de, /Standardwert/)
  assert.match(en, /Default/)
  assert.doesNotMatch(en, /Standardwert|Zulässige Werte|Inaktiv/)
  const assignments = text => text.split('\n').filter(l => /^(# )?[A-Z][A-Z0-9_]* =/.test(l))
  assert.deepEqual(assignments(de), assignments(en))
  for (const o of options) assert.match(en, new RegExp(`^(# )?${o.propertyName} =`, 'm'))
})

test('disabled controls and unavailable choices retain values; false and none stay active', () => {
  const config = defaults()
  config.values['resource.MedicationAdministration.enabled'] = false
  config.values['medication.MedicationAdministration.treatment'] = 'add-statement'
  config.values['medication.MedicationStatement.treatment'] = 'replace-administration'
  config.values['reference.Condition.encounter'] = 'none'
  config.values['resource.Encounter.enabled'] = false
  const text = exported(config)
  assert.match(text, /^MEDICATION_ADMINISTRATION_ENABLED = false$/m)
  assert.match(text, /^# MEDICATION_ADMINISTRATION_TREATMENT = add-statement$/m)
  assert.match(text, /^# MEDICATION_STATEMENT_TREATMENT = replace-administration$/m)
  assert.deepEqual(importConfiguration(text), config)
  config.values['resource.Encounter.enabled'] = true
  assert.match(exported(config), /^REFERENCE_CONDITION_ENCOUNTER = none$/m)
  config.values['resource.MedicationAdministration.enabled'] = true
  assert.match(exported(config), /^MEDICATION_ADMINISTRATION_TREATMENT = add-statement$/m)
})

test('DAR and identifiers survive disabled resources, rules, and escaped text', () => {
  const config = defaults()
  config.values['resource.Patient.mode'] = 'reference-only'
  config.values['ids.patient.prefix'] = '\u00a0  ä \\ folder\n= # !\t\r\f'
  config.dar['Patient.name.family'] = { mode: 'overwrite', code: 'masked' }
  config.identifierRules = [rule, { ...rule, id: 'da0363a7-c29e-4308-b5df-e9bfc30c95da', enabled: false,
    pattern: '\u2028 leading \\ slash\n{patientId}=x: ü ' }]
  const text = exported(config)
  assert.match(text, /^# DAR_PATIENT_NAME_FAMILY = masked$/m)
  assert.match(text, /^IDENTIFIER_RULE_2_ENABLED = false$/m)
  assert.match(text, /^# IDENTIFIER_RULE_1_PATTERN =/m)
  assert.deepEqual(importConfiguration(text), config)
  assert.deepEqual(importConfiguration(text.replaceAll('\n', '\r\n')), config)
  assert.equal(text.split('\n').filter(l => /^(# )?DAR_[A-Z0-9_]+ =/.test(l)).length, darFields.length)
})

test('rejects malformed or ambiguous properties before replacing the draft', () => {
  const prefix = 'CONFIGURATION_VERSION = 1\n'
  for (const text of ['', 'CONFIGURATION_VERSION = 2', '# CONFIGURATION_VERSION = 1',
    prefix + 'UNKNOWN = true', prefix + '# UNKNOWN = true',
    prefix + 'PATIENT_MODE = neither\n# PATIENT_MODE = reference-only',
    prefix + 'CHECK_INPUT_CONSISTENCY = yes', prefix + 'PATIENT_MODE = invalid',
    prefix + 'PID_PREFIX = broken\\', prefix + 'PID_PREFIX = \\uXYZW',
    prefix + 'DAR_PATIENT_NAME_FAMILY = made-up', prefix + 'IDENTIFIER_RULE_1_ENABLED = true',
    prefix + 'this is not an assignment', prefix + 'OUTPUT_FORMATS = JSON,JSON'])
    assert.throws(() => importConfiguration(text), text)
  assert.equal(importConfiguration(prefix + 'PID_PREFIX = \\u00e4').values['ids.patient.prefix'], 'ä')
})

test('complete allowed option values roundtrip and re-export deterministically', () => {
  for (const o of options) {
    for (const value of o.choices ?? [o.default]) {
      const config = defaults()
      config.values[o.id] = o.type === 'set' ? [value] : value
      const text = exported(config)
      assert.deepEqual(importConfiguration(text), config, o.id)
      assert.equal(exported(importConfiguration(text)), text, o.id)
    }
  }
})

test('diagnosis exports omit the retired inheritance switch and preserve inactive roles', () => {
  const config = defaults()
  for (const lang of ['de', 'en']) {
    const text = exported(config, lang)
    assert.doesNotMatch(text, /ADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER/)
    assert.match(text, /^CONTACT_DIAGNOSES_ROLES = CC,CM,AD,DD,pre-op,post-op,billing$/m)
    assert.deepEqual(importConfiguration(text + '\nADD_MISSING_DIAGNOSES_FROM_SUPER_ENCOUNTER = true\n'), config)
  }
  config.values['resource.Condition.enabled'] = false
  assert.match(exported(config), /^# CONTACT_DIAGNOSES_ROLES = /m)
})
