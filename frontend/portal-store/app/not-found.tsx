import Link from 'next/link'

export default function NotFound() {
  return (
    <div className="container-luxe flex min-h-[60vh] flex-col items-center justify-center py-20 text-center">
      <p className="font-display text-8xl font-medium text-gold">404</p>
      <h1 className="mt-4 font-display text-3xl font-medium">This page wandered off</h1>
      <p className="mt-3 max-w-sm text-ink-soft">The page you&apos;re looking for doesn&apos;t exist or has moved. Let&apos;s get you back to the dresses.</p>
      <div className="mt-8 flex gap-3">
        <Link href="/" className="btn-primary">Back Home</Link>
        <Link href="/wedding-dresses" className="btn-outline">Shop Dresses</Link>
      </div>
    </div>
  )
}
