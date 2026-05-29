'use client'

import { useState } from 'react'
import type { Product } from '@/data/types'
import { Stars, Eyebrow } from '@/components/ui/primitives'
import { cn } from '@/lib/utils'

const sampleReviews = [
  { name: 'Madison R.', rating: 5, date: 'May 2026', title: 'Absolutely perfect for our beach wedding', body: 'The fabric moved beautifully in the ocean breeze and the fit was true to size. I got so many compliments!', verified: true, fit: 'True to size' },
  { name: 'Olivia T.', rating: 5, date: 'April 2026', title: 'Dreamy in every way', body: 'Ordered swatches first which made choosing the color so easy. The quality exceeded my expectations.', verified: true, fit: 'True to size' },
  { name: 'Sophia L.', rating: 4, date: 'March 2026', title: 'Beautiful, sized up', body: 'Stunning dress. I sized up for comfort and it was perfect. The custom option is great if you are between sizes.', verified: true, fit: 'Runs small' }
]

const sampleQA = [
  { q: 'Is this dress lined?', a: 'Yes, the bodice is fully lined and the skirt has a soft lining for comfort and coverage.', author: 'Dreamy Stylist' },
  { q: 'How long does custom sizing take?', a: 'Made-to-measure orders take approximately 4–6 weeks from order date.', author: 'Dreamy Stylist' }
]

export function ProductReviews({ product }: { product: Product }) {
  const [tab, setTab] = useState<'reviews' | 'qa'>('reviews')
  const dist = [70, 22, 5, 2, 1]

  return (
    <section id="reviews" className="border-t border-line py-16">
      <div className="container-luxe">
        <div className="mb-8 flex gap-6 border-b border-line">
          <button onClick={() => setTab('reviews')} className={cn('cursor-pointer border-b-2 pb-3 font-display text-2xl', tab === 'reviews' ? 'border-gold text-ink' : 'border-transparent text-ink-faint')}>Reviews ({product.reviewCount})</button>
          <button onClick={() => setTab('qa')} className={cn('cursor-pointer border-b-2 pb-3 font-display text-2xl', tab === 'qa' ? 'border-gold text-ink' : 'border-transparent text-ink-faint')}>Q&A ({sampleQA.length})</button>
        </div>

        {tab === 'reviews' ? (
          <div className="grid gap-10 lg:grid-cols-3">
            <div>
              <div className="flex items-end gap-3">
                <span className="font-display text-5xl font-medium">{product.rating}</span>
                <div className="pb-1"><Stars rating={product.rating} /><p className="mt-1 text-xs text-ink-soft">{product.reviewCount} reviews</p></div>
              </div>
              <div className="mt-5 space-y-1.5">
                {dist.map((pct, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs">
                    <span className="w-3 text-ink-soft">{5 - i}</span>
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-line"><div className="h-full bg-gold" style={{ width: `${pct}%` }} /></div>
                    <span className="w-8 text-right text-ink-faint">{pct}%</span>
                  </div>
                ))}
              </div>
              <button className="btn-outline mt-6 w-full">Write a Review</button>
            </div>
            <div className="space-y-6 lg:col-span-2">
              {sampleReviews.map((r) => (
                <div key={r.name} className="border-b border-line/60 pb-6">
                  <div className="flex items-center justify-between">
                    <Stars rating={r.rating} />
                    <span className="text-xs text-ink-faint">{r.date}</span>
                  </div>
                  <h4 className="mt-2 font-medium">{r.title}</h4>
                  <p className="mt-1 text-sm text-ink-soft">{r.body}</p>
                  <div className="mt-2 flex items-center gap-3 text-xs text-ink-faint">
                    <span className="font-medium text-ink">{r.name}</span>
                    {r.verified && <span className="rounded-full bg-sage/15 px-2 py-0.5 text-sage-deep">Verified Buyer</span>}
                    <span>Fit: {r.fit}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="max-w-3xl space-y-6">
            {sampleQA.map((item, i) => (
              <div key={i} className="border-b border-line/60 pb-6">
                <p className="font-medium">Q: {item.q}</p>
                <p className="mt-2 text-sm text-ink-soft"><span className="font-medium text-gold-deep">A:</span> {item.a}</p>
                <p className="mt-1 text-xs text-ink-faint">— {item.author}</p>
              </div>
            ))}
            <button className="btn-outline">Ask a Question</button>
          </div>
        )}
      </div>
    </section>
  )
}
