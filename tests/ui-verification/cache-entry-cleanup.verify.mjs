// 验证：AdminShell 冗余缓存入口清理——侧栏底部金色快捷与顶栏「缓存」按钮移除，系统管理组内入口保留，/system/cache 路由仍可达
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const browser = await chromium.launch()
const page = await browser.newPage()
const consoleErrors = []
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()) })

await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })
await page.waitForSelector('aside', { timeout: 10000 })

const aside = page.locator('aside')
const header = page.locator('header')

// 1. 侧栏底部金色快捷入口已移除（aside 内除 nav 外不应再有指向 /system/cache 的链接）
const sidebarCacheLinks = await aside.locator('a[href="/system/cache"]').count()
check(sidebarCacheLinks === 1, `侧栏仅剩 1 个缓存管理入口（系统管理组内），实际 ${sidebarCacheLinks}`)
check(await aside.locator('a[href="/system/cache"] .text-gold-soft, aside a[href="/system/cache"].text-gold-soft').count() === 0, '侧栏底部金色快捷入口已消失')

// 2. 顶栏「缓存」按钮已移除
check(await header.locator('a[href="/system/cache"]').count() === 0, '顶栏「缓存」按钮已消失')

// 3. 系统管理组内入口保留且可导航到缓存管理页
await page.click('aside a[href="/system/cache"]')
await page.waitForURL('**/system/cache', { timeout: 10000 })
await page.waitForSelector('text=缓存管理', { timeout: 10000 })
check(page.url().includes('/system/cache'), '系统管理组内入口可正常进入 /system/cache')

// 4. 页面无新增控制台错误（过滤与本次改动无关的既有噪音）
const relevant = consoleErrors.filter((e) => !e.includes('favicon') && !e.includes('net::'))
check(relevant.length === 0, `无新增控制台错误${relevant.length ? '：' + relevant.join(' | ') : ''}`)

await browser.close()

if (failures.length > 0) {
  console.error(`\n失败 ${failures.length} 项:`)
  failures.forEach((f) => console.error(' -', f))
  process.exit(1)
}
console.log('\n全部通过')
