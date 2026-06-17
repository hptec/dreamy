'use client'

/**
 * CartDrawer（data-swap）：cart 上下文 → API 驱动 cartStore（双态）。
 * 行结构不变；定制行显示 Custom 标签；409601 行内提示由 cart 页承载。
 */

import { LocalizedLink as Link } from '@/components/localized-link'
import { X, ShoppingBag, Plus, Minus } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { useI18n } from '@/lib/i18n/i18n-context'
import { formatPrice } from '@/lib/utils'

export function CartDrawer() {
  const { cart, cartOpen, setCartOpen, updateQty, removeLine, cartSubtotal, currency } = useStore()
  const { t } = useI18n()

  if (!cartOpen) return null

  return (
    <div className="fixed inset-0 z-50">
      <div className="absolute inset-0 bg-ink/40 backdrop-blur-sm" onClick={() => setCartOpen(false)} />
      <aside className="absolute right-0 top-0 flex h-full w-full max-w-md animate-fadeup flex-col bg-canvas shadow-lift">
        <div className="flex items-center justify-between border-b border-line px-6 py-5">
          <h2 className="font-display text-2xl font-medium">{t.cart.drawer.title} ({cart.length})</h2>
          <button onClick={() => setCartOpen(false)} className="cursor-pointer p-1" aria-label={t.common.close}><X className="h-5 w-5" /></button>
        </div>

        {cart.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
            <ShoppingBag className="h-12 w-12 text-line" />
            <p className="text-ink-soft">{t.cart.drawer.empty}</p>
            <Link href="/wedding-dresses" onClick={() => setCartOpen(false)} className="btn-outline">{t.common.continueShopping}</Link>
          </div>
        ) : (
          <>
            <div className="flex-1 overflow-y-auto px-6 py-4">
              {cart.map((line) => (
                <div key={line.key} className="flex gap-4 border-b border-line/60 py-4">
                  {line.image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={line.image} alt={line.name} className="h-28 w-20 rounded-sm object-cover" />
                  ) : (
                    <div className="h-28 w-20 rounded-sm bg-muted" />
                  )}
                  <div className="flex flex-1 flex-col">
                    <Link href={`/product/${line.slug}`} onClick={() => setCartOpen(false)} className="text-sm font-medium leading-snug hover:text-gold-deep">{line.name}</Link>
                    <p className="mt-1 text-xs text-ink-soft">
                      {[line.color, line.customSizeData ? t.cart.drawer.customSize : line.size].filter(Boolean).join(' · ')}
                    </p>
                    <div className="mt-auto flex items-center justify-between">
                      <div className="flex items-center border border-line">
                        <button onClick={() => void updateQty(line.key, line.qty - 1)} className="cursor-pointer p-1.5" aria-label={t.cart.drawer.decrease}><Minus className="h-3 w-3" /></button>
                        <span className="w-8 text-center text-sm">{line.qty}</span>
                        <button onClick={() => void updateQty(line.key, line.qty + 1)} className="cursor-pointer p-1.5" aria-label={t.cart.drawer.increase}><Plus className="h-3 w-3" /></button>
                      </div>
                      <span className="text-sm font-medium">{formatPrice(line.priceUsd * line.qty, currency, line.multiCurrencyPrices)}</span>
                    </div>
                  </div>
                  <button onClick={() => void removeLine(line.key)} className="cursor-pointer self-start p-1 text-ink-faint hover:text-blush" aria-label={t.cart.drawer.remove}><X className="h-4 w-4" /></button>
                </div>
              ))}
            </div>
            <div className="border-t border-line px-6 py-5">
              <div className="mb-1 flex items-center justify-between">
                <span className="text-sm text-ink-soft">{t.cart.drawer.subtotal}</span>
                <span className="font-display text-xl font-medium">{formatPrice(cartSubtotal, currency)}</span>
              </div>
              <p className="mb-4 text-xs text-ink-faint">{t.cart.drawer.installments.replace('{amount}', formatPrice(cartSubtotal / 4, currency))}</p>
              <Link href="/checkout" onClick={() => setCartOpen(false)} className="btn-primary w-full">{t.cart.drawer.checkout}</Link>
              <Link href="/cart" onClick={() => setCartOpen(false)} className="mt-2 block text-center text-sm text-ink-soft underline">{t.cart.drawer.viewFullBag}</Link>
            </div>
          </>
        )}
      </aside>
    </div>
  )
}
