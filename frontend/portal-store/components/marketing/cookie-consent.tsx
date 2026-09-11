'use client'

/**
 * CookieConsent（决策 19 连带约束——既有 CookieNotice 升级为 Consent Mode v2 版本，单 banner）：
 * - 无持久化选择 → 1.5s 后弹出（右下角小卡片样式，不再横贯首屏）；
 *   Accept/Decline 真分流并持久化 localStorage（dreamy_cookie_consent），
 *   选择完成派发 CONSENT_CHOSEN_EVENT 供 NewsletterModal 解除互斥。
 * - granted：初始化 gtag 命令队列（consent default denied → update granted → config）
 *   → next/script lazyOnload 注入 gtag.js → usePathname 监听上报 page_view（首屏 + 客户端导航）。
 * - denied/未选择：脚本本体不加载、事件全 no-op → 前端不发任何分析 Cookie（s-1042）。
 * - NEXT_PUBLIC_GA4_ID 未配置：banner 照常弹出（合规口径不变），分析链路全 no-op。
 */

import { useEffect, useState } from 'react'
import Script from 'next/script'
import { usePathname } from 'next/navigation'
import { useI18n } from '@/lib/i18n/i18n-context'
import {
  GA4_ID,
  getStoredConsent,
  storeConsent,
  initGtagQueue,
  trackPageView,
  type ConsentChoice
} from '@/lib/analytics/gtag'

/**
 * consent 选择完成事件（window CustomEvent）：
 * NewsletterModal 监听它实现与 cookie 条互斥——banner 展示期间不弹订阅弹窗，
 * 用户做出选择（banner 关闭）后才装订 15s/滚动 40% 触发器。
 */
export const CONSENT_CHOSEN_EVENT = 'dreamy:consent-chosen'

export function CookieConsent() {
  const { t } = useI18n()
  const [consent, setConsent] = useState<ConsentChoice | null>(null)
  const [show, setShow] = useState(false)
  /** gtag 命令队列已初始化（granted 时一次；脚本注入与 page_view 上报的前置门） */
  const [queued, setQueued] = useState(false)
  const pathname = usePathname()

  useEffect(() => {
    const stored = getStoredConsent()
    if (stored) {
      setConsent(stored)
      return
    }
    const t = setTimeout(() => setShow(true), 1500)
    return () => clearTimeout(t)
  }, [])

  // granted → 先压 consent/config 命令队列，再放行脚本注入（Consent Mode v2 时序）
  useEffect(() => {
    if (consent === 'granted' && !queued) {
      initGtagQueue()
      setQueued(true)
    }
  }, [consent, queued])

  // 路由 page_view 自动上报（queued 翻转时补首屏一次，此后随 pathname 变化）
  useEffect(() => {
    if (queued) trackPageView(pathname)
  }, [queued, pathname])

  const choose = (choice: ConsentChoice) => {
    storeConsent(choice)
    setConsent(choice)
    setShow(false)
    // 通知 NewsletterModal：banner 已关闭，可以装订订阅弹窗触发器（互斥解除）
    window.dispatchEvent(new Event(CONSENT_CHOSEN_EVENT))
  }

  return (
    <>
      {/* 脚本本体仅 granted 且队列就绪后加载——denied 路径零网络请求零 Cookie（s-1042） */}
      {consent === 'granted' && queued && GA4_ID && (
        <Script
          src={`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(GA4_ID)}`}
          strategy="lazyOnload"
        />
      )}

      {show && (
        <div className="fixed bottom-4 right-4 z-50 w-[calc(100%-2rem)] max-w-sm animate-fadeup rounded-sm border border-line bg-surface p-5 shadow-lift">
          <p className="text-sm text-ink-soft">
            {t.cookieConsent.body}
          </p>
          <div className="mt-4 flex gap-2">
            <button
              onClick={() => choose('granted')}
              className="flex-1 cursor-pointer rounded-sm bg-ink px-5 py-2 text-xs font-medium uppercase tracking-luxe text-canvas transition-colors hover:bg-gold-deep"
            >
              {t.cookieConsent.accept}
            </button>
            <button
              onClick={() => choose('denied')}
              className="flex-1 cursor-pointer rounded-sm border border-line px-5 py-2 text-xs font-medium uppercase tracking-luxe transition-colors hover:border-ink"
            >
              {t.cookieConsent.decline}
            </button>
          </div>
        </div>
      )}
    </>
  )
}
