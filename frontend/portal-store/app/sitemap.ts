import type { MetadataRoute } from 'next'
import { SUPPORTED_LOCALES, siteBaseUrl, localizedUrl } from '@/lib/i18n/seo'
import { fetchStoreProducts } from '@/lib/api/catalog-server'
import type { Locale } from '@/lib/api/types'

/**
 * 多语言 sitemap（FUNC-016 / 决策 11）。
 * 每条 URL 在 en/es/fr 三语各生成一条，并附 alternates.languages（hreflang）。
 * - 静态页：固定路径集合。
 * - 商品页：E-CAT-01 拉取首批 slug（失败时静态页仍可用，不阻塞）。
 */

export const dynamic = 'force-dynamic'

const STATIC_PATHS = [
  '/',
  '/wedding-dresses',
  '/special-occasion',
  '/accessories',
  '/outdoor-weddings',
  '/inspiration',
  '/real-weddings',
  '/blog',
  '/wedding-guides',
  '/about',
  '/contact',
  '/faq',
  '/showroom'
]

function langAlternates(path: string): Record<string, string> {
  const languages: Record<string, string> = {}
  for (const l of SUPPORTED_LOCALES) languages[l] = localizedUrl(path, l)
  return languages
}

function entriesFor(path: string, lastModified?: Date): MetadataRoute.Sitemap {
  const languages = langAlternates(path)
  return SUPPORTED_LOCALES.map((locale: Locale) => ({
    url: localizedUrl(path, locale),
    lastModified: lastModified ?? new Date(),
    changeFrequency: path === '/' ? ('daily' as const) : ('weekly' as const),
    priority: path === '/' ? 1 : 0.7,
    alternates: { languages }
  }))
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const entries: MetadataRoute.Sitemap = []

  for (const path of STATIC_PATHS) {
    entries.push(...entriesFor(path))
  }

  // 商品页（首批，失败不阻塞静态条目）
  try {
    const page = await fetchStoreProducts({ page: 1, pageSize: 100 })
    for (const p of page?.data ?? []) {
      if (p.slug) entries.push(...entriesFor(`/product/${p.slug}`))
    }
  } catch {
    /* 上游不可用：仅输出静态条目 */
  }

  // base 兜底（确保至少返回非空）
  if (entries.length === 0) {
    entries.push({ url: siteBaseUrl(), lastModified: new Date() })
  }

  return entries
}
