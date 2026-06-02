'use client'

import { useState } from 'react'
import Link from 'next/link'
import { X } from 'lucide-react'
import type { Product } from '@/data/types'
import { useStore } from '@/components/store-provider'
import { formatPrice, cn } from '@/lib/utils'
import { Stars } from '@/components/ui/primitives'

export function QuickViewModal({ product, onClose }: { product: Product; onClose: () => void }) {
  const { currency, addToCart } = useStore()
  const [color, setColor] = useState(product.colors[0])
  const [size, setSize] = useState<string | null>(null)

  const add = () => {
    if (!size) return
    addToCart({ productId: product.id, slug: product.slug, name: product.name, image: color.image, price: product.price, color: color.name, size })
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative grid w-full max-w-3xl animate-fadeup overflow-hidden rounded-sm bg-canvas shadow-lift sm:grid-cols-2">
        <button onClick={onClose} className="absolute right-3 top-3 z-10 cursor-pointer rounded-full bg-canvas/80 p-1.5" aria-label="Close"><X className="h-4 w-4" /></button>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={color.image} alt={product.name} className="aspect-[3/4] h-full w-full object-cover" />
        <div className="flex flex-col p-7">
          <h2 className="font-display text-2xl font-medium">{product.name}</h2>
          <div className="mt-1.5 flex items-center gap-2">
            <Stars rating={product.rating} /><span className="text-xs text-ink-faint">({product.reviewCount})</span>
          </div>
          <p className="mt-2 text-lg font-medium">{formatPrice(product.price, currency)}</p>

          <div className="mt-4">
            <p className="eyebrow mb-2">Color: {color.name}</p>
            <div className="flex gap-2">
              {product.colors.map((c) => (
                <button key={c.name} onClick={() => setColor(c)} className={cn('h-7 w-7 rounded-full border-2 transition-all', color.name === c.name ? 'border-gold ring-1 ring-gold/30' : 'border-line')} style={{ backgroundColor: c.hex }} title={c.name} aria-label={c.name} />
              ))}
            </div>
          </div>

          <div className="mt-4">
            <p className="eyebrow mb-2">Size</p>
            <div className="flex flex-wrap gap-2">
              {product.sizes.map((s) => (
                <button key={s.size} disabled={!s.inStock} onClick={() => setSize(s.size)} className={cn('min-w-[3rem] cursor-pointer rounded-sm border px-3 py-1.5 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through', size === s.size ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink')}>{s.size}</button>
              ))}
            </div>
          </div>

          <div className="mt-auto pt-6">
            <button onClick={add} disabled={!size} className="btn-primary w-full disabled:opacity-50">{size ? 'Add to Bag' : 'Select a Size'}</button>
            <Link href={`/product/${product.slug}`} onClick={onClose} className="mt-2 block text-center text-sm text-ink-soft underline">View full details</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
