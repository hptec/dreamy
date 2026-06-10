'use client'

import Link from 'next/link'
import { X, ShoppingBag, Plus, Minus } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { formatPrice, formatCustomSize } from '@/lib/utils'

export function CartDrawer() {
  const { cart, cartOpen, setCartOpen, updateQty, removeLine, cartSubtotal, currency } = useStore()

  if (!cartOpen) return null

  return (
    <div className="fixed inset-0 z-50">
      <div className="absolute inset-0 bg-ink/40 backdrop-blur-sm" onClick={() => setCartOpen(false)} />
      <aside className="absolute right-0 top-0 flex h-full w-full max-w-md animate-fadeup flex-col bg-canvas shadow-lift">
        <div className="flex items-center justify-between border-b border-line px-6 py-5">
          <h2 className="font-display text-2xl font-medium">Your Bag ({cart.length})</h2>
          <button onClick={() => setCartOpen(false)} className="cursor-pointer p-1" aria-label="Close cart"><X className="h-5 w-5" /></button>
        </div>

        {cart.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
            <ShoppingBag className="h-12 w-12 text-line" />
            <p className="text-ink-soft">Your bag is empty.</p>
            <Link href="/wedding-dresses" onClick={() => setCartOpen(false)} className="btn-outline">Continue Shopping</Link>
          </div>
        ) : (
          <>
            <div className="flex-1 overflow-y-auto px-6 py-4">
              {cart.map((line, i) => (
                <div key={`${line.productId}-${i}`} className="flex gap-4 border-b border-line/60 py-4">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={line.image} alt={line.name} className="h-28 w-20 rounded-sm object-cover" />
                  <div className="flex flex-1 flex-col">
                    <Link href={`/product/${line.slug}`} onClick={() => setCartOpen(false)} className="text-sm font-medium leading-snug hover:text-gold-deep">{line.name}</Link>
                    <p className="mt-1 text-xs text-ink-soft">{line.color} · {line.size}</p>
                    {line.customSize && <p className="mt-0.5 text-[11px] leading-snug text-gold-deep">{formatCustomSize(line.customSize)}</p>}
                    <div className="mt-auto flex items-center justify-between">
                      <div className="flex items-center border border-line">
                        <button onClick={() => updateQty(i, line.qty - 1)} className="cursor-pointer p-1.5" aria-label="Decrease"><Minus className="h-3 w-3" /></button>
                        <span className="w-8 text-center text-sm">{line.qty}</span>
                        <button onClick={() => updateQty(i, line.qty + 1)} className="cursor-pointer p-1.5" aria-label="Increase"><Plus className="h-3 w-3" /></button>
                      </div>
                      <span className="text-sm font-medium">{formatPrice(line.price * line.qty, currency)}</span>
                    </div>
                  </div>
                  <button onClick={() => removeLine(i)} className="cursor-pointer self-start p-1 text-ink-faint hover:text-blush" aria-label="Remove"><X className="h-4 w-4" /></button>
                </div>
              ))}
            </div>
            <div className="border-t border-line px-6 py-5">
              <div className="mb-1 flex items-center justify-between">
                <span className="text-sm text-ink-soft">Subtotal</span>
                <span className="font-display text-xl font-medium">{formatPrice(cartSubtotal, currency)}</span>
              </div>
              <p className="mb-4 text-xs text-ink-faint">or 4 interest-free payments of {formatPrice(cartSubtotal / 4, currency)} with Klarna</p>
              <Link href="/checkout" onClick={() => setCartOpen(false)} className="btn-primary w-full">Checkout</Link>
              <Link href="/cart" onClick={() => setCartOpen(false)} className="mt-2 block text-center text-sm text-ink-soft underline">View full bag</Link>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
