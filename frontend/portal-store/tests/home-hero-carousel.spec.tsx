import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { HomeHeroCarousel } from '@/components/marketing/home-hero-carousel'
import type { StoreHeroSlide } from '@/lib/api/site-builder-server'

function renderHero(title: StoreHeroSlide['title']) {
  return renderToStaticMarkup(
    <HomeHeroCarousel slides={[{ id: 1, imageUrl: '/hero.jpg', title }]} />,
  )
}

describe('HomeHeroCarousel title', () => {
  it.each([null, '', '   '])('does not render an h1 for an empty title (%j)', (title) => {
    const markup = renderHero(title)

    expect(markup).not.toContain('<h1')
    expect(markup).not.toContain('Dresses made for golden hour')
  })

  it('renders an h1 only when the slide has a real title', () => {
    const markup = renderHero('  Summer ceremony  ')

    expect(markup).toContain('<h1')
    expect(markup).toContain('Summer ceremony</h1>')
  })
})
