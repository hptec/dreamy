import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { fetchStoreWedding } from '@/lib/api/marketing-server'
import { ProductCard } from '@/components/product/product-card'
import type { StoreProductCard } from '@/lib/api/store-types'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

/**
 * /real-weddings/[slug]（PAGE-MKT-S06）：路由段目录名保持 [slug]、参数值改数字 id（契约按 id 取详情）；
 * 404701 → notFound()。
 * 显式偏离（marketing-frontend §C）：契约无 gallery 字段 → 多图画廊降级 cover 单图。
 * Shop the Look 区块 ← 响应 products[]（ProductCard 复用）。
 */

export const dynamic = 'force-dynamic'

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  const id = Number(slug)
  if (!Number.isFinite(id)) return { title: 'Wedding Not Found' }
  const { data: w } = await fetchStoreWedding(id)
  if (!w) return { title: 'Wedding Not Found' }
  return { title: `${w.couple} · Real Wedding`, description: w.title }
}

export default async function RealWeddingDetail({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const id = Number(slug)
  if (!Number.isFinite(id)) notFound()
  const { data: w } = await fetchStoreWedding(id)
  if (!w) notFound()

  const products = w.products ?? []
  const paragraphs = (w.story ?? '').split(/\n+/).filter((p) => p.trim().length > 0)

  return (
    <div>
      <section className="relative h-[65vh] min-h-[440px] overflow-hidden bg-muted">
        {w.cover && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={w.cover} alt={w.couple} className="absolute inset-0 h-full w-full object-cover" />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-ink/70 to-ink/10" />
        <div className="container-luxe relative flex h-full flex-col justify-end pb-12 text-canvas">
          <Eyebrow className="text-gold-light">{[w.theme ? `${w.theme} Wedding` : 'Real Wedding', w.location].filter(Boolean).join(' · ')}</Eyebrow>
          <h1 className="mt-2 font-display text-5xl font-medium lg:text-6xl">{w.couple}</h1>
          {w.weddingDate && <p className="text-canvas/80">{w.weddingDate}</p>}
        </div>
      </section>

      <article className="container-luxe max-w-3xl py-16">
        {w.title && <p className="font-display text-2xl leading-relaxed text-ink">{w.title}</p>}
        {paragraphs.length > 0 && (
          <div className="mt-8 space-y-5 text-lg leading-relaxed text-ink-soft">
            {paragraphs.map((p, i) => <p key={i}>{p}</p>)}
          </div>
        )}
      </article>

      {products.length > 0 && (
        <section className="bg-muted py-16">
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

      <div className="container-luxe py-12 text-center">
        <Link href="/real-weddings" className="btn-outline">← All Real Weddings</Link>
      </div>
    </div>
  )
}
