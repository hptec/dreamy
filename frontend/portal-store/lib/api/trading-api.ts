/**
 * trading 域 — 购物车/地址/结算/订单/收藏/浏览历史/汇率 客户端端点（trading-frontend B.1）。
 * 全部经 client.ts 既有 401→refresh 续期重放 + snake↔camel 边界转换。
 */

import { request } from './client'
import type {
  Address,
  AddressUpsert,
  BrowseHistoryItem,
  CartItemCreate,
  CartResponse,
  CheckoutQuoteRequest,
  CheckoutQuoteResponse,
  CustomSizeData,
  ExchangeRate,
  OrderCreateRequest,
  OrderCreateResponse,
  OrderStatus,
  Paginated,
  PaymentCredential,
  StoreOrderDetail,
  StoreOrderListItem,
  StoreRefund,
  WishlistItem
} from './store-types'

// ===== cart（PAGE-TRD-S01 / STORE-TRD-S01） =====

export function getCart(): Promise<CartResponse> {
  return request<CartResponse>('/api/store/cart', { auth: true })
}

export function addCartItem(body: CartItemCreate): Promise<CartResponse> {
  return request<CartResponse>('/api/store/cart/items', { method: 'POST', auth: true, body })
}

export function updateCartItem(id: number, qty: number): Promise<CartResponse> {
  return request<CartResponse>(`/api/store/cart/items/${id}`, { method: 'PATCH', auth: true, body: { qty } })
}

export function removeCartItem(id: number): Promise<void> {
  return request<void>(`/api/store/cart/items/${id}`, { method: 'DELETE', auth: true })
}

/** 决策 8：登录后匿名购物车整批合并（anon_token 为幂等键） */
export function mergeCart(anonToken: string, items: CartItemCreate[]): Promise<CartResponse> {
  return request<CartResponse>('/api/store/cart/merge', {
    method: 'POST',
    auth: true,
    body: { anonToken, items }
  })
}

// ===== addresses（PAGE-TRD-S07） =====

export async function listAddresses(): Promise<Address[]> {
  const res = await request<{ items: Address[] }>('/api/store/addresses', { auth: true })
  return res.items
}

export function createAddress(body: AddressUpsert): Promise<Address> {
  return request<Address>('/api/store/addresses', { method: 'POST', auth: true, body })
}

export function updateAddress(id: number, body: AddressUpsert): Promise<Address> {
  return request<Address>(`/api/store/addresses/${id}`, { method: 'PUT', auth: true, body })
}

export function deleteAddress(id: number): Promise<void> {
  return request<void>(`/api/store/addresses/${id}`, { method: 'DELETE', auth: true })
}

// ===== checkout（PAGE-TRD-S02 / FORM-TRD-S02/S03） =====

export function quoteCheckout(body: CheckoutQuoteRequest): Promise<CheckoutQuoteResponse> {
  return request<CheckoutQuoteResponse>('/api/store/checkout/quote', { method: 'POST', auth: true, body })
}

export function createOrder(body: OrderCreateRequest): Promise<OrderCreateResponse> {
  return request<OrderCreateResponse>('/api/store/checkout/orders', { method: 'POST', auth: true, body })
}

// ===== orders（PAGE-TRD-S04/S05） =====

/** 订单列表（status 整数枚举筛选；「全部」= 不传 status 参数） */
export function listStoreOrders(params: { page?: number; pageSize?: number; status?: OrderStatus } = {}): Promise<Paginated<StoreOrderListItem>> {
  return request<Paginated<StoreOrderListItem>>('/api/store/orders', { auth: true, query: { ...params } })
}

export function getStoreOrder(id: number): Promise<StoreOrderDetail> {
  return request<StoreOrderDetail>(`/api/store/orders/${id}`, { auth: true })
}

export function cancelStoreOrder(id: number): Promise<StoreOrderDetail> {
  return request<StoreOrderDetail>(`/api/store/orders/${id}/cancel`, { method: 'POST', auth: true })
}

export function retryOrderPayment(id: number): Promise<PaymentCredential> {
  return request<PaymentCredential>(`/api/store/orders/${id}/payment-intent`, { method: 'POST', auth: true })
}

export function applyStoreRefund(id: number, reason: string): Promise<StoreRefund> {
  return request<StoreRefund>(`/api/store/orders/${id}/refunds`, { method: 'POST', auth: true, body: { reason } })
}

// ===== wishlist（PAGE-TRD-S06，决策 18） =====

export async function listWishlist(): Promise<WishlistItem[]> {
  const res = await request<{ items: WishlistItem[] }>('/api/store/wishlists', { auth: true })
  return res.items
}

export function addWishlistItem(productId: number): Promise<WishlistItem> {
  return request<WishlistItem>('/api/store/wishlists', { method: 'POST', auth: true, body: { productId } })
}

export function removeWishlistItem(productId: number): Promise<void> {
  return request<void>(`/api/store/wishlists/${productId}`, { method: 'DELETE', auth: true })
}

export function moveWishlistToCart(
  productId: number,
  body: { skuId?: number; qty?: number; customSizeData?: CustomSizeData }
): Promise<CartResponse> {
  return request<CartResponse>(`/api/store/wishlists/${productId}/move-to-cart`, {
    method: 'POST',
    auth: true,
    body
  })
}

// ===== browse history（决策 23 / FORM-TRD-S07） =====

export async function listBrowseHistory(limit = 20): Promise<BrowseHistoryItem[]> {
  const res = await request<{ items: BrowseHistoryItem[] }>('/api/store/browse-history', {
    auth: true,
    query: { limit }
  })
  return res.items
}

/** PDP 登录态浏览上报（fire-and-forget，失败静默） */
export function recordBrowseHistory(productId: number): Promise<void> {
  return request<void>('/api/store/browse-history', { method: 'POST', auth: true, body: { productId } })
}

// ===== exchange rates（决策 14 / STORE-TRD-S04） =====

export async function listStoreExchangeRates(): Promise<ExchangeRate[]> {
  const res = await request<{ items: ExchangeRate[] }>('/api/store/exchange-rates')
  return res.items
}
