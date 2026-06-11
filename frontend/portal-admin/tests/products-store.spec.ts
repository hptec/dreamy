// STORE-CAT-A01 useProductsStore 单测：分页参数构造 + 行内乐观更新失败回滚
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listProducts = vi.fn()
const toggleProductStatus = vi.fn()
const patchProductFlags = vi.fn()

vi.mock('@/api', () => ({
  catalogApi: {
    listProducts: (...args: unknown[]) => listProducts(...args),
    toggleProductStatus: (...args: unknown[]) => toggleProductStatus(...args),
    patchProductFlags: (...args: unknown[]) => patchProductFlags(...args),
    deleteProduct: vi.fn(),
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
})
