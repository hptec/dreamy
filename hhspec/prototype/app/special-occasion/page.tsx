import type { Metadata } from 'next'
import { CollectionView } from '@/components/product/collection-view'
import { getByCategory } from '@/data/products'

export const metadata: Metadata = {
  title: 'Special Occasion Dresses',
  description: 'Bridesmaid, mother-of-the-bride, prom, and cocktail dresses in 18+ colors with custom sizing.'
}

export default function SpecialOccasionPage() {
  return (
    <CollectionView
      title="Special Occasion"
      description="Bridesmaid, guest, and evening dresses your whole party will love — and actually re-wear."
      products={getByCategory('special-occasion')}
      heroImage="/competitor-refs/birdygrey/bridesmaid-pink-bryten-02.jpg"
      subTabs={[
        { label: 'Bridesmaid', value: 'Bridesmaid' },
        { label: 'Mother of the Bride', value: 'MOB' },
        { label: 'Guest', value: 'Guest' },
        { label: 'Prom', value: 'Prom' },
        { label: 'Cocktail', value: 'Cocktail' },
        { label: 'Evening', value: 'Evening' }
      ]}
    />
  )
}
