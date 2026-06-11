// STORE-CAT-A01 useProductsStore：商品列表服务端分页/筛选 + 行内乐观更新（失败回滚+toast 由视图层处理）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { catalogApi } from '@/api'
import type { AdminProductListItem, ProductBatchAction, ProductBatchResult, ProductStatus } from '@/api/types'
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

  /**
   * STORE-CAT-P01 / FORM-CAT-P01（ALIGN-007）：批量操作（逐条容错语义）。
   * 透传 { successIds, failures }，失败明细展示与整页刷新由视图层负责。
   */
  function batchOperate(action: ProductBatchAction, ids: number[]): Promise<ProductBatchResult> {
    return catalogApi.batchProducts(action, ids)
  }

  const exporting = ref(false)

  /**
   * STORE-CAT-P01 / FORM-CAT-P02（ALIGN-007）：导出当前服务端筛选的商品 CSV。
   * 仅 search/categoryId/status 口径（「更多筛选」当前页过滤不参与导出）；
   * exporting 防重复提交；返回截断标记供视图 toast.warn。
   */
  async function exportCsv(): Promise<{ truncated: boolean }> {
    if (exporting.value) return { truncated: false }
    exporting.value = true
    try {
      return await catalogApi.exportProducts({
        status: normalizeFilter(filterStatus.value),
        categoryId: filterCategoryId.value === 'all' ? undefined : filterCategoryId.value,
        search: search.value.trim() || undefined,
      })
    } finally {
      exporting.value = false
    }
  }

  return {
    list,
    totalElements,
    page,
    pageSize,
    loading,
    exporting,
    filterStatus,
    filterCategoryId,
    search,
    fetchList,
    setPage,
    applyFilters,
    toggleStatus,
    patchFlags,
    remove,
    batchOperate,
    exportCsv,
  }
})
