'use client'

/**
 * ProductCard（COMP-CAT-S01，layout-keep + data-swap）：
 * props 由 mock Product 切换为 StoreProductCard（imageUrl/swatches/ratingAvg/ratingCount/isNew/isBest/compareAt）。
 * 视觉结构不变；收藏未登录 → 引导 /account/login（决策 18）。
 */

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Heart } from 'lucide-react'
import type { StoreProductCard } from '@/lib/api/store-types'
import { useStore } from '@/components/store-provider'
import { formatPrice, installments, cn } from '@/lib/utils'
import { Badge, Stars } from '@/components/ui/primitives'

export function badgeOf(product: StoreProductCard): { label: string; variant: 'default' | 'sale' | 'new' } | null {
  if (product.isNew) return { label: 'New', variant: 'new' }
  if (product.compareAt && product.compareAt > product.price) return { label: 'Sale', variant: 'sale' }
  if (product.isBest) return { label: 'Best Seller', variant: 'default' }
  return null
}

export function ProductCard({ product, onQuickView }: { product: StoreProductCard; onQuickView?: (p: StoreProductCard) => void }) {
  const router = useRouter()
  const { currency, toggleWishlist, isWished } = useStore()
  const wished = isWished(product.id)
  const badge = badgeOf(product)
  const rating = product.ratingAvg ?? 0
  const swatches = product.swatches ?? []

  const onWish = async () => {
    const ok = await toggleWishlist(product.id)
    if (!ok) router.push(`/account/login?returnTo=/product/${product.slug}`)
  }

  return (
    <div className="group relative">
      <Link href={`/product/${product.slug}`} className="block">
        <div className="relative aspect-[3/4] overflow-hidden rounded-sm bg-muted">
          {product.imageUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={product.imageUrl} alt={product.name} className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
          )}
          {badge && (
            <div className="absolute left-3 top-3">
              <Badge variant={badge.variant}>{badge.label}</Badge>
            </div>
          )}
          {onQuickView && (
            <button
              onClick={(e) => { e.preventDefault(); onQuickView(product) }}
              className="absolute inset-x-3 bottom-3 cursor-pointer rounded-sm bg-canvas/95 py-2.5 text-[11px] font-medium uppercase tracking-luxe text-ink opacity-0 shadow-soft backdrop-blur transition-all duration-300 group-hover:translate-y-0 group-hover:opacity-100"
            >
              Quick View
            </button>
          )}
        </div>
      </Link>
      <button
        onClick={onWish}
        className="absolute right-3 top-3 cursor-pointer rounded-full bg-canvas/90 p-2 shadow-soft backdrop-blur transition-colors hover:bg-canvas"
        aria-label={wished ? 'Remove from wishlist' : 'Add to wishlist'}
      >
        <Heart className={cn('h-4 w-4 transition-colors', wished ? 'fill-blush text-blush' : 'text-ink')} />
      </button>

      <div className="mt-3.5">
        <div className="flex items-start justify-between gap-2">
          <Link href={`/product/${product.slug}`} className="text-sm font-medium leading-snug transition-colors hover:text-gold-deep">{product.name}</Link>
        </div>
        {(product.ratingCount ?? 0) > 0 && (
          <div className="mt-1 flex items-center gap-2">
            <Stars rating={rating} />
            <span className="text-xs text-ink-faint">({product.ratingCount})</span>
          </div>
        )}
        <div className="mt-1.5 flex items-center gap-2">
          <span className="text-sm font-medium text-ink">{formatPrice(product.price, currency, product.multiCurrencyPrices)}</span>
          {product.compareAt && product.compareAt > product.price && (
            <span className="text-sm text-ink-faint line-through">{formatPrice(product.compareAt, currency)}</span>
          )}
        </div>
        {product.installment !== false && (
          <p className="mt-0.5 text-[11px] text-ink-faint">or 4× {formatPrice(Number(installments(product.price)), currency)} with Klarna</p>
        )}
        {swatches.length > 1 && (
          <div className="mt-2 flex items-center gap-1.5">
            {swatches.slice(0, 5).map((c, i) => (
              c.url ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img key={`${c.colorName}-${i}`} src={c.url} alt={c.colorName ?? ''} title={c.colorName} className="h-3.5 w-3.5 rounded-full border border-line object-cover" />
              ) : (
                <span key={`${c.colorName}-${i}`} className="h-3.5 w-3.5 rounded-full border border-line bg-muted" title={c.colorName} />
              )
            ))}
            {swatches.length > 5 && <span className="text-[10px] text-ink-faint">+{swatches.length - 5}</span>}
          </div>
        )}
      </div>
    </div>
  )
}

/** ProductRef（内容关联商品）→ 卡片 props 子集映射（COMP-MKT-S03/S06 复用） */
export function productRefToCard(ref: { id: number; slug: string; name: string; price: number; imageUrl?: string }): StoreProductCard {
  return { id: ref.id, slug: ref.slug, name: ref.name, price: ref.price, imageUrl: ref.imageUrl, installment: false }
}

/** ProductBrief（购物车/收藏/浏览历史）→ 卡片 props 映射 */
export function productBriefToCard(brief: {
  id: number
  slug: string
  name: string
  price: number
  compareAt?: number | null
  multiCurrencyPrices?: Record<string, number> | null
  imageUrl?: string
}): StoreProductCard {
  return {
    id: brief.id,
    slug: brief.slug,
    name: brief.name,
    price: brief.price,
    compareAt: brief.compareAt,
    multiCurrencyPrices: brief.multiCurrencyPrices,
    imageUrl: brief.imageUrl
  }
}
