'use client'

import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react'
import type { Currency } from '@/data/types'

export interface CartLine {
  productId: string
  slug: string
  name: string
  image: string
  price: number
  color: string
  size: string
  qty: number
}

interface StoreState {
  cart: CartLine[]
  wishlist: string[]
  recentlyViewed: string[]
  currency: Currency
  language: string
  cartOpen: boolean
  addToCart: (line: Omit<CartLine, 'qty'>, qty?: number) => void
  updateQty: (index: number, qty: number) => void
  removeLine: (index: number) => void
  clearCart: () => void
  toggleWishlist: (productId: string) => void
  isWished: (productId: string) => boolean
  trackView: (productId: string) => void
  setCurrency: (c: Currency) => void
  setLanguage: (l: string) => void
  setCartOpen: (open: boolean) => void
  cartCount: number
  cartSubtotal: number
}

const StoreContext = createContext<StoreState | null>(null)

function usePersisted<T>(key: string, initial: T): [T, (v: T | ((p: T) => T)) => void] {
  const [value, setValue] = useState<T>(initial)
  const [hydrated, setHydrated] = useState(false)

  useEffect(() => {
    try {
      const raw = localStorage.getItem(key)
      if (raw) setValue(JSON.parse(raw))
    } catch {
      /* ignore */
    }
    setHydrated(true)
  }, [key])

  useEffect(() => {
    if (hydrated) {
      try {
        localStorage.setItem(key, JSON.stringify(value))
      } catch {
        /* ignore */
      }
    }
  }, [key, value, hydrated])

  return [value, setValue]
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [cart, setCart] = usePersisted<CartLine[]>('dreamy_cart', [])
  const [wishlist, setWishlist] = usePersisted<string[]>('dreamy_wishlist', [])
  const [recentlyViewed, setRecentlyViewed] = usePersisted<string[]>('dreamy_recent', [])
  const [currency, setCurrency] = usePersisted<Currency>('dreamy_currency', 'USD')
  const [language, setLanguage] = usePersisted<string>('dreamy_lang', 'EN')
  const [cartOpen, setCartOpen] = useState(false)

  const addToCart = useCallback(
    (line: Omit<CartLine, 'qty'>, qty = 1) => {
      setCart((prev) => {
        const idx = prev.findIndex((l) => l.productId === line.productId && l.color === line.color && l.size === line.size)
        if (idx >= 0) {
          const next = [...prev]
          next[idx] = { ...next[idx], qty: next[idx].qty + qty }
          return next
        }
        return [...prev, { ...line, qty }]
      })
      setCartOpen(true)
    },
    [setCart]
  )

  const updateQty = useCallback(
    (index: number, qty: number) => {
      setCart((prev) => prev.map((l, i) => (i === index ? { ...l, qty: Math.max(1, qty) } : l)))
    },
    [setCart]
  )

  const removeLine = useCallback((index: number) => setCart((prev) => prev.filter((_, i) => i !== index)), [setCart])
  const clearCart = useCallback(() => setCart([]), [setCart])

  const toggleWishlist = useCallback(
    (productId: string) => {
      setWishlist((prev) => (prev.includes(productId) ? prev.filter((id) => id !== productId) : [...prev, productId]))
    },
    [setWishlist]
  )

  const isWished = useCallback((productId: string) => wishlist.includes(productId), [wishlist])

  const trackView = useCallback(
    (productId: string) => {
      setRecentlyViewed((prev) => [productId, ...prev.filter((id) => id !== productId)].slice(0, 8))
    },
    [setRecentlyViewed]
  )

  const cartCount = cart.reduce((sum, l) => sum + l.qty, 0)
  const cartSubtotal = cart.reduce((sum, l) => sum + l.price * l.qty, 0)

  const value: StoreState = {
    cart, wishlist, recentlyViewed, currency, language, cartOpen,
    addToCart, updateQty, removeLine, clearCart,
    toggleWishlist, isWished, trackView,
    setCurrency, setLanguage, setCartOpen,
    cartCount, cartSubtotal
  }

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error('useStore must be used within StoreProvider')
  return ctx
}
