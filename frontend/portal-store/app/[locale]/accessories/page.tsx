import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'
import { resolveCollectionHero } from '@/lib/collection-hero'

/** PAGE-CAT-S02：RSC + URL 驱动。 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Bridal Accessories',
  description: 'Veils, shoes, jewelry, and headpieces to complete your outdoor wedding look.'
}

export default async function AccessoriesPage({ searchParams }: { searchParams: Promise<CollectionSearchParams> }) {
  const sp = await searchParams
  return (
    <CollectionPage
      categoryNames={['Accessories']}
      hero={resolveCollectionHero('Accessories')!}
      basePath="/accessories"
      searchParams={sp}
    />
  )
}
