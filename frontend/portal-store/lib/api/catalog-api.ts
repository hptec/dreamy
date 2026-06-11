/**
 * catalog 域 — 客户端端点（lib/api/catalog-api.ts，B.1）。
 * searchStoreProducts：E-CAT-02 全文搜索（客户端 fetch 不缓存，后端 JetCache 60s）。
 * recommendSize：E-CAT-05 Find My Size 纯函数端点（FORM-CAT-S01）。
 */

import { request } from './client'
import type {
  Paginated,
  SizeRecommendationRequest,
  SizeRecommendationResponse,
  StoreProductCard,
  StoreProductDetail
} from './store-types'

/** E-CAT-02 商品全文搜索（PAGE-CAT-S03） */
export function searchStoreProducts(
  q: string,
  page = 1,
  pageSize = 24
): Promise<Paginated<StoreProductCard>> {
  return request<Paginated<StoreProductCard>>('/api/store/products/search', {
    query: { q, page, pageSize }
  })
}

/** E-CAT-04 PDP 详情（客户端场景：QuickView / Move to bag SKU 选择） */
export function getStoreProductClient(slug: string): Promise<StoreProductDetail> {
  return request<StoreProductDetail>(`/api/store/products/${encodeURIComponent(slug)}`)
}

/** E-CAT-05 尺码推荐（COMP-CAT-S04 FindMySizeModal） */
export function recommendSize(
  productId: number,
  answers: SizeRecommendationRequest
): Promise<SizeRecommendationResponse> {
  return request<SizeRecommendationResponse>(`/api/store/products/${productId}/size-recommendation`, {
    method: 'POST',
    body: answers
  })
}
