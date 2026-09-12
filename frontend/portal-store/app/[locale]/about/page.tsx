import type { Metadata } from 'next'
import Link from 'next/link'
import { EditorialHero } from '@/components/marketing/editorial-hero'

export const metadata: Metadata = { title: 'About Dreamy', description: 'Our story — luxury outdoor wedding dresses designed for the modern bride.' }

export default function AboutPage() {
  return (
    <div>
      <EditorialHero
        image="/photography/about-atelier.jpg"
        alt="Gowns hanging in the Dreamy atelier"
        eyebrow="Our Story"
        title="Designed for golden hour"
        description="Made-to-measure gowns, cut for open skies — from our atelier to your aisle."
        objectPosition="center 50%"
      />

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
