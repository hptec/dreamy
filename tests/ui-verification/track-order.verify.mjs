// 验证：游客查单页 /track-order（order-flow-complete D/I）
// API 准备：OTP 登录 → GB 地址 → 加购 → 下单 → stub 支付确认 → 后台发货 + 手工轨迹（订单 SHIPPED）
// UI：订单号 + 邮箱查单成功（状态/时间线/包裹卡/轨迹/活动）→ 再查一次错误邮箱 404 提示 → console 零错误
// 前置：后端 :18081（STRIPE_MODE=stub）、store dev :5173；游客查单频控 10 次/小时/IP（本脚本消耗 2 次）
import { chromium } from 'playwright'
import { api, loginStore, adminLogin, collectConsoleErrors, STORE } from './helpers/store-api.mjs'

const EMAIL = process.env.STORE_EMAIL ?? 'fe-verify-track@dreamy.com'
const RUN = Date.now()
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

console.log('[0] API 准备：登录 / GB 地址 / 加购 / 下单 / 确认支付 / 后台发货 + 轨迹')
const { tokens } = await loginStore(EMAIL)
const T = tokens.access_token
for (const c of (await api('GET', '/api/store/cart', { token: T })).data?.items ?? []) await api('DELETE', `/api/store/cart/items/${c.id}`, { token: T })
const addr = await api('POST', '/api/store/addresses', { token: T, body: { receiver: 'Track Tester', line: '1 Bridal Lane', city: 'London', zip: 'SW1A 1AA', country: 'United Kingdom', country_code: 'GB', is_default: true } })
check(addr.status === 200 || addr.status === 201, `GB 地址创建 id=${addr.data?.id}`)
const product = (await api('GET', '/api/store/products/celeste-lace-gown')).data
const sku = product.skus.find((s) => (s.stock ?? 0) >= 3) ?? product.skus[0]
await api('POST', '/api/store/cart/items', { token: T, body: { product_id: product.id, sku_id: sku.id, qty: 1 } })
const created = await api('POST', '/api/store/checkout/orders', {
  token: T,
  body: { idempotency_key: `verify-track-${RUN}`, address_id: addr.data.id, currency: 'USD', carrier_code: 'DHL', service_level: 1, payment_method: 'Stripe', locale: 'en' }
})
check(created.status === 200 || created.status === 201, `下单 order_no=${created.data?.order?.order_no}`)
const orderId = created.data.order.id
const orderNo = created.data.order.order_no
const confirmed = await api('POST', `/api/store/orders/${orderId}/payment/confirm`, { token: T })
check(confirmed.status === 200 && confirmed.data?.status === 2, 'stub 支付确认 → PAID')
const AT = await adminLogin()
await api('PATCH', `/api/admin/orders/${orderId}/production-stage`, { token: AT, body: { stage: 2 } })
const ship = await api('POST', `/api/admin/orders/${orderId}/shipments`, { token: AT, body: { carrier_code: 'DHL', tracking_no: `DHL-TRK-${RUN}` } })
check(ship.status === 201 || ship.status === 200, `后台发货 shipment=${ship.data?.shipment_no}`)
const ev = await api('POST', `/api/admin/shipments/${ship.data.id}/events`, { token: AT, body: { status: 2, location: 'Hong Kong', description: 'Departed origin facility' } })
check(ev.status === 201 || ev.status === 200, '手工轨迹 IN_TRANSIT')

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const consoleErrors = collectConsoleErrors(page)
await page.addInitScript(() => { localStorage.setItem('dreamy_cookie_consent', 'denied'); sessionStorage.setItem('dreamy_newsletter_seen', '1') })

console.log('\n[1] 游客查单成功')
await page.goto(`${STORE}/track-order`, { waitUntil: 'networkidle', timeout: 60000 })
await page.waitForSelector('[data-testid="track-form"]', { timeout: 30000 })
check(/Track Your Order/.test(await page.locator('h1').innerText()), '页面标题 Track Your Order')
await page.fill('#track-order-no', orderNo.toLowerCase())
await page.fill('#track-email', EMAIL)
await page.click('[data-testid="track-submit"]')
await page.waitForSelector('[data-testid="track-result"]', { timeout: 30000 })
check((await page.locator('[data-testid="track-status"]').getAttribute('data-status')) === '3', '状态 = SHIPPED(3)')
check((await page.locator('[data-testid="track-status"]').innerText()).trim() === 'Shipped', '状态徽章 Shipped')
check(await page.locator('[data-testid^="timeline-step-"]').count() === 6, '6 步时间线')
check((await page.locator('[data-testid="order-timeline"]').getAttribute('data-done')) === '4', '已到达 Placed/Paid/In production/Shipped（done=4）')
check(await page.locator('[data-testid="production-stages"]').count() === 0, 'SHIPPED 不显示制作阶段区块')
check(await page.locator('[data-testid="shipment-card"]').count() === 1, '包裹卡 1 个')
const card = await page.locator('[data-testid="shipment-card"]').innerText()
check(/DHL/.test(card) && card.includes(`DHL-TRK-${RUN}`), '包裹卡显示承运商 + 单号')
check(/In transit/.test(card), '包裹状态 In transit')
check(/Departed origin facility/.test(card) && /Hong Kong/.test(card), '轨迹事件描述 + 地点')
check(await page.locator('[data-testid="shipment-card"] a[href*="dhl.com"]').count() === 1, 'tracking_url 外链（dhl.com）')
check(/Celeste/.test(card), '包裹行明细含商品名')
check(await page.locator('[data-testid="order-activity"]').count() === 1, '显示 Order activity')
const bodyText = await page.locator('body').innerText()
check(/Track T\./.test(bodyText) || /T\./.test(bodyText), '收件人脱敏展示')
check(/United Kingdom/.test(bodyText), '国家名展示（United Kingdom）')
check(!/1 Bridal Lane/.test(bodyText), '不泄露地址明细')
check(/\$\d/.test(bodyText), '金额展示')

console.log('\n[2] 错误邮箱 → 404 提示')
await page.click('[data-testid="track-again"]')
await page.waitForSelector('[data-testid="track-form"]', { timeout: 10000 })
await page.fill('#track-order-no', orderNo)
await page.fill('#track-email', 'nobody@example.com')
await page.click('[data-testid="track-submit"]')
await page.waitForSelector('[data-testid="track-error"]', { timeout: 30000 })
check(/couldn't find an order/.test(await page.locator('[data-testid="track-error"]').innerText()), '404 → 未找到匹配订单提示')
check(await page.locator('[data-testid="track-result"]').count() === 0, '错误时不展示结果区')

console.log('\n[3] 前端预校验（不发请求）')
await page.fill('#track-order-no', '')
await page.fill('#track-email', 'bad')
await page.click('[data-testid="track-submit"]')
await page.waitForTimeout(300)
check(/valid email/.test(await page.locator('[data-testid="track-error"]').innerText()), '无效输入行内提示')

console.log('\n[4] console 零错误')
check(consoleErrors.length === 0, `console error 数=${consoleErrors.length}${consoleErrors.length ? '：' + consoleErrors[0] : ''}`)
if (consoleErrors.length) consoleErrors.forEach((e) => console.log('    -', e))

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length}` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
