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
import { Select } from '@/components/ui/select'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn } from '@/lib/utils'

const SIZE_OPTIONS = ['US 0', 'US 2', 'US 4', 'US 6', 'US 8', 'US 10', 'US 12', 'US 14']

const PRICE_RANGES = [
  { key: 'priceUnder200', min: undefined, max: 200 },
  { key: 'price200to500', min: 200, max: 500 },
  { key: 'price500to1000', min: 500, max: 1000 },
  { key: 'priceOver1000', min: 1000, max: undefined }
] as const

/** 站点装修/外链遗留的旧 sort 别名（?sort=new/best）→ 目录 API 枚举（与 collection-page 同口径），
 *  防止别名 URL 让排序下拉 value 匹配不到 option 而显示空触发器 */
const SORT_ALIASES: Record<string, string> = { new: 'newest', best: 'recommended' }

export function CollectionView({
  title,
  description,
  data,
  heroImage,
  colorOptions = [],
  colorCollectionMap = {},
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
  /** 色板名 → 集合 id:色系筛选走集合维度(后端 color 参数为 SKU 单色精确匹配,色组名必然落空) */
  colorCollectionMap?: Record<string, number>
  /** 动态属性筛选维度（E-CAT-27；空则不渲染属性组） */
  filterDims?: StoreFilterDim[]
  /** 子分类 tab（value=category id 字符串，cat searchParam 驱动） */
  subTabs?: { label: string; value: string }[]
  basePath: string
}) {
  const router = useRouter()
  const params = useSearchParams()
  const { t } = useI18n()
  const [mobileFilter, setMobileFilter] = useState(false)
  const [quickView, setQuickView] = useState<StoreProductCard | null>(null)

  const sorts = [
    { label: t.collection.sortFeatured, value: 'recommended' },
    { label: t.collection.sortNewest, value: 'newest' },
    { label: t.collection.sortPriceAsc, value: 'price_asc' },
    { label: t.collection.sortPriceDesc, value: 'price_desc' }
  ]

  const color = params.get('color') ?? ''
  const collectionActive = params.get('collection') ?? ''
  const size = params.get('size') ?? ''
  const price = params.get('price') ?? ''
  // sort 归一：旧别名（new/best）与非法值都映射到合法 option，保证下拉触发器始终有标签
  const rawSort = params.get('sort') ?? 'recommended'
  const sort = SORT_ALIASES[rawSort] ?? (sorts.some((s) => s.value === rawSort) ? rawSort : 'recommended')
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
  const activeCount = [color, collectionActive, size, price].filter(Boolean).length + attrActiveCount
  const clearAll = () => {
    const patch: Record<string, string | null> = { color: null, collection: null, size: null, price: null }
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
      color={color || (collectionActive && Object.entries(colorCollectionMap).find(([, id]) => String(id) === collectionActive)?.[0]) || ''}
      size={size}
      price={price}
      onColor={(v) => {
        // 色板筛选:命中色板集合则走 collection 维度,否则按 SKU color
        const cid = colorCollectionMap[v]
        if (cid != null) navigate({ collection: String(cid) === collectionActive ? null : String(cid), color: null })
        else navigate({ color: v === color ? null : v, collection: null })
      }}
      onSize={(v) => navigate({ size: v === size ? null : v })}
      onPrice={(v) => navigate({ price: v === price ? null : v })}
      onAttr={toggleAttr}
    />
  )

  return (
    <div>
      {/* Hero / 标题区：70vh 大片 + 深色渐变蒙层（原 42vh 矮版升级） */}
      <div className="relative flex min-h-[70vh] items-center justify-center overflow-hidden bg-ink text-canvas">
        {heroImage && (
          <>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={heroImage} alt={title} className="absolute inset-0 h-full w-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-b from-ink/35 via-ink/45 to-ink/70" />
          </>
        )}
        <div className="container-luxe relative py-24 text-center">
          <p className="eyebrow mb-3 text-gold-light">{t.collection.eyebrow}</p>
          <h1 className="heading-display text-4xl text-canvas sm:text-5xl lg:text-6xl">{title}</h1>
          {description && <p className="mx-auto mt-4 max-w-xl text-canvas/85">{description}</p>}
        </div>
      </div>

      <div className="container-luxe py-10">
        {/* Sub tabs（子分类，cat searchParam 驱动） */}
        {subTabs && subTabs.length > 0 && (
          <div className="mb-8 flex flex-wrap justify-center gap-2 border-b border-line pb-6">
            {[{ label: t.collection.all, value: '' }, ...subTabs].map((t2) => {
              const catPatch: Record<string, string | null> = { cat: t2.value || null }
              for (const key of Object.keys(attrSelections)) catPatch['a_' + key] = null
              return (
                <button
                  key={t2.value || 'all'}
                  onClick={() => navigate(catPatch)}
                  className={cn('cursor-pointer rounded-full px-5 py-2 text-[13px] font-medium uppercase tracking-luxe transition-colors', cat === t2.value ? 'bg-ink text-canvas' : 'border border-line text-ink-soft hover:border-ink')}
                >
                  {t2.label}
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
                <p className="eyebrow">{t.collection.filter}</p>
                {activeCount > 0 && <button onClick={clearAll} className="cursor-pointer text-xs text-gold-deep underline">{t.collection.clearAll} ({activeCount})</button>}
              </div>
              {filterGroups}
            </div>
          </aside>

          {/* 商品区 */}
          <div className="flex-1">
            <div className="mb-6 flex items-center justify-between">
              <p className="text-sm text-ink-soft">{total} {total === 1 ? t.collection.styleOne : t.collection.stylesMany}</p>
              <div className="flex items-center gap-3">
                <button onClick={() => setMobileFilter(true)} className="flex cursor-pointer items-center gap-2 rounded-sm border border-line px-4 py-2 text-xs uppercase tracking-luxe lg:hidden">
                  <SlidersHorizontal className="h-3.5 w-3.5" /> {t.collection.filter} {activeCount > 0 && `(${activeCount})`}
                </button>
                <Select
                  id="sort"
                  ariaLabel={t.collection.sortBy}
                  value={sort}
                  options={sorts}
                  onChange={(v) => navigate({ sort: v === 'recommended' ? null : v })}
                  align="right"
                  triggerClassName="w-auto px-4 py-2 text-xs uppercase tracking-luxe"
                  optionClassName="text-xs uppercase tracking-luxe"
                />
              </div>
            </div>

            {data === null ? (
              <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
                <p className="font-display text-2xl">{t.collection.loadFailedTitle}</p>
                <p className="text-sm text-ink-soft">{t.collection.loadFailedBody}</p>
                <button onClick={() => router.refresh()} className="btn-outline">{t.common.retry}</button>
              </div>
            ) : items.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
                <p className="font-display text-2xl">{t.collection.noMatchTitle}</p>
                <p className="text-sm text-ink-soft">{t.collection.noMatchBody}</p>
                <button onClick={clearAll} className="btn-outline">{t.collection.clearFilters}</button>
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
                      {t.collection.previous}
                    </button>
                    <span className="text-ink-soft">{t.collection.pageOf.replace('{page}', String(page)).replace('{total}', String(totalPages))}</span>
                    <button
                      disabled={page >= totalPages}
                      onClick={() => navigate({ page: String(page + 1) })}
                      className="btn-outline px-5 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      {t.collection.next}
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
              <p className="font-display text-2xl">{t.collection.filter}</p>
              <button onClick={() => setMobileFilter(false)} className="cursor-pointer p-1"><X className="h-5 w-5" /></button>
            </div>
            {filterGroups}
            <div className="mt-8 flex gap-3">
              <button onClick={clearAll} className="btn-outline flex-1">{t.common.clear}</button>
              <button onClick={() => setMobileFilter(false)} className="btn-primary flex-1">{t.collection.showResults.replace('{count}', String(total))}</button>
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
  const { t } = useI18n()
  return (
    <div className="space-y-6">
      {colorOptions.length > 0 && (
        <FilterAccordion label={t.product.color}>
          <div className="flex flex-wrap gap-2 pt-1">
            {colorOptions.map((c) => (
              <button key={c} onClick={() => onColor(c)} className={cn('cursor-pointer rounded-full border px-3 py-1.5 text-xs transition-colors', color === c ? 'border-gold bg-gold/10 text-gold-deep' : 'border-line text-ink-soft hover:border-ink')}>{c}</button>
            ))}
          </div>
        </FilterAccordion>
      )}
      <FilterAccordion label={t.product.size}>
        <ul className="space-y-1.5 pt-1">
          {SIZE_OPTIONS.map((v) => (
            <li key={v}>
              <CheckRow checked={size === v} onClick={() => onSize(v)}>{v}</CheckRow>
            </li>
          ))}
        </ul>
      </FilterAccordion>
      <FilterAccordion label={t.collection.price}>
        <ul className="space-y-1.5 pt-1">
          {PRICE_RANGES.map((r) => (
            <li key={r.key}>
              <CheckRow checked={price === priceValue(r)} onClick={() => onPrice(priceValue(r))}>{t.collection[r.key]}</CheckRow>
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
