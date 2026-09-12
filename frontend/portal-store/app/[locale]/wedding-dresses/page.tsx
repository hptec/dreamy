import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'
import { resolveCollectionHero } from '@/lib/collection-hero'

/** PAGE-CAT-S02：RSC + URL searchParams 驱动筛选（E-CAT-01/06/07）。 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Wedding Dresses',
  description: 'Shop A-line, mermaid, and short wedding gowns in airy fabrics designed for outdoor ceremonies.'
}

export default async function WeddingDressesPage({ searchParams }: { searchParams: Promise<CollectionSearchParams> }) {
  const sp = await searchParams
  return (
    <CollectionPage
      categoryNames={['Wedding Dresses', 'Bridal']}
      hero={resolveCollectionHero('Wedding Dresses')!}
      basePath="/wedding-dresses"
      searchParams={sp}
    />
  )
}
