import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { HomeHeroCarousel } from '@/components/marketing/home-hero-carousel'
import type { StoreHeroSlide } from '@/lib/api/site-builder-server'

const AUTO_PLAY_MS = 6500
const slides: StoreHeroSlide[] = [
  { id: 1, imageUrl: '/first.jpg', title: 'First slide' },
  { id: 2, imageUrl: '/second.jpg', title: 'Second slide' },
]

let root: Root | null = null
let container: HTMLDivElement | null = null
const actEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }

function installMatchMedia(matches: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
}

function renderCarousel(items: StoreHeroSlide[] = slides) {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root?.render(<HomeHeroCarousel slides={items} />))
  return container
}

function section() {
  const element = container?.querySelector<HTMLElement>('section[aria-roledescription="carousel"]')
  if (!element) throw new Error('carousel section not found')
  return element
}

function button(label: string) {
  const element = container?.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)
  if (!element) throw new Error(`button not found: ${label}`)
  return element
}

function heading() {
  return container?.querySelector('h1')?.textContent
}

function click(element: HTMLElement) {
  act(() => element.dispatchEvent(new MouseEvent('click', { bubbles: true })))
}

function advance(ms = AUTO_PLAY_MS) {
  act(() => vi.advanceTimersByTime(ms))
}

beforeAll(() => {
  actEnvironment.IS_REACT_ACT_ENVIRONMENT = true
})

afterAll(() => {
  delete actEnvironment.IS_REACT_ACT_ENVIRONMENT
})

beforeEach(() => {
  vi.useFakeTimers()
  installMatchMedia(false)
})

afterEach(() => {
  if (root) act(() => root?.unmount())
  container?.remove()
  root = null
  container = null
  vi.clearAllTimers()
  vi.useRealTimers()
})

describe('HomeHeroCarousel interactions', () => {
  it('automatically rotates two banners', () => {
    renderCarousel()

    expect(heading()).toBe('First slide')
    expect(button('Pause slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('Second slide')
  })

  it('supports previous/next controls and stops automatic rotation after manual navigation', () => {
    renderCarousel()

    click(button('Next slide'))
    expect(heading()).toBe('Second slide')
    expect(button('Resume slideshow')).toBeTruthy()

    click(button('Previous slide'))
    expect(heading()).toBe('First slide')
    advance()
    expect(heading()).toBe('First slide')
  })

  it('pauses and explicitly resumes with matching button labels', () => {
    renderCarousel()

    click(button('Pause slideshow'))
    expect(button('Resume slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('First slide')

    click(button('Resume slideshow'))
    expect(button('Pause slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('Second slide')
  })

  it('stops when focus enters and explicit resume remains effective while focus stays inside', () => {
    renderCarousel()

    act(() => button('Next slide').focus())
    expect(button('Resume slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('First slide')

    click(button('Resume slideshow'))
    expect(button('Pause slideshow')).toBeTruthy()
    act(() => button('Previous slide').focus())
    expect(button('Pause slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('Second slide')
  })

  it('stops on hover until the user explicitly resumes', () => {
    renderCarousel()

    act(() => section().dispatchEvent(new MouseEvent('mouseover', { bubbles: true })))
    expect(button('Resume slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('First slide')

    click(button('Resume slideshow'))
    advance()
    expect(heading()).toBe('Second slide')
  })

  it('supports ArrowLeft and ArrowRight and leaves automatic rotation paused', () => {
    renderCarousel()
    const right = new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true })
    const left = new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true, cancelable: true })

    act(() => section().dispatchEvent(right))
    expect(heading()).toBe('Second slide')
    expect(right.defaultPrevented).toBe(true)
    act(() => section().dispatchEvent(left))
    expect(heading()).toBe('First slide')
    expect(left.defaultPrevented).toBe(true)
    expect(button('Resume slideshow')).toBeTruthy()
  })

  it('supports touch swipes and pauses automatic rotation', () => {
    renderCarousel()
    const start = new Event('touchstart', { bubbles: true })
    const end = new Event('touchend', { bubbles: true })
    Object.defineProperty(start, 'touches', { value: [{ clientX: 180 }] })
    Object.defineProperty(end, 'changedTouches', { value: [{ clientX: 80 }] })

    act(() => section().dispatchEvent(start))
    act(() => section().dispatchEvent(end))

    expect(heading()).toBe('Second slide')
    expect(button('Resume slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('Second slide')
  })

  it('honors reduced motion by default and removes opacity transitions from every slide', () => {
    installMatchMedia(true)
    const rendered = renderCarousel()

    for (const image of rendered.querySelectorAll('img')) {
      expect(image.className).toContain('motion-reduce:transition-none')
    }
    expect(button('Resume slideshow')).toBeTruthy()
    advance(AUTO_PLAY_MS * 2)
    expect(heading()).toBe('First slide')

    click(button('Resume slideshow'))
    expect(button('Pause slideshow')).toBeTruthy()
    advance()
    expect(heading()).toBe('Second slide')
  })

  it('does not render rotation controls or schedule rotation for one banner', () => {
    const rendered = renderCarousel([slides[0]])

    expect(rendered.querySelector('[aria-label="Previous slide"]')).toBeNull()
    expect(rendered.querySelector('[aria-label="Next slide"]')).toBeNull()
    expect(rendered.querySelector('[aria-label="Pause slideshow"]')).toBeNull()
    expect(rendered.querySelector('[aria-label="Resume slideshow"]')).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
    advance(AUTO_PLAY_MS * 2)
    expect(heading()).toBe('First slide')
  })
})
