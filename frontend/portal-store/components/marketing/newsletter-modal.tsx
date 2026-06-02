'use client'

import { useState, useEffect } from 'react'
import { X } from 'lucide-react'

export function NewsletterModal() {
  const [open, setOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (sessionStorage.getItem('dreamy_newsletter_seen')) return
    const t = setTimeout(() => setOpen(true), 4000)
    return () => clearTimeout(t)
  }, [])

  const close = () => {
    setOpen(false)
    sessionStorage.setItem('dreamy_newsletter_seen', '1')
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={close} />
      <div className="relative grid w-full max-w-3xl animate-fadeup overflow-hidden rounded-sm bg-canvas shadow-lift sm:grid-cols-2">
        <button onClick={close} className="absolute right-3 top-3 z-10 cursor-pointer rounded-full bg-canvas/80 p-1.5" aria-label="Close"><X className="h-4 w-4" /></button>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="Outdoor bride" className="hidden h-full w-full object-cover sm:block" />
        <div className="flex flex-col justify-center p-8 sm:p-10">
          <p className="eyebrow mb-2">Welcome to Dreamy</p>
          <h2 className="font-display text-3xl font-medium leading-tight">Take 10% off<br />your first order</h2>
          <p className="mt-3 text-sm text-ink-soft">Join our list for outdoor wedding inspiration, early access, and a little something to start your story.</p>
          {done ? (
            <p className="mt-6 rounded-sm bg-sage/15 px-4 py-3 text-sm text-sage-deep">Welcome! Check your inbox for your code.</p>
          ) : (
            <form onSubmit={(e) => { e.preventDefault(); setDone(true) }} className="mt-6 space-y-3">
              <label htmlFor="modal-email" className="sr-only">Email</label>
              <input id="modal-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Your email address" className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
              <button type="submit" className="btn-primary w-full">Reveal My Code</button>
            </form>
          )}
          <button onClick={close} className="mt-3 cursor-pointer text-xs text-ink-faint underline">No thanks, I’ll pay full price</button>
        </div>
      </div>
    </div>
  )
}
