import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

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
