// CSV 导出下载工具（FND-REUSE-001 去重：catalog.ts exportProducts / orders.ts exportOrders 同构逻辑收敛于此）
// 统一：fetch + Authorization Bearer、非 2xx 错误处理、Content-Disposition 文件名解析
//（兜底 `{prefix}-{yyyyMMdd}.csv`）、X-Export-Truncated 截断标记解析、blob 触发浏览器下载。
import { getToken } from '@/api/client'

export async function downloadCsv(
  path: string,
  query: URLSearchParams,
  fallbackFilenamePrefix: string,
): Promise<{ truncated: boolean }> {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const qs = query.toString()
  const url = `${base}${path}${qs ? `?${qs}` : ''}`
  const token = getToken()
  const res = await fetch(url, {
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
      'Accept-Language': 'zh',
    },
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message || '导出失败，请稍后重试')
  }
  const truncated = res.headers.get('X-Export-Truncated') === 'true'
  const disposition = res.headers.get('Content-Disposition') || ''
  const matched = /filename="?([^";]+)"?/.exec(disposition)
  const filename =
    matched?.[1] ||
    `${fallbackFilenamePrefix}-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`
  const blob = await res.blob()
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(objectUrl)
  return { truncated }
}
