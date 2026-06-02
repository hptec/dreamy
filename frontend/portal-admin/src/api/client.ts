// API 客户端（code-structure-spec §3：src/api/）
// 约束:
//  - shared-contracts: admin JWT 注入 Authorization、Accept-Language=zh、错误信封 {code,message,details}
//  - STORE-A03: token 持久化 localStorage；admin 8h 无 refresh，401→跳登录
//  - field_mapping_rule: 边界统一 snake_case(后端) ↔ camelCase(前端)，前端不直接消费 snake_case
import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import type { ApiError } from './types'

const TOKEN_KEY = 'dreamy_admin_token'

// ---- token 持久化（STORE-A03）----
export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}
export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* localStorage 不可用时静默降级（隐私模式） */
  }
}
export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* noop */
  }
}

// ---- snake_case ↔ camelCase 转换（field_mapping_rule，边界统一）----
function snakeToCamel(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_m, c: string) => c.toUpperCase())
}
function camelToSnake(key: string): string {
  return key.replace(/([A-Z])/g, (m) => '_' + m.toLowerCase())
}

function isPlainObject(val: unknown): val is Record<string, unknown> {
  return Object.prototype.toString.call(val) === '[object Object]'
}

export function keysToCamel(input: unknown): unknown {
  if (Array.isArray(input)) return input.map(keysToCamel)
  if (isPlainObject(input)) {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(input)) {
      out[snakeToCamel(k)] = keysToCamel(v)
    }
    return out
  }
  return input
}

export function keysToSnake(input: unknown): unknown {
  if (Array.isArray(input)) return input.map(keysToSnake)
  if (isPlainObject(input)) {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(input)) {
      out[camelToSnake(k)] = keysToSnake(v)
    }
    return out
  }
  return input
}

// ---- 401 处理回调（由 router/store 注册，避免循环依赖）----
type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null
export function registerUnauthorizedHandler(fn: UnauthorizedHandler): void {
  onUnauthorized = fn
}

// ---- 规范化错误（admin 直显中文 message；网络/超时给兜底中文）----
export class BizError extends Error {
  code: number
  details?: Record<string, unknown>
  constructor(code: number, message: string, details?: Record<string, unknown>) {
    super(message)
    this.name = 'BizError'
    this.code = code
    this.details = details
  }
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Accept-Language': 'zh' },
})

// 请求拦截：注入 admin JWT + body camel→snake
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  config.headers.set('Accept-Language', 'zh')
  if (config.data && !(config.data instanceof FormData)) {
    config.data = keysToSnake(config.data)
  }
  if (config.params) {
    config.params = keysToSnake(config.params)
  }
  return config
})

// 响应拦截：snake→camel；401→清 token+跳登录；统一抛 BizError（中文 message）
http.interceptors.response.use(
  (response) => {
    response.data = keysToCamel(response.data)
    return response
  },
  (error: AxiosError<ApiError>) => {
    if (error.response) {
      const status = error.response.status
      const body = error.response.data
      const code = body?.code ?? status * 100
      const message = body?.message || defaultMessage(status)
      const details = body?.details
      if (status === 401) {
        clearToken()
        if (onUnauthorized) onUnauthorized()
      }
      return Promise.reject(new BizError(code, message, details))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new BizError(50401, '请求超时，请稍后重试'))
    }
    return Promise.reject(new BizError(50000, '网络异常，请检查连接后重试'))
  },
)

function defaultMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求参数有误'
    case 401:
      return '登录已失效，请重新登录'
    case 403:
      return '无权限执行该操作'
    case 404:
      return '资源不存在'
    case 409:
      return '操作冲突'
    case 422:
      return '参数校验失败'
    default:
      return status >= 500 ? '服务器开小差了，请稍后重试' : '操作失败'
  }
}

// ---- 便捷方法（返回已 camel 化的 data）----
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.get<T>(url, config)
  return res.data
}
export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.post<T>(url, data, config)
  return res.data
}
export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.put<T>(url, data, config)
  return res.data
}
export async function patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.patch<T>(url, data, config)
  return res.data
}
export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.delete<T>(url, config)
  return res.data
}
