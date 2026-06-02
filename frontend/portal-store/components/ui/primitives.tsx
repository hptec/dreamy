import { cn } from '@/lib/utils'
import { ChevronRight } from 'lucide-react'
import Link from 'next/link'

export function Eyebrow({ children, className }: { children: React.ReactNode; className?: string }) {
  return <p className={cn('eyebrow', className)}>{children}</p>
}

export function SectionHeading({
  eyebrow,
  title,
  description,
  align = 'center',
  className
}: {
  eyebrow?: string
  title: string
  description?: string
  align?: 'center' | 'left'
  className?: string
}) {
  return (
    <div className={cn('max-w-2xl', align === 'center' ? 'mx-auto text-center' : 'text-left', className)}>
      {eyebrow && <Eyebrow className="mb-3">{eyebrow}</Eyebrow>}
      <h2 className="heading-display text-3xl sm:text-4xl lg:text-[2.75rem]">{title}</h2>
      {description && <p className="mt-4 text-ink-soft leading-relaxed">{description}</p>}
    </div>
  )
}

export function TextLink({ href, children, className }: { href: string; children: React.ReactNode; className?: string }) {
  return (
    <Link
      href={href}
      className={cn('group inline-flex items-center gap-1.5 text-[13px] font-medium uppercase tracking-luxe text-ink transition-colors hover:text-gold-deep', className)}
    >
      {children}
      <ChevronRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-1" />
    </Link>
  )
}

export function Badge({ children, variant = 'default' }: { children: React.ReactNode; variant?: 'default' | 'sale' | 'new' }) {
  const styles = {
    default: 'bg-ink/85 text-canvas',
    sale: 'bg-blush text-white',
    new: 'bg-sage text-white'
  }
  return (
    <span className={cn('inline-flex items-center rounded-sm px-2.5 py-1 text-[10px] font-medium uppercase tracking-luxe', styles[variant])}>
      {children}
    </span>
  )
}

export function Stars({ rating, className }: { rating: number; className?: string }) {
  return (
    <div className={cn('flex items-center gap-0.5', className)} aria-label={`Rated ${rating} out of 5`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <svg key={i} viewBox="0 0 24 24" className={cn('h-3.5 w-3.5', i <= Math.round(rating) ? 'fill-gold text-gold' : 'fill-line text-line')}>
          <path d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
        </svg>
      ))}
    </div>
  )
}
