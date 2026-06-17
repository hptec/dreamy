'use client'

/**
 * i18n Context（FUNC-001 / FUNC-014 / 决策 11）。
 * - locale 权威来源：URL 路径前缀（[locale] 段）→ 由 layout 注入 initialLocale（SSR 正确）。
 * - 切换语言：写 NEXT_LOCALE cookie（SSR 可读）+ localStorage（降级辅助）+ 路由跳到对应前缀 URL。
 * - localStorage 不再作为路由依据，仅作显式切换记忆（决策 11）。
 */

import { createContext, useCallback, useContext, useMemo, type ReactNode } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import type { Locale } from '../api/types'
import { getMessages, type UiMessages } from './messages'
import { localizeError } from './error-messages'
import { updateProfile } from '../api/auth-api'
import { getAccessToken } from '../api/token-store'

const LOCALE_KEY = 'dreamy_store_locale'
const COOKIE_KEY = 'NEXT_LOCALE'
export const SUPPORTED_LOCALES: Locale[] = ['en', 'es', 'fr']
export const DEFAULT_LOCALE: Locale = 'en'

interface I18nContextValue {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: UiMessages
  te: (code: number) => string
}

const I18nContext = createContext<I18nContextValue | null>(null)

/** 去掉路径上的 locale 前缀，返回无前缀路径（始终以 / 开头）。 */
export function stripLocale(pathname: string): string {
  const seg = pathname.split('/')[1]
  if (SUPPORTED_LOCALES.includes(seg as Locale)) {
    const rest = pathname.slice(seg.length + 1)
    return rest === '' ? '/' : rest
  }
  return pathname
}

/** 给无前缀路径加上 locale 前缀（EN 不加前缀，决策 11）。 */
export function withLocale(path: string, locale: Locale): string {
  const clean = stripLocale(path)
  if (locale === DEFAULT_LOCALE) return clean
  return clean === '/' ? `/${locale}` : `/${locale}${clean}`
}

export function I18nProvider({ children, initialLocale }: { children: ReactNode; initialLocale?: Locale }) {
  const router = useRouter()
  const pathname = usePathname()
  const locale: Locale = SUPPORTED_LOCALES.includes(initialLocale as Locale) ? (initialLocale as Locale) : DEFAULT_LOCALE

  const setLocale = useCallback(
    (next: Locale) => {
      if (!SUPPORTED_LOCALES.includes(next)) return
      try {
        localStorage.setItem(LOCALE_KEY, next)
      } catch {
        /* ignore */
      }
      // NEXT_LOCALE cookie 让 SSR/中间件读到偏好（1 年）
      try {
        document.cookie = `${COOKIE_KEY}=${next};path=/;max-age=31536000;samesite=lax`
      } catch {
        /* ignore */
      }
      // FUNC-019：登录态用户持久化 locale_pref 到服务端（邮件发信优先取此值）。
      // fire-and-forget——未登录或接口失败不阻塞语言切换（决策 13）。
      if (getAccessToken()) {
        void updateProfile({ localePref: next }).catch(() => {
          /* 非阻塞：持久化失败不影响前端切换 */
        })
      }
      // 跳到对应 locale 前缀 URL（保持当前路径）
      const target = withLocale(pathname ?? '/', next)
      router.push(target)
    },
    [pathname, router]
  )

  const value = useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale,
      t: getMessages(locale),
      te: (code: number) => localizeError(locale, code)
    }),
    [locale, setLocale]
  )

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext)
  if (!ctx) throw new Error('useI18n must be used within I18nProvider')
  return ctx
}

/** 当前 locale（便捷 hook）。 */
export function useLocale(): Locale {
  return useI18n().locale
}

/**
 * 当前 locale（用于 API 客户端 Accept-Language，无 React 上下文场景）。
 * 来源优先级：NEXT_LOCALE cookie > localStorage > navigator > 默认 EN（决策 11）。
 */
export function getActiveLocale(): Locale {
  if (typeof document !== 'undefined') {
    const m = document.cookie.match(/(?:^|;\s*)NEXT_LOCALE=([^;]+)/)
    if (m && SUPPORTED_LOCALES.includes(m[1] as Locale)) return m[1] as Locale
  }
  if (typeof window !== 'undefined') {
    try {
      const saved = localStorage.getItem(LOCALE_KEY)
      if (saved && SUPPORTED_LOCALES.includes(saved as Locale)) return saved as Locale
    } catch {
      /* ignore */
    }
    const nav = typeof navigator !== 'undefined' ? navigator.language.slice(0, 2) : 'en'
    if (SUPPORTED_LOCALES.includes(nav as Locale)) return nav as Locale
  }
  return DEFAULT_LOCALE
}
