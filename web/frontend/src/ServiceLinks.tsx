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
  return <div className="mt-6 grid gap-6 md:grid-cols-2">
    {(['portal', 'torch'] as const).map(id => {
      const service = services.find(item => item.id === id)
      return <section key={id} className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="text-lg font-semibold">{t(id === 'portal' ? 'app.services.portal' : 'app.services.torch')}</h2>
        <p className="text-sm text-slate-600">{t(id === 'portal' ? 'app.services.portalHint' : 'app.services.torchHint')}</p>
        <p role="status" className="text-sm">{t(failed ? 'app.services.unknown' : !service ? 'app.services.checking' : service.available ? 'app.upload.ready' : 'app.upload.offline')}</p>
        {service?.available && <a className="block text-sm text-teal-800 underline" href={id === 'portal' ? 'https://localhost:5192' : 'http://localhost:5193/actuator/health'} target="_blank" rel="noreferrer">{t(id === 'portal' ? 'app.services.openPortal' : 'app.services.openTorch')}</a>}
        {id === 'torch' && <a className="block text-sm text-teal-800 underline" href="https://medizininformatik-initiative.github.io/torch/" target="_blank" rel="noreferrer">{t('app.services.torchDocs')}</a>}
        {service && !service.available && <details className="text-sm"><summary className="cursor-pointer font-medium">{t('app.services.start')}</summary>
          <p className="mt-3">{t('app.services.startHint')}</p>
          <code className="my-3 block break-all">{command}</code>
          <Button type="button" variant="outline" onClick={() => void copy()}>{t('app.upload.copy')}</Button>
          {copied && <p role="status" className="mt-2">{t(copied)}</p>}
          <a className="mt-3 block text-teal-800 underline" href="https://github.com/medizininformatik-initiative/excel2fhir/blob/main/web/deployment/README.md" target="_blank" rel="noreferrer">{t('app.services.setupDocs')}</a>
        </details>}
      </section>
    })}
  </div>
}
