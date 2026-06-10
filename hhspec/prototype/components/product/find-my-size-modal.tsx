'use client'

import { useState } from 'react'
import { X, Sparkles, Check } from 'lucide-react'
import type { SizeStock } from '@/data/types'
import { cn } from '@/lib/utils'

interface Answers {
  height: string
  weight: string
  bust: string
  waist: string
  hips: string
  fit: 'fitted' | 'true' | 'relaxed'
}

const SIZE_LADDER = ['US 0', 'US 2', 'US 4', 'US 6', 'US 8', 'US 10', 'US 12', 'US 14']

/** mock 推荐规则：按胸围区间映射 + 松紧偏好微调（F-072/F-073） */
function recommend(a: Answers): string {
  const bust = parseFloat(a.bust)
  let idx: number
  if (bust < 32.5) idx = 0
  else if (bust < 33.5) idx = 1
  else if (bust < 34.5) idx = 2
  else if (bust < 35.5) idx = 3
  else if (bust < 36.5) idx = 4
  else if (bust < 38) idx = 5
  else if (bust < 39.5) idx = 6
  else idx = 7
  if (a.fit === 'relaxed') idx = Math.min(idx + 1, SIZE_LADDER.length - 1)
  return SIZE_LADDER[idx]
}

export function FindMySizeModal({
  open,
  onClose,
  sizes,
  customAvailable,
  onSelect
}: {
  open: boolean
  onClose: () => void
  sizes: SizeStock[]
  customAvailable: boolean
  onSelect: (size: string) => void
}) {
  const [answers, setAnswers] = useState<Answers>({ height: '', weight: '', bust: '', waist: '', hips: '', fit: 'true' })
  const [result, setResult] = useState<string | null>(null)
  const [error, setError] = useState(false)

  if (!open) return null

  const set = (key: keyof Answers) => (e: React.ChangeEvent<HTMLInputElement>) => setAnswers((p) => ({ ...p, [key]: e.target.value }))

  const submit = () => {
    if (!answers.height || !answers.weight || !answers.bust || !answers.waist || !answers.hips) {
      setError(true)
      return
    }
    setError(false)
    setResult(recommend(answers))
  }

  const available = result ? sizes.some((s) => s.size === result && s.inStock) : false

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
            <p className="mt-2 text-sm text-ink-soft">Answer a few quick questions and we&apos;ll match you with the size brides like you kept.</p>
            <div className="mt-5 grid grid-cols-2 gap-4">
              <FitField id="fit-height" label={'Height (in)'} placeholder="65" value={answers.height} onChange={set('height')} />
              <FitField id="fit-weight" label="Weight (lb)" placeholder="135" value={answers.weight} onChange={set('weight')} />
              <FitField id="fit-bust" label={'Bust (in)'} placeholder="34" value={answers.bust} onChange={set('bust')} />
              <FitField id="fit-waist" label={'Waist (in)'} placeholder="27" value={answers.waist} onChange={set('waist')} />
              <FitField id="fit-hips" label={'Hips (in)'} placeholder="37" value={answers.hips} onChange={set('hips')} />
            </div>
            <fieldset className="mt-4">
              <legend className="eyebrow mb-2">How do you like your fit?</legend>
              <div className="flex gap-2">
                {([
                  { id: 'fitted', label: 'Fitted' },
                  { id: 'true', label: 'True to size' },
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
            {error && <p className="mt-3 text-xs text-blush">Please fill in all measurements so we can recommend your best size.</p>}
            <button onClick={submit} className="btn-primary mt-5 w-full"><Sparkles className="h-4 w-4" /> Get My Recommendation</button>
            <p className="mt-3 text-center text-[11px] text-ink-faint">Tip: measure over the undergarments you plan to wear on the day.</p>
          </>
        ) : (
          <div className="mt-5">
            <div className="rounded-sm bg-muted p-6 text-center">
              <p className="eyebrow">We recommend</p>
              <p className="mt-2 font-display text-5xl font-medium text-gold-deep">{result}</p>
              <p className="mx-auto mt-3 max-w-xs text-sm text-ink-soft">
                Based on 1,200+ brides with similar measurements, we recommend <strong className="text-ink">{result}</strong>. 94% of customers with your measurements kept this size.
              </p>
            </div>
            {available ? (
              <button onClick={() => pick(result)} className="btn-primary mt-5 w-full"><Check className="h-4 w-4" /> Select {result}</button>
            ) : (
              <div className="mt-5 space-y-3">
                <p className="rounded-sm bg-gold/10 px-4 py-3 text-sm text-gold-deep">{result} is currently unavailable in standard sizing for this style.</p>
                {customAvailable && (
                  <button onClick={() => pick('Custom')} className="btn-primary w-full">Choose Custom Size · Free</button>
                )}
              </div>
            )}
            <button onClick={() => setResult(null)} className="mt-3 w-full cursor-pointer py-2 text-center text-sm text-ink-soft underline">Adjust my measurements</button>
          </div>
        )}
      </div>
    </div>
  )
}

function FitField({ id, label, placeholder, value, onChange }: { id: string; label: string; placeholder: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void }) {
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} type="number" inputMode="decimal" min="0" placeholder={placeholder} value={value} onChange={onChange} className="w-full rounded-sm border border-line bg-surface px-3 py-2.5 text-sm outline-none focus:border-gold" />
    </div>
  )
}
