import type { Metadata } from 'next'
import Link from 'next/link'
import { Clock3 } from 'lucide-react'

export const metadata: Metadata = {
  title: 'Preview unavailable',
  robots: { index: false, follow: false, nocache: true },
  referrer: 'no-referrer',
}

export default function InvalidHomePreviewPage() {
  return (
    <section className="container-luxe flex min-h-[55vh] items-center justify-center py-20">
      <div className="max-w-lg text-center">
        <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gold/10 text-gold-deep">
          <Clock3 className="h-5 w-5" />
        </span>
        <p className="eyebrow mt-6">Private preview</p>
        <h1 className="mt-3 font-display text-4xl font-medium">This preview link is unavailable</h1>
        <p className="mt-4 text-ink-soft">The link may have expired or been copied incorrectly. Generate a new private preview from the homepage builder.</p>
        <Link href="/" className="btn-primary mt-8">Return to storefront</Link>
      </div>
    </section>
  )
}
