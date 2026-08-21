import type { MetadataRoute } from 'next'
import { siteBaseUrl } from '@/lib/i18n/seo'

/**
 * robots.txt（2026-08-20 新建）
 * - 允许全部公开页面
 * - 禁止爬虫：API 路径 / 草稿预览 / 首页预览
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: '*',
      allow: '/',
      disallow: ['/api/', '/blog/preview/', '/api/home-preview/'],
    },
    sitemap: `${siteBaseUrl()}/sitemap.xml`,
  }
}
