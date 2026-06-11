/**
 * portal-store API 客户端（STORE-S03）。
 * - 自动附加 Authorization: Bearer <access> + Accept-Language（en/es/fr）。
 * - 请求体 camelCase → snake_case；响应体 snake_case → camelCase（lib/api/case）。
 * - access 过期或 401 → 用 refresh 续期后自动重放一次；refresh 失效 → 抛 ApiError(40102) 并清 token。
 * - 错误统一抛 ApiError（含 code/message/details），页面按 code 本地化（lib/i18n）。
 */

import { deepCamelize, deepSnakeize } from './case'
import type { ApiErrorBody, Locale, TokenPair } from './types'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  isAccessExpired,
  saveTokens
} from './token-store'
import { getActiveLocale } from '../i18n/i18n-context'

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') ?? 'http://localhost:8080'

export class ApiError extends Error {
  readonly code: number
  readonly details?: Record<string, unknown> | null
  readonly httpStatus: number

  constructor(code: number, message: string, httpStatus: number, details?: Record<string, unknown> | null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.details = details ?? null
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  body?: unknown
  auth?: boolean
  locale?: Locale
  retryOnUnauthorized?: boolean
  /**
   * showroom guest JWT 注入（showroom-frontend B.1 最小 diff）：提供时 Authorization 直用该值，
   * 且跳过 401→refresh 续期重放（guest token 无 refresh 概念，401101 直接抛 ApiError 由 guest 流程处理）。
   */
  authTokenOverride?: string
  /** query 参数（值为 undefined/null/'' 时忽略；key 自动 camel→snake） */
  query?: Record<string, string | number | boolean | undefined | null>
}

function camelKeyToSnake(key: string): string {
  return key.replace(/([A-Z])/g, (m) => `_${m.toLowerCase()}`)
}

/** 拼接 query string（忽略空值，key 转 snake_case） */
export function buildQuery(query?: Record<string, string | number | boolean | undefined | null>): string {
  if (!query) return ''
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue
    sp.set(camelKeyToSnake(k), String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

// 续期单飞：并发请求共享同一次 refresh
let refreshInflight: Promise<TokenPair> | null = null

function buildHeaders(opts: RequestOptions): Headers {
  const headers = new Headers()
  headers.set('Accept', 'application/json')
  headers.set('Accept-Language', opts.locale ?? getActiveLocale())
  if (opts.body !== undefined) headers.set('Content-Type', 'application/json')
  if (opts.authTokenOverride) {
    headers.set('Authorization', `Bearer ${opts.authTokenOverride}`)
  } else if (opts.auth) {
    const token = getAccessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
  }
  return headers
}

async function parseError(res: Response): Promise<ApiError> {
  let body: ApiErrorBody | null = null
  try {
    const raw = await res.json()
    body = deepCamelize<ApiErrorBody>(raw)
  } catch {
    body = null
  }
  const code = body?.code ?? mapHttpToCode(res.status)
  const message = body?.message ?? 'Request failed'
  return new ApiError(code, message, res.status, body?.data as Record<string, unknown> | null)
}

function mapHttpToCode(status: number): number {
  switch (status) {
    case 401:
      return 40100
    case 403:
      return 40300
    case 404:
      return 40400
    case 409:
      return 40901
    case 410:
      return 41001
    case 429:
      return 42902
    case 502:
      return 50201
    case 504:
      return 50401
    default:
      return status >= 500 ? 50000 : 40000
  }
}

/** 用 refresh token 续期（FLOW-04）；失败清 token 并抛 40102。 */
export async function refreshTokens(): Promise<TokenPair> {
  if (refreshInflight) return refreshInflight
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    clearTokens()
    throw new ApiError(40102, 'Session expired', 401, null)
  }
  refreshInflight = (async () => {
    const res = await fetch(`${API_BASE}/api/store/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(deepSnakeize({ refreshToken }))
    })
    if (!res.ok) {
      clearTokens()
      throw await parseError(res)
    }
    const json = deepCamelize<{ code: number; data: { tokens: TokenPair } }>(await res.json())
    saveTokens(json.data.tokens)
    return json.data.tokens
  })()
  try {
    return await refreshInflight
  } finally {
    refreshInflight = null
  }
}

/** 核心请求方法 */
export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = false, retryOnUnauthorized = true, authTokenOverride, query } = opts

  // 前置续期：access 已过期且有 refresh 时先续期，减少一次 401 往返（STORE-S03）
  // guest token 注入时跳过（无 refresh 概念）
  if (!authTokenOverride && auth && isAccessExpired() && getRefreshToken()) {
    try {
      await refreshTokens()
    } catch {
      /* 续期失败留待下方 401 分支统一处理 */
    }
  }

  const doFetch = async (): Promise<Response> =>
    fetch(`${API_BASE}${path}${buildQuery(query)}`, {
      method,
      headers: buildHeaders(opts),
      body: body !== undefined ? JSON.stringify(deepSnakeize(body)) : undefined
    })

  let res = await doFetch()

  // 401 → 续期重放一次（仅鉴权请求；guest token 注入时直抛由 guest 流程处理 401101）
  if (res.status === 401 && auth && retryOnUnauthorized && !authTokenOverride && getRefreshToken()) {
    try {
      await refreshTokens()
      res = await doFetch()
    } catch (err) {
      clearTokens()
      throw err instanceof ApiError ? err : new ApiError(40102, 'Session expired', 401, null)
    }
  }

  if (res.status === 204 || res.headers.get('content-length') === '0') {
    if (!res.ok) throw await parseError(res)
    return undefined as T
  }

  if (!res.ok) throw await parseError(res)

  const json = await res.json()
  const camelized = deepCamelize<{ code: number; data: T; message?: string }>(json)
  return camelized.data
}

export { API_BASE }
