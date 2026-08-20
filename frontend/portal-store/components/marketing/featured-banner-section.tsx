import Link from 'next/link'
import type { StoreHeroSlide } from '@/lib/api/site-builder-server'

export function FeaturedBannerSection({ banners }: { banners: StoreHeroSlide[] }) {
  const visibleBanners = banners.filter((banner) => Boolean(banner.imageUrl))
  if (visibleBanners.length === 0) return null

  return (
    <section className="border-y border-line bg-muted py-10 sm:py-14">
      <div className="container-luxe grid gap-5 lg:grid-cols-2">
        {visibleBanners.map((banner, index) => {
          const title = banner.title?.trim() || 'Featured collection'
          const href = banner.ctaLink || '/special-occasion'
          return (
            <article key={banner.id ?? `${banner.imageUrl}-${index}`} className="group grid overflow-hidden bg-canvas sm:grid-cols-2">
              <Link href={href} className="aspect-[4/3] overflow-hidden bg-line sm:aspect-auto">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={banner.imageUrl ?? ''} alt={title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
              </Link>
              <div className="flex min-w-0 flex-col items-start justify-center p-7 sm:p-8">
                <p className="eyebrow">Featured edit</p>
                <h2 className="mt-3 break-words font-display text-3xl font-medium text-ink">{title}</h2>
                {banner.subtitle && <p className="mt-3 text-sm leading-6 text-ink-soft">{banner.subtitle}</p>}
                <div className="mt-6">
                  <Link
                    href={href}
                    className="btn-primary"
                  >
                    {banner.ctaText || 'Explore now'}
                  </Link>
                </div>
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}
