'use client'

import Link from 'next/link'
import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { StoreHeroSlide } from '@/lib/api/site-builder-server'

const AUTO_PLAY_MS = 6500
const SWIPE_THRESHOLD_PX = 48
type RotationMode = 'auto' | 'paused' | 'explicit'

export function HomeHeroCarousel({ slides }: { slides: StoreHeroSlide[] }) {
  const visibleSlides = slides.filter((slide) => Boolean(slide.imageUrl))
  const [activeIndex, setActiveIndex] = useState(0)
  const [rotationMode, setRotationMode] = useState<RotationMode>('auto')
  const [reducedMotion, setReducedMotion] = useState(false)
  const touchStartX = useRef<number | null>(null)
  const hasMultiple = visibleSlides.length > 1
  const isAutoPlaying = hasMultiple
    && rotationMode !== 'paused'
    && (!reducedMotion || rotationMode === 'explicit')

  const goTo = useCallback((index: number) => {
    const count = visibleSlides.length
    if (count === 0) return
    setActiveIndex((index + count) % count)
  }, [visibleSlides.length])

  const stopAutoPlay = useCallback(() => {
    setRotationMode('paused')
  }, [])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    setReducedMotion(mediaQuery.matches)

    const handleChange = (event: MediaQueryListEvent) => {
      setReducedMotion(event.matches)
      if (event.matches) setRotationMode('paused')
    }
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [])

  useEffect(() => {
    if (!isAutoPlaying) return

    const timer = window.setInterval(() => {
      setActiveIndex((current) => (current + 1) % visibleSlides.length)
    }, AUTO_PLAY_MS)
    return () => window.clearInterval(timer)
  }, [isAutoPlaying, visibleSlides.length])

  useEffect(() => {
    if (activeIndex >= visibleSlides.length) setActiveIndex(0)
  }, [activeIndex, visibleSlides.length])

  if (visibleSlides.length === 0) return null

  const activeSlide = visibleSlides[activeIndex] ?? visibleSlides[0]
  const activeTitle = activeSlide.title?.trim()

  return (
    <section
      className="relative h-[72svh] min-h-[440px] max-h-[760px] overflow-hidden bg-ink text-canvas"
      role="region"
      aria-roledescription="carousel"
      aria-label="Homepage highlights"
      onMouseEnter={stopAutoPlay}
      onFocusCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) stopAutoPlay()
      }}
      onKeyDown={(event) => {
        if (event.key === 'ArrowLeft') {
          event.preventDefault()
          stopAutoPlay()
          goTo(activeIndex - 1)
        }
        if (event.key === 'ArrowRight') {
          event.preventDefault()
          stopAutoPlay()
          goTo(activeIndex + 1)
        }
      }}
      onTouchStart={(event) => {
        touchStartX.current = event.touches[0]?.clientX ?? null
        stopAutoPlay()
      }}
      onTouchEnd={(event) => {
        const start = touchStartX.current
        const end = event.changedTouches[0]?.clientX
        touchStartX.current = null
        if (start == null || end == null || Math.abs(end - start) < SWIPE_THRESHOLD_PX) return
        goTo(end < start ? activeIndex + 1 : activeIndex - 1)
      }}
      onTouchCancel={() => {
        touchStartX.current = null
      }}
    >
      {visibleSlides.map((slide, index) => (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          key={slide.id ?? `${slide.imageUrl}-${index}`}
          src={slide.imageUrl ?? ''}
          alt=""
          aria-hidden={index !== activeIndex}
          className={`absolute inset-0 h-full w-full object-cover object-top transition-opacity duration-700 ease-luxe motion-reduce:transition-none ${
            index === activeIndex ? 'opacity-100' : 'opacity-0'
          }`}
        />
      ))}
      <div className="absolute inset-0 bg-ink/45" aria-hidden="true" />

      <div className="container-luxe relative flex h-full items-end pb-16 pt-24 sm:pb-20 lg:pb-24">
        <div className="max-w-2xl" aria-live={isAutoPlaying ? 'off' : 'polite'}>
          <p className="mb-4 text-[11px] font-medium uppercase tracking-luxe text-canvas/75">
            {String(activeIndex + 1).padStart(2, '0')} / {String(visibleSlides.length).padStart(2, '0')}
          </p>
          {activeTitle && (
            <h1 className="break-words font-display text-4xl font-medium leading-[1.02] tracking-normal sm:text-5xl lg:text-6xl">
              {activeTitle}
            </h1>
          )}
          {activeSlide.subtitle && (
            <p className="mt-5 max-w-xl text-base leading-7 text-canvas/85 sm:text-lg">
              {activeSlide.subtitle}
            </p>
          )}
          {(activeSlide.ctaLink && activeSlide.ctaText)
            || (activeSlide.ctaLinkSecondary && activeSlide.ctaTextSecondary) ? (
              <div className="mt-8 flex flex-wrap gap-3">
                {activeSlide.ctaLink && activeSlide.ctaText && (
                  <Link
                    href={activeSlide.ctaLink}
                    className="inline-flex items-center justify-center border border-canvas bg-canvas px-7 py-3.5 text-[13px] font-medium uppercase tracking-luxe text-ink transition-colors duration-300 hover:bg-canvas/90"
                  >
                    {activeSlide.ctaText}
                  </Link>
                )}
                {activeSlide.ctaLinkSecondary && activeSlide.ctaTextSecondary && (
                  <Link
                    href={activeSlide.ctaLinkSecondary}
                    className="inline-flex items-center justify-center border border-canvas/70 px-7 py-3.5 text-[13px] font-medium uppercase tracking-luxe text-canvas transition-colors duration-300 hover:border-canvas hover:bg-canvas hover:text-ink"
                  >
                    {activeSlide.ctaTextSecondary}
                  </Link>
                )}
              </div>
            ) : null}
        </div>
      </div>

      {hasMultiple && (
        <div className="absolute bottom-5 right-5 flex items-center gap-2 sm:bottom-8 sm:right-8">
          <button
            type="button"
            onClick={() => {
              stopAutoPlay()
              goTo(activeIndex - 1)
            }}
            className="grid h-10 w-10 place-items-center border border-canvas/55 text-canvas transition-colors hover:border-canvas hover:bg-canvas hover:text-ink"
            aria-label="Previous slide"
            title="Previous slide"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => setRotationMode(isAutoPlaying ? 'paused' : 'explicit')}
            className="grid h-10 w-10 place-items-center border border-canvas/55 text-canvas transition-colors hover:border-canvas hover:bg-canvas hover:text-ink"
            aria-label={isAutoPlaying ? 'Pause slideshow' : 'Resume slideshow'}
            title={isAutoPlaying ? 'Pause slideshow' : 'Resume slideshow'}
          >
            {isAutoPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
          </button>
          <button
            type="button"
            onClick={() => {
              stopAutoPlay()
              goTo(activeIndex + 1)
            }}
            className="grid h-10 w-10 place-items-center border border-canvas/55 text-canvas transition-colors hover:border-canvas hover:bg-canvas hover:text-ink"
            aria-label="Next slide"
            title="Next slide"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      )}

      {hasMultiple && (
        <div className="absolute bottom-6 left-5 flex gap-2 sm:bottom-9 sm:left-8" aria-label="Choose slide">
          {visibleSlides.map((slide, index) => (
            <button
              key={slide.id ?? index}
              type="button"
              onClick={() => {
                stopAutoPlay()
                goTo(index)
              }}
              className={`h-2.5 w-2.5 border border-canvas transition-colors ${
                index === activeIndex ? 'bg-canvas' : 'bg-transparent'
              }`}
              aria-label={`Go to slide ${index + 1}`}
              aria-current={index === activeIndex ? 'true' : undefined}
            />
          ))}
        </div>
      )}
    </section>
  )
}
