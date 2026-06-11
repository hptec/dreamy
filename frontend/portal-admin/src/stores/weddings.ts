// STORE-MKT-A04 useWeddingsStore：Real Weddings 分页 + CRUD + 状态流转
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { marketingApi } from '@/api'
import type { RealWedding, RealWeddingUpsert } from '@/api/types'
import { normalizeFilter } from '@/utils/validators'

export const useWeddingsStore = defineStore('weddings', () => {
  const list = ref<RealWedding[]>([])
  const totalElements = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const statusFilter = ref('all')
  const loading = ref(false)

  async function fetch() {
    loading.value = true
    try {
      const res = await marketingApi.listWeddings({
        page: page.value,
        pageSize: pageSize.value,
        status: normalizeFilter(statusFilter.value),
      })
      list.value = res.data
      totalElements.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetch()
  }

  async function save(body: RealWeddingUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createWedding(body) : await marketingApi.updateWedding(id, body)
    await fetch()
    return saved
  }

  async function remove(id: number) {
    await marketingApi.deleteWedding(id)
    await fetch()
  }

  async function patchStatus(id: number, status: string) {
    const updated = await marketingApi.patchWeddingStatus(id, status)
    const idx = list.value.findIndex((w) => w.id === id)
    if (idx >= 0) list.value[idx] = updated
    return updated
  }

  return {
    list,
    totalElements,
    page,
    pageSize,
    statusFilter,
    loading,
    fetch,
    setPage,
    save,
    remove,
    patchStatus,
  }
})
