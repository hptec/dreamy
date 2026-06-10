'use client'

import { useState } from 'react'
import Link from 'next/link'
import { X, Plus, Users, CalendarDays, Trash2, ArrowRight, Shirt, PartyPopper } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { products } from '@/data/products'
import { daysUntil, formatDateLong } from '@/lib/utils'

export default function ShowroomListPage() {
  const { showrooms, createShowroom, deleteShowroom } = useStore()
  const [modalOpen, setModalOpen] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  return (
    <div className="container-luxe py-12">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Bridal Party Collaboration</p>
          <h1 className="mt-1 font-display text-4xl font-medium">My Showrooms</h1>
          <p className="mt-2 max-w-xl text-sm text-ink-soft">Collect styles, invite your party to vote and comment, and assign each bridesmaid her dress and color — all in one place.</p>
        </div>
        <button onClick={() => setModalOpen(true)} className="btn-primary self-start sm:self-auto"><Plus className="h-4 w-4" /> New Showroom</button>
      </div>

      {showrooms.length === 0 ? (
        <div className="mt-12 flex flex-col items-center gap-5 rounded-sm border border-dashed border-line py-20 text-center">
          <PartyPopper className="h-14 w-14 text-line" strokeWidth={1} />
          <div>
            <h2 className="font-display text-2xl font-medium">Start your first Showroom</h2>
            <p className="mx-auto mt-2 max-w-sm text-sm text-ink-soft">Name it after your big day, set the date, then add dresses you love from any product page.</p>
          </div>
          <div className="flex flex-wrap justify-center gap-3">
            <button onClick={() => setModalOpen(true)} className="btn-primary"><Plus className="h-4 w-4" /> Create a Showroom</button>
            <Link href="/special-occasion" className="btn-outline">Browse Bridesmaid Dresses</Link>
          </div>
        </div>
      ) : (
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {showrooms.map((s) => {
            const days = daysUntil(s.weddingDate)
            const covers = s.items.slice(0, 3).map((it) => products.find((p) => p.id === it.productId)).filter(Boolean)
            return (
              <article key={s.id} className="group relative flex flex-col rounded-sm border border-line bg-surface transition-shadow hover:shadow-card">
                <Link href={`/showroom/${s.id}`} className="block p-6 pb-0">
                  <div className="flex h-32 gap-2 overflow-hidden rounded-sm bg-muted">
                    {covers.length === 0 ? (
                      <div className="flex w-full items-center justify-center text-xs text-ink-faint">No styles yet</div>
                    ) : (
                      covers.map((p) => (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img key={p!.id} src={p!.gallery[0]} alt={p!.name} className="h-full min-w-0 flex-1 object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                      ))
                    )}
                  </div>
                  <h2 className="mt-4 font-display text-2xl font-medium leading-snug transition-colors group-hover:text-gold-deep">{s.name}</h2>
                </Link>
                <div className="flex flex-1 flex-col p-6 pt-2">
                  <p className="flex items-center gap-1.5 text-sm text-ink-soft">
                    <CalendarDays className="h-3.5 w-3.5 text-gold" /> {formatDateLong(s.weddingDate)}
                    {days >= 0 && <span className="text-xs text-ink-faint">· {days} days to go</span>}
                  </p>
                  <div className="mt-3 flex items-center gap-5 text-xs text-ink-soft">
                    <span className="flex items-center gap-1.5"><Shirt className="h-3.5 w-3.5 text-gold" /> {s.items.length} {s.items.length === 1 ? 'style' : 'styles'}</span>
                    <span className="flex items-center gap-1.5"><Users className="h-3.5 w-3.5 text-gold" /> {s.members.length} {s.members.length === 1 ? 'member' : 'members'}</span>
                  </div>
                  <div className="mt-5 flex items-center justify-between border-t border-line pt-4">
                    <Link href={`/showroom/${s.id}`} className="flex items-center gap-1.5 text-[12px] font-medium uppercase tracking-luxe text-ink transition-colors hover:text-gold-deep">
                      Open Showroom <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                    {confirmDelete === s.id ? (
                      <span className="flex items-center gap-2 text-xs">
                        <button onClick={() => { deleteShowroom(s.id); setConfirmDelete(null) }} className="cursor-pointer font-medium text-blush underline">Delete</button>
                        <button onClick={() => setConfirmDelete(null)} className="cursor-pointer text-ink-soft underline">Keep</button>
                      </span>
                    ) : (
                      <button onClick={() => setConfirmDelete(s.id)} className="cursor-pointer p-1 text-ink-faint transition-colors hover:text-blush" aria-label={`Delete ${s.name}`}>
                        <Trash2 className="h-4 w-4" />
                      </button>
                    )}
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      )}

      {modalOpen && <CreateShowroomModal onClose={() => setModalOpen(false)} createShowroom={createShowroom} />}
    </div>
  )
}

function CreateShowroomModal({ onClose, createShowroom }: { onClose: () => void; createShowroom: (name: string, date: string) => string | null }) {
  const [name, setName] = useState('')
  const [date, setDate] = useState('')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    if (!name.trim() || !date) { setError('Please enter a name and wedding date.'); return }
    const id = createShowroom(name.trim(), date)
    if (!id) { setError('Showroom limit reached for this demo.'); return }
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-md animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <p className="eyebrow">Bridal Party</p>
        <h2 className="mt-1 font-display text-2xl font-medium">New Showroom</h2>
        <div className="mt-5 space-y-4">
          <div>
            <label htmlFor="new-showroom-name" className="eyebrow mb-1.5 block">Showroom name</label>
            <input id="new-showroom-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Sarah's Bridal Party" className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
          </div>
          <div>
            <label htmlFor="new-showroom-date" className="eyebrow mb-1.5 block">Wedding date</label>
            <input id="new-showroom-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
          </div>
          {error && <p className="text-xs text-blush">{error}</p>}
          <button onClick={submit} className="btn-primary w-full">Create Showroom</button>
        </div>
      </div>
    </div>
  )
}
