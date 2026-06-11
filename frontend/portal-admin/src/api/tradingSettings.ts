// trading 域汇率维护 + 结算配置 API（PAGE-TRD-A04 Settings 汇率/结算 tab；决策 14/24/28）
import { get, put } from './client'
import type { CheckoutConfig, ExchangeRate } from './types'

export function listExchangeRates(): Promise<{ items: ExchangeRate[] }> {
  return get<{ items: ExchangeRate[] }>('/api/admin/exchange-rates')
}

export function updateExchangeRate(currency: string, rate: number | string): Promise<ExchangeRate> {
  return put<ExchangeRate>(`/api/admin/exchange-rates/${currency}`, { rate })
}

export function getCheckoutConfig(): Promise<CheckoutConfig> {
  return get<CheckoutConfig>('/api/admin/checkout-config')
}

export function updateCheckoutConfig(body: CheckoutConfig): Promise<CheckoutConfig> {
  return put<CheckoutConfig>('/api/admin/checkout-config', body)
}
