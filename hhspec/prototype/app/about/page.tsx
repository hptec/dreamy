import type { Metadata } from 'next'
import Link from 'next/link'
import { Eyebrow } from '@/components/ui/primitives'

export const metadata: Metadata = { title: 'About Dreamy', description: 'Our story — luxury outdoor wedding dresses designed for the modern bride.' }

export default function AboutPage() {
  return (
    <div>
      <section className="relative h-[55vh] min-h-[380px] overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="The Dreamy atelier" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-ink/35" />
        <div className="container-luxe relative flex h-full flex-col items-center justify-center text-center text-canvas">
          <Eyebrow className="text-gold-light">Our Story</Eyebrow>
          <h1 className="mt-3 font-display text-5xl font-medium lg:text-6xl">Designed for golden hour</h1>
        </div>
      </section>

      <section className="container-luxe max-w-3xl py-16 text-center">
        <p className="font-display text-3xl leading-relaxed">Dreamy began with a simple belief: a wedding dress should feel as free and natural as the place you fall in love.</p>
        <div className="mt-8 space-y-5 text-left text-lg leading-relaxed text-ink-soft">
          <p>We design for the modern outdoor bride — the one saying her vows on a windswept beach, in a sun-dappled garden, or beneath towering redwoods. Our gowns are made from light, breathable fabrics that move with you, in a palette curated for every outdoor setting.</p>
          <p>From our signature luxe-knit bridesmaid dresses in 18+ shades to made-to-measure bridal gowns, every piece is created to be worn, loved, and remembered. We offer free fabric swatches so you can see your colors in person, and custom sizing on every style at no extra cost.</p>
          <p>Because your wedding day should feel effortless — and so should finding the dress.</p>
        </div>
      </section>

      <section className="bg-muted py-16">
        <div className="container-luxe grid gap-8 text-center sm:grid-cols-3">
          {[
            { stat: '18+', label: 'Bridesmaid shades' },
            { stat: 'Free', label: 'Fabric swatches & worldwide shipping' },
            { stat: '100%', label: 'Made-to-measure available' }
          ].map((s) => (
            <div key={s.label}>
              <p className="font-display text-5xl font-medium text-gold">{s.stat}</p>
              <p className="mt-2 text-sm text-ink-soft">{s.label}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="container-luxe py-16 text-center">
        <h2 className="font-display text-3xl font-medium">Ready to find your dress?</h2>
        <div className="mt-6 flex justify-center gap-3">
          <Link href="/wedding-dresses" className="btn-primary">Shop Gowns</Link>
          <Link href="/contact" className="btn-outline">Contact Us</Link>
        </div>
      </section>
    </div>
  )
}
