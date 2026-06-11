import type { Metadata } from 'next'
import Link from 'next/link'
import { ListChecks } from 'lucide-react'
import { fetchStoreGuides } from '@/lib/api/marketing-server'
import { Eyebrow } from '@/components/ui/primitives'

/**
 * /wedding-guides（PAGE-MKT-S08，layout-keep + data-swap）：mock guides → E-MKT-08。
 * 时间轴布局保持；原型 tasks 列表契约无字段 → 渲染 body 正文 + tasks_count 徽章（data-swap 标注）。
 */

export const revalidate = 300

export const metadata: Metadata = {
  title: 'Wedding Planning Guides',
  description: 'A timeline-based guide to planning your outdoor wedding wardrobe, from dream to I do.'
}

export default async function WeddingGuidesPage() {
  const guides = await fetchStoreGuides()

  return (
    <div>
      <section className="relative bg-muted py-20 text-center">
        <div className="container-luxe">
          <Eyebrow>Plan with us</Eyebrow>
          <h1 className="mt-3 font-display text-5xl font-medium">Your Wedding Wardrobe Timeline</h1>
          <p className="mx-auto mt-4 max-w-xl text-ink-soft">From the first daydream to the final fitting — here&apos;s exactly when to tackle each part of your outdoor wedding look.</p>
        </div>
      </section>

      <section className="container-luxe py-16">
        {guides.length === 0 ? (
          <p className="py-16 text-center text-ink-soft">Planning guides are on the way — check back soon.</p>
        ) : (
          <div className="relative mx-auto max-w-3xl">
            <div className="absolute bottom-0 left-6 top-0 w-px bg-line sm:left-1/2" />
            <div className="space-y-12">
              {guides.map((g, i) => (
                <div key={g.id} className={`relative grid gap-6 sm:grid-cols-2 ${i % 2 ? 'sm:[direction:rtl]' : ''}`}>
                  <div className="absolute left-6 top-2 z-10 flex h-4 w-4 -translate-x-1/2 items-center justify-center rounded-full bg-gold ring-4 ring-canvas sm:left-1/2" />
                  <div className={`pl-12 [direction:ltr] sm:pl-0 ${i % 2 ? 'sm:pr-12 sm:text-right' : 'sm:pl-12'}`}>
                    <Eyebrow>{[g.phase, g.timeframe].filter(Boolean).join(' · ')}</Eyebrow>
                    <h3 className="mt-1 font-display text-2xl font-medium">{g.title}</h3>
                  </div>
                  <div className="pl-12 [direction:ltr] sm:pl-0">
                    <div className="space-y-3 rounded-sm border border-line bg-surface p-5">
                      {g.body && <p className="text-sm leading-relaxed text-ink-soft">{g.body}</p>}
                      {typeof g.tasksCount === 'number' && g.tasksCount > 0 && (
                        <p className="flex items-center gap-2 text-xs text-gold-deep">
                          <ListChecks className="h-4 w-4" /> {g.tasksCount} checklist {g.tasksCount === 1 ? 'task' : 'tasks'}
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-16 text-center">
          <Link href="/wedding-dresses" className="btn-primary">Start with the Dress</Link>
        </div>
      </section>
    </div>
  )
}
