// STORE-TRD-A02 useRefundsStore：退款工单分页/筛选 + 审批/登记（approve/reject 成功后行内刷新）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { refundsApi } from '@/api'
import type { AdminRefund } from '@/api/types'
import { normalizeFilter } from '@/utils/validators'

export const useRefundsStore = defineStore('refunds', () => {
  const list = ref<AdminRefund[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  const status = ref('all')
  const search = ref('')

  async function fetchList() {
    loading.value = true
    try {
      const res = await refundsApi.listRefunds({
        page: page.value,
        pageSize: pageSize.value,
        status: normalizeFilter(status.value),
        search: search.value.trim() || undefined,
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

  function replaceRow(updated: AdminRefund) {
    const idx = list.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) list.value[idx] = updated
  }

  /** FORM-TRD-A03：同意（选填退货物流单号——决策 31）；502601/504601 由视图弹窗可重试 */
  async function approve(id: number, returnTrackingNo?: string) {
    const updated = await refundsApi.approveRefund(id, returnTrackingNo)
    replaceRow(updated)
    return updated
  }

  async function reject(id: number, reason: string) {
    const updated = await refundsApi.rejectRefund(id, reason)
    replaceRow(updated)
    return updated
  }

  async function patchTracking(id: number, returnTrackingNo: string) {
    const updated = await refundsApi.patchRefund(id, returnTrackingNo)
    replaceRow(updated)
    return updated
  }

  return {
    list,
    total,
    page,
    pageSize,
    loading,
    status,
    search,
    fetchList,
    setPage,
    applyFilters,
    approve,
    reject,
    patchTracking,
  }
})
