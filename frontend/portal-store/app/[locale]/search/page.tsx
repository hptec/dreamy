'use client'

/**
 * /search（PAGE-CAT-S03，layout-keep + data-swap）：
 * 内存 filter → E-CAT-02 全文搜索（客户端 fetch，后端 JetCache 60s）。
 * URL ?q= 同步，防抖 350ms（FORM-CAT-S02：q trim 为空不请求；空结果空态不报错）。
 */

import { useState, useEffect, useRef, Suspense } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { Search } from 'lucide-react'
import type { Paginated, StoreProductCard } from '@/lib/api/store-types'
import { searchStoreProducts } from '@/lib/api/catalog-api'
import { ProductCard } from '@/components/product/product-card'
import { Eyebrow } from '@/components/ui/primitives'
import { useI18n } from '@/lib/i18n/i18n-context'

function SearchInner() {
  const params = useSearchParams()
  const router = useRouter()
  const { te } = useI18n()
  const initial = params.get('q') ?? ''
  const [q, setQ] = useState(initial)
  const [result, setResult] = useState<Paginated<StoreProductCard> | null>(null)
  const [items, setItems] = useState<StoreProductCard[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const requestSeq = useRef(0)

  const runSearch = (term: string, page = 1, append = false) => {
    const seq = ++requestSeq.current
    setLoading(true)
    setError(null)
    searchStoreProducts(term, page)
      .then((res) => {
        if (seq !== requestSeq.current) return
        setResult(res)
        setItems((prev) => (append ? [...prev, ...res.data] : res.data))
      })
      .catch(() => {
        if (seq !== requestSeq.current) return
        setError(te(50000))
      })
      .finally(() => {
        if (seq === requestSeq.current) setLoading(false)
      })
  }

  // 防抖 350ms + URL 同步
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    const term = q.trim()
    debounceRef.current = setTimeout(() => {
      const sp = new URLSearchParams()
      if (term) sp.set('q', term)
      router.replace(term ? `/search?${sp.toString()}` : '/search')
      if (!term) {
        requestSeq.current += 1
        setResult(null)
        setItems([])
        return
      }
      runSearch(term)
    }, 350)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q])

  const suggestions = ['Sage', 'A-Line', 'Beach', 'Bridesmaid', 'Veil', 'Lace']
  const total = result?.totalElements ?? 0
  const canLoadMore = result !== null && items.length < total

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
      ) : error ? (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <p className="font-display text-3xl">Something went wrong</p>
          <p className="text-ink-soft">{error}</p>
          <button onClick={() => runSearch(q.trim())} className="btn-primary">Try Again</button>
        </div>
      ) : loading && items.length === 0 ? (
        <div className="grid grid-cols-2 gap-x-5 gap-y-10 py-10 sm:gap-x-6 lg:grid-cols-4" aria-hidden="true">
          {[0, 1, 2, 3].map((i) => <div key={i} className="aspect-[3/4] animate-pulse rounded-sm bg-muted" />)}
        </div>
      ) : result !== null && items.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-24 text-center">
          <p className="font-display text-3xl">No results for “{q}”</p>
          <p className="text-ink-soft">Try a color, silhouette, or occasion — or browse our collections.</p>
          <Link href="/wedding-dresses" className="btn-primary">Browse Dresses</Link>
        </div>
      ) : result !== null ? (
        <div className="py-10">
          <p className="mb-8 text-sm text-ink-soft">{total} {total === 1 ? 'result' : 'results'} for “{q}”</p>
          <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {items.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
          {canLoadMore && (
            <div className="mt-10 text-center">
              <button
                onClick={() => runSearch(q.trim(), (result?.pageNumber ?? 1) + 1, true)}
                disabled={loading}
                className="btn-outline disabled:opacity-60"
              >
                {loading ? 'Loading…' : 'Load more'}
              </button>
            </div>
          )}
        </div>
      ) : null}
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
