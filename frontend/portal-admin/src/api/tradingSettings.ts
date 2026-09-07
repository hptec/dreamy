// trading 域汇率维护 + 结算配置 + 税费 API（PAGE-TRD-A04 Settings 汇率/结算/税费 tab；决策 14/24/28）
// order-flow-complete E/F：汇率 refresh/history/manual_override；结算配置新字段；tax-rules / tax-destination-policies
import { get, post, put, patch, del } from './client'
import type {
  CheckoutConfig,
  ExchangeRate,
  ExchangeRateHistoryItem,
  ExchangeRateRefreshResult,
  TaxDestinationPolicy,
  TaxDestinationPolicyUpsert,
  TaxRule,
  TaxRuleUpsert,
} from './types'

export function listExchangeRates(): Promise<{ items: ExchangeRate[] }> {
  return get<{ items: ExchangeRate[] }>('/api/admin/exchange-rates')
}

/** manualOverride 省略 = 保持现值 */
export function updateExchangeRate(
  currency: string,
  rate: number | string,
  manualOverride?: boolean,
): Promise<ExchangeRate> {
  return put<ExchangeRate>(`/api/admin/exchange-rates/${currency}`, { rate, manualOverride })
}

/** 触发供应商刷新（manual 模式 409905；供应商失败 502602 保留现值） */
export function refreshExchangeRates(): Promise<ExchangeRateRefreshResult> {
  return post<ExchangeRateRefreshResult>('/api/admin/exchange-rates/refresh', {})
}

export function listExchangeRateHistory(currency: string, days = 30): Promise<{ items: ExchangeRateHistoryItem[] }> {
  return get<{ items: ExchangeRateHistoryItem[] }>(`/api/admin/exchange-rates/${currency}/history`, { params: { days } })
}

export function getCheckoutConfig(): Promise<CheckoutConfig> {
  return get<CheckoutConfig>('/api/admin/checkout-config')
}

export function updateCheckoutConfig(body: CheckoutConfig): Promise<CheckoutConfig> {
  return put<CheckoutConfig>('/api/admin/checkout-config', body)
}

// ---- 税率规则 ----

export function listTaxRules(countryCode?: string): Promise<{ items: TaxRule[] }> {
  return get<{ items: TaxRule[] }>('/api/admin/tax-rules', { params: countryCode ? { countryCode } : undefined })
}

export function createTaxRule(body: TaxRuleUpsert): Promise<TaxRule> {
  return post<TaxRule>('/api/admin/tax-rules', body)
}

export function updateTaxRule(id: number, body: TaxRuleUpsert): Promise<TaxRule> {
  return put<TaxRule>(`/api/admin/tax-rules/${id}`, body)
}

export function setTaxRuleEnabled(id: number, enabled: boolean): Promise<TaxRule> {
  return patch<TaxRule>(`/api/admin/tax-rules/${id}/enabled`, { enabled })
}

export function deleteTaxRule(id: number): Promise<void> {
  return del<void>(`/api/admin/tax-rules/${id}`)
}

// ---- 目的国政策（DDP/DDU） ----

export function listTaxDestinationPolicies(): Promise<{ items: TaxDestinationPolicy[] }> {
  return get<{ items: TaxDestinationPolicy[] }>('/api/admin/tax-destination-policies')
}

export function getTaxDestinationPolicy(countryCode: string): Promise<TaxDestinationPolicy> {
  return get<TaxDestinationPolicy>(`/api/admin/tax-destination-policies/${countryCode}`)
}

export function upsertTaxDestinationPolicy(countryCode: string, body: TaxDestinationPolicyUpsert): Promise<TaxDestinationPolicy> {
  return put<TaxDestinationPolicy>(`/api/admin/tax-destination-policies/${countryCode}`, body)
}
