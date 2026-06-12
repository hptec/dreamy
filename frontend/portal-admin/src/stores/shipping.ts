// STORE-SHP-01 useShippingStore：承运方 + 邮费规则（fetchAll 并行；Toggle 乐观更新；enabledCount 预判）
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { shippingApi } from '@/api'
import { CarrierStatus } from '@/api/types'
import type { Carrier, CarrierUpsert, ShippingRate, ShippingRateUpsert } from '@/api/types'

export const useShippingStore = defineStore('shipping', () => {
  const carriers = ref<Carrier[]>([])
  const rates = ref<ShippingRate[]>([])
  const loadingCarriers = ref(false)
  const loadingRates = ref(false)
  const saving = ref(false)

  /** 前端预判：仅剩 1 个 enabled 时 Toggle/删除置灰（后端 409902 兜底） */
  const enabledCount = computed(() => carriers.value.filter((c) => c.status === CarrierStatus.ENABLED).length)

  async function fetchAll() {
    loadingCarriers.value = true
    loadingRates.value = true
    try {
      const [carriersRes, ratesRes] = await Promise.all([shippingApi.listCarriers(), shippingApi.listRates()])
      carriers.value = carriersRes.items
      rates.value = ratesRes.items
    } finally {
      loadingCarriers.value = false
      loadingRates.value = false
    }
  }

  /** FORM-SHP-04：乐观更新 Toggle → PATCH；失败回滚（404901 视图整列表 refetch） */
  async function toggleCarrier(row: Carrier, status: CarrierStatus) {
    const prev = row.status
    if (prev === status) return
    row.status = status
    try {
      const updated = await shippingApi.toggleCarrierStatus(row.id, status)
      Object.assign(row, updated)
    } catch (e) {
      row.status = prev
      throw e
    }
  }

  async function saveCarrier(body: CarrierUpsert, id?: number) {
    saving.value = true
    try {
      const saved = id == null ? await shippingApi.createCarrier(body) : await shippingApi.updateCarrier(id, body)
      const idx = carriers.value.findIndex((c) => c.id === saved.id)
      if (idx >= 0) carriers.value[idx] = saved
      else carriers.value.push(saved)
      return saved
    } finally {
      saving.value = false
    }
  }

  async function removeCarrier(id: number) {
    await shippingApi.deleteCarrier(id)
    carriers.value = carriers.value.filter((c) => c.id !== id)
  }

  async function saveRate(body: ShippingRateUpsert, id?: number) {
    saving.value = true
    try {
      const saved = id == null ? await shippingApi.createRate(body) : await shippingApi.updateRate(id, body)
      const idx = rates.value.findIndex((r) => r.id === saved.id)
      if (idx >= 0) rates.value[idx] = saved
      else rates.value.push(saved)
      return saved
    } finally {
      saving.value = false
    }
  }

  async function removeRate(id: number) {
    await shippingApi.deleteRate(id)
    rates.value = rates.value.filter((r) => r.id !== id)
  }

  return {
    carriers,
    rates,
    loadingCarriers,
    loadingRates,
    saving,
    enabledCount,
    fetchAll,
    toggleCarrier,
    saveCarrier,
    removeCarrier,
    saveRate,
    removeRate,
  }
})
