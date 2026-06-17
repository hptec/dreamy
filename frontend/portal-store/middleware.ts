import { NextResponse, type NextRequest } from 'next/server'

/**
 * Locale 中间件（FUNC-001 / FUNC-014 / EDGE-018 / EDGE-019 / 决策 11）。
 *
 * 路由策略（EN 无前缀根路径，ES/FR 带 /es、/fr 前缀）：
 * - 已带有效前缀（/es、/fr）：放行，刷新 NEXT_LOCALE cookie。
 * - 已带 /en 前缀：301 规范化到无前缀根路径（避免重复索引，EN 唯一 URL）。
 * - 带无效语言段前缀（如 /de/...）：EDGE-018 → 302 临时重定向去掉前缀回 EN。
 * - 无前缀：EDGE-019 旧链接兼容。检测优先级 cookie > Accept-Language。
 *     检测为 EN → rewrite 到内部 /en（URL 保持干净无前缀）。
 *     检测为 ES/FR → 301 永久重定向到带前缀 URL（SEO 友好）。
 */

const SUPPORTED = ['en', 'es', 'fr'] as const
type Locale = (typeof SUPPORTED)[number]
const DEFAULT_LOCALE: Locale = 'en'

const PUBLIC_FILE = /\.(.*)$/

function isLocale(seg: string | undefined): seg is Locale {
  return !!seg && (SUPPORTED as readonly string[]).includes(seg)
}

function parseAcceptLanguage(header: string | null): Locale | null {
  if (!header) return null
  // "es-ES,es;q=0.9,en;q=0.8" → 取首个匹配支持语言
  const parts = header.split(',').map((p) => p.trim().split(';')[0].slice(0, 2).toLowerCase())
  for (const p of parts) {
    if (isLocale(p)) return p
  }
  return null
}

export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl

  // 0. public 静态资源（带扩展名，如 /competitor-refs/x.jpg）直接放行：
  //    否则会被分支 4 rewrite 成 /en/...jpg，被当页面路由 404。
  if (PUBLIC_FILE.test(pathname)) {
    return NextResponse.next()
  }

  const segments = pathname.split('/')
  const firstSeg = segments[1]

  // 1. 已带 /en 前缀 → 规范化为无前缀（EN 唯一 URL）
  if (firstSeg === 'en') {
    const stripped = pathname.replace(/^\/en(?=\/|$)/, '') || '/'
    const url = request.nextUrl.clone()
    url.pathname = stripped
    return NextResponse.redirect(url, 301)
  }

  // 2. 已带有效 ES/FR 前缀 → 放行 + 刷新 cookie
  if (isLocale(firstSeg)) {
    const res = NextResponse.next()
    res.cookies.set('NEXT_LOCALE', firstSeg, { path: '/', maxAge: 31536000, sameSite: 'lax' })
    return res
  }

  // 3. 无前缀，但首段看起来像一个语言代码（2 字母）且不受支持 → EDGE-018 无效 locale
  //    （如 /de/...）：302 去掉首段回 EN
  if (firstSeg && firstSeg.length === 2 && /^[a-z]{2}$/.test(firstSeg) && !isLocale(firstSeg)) {
    const url = request.nextUrl.clone()
    url.pathname = pathname.slice(firstSeg.length + 1) || '/'
    return NextResponse.redirect(url, 302)
  }

  // 4. 无前缀（旧链接兼容 EDGE-019）：检测偏好 locale
  const cookieLocale = request.cookies.get('NEXT_LOCALE')?.value
  const detected: Locale = isLocale(cookieLocale)
    ? cookieLocale
    : parseAcceptLanguage(request.headers.get('accept-language')) ?? DEFAULT_LOCALE

  if (detected === DEFAULT_LOCALE) {
    // EN：rewrite 到内部 [locale]=en 段，URL 保持干净无前缀
    const url = request.nextUrl.clone()
    url.pathname = `/en${pathname === '/' ? '' : pathname}`
    return NextResponse.rewrite(url)
  }

  // ES/FR：301 永久重定向到带前缀 URL（SEO 友好，EDGE-019）
  const url = request.nextUrl.clone()
  url.pathname = `/${detected}${pathname === '/' ? '' : pathname}`
  url.search = search
  return NextResponse.redirect(url, 301)
}

export const config = {
  // 排除 api / _next 静态资源 / 带扩展名的文件 / sitemap / robots
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt|icon.svg).*)']
}

export { SUPPORTED, DEFAULT_LOCALE, isLocale, PUBLIC_FILE }
