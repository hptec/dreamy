import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { fetchStoreBlogPreview } from '@/lib/api/marketing-server'
import { Eyebrow } from '@/components/ui/primitives'
import { BlogPostBody } from '@/components/blog/BlogPostBody'
import { formatDateTimeLong } from '@/lib/utils'

/**
 * /blog/preview/[token]（2026-08-20 新增）
 * - 草稿预览:凭后端签发的 UUID v4 token 访问,不校验文章 status
 * - force-dynamic + no-store:绝不缓存
 * - X-Robots-Tag noindex/nofollow:防搜索引擎索引
 * - 顶部 Banner 提示 + 过期时间显示
 */

export const dynamic = 'force-dynamic'
export const fetchCache = 'force-no-store'

export async function generateMetadata({ params }: { params: Promise<{ token: string }> }): Promise<Metadata> {
  await params // 消费参数避免未用警告
  return {
    title: 'Blog Preview',
    robots: { index: false, follow: false },
  }
}

export default async function BlogPreviewPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params
  const result = await fetchStoreBlogPreview(token)
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
