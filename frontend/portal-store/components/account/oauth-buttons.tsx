'use client'

/**
 * COMP-S04 <OAuthButtons>：Google/Apple 登录入口。
 * - 按 authConfig 开关显隐（FUNC-003/006）。
 * - 点击走 OIDC 流（FORM-S03）；502/504 → 提示改用 OTP（DG-001，由父级 catch 显错）。
 */

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { GoogleIcon, AppleIcon } from './provider-icons'
import { useI18n } from '@/lib/i18n/i18n-context'
import { useAuthStore } from '@/lib/stores/auth-store'
import { oidcCallback } from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { beginOidc, isOidcStubMode, stubIdToken, type OidcProvider } from '@/lib/auth/oauth'

interface OAuthButtonsProps {
  googleEnabled: boolean
  appleEnabled: boolean
  email: string
  onError: (err: ApiError) => void
}

export function OAuthButtons({ googleEnabled, appleEnabled, email, onError }: OAuthButtonsProps) {
  const { t } = useI18n()
  const router = useRouter()
  const login = useAuthStore((s) => s.login)
  const [pending, setPending] = useState<OidcProvider | null>(null)

  if (!googleEnabled && !appleEnabled) return null

  async function handleOidc(provider: OidcProvider) {
    setPending(provider)
    try {
      if (isOidcStubMode(provider)) {
        // stub 联调：用占位 id_token 直接换取会话
        const safeEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
          ? email.trim().toLowerCase()
          : `${provider}-user@email.com`
        const result = await oidcCallback(provider, stubIdToken(provider, safeEmail))
        login(result)
        router.push('/account')
      } else {
        // 真实模式：跳转 provider 授权页，回调由登录页 useEffect 处理
        beginOidc(provider)
      }
    } catch (err) {
      if (err instanceof ApiError) onError(err)
    } finally {
      setPending(null)
    }
  }

  return (
    <div className="mt-7 space-y-3">
      {googleEnabled && (
        <button
          type="button"
          onClick={() => handleOidc('google')}
          disabled={pending !== null}
          className="flex w-full items-center justify-center gap-3 rounded-sm border border-line bg-surface py-3 text-sm font-medium transition-colors hover:border-ink disabled:cursor-not-allowed disabled:opacity-60"
        >
          <GoogleIcon /> {t.login.continueWithGoogle}
        </button>
      )}
      {appleEnabled && (
        <button
          type="button"
          onClick={() => handleOidc('apple')}
          disabled={pending !== null}
          className="flex w-full items-center justify-center gap-3 rounded-sm border border-line bg-surface py-3 text-sm font-medium transition-colors hover:border-ink disabled:cursor-not-allowed disabled:opacity-60"
        >
          <AppleIcon /> {t.login.continueWithApple}
        </button>
      )}
    </div>
  )
}
