'use client'

/**
 * LocalizedLink（决策 11 / 内部链接 locale 前缀）。
 * 包装 next/link：对站内绝对路径（以 / 开头、非 //）自动附加当前 locale 前缀。
 * - EN：保持无前缀根路径。
 * - ES/FR：/es/... 或 /fr/...。
 * 外链（http、mailto、#、//）与已带前缀的路径原样透传。
 */

import Link from 'next/link'
import type { ComponentProps } from 'react'
import { useLocale, withLocale, SUPPORTED_LOCALES, DEFAULT_LOCALE } from '@/lib/i18n/i18n-context'
import type { Locale } from '@/lib/api/types'

type LinkProps = ComponentProps<typeof Link>

function localizeHref(href: LinkProps['href'], locale: Locale): LinkProps['href'] {
  if (locale === DEFAULT_LOCALE) return href
  if (typeof href !== 'string') return href
  // 仅处理站内绝对路径
  if (!href.startsWith('/') || href.startsWith('//')) return href
  // 已带 locale 前缀则不重复
  const seg = href.split('/')[1]
  if (SUPPORTED_LOCALES.includes(seg as Locale)) return href
  return withLocale(href, locale)
}

export function LocalizedLink({ href, ...props }: LinkProps) {
  const locale = useLocale()
  return <Link href={localizeHref(href, locale)} {...props} />
}
