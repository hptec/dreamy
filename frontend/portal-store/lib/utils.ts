import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const symbols: Record<string, string> = { USD: '$', EUR: '€', CAD: 'C$', AUD: 'A$', GBP: '£' }

// 展示换算汇率注册表（COMP-TRD-S09 / 决策 14）：
// 默认静态兜底，应用启动由 currencyStore 用 GET /api/store/exchange-rates 覆盖（下单锁汇以服务端为准）。
let liveRates: Record<string, number> = { USD: 1, EUR: 0.92, CAD: 1.37, AUD: 1.52, GBP: 0.79 }

export function setDisplayRates(rates: Record<string, number>) {
  liveRates = { ...liveRates, ...rates, USD: 1 }
}

export function getDisplayRate(currency: string): number {
  return liveRates[currency] ?? 1
}

/**
 * USD 基准价 → 当前币种展示（决策 14 客户端展示换算）。
 * multiCurrencyPrices 覆盖价优先（决策 14 连带约束）。
 */
export function formatPrice(
  amountUsd: number,
  currency = 'USD',
  multiCurrencyPrices?: Record<string, number> | null
) {
  const override = multiCurrencyPrices?.[currency]
  const converted = override ?? amountUsd * (liveRates[currency] ?? 1)
  return `${symbols[currency] ?? '$'}${converted.toFixed(0)}`
}

/** 已是订单币种的金额格式化（结算/订单：金额由服务端按锁汇产出，不再换算） */
export function formatAmount(amount: number, currency = 'USD', digits = 2) {
  return `${symbols[currency] ?? '$'}${amount.toFixed(digits)}`
}

export function installments(amount: number, parts = 4) {
  return (amount / parts).toFixed(0)
}

/** 'YYYY-MM-DD' → 'Sep 19, 2026' */
export function formatDateLong(dateStr: string): string {
  const d = new Date(`${dateStr}T00:00:00`)
  if (Number.isNaN(d.getTime())) return dateStr
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

/** ISO datetime → 'Sep 19, 2026' */
export function formatDateTimeLong(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export function daysUntil(dateStr: string): number {
  const target = new Date(`${dateStr}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86400000)
}
