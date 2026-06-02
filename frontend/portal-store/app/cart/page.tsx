'use client'

import Link from 'next/link'
import { X, Plus, Minus, Heart, ShoppingBag, Truck, ShieldCheck } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { formatPrice } from '@/lib/utils'
import { products } from '@/data/products'
import { ProductCard } from '@/components/product/product-card'

export default function CartPage() {
  const { cart, updateQty, removeLine, toggleWishlist, cartSubtotal, currency } = useStore()
  const shipping = cartSubtotal > 200 || cartSubtotal === 0 ? 0 : 25
  const total = cartSubtotal + shipping
  const recommended = products.filter((p) => p.isBestSeller).slice(0, 4)

  if (cart.length === 0) {
    return (
      <div className="container-luxe py-20">
        <div className="flex flex-col items-center gap-5 py-16 text-center">
          <ShoppingBag className="h-14 w-14 text-line" strokeWidth={1} />
          <h1 className="font-display text-4xl font-medium">Your bag is empty</h1>
          <p className="max-w-sm text-ink-soft">Looks like you haven&apos;t added anything yet. Let&apos;s find the one.</p>
          <Link href="/wedding-dresses" className="btn-primary">Shop Wedding Dresses</Link>
        </div>
        <section className="mt-16">
          <h2 className="mb-8 text-center font-display text-3xl font-medium">Best Sellers</h2>
          <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {recommended.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className="container-luxe py-12">
      <h1 className="font-display text-4xl font-medium">Your Bag</h1>
      <div className="mt-8 grid gap-12 lg:grid-cols-3">
        {/* Lines */}
        <div className="lg:col-span-2">
          {cart.map((line, i) => (
            <div key={`${line.productId}-${i}`} className="flex gap-5 border-b border-line py-6">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={line.image} alt={line.name} className="h-40 w-28 rounded-sm object-cover" />
              <div className="flex flex-1 flex-col">
                <div className="flex justify-between">
                  <Link href={`/product/${line.slug}`} className="font-display text-xl font-medium hover:text-gold-deep">{line.name}</Link>
                  <span className="font-medium">{formatPrice(line.price * line.qty, currency)}</span>
                </div>
                <p className="mt-1 text-sm text-ink-soft">{line.color} · Size {line.size}</p>
                <div className="mt-auto flex items-center gap-4">
                  <div className="flex items-center border border-line">
                    <button onClick={() => updateQty(i, line.qty - 1)} className="cursor-pointer p-2" aria-label="Decrease"><Minus className="h-3.5 w-3.5" /></button>
                    <span className="w-9 text-center text-sm">{line.qty}</span>
                    <button onClick={() => updateQty(i, line.qty + 1)} className="cursor-pointer p-2" aria-label="Increase"><Plus className="h-3.5 w-3.5" /></button>
                  </div>
                  <button onClick={() => { toggleWishlist(line.productId); removeLine(i) }} className="flex cursor-pointer items-center gap-1 text-xs text-ink-soft hover:text-gold-deep"><Heart className="h-3.5 w-3.5" /> Save for later</button>
                  <button onClick={() => removeLine(i)} className="flex cursor-pointer items-center gap-1 text-xs text-ink-soft hover:text-blush"><X className="h-3.5 w-3.5" /> Remove</button>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div>
          <div className="rounded-sm border border-line bg-surface p-6 lg:sticky lg:top-28">
            <h2 className="font-display text-2xl font-medium">Order Summary</h2>
            <div className="mt-4 flex gap-2">
              <input placeholder="Promo code" className="flex-1 rounded-sm border border-line bg-canvas px-3 py-2.5 text-sm outline-none focus:border-gold" aria-label="Promo code" />
              <button className="cursor-pointer rounded-sm border border-ink px-4 py-2.5 text-xs uppercase tracking-luxe transition-colors hover:bg-ink hover:text-canvas">Apply</button>
            </div>
            <dl className="mt-5 space-y-2.5 border-t border-line pt-5 text-sm">
              <div className="flex justify-between"><dt className="text-ink-soft">Subtotal</dt><dd>{formatPrice(cartSubtotal, currency)}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-soft">Shipping</dt><dd>{shipping === 0 ? 'Free' : formatPrice(shipping, currency)}</dd></div>
              <div className="flex justify-between border-t border-line pt-3 text-base font-medium"><dt>Total</dt><dd className="font-display text-xl">{formatPrice(total, currency)}</dd></div>
            </dl>
            <p className="mt-2 text-xs text-ink-faint">or 4 interest-free payments of {formatPrice(total / 4, currency)} with Klarna</p>
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
