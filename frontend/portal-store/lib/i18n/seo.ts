import type { Metadata } from 'next'
import type { Locale } from '../api/types'

/**
 * SEO 多语言 alternates 构造（FUNC-015 / 决策 11）。
 * EN 无前缀根路径为 x-default；ES/FR 带 /es、/fr 前缀。
 */

export const SUPPORTED_LOCALES: Locale[] = ['en', 'es', 'fr']

export function siteBaseUrl(): string {
  return (process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:5173').replace(/\/$/, '')
}

/** 去掉路径上的 locale 前缀，返回无前缀路径（始终以 / 开头）。 */
export function stripLocalePath(path: string): string {
  const seg = path.split('/')[1]
  if ((SUPPORTED_LOCALES as string[]).includes(seg)) {
    const rest = path.slice(seg.length + 1)
    return rest === '' ? '/' : rest
  }
  return path.startsWith('/') ? path : `/${path}`
}

/** locale + 无前缀路径 → 完整 URL（EN 不加前缀）。 */
export function localizedUrl(path: string, locale: Locale): string {
  const clean = stripLocalePath(path)
  const base = siteBaseUrl()
  if (locale === 'en') return `${base}${clean === '/' ? '' : clean}`
  return `${base}/${locale}${clean === '/' ? '' : clean}`
}

/** 生成 Next Metadata.alternates（canonical + hreflang languages + x-default）。 */
export function buildAlternates(path: string, current: Locale): NonNullable<Metadata['alternates']> {
  const clean = stripLocalePath(path)
  const languages: Record<string, string> = {}
  for (const l of SUPPORTED_LOCALES) {
    languages[l] = localizedUrl(clean, l)
  }
  languages['x-default'] = localizedUrl(clean, 'en')
  return {
    canonical: localizedUrl(clean, current),
    languages
  }
}
