import type { Metadata } from 'next'
import { CollectionView } from '@/components/product/collection-view'
import { getByCategory } from '@/data/products'

export const metadata: Metadata = {
  title: 'Bridal Accessories',
  description: 'Veils, shoes, jewelry, and headpieces to complete your outdoor wedding look.'
}

export default function AccessoriesPage() {
  return (
    <CollectionView
      title="Accessories"
      description="The finishing touches — veils, heels, jewelry, and headpieces to complete every look."
      products={getByCategory('accessories')}
      heroImage="/competitor-refs/birdygrey/accessory-jewelry-01.jpg"
      subTabs={[
        { label: 'Veils', value: 'Veils' },
        { label: 'Shoes', value: 'Shoes' },
        { label: 'Jewelry', value: 'Jewelry' },
        { label: 'Headpieces', value: 'Headpieces' }
      ]}
    />
  )
}
