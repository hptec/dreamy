/**
 * Token 持久化（STORE-S03）。
 * 设计：access token 仅内存（防 XSS 长期窃取）；refresh token 持久化到 localStorage
 * （后端以 JSON body 下发 token，非 httpOnly cookie 模式，故采用 secure storage 持久化）。
 * access 2h / refresh 30d 滑动续期（shared-contracts jwt_isolation.store）。
 */

import type { TokenPair } from './types'

const ACCESS_KEY = 'dreamy_store_access'
const ACCESS_EXP_KEY = 'dreamy_store_access_exp'
const REFRESH_KEY = 'dreamy_store_refresh'
const REFRESH_EXP_KEY = 'dreamy_store_refresh_exp'

// access token 内存态（页面刷新后由 refresh 续期重建）
let accessToken: string | null = null
let accessExpiresAt: string | null = null

function isBrowser(): boolean {
  return typeof window !== 'undefined'
}

export function getAccessToken(): string | null {
  return accessToken
}

export function getAccessExpiresAt(): string | null {
  return accessExpiresAt
}

export function getRefreshToken(): string | null {
  if (!isBrowser()) return null
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

export function getRefreshExpiresAt(): string | null {
  if (!isBrowser()) return null
  try {
    return localStorage.getItem(REFRESH_EXP_KEY)
  } catch {
    return null
  }
}

/** 保存 TokenPair：access 入内存，refresh 入 localStorage */
export function saveTokens(tokens: TokenPair): void {
  accessToken = tokens.accessToken
  accessExpiresAt = tokens.accessExpiresAt
  if (!isBrowser()) return
  try {
    localStorage.setItem(ACCESS_KEY, tokens.accessToken)
    localStorage.setItem(ACCESS_EXP_KEY, tokens.accessExpiresAt)
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    localStorage.setItem(REFRESH_EXP_KEY, tokens.refreshExpiresAt)
  } catch {
    /* storage 不可用时退化为纯内存态 */
  }
}

/** 应用启动时从 localStorage 回填内存态 access（配合 refresh 续期） */
export function hydrateFromStorage(): void {
  if (!isBrowser()) return
  try {
    accessToken = localStorage.getItem(ACCESS_KEY)
    accessExpiresAt = localStorage.getItem(ACCESS_EXP_KEY)
  } catch {
    accessToken = null
    accessExpiresAt = null
  }
}

/** 清空全部 token（登出 / refresh 失效 / 注销账户） */
export function clearTokens(): void {
  accessToken = null
  accessExpiresAt = null
  if (!isBrowser()) return
  try {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(ACCESS_EXP_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(REFRESH_EXP_KEY)
  } catch {
    /* ignore */
  }
}

/** access token 是否已过期（含 30s 时钟偏移缓冲） */
export function isAccessExpired(): boolean {
  if (!accessToken || !accessExpiresAt) return true
  const exp = new Date(accessExpiresAt).getTime()
  if (Number.isNaN(exp)) return true
  return Date.now() >= exp - 30_000
}
