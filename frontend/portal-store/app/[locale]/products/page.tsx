import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'

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
      title="All Styles"
      description="Explore every dress, gown, and accessory for your celebration."
      heroImage="/competitor-refs/davidsbridal/wedding-dress-04.jpg"
      basePath="/products"
      searchParams={sp}
    />
  )
}
