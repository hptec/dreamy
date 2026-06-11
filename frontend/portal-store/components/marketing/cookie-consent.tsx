'use client'

/**
 * CookieConsent（决策 19 连带约束——既有 CookieNotice 升级为 Consent Mode v2 版本，单 banner）：
 * - 无持久化选择 → 1.5s 后弹出（沿用原 CookieNotice 节奏与视觉 token）；
 *   Accept/Decline 真分流并持久化 localStorage（dreamy_cookie_consent）。
 * - granted：初始化 gtag 命令队列（consent default denied → update granted → config）
 *   → next/script lazyOnload 注入 gtag.js → usePathname 监听上报 page_view（首屏 + 客户端导航）。
 * - denied/未选择：脚本本体不加载、事件全 no-op → 前端不发任何分析 Cookie（s-1042）。
 * - NEXT_PUBLIC_GA4_ID 未配置：banner 照常弹出（合规口径不变），分析链路全 no-op。
 */

import { useEffect, useState } from 'react'
import Script from 'next/script'
import { usePathname } from 'next/navigation'
import {
  GA4_ID,
  getStoredConsent,
  storeConsent,
  initGtagQueue,
  trackPageView,
  type ConsentChoice
} from '@/lib/analytics/gtag'

export function CookieConsent() {
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
        <div className="fixed bottom-4 left-4 right-4 z-50 mx-auto max-w-2xl animate-fadeup rounded-sm border border-line bg-surface p-5 shadow-lift sm:flex sm:items-center sm:gap-6">
          <p className="text-sm text-ink-soft">
            We use analytics cookies to understand how you shop and to show you the gowns you’ll love.
            Choose “Accept” to allow them, or “Decline” and we won’t set any analytics cookies.
          </p>
          <div className="mt-3 flex shrink-0 gap-2 sm:mt-0">
            <button
              onClick={() => choose('granted')}
              className="cursor-pointer rounded-sm bg-ink px-5 py-2 text-xs font-medium uppercase tracking-luxe text-canvas transition-colors hover:bg-gold-deep"
            >
              Accept
            </button>
            <button
              onClick={() => choose('denied')}
              className="cursor-pointer rounded-sm border border-line px-5 py-2 text-xs font-medium uppercase tracking-luxe transition-colors hover:border-ink"
            >
              Decline
            </button>
          </div>
        </div>
      )}
    </>
  )
}
