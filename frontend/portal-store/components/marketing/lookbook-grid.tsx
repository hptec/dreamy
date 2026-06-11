'use client'

/**
 * LookbookGrid（COMP-MKT-S07）：lookbook 卡片网格 + 页内展开详情（E-MKT-07 拉关联商品）。
 * 卡片视觉沿用 inspiration 页既有形态；展开区 ProductCard 复用。
 */

import { useState } from 'react'
import type { StoreLookbook } from '@/lib/api/store-types'
import { getStoreLookbook } from '@/lib/api/marketing-api'
import { ProductCard, productRefToCard } from '@/components/product/product-card'
import { cn } from '@/lib/utils'

export function LookbookGrid({ lookbooks }: { lookbooks: StoreLookbook[] }) {
  const [expanded, setExpanded] = useState<number | null>(null)
  const [details, setDetails] = useState<Record<number, StoreLookbook>>({})
  const [loadingId, setLoadingId] = useState<number | null>(null)

  const toggle = async (id: number) => {
    if (expanded === id) {
      setExpanded(null)
      return
    }
    setExpanded(id)
    if (!details[id]) {
      setLoadingId(id)
      try {
        const detail = await getStoreLookbook(id)
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
              <div className="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent" />
              <div className="absolute bottom-5 left-5 text-canvas">
                <p className="eyebrow text-gold-light">{l.theme}</p>
                <h3 className="font-display text-2xl font-medium">{l.title}</h3>
                {l.description && <p className="mt-1 max-w-[16rem] text-xs text-canvas/70 line-clamp-2">{l.description}</p>}
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
