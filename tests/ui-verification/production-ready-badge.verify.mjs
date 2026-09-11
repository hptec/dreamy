// 验证：ProductionStageStepper 终态（READY_TO_SHIP）不再渲染为金色 disabled 按钮，而是非交互「已可发货」徽章
// 前置：后端 :18081、portal-admin dev :5174；验证后恢复订单原制作阶段
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const API = 'http://localhost:18081'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

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

const login = await api('POST', '/api/admin/auth/login', { email: 'admin@dreamy.com', password: 'Admin@123456' })
if (login.status !== 200) { console.log('FAILED: admin login', login.status); process.exit(1) }
const TOKEN = login.body.data.token

const list = await api('GET', '/api/admin/orders?status=2&page_size=10', null, TOKEN)
// 只选 production_stage 非空的订单：null 原值无法通过 API 恢复，会造成数据污染
const cand = (list.body?.data?.data || []).find((o) => o.production_stage != null && o.production_stage < 4)
if (!cand) { console.log('FAILED: no PAID order available'); process.exit(1) }
const { id, order_no, production_stage: origStage } = cand
console.log(`[0] 目标订单 #${id} ${order_no}，原阶段=${origStage ?? '(null)'}，逐级推至 READY_TO_SHIP`)
for (let s = (origStage ?? 0) + 1; s <= 4; s++) {
  const patch = await api('PATCH', `/api/admin/orders/${id}/production-stage`, { stage: s }, TOKEN)
  if (![200, 204].includes(patch.status)) { console.log(`FAILED: patch stage ${s}`, patch.status, JSON.stringify(patch.body).slice(0, 200)); process.exit(1) }
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const consoleErrors = []
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)) })
page.on('pageerror', (e) => consoleErrors.push('PAGEERROR ' + e.message))

try {
  console.log('[1] 登录并打开订单详情（终态）')
  await page.goto(`${ADMIN}/login`)
  await page.fill('input[type="email"]', 'admin@dreamy.com')
  await page.fill('input[type="password"]', 'Admin@123456')
  await page.click('button[type="submit"]')
  await page.waitForTimeout(2000)
  await page.goto(`${ADMIN}/orders/${id}`)
  await page.waitForSelector('[data-testid="production-stepper"]', { timeout: 15000 })
  await page.waitForTimeout(800)

  const badge = page.locator('[data-testid="production-ready"]')
  check(await badge.isVisible(), '终态渲染「已可发货」徽章（production-ready）')
  check((await badge.innerText()).includes('已可发货'), '徽章文案为「已可发货」')
  const cls = await badge.getAttribute('class')
  check(cls.includes('badge') && !cls.includes('btn'), '徽章为 badge 胶囊样式，非按钮样式')
  check((await badge.evaluate((el) => el.tagName)) !== 'BUTTON', '渲染元素不是 button（无点击语义）')
  check(await page.locator('[data-testid="production-next"]').count() === 0, '终态不存在 production-next 推进按钮')
  check(!(await page.locator('[data-testid="production-prev"]').isDisabled()), '「回退一档」按钮仍可用')

  await page.locator('[data-testid="production-stepper"]').screenshot({ path: 'logs/production-ready-badge.png' })
  console.log('  (截图 logs/production-ready-badge.png)')
  check(consoleErrors.length === 0, `页面 console 零错误${consoleErrors.length ? '：' + consoleErrors[0] : ''}`)
} finally {
  console.log('[2] 恢复订单原制作阶段')
  const target = origStage ?? 1
  for (let s = 3; s >= target; s--) {
    const step = await api('PATCH', `/api/admin/orders/${id}/production-stage`, { stage: s }, TOKEN)
    if (![200, 204].includes(step.status)) console.log(`  (回退到 ${s} 失败 ${step.status}，继续尝试)`)
  }
  const verify = await api('GET', `/api/admin/orders/${id}`, null, TOKEN)
  check(verify.body?.data?.production_stage === target, `阶段已恢复为 ${target}`)
  await browser.close()
}

if (failures.length) { console.log(`FAILED: ${failures.length} 项断言未通过`); process.exit(1) }
console.log('ALL PASS')
