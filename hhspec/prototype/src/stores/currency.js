import { defineStore } from 'pinia'
import { CURRENCIES, CURRENCY_MAP, formatMoney, convert } from '../config/currency'

export const useCurrencyStore = defineStore('currency', {
  state: () => ({
    code: (typeof localStorage !== 'undefined' && localStorage.getItem('me_currency')) || 'USD',
  }),
  getters: {
    current: (s) => CURRENCY_MAP[s.code] || CURRENCY_MAP.USD,
    list: () => CURRENCIES,
  },
  actions: {
    setCurrency(code) {
      this.code = code
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_currency', code)
    },
    format(usdAmount) {
      return formatMoney(usdAmount, this.code)
    },
    convert(usdAmount) {
      return convert(usdAmount, this.code)
    },
  },
})
