import type { Metadata } from 'next'
import Link from 'next/link'
import { fetchStoreBlogs } from '@/lib/api/marketing-server'
import { SectionHeading } from '@/components/ui/primitives'
import { formatDateTimeLong } from '@/lib/utils'
import { getMessages } from '@/lib/i18n/messages'
import { buildAlternates } from '@/lib/i18n/seo'
import type { Locale } from '@/lib/api/types'

/** /blog（PAGE-MKT-S03，layout-keep + data-swap）：E-MKT-02；?page= searchParams 分页驱动。
 *  2026-08-28: 输出 alternates.languages(hreflang en/es/fr/x-default),对齐三语列表页。 */

export const dynamic = 'force-dynamic'

type PageParams = { locale: string }
type PageSearch = { page?: string }

export async function generateMetadata({ params }: { params: Promise<PageParams> }): Promise<Metadata> {
  const { locale } = await params
  const activeLocale = (locale as Locale) ?? 'en'
  const t = getMessages(activeLocale).blog
  return {
    title: t.title,
    description: t.description,
    alternates: buildAlternates('/blog', activeLocale),
  }
}

export default async function BlogPage({
  params,
  searchParams
}: {
  params: Promise<PageParams>
  searchParams: Promise<PageSearch>
}) {
  const [{ locale }, sp] = await Promise.all([params, searchParams])
  const activeLocale = (locale as Locale) ?? 'en'
  const t = getMessages(activeLocale).blog
  const page = Math.max(1, Number(sp.page ?? '1') || 1)
  const result = await fetchStoreBlogs({ page, pageSize: 9, locale: activeLocale })
  const posts = result?.data ?? []
  const totalPages = result?.totalPages ?? 1
  const pageOfText = t.pageOf.replace('{page}', String(page)).replace('{total}', String(totalPages))

  if (posts.length === 0) {
    return (
      <div className="container-luxe py-12">
        <SectionHeading eyebrow={t.eyebrow} title={t.title} description={t.description} />
        <p className="py-24 text-center text-ink-soft">{t.empty}</p>
      </div>
    )
  }

  // 3 列大图卡片流（4:5 画幅 + Cormorant 标题 + 分类 eyebrow + hover 轻缩放；数据结构不变）
  return (
    <div className="container-luxe py-12">
      <SectionHeading eyebrow={t.eyebrow} title={t.title} description={t.description} />

      <div className="mt-12 grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
        {posts.map((post) => (
          <Link key={post.id} href={`/blog/${post.slug}`} className="group min-w-0">
            <div className="aspect-[4/5] overflow-hidden rounded-sm bg-muted">
              {post.cover && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={post.cover} alt={post.title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
              )}
            </div>
            {post.category && <p className="eyebrow mt-4">{post.category}</p>}
            <h3 className="mt-1.5 break-words font-display text-2xl font-medium leading-snug">{post.title}</h3>
            {post.excerpt && <p className="mt-2 line-clamp-2 text-sm text-ink-soft">{post.excerpt}</p>}
            <p className="mt-2.5 text-xs text-ink-faint">{[post.author, formatDateTimeLong(post.publishedAt)].filter(Boolean).join(' · ')}</p>
          </Link>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="mt-12 flex items-center justify-center gap-4 text-sm">
          {page > 1 ? (
            <Link href={page === 2 ? '/blog' : `/blog?page=${page - 1}`} className="btn-outline px-5 py-2 text-xs">{t.previous}</Link>
          ) : (
            <span className="btn-outline cursor-not-allowed px-5 py-2 text-xs opacity-40">{t.previous}</span>
          )}
          <span className="text-ink-soft">{pageOfText}</span>
          {page < totalPages ? (
            <Link href={`/blog?page=${page + 1}`} className="btn-outline px-5 py-2 text-xs">{t.next}</Link>
          ) : (
            <span className="btn-outline cursor-not-allowed px-5 py-2 text-xs opacity-40">{t.next}</span>
          )}
        </div>
      )}
    </div>
  )
}
