'use client'

/**
 * PAGE-S01 /account/login — 消费者登录（OTP + Google/Apple OIDC）。
 * 原型骨架已替换为真实接口（COMP-S01 LoginCard）。
 * 真实 OIDC 回调（fragment 携带 id_token）由 useEffect 捕获 → oidcCallback → 存 token 跳 /account（FORM-S03）。
 */

import { useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { LoginCard, readReturnTo } from '@/components/account/login-card'
import { useAuthStore } from '@/lib/stores/auth-store'
import { oidcCallback } from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { readNonce, type OidcProvider } from '@/lib/auth/oauth'

function OidcCallbackHandler() {
  const router = useRouter()
  const login = useAuthStore((s) => s.login)
  const handled = useRef(false)

  useEffect(() => {
    if (handled.current || typeof window === 'undefined') return
    const url = new URL(window.location.href)
    const provider = url.searchParams.get('oidc') as OidcProvider | null
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ''))
    const idToken = hash.get('id_token')
    if (!provider || !idToken) return
    handled.current = true
    const nonce = readNonce(provider)
    oidcCallback(provider, idToken, nonce)
      .then((result) => {
        login(result)
        router.replace(readReturnTo())
      })
      .catch((err: unknown) => {
        // OIDC 失败（502/504/409）回到登录页，由 OTP 流兜底引导（DG-001）
        if (err instanceof ApiError) router.replace('/account/login')
      })
  }, [router, login])

  return null
}

export default function LoginPage() {
  return (
    <>
      <OidcCallbackHandler />
      <LoginCard />
    </>
  )
}
