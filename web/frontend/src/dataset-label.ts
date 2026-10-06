import { translate, type Language } from './i18n'

export type DatasetContext = { datasetName?: string; source?: string; sourceName?: string; configuration?: { id: string } }
export function datasetLabel(dataset: DatasetContext & { name: string }, language: Language): string {
  const t = (key: Parameters<typeof translate>[1]) => translate(language, key)
  const source = dataset.source === 'starter' ? t('app.starter') : dataset.source === 'demo' ? t('app.demo')
    : dataset.source === 'synthea-generation' ? t('app.generation.title') : dataset.sourceName || dataset.source
  const id = dataset.configuration?.id
  const name = id === 'editor' ? t('app.defaults') : id === 'default' ? t('app.converterDefaults')
    : id === 'synthea' ? t('app.generation.nativeOutput')
    : id === 'workbook' && ['Konvertierungsoptionen', 'Template Configuration', 'Template configuration', 'default'].includes(dataset.name)
      ? t('app.workbookConfiguration') : dataset.name
  return [dataset.datasetName, source, name].filter(Boolean).join(' · ')
}
