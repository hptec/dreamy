import type { Metadata } from 'next'
import { CollectionView } from '@/components/product/collection-view'
import { getByCategory } from '@/data/products'

export const metadata: Metadata = {
  title: 'Wedding Dresses',
  description: 'Shop A-line, mermaid, and short wedding gowns in airy fabrics designed for outdoor ceremonies.'
}

export default function WeddingDressesPage() {
  return (
    <CollectionView
      title="Wedding Dresses"
      description="Airy, light-catching gowns made to move with you — from beachfront vows to garden celebrations."
      products={getByCategory('wedding-dresses')}
      heroImage="/competitor-refs/davidsbridal/wedding-dress-04.jpg"
    />
  )
}
