import { NextRequest, NextResponse } from 'next/server'
import { fetchStoreHomePreview } from '@/lib/api/site-builder-server'

export const dynamic = 'force-dynamic'

const SUPPORTED = new Set(['en', 'es', 'fr'])

function publicPath(locale: string, path = '') {
  const prefix = locale === 'en' ? '' : `/${locale}`
  return `${prefix}${path || '/'}`
}

function securePreviewResponse(response: NextResponse) {
  response.headers.set('Cache-Control', 'private, no-store, max-age=0')
  response.headers.set('Referrer-Policy', 'no-referrer')
  response.headers.set('X-Robots-Tag', 'noindex, nofollow, noarchive')
  return response
}

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ locale: string }> },
) {
  const { locale: rawLocale } = await params
  const locale = SUPPORTED.has(rawLocale) ? rawLocale : 'en'
  const token = request.nextUrl.searchParams.get('token')?.trim() ?? ''
  const invalidUrl = new URL(publicPath(locale, '/preview/invalid'), request.url)

  if (token.length < 32) {
    return securePreviewResponse(NextResponse.redirect(invalidUrl))
  }

  const preview = await fetchStoreHomePreview(locale, token)
  if (preview.status !== 200 || !preview.page?.preview) {
    return securePreviewResponse(NextResponse.redirect(invalidUrl))
  }

  const homeUrl = new URL(publicPath(locale), request.url)
  const response = NextResponse.redirect(homeUrl)
  response.cookies.set('dreamy_home_preview', token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 30 * 60,
    path: '/',
  })
  return securePreviewResponse(response)
}
