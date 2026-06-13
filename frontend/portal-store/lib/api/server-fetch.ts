/**
 * RSC 服务端取数（决策 22 Node standalone）。
 * - 独立于 lib/api/client.ts（client.ts 依赖浏览器态 token/locale，仅供客户端组件）。
 * - 失败容错：任何网络/HTTP 错误返回 null（首页/列表冷启动安全回退静态内容，不挂构建）。
 * - 缓存：一律 no-store，每次直连后端读取最新值（不启用 ISR/fetch 缓存）。
 */

import { deepCamelize } from './case'

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') ?? 'http://localhost:8080'

export interface ServerGetOptions {
  locale?: string
  query?: Record<string, string | number | boolean | string[] | undefined | null>
}

function camelKeyToSnake(key: string): string {
  return key.replace(/([A-Z])/g, (m) => `_${m.toLowerCase()}`)
}

function buildQuery(query?: ServerGetOptions['query']): string {
  if (!query) return ''
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue
    if (Array.isArray(v)) {
      // 重复参数（?attr=a:b&attr=c:d——动态属性筛选口径）
      for (const item of v) {
        if (item !== '') sp.append(camelKeyToSnake(k), item)
      }
      continue
    }
    sp.set(camelKeyToSnake(k), String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

export interface ServerResult<T> {
  data: T | null
  status: number
}

/**
 * 构建阶段跳过取数（冷启动安全）：
 * - pnpm build 不依赖后端存活（CI/沙箱可构建）；预渲染产物为回退内容。
 * - 运行时（next start）NEXT_PHASE 为空 → 真实取数直连后端。
 * - 同时规避 Next 15.0.x 构建期 fetch 失败的去重缓存悬挂问题。
 */
function isBuildPhase(): boolean {
  return process.env.NEXT_PHASE === 'phase-production-build'
}

/** 带 HTTP 状态返回（404 区分 notFound 与一般失败） */
export async function serverGetWithStatus<T>(
  path: string,
  opts: ServerGetOptions = {}
): Promise<ServerResult<T>> {
  const { locale = 'en', query } = opts
  if (isBuildPhase()) return { data: null, status: 0 }
  try {
    const res = await fetch(`${API_BASE}${path}${buildQuery(query)}`, {
      headers: { Accept: 'application/json', 'Accept-Language': locale },
      signal: AbortSignal.timeout(10_000),
      cache: 'no-store'
    })
    if (!res.ok) return { data: null, status: res.status }
    const json = await res.json()
    const body = deepCamelize<{ code: number; data: T }>(json)
    return { data: body.data ?? null, status: res.status }
  } catch {
    return { data: null, status: 0 }
  }
}

/** 容错取数：失败一律 null（冷启动安全） */
export async function serverGet<T>(path: string, opts: ServerGetOptions = {}): Promise<T | null> {
  const { data } = await serverGetWithStatus<T>(path, opts)
  return data
}
