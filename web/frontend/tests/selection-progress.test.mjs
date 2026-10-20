import { test } from 'node:test'
import assert from 'node:assert/strict'
import { selectionProgress } from '../src/selection-progress.ts'
const progress = { attempts: 240, matched: 3, requested: 10, sinceMatch: 100, slotAttempts: 100, limit: 1000, days: 7, history: 10, exhausted: false }
test('progress survives a partial final log line and rejects malformed data', () => {
  assert.deepEqual(selectionProgress('logs\nWORKBENCH_SELECTION ' + JSON.stringify(progress) + '\nWORKBENCH_SELECTION {'), progress)
  assert.equal(selectionProgress('WORKBENCH_SELECTION {"attempts":-1}'), null)
  assert.equal(selectionProgress('normal output'), null)
})
test('latest complete status reports exhaustion', () => {
  const last = {...progress, exhausted: true, slotAttempts: 1000}
  assert.deepEqual(selectionProgress([progress, last].map(p => 'WORKBENCH_SELECTION ' + JSON.stringify(p)).join('\n')), last)
})
