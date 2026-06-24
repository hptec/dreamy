// site_builder 域 API（site-decoration-fullstack 变更）
// 端点：/api/admin/site-builder/* 和 /api/store/content/*
import { get, post, put, patch, del } from './client'

// ===== 类型定义 =====

export interface HomePageSection {
  id: number
  sectionType: string
  enabled: boolean
  sortOrder: number
  dataJson: any
  i18nJson: any
  label: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface HomePageSectionUpsert {
  sectionType: string
  enabled: boolean
  sortOrder: number
  dataJson?: any
  i18nJson?: any
  label?: string
  version?: number
}

export interface SortItem {
  id: number
  sortOrder: number
}

export interface NavigationItem {
  id: number
  parentId: number | null
  label: string
  labelI18nKey: string | null
  url: string | null
  target: string
  linkType: string
  taxonomyId: number | null
  megaMenuJson: any
  i18nJson: any
  sortOrder: number
  enabled: boolean
  version: number
}

export interface NavigationItemUpsert {
  id?: number
  parentId?: number | null
  label: string
  labelI18nKey?: string
  url?: string
  target?: string
  linkType?: string
  taxonomyId?: number
  megaMenuJson?: any
  i18nJson?: any
  sortOrder?: number
  enabled?: boolean
}

export interface FooterColumn {
  id: number
  title: string
  i18nJson: any
  sortOrder: number
  enabled: boolean
  links: FooterLink[]
}

export interface FooterLink {
  id: number
  columnId: number
  label: string
  url: string
  target: string
  i18nJson: any
  sortOrder: number
}

export interface FooterLinkUpsert {
  id?: number
  columnId?: number
  label: string
  url: string
  target?: string
  i18nJson?: any
  sortOrder?: number
}

export interface FooterColumnUpsert {
  id?: number
  title: string
  i18nJson?: any
  sortOrder?: number
  enabled?: boolean
  links?: FooterLinkUpsert[]
}

export interface Announcement {
  id: number
  enabled: boolean
  priority: number
  startAt: string | null
  endAt: string | null
  content: string | null
  contentI18nJson: any
  i18nJson: any
  version: number
  createdAt: string
  updatedAt: string
}

export interface AnnouncementUpsert {
  enabled: boolean
  priority: number
  startAt?: string | null
  endAt?: string | null
  contentI18nJson: any
  i18nJson?: any
  version?: number
}

// ===== admin 端 API =====

export function listHomeSections(enabledOnly?: boolean): Promise<{ items: HomePageSection[] }> {
  return get<{ items: HomePageSection[] }>('/api/admin/site-builder/home-sections', {
    params: enabledOnly !== undefined ? { enabled_only: enabledOnly } : undefined,
  })
}

export function createHomeSection(body: HomePageSectionUpsert): Promise<HomePageSection> {
  return post<HomePageSection>('/api/admin/site-builder/home-sections', body)
}

export function getHomeSection(id: number): Promise<HomePageSection> {
  return get<HomePageSection>(`/api/admin/site-builder/home-sections/${id}`)
}

export function updateHomeSection(id: number, body: HomePageSectionUpsert): Promise<HomePageSection> {
  return put<HomePageSection>(`/api/admin/site-builder/home-sections/${id}`, body)
}

export function deleteHomeSection(id: number): Promise<void> {
  return del<void>(`/api/admin/site-builder/home-sections/${id}`)
}

export function sortHomeSections(items: SortItem[]): Promise<void> {
  return put<void>('/api/admin/site-builder/home-sections/sort', { items })
}

export function toggleHomeSection(id: number, enabled: boolean): Promise<HomePageSection> {
  return patch<HomePageSection>(`/api/admin/site-builder/home-sections/${id}/toggle`, { enabled })
}

export function getNavigation(): Promise<{ items: NavigationItem[] }> {
  return get<{ items: NavigationItem[] }>('/api/admin/site-builder/navigation')
}

export function saveNavigation(items: NavigationItemUpsert[]): Promise<{ items: NavigationItem[] }> {
  return put<{ items: NavigationItem[] }>('/api/admin/site-builder/navigation', { items })
}

export function getFooter(): Promise<{ columns: FooterColumn[] }> {
  return get<{ columns: FooterColumn[] }>('/api/admin/site-builder/footer')
}

export function saveFooter(columns: FooterColumnUpsert[]): Promise<{ columns: FooterColumn[] }> {
  return put<{ columns: FooterColumn[] }>('/api/admin/site-builder/footer', { columns })
}

export function listAnnouncements(params: {
  enabledOnly?: boolean
  page?: number
  pageSize?: number
}): Promise<any> {
  return get('/api/admin/site-builder/announcements', {
    params: {
      enabled_only: params.enabledOnly,
      page: params.page ?? 1,
      page_size: params.pageSize ?? 20,
    },
  })
}

export function createAnnouncement(body: AnnouncementUpsert): Promise<Announcement> {
  return post<Announcement>('/api/admin/site-builder/announcements', body)
}

export function getAnnouncement(id: number): Promise<Announcement> {
  return get<Announcement>(`/api/admin/site-builder/announcements/${id}`)
}

export function updateAnnouncement(id: number, body: AnnouncementUpsert): Promise<Announcement> {
  return put<Announcement>(`/api/admin/site-builder/announcements/${id}`, body)
}

export function deleteAnnouncement(id: number): Promise<void> {
  return del<void>(`/api/admin/site-builder/announcements/${id}`)
}

export function toggleAnnouncement(id: number, enabled: boolean): Promise<Announcement> {
  return patch<Announcement>(`/api/admin/site-builder/announcements/${id}/toggle`, { enabled })
}
