import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { getBlogPost, blogPosts } from '@/data/content'
import { products } from '@/data/products'
import { ProductCard } from '@/components/product/product-card'
import { Eyebrow } from '@/components/ui/primitives'

export function generateStaticParams() {
  return blogPosts.map((b) => ({ slug: b.slug }))
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  const post = getBlogPost(slug)
  if (!post) return { title: 'Post Not Found' }
  return { title: post.title, description: post.excerpt }
}

export default async function BlogPostPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const post = getBlogPost(slug)
  if (!post) notFound()
  const related = blogPosts.filter((b) => b.slug !== slug).slice(0, 2)
  const embed = products.filter((p) => p.isBestSeller).slice(0, 3)

  return (
    <div>
      <article className="container-luxe max-w-3xl py-12">
        <Link href="/blog" className="text-sm text-gold-deep underline">← Back to blog</Link>
        <Eyebrow className="mt-6">{post.category} · {post.readMinutes} min read</Eyebrow>
        <h1 className="mt-2 font-display text-4xl font-medium leading-tight lg:text-5xl">{post.title}</h1>
        <p className="mt-3 text-sm text-ink-faint">{post.author} · {post.date}</p>
        <div className="mt-8 aspect-[16/9] overflow-hidden rounded-sm">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={post.cover} alt={post.title} className="h-full w-full object-cover" />
        </div>
        <div className="mt-8 space-y-5 text-lg leading-relaxed text-ink-soft">
          {post.body.map((p, i) => <p key={i}>{p}</p>)}
        </div>

        {/* Embedded shop */}
        <div className="mt-12 rounded-sm bg-muted p-6">
          <Eyebrow className="mb-4">Shop this story</Eyebrow>
          <div className="grid grid-cols-3 gap-4">
            {embed.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </div>
      </article>

      <section className="bg-muted py-16">
        <div className="container-luxe">
          <h2 className="mb-8 font-display text-2xl font-medium">Keep reading</h2>
          <div className="grid gap-8 sm:grid-cols-2">
            {related.map((post) => (
              <Link key={post.id} href={`/blog/${post.slug}`} className="group grid grid-cols-[120px_1fr] gap-4">
                <div className="aspect-square overflow-hidden rounded-sm">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={post.cover} alt={post.title} className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
                </div>
                <div>
                  <p className="eyebrow">{post.category}</p>
                  <h3 className="mt-1 font-display text-lg font-medium leading-snug">{post.title}</h3>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}
