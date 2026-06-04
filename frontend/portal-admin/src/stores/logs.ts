// STORE-A04 logsStore：操作日志分页/筛选/loading（PAGE-A07，只读 + 导出）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { logsApi } from '@/api'
import type { OperationLog } from '@/api/types'

export const useLogsStore = defineStore('logs', () => {
  const list = ref<OperationLog[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)

  const filterAction = ref('')
  const filterOperatorId = ref('')
  const filterFrom = ref('')
  const filterTo = ref('')

  function query() {
    return {
      action: filterAction.value || undefined,
      operatorId: filterOperatorId.value ? Number(filterOperatorId.value) : undefined,
      from: filterFrom.value ? new Date(filterFrom.value).toISOString() : undefined,
      to: filterTo.value ? new Date(filterTo.value).toISOString() : undefined,
    }
  }

  async function fetchList() {
    loading.value = true
    try {
      const res = await logsApi.listOperationLogs({
        page: page.value,
        pageSize: pageSize.value,
        ...query(),
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

  function exportCsv() {
    const q = query()
    // 后端导出强制时间窗（必传 from/to，跨度 ≤92 天，防全表流式扫描）。
    // 未选范围时按「近 90 天」补全，避免 422；只缺一端则按另一端推算。
    const DAY = 24 * 60 * 60 * 1000
    let from = q.from ? new Date(q.from).getTime() : NaN
    let to = q.to ? new Date(q.to).getTime() : NaN
    if (Number.isNaN(to)) to = Date.now()
    if (Number.isNaN(from)) from = to - 90 * DAY
    q.from = new Date(from).toISOString()
    q.to = new Date(to).toISOString()
    return logsApi.exportOperationLogs(q)
  }

  return {
    list,
    total,
    page,
    pageSize,
    loading,
    filterAction,
    filterOperatorId,
    filterFrom,
    filterTo,
    fetchList,
    setPage,
    applyFilters,
    exportCsv,
  }
})
