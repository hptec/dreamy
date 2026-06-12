'use client'

/**
 * ShowroomDetail（COMP-SHR-S03~S10，copy-adapt 自 hhspec/prototype/components/showroom/showroom-detail.tsx）。
 * 结构保真：返回链接/头部/邀请条/dye lot 条/款式网格/成员表/Toast。
 * 显式偏离（showroom-frontend §6）：
 * ③ 原型 bride/guest 手动切换按钮组移除——视图由 API is_owner 驱动；
 * ④ persona 下拉 → GuestJoinGate 昵称 + invite_token 换 guest JWT（决策 20.2）；
 * ⑤ owner 与 guest 均可投票（契约 E-SHR-10）；
 * ⑥ 成员表新增 email 输入（决策 20.5 提醒邮箱）；
 * ⑦ Status 四态（补 Reminded）；
 * ⑨ 邀请条新增 Reset link（E-SHR-06，二次确认 CP-071）。
 */

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import {
  ArrowLeft, CalendarDays, Users, Copy, Check, ThumbsUp, ThumbsDown, MessageCircle,
  Bell, Trash2, Clock, Crown, ShoppingBag, Send, PartyPopper, Pencil, RefreshCw, X
} from 'lucide-react'
import type { ShowroomDetail as ShowroomDetailDTO, ShowroomItem, ShowroomMember } from '@/lib/api/store-types'
import { AssignStatus, VoteValue } from '@/lib/api/store-types'
import { useShowroomDetailStore } from '@/lib/stores/showroom-store'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn, daysUntil, formatDateLong, formatPrice, formatDateTimeLong } from '@/lib/utils'
import { useStore } from '@/components/store-provider'

export function ShowroomDetailView({ id }: { id: number }) {
  const router = useRouter()
  const params = useSearchParams()
  const inviteParam = params.get('invite')
  const { te } = useI18n()
  const { currency } = useStore()

  const room = useShowroomDetailStore((s) => s.room)
  const identity = useShowroomDetailStore((s) => s.identity)
  const gate = useShowroomDetailStore((s) => s.gate)
  const resolve = useShowroomDetailStore((s) => s.resolve)
  const join = useShowroomDetailStore((s) => s.join)
  const vote = useShowroomDetailStore((s) => s.vote)
  const comment = useShowroomDetailStore((s) => s.comment)
  const removeItem = useShowroomDetailStore((s) => s.removeItem)
  const assign = useShowroomDetailStore((s) => s.assign)
  const remind = useShowroomDetailStore((s) => s.remind)
  const updateProfile = useShowroomDetailStore((s) => s.updateProfile)
  const resetInvite = useShowroomDetailStore((s) => s.resetInvite)
  const guestSession = useShowroomDetailStore((s) => s.guestSession)

  const [toast, setToast] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [expandedComments, setExpandedComments] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [confirmReset, setConfirmReset] = useState(false)
  const resolvedOnce = useRef(false)

  useEffect(() => {
    if (resolvedOnce.current) return
    resolvedOnce.current = true
    void resolve(id, inviteParam)
  }, [id, inviteParam, resolve])

  useEffect(() => {
    if (gate === 'login-required') {
      const query = inviteParam ? `?invite=${encodeURIComponent(inviteParam)}` : ''
      router.replace(`/account/login?returnTo=${encodeURIComponent(`/showroom/${id}${query}`)}`)
    }
  }, [gate, id, inviteParam, router])

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 2600)
    return () => clearTimeout(t)
  }, [toast])

  if (gate === 'loading' || gate === 'login-required') {
    return <div className="container-luxe py-24" aria-hidden="true"><div className="h-10 w-64 animate-pulse rounded-sm bg-muted" /></div>
  }

  // 404 防探测通用页（COMP-SHR-S10）
  if (gate === 'not-found' || gate === 'error') {
    return (
      <div className="container-luxe py-24 text-center">
        <PartyPopper className="mx-auto h-12 w-12 text-line" strokeWidth={1} />
        <h1 className="mt-4 font-display text-3xl font-medium">Showroom not found</h1>
        <p className="mt-2 text-sm text-ink-soft">{gate === 'error' ? te(50000) : 'It may have been deleted, or you don’t have access to it.'}</p>
        <Link href="/showroom" className="btn-primary mt-6 inline-flex">Back to My Showrooms</Link>
      </div>
    )
  }

  if (gate === 'invite-revoked' || gate === 'guest-expired') {
    return (
      <div className="container-luxe py-24 text-center">
        <Clock className="mx-auto h-12 w-12 text-line" strokeWidth={1} />
        <h1 className="mt-4 font-display text-3xl font-medium">{gate === 'invite-revoked' ? 'Invite link expired' : 'Access link expired'}</h1>
        <p className="mx-auto mt-2 max-w-sm text-sm text-ink-soft">{te(gate === 'invite-revoked' ? 410101 : 401101)}</p>
        <Link href="/" className="btn-outline mt-6 inline-flex">Back to Home</Link>
      </div>
    )
  }

  if (gate === 'join') {
    return <GuestJoinGate id={id} inviteToken={inviteParam ?? ''} onJoin={join} />
  }

  if (!room) return null

  const isOwner = identity === 'owner' && room.isOwner
  const days = room.weddingDate ? daysUntil(room.weddingDate) : -1
  const dyeLotActive = room.items.some((it) => it.dyeLotNotice)
  const myNickname = guestSession?.nickname
  const me = room.members.find((m) => m.id === (room.myMemberId ?? guestSession?.memberId))
  const myAssignment = me?.assignedItemId ? room.items.find((it) => it.id === me.assignedItemId) : null
  const inviteLink = room.inviteToken && typeof window !== 'undefined'
    ? `${window.location.origin}/showroom/${room.id}?invite=${room.inviteToken}`
    : ''

  const copyInvite = async () => {
    try {
      await navigator.clipboard.writeText(inviteLink)
    } catch {
      /* clipboard unavailable — still show feedback */
    }
    setCopied(true)
    setToast('Invite link copied — share it with your party')
    setTimeout(() => setCopied(false), 2000)
  }

  const doResetInvite = async () => {
    try {
      await resetInvite()
      setToast('Invite link has been reset')
    } catch (err) {
      setToast(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setConfirmReset(false)
    }
  }

  const onVote = async (item: ShowroomItem, dir: VoteValue) => {
    try {
      await vote(item.id, dir)
    } catch (err) {
      if (err instanceof ApiError && err.code === 401101) {
        setToast(te(401101))
        void resolve(id, inviteParam)
      } else {
        setToast(err instanceof ApiError ? te(err.code) : te(50000))
      }
    }
  }

  const onComment = async (item: ShowroomItem, text: string) => {
    try {
      await comment(item.id, text)
    } catch (err) {
      setToast(err instanceof ApiError ? te(err.code) : te(50000))
    }
  }

  return (
    <div className="container-luxe py-10">
      {/* 返回 */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        {isOwner ? (
          <Link href="/showroom" className="flex items-center gap-1.5 text-[12px] font-medium uppercase tracking-luxe text-ink-soft transition-colors hover:text-ink">
            <ArrowLeft className="h-3.5 w-3.5" /> My Showrooms
          </Link>
        ) : (
          <Link href="/special-occasion" className="flex items-center gap-1.5 text-[12px] font-medium uppercase tracking-luxe text-ink-soft transition-colors hover:text-ink">
            <ArrowLeft className="h-3.5 w-3.5" /> Browse Dresses
          </Link>
        )}
      </div>

      {/* 头部 */}
      <div className="mt-6">
        <p className="eyebrow">Showroom</p>
        <div className="flex items-center gap-3">
          <h1 className="mt-1 font-display text-4xl font-medium">{room.name}</h1>
          {isOwner && (
            <button onClick={() => setEditOpen(true)} className="mt-1 cursor-pointer p-1.5 text-ink-faint transition-colors hover:text-gold-deep" aria-label="Edit showroom">
              <Pencil className="h-4 w-4" />
            </button>
          )}
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-5 text-sm text-ink-soft">
          {room.weddingDate && (
            <span className="flex items-center gap-1.5">
              <CalendarDays className="h-4 w-4 text-gold" /> {formatDateLong(room.weddingDate)}
              {days >= 0 && <span className="text-xs text-ink-faint">· {days} days to go</span>}
            </span>
          )}
          <span className="flex items-center gap-1.5"><Users className="h-4 w-4 text-gold" /> {room.members.length} members</span>
        </div>
      </div>

      {/* 访客身份条（guest 视图） */}
      {!isOwner && (
        <div className="mt-6 flex flex-wrap items-center gap-3 rounded-sm bg-muted p-4 text-sm">
          <p className="text-ink-soft">
            You&apos;re invited — no account needed to vote or comment.
            {myNickname && <> Participating as <strong className="text-ink">{myNickname}</strong>.</>}
          </p>
        </div>
      )}

      {/* 邀请条（owner 视图，COMP-SHR-S08） */}
      {isOwner && room.inviteToken && (
        <div className="mt-6 flex flex-col gap-3 rounded-sm border border-line bg-surface p-4 sm:flex-row sm:items-center">
          <p className="eyebrow shrink-0">Invite your party</p>
          <input readOnly value={inviteLink} aria-label="Invite link" className="min-w-0 flex-1 rounded-sm border border-line bg-muted px-3 py-2.5 text-xs text-ink-soft outline-none" onFocus={(e) => e.target.select()} />
          <button onClick={copyInvite} className="btn-outline shrink-0 px-5 py-2.5 text-xs">
            {copied ? <><Check className="h-3.5 w-3.5" /> Copied</> : <><Copy className="h-3.5 w-3.5" /> Copy Link</>}
          </button>
          {confirmReset ? (
            <span className="flex shrink-0 items-center gap-2 text-xs">
              <span className="text-ink-soft">Old link & guest sessions will stop working.</span>
              <button onClick={doResetInvite} className="cursor-pointer font-medium text-blush underline">Reset</button>
              <button onClick={() => setConfirmReset(false)} className="cursor-pointer text-ink-soft underline">Keep</button>
            </span>
          ) : (
            <button onClick={() => setConfirmReset(true)} className="btn-outline shrink-0 px-5 py-2.5 text-xs">
              <RefreshCw className="h-3.5 w-3.5" /> Reset link
            </button>
          )}
        </div>
      )}

      {/* F-071：24h dye lot 保证提示条（COMP-SHR-S09，items.dye_lot_notice 驱动） */}
      {dyeLotActive && (
        <div className="mt-4 flex items-start gap-2.5 rounded-sm bg-gold/10 px-4 py-3 text-sm text-gold-deep">
          <Clock className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Someone in your party has already ordered. Order within 24h of your party to guarantee the same dye lot — every dress cut from the same fabric batch.
          </p>
        </div>
      )}

      {/* 访客：我的指派 */}
      {!isOwner && myAssignment && (
        <div className="mt-6 flex flex-col gap-4 rounded-sm border border-gold/40 bg-gold/5 p-5 sm:flex-row sm:items-center">
          {myAssignment.product.imageUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={myAssignment.product.imageUrl} alt={myAssignment.product.name} className="h-28 w-20 rounded-sm object-cover" />
          )}
          <div className="flex-1">
            <p className="eyebrow">Your assigned look{myNickname ? `, ${myNickname}` : ''}</p>
            <p className="mt-1 font-display text-xl font-medium">{myAssignment.product.name}</p>
            <p className="mt-0.5 text-sm text-ink-soft">
              {myAssignment.color ? `Color: ${myAssignment.color} · ` : ''}{formatPrice(myAssignment.product.price, currency)}
            </p>
          </div>
          <Link href={`/product/${myAssignment.product.slug}`} className="btn-primary shrink-0"><ShoppingBag className="h-4 w-4" /> Order This Dress</Link>
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
                key={item.id}
                room={room}
                item={item}
                isOwner={isOwner}
                currency={currency}
                expanded={expandedComments === item.id}
                onToggleComments={() => setExpandedComments(expandedComments === item.id ? null : item.id)}
                onVote={(dir) => onVote(item, dir)}
                onComment={(text) => onComment(item, text)}
                onRemove={async () => {
                  try {
                    await removeItem(item.id)
                    setToast('Style removed from showroom')
                  } catch (err) {
                    setToast(err instanceof ApiError ? te(err.code) : te(50000))
                  }
                }}
              />
            ))}
          </div>
        )}
      </section>

      {/* 成员与指派（owner 视图，F-070 / COMP-SHR-S06） */}
      {isOwner && (
        <MembersTable
          room={room}
          currency={currency}
          onAssign={async (member, itemId, email) => {
            try {
              await assign(member.id, itemId, email)
              const it = room.items.find((i) => i.id === itemId)
              setToast(`${member.nickname} assigned ${it?.product.name ?? 'style'}${it?.color ? ` · ${it.color}` : ''}`)
            } catch (err) {
              if (err instanceof ApiError && err.code === 409103) {
                setToast('This member has already ordered — assignment locked.')
              } else if (err instanceof ApiError && err.code === 404102) {
                setToast('That style was removed from the showroom.')
              } else {
                setToast(err instanceof ApiError ? te(err.code) : te(50000))
              }
              void useShowroomDetailStore.getState().refetch(room.id)
            }
          }}
          onRemind={async (member) => {
            try {
              const before = member.email
              await remind(member.id)
              setToast(`Reminder email sent${before ? ` to ${before}` : ''}`)
            } catch (err) {
              if (err instanceof ApiError && err.code === 409103) {
                const reason = (err.details as Record<string, unknown> | null)?.reason
                setToast(reason === 'email_missing' ? 'Add the member’s email before sending a reminder.' : 'Assign a style before sending a reminder.')
              } else {
                setToast(err instanceof ApiError ? te(err.code) : te(50000))
              }
            }
          }}
        />
      )}

      {/* 编辑弹窗（E-SHR-04，复用创建弹窗结构） */}
      {editOpen && (
        <EditShowroomModal
          room={room}
          onClose={() => setEditOpen(false)}
          onSave={async (name, date) => {
            await updateProfile(name, date)
            setEditOpen(false)
            setToast('Showroom updated')
          }}
        />
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

/** GuestJoinGate（COMP-SHR-S04，改造自原型访客身份条）：昵称 + Join 换 guest JWT（决策 20.2） */
function GuestJoinGate({ id, inviteToken, onJoin }: { id: number; inviteToken: string; onJoin: (id: number, token: string, nickname: string) => Promise<void> }) {
  const { te } = useI18n()
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [welcome, setWelcome] = useState(false)

  const submit = async () => {
    const trimmed = nickname.trim()
    if (!trimmed) { setError('Please enter a nickname to join.'); return }
    if (trimmed.length > 32) { setError('Nickname must be 32 characters or less.'); return }
    setBusy(true)
    setError(null)
    try {
      await onJoin(id, inviteToken, trimmed)
      setWelcome(true)
    } catch (err) {
      if (err instanceof ApiError && err.code === 409101) setError(te(409101))
      else if (err instanceof ApiError && (err.code === 410101 || err.code === 401101)) setError(te(err.code))
      else if (err instanceof ApiError) setError(te(err.code))
      else setError(te(50000))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container-luxe py-20">
      <div className="mx-auto max-w-md rounded-sm border border-line bg-surface p-7 text-center">
        <PartyPopper className="mx-auto h-10 w-10 text-gold" strokeWidth={1.25} />
        <p className="eyebrow mt-4">Bridal Party Invitation</p>
        <h1 className="mt-1 font-display text-3xl font-medium">Join the Showroom</h1>
        <p className="mt-2 text-sm text-ink-soft">You&apos;re invited — no account needed to vote or comment on the looks.</p>
        <div className="mt-6 flex items-center gap-2">
          <label htmlFor="guest-nickname" className="shrink-0 text-xs uppercase tracking-luxe text-ink-faint">I am</label>
          <input
            id="guest-nickname"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void submit() }}
            placeholder="Your nickname"
            maxLength={48}
            className="min-w-0 flex-1 rounded-sm border border-line bg-canvas px-3 py-2.5 text-sm outline-none focus:border-gold"
          />
          <button onClick={submit} disabled={busy} className="btn-primary shrink-0 px-5 py-2.5 text-xs disabled:opacity-60">{busy ? 'Joining…' : 'Join'}</button>
        </div>
        {error && <p className="mt-3 text-xs text-blush">{error}</p>}
        {welcome && <p className="mt-3 text-xs text-sage-deep">Welcome, {nickname.trim()}!</p>}
      </div>
    </div>
  )
}

function ShowroomItemCard({
  room, item, isOwner, currency, expanded, onToggleComments, onVote, onComment, onRemove
}: {
  room: ShowroomDetailDTO
  item: ShowroomItem
  isOwner: boolean
  currency: string
  expanded: boolean
  onToggleComments: () => void
  onVote: (dir: VoteValue) => void
  onComment: (text: string) => void
  onRemove: () => void
}) {
  const [draft, setDraft] = useState('')
  const [confirmRemove, setConfirmRemove] = useState(false)
  const product = item.product
  const assignedTo = room.members.filter((m) => m.assignedItemId === item.id)
  const comments = item.comments ?? []
  const votedUp = item.myVote === VoteValue.LIKE
  const votedDown = item.myVote === VoteValue.DISLIKE

  const post = () => {
    if (!draft.trim()) return
    onComment(draft.trim())
    setDraft('')
  }

  return (
    <article className="flex flex-col rounded-sm border border-line bg-surface">
      <Link href={`/product/${product.slug}`} className="relative block overflow-hidden rounded-t-sm">
        {product.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={product.imageUrl} alt={`${product.name}${item.color ? ` in ${item.color}` : ''}`} className="aspect-[3/4] w-full object-cover transition-transform duration-700 ease-luxe hover:scale-105" />
        ) : (
          <div className="aspect-[3/4] w-full bg-muted" />
        )}
        {item.dyeLotNotice && (
          <span className="absolute left-2 top-2 rounded-sm bg-gold/90 px-2 py-1 text-[10px] font-medium uppercase tracking-luxe text-white">Dye lot 24h</span>
        )}
      </Link>
      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-start justify-between gap-2">
          <Link href={`/product/${product.slug}`} className="text-sm font-medium leading-snug transition-colors hover:text-gold-deep">{product.name}</Link>
          {isOwner && (
            confirmRemove ? (
              <span className="flex shrink-0 items-center gap-1.5 text-xs">
                <button onClick={() => { onRemove(); setConfirmRemove(false) }} className="cursor-pointer font-medium text-blush underline">Remove</button>
                <button onClick={() => setConfirmRemove(false)} className="cursor-pointer text-ink-soft underline">Keep</button>
              </span>
            ) : (
              <button onClick={() => setConfirmRemove(true)} className="cursor-pointer p-1 text-ink-faint transition-colors hover:text-blush" aria-label={`Remove ${product.name} from showroom`}>
                <Trash2 className="h-4 w-4" />
              </button>
            )
          )}
        </div>
        <p className="mt-1 flex items-center gap-1.5 text-xs text-ink-soft">
          {item.color ? `${item.color} · ` : ''}{formatPrice(product.price, currency)}
        </p>
        {assignedTo.length > 0 && (
          <p className="mt-1.5 text-[11px] uppercase tracking-luxe text-gold-deep">Assigned to {assignedTo.map((m) => m.nickname).join(', ')}</p>
        )}

        {/* 投票（owner 与 guest 均可投，显式偏离⑤） + 留言计数 */}
        <div className="mt-3 flex items-center gap-2 border-t border-line pt-3">
          <button
            onClick={() => onVote(VoteValue.LIKE)}
            aria-pressed={votedUp}
            className={cn('flex cursor-pointer items-center gap-1.5 rounded-sm border px-3 py-1.5 text-xs transition-colors', votedUp ? 'border-sage bg-sage/15 text-sage-deep' : 'border-line text-ink-soft hover:border-sage hover:text-sage-deep')}
          >
            <ThumbsUp className="h-3.5 w-3.5" /> {item.likeCount}
          </button>
          <button
            onClick={() => onVote(VoteValue.DISLIKE)}
            aria-pressed={votedDown}
            className={cn('flex cursor-pointer items-center gap-1.5 rounded-sm border px-3 py-1.5 text-xs transition-colors', votedDown ? 'border-blush bg-blush/15 text-blush' : 'border-line text-ink-soft hover:border-blush hover:text-blush')}
          >
            <ThumbsDown className="h-3.5 w-3.5" /> {item.dislikeCount}
          </button>
          <button onClick={onToggleComments} className={cn('ml-auto flex cursor-pointer items-center gap-1.5 rounded-sm border px-3 py-1.5 text-xs transition-colors', expanded ? 'border-ink text-ink' : 'border-line text-ink-soft hover:border-ink hover:text-ink')} aria-expanded={expanded}>
            <MessageCircle className="h-3.5 w-3.5" /> {comments.length}
          </button>
        </div>

        {/* 留言区 */}
        {expanded && (
          <div className="mt-3 space-y-2.5">
            {comments.length === 0 && <p className="text-xs text-ink-faint">No comments yet — be the first.</p>}
            {comments.map((c) => (
              <div key={c.id} className="rounded-sm bg-muted px-3 py-2.5">
                <p className="text-xs font-medium">{c.nickname} {c.createdAt && <span className="ml-1 font-normal text-ink-faint">{formatDateTimeLong(c.createdAt)}</span>}</p>
                <p className="mt-1 text-xs leading-relaxed text-ink-soft">{c.content}</p>
              </div>
            ))}
            <div className="flex gap-2">
              <label htmlFor={`comment-${item.id}`} className="sr-only">Leave a comment</label>
              <input
                id={`comment-${item.id}`}
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') post() }}
                maxLength={500}
                placeholder="Leave a comment…"
                className="min-w-0 flex-1 rounded-sm border border-line bg-canvas px-3 py-2 text-xs outline-none focus:border-gold"
              />
              <button onClick={post} className="cursor-pointer rounded-sm border border-line p-2 transition-colors hover:border-gold hover:text-gold-deep" aria-label="Post comment">
                <Send className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}

        {!isOwner && (
          <Link href={`/product/${product.slug}`} className="btn-outline mt-4 w-full px-4 py-2.5 text-xs">
            <ShoppingBag className="h-3.5 w-3.5" /> Order This Dress
          </Link>
        )}
      </div>
    </article>
  )
}

/** MembersTable（COMP-SHR-S06）：四列保真 + email 输入（⑥）+ Status 四态（⑦）+ 真发提醒（决策 20.5） */
function MembersTable({
  room,
  currency,
  onAssign,
  onRemind
}: {
  room: ShowroomDetailDTO
  currency: string
  onAssign: (member: ShowroomMember, itemId: number, email?: string) => Promise<void>
  onRemind: (member: ShowroomMember) => Promise<void>
}) {
  const [emailDrafts, setEmailDrafts] = useState<Record<number, string>>({})
  const [emailHints, setEmailHints] = useState<Record<number, string | null>>({})
  const members = room.members.filter((m) => m.id !== room.myMemberId)
  const owner = room.members.find((m) => m.id === room.myMemberId)

  const emailOf = (m: ShowroomMember) => emailDrafts[m.id] ?? m.email ?? ''

  const handleAssign = async (m: ShowroomMember, value: string) => {
    if (!value) return
    const itemId = Number(value)
    const email = emailOf(m).trim()
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailHints((p) => ({ ...p, [m.id]: 'Please enter a valid email address.' }))
      return
    }
    if (!email) {
      // email 契约可选：行内提示仍可提交（FORM-SHR-S05）
      setEmailHints((p) => ({ ...p, [m.id]: 'Add an email so we can send order reminders.' }))
    } else {
      setEmailHints((p) => ({ ...p, [m.id]: null }))
    }
    await onAssign(m, itemId, email || undefined)
  }

  return (
    <section className="mt-14">
      <h2 className="font-display text-2xl font-medium">Party Members</h2>
      <p className="mt-1 text-sm text-ink-soft">Assign each bridesmaid a style and color, then send a gentle reminder to order.</p>
      <div className="mt-6 overflow-x-auto rounded-sm border border-line">
        <table className="w-full min-w-[720px] text-sm">
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
                <span className="flex items-center gap-2 font-medium"><Crown className="h-3.5 w-3.5 text-gold" /> {owner?.nickname ?? 'You'}</span>
              </td>
              <td className="px-4 py-3.5 text-ink-faint">— Bride</td>
              <td className="px-4 py-3.5 text-ink-faint">—</td>
              <td className="px-4 py-3.5" />
            </tr>
            {members.length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-sm text-ink-soft">No members yet — share the invite link above.</td>
              </tr>
            )}
            {members.map((m) => {
              const assigned = m.assignedItemId ? room.items.find((it) => it.id === m.assignedItemId) : null
              const canRemind = (m.assignStatus === AssignStatus.ASSIGNED || m.assignStatus === AssignStatus.REMINDED) && !!(m.email || emailDrafts[m.id])
              return (
                <tr key={m.id} className="border-b border-line/60 last:border-0">
                  <td className="px-4 py-3.5 font-medium">{m.nickname}</td>
                  <td className="px-4 py-3.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <label className="sr-only" htmlFor={`assign-${m.id}`}>Assign style to {m.nickname}</label>
                      <select
                        id={`assign-${m.id}`}
                        value={m.assignedItemId ?? ''}
                        disabled={m.assignStatus === AssignStatus.ORDERED}
                        onChange={(e) => void handleAssign(m, e.target.value)}
                        className="w-full max-w-[15rem] cursor-pointer rounded-sm border border-line bg-canvas px-3 py-2 text-sm outline-none focus:border-gold disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <option value="">Unassigned</option>
                        {room.items.map((it) => (
                          <option key={it.id} value={it.id}>{it.product.name}{it.color ? ` — ${it.color}` : ''}</option>
                        ))}
                      </select>
                      <label className="sr-only" htmlFor={`email-${m.id}`}>Email for {m.nickname}</label>
                      <input
                        id={`email-${m.id}`}
                        type="email"
                        value={emailOf(m)}
                        disabled={m.assignStatus === AssignStatus.ORDERED}
                        onChange={(e) => setEmailDrafts((p) => ({ ...p, [m.id]: e.target.value }))}
                        placeholder="member@email.com"
                        className="w-full max-w-[13rem] rounded-sm border border-line bg-canvas px-3 py-2 text-sm outline-none focus:border-gold disabled:opacity-60"
                      />
                    </div>
                    {assigned && <span className="mt-1 block text-xs text-ink-faint">{formatPrice(assigned.product.price, currency)}</span>}
                    {emailHints[m.id] && <span className="mt-1 block text-xs text-gold-deep">{emailHints[m.id]}</span>}
                  </td>
                  <td className="px-4 py-3.5">
                    {m.assignStatus === AssignStatus.ORDERED ? (
                      <span className="inline-flex items-center gap-1 rounded-sm bg-sage/15 px-2 py-1 text-xs text-sage-deep"><Check className="h-3 w-3" /> Ordered</span>
                    ) : m.assignStatus === AssignStatus.REMINDED ? (
                      <span className="inline-flex items-center gap-1 rounded-sm bg-gold/15 px-2 py-1 text-xs text-gold-deep"><Bell className="h-3 w-3" /> Reminded</span>
                    ) : m.assignStatus === AssignStatus.ASSIGNED ? (
                      <span className="inline-flex items-center rounded-sm bg-gold/10 px-2 py-1 text-xs text-gold-deep">Awaiting order</span>
                    ) : (
                      <span className="inline-flex items-center rounded-sm bg-muted px-2 py-1 text-xs text-ink-faint">Browsing</span>
                    )}
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    {canRemind && (
                      <button
                        onClick={() => void onRemind(m)}
                        className="inline-flex cursor-pointer items-center gap-1.5 rounded-sm border border-line px-3 py-1.5 text-xs transition-colors hover:border-gold hover:text-gold-deep"
                      >
                        <Bell className="h-3.5 w-3.5" /> {m.assignStatus === AssignStatus.REMINDED ? 'Send again' : 'Send reminder'}
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
  )
}

/** 编辑弹窗（复用创建弹窗结构，E-SHR-04 婚期变更 → 结算自动带入随之更新 F-077） */
function EditShowroomModal({ room, onClose, onSave }: { room: ShowroomDetailDTO; onClose: () => void; onSave: (name: string, date?: string) => Promise<void> }) {
  const { te } = useI18n()
  const [name, setName] = useState(room.name)
  const [date, setDate] = useState(room.weddingDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    if (!name.trim()) { setError('Please enter a showroom name.'); return }
    setBusy(true)
    setError(null)
    try {
      await onSave(name.trim(), date || undefined)
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
        <h2 className="mt-1 font-display text-2xl font-medium">Edit Showroom</h2>
        <div className="mt-5 space-y-4">
          <div>
            <label htmlFor="edit-showroom-name" className="eyebrow mb-1.5 block">Showroom name</label>
            <input id="edit-showroom-name" value={name} onChange={(e) => setName(e.target.value)} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
          </div>
          <div>
            <label htmlFor="edit-showroom-date" className="eyebrow mb-1.5 block">Wedding date (optional)</label>
            <input id="edit-showroom-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
          </div>
          {error && <p className="text-xs text-blush">{error}</p>}
          <button onClick={submit} disabled={busy} className="btn-primary w-full disabled:opacity-60">{busy ? 'Saving…' : 'Save Changes'}</button>
        </div>
      </div>
    </div>
  )
}
