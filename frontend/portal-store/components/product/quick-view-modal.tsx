'use client'

/**
 * QuickViewModal（data-swap）：卡片数据仅含摘要 → 打开时实时拉 PDP 详情（E-CAT-04）取 SKU 矩阵。
 * 布局结构保持；加购走 cartStore（双态，FORM-TRD-S01 现货未选 SKU 前端阻断）。
 */

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { X } from 'lucide-react'
import type { StoreProductCard, StoreProductDetail } from '@/lib/api/store-types'
import { getStoreProductClient } from '@/lib/api/catalog-api'
import { useStore } from '@/components/store-provider'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ApiError } from '@/lib/api/client'
import { formatPrice, cn } from '@/lib/utils'
import { Stars } from '@/components/ui/primitives'
import { colorOptionsOf, skuFor, primaryImageOf } from './product-utils'

export function QuickViewModal({ product, onClose }: { product: StoreProductCard; onClose: () => void }) {
  const { currency, addToCart } = useStore()
  const { te } = useI18n()
  const [detail, setDetail] = useState<StoreProductDetail | null>(null)
  const [failed, setFailed] = useState(false)
  const [color, setColor] = useState<string>('')
  const [size, setSize] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    let active = true
    getStoreProductClient(product.slug)
      .then((d) => {
        if (!active) return
        setDetail(d)
        const colors = colorOptionsOf(d)
        setColor(colors[0]?.name ?? '')
      })
      .catch(() => active && setFailed(true))
    return () => {
      active = false
    }
  }, [product.slug])

  const colors = useMemo(() => (detail ? colorOptionsOf(detail) : []), [detail])
  const activeColor = colors.find((c) => c.name === color)
  const sizes = useMemo(() => {
    if (!detail) return []
    return detail.skus.filter((s) => !color || s.color === color).map((s) => ({ size: s.size, inStock: (s.stock ?? 0) > 0 }))
  }, [detail, color])
  const image = activeColor?.image ?? product.imageUrl ?? (detail ? primaryImageOf(detail) : undefined)

  const add = async () => {
    if (!detail || !size) return
    const sku = skuFor(detail, color, size)
    if (!sku?.id) { setError(te(422604)); return }
    setAdding(true)
    setError(null)
    try {
      await addToCart({
        productId: detail.id,
        skuId: sku.id,
        qty: 1,
        snapshot: {
          slug: detail.slug,
          name: detail.name,
          image,
          priceUsd: detail.price,
          multiCurrencyPrices: detail.multiCurrencyPrices,
          color,
          size,
          stock: sku.stock
        }
      })
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setAdding(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative grid w-full max-w-3xl animate-fadeup overflow-hidden rounded-sm bg-canvas shadow-lift sm:grid-cols-2">
        <button onClick={onClose} className="absolute right-3 top-3 z-10 cursor-pointer rounded-full bg-canvas/80 p-1.5" aria-label="Close"><X className="h-4 w-4" /></button>
        {image ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={image} alt={product.name} className="aspect-[3/4] h-full w-full object-cover" />
        ) : (
          <div className="aspect-[3/4] h-full w-full bg-muted" />
        )}
        <div className="flex flex-col p-7">
          <h2 className="font-display text-2xl font-medium">{product.name}</h2>
          {(product.ratingCount ?? 0) > 0 && (
            <div className="mt-1.5 flex items-center gap-2">
              <Stars rating={product.ratingAvg ?? 0} /><span className="text-xs text-ink-faint">({product.ratingCount})</span>
            </div>
          )}
          <p className="mt-2 text-lg font-medium">{formatPrice(product.price, currency, product.multiCurrencyPrices)}</p>

          {failed && <p className="mt-4 text-sm text-blush">{te(50000)}</p>}
          {!detail && !failed && <p className="mt-4 text-sm text-ink-soft">Loading…</p>}

          {detail && colors.length > 0 && (
            <div className="mt-4">
              <p className="eyebrow mb-2">Color: {color}</p>
              <div className="flex gap-2">
                {colors.map((c) => (
                  c.image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img key={c.name} src={c.image} alt={c.name} title={c.name} onClick={() => setColor(c.name)} className={cn('h-7 w-7 cursor-pointer rounded-full border-2 object-cover transition-all', color === c.name ? 'border-gold ring-1 ring-gold/30' : 'border-line')} />
                  ) : (
                    <button key={c.name} onClick={() => setColor(c.name)} className={cn('h-7 w-7 rounded-full border-2 bg-muted transition-all', color === c.name ? 'border-gold ring-1 ring-gold/30' : 'border-line')} title={c.name} aria-label={c.name} />
                  )
                ))}
              </div>
            </div>
          )}

          {detail && sizes.length > 0 && (
            <div className="mt-4">
              <p className="eyebrow mb-2">Size</p>
              <div className="flex flex-wrap gap-2">
                {sizes.map((s) => (
                  <button key={s.size} disabled={!s.inStock} onClick={() => setSize(s.size)} className={cn('min-w-[3rem] cursor-pointer rounded-sm border px-3 py-1.5 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through', size === s.size ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink')}>{s.size}</button>
                ))}
              </div>
            </div>
          )}

          {error && <p className="mt-3 text-xs text-blush">{error}</p>}

          <div className="mt-auto pt-6">
            <button onClick={add} disabled={!size || adding} className="btn-primary w-full disabled:opacity-50">{size ? (adding ? 'Adding…' : 'Add to Bag') : 'Select a Size'}</button>
            <Link href={`/product/${product.slug}`} onClick={onClose} className="mt-2 block text-center text-sm text-ink-soft underline">View full details</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
