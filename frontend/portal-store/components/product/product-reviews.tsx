'use client'

/**
 * ProductReviews（COMP-REV-S01，layout-keep + data-swap）：
 * - 双 tab（Reviews(N)/Q&A(N)）布局不变；sampleReviews/sampleQA/静态 dist → API 数据。
 * - dist 星级条 ← rating_breakdown 百分比计算；评价行收紧为 Stars+日期+内容+姓名
 *   （原型 title/fit/Verified Buyer 契约无字段 → 不渲染，显式偏离标注）。
 * - 官方回复小卡（replyContent 非空）；ReviewImageStrip（COMP-REV-S02）；Load more 翻页（btn-outline）。
 * - 排序四枚举（featured_first 缺省）；写评价/提问入口挂真实表单弹窗（COMP-REV-S03/S04）。
 * - 写评价入口三态（FORM-REV-S01）：未登录跳登录 / 403801 提示文案替换入口 / 409801 toast。
 */

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { X } from 'lucide-react'
import type { Paginated, ReviewImage, StoreQuestion, StoreReview, StoreReviewListResponse, ReviewSort } from '@/lib/api/store-types'
import { fetchStoreReviews, fetchStoreQuestions } from '@/lib/api/review-api'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { Stars } from '@/components/ui/primitives'
import { cn, formatDateTimeLong } from '@/lib/utils'
import { WriteReviewModal } from './write-review-modal'
import { AskQuestionModal } from './ask-question-modal'

const SORT_OPTIONS: { value: ReviewSort; label: string }[] = [
  { value: 'featured_first', label: 'Featured' },
  { value: 'newest', label: 'Newest' },
  { value: 'rating_desc', label: 'Highest rated' },
  { value: 'rating_asc', label: 'Lowest rated' }
]

export function ProductReviews({
  productId,
  slug,
  initialReviews,
  initialQuestions
}: {
  productId: number
  slug: string
  initialReviews: StoreReviewListResponse | null
  initialQuestions: Paginated<StoreQuestion> | null
}) {
  const router = useRouter()
  const { te } = useI18n()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const [tab, setTab] = useState<'reviews' | 'qa'>('reviews')

  // 评价区客户端态（STORE-REV-S01 useReviewSection：首屏 props 初始化）
  const [reviews, setReviews] = useState<StoreReview[]>(initialReviews?.data ?? [])
  const [reviewMeta, setReviewMeta] = useState({
    totalElements: initialReviews?.totalElements ?? 0,
    page: initialReviews?.pageNumber ?? 1,
    ratingAvg: initialReviews?.ratingAvg ?? 0,
    ratingCount: initialReviews?.ratingCount ?? 0,
    ratingBreakdown: initialReviews?.ratingBreakdown ?? {}
  })
  const [sort, setSort] = useState<ReviewSort>('featured_first')
  const [loadingMore, setLoadingMore] = useState(false)

  // Q&A 区（STORE-REV-S03 useQaSection）
  const [questions, setQuestions] = useState<StoreQuestion[]>(initialQuestions?.data ?? [])
  const [qaMeta, setQaMeta] = useState({
    totalElements: initialQuestions?.totalElements ?? 0,
    page: initialQuestions?.pageNumber ?? 1
  })
  const [qaLoading, setQaLoading] = useState(false)

  const [writeOpen, setWriteOpen] = useState(false)
  const [askOpen, setAskOpen] = useState(false)
  const [reviewBlocked, setReviewBlocked] = useState(false)
  const [confirmation, setConfirmation] = useState<string | null>(null)
  const [lightbox, setLightbox] = useState<string | null>(null)

  const dist = useMemo(() => {
    const breakdown = reviewMeta.ratingBreakdown ?? {}
    const total = Object.values(breakdown).reduce((s, n) => s + n, 0)
    return [5, 4, 3, 2, 1].map((star) => {
      const count = breakdown[String(star)] ?? 0
      return total > 0 ? Math.round((count / total) * 100) : 0
    })
  }, [reviewMeta.ratingBreakdown])

  const applySort = async (next: ReviewSort) => {
    setSort(next)
    setLoadingMore(true)
    try {
      const res = await fetchStoreReviews(productId, { sort: next, page: 1 })
      setReviews(res.data)
      setReviewMeta({
        totalElements: res.totalElements,
        page: res.pageNumber,
        ratingAvg: res.ratingAvg,
        ratingCount: res.ratingCount,
        ratingBreakdown: res.ratingBreakdown ?? {}
      })
    } catch {
      /* 保留现场 */
    } finally {
      setLoadingMore(false)
    }
  }

  const loadMoreReviews = async () => {
    setLoadingMore(true)
    try {
      const res = await fetchStoreReviews(productId, { sort, page: reviewMeta.page + 1 })
      setReviews((prev) => [...prev, ...res.data])
      setReviewMeta((m) => ({ ...m, page: res.pageNumber, totalElements: res.totalElements }))
    } catch {
      /* 保留现场 */
    } finally {
      setLoadingMore(false)
    }
  }

  const loadMoreQuestions = async () => {
    setQaLoading(true)
    try {
      const res = await fetchStoreQuestions(productId, { page: qaMeta.page + 1 })
      setQuestions((prev) => [...prev, ...res.data])
      setQaMeta({ totalElements: res.totalElements, page: res.pageNumber })
    } catch {
      /* 保留现场 */
    } finally {
      setQaLoading(false)
    }
  }

  const openWrite = () => {
    // 三态①：未登录 → 跳登录带 returnTo 锚点
    if (!isAuthenticated) {
      router.push(`/account/login?returnTo=${encodeURIComponent(`/product/${slug}#reviews`)}`)
      return
    }
    setWriteOpen(true)
  }

  const openAsk = () => {
    if (!isAuthenticated) {
      router.push(`/account/login?returnTo=${encodeURIComponent(`/product/${slug}#reviews`)}`)
      return
    }
    setAskOpen(true)
  }

  return (
    <section id="reviews" className="border-t border-line py-16">
      <div className="container-luxe">
        {confirmation && (
          <p className="mb-6 rounded-sm bg-sage/10 px-4 py-3 text-sm text-sage-deep" role="status">{confirmation}</p>
        )}
        <div className="mb-8 flex gap-6 border-b border-line">
          <button onClick={() => setTab('reviews')} className={cn('cursor-pointer border-b-2 pb-3 font-display text-2xl', tab === 'reviews' ? 'border-gold text-ink' : 'border-transparent text-ink-faint')}>Reviews ({reviewMeta.ratingCount})</button>
          <button onClick={() => setTab('qa')} className={cn('cursor-pointer border-b-2 pb-3 font-display text-2xl', tab === 'qa' ? 'border-gold text-ink' : 'border-transparent text-ink-faint')}>Q&A ({qaMeta.totalElements})</button>
        </div>

        {tab === 'reviews' ? (
          <div className="grid gap-10 lg:grid-cols-3">
            <div>
              <div className="flex items-end gap-3">
                <span className="font-display text-5xl font-medium">{reviewMeta.ratingAvg ? reviewMeta.ratingAvg.toFixed(1) : '—'}</span>
                <div className="pb-1"><Stars rating={reviewMeta.ratingAvg} /><p className="mt-1 text-xs text-ink-soft">{reviewMeta.ratingCount} reviews</p></div>
              </div>
              <div className="mt-5 space-y-1.5">
                {dist.map((pct, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs">
                    <span className="w-3 text-ink-soft">{5 - i}</span>
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-line"><div className="h-full bg-gold" style={{ width: `${pct}%` }} /></div>
                    <span className="w-8 text-right text-ink-faint">{pct}%</span>
                  </div>
                ))}
              </div>
              {reviewBlocked ? (
                <p className="mt-6 rounded-sm bg-muted px-4 py-3 text-xs text-ink-soft">{te(403801)}</p>
              ) : (
                <button onClick={openWrite} className="btn-outline mt-6 w-full">Write a Review</button>
              )}
            </div>
            <div className="space-y-6 lg:col-span-2">
              <div className="flex items-center justify-end">
                <label htmlFor="review-sort" className="sr-only">Sort reviews</label>
                <select
                  id="review-sort"
                  value={sort}
                  onChange={(e) => void applySort(e.target.value as ReviewSort)}
                  className="cursor-pointer appearance-none rounded-sm border border-line bg-surface px-4 py-2 text-xs uppercase tracking-luxe outline-none focus:border-gold"
                >
                  {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </div>
              {reviews.length === 0 ? (
                <div className="rounded-sm border border-dashed border-line py-14 text-center">
                  <p className="font-display text-xl">Be the first to review</p>
                  <p className="mt-1 text-sm text-ink-soft">Share your experience with other brides.</p>
                </div>
              ) : (
                reviews.map((r) => (
                  <div key={r.id} className="border-b border-line/60 pb-6">
                    <div className="flex items-center justify-between">
                      <Stars rating={r.rating} />
                      <span className="text-xs text-ink-faint">{formatDateTimeLong(r.submittedAt)}</span>
                    </div>
                    {r.content && <p className="mt-2 text-sm text-ink-soft">{r.content}</p>}
                    <div className="mt-2 flex items-center gap-3 text-xs text-ink-faint">
                      <span className="font-medium text-ink">{r.customerName ?? 'Dreamy Customer'}</span>
                      {r.featured && <span className="rounded-full bg-gold/15 px-2 py-0.5 text-gold-deep">Featured</span>}
                    </div>
                    <ReviewImageStrip images={r.images ?? []} onOpen={setLightbox} />
                    {r.replyContent && (
                      <div className="mt-3 rounded-sm bg-muted px-4 py-3">
                        <p className="text-xs font-medium text-gold-deep">{r.replyAuthor ?? 'Dreamy Team'}</p>
                        <p className="mt-1 text-sm text-ink-soft">{r.replyContent}</p>
                        {r.replyTime && <p className="mt-1 text-[11px] text-ink-faint">{formatDateTimeLong(r.replyTime)}</p>}
                      </div>
                    )}
                  </div>
                ))
              )}
              {reviews.length < reviewMeta.totalElements && (
                <button onClick={loadMoreReviews} disabled={loadingMore} className="btn-outline w-full disabled:opacity-60">
                  {loadingMore ? 'Loading…' : 'Load more reviews'}
                </button>
              )}
            </div>
          </div>
        ) : (
          <div className="max-w-3xl space-y-6">
            {questions.length === 0 ? (
              <div className="rounded-sm border border-dashed border-line py-14 text-center">
                <p className="font-display text-xl">No questions yet</p>
                <p className="mt-1 text-sm text-ink-soft">Ask us anything about fit, fabric, or delivery.</p>
              </div>
            ) : (
              questions.map((item) => (
                <div key={item.id} className="border-b border-line/60 pb-6">
                  <p className="font-medium">Q: {item.question}</p>
                  {item.answer && (
                    <p className="mt-2 text-sm text-ink-soft"><span className="font-medium text-gold-deep">A:</span> {item.answer}</p>
                  )}
                  <p className="mt-1 text-xs text-ink-faint">{item.asker ? `Asked by ${item.asker}` : 'Asked'}{item.askedAt ? ` · ${formatDateTimeLong(item.askedAt)}` : ''}</p>
                </div>
              ))
            )}
            {questions.length < qaMeta.totalElements && (
              <button onClick={loadMoreQuestions} disabled={qaLoading} className="btn-outline w-full disabled:opacity-60">
                {qaLoading ? 'Loading…' : 'Load more questions'}
              </button>
            )}
            <button onClick={openAsk} className="btn-outline">Ask a Question</button>
          </div>
        )}
      </div>

      {writeOpen && (
        <WriteReviewModal
          productId={productId}
          onClose={() => setWriteOpen(false)}
          onBlocked={() => {
            // 三态②：403801 → 入口替换为提示文案
            setWriteOpen(false)
            setReviewBlocked(true)
          }}
          onSubmitted={() => {
            setWriteOpen(false)
            setConfirmation('Your review has been submitted and will appear after moderation.')
          }}
        />
      )}
      {askOpen && (
        <AskQuestionModal
          productId={productId}
          onClose={() => setAskOpen(false)}
          onSubmitted={() => {
            setAskOpen(false)
            setConfirmation('Your question has been submitted — the answer will appear here once published.')
          }}
        />
      )}

      {/* Lightbox（复用 gallery overlay 风格） */}
      {lightbox && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-ink/70 backdrop-blur-sm" onClick={() => setLightbox(null)} />
          <div className="relative max-h-[85vh] max-w-2xl animate-fadeup">
            <button onClick={() => setLightbox(null)} className="absolute -right-3 -top-3 z-10 cursor-pointer rounded-full bg-canvas p-1.5 shadow-lift" aria-label="Close"><X className="h-4 w-4" /></button>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={lightbox} alt="Customer photo" className="max-h-[85vh] w-auto rounded-sm object-contain" />
          </div>
        </div>
      )}
    </section>
  )
}

/** ReviewImageStrip（COMP-REV-S02）：评价行缩略图（≤9，后端已过滤 rejected）+ 点击放大 */
function ReviewImageStrip({ images, onOpen }: { images: ReviewImage[]; onOpen: (url: string) => void }) {
  if (images.length === 0) return null
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {images.map((img) => (
        <button key={img.id} onClick={() => onOpen(img.url)} className="cursor-pointer overflow-hidden rounded-sm border border-line" aria-label="View customer photo">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={img.url} alt="Customer photo" className="h-16 w-16 object-cover transition-transform duration-300 hover:scale-105" />
        </button>
      ))}
    </div>
  )
}
