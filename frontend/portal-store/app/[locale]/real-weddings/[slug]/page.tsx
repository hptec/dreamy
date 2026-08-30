import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { fetchStoreWedding } from '@/lib/api/marketing-server'
import { ProductCard } from '@/components/product/product-card'
import type { StoreProductCard } from '@/lib/api/store-types'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'
import { BlogPostBody } from '@/components/blog/BlogPostBody'
import type { Locale } from '@/lib/api/types'

/**
 * /real-weddings/[slug]（PAGE-MKT-S06）：路由段目录名保持 [slug]、参数值改数字 id（契约按 id 取详情）；
 * 404701 → notFound()。
 * 显式偏离（marketing-frontend §C）：契约无 gallery 字段 → 多图画廊降级 cover 单图。
 * Shop the Look 区块 ← 响应 products[]（ProductCard 复用）。
 */

export const dynamic = 'force-dynamic'

type PageParams = { locale: string; slug: string }

export async function generateMetadata({ params }: { params: Promise<PageParams> }): Promise<Metadata> {
  const { locale, slug } = await params
  const activeLocale = (locale as Locale) ?? 'en'
  const id = Number(slug)
  if (!Number.isFinite(id)) return { title: 'Wedding Not Found' }
  const { data: w } = await fetchStoreWedding(id, activeLocale)
  if (!w) return { title: 'Wedding Not Found' }
  return { title: `${w.couple} · Real Wedding`, description: w.title }
}

export default async function RealWeddingDetail({ params }: { params: Promise<PageParams> }) {
  const { locale, slug } = await params
  const activeLocale = (locale as Locale) ?? 'en'
  const id = Number(slug)
  if (!Number.isFinite(id)) notFound()
  const { data: w } = await fetchStoreWedding(id, activeLocale)
  if (!w) notFound()

  const products = w.products ?? []

  return (
    <div>
      <section className="container-luxe pt-14 pb-10 text-center lg:pt-20 lg:pb-14">
        <Eyebrow>{[w.theme ? `${w.theme} Wedding` : 'Real Wedding', w.location].filter(Boolean).join(' · ')}</Eyebrow>
        <h1 className="heading-display mx-auto mt-4 max-w-4xl text-5xl lg:text-7xl">{w.couple}</h1>
        <div className="mx-auto mt-6 flex items-center justify-center gap-4 text-sm text-ink-faint">
          <span className="h-px w-10 bg-line" aria-hidden />
          {w.weddingDate && <span className="uppercase tracking-luxe">{w.weddingDate}</span>}
          <span className="h-px w-10 bg-line" aria-hidden />
        </div>
        {w.title && <p className="mx-auto mt-6 max-w-2xl font-display text-xl italic leading-relaxed text-ink-soft lg:text-2xl">{w.title}</p>}
      </section>

      {w.cover && (
        <section className="container-luxe">
          <div className="mx-auto aspect-[4/5] max-w-3xl overflow-hidden rounded-sm bg-muted lg:max-w-4xl">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={w.cover} alt={w.couple} className="h-full w-full object-cover" />
          </div>
        </section>
      )}

      <article className="container-luxe max-w-3xl py-16 lg:py-20">
        {w.story && <BlogPostBody content={w.story} />}
      </article>

      {products.length > 0 && (
        <section className="bg-muted py-16 lg:py-20">
          <div className="container-luxe">
            <SectionHeading eyebrow="Get the look" title="Shop this wedding" />
            <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-3">
              {products.map((p) => {
                const card: StoreProductCard = { id: p.id, slug: p.slug, name: p.name, price: p.price, imageUrl: p.imageUrl, installment: false }
                return <ProductCard key={p.id} product={card} />
              })}
            </div>
          </div>
        </section>
      )}

      <div className="container-luxe py-14 text-center">
        <Link href="/real-weddings" className="btn-outline">← All Real Weddings</Link>
      </div>
    </div>
  )
}
