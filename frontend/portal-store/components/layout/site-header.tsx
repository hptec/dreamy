'use client'

/**
 * SiteHeader（COMP-MKT-S02 AnnouncementBar data-swap + 搜索抽屉 API 化）：
 * - announcements ← layout RSC 下传 topbar banners title 列表（E-MKT-01 position=topbar）；空回退静态文案。
 * - SearchDrawer：mock 内存检索 → E-CAT-02（防抖 300ms，4 卡）。
 * - 语言切换联动 i18n locale（Accept-Language 驱动 API 文案语言）。
 */

import { useState, useEffect, useRef } from 'react'
import { LocalizedLink as Link } from '@/components/localized-link'
import { usePathname } from 'next/navigation'
import { Search, Heart, User, ShoppingBag, Menu, X, ChevronDown, Globe } from 'lucide-react'
import { mainNav, announcements as staticAnnouncements, currencies, languages } from '@/data/navigation'
import type { StoreProductCard } from '@/lib/api/store-types'
import { searchStoreProducts } from '@/lib/api/catalog-api'
import { useStore } from '@/components/store-provider'
import { CartDrawer } from '@/components/cart/cart-drawer'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n, stripLocale } from '@/lib/i18n/i18n-context'
import type { Locale } from '@/lib/api/types'
import { cn } from '@/lib/utils'

export function SiteHeader({ announcements: serverAnnouncements }: { announcements?: string[] }) {
  const pathname = usePathname()
  const { t } = useI18n()
  const { cartCount, wishlist, currency, setCurrency, language, setLanguage, setCartOpen } = useStore()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const hydrate = useAuthStore((s) => s.hydrate)
  const accountHref = isAuthenticated ? '/account' : '/account/login'
  const announcements = serverAnnouncements && serverAnnouncements.length > 0 ? serverAnnouncements : staticAnnouncements
  const activePath = stripLocale(pathname ?? '/')
  const [announceIdx, setAnnounceIdx] = useState(0)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const t = setInterval(() => setAnnounceIdx((i) => (i + 1) % announcements.length), 4000)
    return () => clearInterval(t)
  }, [announcements.length])

  useEffect(() => {
    void hydrate()
  }, [hydrate])

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', onScroll)
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    setMobileOpen(false)
    setOpenMenu(null)
    setSearchOpen(false)
  }, [pathname])

  return (
    <header className="sticky top-0 z-40">
      {/* 公告条 + 币种语言切换 */}
      <div className="bg-ink text-canvas">
        <div className="container-luxe flex h-9 items-center justify-between text-[11px]">
          <div className="hidden items-center gap-4 sm:flex">
            <CurrencyLang currency={currency} setCurrency={setCurrency} language={language} setLanguage={setLanguage} />
          </div>
          <p key={announceIdx} className="mx-auto animate-fadeup text-center tracking-wide sm:mx-0 sm:flex-1">
            {announcements[announceIdx % announcements.length]}
          </p>
          <div className="hidden items-center gap-4 sm:flex">
            <Link href="/contact" className="transition-colors hover:text-gold-light">{t.layout.header.contact}</Link>
            <Link href="/wedding-guides" className="transition-colors hover:text-gold-light">{t.layout.header.planning}</Link>
          </div>
        </div>
      </div>

      {/* 主导航 */}
      <div className={cn('border-b border-line bg-canvas/95 backdrop-blur transition-shadow', scrolled && 'shadow-soft')}>
        <div className="container-luxe" onMouseLeave={() => setOpenMenu(null)}>
          <div className="flex h-16 items-center justify-between lg:h-20">
            {/* 移动端菜单按钮 */}
            <button onClick={() => setMobileOpen(true)} className="cursor-pointer p-2 lg:hidden" aria-label={t.layout.header.openMenu}>
              <Menu className="h-5 w-5" />
            </button>

            {/* Logo */}
            <Link href="/" className="absolute left-1/2 -translate-x-1/2 lg:static lg:translate-x-0">
              <span className="font-display text-2xl font-semibold tracking-tight lg:text-[1.75rem]">Dreamy</span>
              <span className="ml-1 hidden align-super text-[9px] uppercase tracking-wide2 text-gold lg:inline">Atelier</span>
            </Link>

            {/* 桌面导航 */}
            <nav className="hidden items-center gap-7 lg:flex">
              {mainNav.map((item) => (
                <div key={item.label} onMouseEnter={() => setOpenMenu(item.label)} className="py-7">
                  <Link
                    href={item.href}
                    className={cn(
                      'flex items-center gap-1 text-[13px] font-medium uppercase tracking-luxe transition-colors hover:text-gold-deep',
                      activePath.startsWith(item.href) && item.href !== '/' ? 'text-gold-deep' : 'text-ink'
                    )}
                  >
                    {item.label}
                    {item.columns && <ChevronDown className="h-3 w-3" />}
                  </Link>
                </div>
              ))}
            </nav>

            {/* 工具图标 */}
            <div className="flex items-center gap-1 sm:gap-2">
              <button onClick={() => setSearchOpen(true)} className="cursor-pointer p-2 transition-colors hover:text-gold-deep" aria-label={t.layout.header.searchAria}>
                <Search className="h-5 w-5" />
              </button>
              <Link href="/account/wishlist" className="relative p-2 transition-colors hover:text-gold-deep" aria-label={t.layout.header.wishlistAria}>
                <Heart className="h-5 w-5" />
                {wishlist.length > 0 && (
                  <span className="absolute right-0 top-0 flex h-4 w-4 items-center justify-center rounded-full bg-blush text-[9px] text-white">{wishlist.length}</span>
                )}
              </Link>
              <Link href={accountHref} className="p-2 transition-colors hover:text-gold-deep" aria-label={t.layout.header.accountAria}>
                <User className="h-5 w-5" />
              </Link>
              <button onClick={() => setCartOpen(true)} className="relative cursor-pointer p-2 transition-colors hover:text-gold-deep" aria-label={t.layout.header.cartAria}>
                <ShoppingBag className="h-5 w-5" />
                {cartCount > 0 && (
                  <span className="absolute right-0 top-0 flex h-4 w-4 items-center justify-center rounded-full bg-gold text-[9px] text-white">{cartCount}</span>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* Mega Menu */}
        {openMenu && (
          <MegaPanel label={openMenu} onMouseEnter={() => setOpenMenu(openMenu)} onClose={() => setOpenMenu(null)} />
        )}
      </div>

      {/* 搜索抽屉 */}
      {searchOpen && <SearchDrawer onClose={() => setSearchOpen(false)} />}
      {/* 移动端菜单 */}
      {mobileOpen && <MobileMenu onClose={() => setMobileOpen(false)} />}
      {/* 购物车抽屉 */}
      <CartDrawer />
    </header>
  )
}

function CurrencyLang({ currency, setCurrency, language, setLanguage }: any) {
  const { t, locale, setLocale } = useI18n()
  return (
    <div className="flex items-center gap-3">
      <Globe className="h-3.5 w-3.5 text-gold-light" />
      <select
        value={currency}
        onChange={(e) => setCurrency(e.target.value)}
        className="cursor-pointer bg-transparent text-canvas outline-none [&>option]:text-ink"
        aria-label={t.layout.header.currencyAria}
      >
        {currencies.map((c) => (
          <option key={c.code} value={c.code}>{c.label}</option>
        ))}
      </select>
      <span className="text-gold-light/40">|</span>
      <select
        value={locale.toUpperCase()}
        onChange={(e) => {
          const code = e.target.value
          setLanguage(code)
          // 语言切换：写 cookie + 跳转到 locale 前缀 URL（决策 11）
          setLocale(code.toLowerCase() as Locale)
        }}
        className="cursor-pointer bg-transparent text-canvas outline-none [&>option]:text-ink"
        aria-label={t.layout.header.languageAria}
      >
        {languages.map((l) => (
          <option key={l.code} value={l.code}>{l.label}</option>
        ))}
      </select>
    </div>
  )
}

function MegaPanel({ label, onClose, onMouseEnter }: { label: string; onClose: () => void; onMouseEnter: () => void }) {
  const item = mainNav.find((n) => n.label === label)
  if (!item?.columns) return null
  return (
    <div onMouseEnter={onMouseEnter} onMouseLeave={onClose} className="absolute inset-x-0 top-full hidden border-b border-line bg-canvas shadow-lift lg:block">
      <div className="container-luxe grid grid-cols-4 gap-8 py-10">
        {item.columns.map((col) => (
          <div key={col.title}>
            <p className="eyebrow mb-4">{col.title}</p>
            <ul className="space-y-2.5">
              {col.links.map((l) => (
                <li key={l.label}>
                  <Link href={l.href} onClick={onClose} className="link-underline text-sm text-ink-soft transition-colors hover:text-ink">{l.label}</Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
        {item.featured && (
          <Link href={item.featured.href} onClick={onClose} className="group relative col-start-4 overflow-hidden rounded-sm">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={item.featured.image} alt={item.featured.label} className="h-56 w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
            <div className="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent" />
            <span className="absolute bottom-4 left-4 text-sm font-medium uppercase tracking-luxe text-canvas">{item.featured.label}</span>
          </Link>
        )}
      </div>
    </div>
  )
}

function SearchDrawer({ onClose }: { onClose: () => void }) {
  const { t } = useI18n()
  const [q, setQ] = useState('')
  const [results, setResults] = useState<StoreProductCard[]>([])
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const seq = useRef(0)
  const popular = ['Sage bridesmaid', 'A-line tulle', 'Beach wedding', 'Cathedral veil']

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    const term = q.trim()
    if (term.length <= 1) {
      seq.current += 1
      setResults([])
      return
    }
    debounceRef.current = setTimeout(() => {
      const mySeq = ++seq.current
      searchStoreProducts(term, 1, 4)
        .then((res) => {
          if (mySeq === seq.current) setResults(res.data)
        })
        .catch(() => {
          if (mySeq === seq.current) setResults([])
        })
    }, 300)
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [q])

  return (
    <div className="fixed inset-0 z-50">
      <div className="absolute inset-0 bg-ink/30 backdrop-blur-sm" onClick={onClose} />
      <div className="absolute inset-x-0 top-0 animate-fadeup bg-canvas shadow-lift">
        <div className="container-luxe py-8">
          <div className="flex items-center gap-4 border-b border-ink/20 pb-4">
            <Search className="h-5 w-5 text-ink-soft" />
            <input
              autoFocus
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t.layout.search.placeholder}
              className="flex-1 bg-transparent font-display text-2xl outline-none placeholder:text-ink-faint"
            />
            <button onClick={onClose} className="cursor-pointer p-2" aria-label={t.common.close}><X className="h-5 w-5" /></button>
          </div>
          {q.length <= 1 ? (
            <div className="py-6">
              <p className="eyebrow mb-3">{t.layout.search.popular}</p>
              <div className="flex flex-wrap gap-2">
                {popular.map((p) => (
                  <button key={p} onClick={() => setQ(p)} className="cursor-pointer rounded-full border border-line px-4 py-1.5 text-sm transition-colors hover:border-gold hover:text-gold-deep">{p}</button>
                ))}
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4 py-6 sm:grid-cols-4">
              {results.length === 0 ? (
                <p className="col-span-full py-8 text-center text-ink-soft">{t.layout.search.noMatches}</p>
              ) : (
                results.map((p) => (
                  <Link key={p.id} href={`/product/${p.slug}`} onClick={onClose} className="group">
                    {p.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={p.imageUrl} alt={p.name} className="aspect-[3/4] w-full rounded-sm object-cover" />
                    ) : (
                      <div className="aspect-[3/4] w-full rounded-sm bg-muted" />
                    )}
                    <p className="mt-2 text-sm">{p.name}</p>
                  </Link>
                ))
              )}
              <Link href={`/search?q=${encodeURIComponent(q)}`} onClick={onClose} className="col-span-full mt-2 text-center text-sm font-medium uppercase tracking-luxe text-gold-deep">
                {t.layout.search.viewAllResults}
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function MobileMenu({ onClose }: { onClose: () => void }) {
  const { t } = useI18n()
  const [expanded, setExpanded] = useState<string | null>(null)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return (
    <div className="fixed inset-0 z-50 lg:hidden">
      <div className="absolute inset-0 bg-ink/40" onClick={onClose} />
      <div className="absolute left-0 top-0 h-full w-[85%] max-w-sm animate-fadeup overflow-y-auto bg-canvas">
        <div className="flex items-center justify-between border-b border-line p-5">
          <span className="font-display text-2xl font-semibold">Dreamy</span>
          <button onClick={onClose} className="cursor-pointer p-2" aria-label={t.layout.header.closeMenu}><X className="h-5 w-5" /></button>
        </div>
        <nav className="p-5">
          {mainNav.map((item) => (
            <div key={item.label} className="border-b border-line/60 py-1">
              <button
                onClick={() => setExpanded(expanded === item.label ? null : item.label)}
                className="flex w-full cursor-pointer items-center justify-between py-3 text-sm font-medium uppercase tracking-luxe"
              >
                {item.label}
                {item.columns && <ChevronDown className={cn('h-4 w-4 transition-transform', expanded === item.label && 'rotate-180')} />}
              </button>
              {expanded === item.label && item.columns && (
                <div className="space-y-3 pb-4 pl-3">
                  {item.columns.map((col) => (
                    <div key={col.title}>
                      <p className="eyebrow mb-1.5">{col.title}</p>
                      {col.links.map((l) => (
                        <Link key={l.label} href={l.href} onClick={onClose} className="block py-1.5 text-sm text-ink-soft">{l.label}</Link>
                      ))}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
          <Link href={isAuthenticated ? '/account' : '/account/login'} onClick={onClose} className="mt-6 block text-sm font-medium uppercase tracking-luxe text-gold-deep">{isAuthenticated ? t.layout.header.myAccount : t.layout.header.signInRegister}</Link>
        </nav>
      </div>
    </div>
  )
}
