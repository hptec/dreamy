/**
 * marketing 域 — 内容/促销 取数与表单端点。
 * 服务端（RSC，直连后端无缓存）：banners/blogs/weddings/lookbooks/guides/flash-sales。
 * 客户端：validateCoupon（StoreBearerAuth）/ subscribeNewsletter / submitContact（匿名）。
 */

import { cache } from 'react'
import { serverGet, serverGetWithStatus, type ServerResult } from './server-fetch'
import type {
  BannerPosition,
  Paginated,
  StoreBanner,
  StoreBlogPostCard,
  StoreBlogPostDetail,
  StoreFlashSale,
  StoreGuide,
  StoreLookbook,
  StoreRealWedding
} from './store-types'

// ===== 服务端（RSC） =====

/** E-MKT-01 在线 Banner（position=BannerPosition 整数枚举） */
export async function fetchStoreBanners(position: BannerPosition): Promise<StoreBanner[]> {
  const res = await serverGet<{ items: StoreBanner[] }>('/api/store/content/banners', {
    query: { position }
  })
  return res?.items ?? []
}

/** E-MKT-02 博客列表 */
export function fetchStoreBlogs(
  params: { category?: string; page?: number; pageSize?: number } = {}
): Promise<Paginated<StoreBlogPostCard> | null> {
  return serverGet<Paginated<StoreBlogPostCard>>('/api/store/content/blogs', {
    query: { ...params }
  })
}

/** E-MKT-03 博客详情（404701 → notFound；React cache 每请求单飞） */
export const fetchStoreBlog = cache((slug: string): Promise<ServerResult<StoreBlogPostDetail>> => {
  return serverGetWithStatus<StoreBlogPostDetail>(
    `/api/store/content/blogs/${encodeURIComponent(slug)}`
  )
})

/** E-MKT-04 真实婚礼列表 */
export function fetchStoreWeddings(
  params: { page?: number; pageSize?: number } = {}
): Promise<Paginated<StoreRealWedding> | null> {
  return serverGet<Paginated<StoreRealWedding>>('/api/store/content/weddings', {
    query: { ...params }
  })
}

/** E-MKT-05 真实婚礼详情（React cache 每请求单飞） */
export const fetchStoreWedding = cache((id: number): Promise<ServerResult<StoreRealWedding>> => {
  return serverGetWithStatus<StoreRealWedding>(`/api/store/content/weddings/${id}`)
})

/** E-MKT-06 Lookbook 列表 */
export async function fetchStoreLookbooks(): Promise<StoreLookbook[]> {
  const res = await serverGet<{ items: StoreLookbook[] }>('/api/store/content/lookbooks')
  return res?.items ?? []
}

/** E-MKT-08 备婚指南列表 */
export async function fetchStoreGuides(): Promise<StoreGuide[]> {
  const res = await serverGet<{ items: StoreGuide[] }>('/api/store/content/guides')
  return res?.items ?? []
}

/** E-MKT-09 进行中闪购（空 items 整段不渲染） */
export async function fetchStoreFlashSales(): Promise<StoreFlashSale[]> {
  const res = await serverGet<{ items: StoreFlashSale[] }>('/api/store/promotions/flash-sales')
  return res?.items ?? []
}
