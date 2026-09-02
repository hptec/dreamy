'use client'

/**
 * LookbookGrid（COMP-MKT-S07）：lookbook 卡片网格 + 页内展开详情（E-MKT-07 拉关联商品）。
 * 卡片视觉沿用 inspiration 页既有形态；展开区 ProductCard 复用。
 */

import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import type { StoreLookbook } from '@/lib/api/store-types'
import { getStoreLookbook } from '@/lib/api/marketing-api'
import { ProductCard, productRefToCard } from '@/components/product/product-card'
import { cn } from '@/lib/utils'
import { useLocale } from '@/lib/i18n/i18n-context'

export function LookbookGrid({ lookbooks, initialId }: { lookbooks: StoreLookbook[]; initialId?: number }) {
  const locale = useLocale()
  const [expanded, setExpanded] = useState<number | null>(initialId ?? null)
  const [details, setDetails] = useState<Record<number, StoreLookbook>>({})
  const [loadingId, setLoadingId] = useState<number | null>(null)

  // Language changes replace the server-rendered cards; discard details fetched
  // under the previous locale so expanded products/titles cannot leak across locales.
  useEffect(() => {
    setDetails({})
    setExpanded(initialId ?? null)
  }, [locale, initialId])

  useEffect(() => {
    if (initialId && lookbooks.some((l) => l.id === initialId)) setExpanded(initialId)
  }, [initialId, lookbooks])

  const toggle = async (id: number) => {
    if (expanded === id) {
      setExpanded(null)
      return
    }
    setExpanded(id)
    if (!details[id]) {
      setLoadingId(id)
      try {
        const detail = await getStoreLookbook(id, locale)
        setDetails((p) => ({ ...p, [id]: detail }))
      } catch {
        /* 展开失败保持卡片态 */
      } finally {
        setLoadingId(null)
      }
    }
  }

  const active = expanded !== null ? details[expanded] : null

  return (
    <div>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {lookbooks.map((l) => (
          <button key={l.id} onClick={() => void toggle(l.id)} className="group cursor-pointer text-left" aria-expanded={expanded === l.id}>
            <div className={cn('relative aspect-[4/5] overflow-hidden rounded-sm bg-muted transition-shadow', expanded === l.id && 'ring-2 ring-gold')}>
              {l.cover || l.fallbackCover ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={l.cover || l.fallbackCover} alt={l.title} className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
              ) : (
                <div className="absolute inset-0 bg-muted" aria-hidden="true" />
              )}
              <div className="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent" />
              <div className="absolute bottom-5 left-5 text-canvas">
                <p className="eyebrow text-gold-light">{l.theme}</p>
                <h3 className="font-display text-2xl font-medium">{l.title}</h3>
                {l.description && <div className="mt-1 max-w-[16rem] line-clamp-2 text-xs text-canvas/70"><ReactMarkdown>{l.description}</ReactMarkdown></div>}
              </div>
            </div>
          </button>
        ))}
      </div>

      {expanded !== null && (
        <div className="mt-10 rounded-sm border border-line bg-surface p-6">
          {loadingId === expanded ? (
            <div className="grid grid-cols-2 gap-5 lg:grid-cols-4" aria-hidden="true">
              {[0, 1, 2, 3].map((i) => <div key={i} className="aspect-[3/4] animate-pulse rounded-sm bg-muted" />)}
            </div>
          ) : active && (active.products ?? []).length > 0 ? (
            <>
              <p className="eyebrow mb-6">Shop the {active.title} edit</p>
              <div className="grid grid-cols-2 gap-x-5 gap-y-10 lg:grid-cols-4">
                {(active.products ?? []).map((p) => <ProductCard key={p.id} product={productRefToCard(p)} />)}
              </div>
            </>
          ) : (
            <p className="py-8 text-center text-sm text-ink-soft">No styles linked to this lookbook yet.</p>
          )}
        </div>
      )}
    </div>
  )
}
