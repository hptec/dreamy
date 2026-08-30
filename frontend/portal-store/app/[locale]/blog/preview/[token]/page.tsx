import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { fetchStoreBlogPreview } from '@/lib/api/marketing-server'
import { Eyebrow } from '@/components/ui/primitives'
import { BlogPostBody } from '@/components/blog/BlogPostBody'
import { formatDateTimeLong } from '@/lib/utils'
import type { Locale } from '@/lib/api/types'

/**
 * /blog/preview/[token]（2026-08-20 新增）
 * - 草稿预览:凭后端签发的 UUID v4 token 访问,不校验文章 status
 * - force-dynamic + no-store:绝不缓存
 * - X-Robots-Tag noindex/nofollow:防搜索引擎索引
 * - 顶部 Banner 提示 + 过期时间显示
 * - 2026-08-28: 透传 ?locale=es|fr 给后端,管理员可在 admin 抽屉切到 ES/FR tab 后点预览直接看对应译文
 */

export const dynamic = 'force-dynamic'
export const fetchCache = 'force-no-store'

type PageParams = { locale: string; token: string }
type PageSearch = { locale?: string }

export async function generateMetadata({ params }: { params: Promise<PageParams> }): Promise<Metadata> {
  await params // 消费参数避免未用警告
  return {
    title: 'Blog Preview',
    robots: { index: false, follow: false },
  }
}

export default async function BlogPreviewPage({
  params,
  searchParams,
}: {
  params: Promise<PageParams>
  searchParams: Promise<PageSearch>
}) {
  const [{ locale: pathLocale, token }, sp] = await Promise.all([params, searchParams])
  // 优先 query locale（admin 预览当前 tab 语言）；fallback 路径 locale（手动分享场景）
  const rawLocale = sp.locale ?? pathLocale
  const activeLocale: Locale = rawLocale === 'es' || rawLocale === 'fr' ? rawLocale : 'en'
  const result = await fetchStoreBlogPreview(token, activeLocale)
  if (!result.data) notFound()

  const post = result.data

  return (
    <div>
      {/* 预览模式 Banner */}
      <div className="sticky top-0 z-50 border-b border-amber-300 bg-amber-50 py-3 text-center text-sm font-medium text-amber-900">
        Preview Mode · 此为草稿预览，链接 4 小时后过期 · 请勿分享
      </div>

      <article className="container-luxe max-w-3xl py-12">
        <Eyebrow className="mt-6">{post.category}</Eyebrow>
        <h1 className="mt-2 font-display text-4xl font-medium leading-tight lg:text-5xl">{post.title}</h1>
        <p className="mt-3 text-sm text-ink-faint">
          {[post.author, post.publishedAt ? formatDateTimeLong(post.publishedAt) : '未发布'].filter(Boolean).join(' · ')}
        </p>
        {post.cover && (
          <div className="mt-8 aspect-[16/9] overflow-hidden rounded-sm">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={post.cover} alt={post.title} className="h-full w-full object-cover" />
          </div>
        )}
        <BlogPostBody content={post.content} />
      </article>
    </div>
  )
}
