'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { getGuideProgress } from '@/lib/api/marketing-api'
import { request } from '@/lib/api/client'
import type { StoreGuide } from '@/lib/api/store-types'
import { useI18n } from '@/lib/i18n/i18n-context'

export default function WeddingPlansPage() {
  const { locale } = useI18n()
  const [guides, setGuides] = useState<StoreGuide[]>([])
  const [progress, setProgress] = useState<Record<string, number[]>>({})
  useEffect(() => {
    request<{ items: StoreGuide[] }>('/api/store/content/guides', { query: { locale } }).then((r) => setGuides(r.items ?? [])).catch(() => undefined)
    getGuideProgress().then(setProgress).catch(() => undefined)
  }, [locale])
  return <div><h1 className="font-display text-3xl font-medium">Wedding Plans</h1><p className="mt-2 text-sm text-ink-soft">Your saved wedding guide progress.</p><div className="mt-8 space-y-4">{guides.map((g) => { const done = progress[String(g.id)]?.length ?? 0; const total = g.tasks?.length ?? g.tasksCount ?? 0; return <Link key={g.id} href={`/wedding-guides#guide-${g.id}`} className="block rounded-sm border border-line bg-surface p-5 hover:border-gold"><div className="flex items-center justify-between gap-4"><span className="font-display text-xl">{g.title}</span><span className="text-sm text-ink-soft">{done}/{total}</span></div><div className="mt-3 h-1.5 bg-muted"><div className="h-full bg-gold" style={{ width: total ? `${Math.min(100, done / total * 100)}%` : '0%' }} /></div></Link> })}</div></div>
}
