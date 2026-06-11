'use client'

/**
 * cartStore（STORE-TRD-S01，决策 8 双态模型）：
 * - 匿名态：items 持久化 localStorage（dreamy.cart.anon + dreamy.cart.anon_token 前端 UUID）。
 * - 登录态：以服务端 CartResponse 为真值（本地仅缓存渲染）。
 * - 登录成功钩子 → mergeCart(anonToken, localItems) 一次性合并 → 清 localStorage → 以响应覆盖
 *   （mergedTruncatedItemIds 行内提示「已按库存调整数量」）。
 * - dyeLotProductIds 透出供购物车/结算提示条（决策 20.4）。
 */

import { create } from 'zustand'
import * as tradingApi from '../api/trading-api'
import type { CartItemCreate, CartResponse, CustomSizeData } from '../api/store-types'
import { useAuthStore } from './auth-store'

const ANON_KEY = 'dreamy.cart.anon'
const ANON_TOKEN_KEY = 'dreamy.cart.anon_token'

export interface CartLineVM {
  /** 渲染键：服务端 `s-{id}` / 匿名 `a-{uuid}` */
  key: string
  serverId?: number
  productId: number
  skuId?: number
  slug: string
  name: string
  image?: string
  priceUsd: number
  multiCurrencyPrices?: Record<string, number> | null
  color?: string
  size?: string
  qty: number
  /** 现货可售库存（qty 超限行内提示 + stepper 上限） */
  stock?: number
  /** 下架商品（置灰 No longer available） */
  unavailable?: boolean
  customSizeData?: CustomSizeData | null
}

export interface AddCartInput {
  productId: number
  skuId?: number
  qty: number
  customSizeData?: CustomSizeData
  /** 匿名态本地渲染快照 */
  snapshot: {
    slug: string
    name: string
    image?: string
    priceUsd: number
    multiCurrencyPrices?: Record<string, number> | null
    color?: string
    size?: string
    stock?: number
  }
}

interface AnonLine {
  localId: string
  productId: number
  skuId?: number
  qty: number
  customSizeData?: CustomSizeData
  snapshot: AddCartInput['snapshot']
}

function isBrowser() {
  return typeof window !== 'undefined'
}

function uuid(): string {
  if (isBrowser() && typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function readAnonLines(): AnonLine[] {
  if (!isBrowser()) return []
  try {
    return JSON.parse(localStorage.getItem(ANON_KEY) ?? '[]') as AnonLine[]
  } catch {
    return []
  }
}

function writeAnonLines(lines: AnonLine[]) {
  if (!isBrowser()) return
  try {
    localStorage.setItem(ANON_KEY, JSON.stringify(lines))
  } catch {
    /* ignore */
  }
}

export function getAnonToken(): string {
  if (!isBrowser()) return 'ssr'
  try {
    let token = localStorage.getItem(ANON_TOKEN_KEY)
    if (!token) {
      token = uuid()
      localStorage.setItem(ANON_TOKEN_KEY, token)
    }
    return token
  } catch {
    return 'fallback'
  }
}

function anonToVM(line: AnonLine): CartLineVM {
  return {
    key: `a-${line.localId}`,
    productId: line.productId,
    skuId: line.skuId,
    qty: line.qty,
    customSizeData: line.customSizeData,
    slug: line.snapshot.slug,
    name: line.snapshot.name,
    image: line.snapshot.image,
    priceUsd: line.snapshot.priceUsd,
    multiCurrencyPrices: line.snapshot.multiCurrencyPrices,
    color: line.snapshot.color,
    size: line.snapshot.size,
    stock: line.snapshot.stock
  }
}

function serverToVM(res: CartResponse): CartLineVM[] {
  return res.items.map((it) => ({
    key: `s-${it.id}`,
    serverId: it.id,
    productId: it.productId,
    skuId: it.skuId ?? undefined,
    qty: it.qty,
    customSizeData: it.customSizeData,
    slug: it.product.slug,
    name: it.product.name,
    image: it.product.imageUrl,
    priceUsd: it.product.price,
    multiCurrencyPrices: it.product.multiCurrencyPrices,
    color: it.sku?.color ?? (it.customSizeData ? undefined : undefined),
    size: it.sku?.size ?? (it.customSizeData ? 'Custom' : undefined),
    stock: it.sku?.stock,
    unavailable: it.product.status === 'draft'
  }))
}

interface CartState {
  lines: CartLineVM[]
  dyeLotProductIds: number[]
  truncatedItemIds: number[]
  cartOpen: boolean
  loading: boolean
  hydrated: boolean
  setCartOpen: (open: boolean) => void
  /** 启动装载：登录态拉服务端，匿名态读 localStorage */
  hydrate: () => Promise<void>
  add: (input: AddCartInput) => Promise<void>
  updateQty: (key: string, qty: number) => Promise<void>
  remove: (key: string) => Promise<void>
  clearLocal: () => void
  /** 登录成功钩子（决策 8）：合并匿名购物车 → 服务端真值 */
  mergeAfterLogin: () => Promise<void>
  refresh: () => Promise<void>
}

export const useCartStore = create<CartState>((set, get) => ({
  lines: [],
  dyeLotProductIds: [],
  truncatedItemIds: [],
  cartOpen: false,
  loading: false,
  hydrated: false,

  setCartOpen: (open) => set({ cartOpen: open }),

  hydrate: async () => {
    if (useAuthStore.getState().isAuthenticated) {
      await get().refresh()
      set({ hydrated: true })
    } else {
      set({ lines: readAnonLines().map(anonToVM), hydrated: true })
    }
  },

  refresh: async () => {
    if (!useAuthStore.getState().isAuthenticated) {
      set({ lines: readAnonLines().map(anonToVM) })
      return
    }
    set({ loading: true })
    try {
      const res = await tradingApi.getCart()
      set({ lines: serverToVM(res), dyeLotProductIds: res.dyeLotProductIds ?? [] })
    } finally {
      set({ loading: false })
    }
  },

  add: async (input) => {
    if (useAuthStore.getState().isAuthenticated) {
      const res = await tradingApi.addCartItem({
        productId: input.productId,
        skuId: input.skuId,
        qty: input.qty,
        customSizeData: input.customSizeData
      })
      set({ lines: serverToVM(res), dyeLotProductIds: res.dyeLotProductIds ?? [], cartOpen: true })
      return
    }
    const lines = readAnonLines()
    const idx = lines.findIndex(
      (l) =>
        l.productId === input.productId &&
        l.skuId === input.skuId &&
        JSON.stringify(l.customSizeData ?? null) === JSON.stringify(input.customSizeData ?? null)
    )
    if (idx >= 0) lines[idx] = { ...lines[idx], qty: lines[idx].qty + input.qty }
    else lines.push({ localId: uuid(), productId: input.productId, skuId: input.skuId, qty: input.qty, customSizeData: input.customSizeData, snapshot: input.snapshot })
    writeAnonLines(lines)
    set({ lines: lines.map(anonToVM), cartOpen: true })
  },

  updateQty: async (key, qty) => {
    const next = Math.max(1, qty)
    if (key.startsWith('s-')) {
      const id = Number(key.slice(2))
      const res = await tradingApi.updateCartItem(id, next)
      set({ lines: serverToVM(res), dyeLotProductIds: res.dyeLotProductIds ?? [] })
      return
    }
    const localId = key.slice(2)
    const lines = readAnonLines().map((l) => (l.localId === localId ? { ...l, qty: next } : l))
    writeAnonLines(lines)
    set({ lines: lines.map(anonToVM) })
  },

  remove: async (key) => {
    if (key.startsWith('s-')) {
      const id = Number(key.slice(2))
      await tradingApi.removeCartItem(id)
      set({ lines: get().lines.filter((l) => l.key !== key) })
      return
    }
    const localId = key.slice(2)
    const lines = readAnonLines().filter((l) => l.localId !== localId)
    writeAnonLines(lines)
    set({ lines: lines.map(anonToVM) })
  },

  clearLocal: () => {
    writeAnonLines([])
    set({ lines: [] })
  },

  mergeAfterLogin: async () => {
    const anonLines = readAnonLines()
    try {
      if (anonLines.length > 0) {
        const items: CartItemCreate[] = anonLines.map((l) => ({
          productId: l.productId,
          skuId: l.skuId,
          qty: l.qty,
          customSizeData: l.customSizeData
        }))
        const res = await tradingApi.mergeCart(getAnonToken(), items)
        writeAnonLines([])
        if (isBrowser()) {
          try {
            localStorage.removeItem(ANON_TOKEN_KEY)
          } catch {
            /* ignore */
          }
        }
        set({
          lines: serverToVM(res),
          dyeLotProductIds: res.dyeLotProductIds ?? [],
          truncatedItemIds: res.mergedTruncatedItemIds ?? []
        })
      } else {
        await get().refresh()
      }
    } catch {
      // 合并失败保留本地条目（下次登录重试，anon_token 幂等）
      set({ lines: anonLines.map(anonToVM) })
    }
  }
}))

export function cartCount(lines: CartLineVM[]): number {
  return lines.reduce((sum, l) => sum + l.qty, 0)
}

export function cartSubtotalUsd(lines: CartLineVM[]): number {
  return lines.reduce((sum, l) => sum + (l.unavailable ? 0 : l.priceUsd * l.qty), 0)
}
