/**
 * RSC 服务端取数（ISR 策略承载，决策 22 Node standalone）。
 * - 独立于 lib/api/client.ts（client.ts 依赖浏览器态 token/locale，仅供客户端组件）。
 * - 失败容错：任何网络/HTTP 错误返回 null（首页/列表冷启动安全回退静态内容，不挂构建）。
 * - 缓存：next.revalidate 由调用方传入（TTL 兜底）；秒级失效靠 POST /api/revalidate on-demand（FLOW-P03/s-758）。
 */

import { deepCamelize } from './case'

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') ?? 'http://localhost:8080'

export interface ServerGetOptions {
  /** ISR TTL 秒；0 = no-store（协作/个人数据） */
  revalidate?: number
  locale?: string
  query?: Record<string, string | number | boolean | undefined | null>
}

function camelKeyToSnake(key: string): string {
  return key.replace(/([A-Z])/g, (m) => `_${m.toLowerCase()}`)
}

function buildQuery(query?: ServerGetOptions['query']): string {
  if (!query) return ''
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue
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
 * - 运行时（next start）NEXT_PHASE 为空 → 真实取数 + ISR TTL/on-demand revalidate 即时补水。
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
  const { revalidate = 300, locale = 'en', query } = opts
  if (isBuildPhase()) return { data: null, status: 0 }
  try {
    const res = await fetch(`${API_BASE}${path}${buildQuery(query)}`, {
      headers: { Accept: 'application/json', 'Accept-Language': locale },
      // 上游悬挂保护：10s 硬超时（连接拒绝即时失败；慢响应走 CDN serve-stale 兜底）
      signal: AbortSignal.timeout(10_000),
      ...(revalidate === 0 ? { cache: 'no-store' as const } : { next: { revalidate } })
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
