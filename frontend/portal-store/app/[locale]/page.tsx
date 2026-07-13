import Link from 'next/link'
import { fetchStoreHome } from '@/lib/api/site-builder-server'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'
import { HomeNewsletterForm } from './home-newsletter-form'
import type { Locale } from '@/lib/api/types'

export const dynamic = 'force-dynamic'

const SUPPORTED_LOCALES: Locale[] = ['en', 'es', 'fr']

export default async function HomePage({
  params,
}: {
  params: Promise<{ locale: string }>
}) {
  const { locale } = await params
  const activeLocale: Locale = SUPPORTED_LOCALES.includes(locale as Locale)
    ? locale as Locale
    : 'en'
  const homePage = await fetchStoreHome(activeLocale)
  const homeSections = homePage?.sections ?? []

  return (
    <main>
      {homeSections.map((section, index) => {
        const key = `${section.sectionType}-${index}`

        switch (section.sectionType) {
          case 'hero': {
            const { data } = section
            if (!data.imageUrl) return null

            const title = data.title || 'Dresses made for golden hour'
            const subtitle = data.subtitle || null
            const primaryLink = data.ctaLink || null
            const primaryText = data.ctaText || null

            return (
              <section key={key} className="relative grid min-h-[600px] lg:grid-cols-2">
                <div className="order-2 flex items-center bg-canvas px-6 py-14 lg:order-1 lg:px-16">
                  <div className="max-w-lg animate-fadeup">
                    <h1 className="break-words font-display text-5xl font-medium leading-[1.02] text-ink sm:text-6xl lg:text-[4.25rem]">
                      {title}
                    </h1>
                    {subtitle && <p className="mt-5 max-w-md text-ink-soft">{subtitle}</p>}
                    {(primaryLink && primaryText) || (data.ctaLinkSecondary && data.ctaTextSecondary) ? (
                      <div className="mt-8 flex flex-wrap gap-3">
                        {primaryLink && primaryText && (
                          <Link href={primaryLink} className="btn-primary">
                            {primaryText}
                          </Link>
                        )}
                        {data.ctaLinkSecondary && data.ctaTextSecondary && (
                          <Link href={data.ctaLinkSecondary} className="btn-outline">
                            {data.ctaTextSecondary}
                          </Link>
                        )}
                      </div>
                    ) : null}
                  </div>
                </div>
                <div className="relative order-1 min-h-[420px] overflow-hidden lg:order-2">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={data.imageUrl}
                    alt={data.title || 'Dreamy bridal collection'}
                    className="absolute inset-0 h-full w-full animate-kenburns object-cover object-top"
                  />
                </div>
              </section>
            )
          }

          case 'themeCards': {
            const { data } = section
            const cards = data.cards ?? []
            if (cards.length === 0) return null

            return (
              <section key={key} className="container-luxe py-16 lg:py-24">
                <SectionHeading
                  eyebrow={data.eyebrow || 'Explore'}
                  title={data.heading || 'Shop by Theme'}
                  description={data.description || undefined}
                />
                <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  {cards.map((card) => (
                    <Link
                      key={card.id}
                      href={`/search?q=${encodeURIComponent(card.name)}`}
                      className="group relative aspect-[3/4] overflow-hidden rounded-sm bg-muted"
                    >
                      {card.imageUrl && (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={card.imageUrl}
                          alt=""
                          className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105"
                        />
                      )}
                      <div className="absolute inset-0 bg-gradient-to-t from-ink/70 via-ink/10 to-transparent" />
                      <div className="absolute inset-x-0 bottom-0 p-5 text-canvas">
                        <h3 className="break-words font-display text-2xl font-medium">{card.name}</h3>
                        {typeof card.productCount === 'number' && card.productCount > 0 && (
                          <p className="text-xs text-canvas/80">{card.productCount} styles</p>
                        )}
                      </div>
                    </Link>
                  ))}
                </div>
              </section>
            )
          }

          case 'productRail': {
            const { data } = section
            const products = data.products ?? []
            if (products.length === 0) return null

            return (
              <section key={key} className="container-luxe py-16 lg:py-24">
                <SectionHeading
                  eyebrow={data.eyebrow || undefined}
                  title={data.heading || 'Featured Products'}
                  description={data.description || undefined}
                />
                <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
                  {products.map((product) => (
                    <Link key={product.id} href={`/product/${product.slug}`} className="group min-w-0">
                      <div className="aspect-[3/4] overflow-hidden rounded-sm bg-muted">
                        {product.imageUrl && (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={product.imageUrl}
                            alt={product.name}
                            className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105"
                          />
                        )}
                      </div>
                      <div className="mt-3">
                        <h3 className="break-words font-medium">{product.name}</h3>
                        <p className="mt-1 text-sm text-ink-soft">${product.price}</p>
                      </div>
                    </Link>
                  ))}
                </div>
              </section>
            )
          }

          case 'editorialFeature': {
            const { data } = section
            const stories = data.stories ?? []
            if (stories.length === 0) return null

            return (
              <section key={key} className="bg-muted py-16 lg:py-24">
                <div className="container-luxe">
                  <SectionHeading
                    eyebrow={data.eyebrow || 'Real love stories'}
                    title={data.heading || 'Real Weddings'}
                    description={data.description || undefined}
                  />
                  <div className="mt-10 grid gap-6 lg:grid-cols-3">
                    {stories.map((story) => (
                      <Link key={story.id} href={`/real-weddings/${story.id}`} className="group min-w-0">
                        <div className="aspect-[4/3] overflow-hidden rounded-sm bg-canvas">
                          {story.cover && (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img
                              src={story.cover}
                              alt={story.couple ? `${story.couple} wedding` : 'Real wedding'}
                              className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105"
                            />
                          )}
                        </div>
                        {(story.theme || story.location) && (
                          <p className="eyebrow mt-4 break-words">
                            {[story.theme, story.location].filter(Boolean).join(' / ')}
                          </p>
                        )}
                        <h3 className="mt-1 break-words font-display text-2xl font-medium">{story.couple}</h3>
                        {story.title && <p className="mt-1 line-clamp-2 text-sm text-ink-soft">{story.title}</p>}
                      </Link>
                    ))}
                  </div>
                </div>
              </section>
            )
          }

          case 'newsletter': {
            const { data } = section
            return (
              <section key={key} className="container-luxe py-16 lg:py-24">
                <div className="mx-auto max-w-2xl text-center">
                  <Eyebrow>{data.eyebrow || 'Stay in touch'}</Eyebrow>
                  <h2 className="mt-3 break-words font-display text-4xl font-medium">
                    {data.heading || 'Join the Dreamy List'}
                  </h2>
                  {data.description && <p className="mt-4 text-ink-soft">{data.description}</p>}
                  <HomeNewsletterForm
                    locale={activeLocale}
                    placeholder={data.placeholder}
                    cta={data.cta}
                  />
                </div>
              </section>
            )
          }

          case 'custom': {
            const { data } = section
            return (
              <section key={key} className="container-luxe py-16 lg:py-24">
                <div className={data.imageUrl
                  ? 'grid items-center gap-10 lg:grid-cols-2'
                  : 'mx-auto max-w-3xl text-center'}>
                  {data.imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={data.imageUrl}
                      alt={data.heading || ''}
                      className="aspect-[16/10] w-full rounded-sm object-cover"
                    />
                  )}
                  <div className="min-w-0">
                    {data.heading && <h2 className="break-words font-display text-4xl font-medium">{data.heading}</h2>}
                    {data.subtitle && <p className="mt-3 break-words text-lg text-ink-soft">{data.subtitle}</p>}
                    {data.content && <p className="mt-5 whitespace-pre-line break-words text-ink-soft">{data.content}</p>}
                    {data.ctaText && data.ctaLink && (
                      <Link href={data.ctaLink} className="btn-primary mt-7">
                        {data.ctaText}
                      </Link>
                    )}
                  </div>
                </div>
              </section>
            )
          }
        }
      })}
    </main>
  )
}
