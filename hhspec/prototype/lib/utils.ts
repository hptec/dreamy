import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { CustomSizeMeasurements } from '@/data/types'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatPrice(amount: number, currency = 'USD') {
  const symbols: Record<string, string> = { USD: '$', CAD: 'C$', AUD: 'A$', GBP: '£' }
  const rates: Record<string, number> = { USD: 1, CAD: 1.37, AUD: 1.52, GBP: 0.79 }
  const converted = amount * (rates[currency] ?? 1)
  return `${symbols[currency] ?? '$'}${converted.toFixed(0)}`
}

export function installments(amount: number, parts = 4) {
  return (amount / parts).toFixed(0)
}

// ============ 迭代 4：婚期交期判定 / 定制尺寸 ============

/** 运输缓冲（天）：生产周期之外的物流时间 */
export const SHIPPING_BUFFER_DAYS = 10
/** 加急生产费（USD） */
export const RUSH_FEE = 45

export type DeliveryVerdict = 'ok' | 'rush' | 'late'

/** 距目标日期的整天数（含今天为 0） */
export function daysUntil(dateStr: string): number {
  const target = new Date(`${dateStr}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86400000)
}

/** 按商品生产周期 + 运输缓冲判定三态：标准来得及 / 需加急 / 来不及 */
export function deliveryVerdict(weddingDate: string, leadTimeDays: number, rushAvailable: boolean): DeliveryVerdict {
  const days = daysUntil(weddingDate)
  if (days >= leadTimeDays + SHIPPING_BUFFER_DAYS) return 'ok'
  const rushLead = Math.ceil(leadTimeDays * 0.6)
  if (rushAvailable && days >= rushLead + SHIPPING_BUFFER_DAYS) return 'rush'
  return 'late'
}

export function formatCustomSize(m: CustomSizeMeasurements): string {
  return `Bust ${m.bust}″ · Waist ${m.waist}″ · Hips ${m.hips}″ · Hollow-to-Floor ${m.hollowToFloor}″ · Height ${m.height}″ · Heel ${m.heelHeight}″`
}

/** 'YYYY-MM-DD' → 'Sep 19, 2026' */
export function formatDateLong(dateStr: string): string {
  const d = new Date(`${dateStr}T00:00:00`)
  if (Number.isNaN(d.getTime())) return dateStr
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
