import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'
import { ALL_STYLES_HERO } from '@/lib/collection-hero'

/** Canonical all-products route used by site-builder navigation and revalidation. */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'All Dresses & Accessories',
  description: 'Browse every Dreamy gown, dress, accessory, and finishing touch in one place.'
}

export default async function ProductsPage({ searchParams }: { searchParams: Promise<CollectionSearchParams> }) {
  const sp = await searchParams
  return (
    <CollectionPage
      categoryNames={[]}
      hero={ALL_STYLES_HERO}
      basePath="/products"
      searchParams={sp}
    />
  )
}
