// 验证：结算页 wedding date 完全放行（V-TRD-019 2026-09-08 放开）
// 口径：过去日期（补拍/纪念日购买场景）可自由填写，随报价与下单请求原样提交，
// 无 min 限制、无字段报错、无 UI 前置拦截、后端不再 422601 拒绝。
// 用法：node wedding-date-guard.verify.mjs（默认 dev 环境 :5173/:18081）
import { chromium } from 'playwright'
import { api, loginStore, injectSession, collectConsoleErrors, STORE } from './helpers/store-api.mjs'

const EMAIL = process.env.STORE_EMAIL ?? 'fe-verify-store@dreamy.com'
const PAST_DATE = '2020-01-01'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

console.log('[0] API 准备：OTP 登录 / 清地址簿 / 清购物车 / 加购 1 件')
const { tokens } = await loginStore(EMAIL)
const T = tokens.access_token
for (const a of (await api('GET', '/api/store/addresses', { token: T })).data?.items ?? []) await api('DELETE', `/api/store/addresses/${a.id}`, { token: T })
for (const c of (await api('GET', '/api/store/cart', { token: T })).data?.items ?? []) await api('DELETE', `/api/store/cart/items/${c.id}`, { token: T })
const product = (await api('GET', '/api/store/products/celeste-lace-gown')).data
const sku = product.skus.find((s) => (s.stock ?? 0) >= 1) ?? product.skus[0]
const added = await api('POST', '/api/store/cart/items', { token: T, body: { product_id: product.id, sku_id: sku.id, qty: 1 } })
check(added.status === 200 || added.status === 201, `加购成功 sku=${sku.id}`)

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
const page = await context.newPage()
const consoleErrors = collectConsoleErrors(page)
const quoteBodies = []
let orderStatus = null
page.on('request', (req) => {
  if (req.method() === 'POST' && req.url().includes('/api/store/checkout/quote')) {
    try { quoteBodies.push(req.postDataJSON()) } catch { /* ignore */ }
  }
})
page.on('response', async (res) => {
  if (res.request().method() === 'POST' && res.url().includes('/api/store/checkout/orders')) {
    orderStatus = res.status()
  }
})
await injectSession(page, tokens)

console.log('\n[1] 结算页 · Address 步：GB 地址')
await page.goto(`${STORE}/checkout`, { waitUntil: 'networkidle', timeout: 60000 })
await page.waitForSelector('[data-testid="address-form"]', { timeout: 30000 })
await page.fill('#full-name', 'Verify Tester')
await page.fill('#address', '10 Downing Street')
await page.fill('#city', 'London')
await page.fill('#zip', 'SW1A 2AA')
await page.click('#addr-country')
const gbOpt = page.locator('li[role="option"]', { hasText: /^United Kingdom$/ })
await gbOpt.waitFor({ timeout: 10000 })
await gbOpt.click()
await page.fill('#state', 'London')
await page.click('[data-testid="save-address"]')
await page.waitForSelector('input[name="address"]', { timeout: 15000 })
await page.locator('button', { hasText: /continue to shipping/i }).click()
await page.waitForSelector('#wedding-date', { timeout: 15000 })

console.log('\n[2] wedding date 完全放行断言')
const minAttr = await page.getAttribute('#wedding-date', 'min')
check(minAttr === null, `日期框无 min 限制（实际: ${minAttr}）`)

// 填过去日期 → 无字段报错、无 aria-invalid、无通用横幅
await page.fill('#wedding-date', PAST_DATE)
await page.waitForTimeout(1800)
check((await page.locator('#wedding-date + p').count()) === 0, '无就近字段错误提示')
const ariaInvalid = await page.getAttribute('#wedding-date', 'aria-invalid')
check(ariaInvalid === null, `无 aria-invalid（实际: ${ariaInvalid}）`)
const banner = await page.locator('p', { hasText: /check the highlighted fields/i }).count()
check(banner === 0, '无 "check the highlighted fields" 通用横幅')

// 报价请求原样携带过去日期（不再剔除）且报价成功（订单摘要可见）
const lastQuote = quoteBodies[quoteBodies.length - 1]
check(lastQuote?.wedding_date === PAST_DATE, `报价请求原样携带过去日期（实际: ${lastQuote?.wedding_date}）`)
const summaryVisible = await page.locator('h3', { hasText: /^summary$/i }).first().isVisible().catch(() => false)
check(summaryVisible, '订单摘要可见（报价未被 422601 阻断）')

console.log('\n[3] 过去日期 · 下单不被拦截')
await page.locator('button', { hasText: /continue to payment/i }).click()
await page.locator('button', { hasText: /review order/i }).click()
await page.locator('button', { hasText: /place order/i }).click()
await page.waitForTimeout(2500)
const placeErr = await page.locator('p', { hasText: /today or later/i }).count()
check(placeErr === 0, '无 "today or later" 拦截提示')
check(orderStatus === 201 || orderStatus === 200, `createOrder 请求发出且成功（status: ${orderStatus}）`)

const errs = consoleErrors.filter((e) => !e.includes('[server-fetch] API_ORIGIN'))
check(errs.length === 0, `console 干净（${errs.length} 条）`)

await context.close()
await browser.close()
console.log(`\n===== ${failures.length === 0 ? 'ALL PASS' : `FAIL x${failures.length}`} =====`)
failures.forEach((f) => console.log(` - ${f}`))
process.exit(failures.length === 0 ? 0 : 1)
