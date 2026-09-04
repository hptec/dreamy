'use client'

import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { useI18n } from '@/lib/i18n/i18n-context'
import { useAuthStore } from '@/lib/stores/auth-store'
import { LocalizedLink } from '@/components/localized-link'
import { getGuideProgress, saveGuideProgress } from '@/lib/api/marketing-api'

export function GuideChecklist({ guideId, tasks = [] }: { guideId: number; tasks?: { taskId: number; label: string }[] }) {
  const { t } = useI18n()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const authHydrated = useAuthStore((state) => state.hydrated)
  const [done, setDone] = useState<number[]>([])
  const [hydrated, setHydrated] = useState(false)
  const [serverLoaded, setServerLoaded] = useState(false)
  useEffect(() => {
    const key = `dreamy-guide-checklist-${guideId}`
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(key) || '[]')
      setDone(Array.isArray(saved) && saved.every((item): item is number => typeof item === 'number') ? saved : [])
    } catch { setDone([]) }
    setHydrated(true)
  }, [guideId])
  useEffect(() => {
    if (!authHydrated) return
    if (!isAuthenticated) { setServerLoaded(false); return }
    setServerLoaded(false)
    getGuideProgress().then((all) => {
      const serverDone = all[String(guideId)] ?? []
      setDone((localDone) => [...new Set([...serverDone, ...localDone])])
      setServerLoaded(true)
    }).catch(() => setServerLoaded(true))
  }, [authHydrated, isAuthenticated, guideId])
  useEffect(() => {
    if (!hydrated) return
    localStorage.setItem(`dreamy-guide-checklist-${guideId}`, JSON.stringify(done))
    if (authHydrated && isAuthenticated && serverLoaded) void saveGuideProgress(guideId, done).catch(() => undefined)
  }, [done, guideId, hydrated, authHydrated, isAuthenticated, serverLoaded])
  if (!tasks.length) return null
  const completed = tasks.filter((task) => done.includes(task.taskId)).length
  return <div className="space-y-2 border-t border-line pt-3">
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
      <p className="text-xs font-medium text-ink-soft">{t.guide.checklist} · {completed}/{tasks.length} {t.guide.complete}</p>
      {authHydrated && !isAuthenticated && <span className="text-[11px] text-ink-faint">{t.guide.savedOnDevice}</span>}
    </div>
    {tasks.map((task) => <label key={task.taskId} className="flex cursor-pointer items-start gap-2 text-sm text-ink-soft">
      <input type="checkbox" checked={done.includes(task.taskId)} onChange={(e) => setDone((v) => e.target.checked ? [...new Set([...v, task.taskId])] : v.filter((item) => item !== task.taskId))} className="sr-only" />
      <span className={`mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded border ${done.includes(task.taskId) ? 'border-gold bg-gold text-white' : 'border-line'}`}>{done.includes(task.taskId) && <Check className="h-3 w-3" />}</span>
      <span className={done.includes(task.taskId) ? 'text-ink-faint line-through' : ''}>{task.label}</span>
    </label>)}
    {authHydrated && !isAuthenticated && <LocalizedLink href="/account/login?returnTo=/wedding-guides" className="inline-block pt-1 text-xs font-medium text-gold-deep underline-offset-2 hover:underline">{t.guide.saveAcrossDevices}</LocalizedLink>}
  </div>
}
