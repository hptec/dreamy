'use client'

import { useEffect } from 'react'
import { recordBlogView } from '@/lib/api/marketing-api'

/**
 * 2026-08-21: 博客阅读计数埋点（UV 口径）
 * - sessionStorage `blog:viewed:{slug}` 去重，同一会话重复访问不重复 +1
 * - fire-and-forget：失败静默，不影响阅读
 * - SSR 阶段不执行（useEffect 仅客户端）
 */
export function BlogViewTracker({ slug }: { slug: string }) {
  useEffect(() => {
    if (!slug) return
    const key = `blog:viewed:${slug}`
    try {
      if (window.sessionStorage.getItem(key)) return
      window.sessionStorage.setItem(key, '1')
    } catch {
      // sessionStorage 不可用（隐私模式）时仍尝试上报，宁多勿漏
    }
    recordBlogView(slug).catch(() => {
      // 失败则清除标记，下次访问重试
      try {
        window.sessionStorage.removeItem(key)
      } catch {
        /* ignore */
      }
    })
  }, [slug])
  return null
}
