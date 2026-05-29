'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { Heart, Truck, Sparkles, Ruler, ChevronDown, Plus, Minus } from 'lucide-react'
import type { Product } from '@/data/types'
import { useStore } from '@/components/store-provider'
import { formatPrice, installments, cn } from '@/lib/utils'
import { Stars, Badge } from '@/components/ui/primitives'
import { SizeGuideModal } from './size-guide-modal'

export function ProductBuyBox({ product }: { product: Product }) {
  const { currency, addToCart, toggleWishlist, isWished, trackView } = useStore()
  const [color, setColor] = useState(product.colors[0])
  const [size, setSize] = useState<string | null>(null)
  const [qty, setQty] = useState(1)
  const [sizeGuide, setSizeGuide] = useState(false)
  const [error, setError] = useState(false)
  const wished = isWished(product.id)

  useEffect(() => { trackView(product.id) }, [product.id, trackView])

  const add = () => {
    if (!size) { setError(true); return }
    addToCart({ productId: product.id, slug: product.slug, name: product.name, image: color.image, price: product.price, color: color.name, size }, qty)
  }

  return (
    <div className="lg:sticky lg:top-28">
      {product.badges?.[0] && <div className="mb-3"><Badge variant={product.badges[0] === 'Sale' ? 'sale' : product.badges[0] === 'New' ? 'new' : 'default'}>{product.badges[0]}</Badge></div>}
      <p className="eyebrow">{product.subCategory}</p>
      <h1 className="mt-1 font-display text-3xl font-medium leading-tight lg:text-4xl">{product.name}</h1>
      <div className="mt-3 flex items-center gap-2">
        <Stars rating={product.rating} />
        <a href="#reviews" className="text-sm text-ink-soft underline">{product.reviewCount} reviews</a>
      </div>

      <div className="mt-4 flex items-center gap-3">
        <span className="font-display text-2xl font-medium">{formatPrice(product.price, currency)}</span>
        {product.compareAtPrice && <span className="text-lg text-ink-faint line-through">{formatPrice(product.compareAtPrice, currency)}</span>}
      </div>
      <p className="mt-1 text-sm text-ink-soft">or 4 interest-free payments of {formatPrice(Number(installments(product.price)), currency)} with <strong>Klarna</strong></p>

      {/* Color */}
      <div className="mt-6">
        <p className="eyebrow mb-2.5">Color — <span className="text-ink-soft">{color.name}</span></p>
        <div className="flex flex-wrap gap-2.5">
          {product.colors.map((c) => (
            <button key={c.name} onClick={() => setColor(c)} className={cn('h-9 w-9 rounded-full border-2 transition-all', color.name === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')} style={{ backgroundColor: c.hex }} title={c.name} aria-label={c.name} />
          ))}
        </div>
      </div>

      {/* Size */}
      <div className="mt-6">
        <div className="mb-2.5 flex items-center justify-between">
          <p className="eyebrow">Size</p>
          <button onClick={() => setSizeGuide(true)} className="flex cursor-pointer items-center gap-1 text-xs text-gold-deep underline"><Ruler className="h-3.5 w-3.5" /> Size Guide</button>
        </div>
        <div className="flex flex-wrap gap-2">
          {product.sizes.map((s) => (
            <button
              key={s.size}
              disabled={!s.inStock}
              onClick={() => { setSize(s.size); setError(false) }}
              className={cn('min-w-[3.25rem] cursor-pointer rounded-sm border px-3 py-2 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through',
                size === s.size ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink',
                s.size === 'Custom' && size !== 'Custom' && 'border-gold/50 text-gold-deep')}
            >
              {s.size}
            </button>
          ))}
        </div>
        {error && <p className="mt-2 text-xs text-blush">Please select a size to continue.</p>}
        {size === 'Custom' && <p className="mt-2 rounded-sm bg-sage/10 px-3 py-2 text-xs text-sage-deep">Made-to-measure at no extra cost. Add your measurements at checkout. Allow 4–6 weeks.</p>}
      </div>

      {/* Qty + CTA */}
      <div className="mt-6 flex items-center gap-3">
        <div className="flex items-center border border-line">
          <button onClick={() => setQty(Math.max(1, qty - 1))} className="cursor-pointer p-3" aria-label="Decrease quantity"><Minus className="h-3.5 w-3.5" /></button>
          <span className="w-10 text-center text-sm">{qty}</span>
          <button onClick={() => setQty(qty + 1)} className="cursor-pointer p-3" aria-label="Increase quantity"><Plus className="h-3.5 w-3.5" /></button>
        </div>
        <button onClick={add} className="btn-primary flex-1">Add to Bag</button>
        <button onClick={() => toggleWishlist(product.id)} className="cursor-pointer rounded-sm border border-line p-3.5 transition-colors hover:border-ink" aria-label="Add to wishlist">
          <Heart className={cn('h-5 w-5', wished ? 'fill-blush text-blush' : 'text-ink')} />
        </button>
      </div>

      <div className="mt-3 flex gap-3">
        <button className="flex-1 cursor-pointer rounded-sm border border-line py-3 text-[12px] font-medium uppercase tracking-luxe transition-colors hover:border-gold hover:text-gold-deep">Order a Swatch</button>
        <button className="flex-1 cursor-pointer rounded-sm border border-line py-3 text-[12px] font-medium uppercase tracking-luxe text-ink-faint" title="Coming soon" disabled>Try in AR · Soon</button>
      </div>

      {/* Trust 条 */}
      <div className="mt-6 space-y-2.5 border-t border-line pt-5 text-sm text-ink-soft">
        <p className="flex items-center gap-2"><Truck className="h-4 w-4 text-gold" /> Free worldwide shipping over $200</p>
        <p className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-gold" /> Order fabric swatches before you commit</p>
        <p className="flex items-center gap-2"><Ruler className="h-4 w-4 text-gold" /> Custom sizing available</p>
      </div>

      {/* 描述折叠 */}
      <div className="mt-6 divide-y divide-line border-y border-line">
        <Accordion title="Description" defaultOpen>
          <p>{product.description}</p>
        </Accordion>
        <Accordion title="Details">
          <ul className="list-inside list-disc space-y-1">{product.details.map((d) => <li key={d}>{d}</li>)}</ul>
        </Accordion>
        <Accordion title="Fabric & Care">
          <ul className="list-inside list-disc space-y-1">{product.fabricCare.map((d) => <li key={d}>{d}</li>)}</ul>
        </Accordion>
        <Accordion title="Shipping & Delivery">
          <p>Standard production 1–2 weeks. Custom & made-to-measure 4–6 weeks. Free worldwide shipping on orders over $200 via FedEx, UPS, or DHL Express.</p>
        </Accordion>
      </div>

      <SizeGuideModal open={sizeGuide} onClose={() => setSizeGuide(false)} />
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
