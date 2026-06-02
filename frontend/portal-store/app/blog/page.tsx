import type { Metadata } from 'next'
import Link from 'next/link'
import { blogPosts } from '@/data/content'
import { SectionHeading } from '@/components/ui/primitives'

export const metadata: Metadata = {
  title: 'Wedding Blog',
  description: 'Outdoor wedding planning tips, fabric guides, and color inspiration from the Dreamy team.'
}

export default function BlogPage() {
  const [featured, ...rest] = blogPosts
  return (
    <div className="container-luxe py-12">
      <SectionHeading eyebrow="The Journal" title="Wedding Blog" description="Planning tips, fabric guides, and outdoor wedding inspiration." />

      <Link href={`/blog/${featured.slug}`} className="group mt-12 grid items-center gap-8 lg:grid-cols-2">
        <div className="aspect-[4/3] overflow-hidden rounded-sm">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={featured.cover} alt={featured.title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
        </div>
        <div>
          <p className="eyebrow">{featured.category} · {featured.readMinutes} min read</p>
          <h2 className="mt-2 font-display text-4xl font-medium leading-tight">{featured.title}</h2>
          <p className="mt-3 text-ink-soft">{featured.excerpt}</p>
          <p className="mt-4 text-xs text-ink-faint">{featured.author} · {featured.date}</p>
        </div>
      </Link>

      <div className="mt-16 grid gap-8 lg:grid-cols-2">
        {rest.map((post) => (
          <Link key={post.id} href={`/blog/${post.slug}`} className="group grid grid-cols-[140px_1fr] gap-5">
            <div className="aspect-square overflow-hidden rounded-sm">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={post.cover} alt={post.title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
            </div>
            <div>
              <p className="eyebrow">{post.category}</p>
              <h3 className="mt-1 font-display text-xl font-medium leading-snug">{post.title}</h3>
              <p className="mt-2 text-sm text-ink-soft line-clamp-2">{post.excerpt}</p>
              <p className="mt-2 text-xs text-ink-faint">{post.date} · {post.readMinutes} min</p>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
