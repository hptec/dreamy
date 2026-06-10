'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import {
  ArrowLeft, CalendarDays, Users, Copy, Check, ThumbsUp, ThumbsDown, MessageCircle,
  Bell, Trash2, Clock, Eye, Crown, ShoppingBag, Send, PartyPopper
} from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { products } from '@/data/products'
import type { Showroom, ShowroomItem } from '@/data/types'
import { cn, daysUntil, formatDateLong, formatPrice } from '@/lib/utils'

type ViewMode = 'bride' | 'guest'

export function ShowroomDetail({ id }: { id: string }) {
  const { showrooms, removeFromShowroom, voteShowroomItem, commentShowroomItem, assignShowroomMember, currency } = useStore()
  const [mounted, setMounted] = useState(false)
  const [view, setView] = useState<ViewMode>('bride')
  const [persona, setPersona] = useState('')
  const [customPersona, setCustomPersona] = useState(false)
  const [copied, setCopied] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const [expandedComments, setExpandedComments] = useState<string | null>(null)

  useEffect(() => { setMounted(true) }, [])

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 2600)
    return () => clearTimeout(t)
  }, [toast])

  const room = showrooms.find((s) => s.id === id)

  // 访客默认以第一位伴娘身份参与（可改昵称，免注册）；无伴娘成员时直接用昵称输入
  useEffect(() => {
    if (room && !persona) {
      const firstMaid = room.members.find((m) => m.role === 'bridesmaid')
      if (firstMaid) setPersona(firstMaid.name)
      else { setCustomPersona(true); setPersona('Guest') }
    }
  }, [room, persona])

  if (!mounted) return <div className="container-luxe py-24" aria-hidden="true"><div className="skeleton h-10 w-64 rounded-sm" /></div>

  if (!room) {
    return (
      <div className="container-luxe py-24 text-center">
        <PartyPopper className="mx-auto h-12 w-12 text-line" strokeWidth={1} />
        <h1 className="mt-4 font-display text-3xl font-medium">Showroom not found</h1>
        <p className="mt-2 text-sm text-ink-soft">It may have been deleted, or the invite link is incorrect.</p>
        <Link href="/showroom" className="btn-primary mt-6 inline-flex">Back to My Showrooms</Link>
      </div>
    )
  }

  const days = daysUntil(room.weddingDate)
  const bride = room.members.find((m) => m.role === 'bride')
  const bridesmaids = room.members.filter((m) => m.role === 'bridesmaid')
  const someoneOrdered = room.members.some((m) => m.hasOrdered)
  const inviteLink = `https://dreamy.shop/showroom/${room.id}?invite=bridal-party`
  const me = room.members.find((m) => m.name === persona)
  const myAssignment = me?.assignedProductId ? products.find((p) => p.id === me.assignedProductId) : null

  const copyInvite = async () => {
    try {
      await navigator.clipboard.writeText(inviteLink)
    } catch {
      /* clipboard unavailable in some contexts — prototype still shows feedback */
    }
    setCopied(true)
    setToast('Invite link copied — share it with your party')
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="container-luxe py-10">
      {/* 返回 + 视图切换 */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <Link href="/showroom" className="flex items-center gap-1.5 text-[12px] font-medium uppercase tracking-luxe text-ink-soft transition-colors hover:text-ink">
          <ArrowLeft className="h-3.5 w-3.5" /> My Showrooms
        </Link>
        <div className="flex rounded-sm border border-line p-0.5" role="group" aria-label="Switch view">
          <button onClick={() => setView('bride')} className={cn('flex cursor-pointer items-center gap-1.5 rounded-sm px-4 py-2 text-[11px] font-medium uppercase tracking-luxe transition-colors', view === 'bride' ? 'bg-ink text-canvas' : 'text-ink-soft hover:text-ink')}>
            <Crown className="h-3.5 w-3.5" /> Bride view
          </button>
          <button onClick={() => setView('guest')} className={cn('flex cursor-pointer items-center gap-1.5 rounded-sm px-4 py-2 text-[11px] font-medium uppercase tracking-luxe transition-colors', view === 'guest' ? 'bg-ink text-canvas' : 'text-ink-soft hover:text-ink')}>
            <Eye className="h-3.5 w-3.5" /> View as guest
          </button>
        </div>
      </div>

      {/* 头部 */}
      <div className="mt-6">
        <p className="eyebrow">Showroom</p>
        <h1 className="mt-1 font-display text-4xl font-medium">{room.name}</h1>
        <div className="mt-3 flex flex-wrap items-center gap-5 text-sm text-ink-soft">
          <span className="flex items-center gap-1.5"><CalendarDays className="h-4 w-4 text-gold" /> {formatDateLong(room.weddingDate)}{days >= 0 && <span className="text-xs text-ink-faint">· {days} days to go</span>}</span>
          <span className="flex items-center gap-1.5"><Users className="h-4 w-4 text-gold" /> {room.members.length} members</span>
        </div>
      </div>

      {/* 访客身份条 */}
      {view === 'guest' && (
        <div className="mt-6 flex flex-wrap items-center gap-3 rounded-sm bg-muted p-4 text-sm">
          <p className="text-ink-soft">You&apos;re invited by <strong className="text-ink">{bride?.name ?? 'the bride'}</strong> — no account needed to vote or comment.</p>
          <div className="flex items-center gap-2">
            <label htmlFor="guest-persona" className="text-xs uppercase tracking-luxe text-ink-faint">I am</label>
            {customPersona ? (
              <input
                id="guest-persona"
                value={persona}
                onChange={(e) => setPersona(e.target.value)}
                placeholder="Your nickname"
                className="w-36 rounded-sm border border-line bg-canvas px-3 py-1.5 text-sm outline-none focus:border-gold"
              />
            ) : (
              <select
                id="guest-persona"
                value={persona}
                onChange={(e) => {
                  if (e.target.value === '__other__') { setCustomPersona(true); setPersona('') }
                  else setPersona(e.target.value)
                }}
                className="cursor-pointer rounded-sm border border-line bg-canvas px-3 py-1.5 text-sm outline-none focus:border-gold"
              >
                {bridesmaids.map((m) => <option key={m.id} value={m.name}>{m.name}</option>)}
                <option value="__other__">Someone else…</option>
              </select>
            )}
          </div>
        </div>
      )}

      {/* 邀请链接（新娘视图） */}
      {view === 'bride' && (
        <div className="mt-6 flex flex-col gap-3 rounded-sm border border-line bg-surface p-4 sm:flex-row sm:items-center">
          <p className="eyebrow shrink-0">Invite your party</p>
          <input readOnly value={inviteLink} aria-label="Invite link" className="min-w-0 flex-1 rounded-sm border border-line bg-muted px-3 py-2.5 text-xs text-ink-soft outline-none" onFocus={(e) => e.target.select()} />
          <button onClick={copyInvite} className="btn-outline shrink-0 px-5 py-2.5 text-xs">
            {copied ? <><Check className="h-3.5 w-3.5" /> Copied</> : <><Copy className="h-3.5 w-3.5" /> Copy Link</>}
          </button>
        </div>
      )}

      {/* F-071：24h dye lot 保证提示条 */}
      {someoneOrdered && (
        <div className="mt-4 flex items-start gap-2.5 rounded-sm bg-gold/10 px-4 py-3 text-sm text-gold-deep">
          <Clock className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            <strong>{room.members.find((m) => m.hasOrdered)?.name}</strong> has already ordered. Order within 24h of your party to guarantee the same dye lot — every dress cut from the same fabric batch.
          </p>
        </div>
      )}

      {/* 访客：我的指派 */}
      {view === 'guest' && me?.assignedProductId && myAssignment && (
        <div className="mt-6 flex flex-col gap-4 rounded-sm border border-gold/40 bg-gold/5 p-5 sm:flex-row sm:items-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={myAssignment.gallery[0]} alt={myAssignment.name} className="h-28 w-20 rounded-sm object-cover" />
          <div className="flex-1">
            <p className="eyebrow">Your assigned look, {persona}</p>
            <p className="mt-1 font-display text-xl font-medium">{myAssignment.name}</p>
            <p className="mt-0.5 text-sm text-ink-soft">Color: {me.assignedColor} · {formatPrice(myAssignment.price, currency)}</p>
          </div>
          <Link href={`/product/${myAssignment.slug}`} className="btn-primary shrink-0"><ShoppingBag className="h-4 w-4" /> Order This Dress</Link>
        </div>
      )}

      {/* 款式网格 */}
      <section className="mt-10">
        <h2 className="font-display text-2xl font-medium">Styles ({room.items.length})</h2>
        {room.items.length === 0 ? (
          <div className="mt-6 flex flex-col items-center gap-4 rounded-sm border border-dashed border-line py-16 text-center">
            <p className="max-w-sm text-sm text-ink-soft">No styles yet. Browse the collection and tap the showroom icon on any dress to add it here.</p>
            <Link href="/special-occasion" className="btn-outline">Browse Dresses</Link>
          </div>
        ) : (
          <div className="mt-6 grid gap-x-5 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
            {room.items.map((item) => (
              <ShowroomItemCard
                key={`${item.productId}-${item.color}`}
                room={room}
                item={item}
                view={view}
                persona={persona}
                currency={currency}
                expanded={expandedComments === `${item.productId}-${item.color}`}
                onToggleComments={() => setExpandedComments(expandedComments === `${item.productId}-${item.color}` ? null : `${item.productId}-${item.color}`)}
                onVote={(dir) => {
                  if (!persona.trim()) { setToast('Enter a nickname first to vote'); return }
                  voteShowroomItem(room.id, item.productId, item.color, persona.trim(), dir)
                }}
                onComment={(text) => {
                  if (!persona.trim()) { setToast('Enter a nickname first to comment'); return }
                  commentShowroomItem(room.id, item.productId, item.color, persona.trim(), text)
                }}
                onRemove={() => { removeFromShowroom(room.id, item.productId, item.color); setToast('Style removed from showroom') }}
              />
            ))}
          </div>
        )}
      </section>

      {/* 成员与指派（新娘视图，F-070） */}
      {view === 'bride' && (
        <section className="mt-14">
          <h2 className="font-display text-2xl font-medium">Party Members</h2>
          <p className="mt-1 text-sm text-ink-soft">Assign each bridesmaid a style and color, then send a gentle reminder to order.</p>
          <div className="mt-6 overflow-x-auto rounded-sm border border-line">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-line bg-muted/60 text-left text-[11px] uppercase tracking-luxe text-ink-soft">
                  <th className="px-4 py-3 font-medium">Member</th>
                  <th className="px-4 py-3 font-medium">Assigned style & color</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 text-right font-medium">Reminder</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-b border-line/60">
                  <td className="px-4 py-3.5">
                    <span className="flex items-center gap-2 font-medium"><Crown className="h-3.5 w-3.5 text-gold" /> {bride?.name ?? 'You'}</span>
                  </td>
                  <td className="px-4 py-3.5 text-ink-faint">— Bride</td>
                  <td className="px-4 py-3.5 text-ink-faint">—</td>
                  <td className="px-4 py-3.5" />
                </tr>
                {bridesmaids.map((m) => {
                  const assigned = m.assignedProductId ? products.find((p) => p.id === m.assignedProductId) : null
                  return (
                    <tr key={m.id} className="border-b border-line/60 last:border-0">
                      <td className="px-4 py-3.5 font-medium">{m.name}</td>
                      <td className="px-4 py-3.5">
                        <label className="sr-only" htmlFor={`assign-${m.id}`}>Assign style to {m.name}</label>
                        <select
                          id={`assign-${m.id}`}
                          value={m.assignedProductId ? `${m.assignedProductId}|${m.assignedColor ?? ''}` : ''}
                          onChange={(e) => {
                            const v = e.target.value
                            if (!v) { assignShowroomMember(room.id, m.id); return }
                            const [pid, color] = v.split('|')
                            assignShowroomMember(room.id, m.id, pid, color)
                            setToast(`${m.name} assigned ${products.find((p) => p.id === pid)?.name ?? 'style'} · ${color}`)
                          }}
                          className="w-full max-w-[16rem] cursor-pointer rounded-sm border border-line bg-canvas px-3 py-2 text-sm outline-none focus:border-gold"
                        >
                          <option value="">Unassigned</option>
                          {room.items.map((it) => {
                            const p = products.find((pp) => pp.id === it.productId)
                            return <option key={`${it.productId}|${it.color}`} value={`${it.productId}|${it.color}`}>{p?.name ?? it.productId} — {it.color}</option>
                          })}
                        </select>
                        {assigned && <span className="mt-1 block text-xs text-ink-faint">{formatPrice(assigned.price, currency)}</span>}
                      </td>
                      <td className="px-4 py-3.5">
                        {m.hasOrdered ? (
                          <span className="inline-flex items-center gap-1 rounded-sm bg-sage/15 px-2 py-1 text-xs text-sage-deep"><Check className="h-3 w-3" /> Ordered</span>
                        ) : m.assignedProductId ? (
                          <span className="inline-flex items-center rounded-sm bg-gold/10 px-2 py-1 text-xs text-gold-deep">Awaiting order</span>
                        ) : (
                          <span className="inline-flex items-center rounded-sm bg-muted px-2 py-1 text-xs text-ink-faint">Browsing</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        {m.assignedProductId && !m.hasOrdered && (
                          <button
                            onClick={() => setToast(`Reminder sent to ${m.name} (simulated — no real email in this prototype)`)}
                            className="inline-flex cursor-pointer items-center gap-1.5 rounded-sm border border-line px-3 py-1.5 text-xs transition-colors hover:border-gold hover:text-gold-deep"
                          >
                            <Bell className="h-3.5 w-3.5" /> Send reminder
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Toast */}
      {toast && (
        <div role="status" className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 animate-fadeup rounded-sm bg-ink px-5 py-3 text-sm text-canvas shadow-lift">
          {toast}
        </div>
      )}
    </div>
  )
}

function ShowroomItemCard({
  room, item, view, persona, currency, expanded, onToggleComments, onVote, onComment, onRemove
}: {
  room: Showroom
  item: ShowroomItem
  view: ViewMode
  persona: string
  currency: string
  expanded: boolean
  onToggleComments: () => void
  onVote: (dir: 'up' | 'down') => void
  onComment: (text: string) => void
  onRemove: () => void
}) {
  const [draft, setDraft] = useState('')
  const product = products.find((p) => p.id === item.productId)
  if (!product) return null
  const colorHex = product.colors.find((c) => c.name === item.color)?.hex
  const image = product.colors.find((c) => c.name === item.color)?.image ?? product.gallery[0]
  const assignedTo = room.members.filter((m) => m.assignedProductId === item.productId && m.assignedColor === item.color)
  const votedUp = item.upVotes.includes(persona)
  const votedDown = item.downVotes.includes(persona)

  const post = () => {
    if (!draft.trim()) return
    onComment(draft.trim())
    setDraft('')
  }

  return (
    <article className="flex flex-col rounded-sm border border-line bg-surface">
      <Link href={`/product/${product.slug}`} className="block overflow-hidden rounded-t-sm">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={image} alt={`${product.name} in ${item.color}`} className="aspect-[3/4] w-full object-cover transition-transform duration-700 ease-luxe hover:scale-105" />
      </Link>
      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-start justify-between gap-2">
          <Link href={`/product/${product.slug}`} className="text-sm font-medium leading-snug transition-colors hover:text-gold-deep">{product.name}</Link>
          {view === 'bride' && (
            <button onClick={onRemove} className="cursor-pointer p-1 text-ink-faint transition-colors hover:text-blush" aria-label={`Remove ${product.name} from showroom`}>
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
        <p className="mt-1 flex items-center gap-1.5 text-xs text-ink-soft">
          {colorHex && <span className="h-3 w-3 rounded-full border border-line" style={{ backgroundColor: colorHex }} aria-hidden="true" />}
          {item.color} · {formatPrice(product.price, currency)}
        </p>
        {assignedTo.length > 0 && (
          <p className="mt-1.5 text-[11px] uppercase tracking-luxe text-gold-deep">Assigned to {assignedTo.map((m) => m.name).join(', ')}</p>
        )}

        {/* 投票 + 留言计数 */}
        <div className="mt-3 flex items-center gap-2 border-t border-line pt-3">
          {view === 'guest' ? (
            <>
              <button
                onClick={() => onVote('up')}
                aria-pressed={votedUp}
                className={cn('flex cursor-pointer items-center gap-1.5 rounded-sm border px-3 py-1.5 text-xs transition-colors', votedUp ? 'border-sage bg-sage/15 text-sage-deep' : 'border-line text-ink-soft hover:border-sage hover:text-sage-deep')}
              >
                <ThumbsUp className="h-3.5 w-3.5" /> {item.upVotes.length}
              </button>
              <button
                onClick={() => onVote('down')}
                aria-pressed={votedDown}
                className={cn('flex cursor-pointer items-center gap-1.5 rounded-sm border px-3 py-1.5 text-xs transition-colors', votedDown ? 'border-blush bg-blush/15 text-blush' : 'border-line text-ink-soft hover:border-blush hover:text-blush')}
              >
                <ThumbsDown className="h-3.5 w-3.5" /> {item.downVotes.length}
              </button>
            </>
          ) : (
            <>
              <span className="flex items-center gap-1.5 rounded-sm bg-sage/10 px-3 py-1.5 text-xs text-sage-deep"><ThumbsUp className="h-3.5 w-3.5" /> {item.upVotes.length}</span>
              <span className="flex items-center gap-1.5 rounded-sm bg-blush/10 px-3 py-1.5 text-xs text-ink-soft"><ThumbsDown className="h-3.5 w-3.5" /> {item.downVotes.length}</span>
            </>
          )}
          <button onClick={onToggleComments} className={cn('ml-auto flex cursor-pointer items-center gap-1.5 rounded-sm border px-3 py-1.5 text-xs transition-colors', expanded ? 'border-ink text-ink' : 'border-line text-ink-soft hover:border-ink hover:text-ink')} aria-expanded={expanded}>
            <MessageCircle className="h-3.5 w-3.5" /> {item.comments.length}
          </button>
        </div>

        {/* 留言区 */}
        {expanded && (
          <div className="mt-3 space-y-2.5">
            {item.comments.length === 0 && <p className="text-xs text-ink-faint">No comments yet — be the first.</p>}
            {item.comments.map((c, i) => (
              <div key={`${c.author}-${i}`} className="rounded-sm bg-muted px-3 py-2.5">
                <p className="text-xs font-medium">{c.author} <span className="ml-1 font-normal text-ink-faint">{formatDateLong(c.date)}</span></p>
                <p className="mt-1 text-xs leading-relaxed text-ink-soft">{c.text}</p>
              </div>
            ))}
            {view === 'guest' && (
              <div className="flex gap-2">
                <label htmlFor={`comment-${item.productId}-${item.color}`} className="sr-only">Leave a comment</label>
                <input
                  id={`comment-${item.productId}-${item.color}`}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') post() }}
                  placeholder="Leave a comment…"
                  className="min-w-0 flex-1 rounded-sm border border-line bg-canvas px-3 py-2 text-xs outline-none focus:border-gold"
                />
                <button onClick={post} className="cursor-pointer rounded-sm border border-line p-2 transition-colors hover:border-gold hover:text-gold-deep" aria-label="Post comment">
                  <Send className="h-3.5 w-3.5" />
                </button>
              </div>
            )}
          </div>
        )}

        {view === 'guest' && (
          <Link href={`/product/${product.slug}`} className="btn-outline mt-4 w-full px-4 py-2.5 text-xs">
            <ShoppingBag className="h-3.5 w-3.5" /> Order This Dress
          </Link>
        )}
      </div>
    </article>
  )
}
