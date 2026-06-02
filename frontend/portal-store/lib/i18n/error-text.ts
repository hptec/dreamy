'use client'

/**
 * ApiError → 本地化展示文案（含 details 增强）。
 * - 40101 附剩余次数（remaining_attempts）。
 * - 42901 附剩余重发秒（remaining_resend_seconds）。
 * 数字 code → lib/i18n/error-messages 三语映射（STORE-S04）。
 */

import type { Locale } from '../api/types'
import { ApiError } from '../api/client'
import { localizeError } from './error-messages'

function num(details: Record<string, unknown> | null | undefined, ...keys: string[]): number | null {
  if (!details) return null
  for (const k of keys) {
    const v = details[k]
    if (typeof v === 'number') return v
    if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) return Number(v)
  }
  return null
}

export function errorText(err: ApiError, locale: Locale): string {
  const base = localizeError(locale, err.code)
  if (err.code === 40101) {
    const remaining = num(err.details, 'remaining_attempts', 'remainingAttempts')
    if (remaining != null) {
      const suffix: Record<Locale, string> = {
        en: ` ${remaining} attempt(s) left.`,
        es: ` Quedan ${remaining} intento(s).`,
        fr: ` ${remaining} tentative(s) restante(s).`
      }
      return base + suffix[locale]
    }
  }
  if (err.code === 42901) {
    const secs = num(err.details, 'remaining_resend_seconds', 'remainingResendSeconds')
    if (secs != null) {
      const suffix: Record<Locale, string> = {
        en: ` (${secs}s)`,
        es: ` (${secs}s)`,
        fr: ` (${secs}s)`
      }
      return base + suffix[locale]
    }
  }
  return base
}

/** 从 ApiError 提取剩余重发秒（用于驱动倒计时），无则 null。 */
export function resendSecondsFrom(err: ApiError): number | null {
  if (err.code !== 42901) return null
  return num(err.details, 'remaining_resend_seconds', 'remainingResendSeconds')
}
