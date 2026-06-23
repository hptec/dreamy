'use client'

/**
 * /showroom（PAGE-SHR-S01，copy-adapt 自 hhspec/prototype/app/showroom/page.tsx）。
 * 客户端组件 + 登录守卫（未登录 → /account/login?returnTo=/showroom，与 Wishlist 同口径）。
 * 数据源 useStore().showrooms mock → showroomListStore（E-SHR-01/02/05）。
 * 显式偏离①：卡片封面三图 → 占位 + 计数（契约 ShowroomSummary 无 items，避免列表页 N 次详情请求）。
 * 显式偏离②：创建弹窗婚期改可选（契约 required 仅 name）。
 */

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { X, Plus, Users, CalendarDays, Trash2, ArrowRight, Shirt, PartyPopper } from 'lucide-react'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useShowroomListStore } from '@/lib/stores/showroom-store'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { daysUntil, formatDateLong } from '@/lib/utils'

export default function ShowroomListPage() {
  const router = useRouter()
  const { te } = useI18n()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const hydrated = useAuthStore((s) => s.hydrated)
  const hydrate = useAuthStore((s) => s.hydrate)
  const { items: showrooms, loading, fetched, fetch: fetchRooms, create, remove } = useShowroomListStore()
  const [modalOpen, setModalOpen] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    void hydrate()
  }, [hydrate])

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace('/account/login?returnTo=/showroom')
    }
  }, [hydrated, isAuthenticated, router])

  useEffect(() => {
    if (hydrated && isAuthenticated) void fetchRooms()
  }, [hydrated, isAuthenticated, fetchRooms])

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 2600)
    return () => clearTimeout(t)
  }, [toast])

  if (!hydrated || !isAuthenticated) {
    return <div className="container-luxe py-24" aria-hidden="true"><div className="h-10 w-64 animate-pulse rounded-sm bg-muted" /></div>
  }

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

      {loading && !fetched ? (
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {[0, 1, 2].map((i) => <div key={i} className="h-64 animate-pulse rounded-sm bg-muted" />)}
        </div>
      ) : showrooms.length === 0 ? (
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
            const days = s.weddingDate ? daysUntil(s.weddingDate) : -1
            return (
              <article key={s.id} className="group relative flex flex-col rounded-sm border border-line bg-surface transition-shadow hover:shadow-card">
                <Link href={`/showroom/${s.id}`} className="block p-6 pb-0">
                  <CoverCollage images={s.coverImages} itemCount={s.itemCount ?? 0} />
                  <h2 className="mt-4 font-display text-2xl font-medium leading-snug transition-colors group-hover:text-gold-deep">{s.name}</h2>
                </Link>
                <div className="flex flex-1 flex-col p-6 pt-2">
                  {s.weddingDate && (
                    <p className="flex items-center gap-1.5 text-sm text-ink-soft">
                      <CalendarDays className="h-3.5 w-3.5 text-gold" /> {formatDateLong(s.weddingDate)}
                      {days >= 0 && <span className="text-xs text-ink-faint">· {days} days to go</span>}
                    </p>
                  )}
                  <div className="mt-3 flex items-center gap-5 text-xs text-ink-soft">
                    <span className="flex items-center gap-1.5"><Shirt className="h-3.5 w-3.5 text-gold" /> {s.itemCount ?? 0} {s.itemCount === 1 ? 'style' : 'styles'}</span>
                    <span className="flex items-center gap-1.5"><Users className="h-3.5 w-3.5 text-gold" /> {s.memberCount ?? 0} {s.memberCount === 1 ? 'member' : 'members'}</span>
                  </div>
                  <div className="mt-5 flex items-center justify-between border-t border-line pt-4">
                    <Link href={`/showroom/${s.id}`} className="flex items-center gap-1.5 text-[12px] font-medium uppercase tracking-luxe text-ink transition-colors hover:text-gold-deep">
                      Open Showroom <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                    {confirmDelete === s.id ? (
                      <span className="flex items-center gap-2 text-xs">
                        <button
                          onClick={async () => {
                            try {
                              await remove(s.id)
                            } catch (err) {
                              setToast(err instanceof ApiError ? te(err.code) : te(50000))
                            }
                            setConfirmDelete(null)
                          }}
                          className="cursor-pointer font-medium text-blush underline"
                        >
                          Delete
                        </button>
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

      {modalOpen && (
        <CreateShowroomModal
          onClose={() => setModalOpen(false)}
          onCreate={async (name, date) => {
            await create(name, date)
          }}
        />
      )}

      {toast && (
        <div role="status" className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 animate-fadeup rounded-sm bg-ink px-5 py-3 text-sm text-canvas shadow-lift">
          {toast}
        </div>
      )}
    </div>
  )
}

/** 封面拼贴（E-SHR-02）：1-4 张商品图按数量自适应网格；空房回退到精致渐变占位 */
function CoverCollage({ images, itemCount }: { images?: string[]; itemCount: number }) {
  const pics = (images ?? []).slice(0, 4)

  if (pics.length === 0) {
    return (
      <div className="relative flex h-32 items-center justify-center overflow-hidden rounded-sm bg-gradient-to-br from-blush-light via-muted to-canvas">
        <div className="absolute inset-0 opacity-[0.06]" style={{ backgroundImage: 'radial-gradient(circle at 1px 1px, #2B2925 1px, transparent 0)', backgroundSize: '12px 12px' }} aria-hidden="true" />
        <span className="relative flex flex-col items-center gap-1.5 text-ink-faint">
          <Shirt className="h-6 w-6 text-gold" strokeWidth={1.5} />
          <span className="text-[11px] uppercase tracking-luxe">{itemCount === 0 ? 'No styles yet' : `${itemCount} ${itemCount === 1 ? 'style' : 'styles'} saved`}</span>
        </span>
      </div>
    )
  }

  return (
    <div className={`grid h-32 gap-0.5 overflow-hidden rounded-sm bg-muted ${pics.length === 1 ? 'grid-cols-1' : 'grid-cols-2'} ${pics.length >= 3 ? 'grid-rows-2' : ''}`}>
      {pics.map((src, i) => {
        // 3 图：首图占左列竖向通栏；其余右列堆叠
        const span = pics.length === 3 && i === 0 ? 'row-span-2' : ''
        return (
          // eslint-disable-next-line @next/next/no-img-element
          <img key={i} src={src} alt="" aria-hidden="true" className={`h-full w-full object-cover ${span}`} />
        )
      })}
    </div>
  )
}

/** CreateShowroomModal（COMP-SHR-S02，name 必填 + 婚期可选——显式偏离②） */
function CreateShowroomModal({ onClose, onCreate }: { onClose: () => void; onCreate: (name: string, date?: string) => Promise<void> }) {
  const { te } = useI18n()
  const [name, setName] = useState('')
  const [date, setDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    if (!name.trim()) { setError('Please enter a showroom name.'); return }
    setBusy(true)
    setError(null)
    try {
      await onCreate(name.trim(), date || undefined)
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
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
        <h2 className="mt-1 font-display text-2xl font-medium">New Showroom</h2>
        <div className="mt-5 space-y-4">
          <div>
            <label htmlFor="new-showroom-name" className="eyebrow mb-1.5 block">Showroom name</label>
            <input id="new-showroom-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Sarah's Bridal Party" className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
          </div>
          <div>
            <label htmlFor="new-showroom-date" className="eyebrow mb-1.5 block">Wedding date (optional)</label>
            <input id="new-showroom-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
          </div>
          {error && <p className="text-xs text-blush">{error}</p>}
          <button onClick={submit} disabled={busy} className="btn-primary w-full disabled:opacity-60">{busy ? 'Creating…' : 'Create Showroom'}</button>
        </div>
      </div>
    </div>
  )
}
