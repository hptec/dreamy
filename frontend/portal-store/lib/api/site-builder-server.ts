/**
 * site_builder 域 — 消费端内容取数（RSC server-side fetch）。
 * site-decoration-fullstack 变更新增（KD-5 消费端全量改造）。
 * 端点：GET /api/store/content/home | navigation | footer | announcements
 */

import { cache } from 'react'
import { serverGet } from './server-fetch'

// ===== 类型定义 =====

export type StoreHomeSectionType =
  | 'hero'
  | 'themeCards'
  | 'productRail'
  | 'editorialFeature'
  | 'newsletter'
  | 'custom'

interface StoreSectionCopy {
  eyebrow?: string | null
  heading?: string | null
  description?: string | null
}

export interface StoreHeroSlide {
  id?: number | null
  title?: string | null
  subtitle?: string | null
  imageUrl?: string | null
  ctaText?: string | null
  ctaLink?: string | null
  ctaTextSecondary?: string | null
  ctaLinkSecondary?: string | null
}

export interface StoreHeroData extends StoreHeroSlide {
  /** 全部在线 HERO Banner，按后台 sort/id 顺序。扁平字段保留用于滚动部署兼容。 */
  banners?: StoreHeroSlide[]
}

export interface StoreThemeCard {
  id: number
  name: string
  productCount?: number | null
  imageUrl?: string | null
}

export interface StoreThemeCardsData extends StoreSectionCopy {
  cards?: StoreThemeCard[]
}

export interface StoreProductRailItem {
  id: number
  slug: string
  name: string
  price: number | string
  imageUrl?: string | null
  isNew?: boolean
  isBest?: boolean
}

export interface StoreProductRailData extends StoreSectionCopy {
  products?: StoreProductRailItem[]
}

export interface StoreEditorialStory {
  id: number
  couple: string
  location?: string | null
  theme?: string | null
  weddingDate?: string | null
  cover?: string | null
  title?: string | null
}

export interface StoreEditorialFeatureData extends StoreSectionCopy {
  stories?: StoreEditorialStory[]
}

export interface StoreNewsletterData extends StoreSectionCopy {
  placeholder?: string | null
  cta?: string | null
}

export interface StoreCustomData {
  heading?: string | null
  subtitle?: string | null
  content?: string | null
  imageUrl?: string | null
  ctaText?: string | null
  ctaLink?: string | null
}

interface StoreHomeSectionDataMap {
  hero: StoreHeroData
  themeCards: StoreThemeCardsData
  productRail: StoreProductRailData
  editorialFeature: StoreEditorialFeatureData
  newsletter: StoreNewsletterData
  custom: StoreCustomData
}

export type StoreHomeSection = {
  [K in StoreHomeSectionType]: { sectionType: K; data: StoreHomeSectionDataMap[K] }
}[StoreHomeSectionType]

export interface StoreHomePage {
  sections: StoreHomeSection[]
}

interface RawStoreHomeSection {
  sectionType?: string | null
  data?: unknown
}

interface RawStoreHomePage {
  sections?: RawStoreHomeSection[] | null
}

const SECTION_TYPE_MAP: Record<string, StoreHomeSectionType> = {
  hero: 'hero',
  theme_cards: 'themeCards',
  themeCards: 'themeCards',
  product_rail: 'productRail',
  productRail: 'productRail',
  editorial_feature: 'editorialFeature',
  editorialFeature: 'editorialFeature',
  newsletter: 'newsletter',
  custom: 'custom',
}

function sectionData(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function normalizeHeroData(data: Record<string, unknown>): StoreHeroData {
  const legacy = data as StoreHeroSlide
  const banners = Array.isArray(data.banners)
    ? data.banners.filter((item): item is StoreHeroSlide => item !== null && typeof item === 'object')
    : legacy.imageUrl
      ? [legacy]
      : []
  return { ...legacy, banners }
}

function normalizeHomePage(page: RawStoreHomePage | null): StoreHomePage | null {
  if (!page) return null

  const sections = (page.sections ?? []).flatMap((section): StoreHomeSection[] => {
    const sectionType = section.sectionType ? SECTION_TYPE_MAP[section.sectionType] : undefined
    if (!sectionType) return []
    const data = sectionData(section.data)

    switch (sectionType) {
      case 'hero':
        return [{ sectionType, data: normalizeHeroData(data) }]
      case 'themeCards':
        return [{ sectionType, data: data as StoreThemeCardsData }]
      case 'productRail':
        return [{ sectionType, data: data as StoreProductRailData }]
      case 'editorialFeature':
        return [{ sectionType, data: data as StoreEditorialFeatureData }]
      case 'newsletter':
        return [{ sectionType, data: data as StoreNewsletterData }]
      case 'custom':
        return [{ sectionType, data: data as StoreCustomData }]
    }
  })

  return { sections }
}

export interface StoreNavigationItem {
  id: number
  parentId: number | null
  label: string
  url: string | null
  target: string
  linkType: string
  taxonomyId: number | null
  megaMenu: any
  sortOrder: number
}

export interface StoreNavigation {
  items: StoreNavigationItem[]
}

export interface StoreFooterLink {
  id: number
  label: string
  url: string
  target: string
  sortOrder: number
}

export interface StoreFooterColumn {
  id: number
  title: string
  sortOrder: number
  links: StoreFooterLink[]
}

export interface StoreFooter {
  columns: StoreFooterColumn[]
}

export interface StoreAnnouncement {
  id: number
  priority: number
  content: string
  startAt: string | null
  endAt: string | null
}

export interface StoreAnnouncementList {
  announcements: StoreAnnouncement[]
}

// ===== Server-side fetch（RSC，force-dynamic） =====

/** FLOW-SB05 消费端首页区块聚合 */
export const fetchStoreHome = cache(async (locale: string): Promise<StoreHomePage | null> => {
  try {
    const page = await serverGet<RawStoreHomePage>('/api/store/content/home', { query: { locale } })
    return normalizeHomePage(page)
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
