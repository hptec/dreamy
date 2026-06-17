/**
 * catalog 域 — RSC 服务端取数（直连后端，无缓存）。
 * 端点：E-CAT-01 列表 / E-CAT-03 推荐位 / E-CAT-04 PDP / E-CAT-06 分类树 / E-CAT-07 集合。
 * 全部匿名公开；locale 服务端默认 en。
 */

import { cache } from 'react'
import { serverGet, serverGetWithStatus, type ServerResult } from './server-fetch'
import type {
  Paginated,
  RecommendationBlock,
  StoreCategoryNode,
  StoreCollectionGroup,
  StoreFilterDim,
  StoreProductCard,
  StoreProductDetail,
} from './store-types'

export interface StoreProductListParams {
  page?: number
  pageSize?: number
  categoryId?: number
  collectionId?: number
  color?: string
  size?: string
  priceMin?: number
  priceMax?: number
  sort?: 'newest' | 'price_asc' | 'price_desc' | 'recommended'
  /** 动态属性筛选（每项 "key:value"，重复 attr 参数；同 key 多值 OR、跨 key AND） */
  attrs?: string[]
}

/** E-CAT-01 商品列表 */
export function fetchStoreProducts(
  params: StoreProductListParams = {}
): Promise<Paginated<StoreProductCard> | null> {
  const { attrs, ...rest } = params
  return serverGet<Paginated<StoreProductCard>>('/api/store/products', {
    query: { ...rest, attr: attrs }
  })
}

/** E-CAT-27 分类动态属性筛选维度（PLP 筛选组数据源） */
export async function fetchStoreProductFilters(
  categoryId: number | undefined
): Promise<StoreFilterDim[]> {
  if (categoryId == null) return []
  const res = await serverGet<{ items: StoreFilterDim[] }>('/api/store/products/filters', {
    query: { categoryId }
  })
  return res?.items ?? []
}

/** E-CAT-03 推荐位（首页/PDP 区块；空 items 调用方整段不渲染） */
export async function fetchRecommendations(
  block: RecommendationBlock,
  opts: { productId?: number; collectionId?: number; limit?: number } = {}
): Promise<StoreProductCard[]> {
  const res = await serverGet<{ items: StoreProductCard[] }>('/api/store/products/recommendations', {
    query: { block, ...opts }
  })
  return res?.items ?? []
}

/**
 * E-CAT-04 PDP 详情（404501 → notFound）。
 * React cache 包裹：generateMetadata 与页面体共享同一次取数（每请求单飞）。
 */
export const fetchStoreProduct = cache((slug: string): Promise<ServerResult<StoreProductDetail>> => {
  return serverGetWithStatus<StoreProductDetail>(`/api/store/products/${encodeURIComponent(slug)}`)
})

/** E-CAT-06 分类树（layout/列表页 slug→category_id 映射） */
export async function fetchStoreCategories(): Promise<StoreCategoryNode[]> {
  const res = await serverGet<{ items: StoreCategoryNode[] }>('/api/store/categories')
  return res?.items ?? []
}

/** E-CAT-07 集合导航（Shop by Color 色板等） */
export async function fetchStoreCollections(groupId?: number): Promise<StoreCollectionGroup[]> {
  const res = await serverGet<{ items: StoreCollectionGroup[] }>('/api/store/collections', {
    query: { groupId }
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
