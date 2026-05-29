import type { Metadata } from 'next'
import Link from 'next/link'
import { Check } from 'lucide-react'
import { weddingGuides } from '@/data/content'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

export const metadata: Metadata = {
  title: 'Wedding Planning Guides',
  description: 'A timeline-based guide to planning your outdoor wedding wardrobe, from dream to I do.'
}

export default function WeddingGuidesPage() {
  return (
    <div>
      <section className="relative bg-muted py-20 text-center">
        <div className="container-luxe">
          <Eyebrow>Plan with us</Eyebrow>
          <h1 className="mt-3 font-display text-5xl font-medium">Your Wedding Wardrobe Timeline</h1>
          <p className="mx-auto mt-4 max-w-xl text-ink-soft">From the first daydream to the final fitting — here&apos;s exactly when to tackle each part of your outdoor wedding look.</p>
        </div>
      </section>

      <section className="container-luxe py-16">
        <div className="relative mx-auto max-w-3xl">
          <div className="absolute bottom-0 left-6 top-0 w-px bg-line sm:left-1/2" />
          <div className="space-y-12">
            {weddingGuides.map((g, i) => (
              <div key={g.id} className={`relative grid gap-6 sm:grid-cols-2 ${i % 2 ? 'sm:[direction:rtl]' : ''}`}>
                <div className="absolute left-6 top-2 z-10 flex h-4 w-4 -translate-x-1/2 items-center justify-center rounded-full bg-gold ring-4 ring-canvas sm:left-1/2" />
                <div className={`pl-12 [direction:ltr] sm:pl-0 ${i % 2 ? 'sm:pr-12 sm:text-right' : 'sm:pl-12'}`}>
                  <Eyebrow>{g.phase} · {g.timeframe}</Eyebrow>
                  <h3 className="mt-1 font-display text-2xl font-medium">{g.title}</h3>
                  <p className="mt-2 text-sm text-ink-soft">{g.description}</p>
                </div>
                <div className="pl-12 [direction:ltr] sm:pl-0">
                  <ul className="space-y-2 rounded-sm border border-line bg-surface p-5">
                    {g.tasks.map((t) => (
                      <li key={t} className="flex items-start gap-2 text-sm text-ink-soft">
                        <Check className="mt-0.5 h-4 w-4 shrink-0 text-gold" /> {t}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-16 text-center">
          <Link href="/wedding-dresses" className="btn-primary">Start with the Dress</Link>
        </div>
      </section>
    </div>
  )
}
