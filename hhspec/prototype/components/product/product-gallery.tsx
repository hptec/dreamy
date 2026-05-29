'use client'

import { useState } from 'react'
import { Play, ZoomIn } from 'lucide-react'
import { cn } from '@/lib/utils'

export function ProductGallery({ images, name, hasVideo, lifestyle }: { images: string[]; name: string; hasVideo?: boolean; lifestyle?: string }) {
  const all = [...images, ...(lifestyle ? [lifestyle] : [])]
  const [active, setActive] = useState(0)
  const [zoom, setZoom] = useState(false)
  const [pos, setPos] = useState({ x: 50, y: 50 })

  const onMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setPos({ x: ((e.clientX - rect.left) / rect.width) * 100, y: ((e.clientY - rect.top) / rect.height) * 100 })
  }

  const isVideoSlide = hasVideo && active === all.length // virtual video slide
  const slides = hasVideo ? [...all, all[0]] : all

  return (
    <div className="flex gap-4">
      {/* 缩略图竖排 */}
      <div className="hidden w-20 shrink-0 flex-col gap-3 sm:flex">
        {all.map((img, i) => (
          <button key={i} onClick={() => setActive(i)} className={cn('aspect-[3/4] overflow-hidden rounded-sm border-2 transition-colors', active === i ? 'border-gold' : 'border-transparent hover:border-line')}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={img} alt={`${name} view ${i + 1}`} className="h-full w-full object-cover" />
          </button>
        ))}
        {hasVideo && (
          <button onClick={() => setActive(all.length)} className={cn('relative aspect-[3/4] overflow-hidden rounded-sm border-2 transition-colors', active === all.length ? 'border-gold' : 'border-transparent hover:border-line')}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={all[0]} alt="Runway video" className="h-full w-full object-cover" />
            <span className="absolute inset-0 flex items-center justify-center bg-ink/30"><Play className="h-5 w-5 fill-canvas text-canvas" /></span>
          </button>
        )}
      </div>

      {/* 主图 */}
      <div className="flex-1">
        <div
          className="relative aspect-[3/4] overflow-hidden rounded-sm bg-muted"
          onMouseEnter={() => !isVideoSlide && setZoom(true)}
          onMouseLeave={() => setZoom(false)}
          onMouseMove={onMove}
        >
          {isVideoSlide ? (
            <div className="flex h-full w-full items-center justify-center bg-ink">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={all[0]} alt={`${name} runway`} className="h-full w-full object-cover opacity-60" />
              <button className="absolute flex flex-col items-center gap-2 text-canvas">
                <span className="flex h-16 w-16 items-center justify-center rounded-full bg-canvas/20 backdrop-blur"><Play className="h-7 w-7 fill-canvas" /></span>
                <span className="text-xs uppercase tracking-luxe">Watch the runway</span>
              </button>
            </div>
          ) : (
            <>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={slides[active]}
                alt={`${name} view ${active + 1}`}
                className="h-full w-full object-cover transition-transform duration-200"
                style={zoom ? { transform: 'scale(1.8)', transformOrigin: `${pos.x}% ${pos.y}%` } : undefined}
              />
              <span className="absolute bottom-3 right-3 flex items-center gap-1 rounded-full bg-canvas/80 px-3 py-1 text-[10px] uppercase tracking-luxe backdrop-blur">
                <ZoomIn className="h-3 w-3" /> Hover to zoom
              </span>
            </>
          )}
        </div>
        {/* 移动端缩略图横排 */}
        <div className="mt-3 flex gap-2 overflow-x-auto sm:hidden">
          {all.map((img, i) => (
            <button key={i} onClick={() => setActive(i)} className={cn('aspect-[3/4] w-16 shrink-0 overflow-hidden rounded-sm border-2', active === i ? 'border-gold' : 'border-line')}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={img} alt={`${name} ${i + 1}`} className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
