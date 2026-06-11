import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'

/** PAGE-CAT-S02：RSC + ISR + URL 驱动。 */

export const revalidate = 300

export const metadata: Metadata = {
  title: 'Bridal Accessories',
  description: 'Veils, shoes, jewelry, and headpieces to complete your outdoor wedding look.'
}

export default async function AccessoriesPage({ searchParams }: { searchParams: Promise<CollectionSearchParams> }) {
  const sp = await searchParams
  return (
    <CollectionPage
      categoryNames={['Accessories']}
      title="Accessories"
      description="The finishing touches — veils, heels, jewelry, and headpieces to complete every look."
      heroImage="/competitor-refs/birdygrey/accessory-jewelry-01.jpg"
      basePath="/accessories"
      searchParams={sp}
    />
  )
}
