// STORE-TRD-A03 useTradingSettingsStore：汇率列表 + 结算配置 + 税率规则/目的国政策（PAGE-TRD-A04 Settings 三 tab）
// order-flow-complete E/F：refresh/history/manual_override；tax-rules CRUD + enabled；tax-destination-policies upsert
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { tradingSettingsApi } from '@/api'
import type {
  CheckoutConfig,
  ExchangeRate,
  ExchangeRateHistoryItem,
  TaxDestinationPolicy,
  TaxDestinationPolicyUpsert,
  TaxRule,
  TaxRuleUpsert,
} from '@/api/types'

export const useTradingSettingsStore = defineStore('tradingSettings', () => {
  const rates = ref<ExchangeRate[]>([])
  const checkoutConfig = ref<CheckoutConfig | null>(null)
  const loadingRates = ref(false)
  const loadingConfig = ref(false)
  const refreshing = ref(false)

  async function fetchRates() {
    loadingRates.value = true
    try {
      const res = await tradingSettingsApi.listExchangeRates()
      rates.value = res.items
    } finally {
      loadingRates.value = false
    }
  }

  /** FORM-TRD-A04：保存成功后重拉列表 */
  async function saveRate(currency: string, rate: number | string, manualOverride?: boolean) {
    await tradingSettingsApi.updateExchangeRate(currency, rate, manualOverride)
    await fetchRates()
  }

  /** 手工锁定开关：rate 传现值，仅切换 manual_override（乐观更新，失败回滚） */
  async function toggleManualOverride(row: ExchangeRate, on: boolean) {
    const prev = row.manualOverride
    row.manualOverride = on
    try {
      const updated = await tradingSettingsApi.updateExchangeRate(row.currency, row.rate, on)
      Object.assign(row, updated)
    } catch (e) {
      row.manualOverride = prev
      throw e
    }
  }

  async function refreshRates() {
    refreshing.value = true
    try {
      const res = await tradingSettingsApi.refreshExchangeRates()
      await fetchRates()
      return res
    } finally {
      refreshing.value = false
    }
  }

  function fetchHistory(currency: string, days = 30): Promise<ExchangeRateHistoryItem[]> {
    return tradingSettingsApi.listExchangeRateHistory(currency, days).then((r) => r.items)
  }

  async function fetchCheckoutConfig() {
    loadingConfig.value = true
    try {
      checkoutConfig.value = await tradingSettingsApi.getCheckoutConfig()
    } finally {
      loadingConfig.value = false
    }
  }

  async function saveCheckoutConfig(body: CheckoutConfig) {
    checkoutConfig.value = await tradingSettingsApi.updateCheckoutConfig(body)
    return checkoutConfig.value
  }

  // ---- 税率规则 ----
  const taxRules = ref<TaxRule[]>([])
  const loadingTaxRules = ref(false)

  async function fetchTaxRules() {
    loadingTaxRules.value = true
    try {
      taxRules.value = (await tradingSettingsApi.listTaxRules()).items
    } finally {
      loadingTaxRules.value = false
    }
  }

  async function saveTaxRule(body: TaxRuleUpsert, id?: number) {
    const saved = id == null ? await tradingSettingsApi.createTaxRule(body) : await tradingSettingsApi.updateTaxRule(id, body)
    const idx = taxRules.value.findIndex((r) => r.id === saved.id)
    if (idx >= 0) taxRules.value[idx] = saved
    else taxRules.value.push(saved)
    return saved
  }

  async function toggleTaxRule(row: TaxRule, enabled: boolean) {
    const prev = row.enabled
    if (prev === enabled) return
    row.enabled = enabled
    try {
      const updated = await tradingSettingsApi.setTaxRuleEnabled(row.id, enabled)
      Object.assign(row, updated)
    } catch (e) {
      row.enabled = prev
      throw e
    }
  }

  async function removeTaxRule(id: number) {
    await tradingSettingsApi.deleteTaxRule(id)
    taxRules.value = taxRules.value.filter((r) => r.id !== id)
  }

  // ---- 目的国政策 ----
  const policies = ref<TaxDestinationPolicy[]>([])
  const loadingPolicies = ref(false)

  async function fetchPolicies() {
    loadingPolicies.value = true
    try {
      policies.value = (await tradingSettingsApi.listTaxDestinationPolicies()).items
    } finally {
      loadingPolicies.value = false
    }
  }

  async function savePolicy(countryCode: string, body: TaxDestinationPolicyUpsert) {
    const saved = await tradingSettingsApi.upsertTaxDestinationPolicy(countryCode, body)
    const idx = policies.value.findIndex((p) => p.countryCode === saved.countryCode)
    if (idx >= 0) policies.value[idx] = saved
    else policies.value.push(saved)
    return saved
  }

  return {
    rates,
    checkoutConfig,
    loadingRates,
    loadingConfig,
    refreshing,
    fetchRates,
    saveRate,
    toggleManualOverride,
    refreshRates,
    fetchHistory,
    fetchCheckoutConfig,
    saveCheckoutConfig,
    taxRules,
    loadingTaxRules,
    fetchTaxRules,
    saveTaxRule,
    toggleTaxRule,
    removeTaxRule,
    policies,
    loadingPolicies,
    fetchPolicies,
    savePolicy,
  }
})
