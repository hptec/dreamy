'use client'

/**
 * 购物车页（COMP-TRD-S01，layout-keep + data-swap）：
 * - mock cart → cartStore（双态，决策 8）；行结构不变（图/名/色/码/qty stepper/小计/删）。
 * - qty 超 sku.stock → 行内提示（409601 字典）+ stepper 上限 stock；下架行置灰「No longer available」。
 * - dye lot 提示条（dyeLotProductIds 命中行上方，sage 信息条，决策 20.4）。
 * - merge 截断提示（mergedTruncatedItemIds →「已按库存调整数量」）。
 * - 推荐区块 → E-CAT-03 best_sellers（客户端拉取，空不渲染）。
 */

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { X, Plus, Minus, Heart, ShoppingBag, Truck, ShieldCheck, Clock } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { ApiError, request } from '@/lib/api/client'
import type { StoreProductCard } from '@/lib/api/store-types'
import { ProductCard } from '@/components/product/product-card'
import { useI18n } from '@/lib/i18n/i18n-context'
import { formatPrice, cn } from '@/lib/utils'

export default function CartPage() {
  const { cart, updateQty, removeLine, toggleWishlist, cartSubtotal, currency, dyeLotProductIds, truncatedItemIds } = useStore()
  const { te } = useI18n()
  const [lineErrors, setLineErrors] = useState<Record<string, string>>({})
  const [recommended, setRecommended] = useState<StoreProductCard[]>([])

  useEffect(() => {
    request<{ items: StoreProductCard[] }>('/api/store/products/recommendations', {
      query: { block: 'best_sellers', limit: 4 }
    })
      .then((res) => setRecommended(res.items))
      .catch(() => setRecommended([]))
  }, [])

  const changeQty = async (key: string, qty: number, stock?: number) => {
    if (stock !== undefined && qty > stock) {
      setLineErrors((p) => ({ ...p, [key]: te(409601) }))
      return
    }
    setLineErrors((p) => ({ ...p, [key]: '' }))
    try {
      await updateQty(key, qty)
    } catch (err) {
      setLineErrors((p) => ({ ...p, [key]: err instanceof ApiError ? te(err.code) : te(50000) }))
    }
  }

  if (cart.length === 0) {
    return (
      <div className="container-luxe py-20">
        <div className="flex flex-col items-center gap-5 py-16 text-center">
          <ShoppingBag className="h-14 w-14 text-line" strokeWidth={1} />
          <h1 className="font-display text-4xl font-medium">Your bag is empty</h1>
          <p className="max-w-sm text-ink-soft">Looks like you haven&apos;t added anything yet. Let&apos;s find the one.</p>
          <Link href="/wedding-dresses" className="btn-primary">Shop Wedding Dresses</Link>
        </div>
        {recommended.length > 0 && (
          <section className="mt-16">
            <h2 className="mb-8 text-center font-display text-3xl font-medium">Best Sellers</h2>
            <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
              {recommended.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          </section>
        )}
      </div>
    )
  }

  return (
    <div className="container-luxe py-12">
      <h1 className="font-display text-4xl font-medium">Your Bag</h1>
      {truncatedItemIds.length > 0 && (
        <p className="mt-4 rounded-sm bg-sage/10 px-4 py-3 text-sm text-sage-deep">Some quantities were adjusted to match available stock when we merged your bag.</p>
      )}
      <div className="mt-8 grid gap-12 lg:grid-cols-3">
        {/* Lines */}
        <div className="lg:col-span-2">
          {cart.map((line) => (
            <div key={line.key}>
              {dyeLotProductIds.includes(line.productId) && (
                <p className="mt-4 flex items-start gap-2 rounded-sm bg-sage/10 px-4 py-2.5 text-xs text-sage-deep">
                  <Clock className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                  Order within 24h of your bridal party to guarantee the same dye lot for this style.
                </p>
              )}
              <div className={cn('flex gap-5 border-b border-line py-6', line.unavailable && 'opacity-50')}>
                {line.image ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={line.image} alt={line.name} className="h-40 w-28 rounded-sm object-cover" />
                ) : (
                  <div className="h-40 w-28 rounded-sm bg-muted" />
                )}
                <div className="flex flex-1 flex-col">
                  <div className="flex justify-between">
                    <Link href={`/product/${line.slug}`} className="font-display text-xl font-medium hover:text-gold-deep">{line.name}</Link>
                    <span className="font-medium">{formatPrice(line.priceUsd * line.qty, currency, line.multiCurrencyPrices)}</span>
                  </div>
                  <p className="mt-1 text-sm text-ink-soft">
                    {[line.color, line.customSizeData ? 'Custom size' : line.size ? `Size ${line.size}` : undefined].filter(Boolean).join(' · ')}
                  </p>
                  {line.customSizeData && (
                    <p className="mt-0.5 text-xs text-ink-faint">
                      Bust {line.customSizeData.bust}″ · Waist {line.customSizeData.waist}″ · Hips {line.customSizeData.hips}″ · Hollow-to-floor {line.customSizeData.hollowToFloor}″
                    </p>
                  )}
                  {line.unavailable && <p className="mt-1 text-xs text-blush">No longer available</p>}
                  {lineErrors[line.key] && <p className="mt-1 text-xs text-blush">{lineErrors[line.key]}</p>}
                  <div className="mt-auto flex items-center gap-4">
                    <div className="flex items-center border border-line">
                      <button onClick={() => void changeQty(line.key, line.qty - 1, line.stock)} className="cursor-pointer p-2" aria-label="Decrease"><Minus className="h-3.5 w-3.5" /></button>
                      <span className="w-9 text-center text-sm">{line.qty}</span>
                      <button onClick={() => void changeQty(line.key, line.qty + 1, line.stock)} className="cursor-pointer p-2" aria-label="Increase"><Plus className="h-3.5 w-3.5" /></button>
                    </div>
                    <button
                      onClick={async () => {
                        const ok = await toggleWishlist(line.productId)
                        if (ok) void removeLine(line.key)
                      }}
                      className="flex cursor-pointer items-center gap-1 text-xs text-ink-soft hover:text-gold-deep"
                    >
                      <Heart className="h-3.5 w-3.5" /> Save for later
                    </button>
                    <button onClick={() => void removeLine(line.key)} className="flex cursor-pointer items-center gap-1 text-xs text-ink-soft hover:text-blush"><X className="h-3.5 w-3.5" /> Remove</button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div>
          <div className="rounded-sm border border-line bg-surface p-6 lg:sticky lg:top-28">
            <h2 className="font-display text-2xl font-medium">Order Summary</h2>
            <dl className="mt-5 space-y-2.5 border-t border-line pt-5 text-sm">
              <div className="flex justify-between"><dt className="text-ink-soft">Subtotal</dt><dd>{formatPrice(cartSubtotal, currency)}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-soft">Shipping</dt><dd className="text-ink-soft">Calculated at checkout</dd></div>
              <div className="flex justify-between border-t border-line pt-3 text-base font-medium"><dt>Estimated Total</dt><dd className="font-display text-xl">{formatPrice(cartSubtotal, currency)}</dd></div>
            </dl>
            <p className="mt-2 text-xs text-ink-faint">or 4 interest-free payments of {formatPrice(cartSubtotal / 4, currency)} with Klarna</p>
            <p className="mt-2 text-xs text-ink-faint">Promo codes can be applied at checkout.</p>
            <Link href="/checkout" className="btn-primary mt-5 w-full">Proceed to Checkout</Link>
            <div className="mt-5 space-y-2 text-xs text-ink-soft">
              <p className="flex items-center gap-2"><Truck className="h-4 w-4 text-gold" /> Free shipping over $200</p>
              <p className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 text-gold" /> Secure checkout</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
