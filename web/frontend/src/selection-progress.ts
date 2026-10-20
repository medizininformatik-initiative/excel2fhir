export type SelectionProgress = { attempts: number; matched: number; requested: number; sinceMatch: number; slotAttempts: number; limit: number; days: number; history: number; exhausted: boolean }
export function selectionProgress(log: string): SelectionProgress | null {
  for (const line of log.split('\n').reverse()) {
    if (!line.startsWith('WORKBENCH_SELECTION ')) continue
    try {
      const value = JSON.parse(line.slice('WORKBENCH_SELECTION '.length))
      if (['attempts', 'matched', 'requested', 'sinceMatch', 'slotAttempts', 'limit', 'days', 'history'].every(k => Number.isSafeInteger(value[k]) && value[k] >= 0) && typeof value.exhausted === 'boolean') return value
    } catch { /* An in-progress log write can leave the final line incomplete. */ }
  }
  return null
}
