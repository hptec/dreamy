import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/**
 * 编辑感 hero：图上零蒙层，可读性靠 canvas 面板解决。
 * split：左文字面板 + 右图（适合竖图）；wide：横幅图 + 下沿悬浮标题带（适合横图）。
 */
export function EditorialHero({
  image,
  alt,
  eyebrow,
  title,
  description,
  variant = 'split',
  objectPosition = 'center 30%',
  children,
  className
}: {
  image: string
  alt: string
  eyebrow?: ReactNode
  title: ReactNode
  description?: ReactNode
  variant?: 'split' | 'wide'
  objectPosition?: string
  children?: ReactNode
  className?: string
}) {
  if (variant === 'wide') {
    return (
      <section className={cn('bg-canvas', className)}>
        <div className="relative aspect-[4/5] max-h-[560px] w-full overflow-hidden bg-muted sm:aspect-[16/9] lg:aspect-[21/9]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={image} alt={alt} className="h-full w-full object-cover" style={{ objectPosition }} />
        </div>
        <div className="container-luxe">
          <div className="relative -mt-14 max-w-3xl bg-canvas px-7 pb-2 pt-8 sm:-mt-20 sm:px-10 lg:-mt-24 lg:px-14 lg:pt-12">
            {eyebrow && <p className="eyebrow mb-3 text-gold-deep">{eyebrow}</p>}
            <h1 className="heading-display text-4xl text-ink sm:text-5xl lg:text-6xl">{title}</h1>
            {description && <p className="mt-4 max-w-xl text-ink-soft">{description}</p>}
            {children}
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className={cn('bg-canvas', className)}>
      <div className="grid lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <div className="order-2 flex items-center px-6 py-10 sm:px-10 lg:order-1 lg:px-14 lg:py-16">
          <div className="max-w-xl">
            {eyebrow && <p className="eyebrow mb-4 text-gold-deep">{eyebrow}</p>}
            <h1 className="heading-display text-4xl text-ink sm:text-5xl lg:text-6xl">{title}</h1>
            {description && <p className="mt-5 max-w-lg text-base leading-7 text-ink-soft">{description}</p>}
            {children}
          </div>
        </div>
        <div className="relative order-1 aspect-[4/3] overflow-hidden bg-muted sm:aspect-[5/4] lg:order-2 lg:aspect-auto lg:min-h-[540px] lg:max-h-[640px]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={image} alt={alt} className="absolute inset-0 h-full w-full object-cover" style={{ objectPosition }} />
        </div>
      </div>
    </section>
  )
}
