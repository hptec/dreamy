// STORE-ANA-01 useDashboardStore 单测（unit_task_ana_001 / admin-prototype-alignment）
// ALIGN-011 豁免断言（DEC-ANA-FE-3）：待办瓦片固定 3 项、点击直达带 query 的列表页（store 级可测部分）
// ALIGN-009/010 为组件模板级断言 → deferred-to-L3-test
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const getDashboard = vi.fn()
const getAnalyticsOverview = vi.fn()

vi.mock('@/api', () => ({
  analyticsApi: {
    getDashboard: (...args: unknown[]) => getDashboard(...args),
    getAnalyticsOverview: (...args: unknown[]) => getAnalyticsOverview(...args),
  },
}))

import { useDashboardStore } from '@/stores/dashboard'
import type { AnalyticsOverviewResponse, DashboardResponse } from '@/api/types'

const dashboardPayload = (): DashboardResponse => ({
  kpis: { gmvMonth: 12345.67, orderCount: 89, avgOrderValue: 138.71, refundRate: 1.2 },
  todos: { unshippedOrderCount: 5, pendingRefundCount: 2, pendingReviewCount: 7 },
  gmvTrend: { labels: ['05-01', '05-02'], values: [100, 200] },
})

const overviewPayload = (): AnalyticsOverviewResponse => ({
  kpis: { gmvMonth: 12345.67, orderCount: 89, avgOrderValue: 138.71, refundRate: 1.2 },
  gmvTrend: { labels: [], values: [] },
  categorySales: [],
  topProducts: [],
})

describe('useDashboardStore（ALIGN-011 豁免断言 / DEC-ANA-FE-3）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('todoTiles 固定 3 瓦片，计数取自 E-ANA-01 todos（ALIGN-011 verified_exempt）', async () => {
    getDashboard.mockResolvedValue(dashboardPayload())
    getAnalyticsOverview.mockResolvedValue(overviewPayload())
    const store = useDashboardStore()
    await store.fetch()
    expect(store.todoTiles).toHaveLength(3)
    expect(store.todoTiles.map((t) => t.label)).toEqual(['待发货订单', '退款待审批', '待审核评价'])
    expect(store.todoTiles.map((t) => t.count)).toEqual([5, 2, 7])
  })

  it('瓦片点击直达带 query 的列表页（ALIGN-011 豁免断言第 2 条）', async () => {
    getDashboard.mockResolvedValue(dashboardPayload())
    getAnalyticsOverview.mockResolvedValue(overviewPayload())
    const store = useDashboardStore()
    await store.fetch()
    expect(store.todoTiles[0].to).toBe('/orders?status=2')
    expect(store.todoTiles[1].to).toBe('/refunds')
    expect(store.todoTiles[2].to).toBe('/reviews')
  })

  it('dashboard 未加载时 todoTiles 为空数组（空态基线，不渲染假数据）', () => {
    const store = useDashboardStore()
    expect(store.todoTiles).toEqual([])
  })
})
