// analytics 域 API（PAGE-ANA-A01/A02；E-ANA-01~03；range 固定 30d——DEC-ANA-FE-6）
import { get } from './client'
import type { AnalyticsOverviewResponse, AnalyticsTrafficResponse, DashboardResponse } from './types'

export function getDashboard(): Promise<DashboardResponse> {
  return get<DashboardResponse>('/api/admin/dashboard')
}

export function getAnalyticsOverview(range = '30d'): Promise<AnalyticsOverviewResponse> {
  return get<AnalyticsOverviewResponse>('/api/admin/analytics/overview', { params: { range } })
}

export function getAnalyticsTraffic(range = '30d'): Promise<AnalyticsTrafficResponse> {
  return get<AnalyticsTrafficResponse>('/api/admin/analytics/traffic', { params: { range } })
}
