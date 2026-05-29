import { defineStore } from 'pinia'
import { PROMO_CODES } from '../data/catalog'

function load() {
  try {
    return JSON.parse(localStorage.getItem('me_cart') || '[]')
  } catch (e) {
    return []
  }
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: load(),
    promo: null, // { code, type, value }
  }),
  getters: {
    count: (s) => s.items.reduce((n, i) => n + i.qty, 0),
    subtotal: (s) => s.items.reduce((n, i) => n + i.unitPriceUSD * i.qty, 0),
    discountUSD(s) {
      if (!s.promo) return 0
      const sub = s.items.reduce((n, i) => n + i.unitPriceUSD * i.qty, 0)
      return s.promo.type === 'percent' ? (sub * s.promo.value) / 100 : Math.min(s.promo.value, sub)
    },
    isEmpty: (s) => s.items.length === 0,
  },
  actions: {
    persist() {
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_cart', JSON.stringify(this.items))
    },
    add(item) {
      const key = `${item.productId}|${item.colorId}|${item.size}|${item.productionTimeId}`
      const existing = this.items.find((i) => i.key === key)
      if (existing) existing.qty += item.qty || 1
      else this.items.push({ ...item, key, qty: item.qty || 1 })
      this.persist()
    },
    remove(key) {
      this.items = this.items.filter((i) => i.key !== key)
      this.persist()
    },
    setQty(key, qty) {
      const it = this.items.find((i) => i.key === key)
      if (it) it.qty = Math.max(1, qty)
      this.persist()
    },
    applyPromo(code) {
      const p = PROMO_CODES[code.toUpperCase()]
      if (p) {
        this.promo = p
        return true
      }
      return false
    },
    clearPromo() {
      this.promo = null
    },
    clear() {
      this.items = []
      this.promo = null
      this.persist()
    },
  },
})
