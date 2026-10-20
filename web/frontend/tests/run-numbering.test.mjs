import { test } from 'node:test'
import assert from 'node:assert/strict'
import { numberRuns, continuationReference } from '../src/run-numbering.ts'

const runs = Array.from({ length: 14 }, (_, index) => ({ id: `run-${String(index + 1).padStart(4, '0')}-uuid`, created: index + 1 }))
const predecessor = runs[12].id

test('numbers 14 runs from oldest to newest and resolves the continuation predecessor', () => {
  const { newestFirst, numbers } = numberRuns(runs)
  assert.deepEqual(newestFirst.map(run => numbers.get(run.id)), [14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1])
  assert.deepEqual(continuationReference(predecessor, numbers), { key: 'app.series.from', params: { run: 13, id: 'run-0013' } })
  assert.equal(runs[0].created, 1)
})

test('deleting an older run closes the gap and updates the continuation reference', () => {
  for (const removedIndex of [0, 6, 11]) {
    const { newestFirst, numbers } = numberRuns(runs.filter((_, index) => index !== removedIndex))
    assert.deepEqual(newestFirst.map(run => numbers.get(run.id)), [13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1])
    assert.equal(numbers.get(runs[13].id), 13)
    assert.deepEqual(continuationReference(predecessor, numbers), { key: 'app.series.from', params: { run: 12, id: 'run-0013' } })
  }
})

test('deleting the predecessor keeps its identifier instead of pointing at another run', () => {
  const { numbers } = numberRuns(runs.filter(run => run.id !== predecessor))
  assert.equal(numbers.get(runs[13].id), 13)
  assert.deepEqual(continuationReference(predecessor, numbers), { key: 'app.series.fromMissing', params: { id: 'run-0013' } })
})

test('deleting the newest run preserves earlier numbers; adding a run continues numbering', () => {
  const { numbers } = numberRuns(runs.slice(0, -1))
  assert.equal(numbers.get(predecessor), 13)
  const updated = numberRuns([...runs, { id: 'new', created: 15 }])
  assert.equal(updated.numbers.get('new'), 15)
  assert.equal(updated.numbers.get(predecessor), 13)
})

test('equal timestamps are ordered consistently and an empty list is supported', () => {
  const tied = [{ id: 'b', created: 1 }, { id: 'a', created: 1 }]
  assert.deepEqual(numberRuns(tied), numberRuns([...tied].reverse()))
  assert.equal(numberRuns([]).numbers.size, 0)
})
