'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Stars } from '@/components/ui/primitives'
import { cn } from '@/lib/utils'

const myReviews = [
  { product: 'Meadow Sage Bridesmaid Dress', rating: 5, date: 'May 2026', body: 'Perfect sage shade for our garden wedding. All my bridesmaids loved it!', image: '/competitor-refs/davidsbridal/bridesmaid-sage-01.jpg' },
  { product: 'Estelle Crystal Drop Earrings', rating: 5, date: 'May 2026', body: 'Caught the light beautifully in all our photos.', image: '/competitor-refs/birdygrey/accessory-jewelry-01.jpg' }
]

export default function SettingsPage() {
  const [tab, setTab] = useState<'profile' | 'reviews' | 'preferences'>('profile')

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">Settings</h1>
      <div className="mt-6 flex gap-6 border-b border-line">
        {([['profile', 'Profile'], ['reviews', 'My Reviews'], ['preferences', 'Email Preferences']] as const).map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)} className={cn('cursor-pointer border-b-2 pb-3 text-sm font-medium transition-colors', tab === k ? 'border-gold text-ink' : 'border-transparent text-ink-soft')}>{label}</button>
        ))}
      </div>

      {tab === 'profile' && (
        <form onSubmit={(e) => e.preventDefault()} className="mt-8 max-w-md space-y-4">
          <Field label="Full Name" value="Jane Doe" />
          <Field label="Email" type="email" value="jane@email.com" />
          <p className="-mt-2 text-xs text-ink-faint">Changing your email requires re-verification with a one-time code.</p>
          <Field label="Phone" value="+1 (805) 555-0142" />
          <div className="rounded-sm border border-line bg-muted/50 p-4">
            <p className="text-sm font-medium">Passwordless account</p>
            <p className="mt-1 text-xs text-ink-soft">
              You sign in with a one-time email code, Google, or Apple — no password to manage. Manage your login methods and devices in{' '}
              <Link href="/account/security" className="text-gold-deep underline">Login &amp; Security</Link>.
            </p>
          </div>
          <button className="btn-primary">Save Changes</button>
        </form>
      )}

      {tab === 'reviews' && (
        <div className="mt-8 space-y-4">
          {myReviews.map((r, i) => (
            <div key={i} className="flex gap-4 rounded-sm border border-line bg-surface p-4">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={r.image} alt={r.product} className="h-20 w-14 rounded-sm object-cover" />
              <div className="flex-1">
                <p className="text-sm font-medium">{r.product}</p>
                <div className="mt-1 flex items-center gap-2"><Stars rating={r.rating} /><span className="text-xs text-ink-faint">{r.date}</span></div>
                <p className="mt-2 text-sm text-ink-soft">{r.body}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'preferences' && (
        <div className="mt-8 max-w-md space-y-4">
          {['New collection launches', 'Outdoor wedding inspiration', 'Exclusive offers & sales', 'Order & shipping updates'].map((p, i) => (
            <label key={p} className="flex items-center justify-between rounded-sm border border-line p-4">
              <span className="text-sm">{p}</span>
              <input type="checkbox" defaultChecked={i !== 2} className="h-5 w-9 cursor-pointer appearance-none rounded-full bg-line transition-colors checked:bg-gold" />
            </label>
          ))}
          <button className="btn-primary">Update Preferences</button>
        </div>
      )}
    </div>
  )
}

function Field({ label, type = 'text', value }: { label: string; type?: string; value?: string }) {
  const id = label.toLowerCase().replace(/\s/g, '-')
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} type={type} defaultValue={value} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
    </div>
  )
}
