'use client'

/**
 * AskQuestionModal（COMP-REV-S04，新增——承载 E-REV-04；S03 同风格基线）。
 * question 1..1000 trim 非空 js_guard；成功确认态（hidden 待回答语义，不插入列表，FORM-REV-S04）。
 */

import { useState } from 'react'
import { X } from 'lucide-react'
import { createStoreQuestion } from '@/lib/api/review-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn } from '@/lib/utils'

const MAX_LEN = 1000

export function AskQuestionModal({
  productId,
  onClose,
  onSubmitted
}: {
  productId: number
  onClose: () => void
  onSubmitted: () => void
}) {
  const { te } = useI18n()
  const [question, setQuestion] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmed = question.trim()
  const valid = trimmed.length >= 1 && trimmed.length <= MAX_LEN

  const submit = async () => {
    if (!valid) return
    setSubmitting(true)
    setError(null)
    try {
      await createStoreQuestion(productId, trimmed)
      onSubmitted()
    } catch (err) {
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
        <p className="eyebrow">Ask our stylists</p>
        <h2 className="mt-1 font-display text-2xl font-medium">Ask a Question</h2>
        <div className="mt-5">
          <label htmlFor="qa-question" className="eyebrow mb-1.5 block">Your question</label>
          <textarea
            id="qa-question"
            rows={4}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Is this dress lined? How long does custom sizing take?"
            className={cn('w-full rounded-sm border bg-surface px-4 py-3 text-sm outline-none focus:border-gold', question.length > MAX_LEN ? 'border-blush' : 'border-line')}
          />
          <p className={cn('mt-1 text-right text-[11px]', question.length > MAX_LEN ? 'text-blush' : 'text-ink-faint')}>{question.length}/{MAX_LEN}</p>
        </div>
        {error && <p className="mt-2 text-xs text-blush">{error}</p>}
        <button onClick={submit} disabled={!valid || submitting} className="btn-primary mt-4 w-full disabled:opacity-60">
          {submitting ? 'Submitting…' : 'Submit Question'}
        </button>
        <p className="mt-2 text-center text-[11px] text-ink-faint">Your question will appear here once our team has answered it.</p>
      </div>
    </div>
  )
}
