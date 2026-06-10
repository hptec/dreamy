'use client'

import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react'
import type { Currency, CustomSizeMeasurements, Showroom } from '@/data/types'
import { seedShowrooms, CUSTOM_SHOWROOM_ID_POOL } from '@/data/showrooms'

export interface CartLine {
  productId: string
  slug: string
  name: string
  image: string
  price: number
  color: string
  size: string
  qty: number
  /** 定制尺寸量体数据（size === 'Custom' 时存在） */
  customSize?: CustomSizeMeasurements
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
  // ============ Showroom（迭代 4） ============
  showrooms: Showroom[]
  createShowroom: (name: string, weddingDate: string) => string | null
  deleteShowroom: (id: string) => void
  addToShowroom: (showroomId: string, productId: string, color: string) => void
  removeFromShowroom: (showroomId: string, productId: string, color: string) => void
  voteShowroomItem: (showroomId: string, productId: string, color: string, voter: string, dir: 'up' | 'down') => void
  commentShowroomItem: (showroomId: string, productId: string, color: string, author: string, text: string) => void
  assignShowroomMember: (showroomId: string, memberId: string, productId?: string, color?: string) => void
  isInAnyShowroom: (productId: string) => boolean
  /** 最近一个未过期 Showroom 的婚期（用于 PDP / 结算自动带入） */
  showroomWeddingDate: string | null
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
  const [showrooms, setShowrooms] = usePersisted<Showroom[]>('dreamy_showrooms', seedShowrooms)
  const [cartOpen, setCartOpen] = useState(false)

  const addToCart = useCallback(
    (line: Omit<CartLine, 'qty'>, qty = 1) => {
      setCart((prev) => {
        // 定制尺寸行不合并（量体数据各不相同），标准行按 商品+颜色+尺码 合并
        const idx = line.customSize
          ? -1
          : prev.findIndex((l) => !l.customSize && l.productId === line.productId && l.color === line.color && l.size === line.size)
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

  // ============ Showroom（迭代 4） ============

  const createShowroom = useCallback(
    (name: string, weddingDate: string): string | null => {
      const id = CUSTOM_SHOWROOM_ID_POOL.find((pid) => !showrooms.some((s) => s.id === pid))
      if (!id) return null
      const room: Showroom = {
        id,
        name,
        weddingDate,
        createdAt: new Date().toISOString().slice(0, 10),
        items: [],
        members: [{ id: `${id}-bride`, name: 'You', role: 'bride' }]
      }
      setShowrooms((prev) => [...prev, room])
      return id
    },
    [showrooms, setShowrooms]
  )

  const deleteShowroom = useCallback((id: string) => setShowrooms((prev) => prev.filter((s) => s.id !== id)), [setShowrooms])

  const addToShowroom = useCallback(
    (showroomId: string, productId: string, color: string) => {
      setShowrooms((prev) =>
        prev.map((s) => {
          if (s.id !== showroomId) return s
          if (s.items.some((it) => it.productId === productId && it.color === color)) return s
          return { ...s, items: [...s.items, { productId, color, upVotes: [], downVotes: [], comments: [] }] }
        })
      )
    },
    [setShowrooms]
  )

  const removeFromShowroom = useCallback(
    (showroomId: string, productId: string, color: string) => {
      setShowrooms((prev) =>
        prev.map((s) =>
          s.id === showroomId ? { ...s, items: s.items.filter((it) => !(it.productId === productId && it.color === color)) } : s
        )
      )
    },
    [setShowrooms]
  )

  const voteShowroomItem = useCallback(
    (showroomId: string, productId: string, color: string, voter: string, dir: 'up' | 'down') => {
      setShowrooms((prev) =>
        prev.map((s) => {
          if (s.id !== showroomId) return s
          return {
            ...s,
            items: s.items.map((it) => {
              if (it.productId !== productId || it.color !== color) return it
              const had = dir === 'up' ? it.upVotes.includes(voter) : it.downVotes.includes(voter)
              const upVotes = it.upVotes.filter((v) => v !== voter)
              const downVotes = it.downVotes.filter((v) => v !== voter)
              if (!had) (dir === 'up' ? upVotes : downVotes).push(voter)
              return { ...it, upVotes, downVotes }
            })
          }
        })
      )
    },
    [setShowrooms]
  )

  const commentShowroomItem = useCallback(
    (showroomId: string, productId: string, color: string, author: string, text: string) => {
      setShowrooms((prev) =>
        prev.map((s) => {
          if (s.id !== showroomId) return s
          return {
            ...s,
            items: s.items.map((it) =>
              it.productId === productId && it.color === color
                ? { ...it, comments: [...it.comments, { author, text, date: new Date().toISOString().slice(0, 10) }] }
                : it
            )
          }
        })
      )
    },
    [setShowrooms]
  )

  const assignShowroomMember = useCallback(
    (showroomId: string, memberId: string, productId?: string, color?: string) => {
      setShowrooms((prev) =>
        prev.map((s) => {
          if (s.id !== showroomId) return s
          return {
            ...s,
            members: s.members.map((m) => (m.id === memberId ? { ...m, assignedProductId: productId, assignedColor: color } : m))
          }
        })
      )
    },
    [setShowrooms]
  )

  const isInAnyShowroom = useCallback(
    (productId: string) => showrooms.some((s) => s.items.some((it) => it.productId === productId)),
    [showrooms]
  )

  const showroomWeddingDate =
    showrooms
      .map((s) => s.weddingDate)
      .filter((d) => new Date(`${d}T00:00:00`).getTime() >= Date.now() - 86400000)
      .sort()[0] ?? null

  const value: StoreState = {
    cart, wishlist, recentlyViewed, currency, language, cartOpen,
    addToCart, updateQty, removeLine, clearCart,
    toggleWishlist, isWished, trackView,
    setCurrency, setLanguage, setCartOpen,
    cartCount, cartSubtotal,
    showrooms, createShowroom, deleteShowroom, addToShowroom, removeFromShowroom,
    voteShowroomItem, commentShowroomItem, assignShowroomMember, isInAnyShowroom, showroomWeddingDate
  }

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error('useStore must be used within StoreProvider')
  return ctx
}
