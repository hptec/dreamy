import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'
import { resolveCollectionHero } from '@/lib/collection-hero'

/**
 * PAGE-CAT-S02：RSC + URL 驱动。
 * 原型时代的「Special Occasion」在后台分类树里并不存在（真实顶级分类是 Bridesmaids 与 Occasion & Party），
 * 之前按不存在的分类名解析 → categoryId 为空 → 页面标题写 Special Occasion 却把 32 款含婚纱全量倒出来。
 * 现固定落到 Occasion & Party（Prom & Evening 子类）；伴娘礼服走 /products?cat=Bridesmaids。
 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Occasion & Party Dresses',
  description: 'Prom, evening, and celebration dresses in lace, tulle, and jacquard — with custom sizing.'
}

export default async function SpecialOccasionPage({ searchParams }: { searchParams: Promise<CollectionSearchParams> }) {
  const sp = await searchParams
  return (
    <CollectionPage
      categoryNames={['Occasion & Party']}
      hero={resolveCollectionHero('Occasion & Party')!}
      basePath="/special-occasion"
      searchParams={sp}
    />
  )
}
