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
import { Search, Heart, User, ShoppingBag, Menu, X, ChevronDown, Globe, Check } from 'lucide-react'
import { mainNav, announcements as staticAnnouncements, currencies, languages, type NavItem } from '@/data/navigation'
import type { StoreProductCard } from '@/lib/api/store-types'
import { searchStoreProducts } from '@/lib/api/catalog-api'
import { useStore } from '@/components/store-provider'
import { CartDrawer } from '@/components/cart/cart-drawer'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n, stripLocale } from '@/lib/i18n/i18n-context'
import type { Locale } from '@/lib/api/types'
import { cn, linkTargetProps } from '@/lib/utils'

import type { StoreNavigationItem } from '@/lib/api/site-builder-server'

/** 头部导航项（API 项与静态 mainNav 归一后的形态） */
type HeaderNavItem = NavItem & { target?: string }

/** 有二级内容才展示 chevron / mega panel / 移动端展开 */
function hasColumns(item: HeaderNavItem): boolean {
  return !!item.columns && item.columns.length > 0
}

export function SiteHeader({
  announcements: serverAnnouncements,
  navigationItems,
}: {
  announcements?: string[]
  navigationItems?: StoreNavigationItem[]
}) {
  const pathname = usePathname()
  const { t } = useI18n()
  const { cartCount, wishlist, currency, setCurrency, language, setLanguage, setCartOpen } = useStore()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const hydrate = useAuthStore((s) => s.hydrate)
  const accountHref = isAuthenticated ? '/account' : '/account/login'
  const announcements = serverAnnouncements && serverAnnouncements.length > 0 ? serverAnnouncements : staticAnnouncements
  // KD-5：导航项从 site_builder 域读取，空回退静态 mainNav；url 已由后端按 linkType 解析。
  // 二级菜单（桌面 mega panel / 移动端展开）同源取 mega_menu_json（columns/featured），
  // 桌面与移动端共用同一份 navItems，避免两套信息架构各说各话。
  const navItems: HeaderNavItem[] = navigationItems && navigationItems.length > 0
    ? navigationItems.filter((i) => i.parentId === null).map((i) => ({
        label: i.label,
        href: i.url ?? '/',
        target: i.target,
        columns: i.megaMenu?.columns?.filter((c) => c.links?.length > 0),
        featured: i.megaMenu?.featured ?? undefined,
      }))
    : mainNav
  const desktopNavItems = navItems.filter((i) => i.href !== '/')
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
            <button onClick={() => setMobileOpen(true)} className="cursor-pointer p-2 xl:hidden" aria-label={t.layout.header.openMenu}>
              <Menu className="h-5 w-5" />
            </button>

            {/* Logo */}
            <Link href="/" className="absolute left-1/2 -translate-x-1/2 xl:static xl:mr-6 xl:translate-x-0 min-[1400px]:mr-10">
              <span className="font-display text-2xl font-semibold tracking-tight xl:text-[1.75rem]">Dreamy</span>
              <span className="ml-1 hidden align-super text-[9px] uppercase tracking-wide2 text-gold min-[1400px]:inline">Atelier</span>
            </Link>

            {/* 桌面导航：Home 由 logo 承担不重复占位；六项+图标 1280 以下放不下，走汉堡；
                单行不换行，1280/1400/1536 三档递增字号与间距 */}
            <nav className="hidden items-center gap-4 xl:flex min-[1400px]:gap-6 2xl:gap-7">
              {desktopNavItems.map((item) => (
                <div key={item.label} onMouseEnter={() => setOpenMenu(hasColumns(item) ? item.label : null)} className="py-7">
                  <Link
                    href={item.href}
                    {...linkTargetProps(item.target)}
                    className={cn(
                      'flex items-center gap-1 whitespace-nowrap text-[11px] font-medium uppercase tracking-[0.12em] transition-colors hover:text-gold-deep min-[1400px]:text-[12px] min-[1400px]:tracking-[0.14em] 2xl:text-[13px] 2xl:tracking-luxe',
                      activePath.startsWith(item.href) && item.href !== '/' ? 'text-gold-deep' : 'text-ink'
                    )}
                  >
                    {item.label}
                    {hasColumns(item) && <ChevronDown className="h-3 w-3" />}
                  </Link>
                </div>
              ))}
            </nav>

            {/* 工具图标 */}
            <div className="flex items-center gap-1 sm:gap-2 xl:ml-6">
              <button onClick={() => setSearchOpen(true)} className="cursor-pointer p-2 transition-colors hover:text-gold-deep" aria-label={t.layout.header.searchAria}>
                <Search className="h-5 w-5" />
              </button>
              <Link href="/account/wishlist" className="relative p-2 transition-colors hover:text-gold-deep" aria-label={t.layout.header.wishlistAria}>
                <Heart className="h-5 w-5" />
                {wishlist.length > 0 && (
                  <span className="absolute right-0 top-0 flex h-4 w-4 items-center justify-center rounded-full bg-blush text-[9px] text-white">{wishlist.length}</span>
                )}
              </Link>
              <Link href={accountHref} className="hidden p-2 transition-colors hover:text-gold-deep sm:block" aria-label={t.layout.header.accountAria}>
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
          <MegaPanel item={navItems.find((n) => n.label === openMenu)} onMouseEnter={() => setOpenMenu(openMenu)} onClose={() => setOpenMenu(null)} />
        )}
      </div>

      {/* 搜索抽屉 */}
      {searchOpen && <SearchDrawer onClose={() => setSearchOpen(false)} />}
      {/* 移动端菜单 */}
      {mobileOpen && <MobileMenu items={navItems} onClose={() => setMobileOpen(false)} />}
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
      <TopbarSelect
        value={currency}
        options={currencies}
        onChange={setCurrency}
        ariaLabel={t.layout.header.currencyAria}
      />
      <span className="text-gold-light/40">|</span>
      <TopbarSelect
        value={locale.toUpperCase()}
        options={languages}
        onChange={(code) => {
          setLanguage(code)
          // 语言切换：写 cookie + 跳转到 locale 前缀 URL（决策 11）
          setLocale(code.toLowerCase() as Locale)
        }}
        ariaLabel={t.layout.header.languageAria}
      />
    </div>
  )
}

function TopbarSelect({
  value,
  options,
  onChange,
  ariaLabel,
}: {
  value: string
  options: { code: string; label: string }[]
  onChange: (code: string) => void
  ariaLabel: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const current = options.find((o) => o.code === value)

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={ariaLabel}
        aria-expanded={open}
        className="flex cursor-pointer items-center gap-1 tracking-wide transition-colors hover:text-gold-light"
      >
        {current?.label ?? value}
        <ChevronDown className={cn('h-3 w-3 text-gold-light/70 transition-transform duration-200', open && 'rotate-180')} />
      </button>
      {open && (
        <div className="absolute left-1/2 top-full z-50 mt-2.5 w-36 -translate-x-1/2 animate-fadeup border border-gold-light/20 bg-ink py-1.5 shadow-lift">
          {options.map((o) => (
            <button
              key={o.code}
              type="button"
              onClick={() => {
                onChange(o.code)
                setOpen(false)
              }}
              className={cn(
                'flex w-full cursor-pointer items-center justify-between px-4 py-2 text-left text-[11px] tracking-wide transition-colors',
                o.code === value ? 'text-gold-light' : 'text-canvas/70 hover:bg-white/5 hover:text-canvas'
              )}
            >
              {o.label}
              {o.code === value && <Check className="h-3 w-3" />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function MegaPanel({ item, onClose, onMouseEnter }: { item?: HeaderNavItem; onClose: () => void; onMouseEnter: () => void }) {
  if (!item || !hasColumns(item)) return null
  const columns = item.columns!
  return (
    <div onMouseEnter={onMouseEnter} onMouseLeave={onClose} className="absolute inset-x-0 top-full hidden border-b border-line bg-canvas shadow-lift xl:block">
      <div className="container-luxe grid grid-cols-4 gap-8 py-10">
        {columns.map((col) => (
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
          <Link href={item.featured.href} onClick={onClose} className="group col-start-4">
            <div className="overflow-hidden rounded-sm">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={item.featured.image} alt={item.featured.label} className="h-48 w-full object-cover object-[center_30%] transition-transform duration-700 ease-luxe group-hover:scale-105" />
            </div>
            <span className="mt-3 block text-[11px] font-medium uppercase tracking-luxe text-ink transition-colors group-hover:text-gold-deep">{item.featured.label}</span>
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
  const popular = ['Sage bridesmaid', 'A-line lace', 'Beach wedding', 'Mermaid']

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

function MobileMenu({ items, onClose }: { items: HeaderNavItem[]; onClose: () => void }) {
  const { t } = useI18n()
  const [expanded, setExpanded] = useState<string | null>(null)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return (
    <div className="fixed inset-0 z-50 xl:hidden">
      <div className="absolute inset-0 bg-ink/40" onClick={onClose} />
      <div className="absolute left-0 top-0 h-full w-[85%] max-w-sm animate-fadeup overflow-y-auto bg-canvas">
        <div className="flex items-center justify-between border-b border-line p-5">
          <span className="font-display text-2xl font-semibold">Dreamy</span>
          <button onClick={onClose} className="cursor-pointer p-2" aria-label={t.layout.header.closeMenu}><X className="h-5 w-5" /></button>
        </div>
        <nav className="p-5">
          {items.map((item) => (
            <div key={item.label} className="border-b border-line/60 py-1">
              {hasColumns(item) ? (
                <button
                  onClick={() => setExpanded(expanded === item.label ? null : item.label)}
                  className="flex w-full cursor-pointer items-center justify-between py-3 text-sm font-medium uppercase tracking-luxe"
                >
                  {item.label}
                  <ChevronDown className={cn('h-4 w-4 transition-transform', expanded === item.label && 'rotate-180')} />
                </button>
              ) : (
                // 无二级内容的项直接可点（原实现点标题无响应，无子菜单的项在移动端根本进不去）
                <Link href={item.href} {...linkTargetProps(item.target)} onClick={onClose} className="flex w-full items-center justify-between py-3 text-sm font-medium uppercase tracking-luxe">
                  {item.label}
                </Link>
              )}
              {expanded === item.label && hasColumns(item) && (
                <div className="space-y-3 pb-4 pl-3">
                  <Link href={item.href} {...linkTargetProps(item.target)} onClick={onClose} className="block py-1.5 text-sm font-medium text-ink">
                    {t.common.viewAll} · {item.label}
                  </Link>
                  {item.columns!.map((col) => (
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
