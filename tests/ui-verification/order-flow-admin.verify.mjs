// 验证：order-flow-complete P2-A 后台订单流程 —— 预置已支付订单 → 制作阶段推进 → 部分发货 → 轨迹 → 剩余发货（SHIPPED）
// → 两包裹签收（DELIVERED）→ 顾客可见备注 → 部分退款 1.00 → 各面板渲染断言；列表新筛选/新列；页面 console 零错误
// 前置：后端 :18081（STRIPE_MODE=stub）、portal-admin :5174、商品 celeste-lace-gown 有库存 ≥2
// 用法：node tests/ui-verification/order-flow-admin.verify.mjs
import { chromium } from 'playwright'
import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ADMIN = 'http://localhost:5174'
const API = 'http://localhost:18081'
const LOG_FILE = process.env.OTP_LOG || resolve(dirname(fileURLToPath(import.meta.url)), '../../logs/identity.log')
const RUN = Date.now().toString(36)
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function api(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body == null ? undefined : JSON.stringify(body),
  })
  let json = null
  try { json = await res.json() } catch { /* 204 */ }
  return { status: res.status, body: json }
}

// ---------- [0] 预置一张已支付订单（qty=2 便于部分发货） ----------
console.log('[0] 预置已支付订单（stub 支付闭环）')
const adminLogin = await api('POST', '/api/admin/auth/login', { email: 'admin@dreamy.com', password: 'Admin@123456' })
check(adminLogin.status === 200, '管理员 API 登录')
const ADMIN_TOKEN = adminLogin.body?.data?.token

async function presetOrder() {
  const email = 'admin-flow-verify@dreamy.com'
  let send = await api('POST', '/api/store/auth/otp/send', { email, locale: 'en' })
  if (send.status === 429) {
    const wait = Number(send.body?.data?.retry_after_seconds || 61)
    console.log(`  (OTP 频控 429，等待 ${wait}s)`)
    await sleep(wait * 1000 + 500)
    send = await api('POST', '/api/store/auth/otp/send', { email, locale: 'en' })
  }
  if (send.status !== 200) throw new Error(`otp send ${send.status}`)
  await sleep(800)
  const log = readFileSync(LOG_FILE, 'utf8')
  // stub 日志收件人脱敏（a***@dreamy.com），按首字母 + code=otp 定位最后一条
  const lines = log.split('\n').filter((l) => l.includes('[MAIL-STUB]') && l.includes('code=otp') && l.includes(`to=${email[0]}***@`))
  const last = lines[lines.length - 1] || ''
  const code = (last.match(/vars=\{code=(\d{4,8})/) || [])[1]
  if (!code) throw new Error('otp code not found in log')
  const verify = await api('POST', '/api/store/auth/otp/verify', { email, code })
  if (verify.status !== 200) throw new Error(`otp verify ${verify.status}`)
  const token = verify.body.data.tokens.access_token
  const product = await api('GET', '/api/store/products/celeste-lace-gown')
  const sku = (product.body.data.skus || []).find((s) => s.stock >= 3)
  if (!sku) throw new Error('no sku with stock>=3')
  const addr = await api('POST', '/api/store/addresses', {
    receiver: 'Admin Flow', phone: '+1 555 0100', line: '7 Verify Ave', city: 'Austin', state: 'TX', zip: '73301', country: 'US', is_default: true,
  }, token)
  const addrId = addr.body.data.id
  const cart = await api('GET', '/api/store/cart', null, token)
  for (const it of cart.body?.data?.items || []) await api('DELETE', `/api/store/cart/items/${it.id}`, null, token)
  const add = await api('POST', '/api/store/cart/items', { product_id: product.body.data.id, sku_id: sku.id, qty: 2 }, token)
  if (![200, 201].includes(add.status)) throw new Error(`cart add ${add.status}`)
  const quote = await api('POST', '/api/store/checkout/quote', { address_id: addrId, currency: 'USD' }, token)
  const opt = quote.body.data.shipping_options[0]
  const order = await api('POST', '/api/store/checkout/orders', {
    idempotency_key: `admin-verify-${RUN}`, address_id: addrId, currency: 'USD', carrier: opt.carrier, carrier_code: opt.carrier_code,
    service_level: opt.service_level, payment_method: 'Stripe', locale: 'en',
  }, token)
  if (![200, 201].includes(order.status)) throw new Error(`order ${order.status} ${JSON.stringify(order.body).slice(0, 200)}`)
  const orderId = order.body.data.order.id
  const confirm = await api('POST', `/api/store/orders/${orderId}/payment/confirm`, null, token)
  if (confirm.status !== 200) throw new Error(`confirm ${confirm.status}`)
  return { orderId, orderNo: order.body.data.order.order_no, fresh: true }
}

let target
try {
  target = await presetOrder()
  check(true, `预置订单 #${target.orderId} ${target.orderNo}（status=2, qty=2）`)
} catch (e) {
  console.log('  (预置失败，回退到既有 status=2 且无包裹的订单)', e.message)
  const list = await api('GET', '/api/admin/orders?status=2&has_shipment=false&page_size=20', null, ADMIN_TOKEN)
  const cand = (list.body?.data?.data || []).find((o) => (o.item_count || 0) >= 2)
  check(!!cand, '回退：找到 status=2、无包裹、商品数≥2 的订单')
  target = cand ? { orderId: cand.id, orderNo: cand.order_no, fresh: false } : null
}
if (!target) { console.log('FAILED: no order'); process.exit(1) }
const { orderId } = target

// ---------- 浏览器 ----------
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const consoleErrors = []
// 负向用例（422907 重叠 / 409905 手工模式）会让 Chromium 打印 "Failed to load resource: 4xx" 网络日志，非 JS 错误，排除；5xx/其他一律计入
page.on('console', (m) => {
  if (m.type() !== 'error') return
  const t = m.text()
  if (/Failed to load resource: the server responded with a status of 4\d\d/.test(t)) return
  consoleErrors.push(t.slice(0, 200))
})
page.on('pageerror', (e) => consoleErrors.push('PAGEERROR ' + e.message))

console.log('[1] 登录并打开订单详情')
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForTimeout(2000)
await page.goto(`${ADMIN}/orders/${orderId}`)
await page.waitForSelector('[data-testid="order-status-panel"]', { timeout: 15000 })
await page.waitForTimeout(800)
check((await page.locator('[data-testid="order-actions"]').innerText()).includes('已付款/制作中'), '操作栏显示状态「已付款/制作中」')
check(await page.locator('[data-testid="production-stepper"]').isVisible(), 'PAID 态渲染制作阶段步进器')
check(await page.locator('[data-testid="order-amounts"]').isVisible(), '金额拆分面板渲染')
check((await page.locator('[data-testid="order-amounts"]').innerText()).includes('税费'), '金额拆分含税费行（amount_version=2）')
check((await page.locator('[data-testid="order-eta"]').innerText()).includes('~'), '预计送达区间渲染')
const addrText = (await page.locator('[data-testid="order-address"]').innerText()).replace(/\s+/g, ' ')
check(/ISO： ?US/.test(addrText) && addrText.includes('TX'), '地址快照显示 country_code / region_code')
check(await page.locator('[data-testid="action-cancel-paid"]').isVisible(), 'PAID 态显示「取消订单（自动全额退款）」')
check(await page.locator('[data-testid="action-refund"]').isVisible(), 'PAID 态显示「部分 / 全额退款」')

console.log('[2] 制作阶段推进一档')
const stageBefore = await page.locator('[data-testid="production-stage-current"]').innerText()
const nextBtn = page.locator('[data-testid="production-next"]')
if (await nextBtn.isDisabled()) {
  await page.click('[data-testid="production-prev"]')
  await page.waitForTimeout(1200)
}
await page.click('[data-testid="production-next"]')
await page.waitForTimeout(1500)
const stageAfter = await page.locator('[data-testid="production-stage-current"]').innerText()
check(stageAfter !== stageBefore || stageBefore === '待发货', `制作阶段推进：${stageBefore} → ${stageAfter}`)
if (target.fresh) check(stageAfter === '制作中', '新订单 1→2 后当前为「制作中」')
check((await page.locator('[data-testid="order-events"]').innerText()).includes('制作'), '时间线出现制作阶段事件')

console.log('[3] 创建包裹（部分 1 件）')
await page.click('[data-testid="open-shipment-drawer"]')
await page.waitForSelector('[data-testid="shipment-submit"]', { timeout: 5000 })
const qtyInputs = page.locator('input[data-testid^="shipment-line-qty-"]')
const lineCount = await qtyInputs.count()
check(lineCount >= 1, `抽屉按行渲染数量输入（${lineCount} 行）`)
// 首行分配 1，其余行清零 → 部分发货
await qtyInputs.nth(0).fill('1')
for (let i = 1; i < lineCount; i++) await qtyInputs.nth(i).fill('0')
await page.fill('[data-testid="shipment-tracking-no"]', `ADM-${RUN}-1`)
await page.click('[data-testid="shipment-submit"]')
await page.waitForTimeout(1800)
let cards = page.locator('[data-testid^="shipment-card-"]')
check((await cards.count()) === 1, '包裹面板出现 1 个包裹')
check((await cards.first().innerText()).includes('待揽收'), '包裹状态徽章「待揽收」')
check((await cards.first().innerText()).includes(`ADM-${RUN}-1`), '包裹显示运单号')
check((await page.locator('[data-testid="order-actions"]').innerText()).includes('已付款/制作中'), '部分发货后订单仍为 PAID')
check((await page.locator('[data-testid="order-shipments-panel"]').innerText()).includes('未发'), '包裹面板显示未发件数')

console.log('[4] 添加轨迹事件')
await cards.first().getByRole('button', { name: /轨迹/ }).first().click()
await page.waitForSelector('[data-testid="shipment-event-form"]', { timeout: 5000 })
await page.fill('[data-testid="shipment-event-location"]', 'Shanghai Hub')
await page.fill('[data-testid="shipment-event-desc"]', `Departed facility ${RUN}`)
await page.click('[data-testid="shipment-event-submit"]')
await page.waitForTimeout(1500)
cards = page.locator('[data-testid^="shipment-card-"]')
const card1 = await cards.first().innerText()
check(card1.includes(`Departed facility ${RUN}`), '轨迹列表出现新事件描述')
check(card1.includes('Shanghai Hub'), '轨迹显示地点')
check(card1.includes('运输中'), '包裹状态推进为「运输中」')

console.log('[5] 创建剩余包裹 → 订单 SHIPPED')
await page.click('[data-testid="open-shipment-drawer"]')
await page.waitForSelector('[data-testid="shipment-submit"]', { timeout: 5000 })
await page.fill('[data-testid="shipment-tracking-no"]', `ADM-${RUN}-2`)
await page.click('[data-testid="shipment-submit"]')
await page.waitForTimeout(2000)
cards = page.locator('[data-testid^="shipment-card-"]')
check((await cards.count()) === 2, '包裹面板出现 2 个包裹')
check((await page.locator('[data-testid="order-actions"]').innerText()).includes('已发货'), '全部发出后订单状态「已发货」')
check(await page.locator('[data-testid="action-delivered"]').isVisible(), 'SHIPPED 态显示「标记送达」')
check(!(await page.locator('[data-testid="production-stepper"]').isVisible().catch(() => false)), 'SHIPPED 态不再显示制作阶段步进器')

console.log('[6] 两包裹标记签收 → 订单 DELIVERED')
for (let i = 0; i < 2; i++) {
  const card = page.locator('[data-testid^="shipment-card-"]').filter({ hasText: '标记签收' }).first()
  await card.getByRole('button', { name: /标记签收/ }).click()
  await page.waitForTimeout(400)
  await page.getByRole('button', { name: '确认签收' }).click()
  await page.waitForTimeout(1800)
}
cards = page.locator('[data-testid^="shipment-card-"]')
const allDelivered = (await cards.allInnerTexts()).every((t) => t.includes('已签收'))
check(allDelivered, '两个包裹均显示「已签收」')
check((await page.locator('[data-testid="order-actions"]').innerText()).includes('已签收'), '订单状态聚合为「已签收」')
check(await page.locator('[data-testid="action-complete"]').isVisible(), 'DELIVERED 态显示「标记完成」')
const tl = await page.locator('[data-testid="order-status-panel"]').innerText()
check(tl.includes('已签收') && tl.includes('已发货'), '6 步时间线显示已发货/已签收节点')

console.log('[7] 添加顾客可见备注')
await page.fill('[data-testid="order-note-input"]', `Verify note ${RUN}`)
await page.locator('[data-testid="order-timeline-panel"] [role="switch"]').click()
await page.click('[data-testid="order-note-submit"]')
await page.waitForTimeout(1500)
const events = await page.locator('[data-testid="order-events"]').innerText()
check(events.includes(`Verify note ${RUN}`), '时间线出现备注内容')
check(events.includes('顾客可见'), '备注带「顾客可见」标识')
check(events.includes('管理员'), '事件显示 actor 管理员')

console.log('[8] 部分退款 1.00')
await page.click('[data-testid="action-refund"]')
await page.waitForSelector('[data-testid="refund-dialog"]', { timeout: 5000 })
const defaultAmount = await page.locator('[data-testid="refund-amount"]').inputValue()
check(Number(defaultAmount) > 1, `退款金额默认剩余可退（${defaultAmount}）`)
await page.fill('[data-testid="refund-amount"]', '1.00')
await page.fill('[data-testid="refund-reason"]', `partial refund verify ${RUN}`)
await page.click('[data-testid="refund-submit"]')
await page.waitForTimeout(2000)
check(await page.locator('[data-testid="order-refunds-panel"]').isVisible(), '关联退款工单面板渲染')
const refundRow = await page.locator('[data-testid="order-refunds-panel"]').innerText()
check(refundRow.includes('1.00') && refundRow.includes('待审批'), '退款工单行显示 $1.00 待审批')
check((await page.locator('[data-testid="order-actions"]').innerText()).includes('退款中'), '订单状态「退款中」')
check(await page.locator('[data-testid="order-abnormal"]').isVisible(), '时间线异常分支提示（退款中）')
check((await page.locator('[data-testid="order-events"]').innerText()).includes('退款'), '时间线出现退款事件')

console.log('[9] 订单列表新筛选/新列')
await page.goto(`${ADMIN}/orders?status=6`)
await page.waitForSelector('[data-testid="orders-table"]', { timeout: 15000 })
await page.waitForTimeout(1200)
const headers = await page.locator('[data-testid="orders-table"] thead').innerText()
check(headers.includes('制作阶段') && headers.includes('婚期'), '列表表头含制作阶段/婚期列')
check(await page.locator('[data-testid="filter-production-stage"]').isVisible(), '制作阶段筛选渲染')
check(await page.locator('[data-testid="filter-wedding-before"]').isVisible(), '婚期早于筛选渲染')
check(await page.locator('[data-testid="filter-has-shipment"]').isVisible(), '有无包裹筛选渲染')
check((await page.locator('[data-testid="orders-table"]').innerText()).includes(target.orderNo), '退款中 tab 列出目标订单')
// has_shipment=yes 服务端筛选
await page.click('[data-testid="filter-has-shipment"] button')
await page.getByRole('option', { name: '已创建包裹' }).click()
await page.waitForTimeout(1500)
check((await page.locator('[data-testid="orders-table"]').innerText()).includes(target.orderNo), '「已创建包裹」筛选仍包含目标订单')
await page.click('[data-testid="filter-has-shipment"] button')
await page.getByRole('option', { name: '尚无包裹' }).click()
await page.waitForTimeout(1500)
check(!(await page.locator('[data-testid="orders-table"]').innerText()).includes(target.orderNo), '「尚无包裹」筛选排除目标订单')
const deliveredTabResp = await api('GET', `/api/admin/orders?status=8&page_size=1`, null, ADMIN_TOKEN)
check(deliveredTabResp.status === 200, '列表 status=8（已签收）筛选后端可用')

check(consoleErrors.length === 0, `页面 console 零错误（${consoleErrors.length}）`)
consoleErrors.forEach((e) => console.log('  [console.error]', e))

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length}` : `\nALL PASS (${failures.length === 0 ? 'all' : ''} checks)`)
process.exit(failures.length ? 1 : 0)
