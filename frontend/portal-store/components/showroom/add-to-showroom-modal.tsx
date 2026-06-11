'use client'

/**
 * AddToShowroomModal（COMP-SHR-S07，copy-adapt 自 hhspec/prototype/components/showroom/add-to-showroom-modal.tsx）。
 * F-067：从 PDP 把款式（含颜色）加入已有或新建 Showroom。
 * 数据层换 API：listShowrooms / addShowroomItem / createShowroom（串联 E-SHR-01+08）。
 * 显式偏离⑧：Saved 置灰改 409102 提交容错（Summary 无 items，避免列表页 N 次详情请求）。
 * 显式偏离②：新建表单婚期改可选（契约 ShowroomUpsert required 仅 name）。
 */

import { useEffect, useState } from 'react'
import { X, Plus, Check, CalendarDays, Users } from 'lucide-react'
import type { ShowroomSummary, StoreProductDetail } from '@/lib/api/store-types'
import * as showroomApi from '@/lib/api/showroom-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { colorOptionsOf } from '@/components/product/product-utils'
import { cn, formatDateLong } from '@/lib/utils'

export function AddToShowroomModal({ product, defaultColor, open, onClose }: { product: StoreProductDetail; defaultColor?: string; open: boolean; onClose: () => void }) {
  const { te } = useI18n()
  const colors = colorOptionsOf(product)
  const [rooms, setRooms] = useState<ShowroomSummary[] | null>(null)
  const [loadError, setLoadError] = useState(false)
  const [color, setColor] = useState(defaultColor ?? colors[0]?.name ?? '')
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDate, setNewDate] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [added, setAdded] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    showroomApi
      .listShowrooms()
      .then(setRooms)
      .catch(() => setLoadError(true))
  }, [open])

  if (!open) return null

  const handleAdd = async (room: ShowroomSummary) => {
    if (busy) return
    setBusy(true)
    setCreateError(null)
    try {
      await showroomApi.addShowroomItem(room.id, product.id, color || undefined)
      setAdded(`Added to ${room.name}`)
      setTimeout(onClose, 1100)
    } catch (err) {
      if (err instanceof ApiError && err.code === 409102) {
        // 409102 视同 Saved 态（显式偏离⑧）
        setAdded(`Already saved in ${room.name}`)
        setTimeout(onClose, 1100)
      } else {
        setCreateError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setBusy(false)
    }
  }

  const handleCreate = async () => {
    if (!newName.trim()) {
      setCreateError('Please enter a showroom name.')
      return
    }
    setBusy(true)
    setCreateError(null)
    try {
      const room = await showroomApi.createShowroom({ name: newName.trim(), weddingDate: newDate || undefined })
      await showroomApi.addShowroomItem(room.id, product.id, color || undefined)
      setAdded(`Added to ${newName.trim()}`)
      setTimeout(onClose, 1100)
    } catch (err) {
      setCreateError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setBusy(false)
    }
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
            {colors.length > 1 && (
              <div className="mt-5">
                <p className="eyebrow mb-2">Color — <span className="text-ink-soft">{color}</span></p>
                <div className="flex flex-wrap gap-2">
                  {colors.map((c) => (
                    c.image ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        key={c.name}
                        src={c.image}
                        alt={c.name}
                        title={c.name}
                        onClick={() => setColor(c.name)}
                        className={cn('h-8 w-8 cursor-pointer rounded-full border-2 object-cover transition-all', color === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')}
                      />
                    ) : (
                      <button
                        key={c.name}
                        onClick={() => setColor(c.name)}
                        className={cn('h-8 w-8 cursor-pointer rounded-full border-2 bg-muted transition-all', color === c.name ? 'border-gold ring-2 ring-gold/25' : 'border-line hover:border-ink')}
                        title={c.name}
                        aria-label={c.name}
                      />
                    )
                  ))}
                </div>
              </div>
            )}

            <div className="mt-5 space-y-2">
              {rooms === null && !loadError && (
                <p className="rounded-sm border border-dashed border-line px-4 py-5 text-center text-sm text-ink-soft">Loading your showrooms…</p>
              )}
              {loadError && (
                <p className="rounded-sm border border-dashed border-line px-4 py-5 text-center text-sm text-blush">{te(50000)}</p>
              )}
              {rooms !== null && rooms.length === 0 && !creating && (
                <p className="rounded-sm border border-dashed border-line px-4 py-5 text-center text-sm text-ink-soft">No showrooms yet — create your first below.</p>
              )}
              {(rooms ?? []).map((s) => (
                <button
                  key={s.id}
                  onClick={() => handleAdd(s)}
                  disabled={busy}
                  className="flex w-full cursor-pointer items-center justify-between rounded-sm border border-line px-4 py-3 text-left transition-colors hover:border-gold disabled:opacity-60"
                >
                  <span>
                    <span className="block text-sm font-medium">{s.name}</span>
                    <span className="mt-0.5 flex items-center gap-3 text-xs text-ink-soft">
                      {s.weddingDate && <span className="flex items-center gap-1"><CalendarDays className="h-3 w-3" /> {formatDateLong(s.weddingDate)}</span>}
                      <span className="flex items-center gap-1"><Users className="h-3 w-3" /> {s.memberCount ?? 0}</span>
                    </span>
                  </span>
                  <Plus className="h-4 w-4 text-gold-deep" />
                </button>
              ))}
            </div>

            {createError && <p className="mt-3 text-xs text-blush">{createError}</p>}

            {creating ? (
              <div className="mt-4 space-y-3 rounded-sm bg-muted p-4">
                <div>
                  <label htmlFor="showroom-name" className="eyebrow mb-1.5 block">Showroom name</label>
                  <input id="showroom-name" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Sarah's Bridal Party" className="w-full rounded-sm border border-line bg-canvas px-3 py-2.5 text-sm outline-none focus:border-gold" />
                </div>
                <div>
                  <label htmlFor="showroom-date" className="eyebrow mb-1.5 block">Wedding date (optional)</label>
                  <input id="showroom-date" type="date" value={newDate} onChange={(e) => setNewDate(e.target.value)} className="w-full rounded-sm border border-line bg-canvas px-3 py-2.5 text-sm outline-none focus:border-gold" />
                </div>
                <div className="flex gap-2">
                  <button onClick={handleCreate} disabled={busy} className="btn-primary flex-1 px-4 py-2.5 text-xs disabled:opacity-60">Create & Add</button>
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
