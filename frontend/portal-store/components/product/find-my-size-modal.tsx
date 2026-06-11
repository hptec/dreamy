'use client'

/**
 * FindMySizeModal（COMP-CAT-S04，copy-adapt 自 hhspec/prototype/components/product/find-my-size-modal.tsx）。
 * 原型本地 recommend() 硬编码码梯删除 → 改调 E-CAT-05 尺码推荐 API（决策 20.3 区间说明话术）。
 * weight 字段仅 UI 保留不上送（契约无此字段，显式偏离标注）；fit 三选措辞按契约枚举 snug/regular/relaxed 适配。
 * 422501/422502 → 字段红框/区间提示（FORM-CAT-S01）。
 */

import { useState } from 'react'
import { X, Sparkles, Check } from 'lucide-react'
import type { SizeRecommendationResponse } from '@/lib/api/store-types'
import { recommendSize } from '@/lib/api/catalog-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn } from '@/lib/utils'

interface Answers {
  height: string
  weight: string
  bust: string
  waist: string
  hips: string
  fit: 'snug' | 'regular' | 'relaxed'
}

export function FindMySizeModal({
  open,
  onClose,
  productId,
  sizes,
  customAvailable,
  onSelect
}: {
  open: boolean
  onClose: () => void
  productId: number
  sizes: { size: string; inStock: boolean }[]
  customAvailable: boolean
  onSelect: (size: string) => void
}) {
  const { te } = useI18n()
  const [answers, setAnswers] = useState<Answers>({ height: '', weight: '', bust: '', waist: '', hips: '', fit: 'regular' })
  const [result, setResult] = useState<SizeRecommendationResponse | null>(null)
  const [error, setError] = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!open) return null

  const set = (key: keyof Answers) => (e: React.ChangeEvent<HTMLInputElement>) => setAnswers((p) => ({ ...p, [key]: e.target.value }))

  const submit = async () => {
    // 必填四维前端预校验（缺失红框不发请求——原型行为保留；weight 仅 UI 不上送）
    if (!answers.height || !answers.bust || !answers.waist || !answers.hips) {
      setError(true)
      return
    }
    setError(false)
    setApiError(null)
    setSubmitting(true)
    try {
      const res = await recommendSize(productId, {
        height: parseFloat(answers.height),
        bust: parseFloat(answers.bust),
        waist: parseFloat(answers.waist),
        hips: parseFloat(answers.hips),
        fitPreference: answers.fit
      })
      setResult(res)
    } catch (err) {
      setApiError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setSubmitting(false)
    }
  }

  const recommendedUs = result?.matched ? result.recommendedRow?.us ?? null : null
  const available = recommendedUs ? sizes.some((s) => s.size === recommendedUs && s.inStock) : false

  const pick = (size: string) => {
    onSelect(size)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-lg animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <p className="eyebrow">Fit Finder</p>
        <h2 className="mt-1 font-display text-2xl font-medium">Find My Size</h2>

        {result === null ? (
          <>
            <p className="mt-2 text-sm text-ink-soft">Answer a few quick questions and we&apos;ll match your measurements against this style&apos;s size chart.</p>
            <div className="mt-5 grid grid-cols-2 gap-4">
              <FitField id="fit-height" label={'Height (in)'} placeholder="65" value={answers.height} onChange={set('height')} invalid={error && !answers.height} />
              <FitField id="fit-weight" label="Weight (lb)" placeholder="135" value={answers.weight} onChange={set('weight')} />
              <FitField id="fit-bust" label={'Bust (in)'} placeholder="34" value={answers.bust} onChange={set('bust')} invalid={error && !answers.bust} />
              <FitField id="fit-waist" label={'Waist (in)'} placeholder="27" value={answers.waist} onChange={set('waist')} invalid={error && !answers.waist} />
              <FitField id="fit-hips" label={'Hips (in)'} placeholder="37" value={answers.hips} onChange={set('hips')} invalid={error && !answers.hips} />
            </div>
            <fieldset className="mt-4">
              <legend className="eyebrow mb-2">How do you like your fit?</legend>
              <div className="flex gap-2">
                {([
                  { id: 'snug', label: 'Snug' },
                  { id: 'regular', label: 'True to size' },
                  { id: 'relaxed', label: 'Relaxed' }
                ] as const).map((o) => (
                  <button
                    key={o.id}
                    onClick={() => setAnswers((p) => ({ ...p, fit: o.id }))}
                    className={cn('flex-1 cursor-pointer rounded-sm border px-3 py-2.5 text-xs transition-colors', answers.fit === o.id ? 'border-ink bg-ink text-canvas' : 'border-line hover:border-ink')}
                    aria-pressed={answers.fit === o.id}
                  >
                    {o.label}
                  </button>
                ))}
              </div>
            </fieldset>
            {error && <p className="mt-3 text-xs text-blush">Please fill in your height, bust, waist, and hips so we can recommend your best size.</p>}
            {apiError && <p className="mt-3 text-xs text-blush">{apiError}</p>}
            <button onClick={submit} disabled={submitting} className="btn-primary mt-5 w-full disabled:opacity-60"><Sparkles className="h-4 w-4" /> {submitting ? 'Matching…' : 'Get My Recommendation'}</button>
            <p className="mt-3 text-center text-[11px] text-ink-faint">Tip: measure over the undergarments you plan to wear on the day.</p>
          </>
        ) : (
          <div className="mt-5">
            {result.matched && recommendedUs ? (
              <div className="rounded-sm bg-muted p-6 text-center">
                <p className="eyebrow">We recommend</p>
                <p className="mt-2 font-display text-5xl font-medium text-gold-deep">{recommendedUs}</p>
                {result.explanation && (
                  <p className="mx-auto mt-3 max-w-xs text-sm text-ink-soft">{result.explanation}</p>
                )}
                {result.dimensionNotes && result.dimensionNotes.length > 0 && (
                  <ul className="mx-auto mt-3 max-w-xs space-y-1 text-left text-xs text-ink-soft">
                    {result.dimensionNotes.map((n) => (
                      <li key={n.dimension} className="flex items-center justify-between">
                        <span className="capitalize">{n.dimension.replace(/_/g, ' ')}</span>
                        <span className="font-medium text-ink">{n.matchedUs ?? '—'}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ) : (
              <div className="rounded-sm bg-muted p-6 text-center">
                <p className="eyebrow">No exact match</p>
                <p className="mx-auto mt-3 max-w-xs text-sm text-ink-soft">
                  {result.explanation ?? 'We couldn’t match your measurements to a standard size for this style.'}
                </p>
                <a href="/contact" className="mt-3 inline-block text-sm text-gold-deep underline">Contact a stylist for help</a>
              </div>
            )}

            {result.matched && recommendedUs && available && (
              <button onClick={() => pick(recommendedUs)} className="btn-primary mt-5 w-full"><Check className="h-4 w-4" /> Select {recommendedUs}</button>
            )}
            {result.matched && recommendedUs && !available && (
              <div className="mt-5 space-y-3">
                <p className="rounded-sm bg-gold/10 px-4 py-3 text-sm text-gold-deep">{recommendedUs} is currently unavailable in standard sizing for this style.</p>
                {customAvailable && (
                  <button onClick={() => pick('Custom')} className="btn-primary w-full">Choose Custom Size · Free</button>
                )}
              </div>
            )}
            {!result.matched && customAvailable && (
              <button onClick={() => pick('Custom')} className="btn-primary mt-5 w-full">Choose Custom Size · Free</button>
            )}
            <button onClick={() => setResult(null)} className="mt-3 w-full cursor-pointer py-2 text-center text-sm text-ink-soft underline">Adjust my measurements</button>
          </div>
        )}
      </div>
    </div>
  )
}

function FitField({ id, label, placeholder, value, onChange, invalid }: { id: string; label: string; placeholder: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void; invalid?: boolean }) {
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input
        id={id}
        type="number"
        inputMode="decimal"
        min="0"
        placeholder={placeholder}
        value={value}
        onChange={onChange}
        className={cn('w-full rounded-sm border bg-surface px-3 py-2.5 text-sm outline-none focus:border-gold', invalid ? 'border-blush' : 'border-line')}
      />
    </div>
  )
}
