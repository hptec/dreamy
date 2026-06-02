'use client'

import { useState, useMemo, Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { Search } from 'lucide-react'
import { products } from '@/data/products'
import { ProductCard } from '@/components/product/product-card'
import { Eyebrow } from '@/components/ui/primitives'

function SearchInner() {
  const params = useSearchParams()
  const initial = params.get('q') ?? ''
  const [q, setQ] = useState(initial)

  const results = useMemo(() => {
    if (q.trim().length < 1) return []
    const term = q.toLowerCase()
    return products.filter((p) =>
      p.name.toLowerCase().includes(term) ||
      p.subCategory.toLowerCase().includes(term) ||
      p.colors.some((c) => c.name.toLowerCase().includes(term)) ||
      p.occasion.some((o) => o.toLowerCase().includes(term)) ||
      (p.silhouette ?? '').toLowerCase().includes(term) ||
      (p.themes ?? []).some((t) => t.toLowerCase().includes(term))
    )
  }, [q])

  const suggestions = ['Sage', 'A-Line', 'Beach', 'Bridesmaid', 'Veil', 'Lace']

  return (
    <div className="container-luxe py-12 lg:py-16">
      <Eyebrow className="mb-3">Search</Eyebrow>
      <div className="flex items-center gap-3 border-b-2 border-ink/20 pb-4">
        <Search className="h-6 w-6 text-ink-soft" />
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search gowns, colors, occasions..."
          className="flex-1 bg-transparent font-display text-3xl outline-none placeholder:text-ink-faint lg:text-4xl"
        />
      </div>

      {q.trim().length < 1 ? (
        <div className="py-10">
          <p className="eyebrow mb-3">Try searching for</p>
          <div className="flex flex-wrap gap-2">
            {suggestions.map((s) => (
              <button key={s} onClick={() => setQ(s)} className="cursor-pointer rounded-full border border-line px-4 py-2 text-sm transition-colors hover:border-gold hover:text-gold-deep">{s}</button>
            ))}
          </div>
        </div>
      ) : results.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <p className="font-display text-3xl">No results for “{q}”</p>
          <p className="text-ink-soft">Try a color, silhouette, or occasion — or browse our collections.</p>
          <Link href="/wedding-dresses" className="btn-primary">Browse Dresses</Link>
        </div>
      ) : (
        <div className="py-10">
          <p className="mb-8 text-sm text-ink-soft">{results.length} {results.length === 1 ? 'result' : 'results'} for “{q}”</p>
          <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {results.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </div>
      )}
    </div>
  )
}

export default function SearchPage() {
  return (
    <Suspense fallback={<div className="container-luxe py-24 text-center text-ink-soft">Loading…</div>}>
      <SearchInner />
    </Suspense>
  )
}
