/**
 * 聚合页 RSC 装配（PAGE-CAT-S02）：
 * 分类 slug 常量 → fetchStoreCategories 映射 category_id（同 ISR 周期）；
 * E-CAT-01 列表（searchParams 透传：color/size/price/sort/cat/page）+ E-CAT-07 色板标签（颜色 facet 数据源）。
 */

import { fetchStoreCategories, fetchStoreProductFilters, fetchStoreProducts, fetchStoreCollections, findCategoryByName } from '@/lib/api/catalog-server'
import type { Paginated, StoreProductCard } from '@/lib/api/store-types'
import { CollectionView } from '@/components/product/collection-view'
import { parsePriceParam } from '@/components/product/product-utils'
import { resolveCollectionHero, type CollectionHero } from '@/lib/collection-hero'

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

/** 目录里不存在的 cat → 空结果页（非 null：null 会被 CollectionView 视为加载失败） */
const EMPTY_PAGE: Paginated<StoreProductCard> = { data: [], totalElements: 0, pageNumber: 1, pageSize: 12, numberOfElements: 0, totalPages: 1 }

export async function CollectionPage({
  categoryNames,
  hero,
  basePath,
  searchParams
}: {
  /** 分类名候选（与后台分类树对齐，命中即取其 id；含子分类商品） */
  categoryNames: string[]
  /** 页面默认 hero；/products 聚合页会按 cat 命中的顶级分类切换 */
  hero: CollectionHero
  basePath: string
  searchParams: CollectionSearchParams
}) {
  const [tree, collectionGroups] = await Promise.all([fetchStoreCategories(), fetchStoreCollections()])
  const category = findCategoryByName(tree, categoryNames)

  const cat = single(searchParams.cat)
  const collection = Number(single(searchParams.collection))
  const collectionId = Number.isInteger(collection) && collection > 0 ? collection : undefined
  const requestedSort = single(searchParams.sort)
  // Site-builder's seeded footer links predate the catalog API enum.
  const sort = ({
    new: 'newest',
    best: 'recommended'
  } as Record<string, 'newest' | 'price_asc' | 'price_desc' | 'recommended'>)[requestedSort ?? '']
    ?? requestedSort as 'newest' | 'price_asc' | 'price_desc' | 'recommended' | undefined
  const page = Math.max(1, Number(single(searchParams.page) ?? '1') || 1)
  const { priceMin, priceMax } = parsePriceParam(single(searchParams.price))

  // cat 兼容数字 id 与分类 name（slug）；先父分类子级，再全树（/products 等无父分类聚合页）。
  // cat 给了但目录里不存在（如旧链接 ?cat=Shoes）→ 不再静默回退父分类：那样会把「Shoes」标题下
  // 塞满无关商品，比空页更误导。此时按空结果渲染（noMatch 空态 + 清除筛选），并保留页面 hero。
  const catNumeric = cat ? Number(cat) : NaN
  const catByName = Number.isNaN(catNumeric) && cat
    ? (category?.children?.find((c) => c.name === cat)?.id ?? findCategoryByName(tree, [cat])?.id)
    : undefined
  const catResolved = Number.isNaN(catNumeric) ? catByName : catNumeric
  const unknownCat = !!cat && catResolved === undefined
  const categoryId = catResolved ?? category?.id
  const attrs = parseAttrParams(searchParams)

  const [data, filterDims] = await Promise.all([
    unknownCat
      ? Promise.resolve(EMPTY_PAGE)
      : fetchStoreProducts({
          categoryId,
          collectionId,
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
    fetchStoreProductFilters(unknownCat ? category?.id : categoryId)
  ])

  // 颜色 facet：色板集合分组（E-CAT-07）；空回退静态色板名（冷启动安全）
  const colorGroup = collectionGroups.find((g) => /color/i.test(g.name)) ?? collectionGroups[0]
  const colorOptions = colorGroup?.collections.map((c) => c.name) ?? []
  // 色板名 → 集合 id(色系筛选按集合挂载过滤,与后端 SKU 单色精确匹配解耦)
  const colorCollectionMap = Object.fromEntries((colorGroup?.collections ?? []).map((c) => [c.name, c.id]))

  // 主导航 CATEGORY 条目落到 /products?cat=<顶级分类|子分类>：hero/标题按命中的顶级分类切换
  const topLevel = category ?? tree.find((c) => c.name === cat || String(c.id) === cat || c.children?.some((ch) => ch.name === cat || String(ch.id) === cat))
  const activeHero = resolveCollectionHero(topLevel?.name) ?? hero
  // 子分类 tab：有父分类的路由用其子级；/products 聚合路由用 cat 命中的顶级分类子级（否则主导航进来看不到子分类）
  const tabSource = category ?? topLevel
  const subTabs = (tabSource?.children ?? []).map((c) => ({ label: c.name, value: c.name }))
  // /products 聚合路由的「All」tab 要写回顶级分类名，不能把 cat 清空落到全站
  const subTabsAllValue = category ? '' : (topLevel?.name ?? '')
  // cat 命中子分类时标题用子分类名（Beach & Destination），hero 图/文案沿用顶级分类
  const leaf = cat ? topLevel?.children?.find((ch) => ch.name === cat || String(ch.id) === cat) : undefined
  const title = leaf?.name ?? activeHero.title

  return (
    <CollectionView
      title={title}
      description={activeHero.description}
      heroImage={activeHero.heroImage}
      heroObjectPosition={activeHero.objectPosition}
      heroVariant={activeHero.variant}
      data={data}
      colorOptions={colorOptions.length > 0 ? colorOptions : FALLBACK_COLORS}
      colorCollectionMap={colorCollectionMap}
      filterDims={filterDims}
      subTabs={subTabs.length > 0 ? subTabs : undefined}
      subTabsAllValue={subTabsAllValue}
      basePath={basePath}
    />
  )
}
