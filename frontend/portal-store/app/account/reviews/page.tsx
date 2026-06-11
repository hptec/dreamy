'use client'

/**
 * 我的评价（F-049 / PAGE：/account/reviews）：
 * - 数据：GET /api/store/reviews/mine（Paginated<MyReview>，按当前 user_id 服务端过滤）。
 * - 卡片字段对齐 store 评价卡（Stars/日期/内容/买家秀缩略图/官方回复小卡）+ 商品摘要跳 PDP
 *   + 审核状态徽标（pending/approved/rejected）。
 * - 风格与 account/orders 一致：骨架/错误重试/空态/服务端分页 Load more。
 */

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { X } from 'lucide-react'
import type { MyReview } from '@/lib/api/store-types'
import { listMyReviews } from '@/lib/api/review-api'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ApiError } from '@/lib/api/client'
import { Stars } from '@/components/ui/primitives'
import { formatDateTimeLong, cn } from '@/lib/utils'

/** 审核状态徽标（沿用 order-ui 徽章 token 风格） */
const statusBadge: Record<MyReview['status'], { label: string; className: string }> = {
  pending: { label: 'Pending review', className: 'bg-gold/15 text-gold-deep' },
  approved: { label: 'Approved', className: 'bg-sage/15 text-sage-deep' },
  rejected: { label: 'Rejected', className: 'bg-blush/15 text-blush' }
}

export default function MyReviewsPage() {
  const { te } = useI18n()
  const [reviews, setReviews] = useState<MyReview[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lightbox, setLightbox] = useState<string | null>(null)

  const load = useCallback(async (pageNum: number, append: boolean) => {
    setLoading(true)
    setError(null)
    try {
      const res = await listMyReviews({ page: pageNum, pageSize: 10 })
      setReviews((prev) => (append ? [...prev, ...res.data] : res.data))
      setTotal(res.totalElements)
      setPage(res.pageNumber)
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setLoading(false)
    }
  }, [te])

  useEffect(() => {
    void load(1, false)
  }, [load])

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">My Reviews</h1>
      <p className="mt-2 text-sm text-ink-soft">Reviews you’ve written and their moderation status.</p>

      <div className="mt-6 space-y-4">
        {error && (
          <div className="py-10 text-center">
            <p className="text-sm text-blush">{error}</p>
            <button onClick={() => void load(1, false)} className="btn-outline mt-4">Try Again</button>
          </div>
        )}
        {!error && loading && reviews.length === 0 && (
          <div className="space-y-4" aria-hidden="true">
            {[0, 1, 2].map((i) => <div key={i} className="h-36 animate-pulse rounded-sm bg-muted" />)}
          </div>
        )}
        {!error && !loading && reviews.length === 0 ? (
          <div className="rounded-sm border border-dashed border-line py-16 text-center">
            <p className="font-display text-xl">No reviews yet</p>
            <p className="mt-1 text-sm text-ink-soft">Once you review a purchase, it will appear here with its moderation status.</p>
            <Link href="/account/orders" className="btn-outline mt-6 inline-flex">View My Orders</Link>
          </div>
        ) : (
          reviews.map((r) => {
            const badge = statusBadge[r.status]
            return (
              <div key={r.id} className="rounded-sm border border-line bg-surface p-5">
                <div className="flex items-center justify-between gap-3 border-b border-line/60 pb-3">
                  <div className="flex min-w-0 items-center gap-3">
                    {r.productImg ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={r.productImg} alt="" className="h-14 w-10 shrink-0 rounded-sm border border-line object-cover" />
                    ) : (
                      <div className="h-14 w-10 shrink-0 rounded-sm border border-line bg-muted" />
                    )}
                    <div className="min-w-0">
                      {r.productSlug ? (
                        <Link href={`/product/${r.productSlug}`} className="block truncate text-sm font-medium hover:text-gold-deep">{r.productName ?? 'View product'}</Link>
                      ) : (
                        <p className="truncate text-sm font-medium">{r.productName ?? 'Product'}</p>
                      )}
                      {r.submittedAt && <p className="text-xs text-ink-soft">Submitted {formatDateTimeLong(r.submittedAt)}</p>}
                    </div>
                  </div>
                  <span className={cn('shrink-0 rounded-full px-3 py-1 text-xs', badge.className)}>{badge.label}</span>
                </div>

                <div className="mt-3">
                  <Stars rating={r.rating} />
                  {r.content && <p className="mt-2 text-sm text-ink-soft">{r.content}</p>}
                  {(r.images ?? []).length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {(r.images ?? []).map((img) => (
                        <button key={img.id} onClick={() => setLightbox(img.url)} className="cursor-pointer overflow-hidden rounded-sm border border-line" aria-label="View photo">
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img src={img.url} alt="Review photo" className="h-16 w-16 object-cover transition-transform duration-300 hover:scale-105" />
                        </button>
                      ))}
                    </div>
                  )}
                  {r.replyContent && (
                    <div className="mt-3 rounded-sm bg-muted px-4 py-3">
                      <p className="text-xs font-medium text-gold-deep">{r.replyAuthor ?? 'Dreamy Team'}</p>
                      <p className="mt-1 text-sm text-ink-soft">{r.replyContent}</p>
                      {r.replyTime && <p className="mt-1 text-[11px] text-ink-faint">{formatDateTimeLong(r.replyTime)}</p>}
                    </div>
                  )}
                </div>
              </div>
            )
          })
        )}
        {!error && reviews.length < total && (
          <div className="pt-2 text-center">
            <button onClick={() => void load(page + 1, true)} disabled={loading} className="btn-outline disabled:opacity-60">
              {loading ? 'Loading…' : 'Load more'}
            </button>
          </div>
        )}
      </div>

      {/* Lightbox（复用 product-reviews overlay 风格） */}
      {lightbox && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-ink/70 backdrop-blur-sm" onClick={() => setLightbox(null)} />
          <div className="relative max-h-[85vh] max-w-2xl animate-fadeup">
            <button onClick={() => setLightbox(null)} className="absolute -right-3 -top-3 z-10 cursor-pointer rounded-full bg-canvas p-1.5 shadow-lift" aria-label="Close"><X className="h-4 w-4" /></button>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={lightbox} alt="Review photo" className="max-h-[85vh] w-auto rounded-sm object-contain" />
          </div>
        </div>
      )}
    </div>
  )
}
