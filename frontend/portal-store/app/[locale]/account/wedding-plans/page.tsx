'use client'

/**
 * 婚礼筹备计划账户页：登录用户跨设备同步的指南任务进度（StoreGuideProgressController）。
 * 三态齐全：guides=null 骨架 / 请求失败可重试 / 空列表空态；progress 拉取失败降级为 0 进度展示。
 */

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { ListChecks } from 'lucide-react'
import { getGuideProgress } from '@/lib/api/marketing-api'
import { request } from '@/lib/api/client'
import type { StoreGuide } from '@/lib/api/store-types'
import { useI18n } from '@/lib/i18n/i18n-context'

export default function WeddingPlansPage() {
  const { locale, t } = useI18n()
  const [guides, setGuides] = useState<StoreGuide[] | null>(null)
  const [progress, setProgress] = useState<Record<string, number[]> | null>(null)
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    setGuides(null)
    setProgress(null)
    setFailed(false)
    request<{ items: StoreGuide[] }>('/api/store/content/guides', { query: { locale } })
      .then((r) => setGuides(r.items ?? []))
      .catch(() => setFailed(true))
    getGuideProgress()
      .then(setProgress)
      .catch(() => setProgress({}))
  }, [locale, attempt])

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">{t.account.weddingPlans.title}</h1>
      <p className="mt-2 text-sm text-ink-soft">{t.account.weddingPlans.subtitle}</p>
      <div className="mt-8 space-y-4">
        {failed ? (
          <div className="flex flex-col items-center gap-4 rounded-sm border border-dashed border-line py-16 text-center">
            <p className="text-ink-soft">{t.error.generic}</p>
            <button onClick={() => setAttempt((a) => a + 1)} className="btn-outline">{t.common.retry}</button>
          </div>
        ) : guides === null ? (
          <div className="space-y-4" aria-hidden="true">
            {[0, 1, 2].map((i) => <div key={i} className="h-24 animate-pulse rounded-sm bg-muted" />)}
          </div>
        ) : guides.length === 0 ? (
          <div className="flex flex-col items-center gap-4 rounded-sm border border-dashed border-line py-16 text-center">
            <ListChecks className="h-10 w-10 text-line" strokeWidth={1} />
            <p className="text-ink-soft">{t.account.weddingPlans.empty}</p>
          </div>
        ) : (
          guides.map((g) => {
            const done = progress?.[String(g.id)]?.length ?? 0
            const total = g.tasks?.length ?? g.tasksCount ?? 0
            return (
              <Link key={g.id} href={`/wedding-guides#guide-${g.id}`} className="block rounded-sm border border-line bg-surface p-5 hover:border-gold">
                <div className="flex items-center justify-between gap-4">
                  <span className="font-display text-xl">{g.title}</span>
                  <span className="text-sm text-ink-soft">{done}/{total}</span>
                </div>
                <div className="mt-3 h-1.5 bg-muted"><div className="h-full bg-gold" style={{ width: total ? `${Math.min(100, done / total * 100)}%` : '0%' }} /></div>
              </Link>
            )
          })
        )}
      </div>
    </div>
  )
}
