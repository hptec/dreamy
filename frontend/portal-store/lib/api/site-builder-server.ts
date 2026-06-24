/**
 * site_builder 域 — 消费端内容取数（RSC server-side fetch）。
 * site-decoration-fullstack 变更新增（KD-5 消费端全量改造）。
 * 端点：GET /api/store/content/home | navigation | footer | announcements
 */

import { cache } from 'react'
import { serverGet } from './server-fetch'

// ===== 类型定义 =====

export interface StoreHomeSection {
  section_type: string
  data: Record<string, any>
}

export interface StoreHomePage {
  sections: StoreHomeSection[]
}

export interface StoreNavigationItem {
  id: number
  parent_id: number | null
  label: string
  url: string | null
  target: string
  link_type: string
  taxonomy_id: number | null
  mega_menu: any
  sort_order: number
}

export interface StoreNavigation {
  items: StoreNavigationItem[]
}

export interface StoreFooterLink {
  id: number
  label: string
  url: string
  target: string
  sort_order: number
}

export interface StoreFooterColumn {
  id: number
  title: string
  sort_order: number
  links: StoreFooterLink[]
}

export interface StoreFooter {
  columns: StoreFooterColumn[]
}

export interface StoreAnnouncement {
  id: number
  priority: number
  content: string
  start_at: string | null
  end_at: string | null
}

export interface StoreAnnouncementList {
  announcements: StoreAnnouncement[]
}

// ===== Server-side fetch（RSC，force-dynamic） =====

/** FLOW-SB05 消费端首页区块聚合 */
export const fetchStoreHome = cache(async (locale: string): Promise<StoreHomePage | null> => {
  try {
    return await serverGet<StoreHomePage>('/api/store/content/home', { query: { locale } })
  } catch (e) {
    console.warn('[site_builder] fetchStoreHome failed, degrade to null', e)
    return null
  }
})

/** FLOW-SB06 消费端导航 */
export const fetchStoreNavigation = cache(async (locale: string): Promise<StoreNavigation | null> => {
  try {
    return await serverGet<StoreNavigation>('/api/store/content/navigation', { query: { locale } })
  } catch (e) {
    console.warn('[site_builder] fetchStoreNavigation failed, degrade to null', e)
    return null
  }
})

/** FLOW-SB07 消费端页脚 */
export const fetchStoreFooter = cache(async (locale: string): Promise<StoreFooter | null> => {
  try {
    return await serverGet<StoreFooter>('/api/store/content/footer', { query: { locale } })
  } catch (e) {
    console.warn('[site_builder] fetchStoreFooter failed, degrade to null', e)
    return null
  }
})

/** FLOW-SB08 消费端公告条 */
export const fetchStoreAnnouncements = cache(async (locale: string): Promise<StoreAnnouncementList | null> => {
  try {
    return await serverGet<StoreAnnouncementList>('/api/store/content/announcements', { query: { locale } })
  } catch (e) {
    console.warn('[site_builder] fetchStoreAnnouncements failed, degrade to null', e)
    return null
  }
})

/** FLOW-SB09 Newsletter 订阅（客户端调用） */
export async function subscribeNewsletter(body: {
  email: string
  source?: string
  locale?: string
}): Promise<{ ok: boolean; error?: string }> {
  const res = await fetch('/api/store/newsletter', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...body, source: body.source ?? 'HOME_BLOCK' }),
  })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    return { ok: false, error: data?.message ?? 'Subscribe failed' }
  }
  return { ok: true }
}
