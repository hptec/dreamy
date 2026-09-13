import { chromium } from 'playwright'
const BASE = process.env.SITE ?? 'http://localhost:5199'
const OUT = process.env.OUT ?? 'nav-pages-shots'
const browser = await chromium.launch()
// 桌面 mega menu
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto(BASE + '/', { waitUntil: 'networkidle' })
for (const label of ['Wedding Dresses', 'Bridesmaids', 'Accessories']) {
  await page.getByRole('navigation').getByRole('link', { name: label, exact: true }).first().hover()
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${OUT}/mega-${label.replace(/\W+/g, '-').toLowerCase()}.png`, clip: { x: 0, y: 0, width: 1440, height: 520 } })
  const links = await page.locator('.shadow-lift a.link-underline').allTextContents()
  console.log(label, '→', links.join(' | '))
}
// 移动端菜单
const m = await browser.newPage({ viewport: { width: 390, height: 844 } })
await m.goto(BASE + '/', { waitUntil: 'networkidle' })
await m.getByRole('button', { name: /open menu|menu/i }).first().click()
await m.waitForTimeout(300)
await m.getByRole('button', { name: 'Accessories' }).click()
await m.waitForTimeout(300)
await m.screenshot({ path: `${OUT}/mobile-menu-accessories.png` })
console.log('mobile accessories →', (await m.locator('nav a').allTextContents()).filter(Boolean).join(' | '))
// 旧链接空态 + 子分类页
const p = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await p.goto(BASE + '/accessories?cat=Shoes', { waitUntil: 'networkidle' })
await p.screenshot({ path: `${OUT}/legacy-shoes-empty.png`, fullPage: true })
await p.goto(BASE + '/products?cat=Beach+%26+Destination', { waitUntil: 'networkidle' })
await p.screenshot({ path: `${OUT}/plp-beach-destination.png`, fullPage: true })
await browser.close()
