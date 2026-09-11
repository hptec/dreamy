'use client'

import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { Eyebrow } from '@/components/ui/primitives'
import { cn } from '@/lib/utils'

const faqs = [
  { cat: 'Sizing', q: 'How do I find my size?', a: 'Use our detailed size guide on every product page, with US/UK/AU conversions and measuring instructions. If you\'re between sizes or want a perfect fit, choose Custom for made-to-measure at no extra cost.' },
  { cat: 'Sizing', q: 'What is custom sizing?', a: 'Custom sizing means your gown is made to your exact measurements. Simply select "Custom" at checkout and enter your measurements. Allow 4–6 weeks for production.' },
  { cat: 'Shipping', q: 'How much is shipping?', a: 'U.S. shipping is complimentary on orders over $199. Below that, a flat rate of $12.95 applies. International rates are calculated at checkout by destination.' },
  { cat: 'Shipping', q: 'How long does delivery take?', a: 'Made-to-order gowns take 8–12 weeks from your order date; rush production (3–4 weeks) is available on most styles for a fee starting at $79. In-stock accessories ship within a week.' },
  { cat: 'Shipping', q: 'Do you ship internationally?', a: 'Yes! We ship worldwide via FedEx, UPS, and DHL Express. Prices can be viewed in USD, CAD, AUD, and GBP using the currency switcher.' },
  { cat: 'Orders', q: 'Can I order fabric swatches?', a: 'Absolutely. Order free fabric swatches from any product page to see your colors in person before committing.' },
  { cat: 'Orders', q: 'How do payment plans work?', a: 'We offer Klarna and Afterpay at checkout, letting you split your order into 4 interest-free payments.' },
  { cat: 'Orders', q: 'How can I track my order?', a: 'Once your order ships, you\'ll receive a tracking number by email. You can also track it anytime from your account under My Orders.' },
  { cat: 'Returns', q: 'What is your return policy?', a: 'Ready-to-wear styles can be returned within 30 days of delivery in original condition for a full refund. Made-to-order gowns over $999 may be returned within 14 days.' },
  { cat: 'Returns', q: 'Are exchanges free?', a: 'Yes — exchanges are always free, including size exchanges on made-to-order gowns. We cover the shipping both ways; simply contact care@dreamy.com to start.' },
  { cat: 'Returns', q: 'Can custom-sized gowns be returned?', a: 'Custom-sized gowns are final sale, as they are cut specifically for you. If the fit isn\'t right, we offer alterations credit of 50% of the gown price toward a local seamstress, or a one-time size exchange.' }
]

const cats = ['All', 'Sizing', 'Shipping', 'Orders', 'Returns']

export default function FaqPage() {
  const [cat, setCat] = useState('All')
  const [open, setOpen] = useState<number | null>(0)
  const list = cat === 'All' ? faqs : faqs.filter((f) => f.cat === cat)

  return (
    <div className="container-luxe max-w-3xl py-16">
      <div className="text-center">
        <Eyebrow>Help Center</Eyebrow>
        <h1 className="mt-2 font-display text-5xl font-medium">Frequently Asked Questions</h1>
      </div>

      <div className="mt-10 flex flex-wrap justify-center gap-2">
        {cats.map((c) => (
          <button key={c} onClick={() => { setCat(c); setOpen(null) }} className={cn('cursor-pointer rounded-full px-5 py-2 text-xs uppercase tracking-luxe transition-colors', cat === c ? 'bg-ink text-canvas' : 'border border-line text-ink-soft hover:border-ink')}>{c}</button>
        ))}
      </div>

      <div id="size" className="mt-10 divide-y divide-line border-y border-line">
        {list.map((f, i) => (
          <div key={f.q} id={f.cat === 'Shipping' ? 'shipping' : undefined}>
            <button onClick={() => setOpen(open === i ? null : i)} className="flex w-full cursor-pointer items-center justify-between py-5 text-left">
              <span className="font-medium">{f.q}</span>
              <ChevronDown className={cn('h-5 w-5 shrink-0 transition-transform', open === i && 'rotate-180')} />
            </button>
            {open === i && <p className="pb-5 text-sm leading-relaxed text-ink-soft">{f.a}</p>}
          </div>
        ))}
      </div>
    </div>
  )
}
