'use client'

/**
 * SizeGuideModal（COMP-CAT-S05，data-swap）：数据源切 StoreProductDetail.size_chart（E-CAT-04）。
 * 布局/双 tab 结构保持；尺码表为空时回退提示文案。
 */

import { useState } from 'react'
import { X } from 'lucide-react'
import type { SizeChartRow } from '@/lib/api/store-types'

const fmt = (v?: number) => (v === undefined || v === null ? '—' : `${v}"`)

export function SizeGuideModal({ open, onClose, sizeChart = [] }: { open: boolean; onClose: () => void; sizeChart?: SizeChartRow[] }) {
  const [tab, setTab] = useState<'chart' | 'measure'>('chart')
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-2xl animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <h2 className="font-display text-2xl font-medium">Size Guide</h2>
        <div className="mt-4 flex gap-2 border-b border-line">
          {(['chart', 'measure'] as const).map((t) => (
            <button key={t} onClick={() => setTab(t)} className={`cursor-pointer border-b-2 px-4 py-2 text-sm font-medium capitalize ${tab === t ? 'border-gold text-ink' : 'border-transparent text-ink-soft'}`}>
              {t === 'chart' ? 'Size Chart' : 'How to Measure'}
            </button>
          ))}
        </div>
        {tab === 'chart' ? (
          <div className="mt-4 overflow-x-auto">
            {sizeChart.length === 0 ? (
              <p className="py-8 text-center text-sm text-ink-soft">This style doesn&apos;t have a size chart yet. Contact a stylist for fit guidance.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-soft">
                    <th className="py-2 pr-4">US</th><th className="py-2 pr-4">UK</th><th className="py-2 pr-4">AU</th><th className="py-2 pr-4">Bust</th><th className="py-2 pr-4">Waist</th><th className="py-2 pr-4">Hips</th><th className="py-2">Hollow to Floor</th>
                  </tr>
                </thead>
                <tbody>
                  {sizeChart.map((r) => (
                    <tr key={r.us} className="border-b border-line/40">
                      <td className="py-2 pr-4 font-medium">{r.us}</td><td className="py-2 pr-4">{r.uk ?? '—'}</td><td className="py-2 pr-4">{r.au ?? '—'}</td><td className="py-2 pr-4">{fmt(r.bust)}</td><td className="py-2 pr-4">{fmt(r.waist)}</td><td className="py-2 pr-4">{fmt(r.hips)}</td><td className="py-2">{fmt(r.hollowToFloor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="mt-4 text-xs text-ink-soft">Between sizes? Size up for comfort, or choose <strong>Custom</strong> for a made-to-measure fit at no extra cost.</p>
          </div>
        ) : (
          <div className="mt-4 space-y-3 text-sm text-ink-soft">
            <p><strong className="text-ink">Bust:</strong> Measure around the fullest part of your chest, keeping the tape level.</p>
            <p><strong className="text-ink">Waist:</strong> Measure around the narrowest part of your natural waistline.</p>
            <p><strong className="text-ink">Hips:</strong> Measure around the fullest part of your hips, about 8" below your waist.</p>
            <p className="rounded-sm bg-muted p-3 text-xs">Tip: Wear the undergarments you plan to wear on the day, and have a friend help for the most accurate measurements.</p>
          </div>
        )}
      </div>
    </div>
  )
}
