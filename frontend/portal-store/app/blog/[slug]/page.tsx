import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { fetchStoreBlog, fetchStoreBlogs } from '@/lib/api/marketing-server'
import { Eyebrow } from '@/components/ui/primitives'
import { formatDateTimeLong } from '@/lib/utils'

/**
 * /blog/[slug]（PAGE-MKT-S04）：generateStaticParams 删除 → dynamicParams=true + revalidate=300；
 * 404701 → notFound()；content 单字符串按换行 split 渲染段落（COMP-MKT-S05 data-swap 标注）；
 * seo_title/seo_description → generateMetadata。
 */

export const revalidate = 300
export const dynamicParams = true

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  const { data: post } = await fetchStoreBlog(slug)
  if (!post) return { title: 'Post Not Found' }
  return { title: post.seoTitle ?? post.title, description: post.seoDescription ?? post.excerpt }
}

export default async function BlogPostPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const { data: post } = await fetchStoreBlog(slug)
  if (!post) notFound()

  const relatedPage = await fetchStoreBlogs({ page: 1, pageSize: 4 })
  const related = (relatedPage?.data ?? []).filter((b) => b.slug !== slug).slice(0, 2)
  const paragraphs = post.content.split(/\n+/).filter((p) => p.trim().length > 0)

  return (
    <div>
      <article className="container-luxe max-w-3xl py-12">
        <Link href="/blog" className="text-sm text-gold-deep underline">← Back to blog</Link>
        <Eyebrow className="mt-6">{post.category}</Eyebrow>
        <h1 className="mt-2 font-display text-4xl font-medium leading-tight lg:text-5xl">{post.title}</h1>
        <p className="mt-3 text-sm text-ink-faint">{[post.author, formatDateTimeLong(post.publishedAt)].filter(Boolean).join(' · ')}</p>
        {post.cover && (
          <div className="mt-8 aspect-[16/9] overflow-hidden rounded-sm">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={post.cover} alt={post.title} className="h-full w-full object-cover" />
          </div>
        )}
        <div className="mt-8 space-y-5 text-lg leading-relaxed text-ink-soft">
          {paragraphs.map((p, i) => <p key={i}>{p}</p>)}
        </div>
      </article>

      {related.length > 0 && (
        <section className="bg-muted py-16">
          <div className="container-luxe">
            <h2 className="mb-8 font-display text-2xl font-medium">Keep reading</h2>
            <div className="grid gap-8 sm:grid-cols-2">
              {related.map((p) => (
                <Link key={p.id} href={`/blog/${p.slug}`} className="group grid grid-cols-[120px_1fr] gap-4">
                  <div className="aspect-square overflow-hidden rounded-sm bg-canvas">
                    {p.cover && (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={p.cover} alt={p.title} className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
                    )}
                  </div>
                  <div>
                    <p className="eyebrow">{p.category}</p>
                    <h3 className="mt-1 font-display text-lg font-medium leading-snug">{p.title}</h3>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  )
}
