'use client'

/**
 * OIDC 登录入口（COMP-S04 / FORM-S03）。
 * - 真实模式：构造 provider 授权 URL 跳转，回调页取 id_token 调 oidcCallback。
 * - 后端默认 stub 模式（OIDC_MODE=stub）：直接以占位 id_token 触发 oidcCallback，
 *   走后端 stub 验证签发会话（保证端到端可联调，不发明后端不存在的接口）。
 * 配置来自 env（NEXT_PUBLIC_*），缺失时回退 stub。
 */

import type { AuthProvider } from '../api/types'

export type OidcProvider = Extract<AuthProvider, 'google' | 'apple'>

interface OidcConfig {
  clientId?: string
  authorizeUrl: string
  scope: string
}

const CONFIG: Record<OidcProvider, OidcConfig> = {
  google: {
    clientId: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID,
    authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
    scope: 'openid email profile'
  },
  apple: {
    clientId: process.env.NEXT_PUBLIC_APPLE_SERVICE_ID,
    authorizeUrl: 'https://appleid.apple.com/auth/authorize',
    scope: 'openid email name'
  }
}

function redirectUri(provider: OidcProvider): string {
  if (typeof window === 'undefined') return ''
  return `${window.location.origin}/account/login?oidc=${provider}`
}

function randomNonce(): string {
  const arr = new Uint8Array(16)
  if (typeof crypto !== 'undefined') crypto.getRandomValues(arr)
  return Array.from(arr, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** 是否为后端 stub 模式（无 OAuth client 配置即视为 stub 联调态） */
export function isOidcStubMode(provider: OidcProvider): boolean {
  return !CONFIG[provider].clientId
}

/** 启动 OIDC 授权跳转（真实模式）；返回所用 nonce 以便回调校验。 */
export function beginOidc(provider: OidcProvider): string {
  const cfg = CONFIG[provider]
  const nonce = randomNonce()
  if (typeof window !== 'undefined') {
    try {
      sessionStorage.setItem(`oidc_nonce_${provider}`, nonce)
    } catch {
      /* ignore */
    }
  }
  if (cfg.clientId) {
    const params = new URLSearchParams({
      client_id: cfg.clientId,
      redirect_uri: redirectUri(provider),
      response_type: provider === 'apple' ? 'code id_token' : 'token id_token',
      response_mode: 'fragment',
      scope: cfg.scope,
      nonce
    })
    window.location.href = `${cfg.authorizeUrl}?${params.toString()}`
  }
  return nonce
}

/** stub 模式占位 id_token（后端 stub 验证器接受，签发会话用于联调）。 */
export function stubIdToken(provider: OidcProvider, email: string): string {
  return `stub.${provider}.${email}`
}

export function readNonce(provider: OidcProvider): string | undefined {
  if (typeof window === 'undefined') return undefined
  try {
    return sessionStorage.getItem(`oidc_nonce_${provider}`) ?? undefined
  } catch {
    return undefined
  }
}
