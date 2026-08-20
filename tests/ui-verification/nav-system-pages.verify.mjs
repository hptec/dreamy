// 逐个打开导航/页脚「系统页」下拉里的每个 page_key 对应路由，验证是否可用。
// 校验维度：HTTP 状态 / 是否重定向 / 页面是否渲染出内容 / 控制台与页面错误 / Next.js 错误页特征
import { chromium } from 'playwright'

const BASE = process.env.STORE_BASE || 'http://localhost:5173'
const LOCALE = process.env.LOCALE || 'en'

const PAGES = [
  ['home', '/'],
  ['products', '/products'],
  ['wedding-dresses', '/wedding-dresses'],
  ['special-occasion', '/special-occasion'],
  ['accessories', '/accessories'],
  ['outdoor-weddings', '/outdoor-weddings'],
  ['real-weddings', '/real-weddings'],
  ['inspiration', '/inspiration'],
  ['blog', '/blog'],
  ['wedding-guides', '/wedding-guides'],
  ['showroom', '/showroom'],
  ['about', '/about'],
  ['contact', '/contact'],
  ['faq', '/faq'],
  ['search', '/search'],
  ['cart', '/cart'],
  ['account-login', '/account/login'],
  ['account-orders', '/account/orders'],
  ['account-wishlist', '/account/wishlist'],
]

const results = []

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })

for (const [key, path] of PAGES) {
  const page = await ctx.newPage()
  const consoleErrors = []
  const pageErrors = []
  const failedRequests = []
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200))
  })
  page.on('pageerror', (e) => pageErrors.push(String(e.message).slice(0, 200)))
  page.on('response', (r) => {
    if (r.status() >= 400 && !r.url().includes('favicon')) {
      failedRequests.push(`${r.status()} ${r.url().replace(BASE, '').slice(0, 120)}`)
    }
  })

  const url = `${BASE}/${LOCALE}${path === '/' ? '' : path}`
  let status = null
  let finalUrl = ''
  let bodyLen = 0
  let title = ''
  let hasNextError = false
  let notFound = false
  let err = null
  try {
    const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 })
    status = resp ? resp.status() : null
    await page.waitForTimeout(2500)
    finalUrl = page.url()
    title = await page.title()
    const body = await page.evaluate(() => document.body?.innerText || '')
    bodyLen = body.trim().length
    notFound = /404|This page could not be found|页面不存在/i.test(body.slice(0, 400))
    hasNextError = /Application error|Unhandled Runtime Error|Internal Server Error/i.test(body.slice(0, 600))
    await page.screenshot({ path: `nav-pages-shots/${key}.png`, fullPage: false })
  } catch (e) {
    err = String(e.message).slice(0, 200)
  }

  const ok = status === 200 && !notFound && !hasNextError && !err && bodyLen > 100 && pageErrors.length === 0
  results.push({ key, path, status, finalUrl: finalUrl.replace(BASE, ''), bodyLen, title, notFound, hasNextError, err, consoleErrors, pageErrors, failedRequests, ok })
  console.log(
    `${ok ? 'PASS' : 'FAIL'}  ${key.padEnd(18)} ${path.padEnd(22)} http=${status} len=${bodyLen} final=${finalUrl.replace(BASE, '')}` +
      (pageErrors.length ? ` pageErr=${pageErrors.length}` : '') +
      (consoleErrors.length ? ` consoleErr=${consoleErrors.length}` : '') +
      (failedRequests.length ? ` reqFail=${failedRequests.length}` : '') +
      (err ? ` EX=${err}` : '')
  )
  await page.close()
}

await browser.close()

console.log('\n===== 明细 =====')
for (const r of results) {
  if (r.ok && !r.consoleErrors.length && !r.failedRequests.length) continue
  console.log(`\n[${r.key}] ${r.path} -> ${r.finalUrl} (http=${r.status}, ok=${r.ok})`)
  if (r.err) console.log('  异常:', r.err)
  if (r.notFound) console.log('  命中 404 文案')
  if (r.hasNextError) console.log('  命中 Next.js 错误页')
  r.pageErrors.forEach((e) => console.log('  pageerror:', e))
  r.consoleErrors.forEach((e) => console.log('  console:', e))
  r.failedRequests.forEach((e) => console.log('  request:', e))
}

const failed = results.filter((r) => !r.ok)
console.log(`\n总计 ${results.length}，通过 ${results.length - failed.length}，失败 ${failed.length}`)
if (failed.length) console.log('失败项:', failed.map((r) => r.key).join(', '))
