/**
 * 聚合页 RSC 装配（PAGE-CAT-S02）：
 * 分类 slug 常量 → fetchStoreCategories 映射 category_id（同 ISR 周期）；
 * E-CAT-01 列表（searchParams 透传：color/size/price/sort/cat/page）+ E-CAT-07 色板标签（颜色 facet 数据源）。
 */

import { fetchStoreCategories, fetchStoreProductFilters, fetchStoreProducts, fetchStoreTags, findCategoryByName } from '@/lib/api/catalog-server'
import { CollectionView } from '@/components/product/collection-view'
import { parsePriceParam } from '@/components/product/product-utils'

export interface CollectionSearchParams {
  [key: string]: string | string[] | undefined
}

function single(v: string | string[] | undefined): string | undefined {
  return Array.isArray(v) ? v[0] : v
}

/** a_<key>=v1|v2 searchParams → attr 重复参数（"key:value" 每值一项；值逐项 decodeURIComponent） */
function parseAttrParams(searchParams: CollectionSearchParams): string[] {
  const attrs: string[] = []
  for (const [k, raw] of Object.entries(searchParams)) {
    if (!k.startsWith('a_')) continue
    const key = k.slice(2)
    const v = single(raw)
    if (!key || !v) continue
    for (const value of v.split('|').filter(Boolean)) {
      attrs.push(`${key}:${decodeURIComponent(value)}`)
    }
  }
  return attrs
}

const FALLBACK_COLORS = ['Sage', 'Dusty Blue', 'Blush', 'Champagne', 'Lavender', 'Terracotta', 'Ivory', 'Espresso']

export async function CollectionPage({
  categoryNames,
  title,
  description,
  heroImage,
  basePath,
  searchParams
}: {
  /** 分类名候选（与后台分类树对齐，命中即取其 id；含子分类商品） */
  categoryNames: string[]
  title: string
  description?: string
  heroImage?: string
  basePath: string
  searchParams: CollectionSearchParams
}) {
  const [tree, tagGroups] = await Promise.all([fetchStoreCategories(), fetchStoreTags()])
  const category = findCategoryByName(tree, categoryNames)

  const cat = single(searchParams.cat)
  const sort = single(searchParams.sort) as 'newest' | 'price_asc' | 'price_desc' | 'recommended' | undefined
  const page = Math.max(1, Number(single(searchParams.page) ?? '1') || 1)
  const { priceMin, priceMax } = parsePriceParam(single(searchParams.price))

  const categoryId = cat ? Number(cat) : category?.id
  const attrs = parseAttrParams(searchParams)

  const [data, filterDims] = await Promise.all([
    fetchStoreProducts({
      categoryId,
      color: single(searchParams.color),
      size: single(searchParams.size),
      priceMin,
      priceMax,
      sort: sort ?? 'recommended',
      page,
      pageSize: 12,
      attrs
    }),
    // E-CAT-27 动态属性筛选维度（子分类选中时按子分类解析 overrides）
    fetchStoreProductFilters(categoryId)
  ])

  // 颜色 facet：色板维度标签（E-CAT-07）；空回退静态色板名（冷启动安全）
  const colorGroup = tagGroups.find((g) => /color/i.test(g.name)) ?? tagGroups[0]
  const colorOptions = colorGroup?.tags.map((t) => t.name) ?? []

  const subTabs = (category?.children ?? []).map((c) => ({ label: c.name, value: String(c.id) }))

  return (
    <CollectionView
      title={title}
      description={description}
      heroImage={heroImage}
      data={data}
      colorOptions={colorOptions.length > 0 ? colorOptions : FALLBACK_COLORS}
      filterDims={filterDims}
      subTabs={subTabs.length > 0 ? subTabs : undefined}
      basePath={basePath}
    />
  )
}
