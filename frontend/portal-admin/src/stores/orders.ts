// STORE-TRD-A01 useOrdersStore：后台订单列表分页/筛选 + 详情 + 发货/状态/代客退款（写成功后 fetchDetail 重载）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ordersApi } from '@/api'
import type { AdminOrderDetail, AdminOrderListItem } from '@/api/types'
import { dateToEndOfDay, dateToStartOfDay, normalizeFilter } from '@/utils/validators'

export const useOrdersStore = defineStore('orders', () => {
  const list = ref<AdminOrderListItem[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  const status = ref('all')
  const search = ref('')
  const currency = ref('all')
  const from = ref('')
  const to = ref('')

  const detail = ref<AdminOrderDetail | null>(null)
  const detailLoading = ref(false)

  async function fetchList() {
    loading.value = true
    try {
      const res = await ordersApi.listOrders({
        page: page.value,
        pageSize: pageSize.value,
        status: normalizeFilter(status.value),
        search: search.value.trim() || undefined,
        currency: normalizeFilter(currency.value),
        from: dateToStartOfDay(from.value),
        to: dateToEndOfDay(to.value),
      })
      list.value = res.data
      total.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetchList()
  }

  function applyFilters() {
    page.value = 1
    return fetchList()
  }

  async function fetchDetail(id: number) {
    detailLoading.value = true
    try {
      detail.value = await ordersApi.getOrder(id)
      return detail.value
    } finally {
      detailLoading.value = false
    }
  }

  /** FORM-TRD-A01：发货（成功后详情重载） */
  async function ship(id: number, carrier: string, trackingNo: string) {
    detail.value = await ordersApi.shipOrder(id, { carrier, trackingNo })
    return detail.value
  }

  /** COMP-TRD-A05：确认完成 / 取消订单 */
  async function patchStatus(id: number, statusValue: string) {
    detail.value = await ordersApi.patchOrderStatus(id, statusValue)
    return detail.value
  }

  /** FORM-TRD-A02：代客退款（成功后详情重载带出工单） */
  async function createRefund(id: number, amount: number | string, reason: string) {
    const refund = await ordersApi.createRefund(id, { amount, reason })
    await fetchDetail(id)
    return refund
  }

  return {
    list,
    total,
    page,
    pageSize,
    loading,
    status,
    search,
    currency,
    from,
    to,
    detail,
    detailLoading,
    fetchList,
    setPage,
    applyFilters,
    fetchDetail,
    ship,
    patchStatus,
    createRefund,
  }
})
