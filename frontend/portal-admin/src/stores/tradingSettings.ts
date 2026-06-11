// STORE-TRD-A03 useTradingSettingsStore：汇率列表 + 结算配置（PAGE-TRD-A04 Settings 两 tab）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { tradingSettingsApi } from '@/api'
import type { CheckoutConfig, ExchangeRate } from '@/api/types'

export const useTradingSettingsStore = defineStore('tradingSettings', () => {
  const rates = ref<ExchangeRate[]>([])
  const checkoutConfig = ref<CheckoutConfig | null>(null)
  const loadingRates = ref(false)
  const loadingConfig = ref(false)

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
  async function saveRate(currency: string, rate: number | string) {
    await tradingSettingsApi.updateExchangeRate(currency, rate)
    await fetchRates()
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

  return {
    rates,
    checkoutConfig,
    loadingRates,
    loadingConfig,
    fetchRates,
    saveRate,
    fetchCheckoutConfig,
    saveCheckoutConfig,
  }
})
