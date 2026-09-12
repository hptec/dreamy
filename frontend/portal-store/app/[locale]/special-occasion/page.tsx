import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'
import { resolveCollectionHero } from '@/lib/collection-hero'

/** PAGE-CAT-S02：RSC + URL 驱动（原 mock subTabs → 分类树子分类 cat 参数）。 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Special Occasion Dresses',
  description: 'Bridesmaid, mother-of-the-bride, prom, and cocktail dresses in 18+ colors with custom sizing.'
}

export default async function SpecialOccasionPage({ searchParams }: { searchParams: Promise<CollectionSearchParams> }) {
  const sp = await searchParams
  return (
    <CollectionPage
      categoryNames={['Special Occasion']}
      hero={resolveCollectionHero('Special Occasion')!}
      basePath="/special-occasion"
      searchParams={sp}
    />
  )
}
