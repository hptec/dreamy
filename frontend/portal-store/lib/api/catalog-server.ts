/**
 * catalog 域 — RSC 服务端取数（PAGE-CAT-S01~S05 ISR 策略）。
 * 端点：E-CAT-01 列表 / E-CAT-03 推荐位 / E-CAT-04 PDP / E-CAT-06 分类树 / E-CAT-07 标签。
 * 全部匿名公开；locale 服务端默认 en（真实工程为客户端 locale 持久化形态，RSC 缓存以 en 基准，
 * 客户端交互态请求按 Accept-Language 取 locale——与既有 identity 接入范式一致，偏离决策 27 路径前缀方案已在 traceability 标注）。
 */

import { cache } from 'react'
import { serverGet, serverGetWithStatus, type ServerResult } from './server-fetch'
import type {
  Paginated,
  RecommendationBlock,
  StoreCategoryNode,
  StoreProductCard,
  StoreProductDetail,
  StoreTagDimensionGroup
} from './store-types'

export interface StoreProductListParams {
  page?: number
  pageSize?: number
  categoryId?: number
  tagId?: number
  color?: string
  size?: string
  priceMin?: number
  priceMax?: number
  sort?: 'newest' | 'price_asc' | 'price_desc' | 'recommended'
}

/** E-CAT-01 商品列表（RSC + ISR revalidate 300） */
export function fetchStoreProducts(
  params: StoreProductListParams = {},
  revalidate = 300
): Promise<Paginated<StoreProductCard> | null> {
  return serverGet<Paginated<StoreProductCard>>('/api/store/products', {
    revalidate,
    query: { ...params }
  })
}

/** E-CAT-03 推荐位（首页/PDP 区块；空 items 调用方整段不渲染） */
export async function fetchRecommendations(
  block: RecommendationBlock,
  opts: { productId?: number; tagId?: number; limit?: number } = {},
  revalidate = 300
): Promise<StoreProductCard[]> {
  const res = await serverGet<{ items: StoreProductCard[] }>('/api/store/products/recommendations', {
    revalidate,
    query: { block, ...opts }
  })
  return res?.items ?? []
}

/**
 * E-CAT-04 PDP 详情（dynamicParams + revalidate 300；404501 → notFound）。
 * React cache 包裹：generateMetadata 与页面体共享同一次取数（每请求单飞）。
 */
export const fetchStoreProduct = cache((slug: string): Promise<ServerResult<StoreProductDetail>> => {
  return serverGetWithStatus<StoreProductDetail>(`/api/store/products/${encodeURIComponent(slug)}`, {
    revalidate: 300
  })
})

/** E-CAT-06 分类树（layout/列表页 slug→category_id 映射） */
export async function fetchStoreCategories(revalidate = 600): Promise<StoreCategoryNode[]> {
  const res = await serverGet<{ items: StoreCategoryNode[] }>('/api/store/categories', { revalidate })
  return res?.items ?? []
}

/** E-CAT-07 标签导航（Shop by Color 色板等） */
export async function fetchStoreTags(dimensionId?: number, revalidate = 300): Promise<StoreTagDimensionGroup[]> {
  const res = await serverGet<{ items: StoreTagDimensionGroup[] }>('/api/store/tags', {
    revalidate,
    query: { dimensionId }
  })
  return res?.items ?? []
}

/** 分类树扁平化查找（聚合页 slug 常量 → 分类节点） */
export function findCategoryByName(tree: StoreCategoryNode[], names: string[]): StoreCategoryNode | null {
  const lower = names.map((n) => n.toLowerCase())
  const walk = (nodes: StoreCategoryNode[]): StoreCategoryNode | null => {
    for (const n of nodes) {
      if (lower.includes(n.name.toLowerCase())) return n
      const hit = n.children ? walk(n.children) : null
      if (hit) return hit
    }
    return null
  }
  return walk(tree)
}
