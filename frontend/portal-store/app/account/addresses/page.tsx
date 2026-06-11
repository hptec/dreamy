'use client'

/**
 * 地址簿（COMP-TRD-S08，layout-keep + data-swap）：
 * mock → API CRUD（listAddresses/create/update/delete）；默认徽章 + 新增/编辑表单 + 删除二次确认
 * （404602/422601 处理；is_default=true 服务端自动取消原默认）。
 */

import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Check, X } from 'lucide-react'
import type { Address, AddressUpsert } from '@/lib/api/store-types'
import * as tradingApi from '@/lib/api/trading-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn } from '@/lib/utils'

export default function AddressesPage() {
  const { te } = useI18n()
  const [list, setList] = useState<Address[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)

  const load = () => {
    tradingApi.listAddresses()
      .then((items) => { setList(items); setError(null) })
      .catch((err: unknown) => setError(err instanceof ApiError ? te(err.code) : te(50000)))
  }

  useEffect(load, [])

  const setDefault = async (a: Address) => {
    try {
      await tradingApi.updateAddress(a.id, { ...toUpsert(a), isDefault: true })
      load()
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
      load()
    }
  }

  const remove = async (id: number) => {
    try {
      await tradingApi.deleteAddress(id)
      setList((p) => (p ?? []).filter((a) => a.id !== id))
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
      load()
    } finally {
      setConfirmDelete(null)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="font-display text-3xl font-medium">Addresses</h1>
        <button onClick={() => setEditing('new')} className="btn-primary"><Plus className="h-4 w-4" /> Add New</button>
      </div>
      {error && <p className="mt-4 rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">{error}</p>}
      {list === null ? (
        <div className="mt-8 grid gap-4 sm:grid-cols-2" aria-hidden="true">
          {[0, 1].map((i) => <div key={i} className="h-44 animate-pulse rounded-sm bg-muted" />)}
        </div>
      ) : list.length === 0 ? (
        <div className="mt-8 rounded-sm border border-dashed border-line py-16 text-center text-sm text-ink-soft">
          No addresses yet — add one to speed up checkout.
        </div>
      ) : (
        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          {list.map((a) => (
            <div key={a.id} className={cn('rounded-sm border bg-surface p-5', a.isDefault ? 'border-gold' : 'border-line')}>
              <div className="flex items-start justify-between">
                <p className="font-medium">{a.receiver}</p>
                {a.isDefault && <span className="flex items-center gap-1 rounded-full bg-gold/15 px-2.5 py-0.5 text-[11px] text-gold-deep"><Check className="h-3 w-3" /> Default</span>}
              </div>
              <p className="mt-2 text-sm text-ink-soft">{a.line}<br />{a.city}{a.state ? `, ${a.state}` : ''} {a.zip}<br />{a.country}{a.phone ? <><br />{a.phone}</> : null}</p>
              <div className="mt-4 flex items-center gap-3 text-xs">
                <button onClick={() => setEditing(a)} className="flex cursor-pointer items-center gap-1 text-ink-soft hover:text-ink"><Pencil className="h-3.5 w-3.5" /> Edit</button>
                {!a.isDefault && <button onClick={() => void setDefault(a)} className="cursor-pointer text-gold-deep underline">Set as default</button>}
                {!a.isDefault && (
                  confirmDelete === a.id ? (
                    <span className="flex items-center gap-2">
                      <button onClick={() => void remove(a.id)} className="cursor-pointer font-medium text-blush underline">Confirm</button>
                      <button onClick={() => setConfirmDelete(null)} className="cursor-pointer text-ink-soft underline">Keep</button>
                    </span>
                  ) : (
                    <button onClick={() => setConfirmDelete(a.id)} className="flex cursor-pointer items-center gap-1 text-ink-soft hover:text-blush"><Trash2 className="h-3.5 w-3.5" /> Remove</button>
                  )
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <AddressFormModal
          initial={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); load() }}
        />
      )}
    </div>
  )
}

function toUpsert(a: Address): AddressUpsert {
  return { receiver: a.receiver, phone: a.phone, line: a.line, city: a.city, state: a.state, zip: a.zip, country: a.country, isDefault: a.isDefault }
}

function AddressFormModal({ initial, onClose, onSaved }: { initial: Address | null; onClose: () => void; onSaved: () => void }) {
  const { te } = useI18n()
  const [form, setForm] = useState({
    receiver: initial?.receiver ?? '',
    phone: initial?.phone ?? '',
    line: initial?.line ?? '',
    city: initial?.city ?? '',
    state: initial?.state ?? '',
    zip: initial?.zip ?? '',
    country: initial?.country ?? 'United States',
    isDefault: initial?.isDefault ?? false
  })
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((p) => ({ ...p, [key]: e.target.value }))

  const save = async () => {
    if (!form.receiver.trim() || !form.line.trim() || !form.city.trim() || !form.zip.trim() || !form.country.trim()) {
      setError('Please fill in all required fields.')
      return
    }
    setSaving(true)
    setError(null)
    const body: AddressUpsert = {
      receiver: form.receiver.trim(),
      phone: form.phone.trim() || undefined,
      line: form.line.trim(),
      city: form.city.trim(),
      state: form.state.trim() || undefined,
      zip: form.zip.trim(),
      country: form.country.trim(),
      isDefault: form.isDefault
    }
    try {
      if (initial) await tradingApi.updateAddress(initial.id, body)
      else await tradingApi.createAddress(body)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-lg animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <h2 className="font-display text-2xl font-medium">{initial ? 'Edit Address' : 'New Address'}</h2>
        <div className="mt-5 space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="addr-receiver" label="Full name" value={form.receiver} onChange={set('receiver')} />
            <Field id="addr-phone" label="Phone (optional)" value={form.phone} onChange={set('phone')} />
          </div>
          <Field id="addr-line" label="Address" value={form.line} onChange={set('line')} />
          <div className="grid gap-4 sm:grid-cols-3">
            <Field id="addr-city" label="City" value={form.city} onChange={set('city')} />
            <Field id="addr-state" label="State" value={form.state} onChange={set('state')} />
            <Field id="addr-zip" label="ZIP" value={form.zip} onChange={set('zip')} />
          </div>
          <div>
            <label className="eyebrow mb-1.5 block" htmlFor="addr-country">Country</label>
            <select id="addr-country" value={form.country} onChange={set('country')} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold">
              <option>United States</option><option>Canada</option><option>Australia</option><option>United Kingdom</option><option>France</option><option>Spain</option><option>Germany</option>
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={form.isDefault} onChange={(e) => setForm((p) => ({ ...p, isDefault: e.target.checked }))} className="accent-gold" />
            Set as default address
          </label>
          {error && <p className="text-xs text-blush">{error}</p>}
          <button onClick={() => void save()} disabled={saving} className="btn-primary w-full disabled:opacity-60">{saving ? 'Saving…' : 'Save Address'}</button>
        </div>
      </div>
    </div>
  )
}

function Field({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void }) {
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} value={value} onChange={onChange} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
    </div>
  )
}
