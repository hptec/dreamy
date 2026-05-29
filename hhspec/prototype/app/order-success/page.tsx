import Link from 'next/link'
import { Check, Package } from 'lucide-react'
import { products } from '@/data/products'
import { ProductCard } from '@/components/product/product-card'

export default function OrderSuccessPage() {
  const orderNo = 'DRM-' + Math.floor(100000 + Math.random() * 900000)
  const recommended = products.filter((p) => p.category === 'accessories').slice(0, 4)

  return (
    <div className="container-luxe py-16">
      <div className="mx-auto max-w-lg text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-sage/15">
          <Check className="h-8 w-8 text-sage-deep" />
        </div>
        <h1 className="mt-6 font-display text-4xl font-medium">Thank you!</h1>
        <p className="mt-3 text-ink-soft">Your order is confirmed. We&apos;ve sent a confirmation to your email with all the details.</p>

        <div className="mt-8 rounded-sm border border-line bg-surface p-6 text-left">
          <div className="flex items-center justify-between">
            <div><p className="eyebrow">Order Number</p><p className="font-display text-xl">{orderNo}</p></div>
            <Package className="h-8 w-8 text-gold" strokeWidth={1.5} />
          </div>
          <div className="mt-4 border-t border-line pt-4 text-sm text-ink-soft">
            <p>Estimated delivery: <span className="font-medium text-ink">3–5 business days</span></p>
            <p className="mt-1">A tracking number will be emailed once your order ships.</p>
          </div>
        </div>

        <div className="mt-6 flex justify-center gap-3">
          <Link href="/account/orders" className="btn-primary">Track My Order</Link>
          <Link href="/wedding-dresses" className="btn-outline">Continue Shopping</Link>
        </div>
      </div>

      <section className="mt-20">
        <h2 className="mb-8 text-center font-display text-3xl font-medium">Complete your look</h2>
        <div className="grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
          {recommended.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>
    </div>
  )
}
