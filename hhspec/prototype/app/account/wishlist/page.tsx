'use client'

import Link from 'next/link'
import { Heart, ShoppingBag } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { products } from '@/data/products'
import { ProductCard } from '@/components/product/product-card'

export default function WishlistPage() {
  const { wishlist, recentlyViewed } = useStore()
  const wished = products.filter((p) => wishlist.includes(p.id))
  const recent = recentlyViewed.map((id) => products.find((p) => p.id === id)).filter(Boolean) as typeof products

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">My Wishlist</h1>
      {wished.length === 0 ? (
        <div className="mt-8 flex flex-col items-center gap-4 rounded-sm border border-dashed border-line py-16 text-center">
          <Heart className="h-12 w-12 text-line" strokeWidth={1} />
          <p className="text-ink-soft">Your wishlist is empty. Tap the heart on any style to save it here.</p>
          <Link href="/wedding-dresses" className="btn-primary">Start Browsing</Link>
        </div>
      ) : (
        <div className="mt-8 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-3">
          {wished.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      )}

      {recent.length > 0 && (
        <section className="mt-16">
          <h2 className="mb-6 font-display text-2xl font-medium">Recently Viewed</h2>
          <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {recent.slice(0, 4).map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      )}
    </div>
  )
}
