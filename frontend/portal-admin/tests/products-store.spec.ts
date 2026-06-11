// STORE-CAT-A01 useProductsStore 单测：分页参数构造 + 行内乐观更新失败回滚
// STORE-CAT-P01（ALIGN-007）：batchOperate 逐条容错透传 + exportCsv 服务端筛选口径/截断标记
// （组件级断言——勾选列/批量栏/失败明细面板交互：deferred-to-L3-test，工程无 @vue/test-utils）
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listProducts = vi.fn()
const toggleProductStatus = vi.fn()
const patchProductFlags = vi.fn()
const batchProducts = vi.fn()
const exportProducts = vi.fn()

vi.mock('@/api', () => ({
  catalogApi: {
    listProducts: (...args: unknown[]) => listProducts(...args),
    toggleProductStatus: (...args: unknown[]) => toggleProductStatus(...args),
    patchProductFlags: (...args: unknown[]) => patchProductFlags(...args),
    deleteProduct: vi.fn(),
    batchProducts: (...args: unknown[]) => batchProducts(...args),
    exportProducts: (...args: unknown[]) => exportProducts(...args),
  },
}))

import { useProductsStore } from '@/stores/products'
import type { AdminProductListItem } from '@/api/types'

function pageOf(data: AdminProductListItem[], total = data.length) {
  return { data, totalElements: total, pageNumber: 1, pageSize: 10, totalPages: 1, numberOfElements: data.length }
}

const row = (): AdminProductListItem => ({
  id: 1,
  name: 'Gown',
  slug: 'gown',
  categoryId: 1,
  price: 100,
  status: 'draft',
  isNew: false,
  recommend: false,
  sort: 0,
  stockTotal: 5,
})

describe('useProductsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it("fetchList：'all' 哨兵不下发，search trim 空不下发（服务端分页参数）", async () => {
    listProducts.mockResolvedValue(pageOf([row()], 23))
    const store = useProductsStore()
    store.filterStatus = 'all'
    store.search = '  '
    await store.fetchList()
    expect(listProducts).toHaveBeenCalledWith({
      page: 1,
      pageSize: 10,
      status: undefined,
      categoryId: undefined,
      search: undefined,
    })
    expect(store.totalElements).toBe(23)
  })

  it('applyFilters 重置页码到 1', async () => {
    listProducts.mockResolvedValue(pageOf([]))
    const store = useProductsStore()
    store.page = 5
    await store.applyFilters()
    expect(store.page).toBe(1)
  })

  it('toggleStatus 乐观更新失败回滚（FORM-CAT-A02）', async () => {
    const store = useProductsStore()
    const r = row()
    store.list = [r]
    toggleProductStatus.mockRejectedValue(new Error('boom'))
    await expect(store.toggleStatus(r, 'published')).rejects.toThrow('boom')
    expect(r.status).toBe('draft') // 回滚
  })

  it('toggleStatus 同态幂等：不再发请求', async () => {
    const store = useProductsStore()
    const r = row()
    await store.toggleStatus(r, 'draft')
    expect(toggleProductStatus).not.toHaveBeenCalled()
  })

  it('patchFlags 失败回滚局部字段', async () => {
    const store = useProductsStore()
    const r = row()
    patchProductFlags.mockRejectedValue(new Error('x'))
    await expect(store.patchFlags(r, { isNew: true })).rejects.toThrow()
    expect(r.isNew).toBe(false)
  })

  it('batchOperate 透传 action/ids 并返回 successIds/failures（FORM-CAT-P01/ALIGN-007）', async () => {
    const result = {
      successIds: [1, 3],
      failures: [{ id: 2, errorCode: 409509, message: '已发布商品需先下架' }],
    }
    batchProducts.mockResolvedValue(result)
    const store = useProductsStore()
    const resp = await store.batchOperate('delete', [1, 2, 3])
    expect(batchProducts).toHaveBeenCalledWith('delete', [1, 2, 3])
    expect(resp.successIds).toEqual([1, 3])
    expect(resp.failures[0].errorCode).toBe(409509)
  })

  it('batchOperate 逐条容错：行级失败不抛错（部分失败仍 resolve）', async () => {
    batchProducts.mockResolvedValue({
      successIds: [],
      failures: [{ id: 1, errorCode: 500500, message: '内部错误' }],
    })
    const store = useProductsStore()
    await expect(store.batchOperate('publish', [1])).resolves.toMatchObject({ successIds: [] })
  })

  it('exportCsv 仅服务端筛选口径（status/categoryId/search；all 哨兵与空 search 归一 undefined）', async () => {
    exportProducts.mockResolvedValue({ truncated: false })
    const store = useProductsStore()
    store.filterStatus = 'all'
    store.filterCategoryId = 'all'
    store.search = '  '
    const res = await store.exportCsv()
    expect(res.truncated).toBe(false)
    expect(exportProducts).toHaveBeenCalledWith({
      status: undefined,
      categoryId: undefined,
      search: undefined,
    })
  })

  it('exportCsv 带筛选条件透传 + 截断标记透传（FORM-CAT-P02/ALIGN-007）', async () => {
    exportProducts.mockResolvedValue({ truncated: true })
    const store = useProductsStore()
    store.filterStatus = 'published'
    store.filterCategoryId = 7
    store.search = ' Grace '
    const res = await store.exportCsv()
    expect(exportProducts).toHaveBeenCalledWith({
      status: 'published',
      categoryId: 7,
      search: 'Grace',
    })
    expect(res.truncated).toBe(true)
  })

  it('exportCsv exporting 防重复提交：进行中再次调用不发请求', async () => {
    let resolveExport!: (v: { truncated: boolean }) => void
    exportProducts.mockReturnValue(new Promise((r) => { resolveExport = r }))
    const store = useProductsStore()
    const first = store.exportCsv()
    const second = await store.exportCsv() // 进行中 → 直接返回 { truncated: false }
    expect(second.truncated).toBe(false)
    expect(exportProducts).toHaveBeenCalledTimes(1)
    resolveExport({ truncated: false })
    await first
    expect(store.exporting).toBe(false)
  })
})
