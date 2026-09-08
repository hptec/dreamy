// 验证：结算页 wedding date 交互修复
// 修复回归：日期框 min=今天（选择器不可选过去日期）；手动键入过去日期 → 字段就近展示
// "Wedding date must be today or later."、报价请求剔除非法日期（不再触发 422601 通用横幅）、
// 下单按钮前置拦截非法日期。
// 用法：node wedding-date-guard.verify.mjs（默认 dev 环境 :5173/:18081）
import { chromium } from 'playwright'
import { api, loginStore, injectSession, collectConsoleErrors, STORE } from './helpers/store-api.mjs'

const EMAIL = process.env.STORE_EMAIL ?? 'fe-verify-store@dreamy.com'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const todayIso = (() => {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
})()

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
page.on('request', (req) => {
  if (req.method() === 'POST' && req.url().includes('/api/store/checkout/quote')) {
    try { quoteBodies.push(req.postDataJSON()) } catch { /* ignore */ }
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

console.log('\n[2] wedding date 守卫断言')
const minAttr = await page.getAttribute('#wedding-date', 'min')
check(minAttr === todayIso, `日期框 min=今天（期望 ${todayIso}，实际 ${minAttr}）`)

// 手动键入过去日期（fill 绕过选择器限制，模拟手输）
await page.fill('#wedding-date', '2020-01-01')
await page.waitForTimeout(800)
const errText = (await page.locator('#wedding-date + p').innerText().catch(() => '')).trim()
check(/today or later/i.test(errText), `就近展示字段错误（实际: "${errText}"）`)
const ariaInvalid = await page.getAttribute('#wedding-date', 'aria-invalid')
check(ariaInvalid === 'true', `aria-invalid=true（实际: ${ariaInvalid}）`)

// 报价不被 422601 阻断：等待报价横幅（应不出现）+ 订单摘要可见
await page.waitForTimeout(1500)
const banner = await page.locator('p', { hasText: /check the highlighted fields/i }).count()
check(banner === 0, '无 "check the highlighted fields" 通用横幅')
const lastQuote = quoteBodies[quoteBodies.length - 1]
check(lastQuote && !('wedding_date' in lastQuote) && lastQuote.wedding_date === undefined, '报价请求剔除非法 wedding_date')

// 修正为合法日期 → 错误消失
const future = (() => {
  const n = new Date(); n.setFullYear(n.getFullYear() + 1)
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
})()
await page.fill('#wedding-date', future)
await page.waitForTimeout(800)
check((await page.locator('#wedding-date + p').count()) === 0, '改回合法日期 → 字段错误消失')

console.log('\n[3] 非法日期 + 下单拦截')
await page.fill('#wedding-date', '2020-01-01')
await page.waitForTimeout(500)
await page.locator('button', { hasText: /continue to payment/i }).click()
await page.locator('button', { hasText: /review order/i }).click()
await page.locator('button', { hasText: /place order/i }).click()
await page.waitForTimeout(1000)
const placeErr = await page.locator('p', { hasText: /today or later/i }).last().innerText().catch(() => '')
check(/today or later/i.test(placeErr), `Place Order 被拦截并提示（实际: "${placeErr}"）`)
check(!page.url().includes('/account/orders/'), '未跳转订单页（下单未发出）')

const errs = consoleErrors.filter((e) => !e.includes('[server-fetch] API_ORIGIN'))
check(errs.length === 0, `console 干净（${errs.length} 条）`)

await context.close()
await browser.close()
console.log(`\n===== ${failures.length === 0 ? 'ALL PASS' : `FAIL x${failures.length}`} =====`)
failures.forEach((f) => console.log(` - ${f}`))
process.exit(failures.length === 0 ? 0 : 1)
