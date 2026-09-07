// 验证：order-flow-complete P2-S 消费端全链路
// 登录（OTP stub）→ 加购 → 结算（GB 地址：国家下拉/税费行/Express 选项/ETA/EUR 锁汇文案）→ 下单 → stub 支付确认
// → order-success 首帧已支付 → 订单详情 6 步时间线 + 制作阶段 + 无倒计时 + 税费行 + Buy again → 订单列表制作阶段徽章
// 前置：后端 :18081（STRIPE_MODE=stub）、store dev :5173、商品 celeste-lace-gown 有库存 SKU
import { chromium } from 'playwright'
import { api, loginStore, injectSession, collectConsoleErrors, currentAccessToken, STORE } from './helpers/store-api.mjs'

const EMAIL = process.env.STORE_EMAIL ?? 'fe-verify-store@dreamy.com'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

console.log('[0] API 准备：OTP 登录 / 清地址簿 / 清购物车 / 加购 1 件')
const { tokens } = await loginStore(EMAIL)
let T = tokens.access_token
for (const a of (await api('GET', '/api/store/addresses', { token: T })).data?.items ?? []) await api('DELETE', `/api/store/addresses/${a.id}`, { token: T })
for (const c of (await api('GET', '/api/store/cart', { token: T })).data?.items ?? []) await api('DELETE', `/api/store/cart/items/${c.id}`, { token: T })
const product = (await api('GET', '/api/store/products/celeste-lace-gown')).data
const sku = product.skus.find((s) => (s.stock ?? 0) >= 3) ?? product.skus[0]
const added = await api('POST', '/api/store/cart/items', { token: T, body: { product_id: product.id, sku_id: sku.id, qty: 1 } })
check(added.status === 200 || added.status === 201, `加购成功 sku=${sku.id}`)

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
const page = await context.newPage()
const consoleErrors = collectConsoleErrors(page)
await injectSession(page, tokens, { currency: 'EUR' })

console.log('\n[1] 结算页 · Address 步：GB 地址（国家 ISO 下拉，无州省字典 → state 文本）')
await page.goto(`${STORE}/checkout`, { waitUntil: 'networkidle', timeout: 60000 })
await page.waitForSelector('[data-testid="address-form"]', { timeout: 30000 })
await page.waitForSelector('#addr-country', { timeout: 30000 })
await page.fill('#full-name', 'Verify Tester')
await page.fill('#address', '10 Downing Street')
await page.fill('#city', 'London')
await page.fill('#zip', 'SW1A 2AA')
await page.click('#addr-country')
const gbOpt = page.locator('li[role="option"]', { hasText: /^United Kingdom$/ })
await gbOpt.waitFor({ timeout: 10000 })
await gbOpt.click()
check((await page.locator('#addr-country').innerText()).includes('United Kingdom'), '国家下拉选中 United Kingdom')
check(await page.locator('#state').count() === 1, 'GB 无州省字典 → State 文本框')
check(await page.locator('#addr-region').count() === 0, 'GB 不显示 region 下拉')

console.log('  · US 切换验证 region 下拉出现，再切回 GB')
await page.click('#addr-country')
await page.locator('li[role="option"]', { hasText: /^United States$/ }).click()
await page.waitForSelector('#addr-region', { timeout: 5000 })
check(await page.locator('#addr-region').count() === 1, 'US → 显示 State / Province 下拉')
await page.click('#addr-region')
await page.locator('li[role="option"]', { hasText: /^California$/ }).click()
check((await page.locator('#addr-region').innerText()).includes('California'), 'region 下拉可选 California')
await page.click('#addr-country')
await page.locator('li[role="option"]', { hasText: /^United Kingdom$/ }).click()
await page.waitForSelector('#state', { timeout: 5000 })

await page.click('[data-testid="save-address"]')
await page.waitForSelector('input[name="address"]', { timeout: 15000 })
/** 浏览器已用 refresh 续期且 access 会轮换 → 每次 API 调用前取当前 access */
const tok = async () => (await currentAccessToken(page)) ?? T
const addrs = (await api('GET', '/api/store/addresses', { token: await tok() })).data.items
check(addrs.length === 1 && addrs[0].country_code === 'GB', `地址已创建 country_code=GB（${addrs[0]?.country_code}）`)
check((await page.locator('label:has(input[name="address"])').first().innerText()).includes('(GB)'), '地址卡显示 ISO 码 (GB)')
await page.getByRole('button', { name: 'Continue to Shipping' }).click()

console.log('\n[2] Shipping 步：Standard/Express 选项 + 运输天数 + ETA')
await page.waitForSelector('[data-testid="shipping-option"]', { timeout: 30000 })
const optCount = await page.locator('[data-testid="shipping-option"]').count()
const expressCount = await page.locator('[data-testid="shipping-option"][data-service-level="2"]').count()
check(optCount >= 2, `运费选项 ${optCount} 个`)
check(expressCount >= 1, `含 Express 选项 ${expressCount} 个`)
const firstOpt = await page.locator('[data-testid="shipping-option"]').first().innerText()
check(/business days in transit/.test(firstOpt), '选项显示运输天数')
check(/Est\. delivery/.test(firstOpt), '选项显示预计送达区间')
check(/standard|express/i.test(firstOpt), '选项显示服务等级徽章')
const expressOpt = page.locator('[data-testid="shipping-option"][data-service-level="2"]').first()
const expressText = await expressOpt.innerText()
await expressOpt.locator('input[type="radio"]').check()
await page.waitForTimeout(1200)
check(await expressOpt.locator('input[type="radio"]').isChecked(), `选中 Express：${expressText.split('\n')[0]}`)
check(/Made to order in about \d+ days/.test(await page.locator('main, body').first().innerText()), '显示制作周期提示')
await page.getByRole('button', { name: 'Continue to Payment' }).click()

console.log('\n[3] Payment → Review：税费行 / ETA / EUR 锁汇文案 / 已含税提示')
await page.getByRole('button', { name: 'Review Order' }).click()
await page.waitForSelector('[data-testid="review-eta"]', { timeout: 15000 })
check(await page.locator('[data-testid="tax-row"]').count() === 1, 'Summary 出现 Tax 行')
const taxRowText = await page.locator('[data-testid="tax-row"]').innerText()
check(/€\d/.test(taxRowText), `Tax 金额以 EUR 展示：${taxRowText.replace(/\n/g, ' ')}`)
await page.locator('[data-testid="tax-row"] button').click()
await page.waitForTimeout(300)
check(/VAT/.test(await page.locator('[data-testid="tax-row"]').innerText()), 'Tax 展开显示 VAT 明细')
check(/Est\. delivery/.test(await page.locator('[data-testid="review-eta"]').innerText()), 'Review 显示预计送达')
check(await page.locator('[data-testid="rate-locked-note"]').count() === 1, 'EUR 下显示锁汇说明')
check(/1 USD = [\d.]+ EUR/.test(await page.locator('[data-testid="rate-locked-note"]').innerText()), '锁汇文案格式 1 USD = x EUR')
check(await page.locator('[data-testid="duties-notice"]').count() === 0, 'GB(DDP) 不显示关税提示')
check(/Duties & taxes included/.test(await page.locator('body').innerText()), 'DDP 显示已含税提示')
check(/UPS|FedEx|DHL/.test(await page.locator('body').innerText()) && /Express/.test(await page.locator('body').innerText()), 'Review 显示承运商 + Express')

console.log('\n[4] 下单 → stub 支付确认 → order-success 首帧已支付')
await page.getByRole('button', { name: /^Place Order/ }).click()
await page.waitForSelector('[data-testid="stub-payment-panel"]', { timeout: 30000 })
check(true, '出现测试态支付面板')
await page.click('[data-testid="stub-pay-continue"]')
await page.waitForURL(/\/order-success\?order_id=\d+/, { timeout: 30000 })
const orderId = Number(new URL(page.url()).searchParams.get('order_id'))
check(orderId > 0, `跳转 order-success order_id=${orderId}`)
await page.waitForSelector('[data-state]', { timeout: 15000 })
const firstState = await page.locator('[data-state]').first().getAttribute('data-state')
await page.waitForSelector('[data-state="paid"]', { timeout: 10000 })
check(firstState === 'paid' || firstState === 'polling', `首帧状态=${firstState}（确认端点同步落账，polling 首轮即 paid）`)
check(/Thank you!/.test(await page.locator('h1').innerText()), '成功态标题 Thank you!')
check(/Estimated delivery/.test(await page.locator('body').innerText()), '成功页显示预计送达')
const orderApi = (await api('GET', `/api/store/orders/${orderId}`, { token: await tok() })).data
check(orderApi.status === 2 && orderApi.production_stage === 1, `后端订单 status=2 production_stage=${orderApi.production_stage}`)
check(orderApi.shipping_service_level === 2 && Number(orderApi.tax_amount) > 0 && orderApi.amount_version === 2, `订单 service_level=2 tax=${orderApi.tax_amount} v${orderApi.amount_version}`)

console.log('\n[5] 订单详情：6 步时间线 / 制作阶段 / 无倒计时 / 税费行 / 动作按钮')
await page.getByRole('link', { name: 'Track My Order' }).click()
await page.waitForSelector('[data-testid="order-timeline"]', { timeout: 30000 })
check(await page.locator('[data-testid^="timeline-step-"]').count() === 6, '时间线 6 步')
check((await page.locator('[data-testid="order-timeline"]').getAttribute('data-done')) === '2', '已到达 Placed + Paid（done=2）')
const tl = await page.locator('[data-testid="order-timeline"]').innerText()
check(/Placed[\s\S]*Paid[\s\S]*In production[\s\S]*Shipped[\s\S]*Delivered[\s\S]*Completed/.test(tl), '步骤顺序 Placed→Paid→In production→Shipped→Delivered→Completed')
check(await page.locator('[data-testid="production-stages"]').count() === 1, 'PAID 态显示制作阶段区块')
check((await page.locator('[data-testid="production-stages"]').getAttribute('data-stage')) === '1', '制作阶段 = 1 Pending review')
check(/Pending review[\s\S]*In production[\s\S]*Quality check[\s\S]*Ready to ship/.test(await page.locator('[data-testid="production-stages"]').innerText()), '制作阶段 4 小步文案')
check(await page.locator('[data-testid="payment-countdown"]').count() === 0, '已支付不显示倒计时')
check(await page.locator('[data-testid="order-tax-row"]').count() === 1, '详情金额拆分含 Tax 行')
check(await page.locator('[data-testid="order-refunded-row"]').count() === 0, '未退款不显示 Refunded 行')
check(await page.locator('[data-testid="order-eta"]').count() === 1, '详情显示预计送达')
check(await page.locator('[data-testid="buy-again"]').count() === 1, 'PAID 态显示 Buy again')
check(await page.locator('[data-testid="confirm-delivery"]').count() === 0, 'PAID 态不显示 Confirm delivery')
check(await page.locator('[data-testid="order-activity"]').count() === 1, '显示 Order activity')
check(/Payment received/.test(await page.locator('[data-testid="order-activity"]').innerText()), 'Activity 含 Payment received 事件')
check((await page.locator('[data-testid="order-status-badge"]').innerText()).trim() === 'Paid', '状态徽章 Paid')

console.log('  · Buy again → toast added_count')
await page.click('[data-testid="buy-again"]')
await page.waitForSelector('[data-testid="reorder-toast"]', { timeout: 15000 })
check(/1 items added to your cart/.test(await page.locator('[data-testid="reorder-toast"]').innerText()), 'toast 显示 1 items added')
const cartAfter = (await api('GET', '/api/store/cart', { token: await tok() })).data.items
check(cartAfter.length === 1, '购物车回填 1 行')

console.log('\n[6] 订单列表：Delivered chip + 制作阶段徽章 + ETA')
await page.goto(`${STORE}/account/orders`, { waitUntil: 'networkidle', timeout: 60000 })
try {
  await page.waitForSelector(`text=Order ${orderApi.order_no}`, { timeout: 30000 })
} catch {
  await page.screenshot({ path: '/tmp/order-flow-store-orders.png', fullPage: true })
  console.log('  [debug] 列表未见订单，截图 /tmp/order-flow-store-orders.png；body=', (await page.locator('body').innerText()).slice(0, 600).replace(/\n+/g, ' | '))
}
check(await page.getByRole('button', { name: 'Delivered', exact: true }).count() === 1, '筛选 chips 含 Delivered')
check(/Pending review/.test(await page.locator('[data-testid="production-badge"]').first().innerText()), '卡片显示制作阶段徽章')
check(/Est\. delivery/.test(await page.locator('body').innerText()), '卡片显示预计送达')

console.log('\n[7] 待支付订单：倒计时展示（新建第二单不付款）')
const pendingCreate = await api('POST', '/api/store/checkout/orders', {
  token: await tok(),
  body: { idempotency_key: `verify-pending-${Date.now()}`, address_id: addrs[0].id, currency: 'USD', carrier_code: 'UPS', service_level: 1, payment_method: 'Stripe', locale: 'en' }
})
check(pendingCreate.status === 200 || pendingCreate.status === 201, '第二单创建（PENDING）')
const pendingId = pendingCreate.data?.order?.id
if (pendingId) {
  await page.goto(`${STORE}/account/orders/${pendingId}`, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForSelector('[data-testid="payment-countdown"]', { timeout: 30000 })
  const cd = await page.locator('[data-testid="payment-countdown"]').innerText()
  check(/Complete payment within \d{2}:\d{2}/.test(cd), `倒计时 mm:ss：${cd}`)
  check((await page.locator('[data-testid="payment-countdown"]').getAttribute('data-expired')) === 'false', '倒计时未过期')
  check(await page.getByRole('button', { name: 'Pay now' }).count() === 1, 'PENDING 显示 Pay now')
  check(await page.getByRole('button', { name: /Cancel order/ }).count() === 1, 'PENDING 显示 Cancel order')
  // 取消，避免遗留 pending 单
  await api('POST', `/api/store/orders/${pendingId}/cancel`, { token: await tok() })
}

console.log('\n[8] 页面 console 零错误')
check(consoleErrors.length === 0, `console error 数=${consoleErrors.length}${consoleErrors.length ? '：' + consoleErrors[0] : ''}`)
if (consoleErrors.length) consoleErrors.forEach((e) => console.log('    -', e))

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length}` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
