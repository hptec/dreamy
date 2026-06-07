'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { GoogleIcon, AppleIcon } from './provider-icons'
import { useI18n } from '@/lib/i18n/i18n-context'
import { useAuthStore } from '@/lib/stores/auth-store'
import { oidcCallback } from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { beginOidc, type OidcProvider } from '@/lib/auth/oauth'
import { useAuthConfigStore } from '@/lib/stores/auth-config-store'

interface OAuthButtonsProps {
  googleEnabled: boolean
  appleEnabled: boolean
  onError: (err: ApiError) => void
}

export function OAuthButtons({ googleEnabled, appleEnabled, onError }: OAuthButtonsProps) {
  const { t } = useI18n()
  const router = useRouter()
  const login = useAuthStore((s) => s.login)
  const config = useAuthConfigStore((s) => s.config)
  const [pending, setPending] = useState<OidcProvider | null>(null)

  const googleClientId = config?.googleClientId
  const appleServiceId = config?.appleServiceId

  const showGoogle = googleEnabled && !!googleClientId
  const showApple = appleEnabled && !!appleServiceId

  if (!showGoogle && !showApple) return null

  async function handleOidc(provider: OidcProvider) {
    const clientId = provider === 'google' ? googleClientId : appleServiceId
    if (!clientId) return
    setPending(provider)
    try {
      beginOidc(provider, clientId)
      // 真实模式：跳转 provider 授权页，回调由登录页 useEffect 处理，不在此处 await
    } catch (err) {
      if (err instanceof ApiError) onError(err)
      setPending(null)
    }
  }

  return (
    <>
      <div className="mt-7 space-y-3">
        {showGoogle && (
          <button
            type="button"
            onClick={() => handleOidc('google')}
            disabled={pending !== null}
            className="flex w-full items-center justify-center gap-3 rounded-sm border border-line bg-surface py-3 text-sm font-medium transition-colors hover:border-ink disabled:cursor-not-allowed disabled:opacity-60"
          >
            <GoogleIcon /> {t.login.continueWithGoogle}
          </button>
        )}
        {showApple && (
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
      <div className="my-6 flex items-center gap-4">
        <span className="h-px flex-1 bg-line" />
        <span className="text-[11px] uppercase tracking-luxe text-ink-faint">{t.login.or}</span>
        <span className="h-px flex-1 bg-line" />
      </div>
      {showApple && (
        <p className="mt-6 text-center text-xs text-ink-faint">{t.login.appleRelayNote}</p>
      )}
    </>
  )
}
