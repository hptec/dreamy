// STORE-TRD-A01 useOrdersStore 单测（admin-prototype-alignment）：
// ALIGN-013 country/itemCount 透传 + ALIGN-012 导出参数与列表筛选一致/截断标记/防重复
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listOrders = vi.fn()
const exportOrders = vi.fn()

vi.mock('@/api', () => ({
  ordersApi: {
    listOrders: (...args: unknown[]) => listOrders(...args),
    exportOrders: (...args: unknown[]) => exportOrders(...args),
    getOrder: vi.fn(),
    shipOrder: vi.fn(),
    patchOrderStatus: vi.fn(),
    createRefund: vi.fn(),
  },
}))

import { useOrdersStore } from '@/stores/orders'

describe('useOrdersStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList 透传 country/itemCount（ALIGN-013，API-TRD-01）', async () => {
    listOrders.mockResolvedValue({
      data: [
        {
          id: 1,
          orderNo: 'DRM-1',
          status: 'paid',
          currency: 'USD',
          totalAmount: 100,
          country: 'United States',
          itemCount: 3,
        },
      ],
      totalElements: 1,
    })
    const store = useOrdersStore()
    await store.fetchList()
    expect(store.list[0]!.country).toBe('United States')
    expect(store.list[0]!.itemCount).toBe(3)
  })

  it('exportList 筛选参数与列表完全一致且不含分页（FORM-TRD-O01/ALIGN-012）', async () => {
    exportOrders.mockResolvedValue({ truncated: false })
    const store = useOrdersStore()
    store.status = 'paid'
    store.search = '  Grace  '
    store.currency = 'USD'
    store.from = '2026-06-01'
    store.to = '2026-06-10'
    const res = await store.exportList()
    expect(res.truncated).toBe(false)
    expect(exportOrders).toHaveBeenCalledWith({
      status: 'paid',
      search: 'Grace',
      currency: 'USD',
      from: '2026-06-01T00:00:00',
      to: '2026-06-10T23:59:59',
    })
  })

  it('exportList all/空筛选归一为 undefined（与 fetchList normalizeFilter 语义一致）', async () => {
    exportOrders.mockResolvedValue({ truncated: false })
    const store = useOrdersStore()
    await store.exportList()
    expect(exportOrders).toHaveBeenCalledWith({
      status: undefined,
      search: undefined,
      currency: undefined,
      from: undefined,
      to: undefined,
    })
  })

  it('exportList 截断标记透传（X-Export-Truncated → 视图 toast.warn 数据源）', async () => {
    exportOrders.mockResolvedValue({ truncated: true })
    const store = useOrdersStore()
    const res = await store.exportList()
    expect(res.truncated).toBe(true)
  })

  it('exportList exporting 防重复（loading 期间二次调用不发请求）', async () => {
    let resolveFirst!: (v: { truncated: boolean }) => void
    exportOrders.mockReturnValue(
      new Promise((r) => {
        resolveFirst = r
      }),
    )
    const store = useOrdersStore()
    const first = store.exportList()
    expect(store.exporting).toBe(true)
    const second = await store.exportList()
    expect(second.truncated).toBe(false)
    expect(exportOrders).toHaveBeenCalledTimes(1)
    resolveFirst({ truncated: true })
    await expect(first).resolves.toEqual({ truncated: true })
    expect(store.exporting).toBe(false)
  })
})
