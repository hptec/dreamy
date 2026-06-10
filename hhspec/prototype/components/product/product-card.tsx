'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Heart, Users } from 'lucide-react'
import type { Product } from '@/data/types'
import { useStore } from '@/components/store-provider'
import { formatPrice, installments, cn } from '@/lib/utils'
import { Badge, Stars } from '@/components/ui/primitives'
import { AddToShowroomModal } from '@/components/showroom/add-to-showroom-modal'

export function ProductCard({ product, onQuickView }: { product: Product; onQuickView?: (p: Product) => void }) {
  const { currency, toggleWishlist, isWished, isInAnyShowroom } = useStore()
  const [hover, setHover] = useState(false)
  const [showroomOpen, setShowroomOpen] = useState(false)
  const wished = isWished(product.id)
  const inShowroom = isInAnyShowroom(product.id)
  const primary = product.gallery[0]
  const secondary = product.gallery[1] ?? primary

  return (
    <div className="group relative" onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}>
      <Link href={`/product/${product.slug}`} className="block">
        <div className="relative aspect-[3/4] overflow-hidden rounded-sm bg-muted">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={primary} alt={product.name} className={cn('absolute inset-0 h-full w-full object-cover transition-opacity duration-700 ease-luxe', hover && secondary !== primary ? 'opacity-0' : 'opacity-100')} />
          {secondary !== primary && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={secondary} alt={`${product.name} alternate`} className={cn('absolute inset-0 h-full w-full object-cover transition-opacity duration-700 ease-luxe', hover ? 'opacity-100' : 'opacity-0')} />
          )}
          {product.badges?.[0] && (
            <div className="absolute left-3 top-3">
              <Badge variant={product.badges[0] === 'Sale' ? 'sale' : product.badges[0] === 'New' ? 'new' : 'default'}>{product.badges[0]}</Badge>
            </div>
          )}
          {onQuickView && (
            <button
              onClick={(e) => { e.preventDefault(); onQuickView(product) }}
              className={cn('absolute inset-x-3 bottom-3 cursor-pointer rounded-sm bg-canvas/95 py-2.5 text-[11px] font-medium uppercase tracking-luxe text-ink shadow-soft backdrop-blur transition-all duration-300', hover ? 'translate-y-0 opacity-100' : 'translate-y-2 opacity-0')}
            >
              Quick View
            </button>
          )}
        </div>
      </Link>
      <button
        onClick={() => toggleWishlist(product.id)}
        className="absolute right-3 top-3 cursor-pointer rounded-full bg-canvas/90 p-2 shadow-soft backdrop-blur transition-colors hover:bg-canvas"
        aria-label={wished ? 'Remove from wishlist' : 'Add to wishlist'}
      >
        <Heart className={cn('h-4 w-4 transition-colors', wished ? 'fill-blush text-blush' : 'text-ink')} />
      </button>
      <button
        onClick={() => setShowroomOpen(true)}
        className={cn('absolute right-3 top-12 cursor-pointer rounded-full bg-canvas/90 p-2 shadow-soft backdrop-blur transition-all hover:bg-canvas focus-visible:opacity-100', hover || inShowroom ? 'opacity-100' : 'opacity-0')}
        aria-label="Add to Showroom"
        title="Add to Showroom"
      >
        <Users className={cn('h-4 w-4 transition-colors', inShowroom ? 'text-gold-deep' : 'text-ink')} />
      </button>

      <div className="mt-3.5">
        <div className="flex items-start justify-between gap-2">
          <Link href={`/product/${product.slug}`} className="text-sm font-medium leading-snug transition-colors hover:text-gold-deep">{product.name}</Link>
        </div>
        <div className="mt-1 flex items-center gap-2">
          <Stars rating={product.rating} />
          <span className="text-xs text-ink-faint">({product.reviewCount})</span>
        </div>
        <div className="mt-1.5 flex items-center gap-2">
          <span className="text-sm font-medium text-ink">{formatPrice(product.price, currency)}</span>
          {product.compareAtPrice && <span className="text-sm text-ink-faint line-through">{formatPrice(product.compareAtPrice, currency)}</span>}
        </div>
        <p className="mt-0.5 text-[11px] text-ink-faint">or 4× {formatPrice(Number(installments(product.price)), currency)} with Klarna</p>
        {product.colors.length > 1 && (
          <div className="mt-2 flex items-center gap-1.5">
            {product.colors.slice(0, 5).map((c) => (
              <span key={c.name} className="h-3.5 w-3.5 rounded-full border border-line" style={{ backgroundColor: c.hex }} title={c.name} />
            ))}
            {product.colors.length > 5 && <span className="text-[10px] text-ink-faint">+{product.colors.length - 5}</span>}
          </div>
        )}
      </div>
      {showroomOpen && <AddToShowroomModal product={product} open={showroomOpen} onClose={() => setShowroomOpen(false)} />}
    </div>
  )
}
