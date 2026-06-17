'use client'

/**
 * HtmlLangSync（决策 11）：按当前 locale 同步 <html lang> 属性。
 * 根布局静态渲染 lang="en"，此组件在 locale 段挂载后客户端校正，保证 a11y/SEO 语言信号正确。
 */

import { useEffect } from 'react'
import type { Locale } from '@/lib/api/types'

export function HtmlLangSync({ locale }: { locale: Locale }) {
  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = locale
    }
  }, [locale])
  return null
}
