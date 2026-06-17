'use client'

/**
 * Wishlist 页（COMP-TRD-S07，layout-keep + data-swap）：
 * - mock → wishlistStore（E-TRD wishlists）；网格 ProductCard 复用。
 * - 卡片悬浮「Move to bag」：现货弹 SKU 选择后 moveToCart；定制款跳 PDP 定制表单（422604 预判）。
 * - Recently Viewed 区块 → listBrowseHistory（决策 23，登录态才展示）。
 */

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { Heart, ShoppingBag, X } from 'lucide-react'
import type { BrowseHistoryItem, StoreProductDetail, WishlistItem } from '@/lib/api/store-types'
import { useWishlistStore } from '@/lib/stores/wishlist-store'
import { useCartStore } from '@/lib/stores/cart-store'
import { listBrowseHistory, moveWishlistToCart } from '@/lib/api/trading-api'
import { getStoreProductClient } from '@/lib/api/catalog-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ProductCard, productBriefToCard } from '@/components/product/product-card'
import { colorOptionsOf, skuFor, sizesFor } from '@/components/product/product-utils'
import { cn } from '@/lib/utils'

export default function WishlistPage() {
  const { items, fetched, fetch: fetchWishlist, removeByProduct } = useWishlistStore()
  const [recent, setRecent] = useState<BrowseHistoryItem[]>([])
  const [moveTarget, setMoveTarget] = useState<WishlistItem | null>(null)

  useEffect(() => {
    void fetchWishlist()
    listBrowseHistory(8)
      .then(setRecent)
      .catch(() => setRecent([]))
  }, [fetchWishlist])

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">My Wishlist</h1>
      {fetched && items.length === 0 ? (
        <div className="mt-8 flex flex-col items-center gap-4 rounded-sm border border-dashed border-line py-16 text-center">
          <Heart className="h-12 w-12 text-line" strokeWidth={1} />
          <p className="text-ink-soft">Your wishlist is empty. Tap the heart on any style to save it here.</p>
          <Link href="/wedding-dresses" className="btn-primary">Start Browsing</Link>
        </div>
      ) : (
        <div className="mt-8 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-3">
          {items.map((item) => (
            <div key={item.productId} className="group relative">
              <ProductCard product={productBriefToCard(item.product)} />
              <button
                onClick={() => setMoveTarget(item)}
                className="absolute inset-x-3 top-[52%] cursor-pointer rounded-sm bg-canvas/95 py-2.5 text-[11px] font-medium uppercase tracking-luxe text-ink opacity-0 shadow-soft backdrop-blur transition-all duration-300 group-hover:opacity-100"
              >
                <ShoppingBag className="mr-1 inline h-3.5 w-3.5" /> Move to bag
              </button>
            </div>
          ))}
        </div>
      )}

      {recent.length > 0 && (
        <section className="mt-16">
          <h2 className="mb-6 font-display text-2xl font-medium">Recently Viewed</h2>
          <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {recent.slice(0, 4).map((h) => <ProductCard key={h.productId} product={productBriefToCard(h.product)} />)}
          </div>
        </section>
      )}

      {moveTarget && (
        <MoveToBagModal
          item={moveTarget}
          onClose={() => setMoveTarget(null)}
          onMoved={() => {
            setMoveTarget(null)
            void removeByProductSafely(moveTarget.productId)
          }}
        />
      )}
    </div>
  )

  async function removeByProductSafely(productId: number) {
    try {
      await removeByProduct(productId)
    } catch {
      // move-to-cart 单事务已移除收藏 → 404604 容错为本地剔除
      useWishlistStore.setState((s) => ({
        ids: s.ids.filter((id) => id !== productId),
        items: s.items.filter((i) => i.productId !== productId)
      }))
    }
  }
}

/** Move to bag：实时拉详情选 SKU → moveWishlistToCart（定制款跳 PDP，422604 预判） */
function MoveToBagModal({ item, onClose, onMoved }: { item: WishlistItem; onClose: () => void; onMoved: () => void }) {
  const { te } = useI18n()
  const refreshCart = useCartStore((s) => s.refresh)
  const setCartOpen = useCartStore((s) => s.setCartOpen)
  const [detail, setDetail] = useState<StoreProductDetail | null>(null)
  const [failed, setFailed] = useState(false)
  const [color, setColor] = useState('')
  const [size, setSize] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    getStoreProductClient(item.product.slug)
      .then((d) => {
        if (!active) return
        setDetail(d)
        setColor(colorOptionsOf(d)[0]?.name ?? '')
      })
      .catch(() => active && setFailed(true))
    return () => {
      active = false
    }
  }, [item.product.slug])

  const colors = useMemo(() => (detail ? colorOptionsOf(detail) : []), [detail])
  const sizes = useMemo(() => (detail ? sizesFor(detail, color) : []), [detail, color])
  const customOnly = detail !== null && detail.skus.length === 0 && !!detail.customSizeAvailable

  const move = async () => {
    if (!detail || !size) return
    const sku = skuFor(detail, color, size)
    if (!sku?.id) { setError(te(422604)); return }
    setBusy(true)
    setError(null)
    try {
      await moveWishlistToCart(item.productId, { skuId: sku.id, qty: 1 })
      await refreshCart()
      setCartOpen(true)
      onMoved()
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-md animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <p className="eyebrow">Move to bag</p>
        <h2 className="mt-1 font-display text-2xl font-medium">{item.product.name}</h2>

        {failed && <p className="mt-4 text-sm text-blush">{te(50000)}</p>}
        {!detail && !failed && <p className="mt-4 text-sm text-ink-soft">Loading…</p>}

        {customOnly && (
          <div className="mt-5 space-y-3">
            <p className="rounded-sm bg-sage/10 px-4 py-3 text-sm text-sage-deep">This style is made-to-measure — add your measurements on the product page.</p>
            <Link href={`/product/${item.product.slug}`} className="btn-primary w-full">Open Product Page</Link>
          </div>
        )}

        {detail && !customOnly && (
          <>
            {colors.length > 0 && (
              <div className="mt-5">
                <p className="eyebrow mb-2">Color — <span className="text-ink-soft">{color}</span></p>
                <div className="flex flex-wrap gap-2">
                  {colors.map((c) => (
                    c.image ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img key={c.name} src={c.image} alt={c.name} title={c.name} onClick={() => { setColor(c.name); setSize(null) }} className={cn('h-8 w-8 cursor-pointer rounded-full border-2 object-cover transition-all', color === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')} />
                    ) : (
                      <button key={c.name} onClick={() => { setColor(c.name); setSize(null) }} className={cn('h-8 w-8 cursor-pointer rounded-full border-2 bg-muted transition-all', color === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')} title={c.name} aria-label={c.name} />
                    )
                  ))}
                </div>
              </div>
            )}
            <div className="mt-4">
              <p className="eyebrow mb-2">Size</p>
              <div className="flex flex-wrap gap-2">
                {sizes.map((s) => (
                  <button key={s.size} disabled={!s.inStock} onClick={() => setSize(s.size)} className={cn('min-w-[3rem] cursor-pointer rounded-sm border px-3 py-1.5 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through', size === s.size ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink')}>{s.size}</button>
                ))}
              </div>
            </div>
            {error && <p className="mt-3 text-xs text-blush">{error}</p>}
            <button onClick={() => void move()} disabled={!size || busy} className="btn-primary mt-5 w-full disabled:opacity-60">
              {busy ? 'Moving…' : size ? 'Move to Bag' : 'Select a Size'}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
