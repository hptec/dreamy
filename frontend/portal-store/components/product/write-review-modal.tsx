'use client'

/**
 * WriteReviewModal（COMP-REV-S03，新增——原型仅按钮无表单态，复用 size-guide-modal 同型结构与 token）。
 * 星级 1..5 必选 + content ≤5000 计数 + 图片上传 ≤9（presign 直传、进度态、
 * 502801 卡片错误态「可先发布评价稍后补图」——决策 9 降级）。
 * 403801 → onBlocked（入口隐藏+提示）；409801 → toast 字典；422 → 字段红框（FORM-REV-S01/S02/S03）。
 */

import { useRef, useState } from 'react'
import { X, Star, ImagePlus, RotateCcw, Trash2 } from 'lucide-react'
import { createStoreReview, uploadReviewImage } from '@/lib/api/review-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn } from '@/lib/utils'

interface UploadEntry {
  id: string
  file: File
  publicUrl?: string
  uploading: boolean
  error?: string
}

const MAX_IMAGES = 9
const MAX_CONTENT = 5000
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export function WriteReviewModal({
  productId,
  onClose,
  onBlocked,
  onSubmitted
}: {
  productId: number
  onClose: () => void
  onBlocked: () => void
  onSubmitted: () => void
}) {
  const { te } = useI18n()
  const [rating, setRating] = useState(0)
  const [hoverRating, setHoverRating] = useState(0)
  const [content, setContent] = useState('')
  const [images, setImages] = useState<UploadEntry[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [ratingError, setRatingError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const startUpload = (entry: UploadEntry) => {
    uploadReviewImage(entry.file)
      .then((publicUrl) => {
        setImages((prev) => prev.map((e) => (e.id === entry.id ? { ...e, publicUrl, uploading: false, error: undefined } : e)))
      })
      .catch((err: unknown) => {
        const msg = err instanceof ApiError && err.code === 502801 ? te(502801) : 'Upload failed — retry or remove.'
        setImages((prev) => prev.map((e) => (e.id === entry.id ? { ...e, uploading: false, error: msg } : e)))
      })
  }

  const onFiles = (files: FileList | null) => {
    if (!files) return
    const room = MAX_IMAGES - images.length
    const accepted = Array.from(files)
      .filter((f) => ALLOWED_TYPES.includes(f.type))
      .slice(0, Math.max(0, room))
    const entries = accepted.map<UploadEntry>((file) => ({
      id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file,
      uploading: true
    }))
    setImages((prev) => [...prev, ...entries])
    entries.forEach(startUpload)
  }

  const retry = (id: string) => {
    setImages((prev) => prev.map((e) => (e.id === id ? { ...e, uploading: true, error: undefined } : e)))
    const entry = images.find((e) => e.id === id)
    if (entry) startUpload({ ...entry, uploading: true, error: undefined })
  }

  const removeImage = (id: string) => setImages((prev) => prev.filter((e) => e.id !== id))

  const submit = async () => {
    // 前端预校验：rating 必选；content 超长阻断（FORM-REV-S02）
    if (rating < 1 || rating > 5) { setRatingError(true); return }
    if (content.length > MAX_CONTENT) return
    setRatingError(false)
    setError(null)
    setSubmitting(true)
    try {
      await createStoreReview({
        productId,
        rating,
        content: content.trim() || undefined,
        // url 仅取上传成功项（FORM-REV-S03：单张失败不阻塞整单）
        images: images.filter((e) => e.publicUrl).map((e) => ({ url: e.publicUrl as string }))
      })
      onSubmitted()
    } catch (err) {
      if (err instanceof ApiError && err.code === 403801) {
        onBlocked()
        return
      }
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-lg animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <p className="eyebrow">Share your experience</p>
        <h2 className="mt-1 font-display text-2xl font-medium">Write a Review</h2>

        {/* 星级选择 */}
        <div className="mt-5">
          <p className="eyebrow mb-2">Your rating</p>
          <div className="flex gap-1" onMouseLeave={() => setHoverRating(0)}>
            {[1, 2, 3, 4, 5].map((star) => (
              <button
                key={star}
                onClick={() => { setRating(star); setRatingError(false) }}
                onMouseEnter={() => setHoverRating(star)}
                className="cursor-pointer p-0.5"
                aria-label={`${star} star${star > 1 ? 's' : ''}`}
              >
                <Star className={cn('h-7 w-7 transition-colors', (hoverRating || rating) >= star ? 'fill-gold text-gold' : 'fill-line text-line')} />
              </button>
            ))}
          </div>
          {ratingError && <p className="mt-1.5 text-xs text-blush">Please select a star rating.</p>}
        </div>

        {/* 内容 */}
        <div className="mt-4">
          <label htmlFor="review-content" className="eyebrow mb-1.5 block">Your review (optional)</label>
          <textarea
            id="review-content"
            rows={5}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="How was the fit, fabric, and the big day?"
            className={cn('w-full rounded-sm border bg-surface px-4 py-3 text-sm outline-none focus:border-gold', content.length > MAX_CONTENT ? 'border-blush' : 'border-line')}
          />
          <p className={cn('mt-1 text-right text-[11px]', content.length > MAX_CONTENT ? 'text-blush' : 'text-ink-faint')}>{content.length}/{MAX_CONTENT}</p>
        </div>

        {/* 图片上传 */}
        <div className="mt-2">
          <p className="eyebrow mb-2">Photos ({images.length}/{MAX_IMAGES})</p>
          <div className="flex flex-wrap gap-2">
            {images.map((e) => (
              <div key={e.id} className={cn('relative h-16 w-16 overflow-hidden rounded-sm border', e.error ? 'border-blush' : 'border-line')}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={e.publicUrl ?? URL.createObjectURL(e.file)} alt="" className={cn('h-full w-full object-cover', (e.uploading || e.error) && 'opacity-50')} />
                {e.uploading && <span className="absolute inset-0 flex items-center justify-center bg-canvas/40 text-[9px] uppercase tracking-luxe">…</span>}
                {e.error && (
                  <button onClick={() => retry(e.id)} className="absolute inset-0 flex cursor-pointer items-center justify-center bg-canvas/50" aria-label="Retry upload">
                    <RotateCcw className="h-4 w-4 text-blush" />
                  </button>
                )}
                <button onClick={() => removeImage(e.id)} className="absolute right-0 top-0 cursor-pointer rounded-bl-sm bg-canvas/90 p-0.5" aria-label="Remove photo">
                  <Trash2 className="h-3 w-3" />
                </button>
              </div>
            ))}
            {images.length < MAX_IMAGES && (
              <button onClick={() => fileRef.current?.click()} className="flex h-16 w-16 cursor-pointer items-center justify-center rounded-sm border border-dashed border-line text-ink-faint transition-colors hover:border-gold hover:text-gold-deep" aria-label="Add photos">
                <ImagePlus className="h-5 w-5" />
              </button>
            )}
            <input ref={fileRef} type="file" accept={ALLOWED_TYPES.join(',')} multiple className="hidden" onChange={(e) => { onFiles(e.target.files); e.target.value = '' }} />
          </div>
          {images.some((e) => e.error) && (
            <p className="mt-2 text-xs text-gold-deep">Some photos failed to upload — you can publish your review now and add photos later.</p>
          )}
        </div>

        {error && <p className="mt-3 text-xs text-blush">{error}</p>}

        <button onClick={submit} disabled={submitting || images.some((e) => e.uploading)} className="btn-primary mt-5 w-full disabled:opacity-60">
          {submitting ? 'Submitting…' : 'Submit Review'}
        </button>
        <p className="mt-2 text-center text-[11px] text-ink-faint">Your review will appear after moderation.</p>
      </div>
    </div>
  )
}
