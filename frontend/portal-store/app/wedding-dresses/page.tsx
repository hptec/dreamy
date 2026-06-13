import type { Metadata } from 'next'
import { CollectionPage, type CollectionSearchParams } from '@/components/product/collection-page'

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
      title="Wedding Dresses"
      description="Airy, light-catching gowns made to move with you — from beachfront vows to garden celebrations."
      heroImage="/competitor-refs/davidsbridal/wedding-dress-04.jpg"
      basePath="/wedding-dresses"
      searchParams={sp}
    />
  )
}
