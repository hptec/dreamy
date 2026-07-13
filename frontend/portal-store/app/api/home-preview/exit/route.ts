import { NextRequest, NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'

export function GET(request: NextRequest) {
  const requestedLocale = request.nextUrl.searchParams.get('locale')
  const locale = requestedLocale === 'es' || requestedLocale === 'fr' ? requestedLocale : 'en'
  const target = new URL(locale === 'en' ? '/' : `/${locale}`, request.url)
  const response = NextResponse.redirect(target)
  response.cookies.set('dreamy_home_preview', '', {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 0,
    path: '/',
  })
  response.headers.set('Cache-Control', 'private, no-store, max-age=0')
  response.headers.set('Referrer-Policy', 'no-referrer')
  return response
}
