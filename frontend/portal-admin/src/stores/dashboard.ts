// STORE-ANA-01 useDashboardStore：E-ANA-01 主体 + E-ANA-02 donut（Promise.allSettled 互不阻塞；
// overview 403 → donutState=forbidden——DEC-ANA-FE-4 ①；趋势 14/30 客户端切片——DEC-ANA-FE-5）
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { analyticsApi } from '@/api'
import { BizError } from '@/api/client'
import type { CategorySalesItem, DashboardResponse } from '@/api/types'
import { OrderStatus } from '@/api/types'

export interface TodoTile {
  label: string
  count: number
  tone: 'danger' | 'warn' | 'info'
  to: string
}

export const useDashboardStore = defineStore('dashboard', () => {
  const dashboard = ref<DashboardResponse | null>(null)
  const categorySales = ref<CategorySalesItem[]>([])
  const loading = ref(false)
  const pageError = ref(false)
  const donutState = ref<'ok' | 'forbidden' | 'error'>('ok')
  const trendWindow = ref<14 | 30>(14)

  async function fetch() {
    loading.value = true
    pageError.value = false
    try {
      const [dash, overview] = await Promise.allSettled([
        analyticsApi.getDashboard(),
        analyticsApi.getAnalyticsOverview('30d'),
      ])
      if (dash.status === 'fulfilled') {
        dashboard.value = dash.value
      } else {
        pageError.value = true
      }
      if (overview.status === 'fulfilled') {
        categorySales.value = overview.value.categorySales
        donutState.value = 'ok'
      } else {
        const e = overview.reason
        donutState.value = e instanceof BizError && e.code === 40300 ? 'forbidden' : 'error'
      }
    } finally {
      loading.value = false
    }
  }

  /** DEC-ANA-FE-5：14 天=30 桶尾 14 切片，无二次请求 */
  const trendSlice = computed(() => {
    const trend = dashboard.value?.gmvTrend
    if (!trend) return { labels: [] as string[], values: [] as number[] }
    if (trendWindow.value === 30) return { labels: trend.labels, values: trend.values }
    return { labels: trend.labels.slice(-14), values: trend.values.slice(-14) }
  })

  /** DEC-ANA-FE-3：三瓦片（unshipped→danger、refund→danger、review→info） */
  const todoTiles = computed<TodoTile[]>(() => {
    const todos = dashboard.value?.todos
    if (!todos) return []
    return [
      { label: '待发货订单', count: todos.unshippedOrderCount, tone: 'danger', to: `/orders?status=${OrderStatus.PAID}` },
      { label: '退款待审批', count: todos.pendingRefundCount, tone: 'danger', to: '/refunds' },
      { label: '待审核评价', count: todos.pendingReviewCount, tone: 'info', to: '/reviews' },
    ]
  })

  return { dashboard, categorySales, loading, pageError, donutState, trendWindow, fetch, trendSlice, todoTiles }
})
