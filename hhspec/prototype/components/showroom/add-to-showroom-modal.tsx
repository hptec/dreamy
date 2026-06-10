'use client'

import { useState } from 'react'
import { X, Plus, Check, CalendarDays, Users } from 'lucide-react'
import type { Product } from '@/data/types'
import { useStore } from '@/components/store-provider'
import { cn, formatDateLong } from '@/lib/utils'

/** F-067：从 PDP / 商品卡片把款式（含颜色）加入已有或新建 Showroom */
export function AddToShowroomModal({ product, defaultColor, open, onClose }: { product: Product; defaultColor?: string; open: boolean; onClose: () => void }) {
  const { showrooms, addToShowroom, createShowroom } = useStore()
  const [color, setColor] = useState(defaultColor ?? product.colors[0]?.name ?? '')
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDate, setNewDate] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [added, setAdded] = useState<string | null>(null)

  if (!open) return null

  const handleAdd = (showroomId: string, showroomName: string) => {
    addToShowroom(showroomId, product.id, color)
    setAdded(`Added to ${showroomName}`)
    setTimeout(onClose, 1100)
  }

  const handleCreate = () => {
    if (!newName.trim() || !newDate) {
      setCreateError('Please enter a name and wedding date.')
      return
    }
    const id = createShowroom(newName.trim(), newDate)
    if (!id) {
      setCreateError('Showroom limit reached for this demo.')
      return
    }
    addToShowroom(id, product.id, color)
    setAdded(`Added to ${newName.trim()}`)
    setTimeout(onClose, 1100)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-md animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <p className="eyebrow">Bridal Party</p>
        <h2 className="mt-1 font-display text-2xl font-medium">Add to Showroom</h2>
        <p className="mt-1 text-sm text-ink-soft">{product.name}</p>

        {added ? (
          <p className="mt-6 flex items-center gap-2 rounded-sm bg-sage/10 px-4 py-3 text-sm text-sage-deep" role="status">
            <Check className="h-4 w-4" /> {added}
          </p>
        ) : (
          <>
            {product.colors.length > 1 && (
              <div className="mt-5">
                <p className="eyebrow mb-2">Color — <span className="text-ink-soft">{color}</span></p>
                <div className="flex flex-wrap gap-2">
                  {product.colors.map((c) => (
                    <button
                      key={c.name}
                      onClick={() => setColor(c.name)}
                      className={cn('h-8 w-8 cursor-pointer rounded-full border-2 transition-all', color === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')}
                      style={{ backgroundColor: c.hex }}
                      title={c.name}
                      aria-label={c.name}
                    />
                  ))}
                </div>
              </div>
            )}

            <div className="mt-5 space-y-2">
              {showrooms.length === 0 && !creating && (
                <p className="rounded-sm border border-dashed border-line px-4 py-5 text-center text-sm text-ink-soft">No showrooms yet — create your first below.</p>
              )}
              {showrooms.map((s) => {
                const already = s.items.some((it) => it.productId === product.id && it.color === color)
                return (
                  <button
                    key={s.id}
                    onClick={() => !already && handleAdd(s.id, s.name)}
                    disabled={already}
                    className={cn(
                      'flex w-full cursor-pointer items-center justify-between rounded-sm border px-4 py-3 text-left transition-colors',
                      already ? 'cursor-default border-sage/40 bg-sage/5' : 'border-line hover:border-gold'
                    )}
                  >
                    <span>
                      <span className="block text-sm font-medium">{s.name}</span>
                      <span className="mt-0.5 flex items-center gap-3 text-xs text-ink-soft">
                        <span className="flex items-center gap-1"><CalendarDays className="h-3 w-3" /> {formatDateLong(s.weddingDate)}</span>
                        <span className="flex items-center gap-1"><Users className="h-3 w-3" /> {s.members.length}</span>
                      </span>
                    </span>
                    {already ? <span className="flex items-center gap-1 text-xs text-sage-deep"><Check className="h-3.5 w-3.5" /> Saved</span> : <Plus className="h-4 w-4 text-gold-deep" />}
                  </button>
                )
              })}
            </div>

            {creating ? (
              <div className="mt-4 space-y-3 rounded-sm bg-muted p-4">
                <div>
                  <label htmlFor="showroom-name" className="eyebrow mb-1.5 block">Showroom name</label>
                  <input id="showroom-name" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Sarah's Bridal Party" className="w-full rounded-sm border border-line bg-canvas px-3 py-2.5 text-sm outline-none focus:border-gold" />
                </div>
                <div>
                  <label htmlFor="showroom-date" className="eyebrow mb-1.5 block">Wedding date</label>
                  <input id="showroom-date" type="date" value={newDate} onChange={(e) => setNewDate(e.target.value)} className="w-full rounded-sm border border-line bg-canvas px-3 py-2.5 text-sm outline-none focus:border-gold" />
                </div>
                {createError && <p className="text-xs text-blush">{createError}</p>}
                <div className="flex gap-2">
                  <button onClick={handleCreate} className="btn-primary flex-1 px-4 py-2.5 text-xs">Create & Add</button>
                  <button onClick={() => setCreating(false)} className="btn-outline px-4 py-2.5 text-xs">Cancel</button>
                </div>
              </div>
            ) : (
              <button onClick={() => setCreating(true)} className="mt-4 flex w-full cursor-pointer items-center justify-center gap-2 rounded-sm border border-dashed border-gold/50 px-4 py-3 text-[12px] font-medium uppercase tracking-luxe text-gold-deep transition-colors hover:border-gold hover:bg-gold/5">
                <Plus className="h-4 w-4" /> New Showroom
              </button>
            )}
          </>
        )}
      </div>
    </div>
  )
}
