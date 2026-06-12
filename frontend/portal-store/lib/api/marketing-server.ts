/**
 * marketing 域 — 内容/促销 取数与表单端点。
 * 服务端（RSC + ISR）：banners/blogs/weddings/lookbooks/guides/flash-sales。
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

/** E-MKT-01 在线 Banner（position=BannerPosition 整数枚举；PAGE-MKT-S01/S02） */
export async function fetchStoreBanners(
  position: BannerPosition,
  revalidate = 300
): Promise<StoreBanner[]> {
  const res = await serverGet<{ items: StoreBanner[] }>('/api/store/content/banners', {
    revalidate,
    query: { position }
  })
  return res?.items ?? []
}

/** E-MKT-02 博客列表（PAGE-MKT-S03，?page= 驱动） */
export function fetchStoreBlogs(
  params: { category?: string; page?: number; pageSize?: number } = {},
  revalidate = 300
): Promise<Paginated<StoreBlogPostCard> | null> {
  return serverGet<Paginated<StoreBlogPostCard>>('/api/store/content/blogs', {
    revalidate,
    query: { ...params }
  })
}

/** E-MKT-03 博客详情（PAGE-MKT-S04，404701 → notFound；React cache 每请求单飞） */
export const fetchStoreBlog = cache((slug: string): Promise<ServerResult<StoreBlogPostDetail>> => {
  return serverGetWithStatus<StoreBlogPostDetail>(
    `/api/store/content/blogs/${encodeURIComponent(slug)}`,
    { revalidate: 300 }
  )
})

/** E-MKT-04 真实婚礼列表（PAGE-MKT-S05 + 首页区块） */
export function fetchStoreWeddings(
  params: { page?: number; pageSize?: number } = {},
  revalidate = 300
): Promise<Paginated<StoreRealWedding> | null> {
  return serverGet<Paginated<StoreRealWedding>>('/api/store/content/weddings', {
    revalidate,
    query: { ...params }
  })
}

/** E-MKT-05 真实婚礼详情（PAGE-MKT-S06，路由参数值=数字 id；React cache 每请求单飞） */
export const fetchStoreWedding = cache((id: number): Promise<ServerResult<StoreRealWedding>> => {
  return serverGetWithStatus<StoreRealWedding>(`/api/store/content/weddings/${id}`, { revalidate: 300 })
})

/** E-MKT-06 Lookbook 列表（PAGE-MKT-S07） */
export async function fetchStoreLookbooks(revalidate = 300): Promise<StoreLookbook[]> {
  const res = await serverGet<{ items: StoreLookbook[] }>('/api/store/content/lookbooks', { revalidate })
  return res?.items ?? []
}

/** E-MKT-08 备婚指南列表（PAGE-MKT-S08） */
export async function fetchStoreGuides(revalidate = 300): Promise<StoreGuide[]> {
  const res = await serverGet<{ items: StoreGuide[] }>('/api/store/content/guides', { revalidate })
  return res?.items ?? []
}

/** E-MKT-09 进行中闪购（PAGE-MKT-S01 FlashSaleRail，空 items 整段不渲染） */
export async function fetchStoreFlashSales(revalidate = 60): Promise<StoreFlashSale[]> {
  const res = await serverGet<{ items: StoreFlashSale[] }>('/api/store/promotions/flash-sales', { revalidate })
  return res?.items ?? []
}
