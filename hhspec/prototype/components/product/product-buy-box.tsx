'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { Heart, Truck, Sparkles, Ruler, ChevronDown, Plus, Minus, Users, CalendarDays, Clock, Zap, AlertTriangle } from 'lucide-react'
import type { Product, CustomSizeMeasurements } from '@/data/types'
import { useStore } from '@/components/store-provider'
import { formatPrice, installments, cn, deliveryVerdict, SHIPPING_BUFFER_DAYS, RUSH_FEE, formatDateLong } from '@/lib/utils'
import { Stars, Badge } from '@/components/ui/primitives'
import { SizeGuideModal } from './size-guide-modal'
import { FindMySizeModal } from './find-my-size-modal'
import { AddToShowroomModal } from '@/components/showroom/add-to-showroom-modal'

const EMPTY_MEASUREMENTS: CustomSizeMeasurements = { bust: '', waist: '', hips: '', hollowToFloor: '', height: '', heelHeight: '' }

export function ProductBuyBox({ product }: { product: Product }) {
  const { currency, addToCart, toggleWishlist, isWished, trackView, isInAnyShowroom, showroomWeddingDate } = useStore()
  const [color, setColor] = useState(product.colors[0])
  const [size, setSize] = useState<string | null>(null)
  const [qty, setQty] = useState(1)
  const [sizeGuide, setSizeGuide] = useState(false)
  const [fitFinder, setFitFinder] = useState(false)
  const [showroomOpen, setShowroomOpen] = useState(false)
  const [error, setError] = useState(false)
  const [measurements, setMeasurements] = useState<CustomSizeMeasurements>(EMPTY_MEASUREMENTS)
  const [measureError, setMeasureError] = useState(false)
  const [weddingDate, setWeddingDate] = useState('')
  const [dateTouched, setDateTouched] = useState(false)
  const wished = isWished(product.id)
  const inShowroom = isInAnyShowroom(product.id)

  useEffect(() => { trackView(product.id) }, [product.id, trackView])

  // F-077 联动：Showroom 已含婚期时自动带入（用户手动修改后不再覆盖）
  useEffect(() => {
    if (!dateTouched && showroomWeddingDate) setWeddingDate(showroomWeddingDate)
  }, [showroomWeddingDate, dateTouched])

  const standardSizes = product.sizes.filter((s) => s.size !== 'Custom')
  const isCustom = size === 'Custom' && product.customSizeEnabled

  const add = () => {
    if (!size) { setError(true); return }
    if (isCustom) {
      const incomplete = Object.values(measurements).some((v) => !v.trim())
      if (incomplete) { setMeasureError(true); return }
      setMeasureError(false)
      addToCart({ productId: product.id, slug: product.slug, name: product.name, image: color.image, price: product.price, color: color.name, size, customSize: measurements }, qty)
      return
    }
    addToCart({ productId: product.id, slug: product.slug, name: product.name, image: color.image, price: product.price, color: color.name, size }, qty)
  }

  const setM = (key: keyof CustomSizeMeasurements) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setMeasurements((p) => ({ ...p, [key]: e.target.value }))

  // F-076：婚期驱动交期三态
  const verdict = weddingDate ? deliveryVerdict(weddingDate, product.leadTimeDays, product.rushAvailable) : null
  const orderByDate = weddingDate
    ? new Date(new Date(`${weddingDate}T00:00:00`).getTime() - (product.leadTimeDays + SHIPPING_BUFFER_DAYS) * 86400000)
        .toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    : ''

  return (
    <div className="lg:sticky lg:top-28">
      {product.badges?.[0] && <div className="mb-3"><Badge variant={product.badges[0] === 'Sale' ? 'sale' : product.badges[0] === 'New' ? 'new' : 'default'}>{product.badges[0]}</Badge></div>}
      <p className="eyebrow">{product.subCategory}</p>
      <h1 className="mt-1 font-display text-3xl font-medium leading-tight lg:text-4xl">{product.name}</h1>
      <div className="mt-3 flex items-center gap-2">
        <Stars rating={product.rating} />
        <a href="#reviews" className="text-sm text-ink-soft underline">{product.reviewCount} reviews</a>
      </div>

      <div className="mt-4 flex items-center gap-3">
        <span className="font-display text-2xl font-medium">{formatPrice(product.price, currency)}</span>
        {product.compareAtPrice && <span className="text-lg text-ink-faint line-through">{formatPrice(product.compareAtPrice, currency)}</span>}
      </div>
      <p className="mt-1 text-sm text-ink-soft">or 4 interest-free payments of {formatPrice(Number(installments(product.price)), currency)} with <strong>Klarna</strong></p>

      {/* Color */}
      <div className="mt-6">
        <p className="eyebrow mb-2.5">Color — <span className="text-ink-soft">{color.name}</span></p>
        <div className="flex flex-wrap gap-2.5">
          {product.colors.map((c) => (
            <button key={c.name} onClick={() => setColor(c)} className={cn('h-9 w-9 rounded-full border-2 transition-all', color.name === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')} style={{ backgroundColor: c.hex }} title={c.name} aria-label={c.name} />
          ))}
        </div>
      </div>

      {/* Size */}
      <div className="mt-6">
        <div className="mb-2.5 flex items-center justify-between">
          <p className="eyebrow">Size</p>
          <div className="flex items-center gap-4">
            <button onClick={() => setFitFinder(true)} className="flex cursor-pointer items-center gap-1 text-xs text-gold-deep underline"><Sparkles className="h-3.5 w-3.5" /> Find My Size</button>
            <button onClick={() => setSizeGuide(true)} className="flex cursor-pointer items-center gap-1 text-xs text-gold-deep underline"><Ruler className="h-3.5 w-3.5" /> Size Guide</button>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {standardSizes.map((s) => (
            <button
              key={s.size}
              disabled={!s.inStock}
              onClick={() => { setSize(s.size); setError(false) }}
              className={cn('min-w-[3.25rem] cursor-pointer rounded-sm border px-3 py-2 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-30 disabled:line-through',
                size === s.size ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink')}
            >
              {s.size}
            </button>
          ))}
          {product.customSizeEnabled && (
            <button
              onClick={() => { setSize('Custom'); setError(false) }}
              className={cn('cursor-pointer rounded-sm border px-3 py-2 text-xs transition-colors',
                size === 'Custom' ? 'border-ink bg-ink text-canvas' : 'border-gold/50 text-gold-deep hover:border-gold')}
            >
              Custom Size · Free
            </button>
          )}
        </div>
        {error && <p className="mt-2 text-xs text-blush">Please select a size to continue.</p>}

        {/* F-074：定制尺寸内联量体表单 */}
        {isCustom && (
          <div className="mt-3 rounded-sm bg-sage/10 p-4">
            <p className="text-xs text-sage-deep">Made-to-measure at no extra cost. Enter your measurements in inches — allow {Math.round(product.leadTimeDays / 7)}–{Math.round(product.leadTimeDays / 7) + 1} weeks.</p>
            <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <MeasureField id="cs-bust" label="Bust" placeholder="34" value={measurements.bust} onChange={setM('bust')} />
              <MeasureField id="cs-waist" label="Waist" placeholder="27" value={measurements.waist} onChange={setM('waist')} />
              <MeasureField id="cs-hips" label="Hips" placeholder="37" value={measurements.hips} onChange={setM('hips')} />
              <MeasureField id="cs-htf" label="Hollow to Floor" placeholder="58" value={measurements.hollowToFloor} onChange={setM('hollowToFloor')} />
              <MeasureField id="cs-height" label="Height" placeholder="65" value={measurements.height} onChange={setM('height')} />
              <MeasureField id="cs-heel" label="Heel Height" placeholder="3" value={measurements.heelHeight} onChange={setM('heelHeight')} />
            </div>
            {measureError && <p className="mt-2 text-xs text-blush">Please complete all six measurements before adding to bag.</p>}
          </div>
        )}
      </div>

      {/* F-076：When is your wedding? 婚期交期判定 */}
      <div className="mt-6 rounded-sm border border-line p-4">
        <label htmlFor="wedding-date" className="eyebrow flex items-center gap-1.5"><CalendarDays className="h-3.5 w-3.5" /> When is your wedding?</label>
        <input
          id="wedding-date"
          type="date"
          value={weddingDate}
          onChange={(e) => { setWeddingDate(e.target.value); setDateTouched(true) }}
          className="mt-2 w-full rounded-sm border border-line bg-surface px-3 py-2.5 text-sm outline-none focus:border-gold"
        />
        {verdict === 'ok' && (
          <p className="mt-2.5 flex items-start gap-2 rounded-sm bg-sage/10 px-3 py-2.5 text-xs text-sage-deep">
            <Clock className="mt-0.5 h-3.5 w-3.5 shrink-0" /> Arrives in time for {formatDateLong(weddingDate)} with standard production. Order by {orderByDate} to stay on schedule.
          </p>
        )}
        {verdict === 'rush' && (
          <p className="mt-2.5 flex items-start gap-2 rounded-sm bg-gold/10 px-3 py-2.5 text-xs text-gold-deep">
            <Zap className="mt-0.5 h-3.5 w-3.5 shrink-0" /> Rush production required (+{formatPrice(RUSH_FEE, currency)}) to arrive before {formatDateLong(weddingDate)}. Standard production won&apos;t make it in time.
          </p>
        )}
        {verdict === 'late' && (
          <p className="mt-2.5 flex items-start gap-2 rounded-sm bg-blush/15 px-3 py-2.5 text-xs text-blush">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> May not arrive in time for {formatDateLong(weddingDate)} — <Link href="/wedding-dresses" className="underline">see Ready-to-Ship styles</Link>.
          </p>
        )}
      </div>

      {/* Qty + CTA */}
      <div className="mt-6 flex items-center gap-3">
        <div className="flex items-center border border-line">
          <button onClick={() => setQty(Math.max(1, qty - 1))} className="cursor-pointer p-3" aria-label="Decrease quantity"><Minus className="h-3.5 w-3.5" /></button>
          <span className="w-10 text-center text-sm">{qty}</span>
          <button onClick={() => setQty(qty + 1)} className="cursor-pointer p-3" aria-label="Increase quantity"><Plus className="h-3.5 w-3.5" /></button>
        </div>
        <button onClick={add} className="btn-primary flex-1">Add to Bag</button>
        <button onClick={() => toggleWishlist(product.id)} className="cursor-pointer rounded-sm border border-line p-3.5 transition-colors hover:border-ink" aria-label="Add to wishlist">
          <Heart className={cn('h-5 w-5', wished ? 'fill-blush text-blush' : 'text-ink')} />
        </button>
        <button onClick={() => setShowroomOpen(true)} className="cursor-pointer rounded-sm border border-line p-3.5 transition-colors hover:border-ink" aria-label="Add to Showroom" title="Add to Showroom">
          <Users className={cn('h-5 w-5', inShowroom ? 'text-gold-deep' : 'text-ink')} />
        </button>
      </div>

      <div className="mt-3 flex gap-3">
        <button className="flex-1 cursor-pointer rounded-sm border border-line py-3 text-[12px] font-medium uppercase tracking-luxe transition-colors hover:border-gold hover:text-gold-deep">Order a Swatch</button>
        <button className="flex-1 cursor-pointer rounded-sm border border-line py-3 text-[12px] font-medium uppercase tracking-luxe text-ink-faint" title="Coming soon" disabled>Try in AR · Soon</button>
      </div>

      {/* Trust 条 */}
      <div className="mt-6 space-y-2.5 border-t border-line pt-5 text-sm text-ink-soft">
        <p className="flex items-center gap-2"><Truck className="h-4 w-4 text-gold" /> Free worldwide shipping over $200</p>
        <p className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-gold" /> Order fabric swatches before you commit</p>
        <p className="flex items-center gap-2"><Ruler className="h-4 w-4 text-gold" /> {product.customSizeEnabled ? 'Free custom sizing available' : `Ships in ${product.leadTimeDays} days or less`}</p>
      </div>

      {/* 描述折叠 */}
      <div className="mt-6 divide-y divide-line border-y border-line">
        <Accordion title="Description" defaultOpen>
          <p>{product.description}</p>
        </Accordion>
        <Accordion title="Details">
          <ul className="list-inside list-disc space-y-1">{product.details.map((d) => <li key={d}>{d}</li>)}</ul>
        </Accordion>
        <Accordion title="Fabric & Care">
          <ul className="list-inside list-disc space-y-1">{product.fabricCare.map((d) => <li key={d}>{d}</li>)}</ul>
        </Accordion>
        <Accordion title="Shipping & Delivery">
          <p>Standard production {product.leadTimeDays} days{product.customSizeEnabled ? ', custom & made-to-measure the same' : ''}{product.rushAvailable ? `. Rush production available (+$${RUSH_FEE})` : ''}. Free worldwide shipping on orders over $200 via FedEx, UPS, or DHL Express.</p>
        </Accordion>
      </div>

      <SizeGuideModal open={sizeGuide} onClose={() => setSizeGuide(false)} />
      <FindMySizeModal
        open={fitFinder}
        onClose={() => setFitFinder(false)}
        sizes={standardSizes}
        customAvailable={product.customSizeEnabled}
        onSelect={(s) => { setSize(s); setError(false) }}
      />
      {showroomOpen && <AddToShowroomModal product={product} defaultColor={color.name} open={showroomOpen} onClose={() => setShowroomOpen(false)} />}
    </div>
  )
}

function MeasureField({ id, label, placeholder, value, onChange }: { id: string; label: string; placeholder: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-[10px] font-medium uppercase tracking-luxe text-ink-soft">{label} (in)</label>
      <input id={id} type="number" inputMode="decimal" min="0" placeholder={placeholder} value={value} onChange={onChange} className="w-full rounded-sm border border-line bg-canvas px-2.5 py-2 text-sm outline-none focus:border-gold" />
    </div>
  )
}

function Accordion({ title, children, defaultOpen }: { title: string; children: React.ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(!!defaultOpen)
  return (
    <div className="py-4">
      <button onClick={() => setOpen(!open)} className="flex w-full cursor-pointer items-center justify-between text-sm font-medium uppercase tracking-luxe">
        {title}
        <ChevronDown className={cn('h-4 w-4 transition-transform', open && 'rotate-180')} />
      </button>
      {open && <div className="mt-3 text-sm leading-relaxed text-ink-soft">{children}</div>}
    </div>
  )
}
