'use client'

/**
 * wishlistStore（STORE-TRD-S03，决策 18 仅登录）：ids 集合 + items。
 * - add 幂等（201/200 同处理）；toggle 乐观更新失败回滚（FORM-TRD-S06）。
 * - 未登录由调用方引导 /account/login?returnTo=（不发请求）。
 */

import { create } from 'zustand'
import * as tradingApi from '../api/trading-api'
import type { WishlistItem } from '../api/store-types'
import { useAuthStore } from './auth-store'

interface WishlistState {
  ids: number[]
  items: WishlistItem[]
  loading: boolean
  fetched: boolean
  fetch: () => Promise<void>
  /** 乐观 toggle；返回 false 表示未登录（调用方引导登录） */
  toggle: (productId: number) => Promise<boolean>
  removeByProduct: (productId: number) => Promise<void>
  reset: () => void
}

export const useWishlistStore = create<WishlistState>((set, get) => ({
  ids: [],
  items: [],
  loading: false,
  fetched: false,

  fetch: async () => {
    if (!useAuthStore.getState().isAuthenticated) return
    set({ loading: true })
    try {
      const items = await tradingApi.listWishlist()
      set({ items, ids: items.map((i) => i.productId), fetched: true })
    } finally {
      set({ loading: false })
    }
  },

  toggle: async (productId) => {
    if (!useAuthStore.getState().isAuthenticated) return false
    const { ids } = get()
    const wished = ids.includes(productId)
    // 乐观更新
    set({ ids: wished ? ids.filter((id) => id !== productId) : [...ids, productId] })
    try {
      if (wished) {
        await tradingApi.removeWishlistItem(productId)
        set({ items: get().items.filter((i) => i.productId !== productId) })
      } else {
        const item = await tradingApi.addWishlistItem(productId)
        set({ items: [item, ...get().items.filter((i) => i.productId !== productId)] })
      }
    } catch {
      // 失败回滚
      set({ ids })
    }
    return true
  },

  removeByProduct: async (productId) => {
    await tradingApi.removeWishlistItem(productId)
    set({
      ids: get().ids.filter((id) => id !== productId),
      items: get().items.filter((i) => i.productId !== productId)
    })
  },

  reset: () => set({ ids: [], items: [], fetched: false })
}))
