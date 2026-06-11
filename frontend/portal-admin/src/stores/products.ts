// STORE-CAT-A01 useProductsStore：商品列表服务端分页/筛选 + 行内乐观更新（失败回滚+toast 由视图层处理）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { catalogApi } from '@/api'
import type { AdminProductListItem, ProductStatus } from '@/api/types'
import { normalizeFilter } from '@/utils/validators'

export const useProductsStore = defineStore('products', () => {
  const list = ref<AdminProductListItem[]>([])
  const totalElements = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  // 服务端筛选（status / categoryId / search）
  const filterStatus = ref('all')
  const filterCategoryId = ref<number | 'all'>('all')
  const search = ref('')

  async function fetchList() {
    loading.value = true
    try {
      const res = await catalogApi.listProducts({
        page: page.value,
        pageSize: pageSize.value,
        status: normalizeFilter(filterStatus.value),
        categoryId: filterCategoryId.value === 'all' ? undefined : filterCategoryId.value,
        search: search.value.trim() || undefined,
      })
      list.value = res.data
      totalElements.value = res.totalElements
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

  /** FORM-CAT-A02：行内 Toggle 乐观更新，失败回滚后抛错由视图 toast */
  async function toggleStatus(row: AdminProductListItem, status: ProductStatus) {
    const prev = row.status
    if (prev === status) return // 幂等：同态不重复发请求
    row.status = status
    try {
      const updated = await catalogApi.toggleProductStatus(row.id, status)
      Object.assign(row, updated)
    } catch (e) {
      row.status = prev
      throw e
    }
  }

  async function patchFlags(
    row: AdminProductListItem,
    partial: { isNew?: boolean; isBest?: boolean; recommend?: boolean; sort?: number },
  ) {
    const prev = { isNew: row.isNew, isBest: row.isBest, recommend: row.recommend, sort: row.sort }
    Object.assign(row, partial)
    try {
      const updated = await catalogApi.patchProductFlags(row.id, partial)
      Object.assign(row, updated)
    } catch (e) {
      Object.assign(row, prev)
      throw e
    }
  }

  async function remove(id: number) {
    await catalogApi.deleteProduct(id)
    await fetchList()
  }

  return {
    list,
    totalElements,
    page,
    pageSize,
    loading,
    filterStatus,
    filterCategoryId,
    search,
    fetchList,
    setPage,
    applyFilters,
    toggleStatus,
    patchFlags,
    remove,
  }
})
