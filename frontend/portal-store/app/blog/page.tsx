import type { Metadata } from 'next'
import Link from 'next/link'
import { fetchStoreBlogs } from '@/lib/api/marketing-server'
import { SectionHeading } from '@/components/ui/primitives'
import { formatDateTimeLong } from '@/lib/utils'

/** /blog（PAGE-MKT-S03，layout-keep + data-swap）：E-MKT-02；?page= searchParams 分页驱动。 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Wedding Blog',
  description: 'Outdoor wedding planning tips, fabric guides, and color inspiration from the Dreamy team.'
}

export default async function BlogPage({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
  const sp = await searchParams
  const page = Math.max(1, Number(sp.page ?? '1') || 1)
  const result = await fetchStoreBlogs({ page, pageSize: 9 })
  const posts = result?.data ?? []
  const totalPages = result?.totalPages ?? 1

  if (posts.length === 0) {
    return (
      <div className="container-luxe py-12">
        <SectionHeading eyebrow="The Journal" title="Wedding Blog" description="Planning tips, fabric guides, and outdoor wedding inspiration." />
        <p className="py-24 text-center text-ink-soft">New stories are on the way — check back soon.</p>
      </div>
    )
  }

  const [featured, ...rest] = posts

  return (
    <div className="container-luxe py-12">
      <SectionHeading eyebrow="The Journal" title="Wedding Blog" description="Planning tips, fabric guides, and outdoor wedding inspiration." />

      <Link href={`/blog/${featured.slug}`} className="group mt-12 grid items-center gap-8 lg:grid-cols-2">
        <div className="aspect-[4/3] overflow-hidden rounded-sm bg-muted">
          {featured.cover && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={featured.cover} alt={featured.title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
          )}
        </div>
        <div>
          <p className="eyebrow">{featured.category}</p>
          <h2 className="mt-2 font-display text-4xl font-medium leading-tight">{featured.title}</h2>
          {featured.excerpt && <p className="mt-3 text-ink-soft">{featured.excerpt}</p>}
          <p className="mt-4 text-xs text-ink-faint">{[featured.author, formatDateTimeLong(featured.publishedAt)].filter(Boolean).join(' · ')}</p>
        </div>
      </Link>

      <div className="mt-16 grid gap-8 lg:grid-cols-2">
        {rest.map((post) => (
          <Link key={post.id} href={`/blog/${post.slug}`} className="group grid grid-cols-[140px_1fr] gap-5">
            <div className="aspect-square overflow-hidden rounded-sm bg-muted">
              {post.cover && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={post.cover} alt={post.title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
              )}
            </div>
            <div>
              <p className="eyebrow">{post.category}</p>
              <h3 className="mt-1 font-display text-xl font-medium leading-snug">{post.title}</h3>
              {post.excerpt && <p className="mt-2 text-sm text-ink-soft line-clamp-2">{post.excerpt}</p>}
              <p className="mt-2 text-xs text-ink-faint">{formatDateTimeLong(post.publishedAt)}</p>
            </div>
          </Link>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="mt-12 flex items-center justify-center gap-4 text-sm">
          {page > 1 ? (
            <Link href={page === 2 ? '/blog' : `/blog?page=${page - 1}`} className="btn-outline px-5 py-2 text-xs">Previous</Link>
          ) : (
            <span className="btn-outline cursor-not-allowed px-5 py-2 text-xs opacity-40">Previous</span>
          )}
          <span className="text-ink-soft">Page {page} of {totalPages}</span>
          {page < totalPages ? (
            <Link href={`/blog?page=${page + 1}`} className="btn-outline px-5 py-2 text-xs">Next</Link>
          ) : (
            <span className="btn-outline cursor-not-allowed px-5 py-2 text-xs opacity-40">Next</span>
          )}
        </div>
      )}
    </div>
  )
}
