import type { Metadata } from 'next'
import Link from 'next/link'
import { fetchStoreGuides } from '@/lib/api/marketing-server'
import { Eyebrow } from '@/components/ui/primitives'
import { GuideBody } from '@/components/marketing/guide-body'
import { GuideChecklist } from '@/components/marketing/guide-checklist'
import type { Locale } from '@/lib/api/types'
import { getMessages } from '@/lib/i18n/messages'

/**
 * /wedding-guides（PAGE-MKT-S08，layout-keep + data-swap）：mock guides → E-MKT-08。
 * 时间轴布局保持；正文与结构化 tasks[] 独立渲染。
 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Wedding Planning Guides',
  description: 'A timeline-based guide to planning your outdoor wedding wardrobe, from dream to I do.'
}

export default async function WeddingGuidesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params
  const activeLocale = locale as Locale
  const guides = await fetchStoreGuides(activeLocale)
  const messages = getMessages(activeLocale)

  return (
    <div>
      <section className="relative bg-muted py-20 text-center">
        <div className="container-luxe">
          <Eyebrow>{messages.guide.eyebrow}</Eyebrow>
          <h1 className="mt-3 font-display text-5xl font-medium">{messages.guide.title}</h1>
          <p className="mx-auto mt-4 max-w-xl text-ink-soft">{messages.guide.description}</p>
        </div>
      </section>

      <section className="container-luxe py-16">
        {guides.length === 0 ? (
          <p className="py-16 text-center text-ink-soft">{messages.guide.empty}</p>
        ) : (
          <div className="relative mx-auto max-w-3xl">
            <div className="absolute bottom-0 left-6 top-0 w-px bg-line sm:left-1/2" />
            <div className="space-y-12">
              {guides.map((g, i) => (
                <div id={`guide-${g.id}`} key={g.id} className={`relative grid gap-6 sm:grid-cols-2 ${i % 2 ? 'sm:[direction:rtl]' : ''}`}>
                  <div className="absolute left-6 top-2 z-10 flex h-4 w-4 -translate-x-1/2 items-center justify-center rounded-full bg-gold ring-4 ring-canvas sm:left-1/2" />
                  <div className={`pl-12 [direction:ltr] sm:pl-0 ${i % 2 ? 'sm:pr-12 sm:text-right' : 'sm:pl-12'}`}>
                    <Eyebrow>{[g.phase, g.timeframe].filter(Boolean).join(' · ')}</Eyebrow>
                    <h3 className="mt-1 font-display text-2xl font-medium">{g.title}</h3>
                  </div>
                  <div className="pl-12 [direction:ltr] sm:pl-0">
                    <div className="space-y-3 rounded-sm border border-line bg-surface p-5">
                      {g.body && <GuideBody body={g.body} />}
                      <GuideChecklist guideId={g.id} tasks={g.tasks} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-16 text-center">
          <Link href="/wedding-dresses" className="btn-primary">{messages.guide.startWithDress}</Link>
        </div>
      </section>
    </div>
  )
}
