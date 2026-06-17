'use client'

/**
 * CollectionView（COMP-CAT-S02，layout-keep + data-swap）：
 * 接收 RSC 传入的 Paginated 数据；筛选/排序控件改为路由 searchParams 驱动
 * （color/size/price/sort/cat/page/a_<key> → URL → RSC refetch，FORM-CAT-S03 单一事实源）。
 * facet 维度：颜色/尺码/价格 + 动态属性维度（E-CAT-27 filterDims，attribute_def 字典驱动，
 * URL 形态 a_<key>=v1|v2，同 key 多值 OR、跨 key AND）。
 */

import { useState, type ReactNode } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { SlidersHorizontal, X, Check, ChevronDown } from 'lucide-react'
import type { Paginated, StoreFilterDim, StoreProductCard } from '@/lib/api/store-types'
import { ProductCard } from '@/components/product/product-card'
import { QuickViewModal } from '@/components/product/quick-view-modal'
import { cn } from '@/lib/utils'

const SORTS = [
  { label: 'Featured', value: 'recommended' },
  { label: 'Newest', value: 'newest' },
  { label: 'Price: Low to High', value: 'price_asc' },
  { label: 'Price: High to Low', value: 'price_desc' }
] as const

const SIZE_OPTIONS = ['US 0', 'US 2', 'US 4', 'US 6', 'US 8', 'US 10', 'US 12', 'US 14']

const PRICE_RANGES = [
  { label: 'Under $200', min: undefined, max: 200 },
  { label: '$200 – $500', min: 200, max: 500 },
  { label: '$500 – $1,000', min: 500, max: 1000 },
  { label: '$1,000 & up', min: 1000, max: undefined }
] as const

export function CollectionView({
  title,
  description,
  data,
  heroImage,
  colorOptions = [],
  filterDims = [],
  subTabs,
  basePath
}: {
  title: string
  description?: string
  data: Paginated<StoreProductCard> | null
  heroImage?: string
  /** Shop by Color 色板标签名（E-CAT-07 派生；空则不渲染颜色组） */
  colorOptions?: string[]
  /** 动态属性筛选维度（E-CAT-27；空则不渲染属性组） */
  filterDims?: StoreFilterDim[]
  /** 子分类 tab（value=category id 字符串，cat searchParam 驱动） */
  subTabs?: { label: string; value: string }[]
  basePath: string
}) {
  const router = useRouter()
  const params = useSearchParams()
  const [mobileFilter, setMobileFilter] = useState(false)
  const [quickView, setQuickView] = useState<StoreProductCard | null>(null)

  const color = params.get('color') ?? ''
  const size = params.get('size') ?? ''
  const price = params.get('price') ?? ''
  const sort = params.get('sort') ?? 'recommended'
  const cat = params.get('cat') ?? ''
  const page = Math.max(1, Number(params.get('page') ?? '1') || 1)

  // 动态属性已选值：a_<key>=v1|v2（值逐项 encodeURIComponent，防值内 '|' 撞分隔符）
  const attrSelections: Record<string, string[]> = {}
  for (const [k, v] of params.entries()) {
    if (k.startsWith('a_') && v) {
      attrSelections[k.slice(2)] = v.split('|').filter(Boolean).map((x) => decodeURIComponent(x))
    }
  }

  const navigate = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params.toString())
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    // 任何筛选变化重置页码
    if (!('page' in patch)) next.delete('page')
    const qs = next.toString()
    router.push(qs ? `${basePath}?${qs}` : basePath)
  }

  const toggleAttr = (key: string, value: string) => {
    const cur = attrSelections[key] ?? []
    const next = cur.includes(value) ? cur.filter((x) => x !== value) : [...cur, value]
    navigate({ ['a_' + key]: next.length ? next.map((x) => encodeURIComponent(x)).join('|') : null })
  }

  const attrActiveCount = Object.keys(attrSelections).length
  const activeCount = [color, size, price].filter(Boolean).length + attrActiveCount
  const clearAll = () => {
    const patch: Record<string, string | null> = { color: null, size: null, price: null }
    for (const key of Object.keys(attrSelections)) patch['a_' + key] = null
    navigate(patch)
  }

  const items = data?.data ?? []
  const total = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 1

  const filterGroups = (
    <FilterGroups
      colorOptions={colorOptions}
      filterDims={filterDims}
      attrSelections={attrSelections}
      color={color}
      size={size}
      price={price}
      onColor={(v) => navigate({ color: v === color ? null : v })}
      onSize={(v) => navigate({ size: v === size ? null : v })}
      onPrice={(v) => navigate({ price: v === price ? null : v })}
      onAttr={toggleAttr}
    />
  )

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
        {/* Sub tabs（子分类，cat searchParam 驱动） */}
        {subTabs && subTabs.length > 0 && (
          <div className="mb-8 flex flex-wrap justify-center gap-2 border-b border-line pb-6">
            {[{ label: 'All', value: '' }, ...subTabs].map((t) => {
              const catPatch: Record<string, string | null> = { cat: t.value || null }
              for (const key of Object.keys(attrSelections)) catPatch['a_' + key] = null
              return (
                <button
                  key={t.value || 'all'}
                  onClick={() => navigate(catPatch)}
                  className={cn('cursor-pointer rounded-full px-5 py-2 text-[13px] font-medium uppercase tracking-luxe transition-colors', cat === t.value ? 'bg-ink text-canvas' : 'border border-line text-ink-soft hover:border-ink')}
                >
                  {t.label}
                </button>
              )
            })}
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
              {filterGroups}
            </div>
          </aside>

          {/* 商品区 */}
          <div className="flex-1">
            <div className="mb-6 flex items-center justify-between">
              <p className="text-sm text-ink-soft">{total} {total === 1 ? 'style' : 'styles'}</p>
              <div className="flex items-center gap-3">
                <button onClick={() => setMobileFilter(true)} className="flex cursor-pointer items-center gap-2 rounded-sm border border-line px-4 py-2 text-xs uppercase tracking-luxe lg:hidden">
                  <SlidersHorizontal className="h-3.5 w-3.5" /> Filter {activeCount > 0 && `(${activeCount})`}
                </button>
                <div className="relative">
                  <label htmlFor="sort" className="sr-only">Sort by</label>
                  <select
                    id="sort"
                    value={sort}
                    onChange={(e) => navigate({ sort: e.target.value === 'recommended' ? null : e.target.value })}
                    className="cursor-pointer appearance-none rounded-sm border border-line bg-surface py-2 pl-4 pr-9 text-xs uppercase tracking-luxe outline-none focus:border-gold"
                  >
                    {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2" />
                </div>
              </div>
            </div>

            {data === null ? (
              <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
                <p className="font-display text-2xl">We couldn&apos;t load this collection</p>
                <p className="text-sm text-ink-soft">Please check your connection and try again.</p>
                <button onClick={() => router.refresh()} className="btn-outline">Try Again</button>
              </div>
            ) : items.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
                <p className="font-display text-2xl">No styles match your filters</p>
                <p className="text-sm text-ink-soft">Try removing a filter or exploring another color.</p>
                <button onClick={clearAll} className="btn-outline">Clear Filters</button>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-3">
                  {items.map((p) => <ProductCard key={p.id} product={p} onQuickView={setQuickView} />)}
                </div>
                {totalPages > 1 && (
                  <div className="mt-12 flex items-center justify-center gap-4 text-sm">
                    <button
                      disabled={page <= 1}
                      onClick={() => navigate({ page: page <= 2 ? null : String(page - 1) })}
                      className="btn-outline px-5 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Previous
                    </button>
                    <span className="text-ink-soft">Page {page} of {totalPages}</span>
                    <button
                      disabled={page >= totalPages}
                      onClick={() => navigate({ page: String(page + 1) })}
                      className="btn-outline px-5 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Next
                    </button>
                  </div>
                )}
              </>
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
            {filterGroups}
            <div className="mt-8 flex gap-3">
              <button onClick={clearAll} className="btn-outline flex-1">Clear</button>
              <button onClick={() => setMobileFilter(false)} className="btn-primary flex-1">Show {total}</button>
            </div>
          </div>
        </div>
      )}

      {quickView && <QuickViewModal product={quickView} onClose={() => setQuickView(null)} />}
    </div>
  )
}

function priceValue(r: (typeof PRICE_RANGES)[number]): string {
  return `${r.min ?? ''}-${r.max ?? ''}`
}

function FilterGroups({
  colorOptions,
  filterDims,
  attrSelections,
  color,
  size,
  price,
  onColor,
  onSize,
  onPrice,
  onAttr
}: {
  colorOptions: string[]
  filterDims: StoreFilterDim[]
  attrSelections: Record<string, string[]>
  color: string
  size: string
  price: string
  onColor: (v: string) => void
  onSize: (v: string) => void
  onPrice: (v: string) => void
  onAttr: (key: string, value: string) => void
}) {
  return (
    <div className="space-y-6">
      {colorOptions.length > 0 && (
        <FilterAccordion label="Color">
          <div className="flex flex-wrap gap-2 pt-1">
            {colorOptions.map((c) => (
              <button key={c} onClick={() => onColor(c)} className={cn('cursor-pointer rounded-full border px-3 py-1.5 text-xs transition-colors', color === c ? 'border-gold bg-gold/10 text-gold-deep' : 'border-line text-ink-soft hover:border-ink')}>{c}</button>
            ))}
          </div>
        </FilterAccordion>
      )}
      <FilterAccordion label="Size">
        <ul className="space-y-1.5 pt-1">
          {SIZE_OPTIONS.map((v) => (
            <li key={v}>
              <CheckRow checked={size === v} onClick={() => onSize(v)}>{v}</CheckRow>
            </li>
          ))}
        </ul>
      </FilterAccordion>
      <FilterAccordion label="Price">
        <ul className="space-y-1.5 pt-1">
          {PRICE_RANGES.map((r) => (
            <li key={r.label}>
              <CheckRow checked={price === priceValue(r)} onClick={() => onPrice(priceValue(r))}>{r.label}</CheckRow>
            </li>
          ))}
        </ul>
      </FilterAccordion>
      {/* 动态属性维度（attribute_def 字典驱动；option.value 写 URL，label 展示） */}
      {filterDims.map((dim) => (
        <FilterAccordion key={dim.key} label={dim.label}>
          <ul className="space-y-1.5 pt-1">
            {dim.options.map((o) => (
              <li key={o.value}>
                <CheckRow
                  checked={(attrSelections[dim.key] ?? []).includes(o.value)}
                  onClick={() => onAttr(dim.key, o.value)}
                >{o.label}</CheckRow>
              </li>
            ))}
          </ul>
        </FilterAccordion>
      ))}
    </div>
  )
}

function CheckRow({ checked, onClick, children }: { checked: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button onClick={onClick} className="flex w-full cursor-pointer items-center gap-2.5 text-sm text-ink-soft hover:text-ink">
      <span className={cn('flex h-4 w-4 items-center justify-center rounded-sm border transition-colors', checked ? 'border-gold bg-gold text-white' : 'border-line')}>
        {checked && <Check className="h-3 w-3" />}
      </span>
      {children}
    </button>
  )
}

function FilterAccordion({ label, children }: { label: string; children: ReactNode }) {
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
