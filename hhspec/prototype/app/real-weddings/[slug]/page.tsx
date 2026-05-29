import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { getRealWedding, realWeddings } from '@/data/content'
import { products } from '@/data/products'
import { ProductCard } from '@/components/product/product-card'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

export function generateStaticParams() {
  return realWeddings.map((w) => ({ slug: w.slug }))
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  const w = getRealWedding(slug)
  if (!w) return { title: 'Wedding Not Found' }
  return { title: `${w.couple} · Real Wedding`, description: w.excerpt }
}

export default async function RealWeddingDetail({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const w = getRealWedding(slug)
  if (!w) notFound()
  const shopLook = w.shopTheLook.map((id) => products.find((p) => p.id === id)).filter(Boolean) as typeof products

  return (
    <div>
      <section className="relative h-[65vh] min-h-[440px] overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={w.cover} alt={w.couple} className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-ink/70 to-ink/10" />
        <div className="container-luxe relative flex h-full flex-col justify-end pb-12 text-canvas">
          <Eyebrow className="text-gold-light">{w.theme} Wedding · {w.location}</Eyebrow>
          <h1 className="mt-2 font-display text-5xl font-medium lg:text-6xl">{w.couple}</h1>
          <p className="text-canvas/80">{w.date}</p>
        </div>
      </section>

      <article className="container-luxe max-w-3xl py-16">
        <p className="font-display text-2xl leading-relaxed text-ink">{w.excerpt}</p>
        <div className="mt-8 space-y-5 text-lg leading-relaxed text-ink-soft">
          {w.story.map((p, i) => <p key={i}>{p}</p>)}
        </div>
        <div className="mt-10 grid grid-cols-2 gap-4 sm:grid-cols-3">
          {w.gallery.map((img, i) => (
            // eslint-disable-next-line @next/next/no-img-element
            <img key={i} src={img} alt={`${w.couple} ${i + 1}`} className="aspect-[3/4] w-full rounded-sm object-cover" />
          ))}
        </div>
      </article>

      <section className="bg-muted py-16">
        <div className="container-luxe">
          <SectionHeading eyebrow="Get the look" title="Shop this wedding" />
          <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-3">
            {shopLook.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </div>
      </section>

      <div className="container-luxe py-12 text-center">
        <Link href="/real-weddings" className="btn-outline">← All Real Weddings</Link>
      </div>
    </div>
  )
}
