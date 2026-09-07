// STORE-SHP-01 useShippingStore：承运方 + 运费选项（fetchAll 并行；Toggle 乐观更新；enabledCount 预判）
// order-flow-complete D：rates → options（旧 /shipping/rates 只读已废弃）；国家列表懒加载供试算 / 税费页复用
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { shippingApi } from '@/api'
import { CarrierStatus } from '@/api/types'
import type { Carrier, CarrierUpsert, Country, ShippingOption, ShippingOptionUpsert } from '@/api/types'

export const useShippingStore = defineStore('shipping', () => {
  const carriers = ref<Carrier[]>([])
  const options = ref<ShippingOption[]>([])
  const countries = ref<Country[]>([])
  const loadingCarriers = ref(false)
  const loadingOptions = ref(false)
  const loadingCountries = ref(false)
  const saving = ref(false)

  /** 前端预判：仅剩 1 个 enabled 时 Toggle/删除置灰（后端 409902 兜底） */
  const enabledCount = computed(() => carriers.value.filter((c) => c.status === CarrierStatus.ENABLED).length)

  /** 已配置 code 的启用承运商（创建包裹 / 运费选项下拉来源） */
  const codedCarriers = computed(() => carriers.value.filter((c) => !!c.code))

  async function fetchCarriers() {
    loadingCarriers.value = true
    try {
      carriers.value = (await shippingApi.listCarriers()).items
    } finally {
      loadingCarriers.value = false
    }
  }

  async function fetchOptions() {
    loadingOptions.value = true
    try {
      options.value = (await shippingApi.listShippingOptions()).items
    } finally {
      loadingOptions.value = false
    }
  }

  async function fetchAll() {
    await Promise.all([fetchCarriers(), fetchOptions()])
  }

  /** 国家列表（公开接口、250 国；带缓存，多页复用） */
  async function ensureCountries() {
    if (countries.value.length) return countries.value
    loadingCountries.value = true
    try {
      countries.value = (await shippingApi.listCountries()).items
      return countries.value
    } finally {
      loadingCountries.value = false
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

  async function saveOption(body: ShippingOptionUpsert, id?: number) {
    saving.value = true
    try {
      const saved = id == null
        ? await shippingApi.createShippingOption(body)
        : await shippingApi.updateShippingOption(id, body)
      const idx = options.value.findIndex((r) => r.id === saved.id)
      if (idx >= 0) options.value[idx] = saved
      else options.value.push(saved)
      return saved
    } finally {
      saving.value = false
    }
  }

  /** 乐观更新启用开关；失败回滚 */
  async function toggleOption(row: ShippingOption, enabled: boolean) {
    const prev = row.enabled
    if (prev === enabled) return
    row.enabled = enabled
    try {
      const updated = await shippingApi.setShippingOptionEnabled(row.id, enabled)
      Object.assign(row, updated)
    } catch (e) {
      row.enabled = prev
      throw e
    }
  }

  async function removeOption(id: number) {
    await shippingApi.deleteShippingOption(id)
    options.value = options.value.filter((r) => r.id !== id)
  }

  return {
    carriers,
    options,
    countries,
    loadingCarriers,
    loadingOptions,
    loadingCountries,
    saving,
    enabledCount,
    codedCarriers,
    fetchCarriers,
    fetchOptions,
    fetchAll,
    ensureCountries,
    toggleCarrier,
    saveCarrier,
    removeCarrier,
    saveOption,
    toggleOption,
    removeOption,
  }
})
