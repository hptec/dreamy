import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'

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
      title="Special Occasion"
      description="Bridesmaid, guest, and evening dresses your whole party will love — and actually re-wear."
      heroImage="/competitor-refs/birdygrey/bridesmaid-pink-bryten-02.jpg"
      basePath="/special-occasion"
      searchParams={sp}
    />
  )
}
