import { useEffect, useState } from 'react'
import { Button } from './components/ui/button'
import { translate, type Language, type TextKey } from './i18n'

const command = 'docker compose -f web/compose.yml --profile data-portal up -d'
export function ServiceLinks({ language }: { language: Language }) {
  const t = (key: TextKey) => translate(language, key)
  const [services, setServices] = useState<{ id: string; available: boolean }[]>([])
  const [failed, setFailed] = useState(false)
  const [copied, setCopied] = useState<TextKey | null>(null)
  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout>
    async function poll() {
      try {
        const response = await fetch('/api/services')
        if (!response.ok) throw new Error()
        const data = await response.json()
        if (active) { setServices(data); setFailed(false) }
      } catch { if (active) setFailed(true) }
      if (active) timer = setTimeout(() => void poll(), 5000)
    }
    void poll()
    return () => { active = false; clearTimeout(timer) }
  }, [])
  async function copy() {
    try { await navigator.clipboard.writeText(command); setCopied('app.upload.copied') }
    catch { setCopied('app.upload.copyFailed') }
  }
  const portal = services.find(service => service.id === 'portal')
  const torch = services.find(service => service.id === 'torch')
  const status = (service: typeof portal) => t(failed ? 'app.services.unknown' : !service ? 'app.services.checking' : service.available ? 'app.upload.ready' : 'app.upload.offline')
  return <section className="mt-6 space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
    <h2 className="text-lg font-semibold">{t('app.services.portal')}</h2>
    <p className="text-sm text-slate-600">{t('app.services.portalHint')}</p>
    <p role="status" className="text-sm">{status(portal)}</p>
    <div className="flex flex-wrap items-center gap-3">
      {portal?.available && <Button asChild><a href="http://localhost:5192" target="_blank" rel="noreferrer">{t('app.services.openPortal')}</a></Button>}
      <p className="text-sm text-slate-600">{t('app.services.loginHint')}</p>
    </div>
    <details className="text-sm">
      <summary className="cursor-pointer font-medium">{t('app.services.start')}</summary>
      <p className="mt-3">{t('app.services.startHint')}</p>
      <code className="my-3 block break-all">{command}</code>
      <Button type="button" variant="outline" onClick={() => void copy()}>{t('app.upload.copy')}</Button>
      {copied && <p role="status" className="mt-2">{t(copied)}</p>}
      <a className="mt-3 block text-teal-800 underline" href="https://github.com/medizininformatik-initiative/excel2fhir/blob/main/web/deployment/README.md" target="_blank" rel="noreferrer">{t('app.services.setupDocs')}</a>
    </details>
    <details className="border-t border-slate-200 pt-4 text-sm">
      <summary className="cursor-pointer font-medium">{t('app.services.torch')}</summary>
      <p className="mt-3 text-slate-600">{t('app.services.torchHint')}</p>
      <p role="status" className="mt-3">{status(torch)}</p>
      {torch?.available && <a className="mt-3 block text-teal-800 underline" href="http://localhost:5193/actuator/health" target="_blank" rel="noreferrer">{t('app.services.openTorch')}</a>}
      <a className="mt-3 block text-teal-800 underline" href="https://medizininformatik-initiative.github.io/torch/" target="_blank" rel="noreferrer">{t('app.services.torchDocs')}</a>
    </details>
  </section>
}
