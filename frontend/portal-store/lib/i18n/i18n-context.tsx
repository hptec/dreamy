'use client'

/**
 * i18n Context（STORE-S04）。
 * 静态导出 SPA 形态：locale 由客户端持久化（localStorage），缺省 en。
 * 提供 UI 文案（t）与错误码本地化（te）。
 */

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { Locale } from '../api/types'
import { getMessages, type UiMessages } from './messages'
import { localizeError } from './error-messages'

const LOCALE_KEY = 'dreamy_store_locale'
const SUPPORTED: Locale[] = ['en', 'es', 'fr']

interface I18nContextValue {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: UiMessages
  te: (code: number) => string
}

const I18nContext = createContext<I18nContextValue | null>(null)

function detectInitialLocale(): Locale {
  if (typeof window === 'undefined') return 'en'
  try {
    const saved = localStorage.getItem(LOCALE_KEY)
    if (saved && SUPPORTED.includes(saved as Locale)) return saved as Locale
  } catch {
    /* ignore */
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language.slice(0, 2) : 'en'
  return SUPPORTED.includes(nav as Locale) ? (nav as Locale) : 'en'
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>('en')

  useEffect(() => {
    setLocaleState(detectInitialLocale())
  }, [])

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next)
    try {
      localStorage.setItem(LOCALE_KEY, next)
    } catch {
      /* ignore */
    }
  }, [])

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

/** 当前 locale（用于 API 客户端 Accept-Language，无 React 上下文场景） */
export function getActiveLocale(): Locale {
  return detectInitialLocale()
}
