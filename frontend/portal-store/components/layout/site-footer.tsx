'use client'

import { useState } from 'react'
import Link from 'next/link'
import { footerNav } from '@/data/navigation'
import { Instagram, Facebook, Twitter } from 'lucide-react'

export function SiteFooter() {
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)

  return (
    <footer className="mt-24 bg-ink text-canvas">
      {/* Newsletter */}
      <div className="border-b border-white/10">
        <div className="container-luxe grid gap-8 py-14 lg:grid-cols-2 lg:items-center">
          <div>
            <p className="eyebrow mb-2 text-gold-light">Join the Atelier</p>
            <h3 className="font-display text-3xl font-medium lg:text-4xl">Be the first to know</h3>
            <p className="mt-2 max-w-md text-sm text-canvas/70">Sign up for early access to new collections, outdoor wedding inspiration, and 10% off your first order.</p>
          </div>
          <form
            onSubmit={(e) => { e.preventDefault(); setDone(true) }}
            className="flex w-full max-w-md gap-3 lg:ml-auto"
          >
            <label htmlFor="footer-email" className="sr-only">Email address</label>
            <input
              id="footer-email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Your email address"
              className="flex-1 rounded-sm border border-white/20 bg-transparent px-4 py-3 text-sm outline-none placeholder:text-canvas/40 focus:border-gold-light"
            />
            <button type="submit" className="cursor-pointer rounded-sm bg-gold px-6 py-3 text-[13px] font-medium uppercase tracking-luxe text-white transition-colors hover:bg-gold-deep">
              {done ? 'Thank you' : 'Subscribe'}
            </button>
          </form>
        </div>
      </div>

      {/* Links */}
      <div className="container-luxe grid grid-cols-2 gap-8 py-14 sm:grid-cols-4">
        {footerNav.map((col) => (
          <div key={col.title}>
            <p className="eyebrow mb-4 text-gold-light">{col.title}</p>
            <ul className="space-y-2.5">
              {col.links.map((l) => (
                <li key={l.label}>
                  <Link href={l.href} className="text-sm text-canvas/70 transition-colors hover:text-canvas">{l.label}</Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      {/* Bottom bar */}
      <div className="border-t border-white/10">
        <div className="container-luxe flex flex-col items-center justify-between gap-4 py-6 text-xs text-canvas/60 sm:flex-row">
          <div className="flex items-center gap-4">
            <span className="font-display text-lg text-canvas">Dreamy</span>
            <span>© 2026 Dreamy Atelier. Prototype demo.</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="hidden sm:inline">We accept</span>
            <div className="flex items-center gap-2">
              {['Visa', 'MC', 'Amex', 'PayPal', 'Klarna'].map((p) => (
                <span key={p} className="rounded border border-white/20 px-2 py-1 text-[10px] tracking-wide">{p}</span>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-3">
            <a href="#" aria-label="Instagram" className="transition-colors hover:text-canvas"><Instagram className="h-4 w-4" /></a>
            <a href="#" aria-label="Facebook" className="transition-colors hover:text-canvas"><Facebook className="h-4 w-4" /></a>
            <a href="#" aria-label="Twitter" className="transition-colors hover:text-canvas"><Twitter className="h-4 w-4" /></a>
          </div>
        </div>
      </div>
    </footer>
  )
}
