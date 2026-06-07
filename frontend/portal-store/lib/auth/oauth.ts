'use client'

import type { AuthProvider } from '../api/types'

export type OidcProvider = Extract<AuthProvider, 'google' | 'apple'>

const AUTHORIZE_URL: Record<OidcProvider, string> = {
  google: 'https://accounts.google.com/o/oauth2/v2/auth',
  apple: 'https://appleid.apple.com/auth/authorize',
}

const SCOPE: Record<OidcProvider, string> = {
  google: 'openid email profile',
  apple: 'openid email name',
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

/** 启动 OIDC 授权跳转；返回 nonce 供回调校验。 */
export function beginOidc(provider: OidcProvider, clientId: string): string {
  const nonce = randomNonce()
  if (typeof window !== 'undefined') {
    try { sessionStorage.setItem(`oidc_nonce_${provider}`, nonce) } catch { /* ignore */ }
    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri(provider),
      response_type: provider === 'apple' ? 'code id_token' : 'token id_token',
      response_mode: 'fragment',
      scope: SCOPE[provider],
      nonce,
    })
    window.location.href = `${AUTHORIZE_URL[provider]}?${params.toString()}`
  }
  return nonce
}

export function readNonce(provider: OidcProvider): string | undefined {
  if (typeof window === 'undefined') return undefined
  try { return sessionStorage.getItem(`oidc_nonce_${provider}`) ?? undefined } catch { return undefined }
}
