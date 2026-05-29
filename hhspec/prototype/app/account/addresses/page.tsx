'use client'

import { useState } from 'react'
import { Plus, Pencil, Trash2, Check } from 'lucide-react'
import { addresses as seed } from '@/data/account'
import { cn } from '@/lib/utils'

export default function AddressesPage() {
  const [list, setList] = useState(seed)

  const setDefault = (id: string) => setList((p) => p.map((a) => ({ ...a, default: a.id === id })))
  const remove = (id: string) => setList((p) => p.filter((a) => a.id !== id))

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-3xl font-medium">Addresses</h1>
        <button className="btn-primary"><Plus className="h-4 w-4" /> Add New</button>
      </div>
      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        {list.map((a) => (
          <div key={a.id} className={cn('rounded-sm border bg-surface p-5', a.default ? 'border-gold' : 'border-line')}>
            <div className="flex items-start justify-between">
              <p className="font-medium">{a.name}</p>
              {a.default && <span className="flex items-center gap-1 rounded-full bg-gold/15 px-2.5 py-0.5 text-[11px] text-gold-deep"><Check className="h-3 w-3" /> Default</span>}
            </div>
            <p className="mt-2 text-sm text-ink-soft">{a.line1}<br />{a.city}, {a.state} {a.zip}<br />{a.country}<br />{a.phone}</p>
            <div className="mt-4 flex items-center gap-3 text-xs">
              <button className="flex cursor-pointer items-center gap-1 text-ink-soft hover:text-ink"><Pencil className="h-3.5 w-3.5" /> Edit</button>
              {!a.default && <button onClick={() => setDefault(a.id)} className="cursor-pointer text-gold-deep underline">Set as default</button>}
              {!a.default && <button onClick={() => remove(a.id)} className="flex cursor-pointer items-center gap-1 text-ink-soft hover:text-blush"><Trash2 className="h-3.5 w-3.5" /> Remove</button>}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
