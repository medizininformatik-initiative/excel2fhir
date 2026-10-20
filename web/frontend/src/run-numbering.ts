import type { Message } from './i18n'

type Run = { id: string; created: number }

export function numberRuns<T extends Run>(runs: T[]) {
  const newestFirst = [...runs].sort((a, b) => b.created - a.created || b.id.localeCompare(a.id))
  const numbers = new Map(newestFirst.map((run, index) => [run.id, newestFirst.length - index]))
  return { newestFirst, numbers }
}

export function continuationReference(sourceRun: string, numbers: Map<string, number>): Message {
  const number = numbers.get(sourceRun)
  return number === undefined
    ? { key: 'app.series.fromMissing', params: { id: sourceRun.slice(0, 8) } }
    : { key: 'app.series.from', params: { run: number, id: sourceRun.slice(0, 8) } }
}
