import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { FeaturedBannerSection } from '@/components/marketing/featured-banner-section'

describe('FeaturedBannerSection', () => {
  it('does not render an empty activity section', () => {
    expect(renderToStaticMarkup(<FeaturedBannerSection banners={[]} />)).toBe('')
  })

  it('uses the banner content and CTA configured in Banner management', () => {
    const markup = renderToStaticMarkup(
      <FeaturedBannerSection banners={[{
        id: 4,
        imageUrl: '/spring-sale.jpg',
        title: 'Spring Sale',
        subtitle: 'Up to 30% off selected styles',
        ctaText: 'Shop Sale',
        ctaLink: '/special-occasion',
      }]} />,
    )

    expect(markup).toContain('Spring Sale')
    expect(markup).toContain('Up to 30% off selected styles')
    expect(markup).toContain('href="/special-occasion"')
  })

  it('does not render a secondary CTA configured on a featured banner', () => {
    const markup = renderToStaticMarkup(
      <FeaturedBannerSection banners={[{
        id: 4,
        imageUrl: '/spring-sale.jpg',
        title: 'Spring Sale',
        ctaTextSecondary: 'View offers',
        ctaLinkSecondary: 'https://example.com/offers',
      }]} />,
    )

    expect(markup).not.toContain('View offers')
    expect(markup).not.toContain('https://example.com/offers')
  })
})
