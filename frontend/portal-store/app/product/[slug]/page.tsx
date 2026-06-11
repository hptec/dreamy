import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { fetchStoreProduct } from '@/lib/api/catalog-server'
import { fetchStoreReviewsServer, fetchStoreQuestionsServer } from '@/lib/api/review-api'
import { ProductGallery } from '@/components/product/product-gallery'
import { ProductBuyBox } from '@/components/product/product-buy-box'
import { ProductReviews } from '@/components/product/product-reviews'
import { RecommendationRail } from '@/components/product/recommendation-rail'
import { galleryOf, lifestyleOf, hasVideoOf } from '@/components/product/product-utils'

/**
 * PDP（PAGE-CAT-S01，layout-keep + data-swap）：
 * - generateStaticParams 全量预构建删除 → dynamicParams=true + revalidate=300（TTL 兜底）；
 *   秒级失效靠 on-demand POST /api/revalidate → revalidatePath（FLOW-P03/s-758）。
 * - 数据：E-CAT-04 详情 + E-REV-01/03 评价区首屏（随 PDP ISR）+ E-CAT-03 推荐区块。
 * - 404501 → notFound()。
 */

export const revalidate = 300
export const dynamicParams = true

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  const { data: product } = await fetchStoreProduct(slug)
  if (!product) return { title: 'Product Not Found' }
  return {
    title: product.seoTitle ?? product.name,
    description: product.seoDesc ?? product.description,
    openGraph: {
      title: product.seoTitle ?? product.name,
      description: product.seoDesc ?? product.description,
      images: product.images[0]?.url ? [product.images[0].url] : undefined
    }
  }
}

export default async function ProductPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const { data: product, status } = await fetchStoreProduct(slug)
  if (!product) {
    if (status === 404) notFound()
    // 上游不可用：不缓存错误页语义，返回 404 视图以免白屏（CDN serve-stale 为主兜底）
    notFound()
  }

  const [initialReviews, initialQuestions] = await Promise.all([
    fetchStoreReviewsServer(product.id, { sort: 'featured_first', page: 1, pageSize: 5 }),
    fetchStoreQuestionsServer(product.id, { page: 1, pageSize: 5 })
  ])

  const gallery = galleryOf(product).map((img) => img.url)
  const categorySlugMap: Record<string, string> = {
    'wedding dresses': '/wedding-dresses',
    'special occasion': '/special-occasion',
    accessories: '/accessories'
  }
  const categoryHref = categorySlugMap[(product.categoryName ?? '').toLowerCase()] ?? '/wedding-dresses'

  return (
    <div>
      {/* 面包屑 */}
      <div className="container-luxe py-5 text-xs text-ink-soft">
        <Link href="/" className="hover:text-ink">Home</Link>
        <span className="mx-2">/</span>
        <Link href={categoryHref} className="capitalize hover:text-ink">{product.categoryName ?? 'Collection'}</Link>
        <span className="mx-2">/</span>
        <span className="text-ink">{product.name}</span>
      </div>

      {/* 主区：图廊 + 购买框 */}
      <div className="container-luxe grid gap-10 pb-12 lg:grid-cols-2 lg:gap-16">
        <ProductGallery images={gallery} name={product.name} hasVideo={hasVideoOf(product)} lifestyle={lifestyleOf(product)} />
        <ProductBuyBox product={product} />
      </div>

      {/* Complete the Look（E-CAT-03 block=complete_the_look） */}
      <RecommendationRail
        block="complete_the_look"
        eyebrow="Style it together"
        title="Complete the Look"
        productId={product.id}
        tone="muted"
      />

      {/* Reviews & Q&A（PAGE-REV-S01） */}
      <ProductReviews
        productId={product.id}
        slug={product.slug}
        initialReviews={initialReviews}
        initialQuestions={initialQuestions}
      />

      {/* You may also like（E-CAT-03 block=you_may_also_like） */}
      <RecommendationRail
        block="you_may_also_like"
        eyebrow="You may also like"
        title="More to love"
        productId={product.id}
      />
    </div>
  )
}
