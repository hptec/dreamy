// STORE-MKT-A01 usePromotionsStore：券分页 + 闪购列表（写成功后列表 refetch）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { marketingApi } from '@/api'
import type { Coupon, CouponUpsert, FlashSale, FlashSaleUpsert } from '@/api/types'
import { normalizeFilter } from '@/utils/validators'

export const usePromotionsStore = defineStore('promotions', () => {
  const coupons = ref<Coupon[]>([])
  const couponsTotal = ref(0)
  const couponPage = ref(1)
  const couponPageSize = ref(9)
  const couponStatus = ref('all')
  const couponSearch = ref('')
  const loadingCoupons = ref(false)

  const flashSales = ref<FlashSale[]>([])
  const flashStatus = ref('all')
  const loadingFlash = ref(false)

  async function fetchCoupons() {
    loadingCoupons.value = true
    try {
      const res = await marketingApi.listCoupons({
        page: couponPage.value,
        pageSize: couponPageSize.value,
        status: normalizeFilter(couponStatus.value),
        search: couponSearch.value.trim() || undefined,
      })
      coupons.value = res.data
      couponsTotal.value = res.totalElements
    } finally {
      loadingCoupons.value = false
    }
  }

  function setCouponPage(p: number) {
    couponPage.value = p
    return fetchCoupons()
  }

  function applyCouponFilters() {
    couponPage.value = 1
    return fetchCoupons()
  }

  async function saveCoupon(body: CouponUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createCoupon(body) : await marketingApi.updateCoupon(id, body)
    await fetchCoupons()
    return saved
  }

  /** 409703 → 视图 toast「当前发布状态不允许该操作」 */
  async function removeCoupon(id: number) {
    await marketingApi.deleteCoupon(id)
    await fetchCoupons()
  }

  async function fetchFlashSales() {
    loadingFlash.value = true
    try {
      const res = await marketingApi.listFlashSales(normalizeFilter(flashStatus.value))
      flashSales.value = res.items
    } finally {
      loadingFlash.value = false
    }
  }

  async function saveFlashSale(body: FlashSaleUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createFlashSale(body) : await marketingApi.updateFlashSale(id, body)
    await fetchFlashSales()
    return saved
  }

  async function removeFlashSale(id: number) {
    await marketingApi.deleteFlashSale(id)
    await fetchFlashSales()
  }

  return {
    coupons,
    couponsTotal,
    couponPage,
    couponPageSize,
    couponStatus,
    couponSearch,
    loadingCoupons,
    flashSales,
    flashStatus,
    loadingFlash,
    fetchCoupons,
    setCouponPage,
    applyCouponFilters,
    saveCoupon,
    removeCoupon,
    fetchFlashSales,
    saveFlashSale,
    removeFlashSale,
  }
})
