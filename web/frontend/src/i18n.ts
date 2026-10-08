import de from '../../catalog/options/de.json'
import en from '../../catalog/options/en.json'

export type Language = 'de' | 'en'
export type TextKey = keyof typeof de
export type Message = { key: TextKey; params?: Record<string, string | number> }
const dictionaries = { de, en }

export function initialLanguage(): Language {
  return localStorage.getItem('workbenchLanguage') === 'en' ? 'en' : 'de'
}

export function translate(language: Language, key: TextKey, params: Message['params'] = {}): string {
  let value: string = dictionaries[language][key]
  for (const [name, replacement] of Object.entries(params)) {
    value = value.replaceAll(`{${name}}`, String(replacement))
  }
  return value
}

export class InterfaceError extends Error {
  constructor(public messageKey: TextKey, public params: Message['params'] = {}) {
    super(messageKey)
  }
}

export function errorMessage(error: unknown): Message {
  return error instanceof InterfaceError
    ? { key: error.messageKey, params: error.params }
    : { key: 'app.error.unexpected' }
}
