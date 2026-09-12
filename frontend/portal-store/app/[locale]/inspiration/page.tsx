import type { Metadata } from 'next'
import Link from 'next/link'
import { palette } from '@/data/products'
import { fetchStoreLookbooks, fetchStoreWeddings } from '@/lib/api/marketing-server'
import { LookbookGrid } from '@/components/marketing/lookbook-grid'
import { SectionHeading } from '@/components/ui/primitives'
import { EditorialHero } from '@/components/marketing/editorial-hero'
import { getMessages } from '@/lib/i18n/messages'
import { buildAlternates } from '@/lib/i18n/seo'
import type { Locale } from '@/lib/api/types'

/** /inspiration（PAGE-MKT-S07，layout-keep + data-swap）：mock lookbooks → E-MKT-06/07；卡片展开拉关联商品。 */

export const dynamic = 'force-dynamic'

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const { locale } = await params
  const activeLocale = (locale as Locale) ?? 'en'
  const t = getMessages(activeLocale).inspiration
  return {
    title: t.metaTitle,
    description: t.metaDescription,
    alternates: buildAlternates('/inspiration', activeLocale)
  }
}

export default async function InspirationPage({
  searchParams,
  params
}: {
  searchParams: Promise<{ lookbook?: string }>
  params: Promise<{ locale: string }>
}) {
  const query = await searchParams
  const { locale } = await params
  const t = getMessages((locale as Locale) ?? 'en').inspiration
  const [lookbooks, weddingsPage] = await Promise.all([
    fetchStoreLookbooks(locale),
    fetchStoreWeddings({ page: 1, pageSize: 3, locale })
  ])
  const weddings = weddingsPage?.data ?? []

  return (
    <div>
      <EditorialHero variant="wide" image="/photography/inspiration-hero.jpg" alt={t.heroTitle} eyebrow={t.heroEyebrow} title={t.heroTitle} objectPosition="center 25%" />

      <section className="container-luxe py-16">
        <SectionHeading eyebrow={t.editsEyebrow} title={t.editsTitle} />
        <div className="mt-10">
          {lookbooks.length === 0 ? (
            <p className="py-16 text-center text-ink-soft">{t.empty}</p>
          ) : (
            <LookbookGrid lookbooks={lookbooks} initialId={query.lookbook ? Number(query.lookbook) : undefined} />
          )}
        </div>
      </section>

      {/* Color palette tool（静态编辑区块保持） */}
      <section className="bg-muted py-16">
        <div className="container-luxe">
          <SectionHeading eyebrow={t.paletteEyebrow} title={t.paletteTitle} description={t.paletteDescription} />
          <div className="mt-10 flex flex-wrap justify-center gap-4">
            {palette.map((c) => (
              <div key={c.name} className="text-center">
                <div className="h-24 w-24 rounded-sm shadow-card" style={{ backgroundColor: c.hex }} />
                <p className="mt-2 text-sm">{c.name}</p>
              </div>
            ))}
          </div>
          <div className="mt-8 text-center">
            <Link href="/special-occasion" className="btn-primary">{t.paletteCta}</Link>
          </div>
        </div>
      </section>

      {/* Real weddings teaser（E-MKT-04） */}
      {weddings.length > 0 && (
        <section className="container-luxe py-16">
          <SectionHeading eyebrow={t.weddingsEyebrow} title={t.weddingsTitle} />
          <div className="mt-10 grid gap-6 lg:grid-cols-3">
            {weddings.map((w) => (
              <Link key={w.id} href={`/real-weddings/${w.id}`} className="group">
                <div className="aspect-[4/3] overflow-hidden rounded-sm bg-muted">
                  {w.cover && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                  )}
                </div>
                <h3 className="mt-3 font-display text-2xl font-medium">{w.couple}</h3>
                {w.location && <p className="text-sm text-ink-soft">{w.location}</p>}
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
