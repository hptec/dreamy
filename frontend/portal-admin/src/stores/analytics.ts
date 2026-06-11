// STORE-ANA-02 useAnalyticsStore：E-ANA-02/03（range 固定 30d——DEC-ANA-FE-6；GA4 降级——DEC-ANA-FE-8）
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { analyticsApi } from '@/api'
import type { AnalyticsOverviewResponse, AnalyticsTrafficResponse, CategorySalesItem } from '@/api/types'

export interface DonutSegment extends CategorySalesItem {
  start: number
  end: number
}

export const useAnalyticsStore = defineStore('analytics', () => {
  const overview = ref<AnalyticsOverviewResponse | null>(null)
  const traffic = ref<AnalyticsTrafficResponse | null>(null)
  const loadingOverview = ref(false)
  const loadingTraffic = ref(false)
  const trafficFailed = ref(false)
  const range = '30d' // DEC-ANA-FE-6 常量

  async function fetchOverview() {
    if (overview.value || loadingOverview.value) return
    loadingOverview.value = true
    try {
      overview.value = await analyticsApi.getAnalyticsOverview(range)
    } finally {
      loadingOverview.value = false
    }
  }

  /** 流量/漏斗 tab 首次激活时懒加载；502001/504001 → trafficFailed 占位 */
  async function fetchTraffic(force = false) {
    if (!force && (traffic.value || loadingTraffic.value)) return
    loadingTraffic.value = true
    trafficFailed.value = false
    try {
      traffic.value = await analyticsApi.getAnalyticsTraffic(range)
    } catch {
      traffic.value = null
      trafficFailed.value = true
    } finally {
      loadingTraffic.value = false
    }
  }

  function retryTraffic() {
    return fetchTraffic(true)
  }

  const trafficUnavailable = computed(
    () => trafficFailed.value || traffic.value?.sourceStatus === 'unavailable',
  )

  /** funnel[0] 除零保护 */
  const maxFunnel = computed(() => {
    const first = traffic.value?.funnel?.[0]?.value ?? 0
    return first > 0 ? first : 1
  })

  /** donut arc 数据（复刻原型 arc 纯函数的输入段） */
  const donutSegments = computed<DonutSegment[]>(() => {
    let acc = 0
    return (overview.value?.categorySales ?? []).map((s) => {
      const start = acc
      acc += Number(s.share)
      return { ...s, start, end: acc }
    })
  })

  return {
    overview,
    traffic,
    loadingOverview,
    loadingTraffic,
    range,
    fetchOverview,
    fetchTraffic,
    retryTraffic,
    trafficUnavailable,
    maxFunnel,
    donutSegments,
  }
})
