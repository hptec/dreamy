'use client'

/**
 * ProductBuyBox（COMP-CAT-S03，layout-keep + data-swap）：
 * - 数据源切 StoreProductDetail（E-CAT-04）：SKU 现货矩阵（stock=0 置灰）/ customSizeAvailable 定制表单开关（A-007）
 *   / leadTimeDays+rushAvailable 交期展示（决策 20.6）/ multi_currency_prices 覆盖价（决策 14）。
 * - Find My Size 入口（COMP-CAT-S04）；Add to Showroom 切面入口（FORM-SHR-S11）。
 * - 加购：现货必选 SKU、定制必填四围（422604 前端预判，FORM-TRD-S01）；409601 行内库存提示。
 * - 收藏/Showroom 未登录 → /account/login?returnTo=（决策 18）。
 * - 浏览上报：mount 时 trackView（决策 23 fire-and-forget）。
 */

import { useState, useEffect, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { Heart, Truck, Sparkles, Ruler, ChevronDown, Plus, Minus, PartyPopper, Check } from 'lucide-react'
import type { CustomSizeData, StoreProductDetail, FabricComposition } from '@/lib/api/store-types'
import { useStore } from '@/components/store-provider'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ApiError } from '@/lib/api/client'
import { trackViewItem } from '@/lib/analytics/gtag'
import { formatPrice, installments, cn } from '@/lib/utils'
import { Stars, Badge } from '@/components/ui/primitives'
import { SizeGuideModal } from './size-guide-modal'
import { FindMySizeModal } from './find-my-size-modal'
import { AddToShowroomModal } from '@/components/showroom/add-to-showroom-modal'
import { badgeOf } from './product-card'
import { colorOptionsOf, sizesFor, skuFor, swatchImageOf, primaryImageOf } from './product-utils'
import { CareSymbolIcon } from './care-symbol-icon'

/** 根据卖点文本智能匹配图标 */
function getIconForSellingPoint(point: string) {
  const lower = point.toLowerCase()
  if (lower.includes('shipping') || lower.includes('delivery') || lower.includes('envío') || lower.includes('livraison'))
    return <Truck className="h-4 w-4 text-gold" />
  if (lower.includes('custom') || lower.includes('sizing') || lower.includes('talla') || lower.includes('taille') || lower.includes('medida'))
    return <Ruler className="h-4 w-4 text-gold" />
  if (lower.includes('swatch') || lower.includes('sample') || lower.includes('fabric') || lower.includes('muestra') || lower.includes('échantillon'))
    return <Sparkles className="h-4 w-4 text-gold" />
  if (lower.includes('production') || lower.includes('day') || lower.includes('producción') || lower.includes('días'))
    return <Truck className="h-4 w-4 text-gold" />
  // 默认勾选图标
  return <Check className="h-4 w-4 text-gold" />
}

// layer 数字 → 展示名（面料分组标签）
const FABRIC_LAYER_NAMES: Record<number, string> = {
  1: 'Shell',
  2: 'Lining',
  3: 'Overlay',
  4: 'Trim'
}

export function ProductBuyBox({ product }: { product: StoreProductDetail }) {
  const router = useRouter()
  const { currency, addToCart, toggleWishlist, isWished, trackView } = useStore()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const { te, t } = useI18n()

  const colors = useMemo(() => colorOptionsOf(product), [product])
  const [colorName, setColorName] = useState(colors[0]?.name ?? '')
  const [size, setSize] = useState<string | null>(null)
  const [qty, setQty] = useState(1)
  const [sizeGuide, setSizeGuide] = useState(false)
  const [fitFinder, setFitFinder] = useState(false)
  const [showroomOpen, setShowroomOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [custom, setCustom] = useState({ bust: '', waist: '', hips: '', hollowToFloor: '', height: '' })
  const [customError, setCustomError] = useState(false)
  const wished = isWished(product.id)
  const badge = badgeOf(product)

  // 面料成分按 layer 分组（Shell/Lining/Overlay/Trim），用于 Fabric & Care 折叠项
  const compositionsByLayer = useMemo(() => {
    const groups = new Map<number, FabricComposition[]>()
    for (const c of product.fabricCompositions ?? []) {
      if (!groups.has(c.layer)) groups.set(c.layer, [])
      groups.get(c.layer)!.push(c)
    }
    return groups
  }, [product.fabricCompositions])
  const hasFabric = (product.fabricCompositions?.length ?? 0) > 0
  const hasCare = (product.careInstructions?.length ?? 0) > 0
  const hasFabricCare = hasFabric || hasCare || !!product.fabricCareNote

  useEffect(() => { trackView(product.id) }, [product.id, trackView])

  // GA4 view_item（决策 19）：PDP 进入上报（consent denied 时 no-op）
  useEffect(() => {
    trackViewItem({
      currency: 'USD',
      value: product.price,
      items: [{ item_id: String(product.id), item_name: product.name, price: product.price, quantity: 1 }]
    })
  }, [product.id, product.name, product.price])

  const sizes = useMemo(() => {
    const base = sizesFor(product, colorName)
    if (product.customSizeAvailable) base.push({ size: 'Custom', inStock: true })
    return base
  }, [product, colorName])

  const customSelected = size === 'Custom'

  const buildCustomData = (): CustomSizeData | null => {
    const { bust, waist, hips, hollowToFloor, height } = custom
    if (!bust || !waist || !hips || !hollowToFloor) return null
    return {
      bust: parseFloat(bust),
      waist: parseFloat(waist),
      hips: parseFloat(hips),
      hollowToFloor: parseFloat(hollowToFloor),
      ...(height ? { height: parseFloat(height) } : {})
    }
  }

  const add = async () => {
    setError(null)
    if (!size) { setError('Please select a size to continue.'); return }
    let skuId: number | undefined
    let customSizeData: CustomSizeData | undefined
    let stock: number | undefined
    if (customSelected) {
      // 定制款：必填四围表单完成才可加购（422604 前端预判）
      const data = buildCustomData()
      if (!data) { setCustomError(true); return }
      setCustomError(false)
      customSizeData = data
    } else {
      const sku = skuFor(product, colorName, size)
      if (!sku?.id) { setError(te(422604)); return }
      skuId = sku.id
      stock = sku.stock
    }
    setAdding(true)
    try {
      await addToCart({
        productId: product.id,
        skuId,
        qty,
        customSizeData,
        snapshot: {
          slug: product.slug,
          name: product.name,
          image: swatchImageOf(product, colorName) ?? primaryImageOf(product),
          priceUsd: product.price,
          multiCurrencyPrices: product.multiCurrencyPrices,
          color: customSelected ? colorName : colorName,
          size,
          stock
        }
      })
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setAdding(false)
    }
  }

  const onWish = async () => {
    const ok = await toggleWishlist(product.id)
    if (!ok) router.push(`/account/login?returnTo=/product/${product.slug}`)
  }

  const openShowroom = () => {
    if (!isAuthenticated) {
      router.push(`/account/login?returnTo=/product/${product.slug}`)
      return
    }
    setShowroomOpen(true)
  }

  return (
    <div className="lg:sticky lg:top-28">
      {badge && <div className="mb-3"><Badge variant={badge.variant}>{badge.label}</Badge></div>}
      <p className="eyebrow">{product.categoryName ?? ''}</p>
      <h1 className="mt-1 font-display text-3xl font-medium leading-tight lg:text-4xl">{product.name}</h1>
      {(product.ratingCount ?? 0) > 0 && (
        <div className="mt-3 flex items-center gap-2">
          <Stars rating={product.ratingAvg ?? 0} />
          <a href="#reviews" className="text-sm text-ink-soft underline">{product.ratingCount} reviews</a>
        </div>
      )}

      <div className="mt-4 flex items-center gap-3">
        <span className="font-display text-2xl font-medium">{formatPrice(product.price, currency, product.multiCurrencyPrices)}</span>
        {product.compareAt && product.compareAt > product.price && (
          <span className="text-lg text-ink-faint line-through">{formatPrice(product.compareAt, currency)}</span>
        )}
      </div>
      {/* Klarna installment line hidden */}

      {/* Color */}
      {colors.length > 0 && (
        <div className="mt-6">
          <p className="eyebrow mb-2.5">Color — <span className="text-ink-soft">{colorName}</span></p>
          <div className="flex flex-wrap gap-2.5">
            {colors.map((c) => (
              c.image ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  key={c.name}
                  src={c.image}
                  alt={c.name}
                  title={c.name}
                  onClick={() => setColorName(c.name)}
                  className={cn('h-9 w-9 cursor-pointer rounded-full border-2 object-cover transition-all', colorName === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')}
                />
              ) : (
                <button key={c.name} onClick={() => setColorName(c.name)} className={cn('h-9 w-9 rounded-full border-2 bg-muted transition-all', colorName === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')} title={c.name} aria-label={c.name} />
              )
            ))}
          </div>
        </div>
      )}

      {/* Size */}
      <div className="mt-6">
        <div className="mb-2.5 flex items-center justify-between">
          <p className="eyebrow">Size</p>
          <div className="flex items-center gap-3">
            <button onClick={() => setFitFinder(true)} className="flex cursor-pointer items-center gap-1 text-xs text-gold-deep underline"><Sparkles className="h-3.5 w-3.5" /> Find My Size</button>
            <button onClick={() => setSizeGuide(true)} className="flex cursor-pointer items-center gap-1 text-xs text-gold-deep underline"><Ruler className="h-3.5 w-3.5" /> Size Guide</button>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {sizes.map((s) => (
            <button
              key={s.size}
              disabled={!s.inStock}
              onClick={() => { setSize(s.size); setError(null) }}
              className={cn('min-w-[3.25rem] cursor-pointer rounded-sm border px-3 py-2 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through',
                size === s.size ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink',
                s.size === 'Custom' && size !== 'Custom' && 'border-gold/50 text-gold-deep')}
            >
              {s.size}
            </button>
          ))}
        </div>
        {error && <p className="mt-2 text-xs text-blush">{error}</p>}
        {customSelected && (
          <div className="mt-3 rounded-sm bg-sage/10 p-4">
            <p className="text-xs text-sage-deep">Made-to-measure at no extra cost. Enter your measurements below. Allow {product.leadTimeDays} days production.</p>
            <div className="mt-3 grid grid-cols-2 gap-3">
              {([
                ['bust', 'Bust (in)'],
                ['waist', 'Waist (in)'],
                ['hips', 'Hips (in)'],
                ['hollowToFloor', 'Hollow to Floor (in)'],
                ['height', 'Height (in, optional)']
              ] as const).map(([key, label]) => (
                <div key={key}>
                  <label htmlFor={`custom-${key}`} className="eyebrow mb-1 block text-[10px]">{label}</label>
                  <input
                    id={`custom-${key}`}
                    type="number"
                    inputMode="decimal"
                    min="0"
                    value={custom[key]}
                    onChange={(e) => setCustom((p) => ({ ...p, [key]: e.target.value }))}
                    className={cn('w-full rounded-sm border bg-canvas px-3 py-2 text-sm outline-none focus:border-gold', customError && key !== 'height' && !custom[key] ? 'border-blush' : 'border-line')}
                  />
                </div>
              ))}
            </div>
            {customError && <p className="mt-2 text-xs text-blush">{te(422604)}</p>}
          </div>
        )}
      </div>

      {/* 交期（决策 20.6） */}
      <p className="mt-4 flex items-center gap-2 text-xs text-ink-soft">
        <Truck className="h-4 w-4 text-gold" />
        Ships in {product.leadTimeDays} {product.leadTimeDays === 1 ? 'day' : 'days'}
        {product.rushAvailable && <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] uppercase tracking-luxe text-gold-deep">Rush available</span>}
      </p>

      {/* Qty + CTA */}
      <div className="mt-6 flex items-center gap-3">
        <div className="flex items-center border border-line">
          <button onClick={() => setQty(Math.max(1, qty - 1))} className="cursor-pointer p-3" aria-label="Decrease quantity"><Minus className="h-3.5 w-3.5" /></button>
          <span className="w-10 text-center text-sm">{qty}</span>
          <button onClick={() => setQty(qty + 1)} className="cursor-pointer p-3" aria-label="Increase quantity"><Plus className="h-3.5 w-3.5" /></button>
        </div>
        <button onClick={add} disabled={adding} className="btn-primary flex-1 disabled:opacity-60">{adding ? 'Adding…' : 'Add to Bag'}</button>
        <button onClick={onWish} className="cursor-pointer rounded-sm border border-line p-3.5 transition-colors hover:border-ink" aria-label="Add to wishlist">
          <Heart className={cn('h-5 w-5', wished ? 'fill-blush text-blush' : 'text-ink')} />
        </button>
        <button onClick={openShowroom} className="cursor-pointer rounded-sm border border-line p-3.5 transition-colors hover:border-gold hover:text-gold-deep" aria-label="Add to Showroom" title="Add to Showroom">
          <PartyPopper className="h-5 w-5" />
        </button>
      </div>

      <div className="mt-3 flex gap-3">
        <button className="flex-1 cursor-pointer rounded-sm border border-line py-3 text-[12px] font-medium uppercase tracking-luxe transition-colors hover:border-gold hover:text-gold-deep">Order a Swatch</button>
        <button className="flex-1 cursor-pointer rounded-sm border border-line py-3 text-[12px] font-medium uppercase tracking-luxe text-ink-faint" title="Coming soon" disabled>Try in AR · Soon</button>
      </div>

      {/* 卖点展示（动态渲染 selling_points，回退默认卖点） */}
      <div className="mt-6 space-y-2.5 border-t border-line pt-5 text-sm text-ink-soft">
        {product.sellingPoints && product.sellingPoints.length > 0 ? (
          product.sellingPoints.map((point, i) => (
            <p key={i} className="flex items-center gap-2">
              {getIconForSellingPoint(point)}
              {point}
            </p>
          ))
        ) : (
          <>
            <p className="flex items-center gap-2"><Truck className="h-4 w-4 text-gold" /> Free worldwide shipping over $200</p>
            <p className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-gold" /> Order fabric swatches before you commit</p>
            {product.customSizeAvailable && <p className="flex items-center gap-2"><Ruler className="h-4 w-4 text-gold" /> Custom sizing available</p>}
          </>
        )}
      </div>

      {/* 描述折叠 */}
      <div className="mt-6 divide-y divide-line border-y border-line">
        {product.description && (
          <Accordion title="Description" defaultOpen>
            <p>{product.description}</p>
            {product.designerNote && <p className="mt-3 italic">{product.designerNote}</p>}
          </Accordion>
        )}
        <Accordion title="Details">
          <ul className="list-inside list-disc space-y-1">
            {/* 动态属性（attribute_def 字典驱动；面料成分/护理走下方 Fabric & Care 折叠项） */}
            {(product.attributes ?? [])
              .filter((a) => a.values.length > 0)
              .map((a) => (
                <li key={a.key}>
                  {a.label}: {a.values.map((v) => v.label).join(', ')}
                </li>
              ))}
            {product.styleNo && <li>Style no.: {product.styleNo}</li>}
          </ul>
        </Accordion>
        {hasFabricCare && (
          <Accordion title="Fabric & Care">
            {hasFabric && (
              <div className="space-y-3">
                {Array.from(compositionsByLayer.entries()).map(([layer, items]) => (
                  <div key={layer}>
                    <p className="mb-1 text-[11px] uppercase tracking-luxe text-gold">
                      {FABRIC_LAYER_NAMES[layer] ?? `Layer ${layer}`}
                    </p>
                    <p>{items.map((c) => `${c.material} ${c.percentage}%`).join(', ')}</p>
                  </div>
                ))}
              </div>
            )}
            {hasCare && (
              <div className={cn('flex flex-wrap gap-x-8 gap-y-3', hasFabric && 'mt-4')}>
                {product.careInstructions!.map((item, idx) => (
                  <div key={idx} className="flex flex-col items-center gap-1.5">
                    <CareSymbolIcon symbol={item.symbol} className="h-7 w-7 text-ink" />
                    <span className="text-[10px] uppercase tracking-luxe text-ink-faint">{item.label}</span>
                  </div>
                ))}
              </div>
            )}
            {product.fabricCareNote && (
              <p className={cn('italic text-ink-faint', (hasFabric || hasCare) && 'mt-3')}>
                {product.fabricCareNote}
              </p>
            )}
          </Accordion>
        )}
        <Accordion title="Shipping & Delivery">
          <p>
            Standard production {product.leadTimeDays} days.
            {product.customSizeAvailable && ' Custom & made-to-measure available.'}
            {' '}Free worldwide shipping on orders over $200 via FedEx, UPS, or DHL Express.
          </p>
        </Accordion>
      </div>

      <SizeGuideModal open={sizeGuide} onClose={() => setSizeGuide(false)} sizeChart={product.sizeChart ?? []} />
      <FindMySizeModal
        open={fitFinder}
        onClose={() => setFitFinder(false)}
        productId={product.id}
        sizes={sizes}
        customAvailable={!!product.customSizeAvailable}
        onSelect={(s) => { setSize(s); setError(null) }}
      />
      {showroomOpen && (
        <AddToShowroomModal
          product={product}
          defaultColor={colorName}
          open={showroomOpen}
          onClose={() => setShowroomOpen(false)}
        />
      )}
    </div>
  )
}

function Accordion({ title, children, defaultOpen }: { title: string; children: React.ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(!!defaultOpen)
  return (
    <div className="py-4">
      <button onClick={() => setOpen(!open)} className="flex w-full cursor-pointer items-center justify-between text-sm font-medium uppercase tracking-luxe">
        {title}
        <ChevronDown className={cn('h-4 w-4 transition-transform', open && 'rotate-180')} />
      </button>
      {open && <div className="mt-3 text-sm leading-relaxed text-ink-soft">{children}</div>}
    </div>
  )
}
