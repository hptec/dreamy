'use client'

/**
 * StoreProvider —— 全局商店上下文适配层（STORE-TRD 系列）。
 * 原 mock 内存态（cart/wishlist/recentlyViewed）迁移为 API 驱动的 zustand store 适配：
 * - cart：双态模型（决策 8）→ lib/stores/cart-store
 * - wishlist：登录态 API（决策 18）→ lib/stores/wishlist-store
 * - recentlyViewed：浏览历史上报（决策 23）→ recordBrowseHistory（fire-and-forget）
 * - currency：展示换算汇率 API 化（决策 14）→ listStoreExchangeRates → setDisplayRates
 * - 登录成功钩子：mergeCart（决策 8）+ wishlist 拉取 + showroom guest 会话绑定回填（FORM-SHR-S08）
 */

import { createContext, useContext, useEffect, useRef, useState, useCallback, type ReactNode } from 'react'
import type { Currency } from '@/data/types'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useCartStore, cartCount as countOf, cartSubtotalUsd, type AddCartInput, type CartLineVM } from '@/lib/stores/cart-store'
import { useWishlistStore } from '@/lib/stores/wishlist-store'
import { rejoinAllGuestSessionsAfterLogin } from '@/lib/stores/guest-session-store'
import { listStoreExchangeRates, recordBrowseHistory } from '@/lib/api/trading-api'
import { setDisplayRates } from '@/lib/utils'
import { trackAddToCart } from '@/lib/analytics/gtag'

interface StoreState {
  cart: CartLineVM[]
  wishlist: number[]
  currency: Currency
  language: string
  cartOpen: boolean
  dyeLotProductIds: number[]
  truncatedItemIds: number[]
  addToCart: (input: AddCartInput) => Promise<void>
  updateQty: (key: string, qty: number) => Promise<void>
  removeLine: (key: string) => Promise<void>
  /** 返回 false = 未登录（调用方引导 /account/login，决策 18） */
  toggleWishlist: (productId: number) => Promise<boolean>
  isWished: (productId: number) => boolean
  /** PDP 浏览上报（登录态 fire-and-forget，决策 23） */
  trackView: (productId: number) => void
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
  const [currency, setCurrency] = usePersisted<Currency>('dreamy_currency', 'USD')
  const [language, setLanguage] = usePersisted<string>('dreamy_lang', 'EN')

  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const authHydrated = useAuthStore((s) => s.hydrated)

  const cartLines = useCartStore((s) => s.lines)
  const cartOpen = useCartStore((s) => s.cartOpen)
  const setCartOpen = useCartStore((s) => s.setCartOpen)
  const dyeLotProductIds = useCartStore((s) => s.dyeLotProductIds)
  const truncatedItemIds = useCartStore((s) => s.truncatedItemIds)
  const addToCartRaw = useCartStore((s) => s.add)
  const updateQty = useCartStore((s) => s.updateQty)
  const removeLine = useCartStore((s) => s.remove)
  const hydrateCart = useCartStore((s) => s.hydrate)
  const mergeAfterLogin = useCartStore((s) => s.mergeAfterLogin)

  const wishlistIds = useWishlistStore((s) => s.ids)
  const toggleWishlist = useWishlistStore((s) => s.toggle)
  const fetchWishlist = useWishlistStore((s) => s.fetch)
  const resetWishlist = useWishlistStore((s) => s.reset)

  // 汇率展示换算（决策 14）：启动拉取覆盖静态兜底
  useEffect(() => {
    listStoreExchangeRates()
      .then((items) => {
        setDisplayRates(Object.fromEntries(items.map((r) => [r.currency, r.rate])))
      })
      .catch(() => {
        /* 静态兜底（lib/utils liveRates 缺省值） */
      })
  }, [])

  // 登录态切换钩子（决策 8 / 决策 18 / FORM-SHR-S08）
  const prevAuth = useRef<boolean | null>(null)
  useEffect(() => {
    if (!authHydrated) return
    const prev = prevAuth.current
    prevAuth.current = isAuthenticated
    if (prev === null) {
      // 首次：按当前态装载
      void hydrateCart()
      if (isAuthenticated) void fetchWishlist()
      return
    }
    if (!prev && isAuthenticated) {
      void mergeAfterLogin()
      void fetchWishlist()
      void rejoinAllGuestSessionsAfterLogin()
    }
    if (prev && !isAuthenticated) {
      resetWishlist()
      void hydrateCart()
    }
  }, [authHydrated, isAuthenticated, hydrateCart, mergeAfterLogin, fetchWishlist, resetWishlist])

  const isWished = useCallback((productId: number) => wishlistIds.includes(productId), [wishlistIds])

  // GA4 add_to_cart（决策 19）：集中承载 buy-box / quick-view 两个加购入口，成功后上报
  const addToCart = useCallback(
    async (input: AddCartInput) => {
      await addToCartRaw(input)
      trackAddToCart({
        currency: 'USD',
        value: input.snapshot.priceUsd * input.qty,
        items: [
          {
            item_id: String(input.productId),
            item_name: input.snapshot.name,
            price: input.snapshot.priceUsd,
            quantity: input.qty,
            item_variant:
              [input.snapshot.color, input.snapshot.size].filter(Boolean).join(' / ') || undefined
          }
        ]
      })
    },
    [addToCartRaw]
  )

  const trackView = useCallback((productId: number) => {
    if (!useAuthStore.getState().isAuthenticated) return
    recordBrowseHistory(productId).catch(() => {
      /* fire-and-forget（FORM-TRD-S07） */
    })
  }, [])

  const value: StoreState = {
    cart: cartLines,
    wishlist: wishlistIds,
    currency,
    language,
    cartOpen,
    dyeLotProductIds,
    truncatedItemIds,
    addToCart,
    updateQty,
    removeLine,
    toggleWishlist,
    isWished,
    trackView,
    setCurrency,
    setLanguage,
    setCartOpen,
    cartCount: countOf(cartLines),
    cartSubtotal: cartSubtotalUsd(cartLines)
  }

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error('useStore must be used within StoreProvider')
  return ctx
}
