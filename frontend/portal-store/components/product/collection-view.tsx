'use client'

import { useState, useMemo } from 'react'
import { SlidersHorizontal, X, Check, ChevronDown } from 'lucide-react'
import type { Product } from '@/data/types'
import { ProductCard } from '@/components/product/product-card'
import { QuickViewModal } from '@/components/product/quick-view-modal'
import { cn } from '@/lib/utils'

type FilterKey = 'colors' | 'silhouette' | 'fabric' | 'neckline' | 'length' | 'occasion'

const SORTS = ['Featured', 'Newest', 'Price: Low to High', 'Price: High to Low', 'Best Selling', 'Top Rated'] as const

function unique(products: Product[], pick: (p: Product) => (string | undefined)[]) {
  const set = new Set<string>()
  products.forEach((p) => pick(p).forEach((v) => v && set.add(v)))
  return [...set]
}

export function CollectionView({
  title,
  description,
  products,
  heroImage,
  subTabs
}: {
  title: string
  description?: string
  products: Product[]
  heroImage?: string
  subTabs?: { label: string; value: string }[]
}) {
  const [filters, setFilters] = useState<Record<FilterKey, string[]>>({ colors: [], silhouette: [], fabric: [], neckline: [], length: [], occasion: [] })
  const [sort, setSort] = useState<(typeof SORTS)[number]>('Featured')
  const [mobileFilter, setMobileFilter] = useState(false)
  const [activeTab, setActiveTab] = useState<string>('All')
  const [quickView, setQuickView] = useState<Product | null>(null)

  const facets = useMemo(() => ({
    colors: unique(products, (p) => p.colors.map((c) => c.name)),
    silhouette: unique(products, (p) => [p.silhouette]),
    fabric: unique(products, (p) => [p.fabric]),
    neckline: unique(products, (p) => [p.neckline]),
    length: unique(products, (p) => [p.length]),
    occasion: unique(products, (p) => p.occasion)
  }), [products])

  const toggle = (key: FilterKey, value: string) => {
    setFilters((prev) => ({ ...prev, [key]: prev[key].includes(value) ? prev[key].filter((v) => v !== value) : [...prev[key], value] }))
  }

  const clearAll = () => setFilters({ colors: [], silhouette: [], fabric: [], neckline: [], length: [], occasion: [] })
  const activeCount = Object.values(filters).flat().length

  const filtered = useMemo(() => {
    let list = products
    if (subTabs && activeTab !== 'All') list = list.filter((p) => p.subCategory === activeTab || p.occasion.includes(activeTab))
    list = list.filter((p) => {
      if (filters.colors.length && !p.colors.some((c) => filters.colors.includes(c.name))) return false
      if (filters.silhouette.length && !filters.silhouette.includes(p.silhouette ?? '')) return false
      if (filters.fabric.length && !filters.fabric.includes(p.fabric ?? '')) return false
      if (filters.neckline.length && !filters.neckline.includes(p.neckline ?? '')) return false
      if (filters.length.length && !filters.length.includes(p.length ?? '')) return false
      if (filters.occasion.length && !p.occasion.some((o) => filters.occasion.includes(o))) return false
      return true
    })
    const sorted = [...list]
    switch (sort) {
      case 'Newest': sorted.sort((a, b) => Number(b.isNew ?? 0) - Number(a.isNew ?? 0)); break
      case 'Price: Low to High': sorted.sort((a, b) => a.price - b.price); break
      case 'Price: High to Low': sorted.sort((a, b) => b.price - a.price); break
      case 'Best Selling': sorted.sort((a, b) => b.reviewCount - a.reviewCount); break
      case 'Top Rated': sorted.sort((a, b) => b.rating - a.rating); break
    }
    return sorted
  }, [products, filters, sort, activeTab, subTabs])

  return (
    <div>
      {/* Hero / 标题区 */}
      <div className="relative overflow-hidden bg-muted">
        {heroImage && (
          <>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={heroImage} alt={title} className="absolute inset-0 h-full w-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-b from-canvas/30 via-canvas/40 to-canvas/85" />
          </>
        )}
        <div className="container-luxe relative py-16 text-center lg:py-24">
          <p className="eyebrow mb-3">Dreamy Collection</p>
          <h1 className="heading-display text-4xl sm:text-5xl lg:text-6xl">{title}</h1>
          {description && <p className="mx-auto mt-4 max-w-xl text-ink-soft">{description}</p>}
        </div>
      </div>

      <div className="container-luxe py-10">
        {/* Sub tabs */}
        {subTabs && (
          <div className="mb-8 flex flex-wrap justify-center gap-2 border-b border-line pb-6">
            {[{ label: 'All', value: 'All' }, ...subTabs].map((t) => (
              <button
                key={t.value}
                onClick={() => setActiveTab(t.value)}
                className={cn('cursor-pointer rounded-full px-5 py-2 text-[13px] font-medium uppercase tracking-luxe transition-colors', activeTab === t.value ? 'bg-ink text-canvas' : 'border border-line text-ink-soft hover:border-ink')}
              >
                {t.label}
              </button>
            ))}
          </div>
        )}

        <div className="flex gap-10">
          {/* 桌面 Filter 侧栏 */}
          <aside className="hidden w-64 shrink-0 lg:block">
            <div className="sticky top-28">
              <div className="mb-4 flex items-center justify-between">
                <p className="eyebrow">Filter</p>
                {activeCount > 0 && <button onClick={clearAll} className="cursor-pointer text-xs text-gold-deep underline">Clear all ({activeCount})</button>}
              </div>
              <FilterGroups facets={facets} filters={filters} toggle={toggle} />
            </div>
          </aside>

          {/* 商品区 */}
          <div className="flex-1">
            <div className="mb-6 flex items-center justify-between">
              <p className="text-sm text-ink-soft">{filtered.length} {filtered.length === 1 ? 'style' : 'styles'}</p>
              <div className="flex items-center gap-3">
                <button onClick={() => setMobileFilter(true)} className="flex cursor-pointer items-center gap-2 rounded-sm border border-line px-4 py-2 text-xs uppercase tracking-luxe lg:hidden">
                  <SlidersHorizontal className="h-3.5 w-3.5" /> Filter {activeCount > 0 && `(${activeCount})`}
                </button>
                <div className="relative">
                  <label htmlFor="sort" className="sr-only">Sort by</label>
                  <select id="sort" value={sort} onChange={(e) => setSort(e.target.value as any)} className="cursor-pointer appearance-none rounded-sm border border-line bg-surface py-2 pl-4 pr-9 text-xs uppercase tracking-luxe outline-none focus:border-gold">
                    {SORTS.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2" />
                </div>
              </div>
            </div>

            {filtered.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
                <p className="font-display text-2xl">No styles match your filters</p>
                <p className="text-sm text-ink-soft">Try removing a filter or exploring another color.</p>
                <button onClick={clearAll} className="btn-outline">Clear Filters</button>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-3">
                {filtered.map((p) => <ProductCard key={p.id} product={p} onQuickView={setQuickView} />)}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 移动 Filter 抽屉 */}
      {mobileFilter && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-ink/40" onClick={() => setMobileFilter(false)} />
          <div className="absolute right-0 top-0 h-full w-[85%] max-w-sm animate-fadeup overflow-y-auto bg-canvas p-6">
            <div className="mb-6 flex items-center justify-between">
              <p className="font-display text-2xl">Filter</p>
              <button onClick={() => setMobileFilter(false)} className="cursor-pointer p-1"><X className="h-5 w-5" /></button>
            </div>
            <FilterGroups facets={facets} filters={filters} toggle={toggle} />
            <div className="mt-8 flex gap-3">
              <button onClick={clearAll} className="btn-outline flex-1">Clear</button>
              <button onClick={() => setMobileFilter(false)} className="btn-primary flex-1">Show {filtered.length}</button>
            </div>
          </div>
        </div>
      )}

      {quickView && <QuickViewModal product={quickView} onClose={() => setQuickView(null)} />}
    </div>
  )
}

function FilterGroups({ facets, filters, toggle }: { facets: Record<FilterKey, string[]>; filters: Record<FilterKey, string[]>; toggle: (k: FilterKey, v: string) => void }) {
  const groups: { key: FilterKey; label: string }[] = [
    { key: 'colors', label: 'Color' },
    { key: 'occasion', label: 'Occasion' },
    { key: 'silhouette', label: 'Silhouette' },
    { key: 'fabric', label: 'Fabric' },
    { key: 'neckline', label: 'Neckline' },
    { key: 'length', label: 'Length' }
  ]
  return (
    <div className="space-y-6">
      {groups.map((g) => facets[g.key].length > 0 && (
        <FilterAccordion key={g.key} label={g.label}>
          {g.key === 'colors' ? (
            <div className="flex flex-wrap gap-2 pt-1">
              {facets.colors.map((c) => (
                <button key={c} onClick={() => toggle('colors', c)} className={cn('cursor-pointer rounded-full border px-3 py-1.5 text-xs transition-colors', filters.colors.includes(c) ? 'border-gold bg-gold/10 text-gold-deep' : 'border-line text-ink-soft hover:border-ink')}>{c}</button>
              ))}
            </div>
          ) : (
            <ul className="space-y-1.5 pt-1">
              {facets[g.key].map((v) => (
                <li key={v}>
                  <button onClick={() => toggle(g.key, v)} className="flex w-full cursor-pointer items-center gap-2.5 text-sm text-ink-soft hover:text-ink">
                    <span className={cn('flex h-4 w-4 items-center justify-center rounded-sm border transition-colors', filters[g.key].includes(v) ? 'border-gold bg-gold text-white' : 'border-line')}>
                      {filters[g.key].includes(v) && <Check className="h-3 w-3" />}
                    </span>
                    {v}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </FilterAccordion>
      ))}
    </div>
  )
}

function FilterAccordion({ label, children }: { label: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(true)
  return (
    <div className="border-b border-line/60 pb-4">
      <button onClick={() => setOpen(!open)} className="flex w-full cursor-pointer items-center justify-between py-1 text-sm font-medium">
        {label}
        <ChevronDown className={cn('h-4 w-4 transition-transform', open && 'rotate-180')} />
      </button>
      {open && <div className="mt-2">{children}</div>}
    </div>
  )
}
