'use client'

import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { Eyebrow } from '@/components/ui/primitives'
import { cn } from '@/lib/utils'

const faqs = [
  { cat: 'Sizing', q: 'How do I find my size?', a: 'Use our detailed size guide on every product page, with US/UK/AU conversions and measuring instructions. If you\'re between sizes or want a perfect fit, choose Custom for made-to-measure at no extra cost.' },
  { cat: 'Sizing', q: 'What is custom sizing?', a: 'Custom sizing means your gown is made to your exact measurements. Simply select "Custom" at checkout and enter your measurements. Allow 4–6 weeks for production.' },
  { cat: 'Shipping', q: 'How much is shipping?', a: 'Shipping is complimentary worldwide on all orders over $200. Below that, a flat rate applies based on your destination, shown at checkout.' },
  { cat: 'Shipping', q: 'How long does delivery take?', a: 'In-stock items ship within 1–2 weeks. Custom orders take 4–6 weeks. You can choose FedEx, UPS, or DHL Express at checkout for faster delivery.' },
  { cat: 'Shipping', q: 'Do you ship internationally?', a: 'Yes! We ship worldwide. Prices can be viewed in USD, CAD, AUD, and GBP using the currency switcher.' },
  { cat: 'Orders', q: 'Can I order fabric swatches?', a: 'Absolutely. Order free fabric swatches from any product page to see your colors in person before committing.' },
  { cat: 'Orders', q: 'How do payment plans work?', a: 'We offer Klarna and Afterpay at checkout, letting you split your order into 4 interest-free payments.' },
  { cat: 'Orders', q: 'How can I track my order?', a: 'Once your order ships, you\'ll receive a tracking number by email. You can also track it anytime from your account under My Orders.' }
]

const cats = ['All', 'Sizing', 'Shipping', 'Orders']

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
