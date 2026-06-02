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
      operatorId: filterOperatorId.value || undefined,
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
      list.value = res.items
      total.value = res.total
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
    return logsApi.exportOperationLogs(query())
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
