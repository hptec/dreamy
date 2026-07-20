'use client'

/**
 * /unsubscribe 退订确认（方案 A 邮件退订链接落地页）：
 * - 确认按钮触发 POST（不用 GET 直接退订，防邮件客户端链接预扫描误触发）。
 * - 挂载即 replaceState 抹掉 ?token=（防泄露到浏览器历史/分析/同域 referrer；token 已存内存）。
 * - 五态：confirm → confirming → success / invalid（token 无效/过期/代际落后 422704，终态）
 *   / error（网络或 5xx 等瞬时故障，可重试，token 保留内存）。
 */

import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { unsubscribeNewsletter } from '@/lib/api/marketing-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { Eyebrow } from '@/components/ui/primitives'

type State = 'confirm' | 'confirming' | 'success' | 'invalid' | 'error'

export function UnsubscribeConfirm() {
  const { t } = useI18n()
  const searchParams = useSearchParams()
  const tokenRef = useRef<string | null>(searchParams.get('token'))
  const [state, setState] = useState<State>(tokenRef.current ? 'confirm' : 'invalid')
  const copy = t.unsubscribe

  useEffect(() => {
    if (tokenRef.current) {
      window.history.replaceState(null, '', window.location.pathname)
    }
  }, [])

  const confirm = async () => {
    const token = tokenRef.current
    if (!token || state === 'confirming') return
    setState('confirming')
    try {
      await unsubscribeNewsletter(token)
      setState('success')
    } catch (err) {
      // 仅 422704 是无效链接终态；网络/5xx 等瞬时故障给可重试态（token 仍在内存）
      setState(err instanceof ApiError && err.code === 422704 ? 'invalid' : 'error')
    }
  }

  if (state === 'success') {
    return (
      <div className="max-w-lg text-center">
        <Eyebrow className="text-gold-light">Newsletter</Eyebrow>
        <h1 className="mt-3 font-display text-4xl font-medium">{copy.successTitle}</h1>
        <p className="mt-4 text-ink-soft">{copy.successBody}</p>
      </div>
    )
  }

  if (state === 'invalid') {
    return (
      <div className="max-w-lg text-center">
        <Eyebrow className="text-gold-light">Newsletter</Eyebrow>
        <h1 className="mt-3 font-display text-4xl font-medium">{copy.invalidTitle}</h1>
        <p className="mt-4 text-ink-soft">{copy.invalidBody}</p>
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div className="max-w-lg text-center">
        <Eyebrow className="text-gold-light">Newsletter</Eyebrow>
        <h1 className="mt-3 font-display text-4xl font-medium">{copy.errorTitle}</h1>
        <p className="mt-4 text-ink-soft">{copy.errorBody}</p>
        <button type="button" onClick={confirm} className="btn-primary mt-8">
          {copy.retry}
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-lg text-center">
      <Eyebrow className="text-gold-light">Newsletter</Eyebrow>
      <h1 className="mt-3 font-display text-4xl font-medium">{copy.title}</h1>
      <p className="mt-4 text-ink-soft">{copy.body}</p>
      <button
        type="button"
        onClick={confirm}
        disabled={state === 'confirming'}
        className="btn-primary mt-8 disabled:opacity-60"
      >
        {state === 'confirming' ? copy.confirming : copy.confirm}
      </button>
    </div>
  )
}
