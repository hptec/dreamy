import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { getProduct, products, getRelated, getPairsWith } from '@/data/products'
import { ProductGallery } from '@/components/product/product-gallery'
import { ProductBuyBox } from '@/components/product/product-buy-box'
import { ProductReviews } from '@/components/product/product-reviews'
import { ProductCard } from '@/components/product/product-card'
import { SectionHeading } from '@/components/ui/primitives'

export function generateStaticParams() {
  return products.map((p) => ({ slug: p.slug }))
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  const product = getProduct(slug)
  if (!product) return { title: 'Product Not Found' }
  return {
    title: product.name,
    description: product.description,
    openGraph: { title: product.name, description: product.description, images: [product.gallery[0]] }
  }
}

export default async function ProductPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const product = getProduct(slug)
  if (!product) notFound()

  const related = getRelated(product)
  const pairs = getPairsWith(product)

  return (
    <div>
      {/* 面包屑 */}
      <div className="container-luxe py-5 text-xs text-ink-soft">
        <Link href="/" className="hover:text-ink">Home</Link>
        <span className="mx-2">/</span>
        <Link href={`/${product.category}`} className="capitalize hover:text-ink">{product.category.replace('-', ' ')}</Link>
        <span className="mx-2">/</span>
        <span className="text-ink">{product.name}</span>
      </div>

      {/* 主区：图廊 + 购买框 */}
      <div className="container-luxe grid gap-10 pb-12 lg:grid-cols-2 lg:gap-16">
        <ProductGallery images={product.gallery} name={product.name} hasVideo={product.hasVideo} lifestyle={product.lifestyle} />
        <ProductBuyBox product={product} />
      </div>

      {/* Complete the Look */}
      {pairs.length > 0 && (
        <section className="bg-muted py-16">
          <div className="container-luxe">
            <SectionHeading align="left" eyebrow="Style it together" title="Complete the Look" />
            <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
              {pairs.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          </div>
        </section>
      )}

      {/* Reviews & Q&A */}
      <ProductReviews product={product} />

      {/* Related */}
      <section className="container-luxe py-16">
        <SectionHeading align="left" eyebrow="You may also like" title="More to love" />
        <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
          {related.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>
    </div>
  )
}
