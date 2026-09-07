// 验证：order-flow-complete P2-A 后台配置页 —— 税率新建/重叠拒绝提示/启用切换/删除、目的国政策改 DDU、
// 运费选项新建 + 试算展示、汇率刷新 manual 模式提示 + 历史弹层、结算配置越界提示与保存；页面 console 零错误
// 前置：后端 :18081（EXCHANGE_RATE_MODE=manual）、portal-admin :5174
// 用法：node tests/ui-verification/admin-trading-settings.verify.mjs
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const API = 'http://localhost:18081'
const RUN = Date.now().toString(36)
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

const adminLogin = await api('POST', '/api/admin/auth/login', { email: 'admin@dreamy.com', password: 'Admin@123456' })
const TOKEN = adminLogin.body?.data?.token
check(adminLogin.status === 200, '管理员 API 登录')

// 选一个尚无税率规则的国家（避免与种子冲突）
const rulesBefore = await api('GET', '/api/admin/tax-rules', null, TOKEN)
const usedCountries = new Set((rulesBefore.body?.data?.items || []).map((r) => r.country_code))
const TAX_COUNTRY = ['NZ', 'JP', 'KR', 'SG', 'BR', 'MX', 'ZA', 'IN', 'TR', 'TH'].find((c) => !usedCountries.has(c)) || 'TH'
const countries = await api('GET', '/api/store/shipping/countries')
const taxCountryName = (countries.body?.data?.items || []).find((c) => c.code === TAX_COUNTRY)?.name || TAX_COUNTRY
console.log(`  (税率验证国家：${taxCountryName} ${TAX_COUNTRY})`)
// 清理同键遗留
for (const r of rulesBefore.body?.data?.items || []) if (r.country_code === TAX_COUNTRY) await api('DELETE', `/api/admin/tax-rules/${r.id}`, null, TOKEN)
// 运费选项：MEA × UPS × EXPRESS（若存在先删）
const optsBefore = await api('GET', '/api/admin/shipping/options', null, TOKEN)
for (const o of optsBefore.body?.data?.items || []) if (o.zone === 'MEA' && o.carrier_code === 'UPS' && o.service_level === 2) await api('DELETE', `/api/admin/shipping/options/${o.id}`, null, TOKEN)
// 记录 AU 政策与结算配置原值以便恢复
const auPolicyBefore = (await api('GET', '/api/admin/tax-destination-policies/AU', null, TOKEN)).body?.data
const cfgBefore = (await api('GET', '/api/admin/checkout-config', null, TOKEN)).body?.data

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

async function pickOption(triggerSelector, optionName) {
  await page.click(`${triggerSelector} button`)
  await page.getByRole('option', { name: optionName }).first().click()
  await page.waitForTimeout(200)
}

console.log('[1] 登录 → 税费规则 tab')
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForTimeout(2000)
await page.goto(`${ADMIN}/settings?tab=tax`)
await page.waitForSelector('[data-testid="tax-rules-panel"]', { timeout: 15000 })
await page.waitForTimeout(1500)
check(await page.locator('[data-testid="tax-rules-table"]').isVisible(), '税率规则表格渲染（种子数据）')
check(await page.locator('[data-testid="tax-policies-table"]').isVisible(), '目的国政策表格渲染')

console.log('[2] 新建税率规则（生效窗口 2026-01-01 ~ 2026-06-30）')
await page.click('[data-testid="tax-add"]')
await page.waitForSelector('[data-testid="tax-submit"]', { timeout: 5000 })
await pickOption('[data-testid="tax-country"]', `${taxCountryName}（${TAX_COUNTRY}）`)
await pickOption('[data-testid="tax-type"]', 'VAT（增值税）')
await page.fill('[data-testid="tax-rate"]', '12.5')
await page.fill('[data-testid="tax-threshold"]', '100')
await page.fill('[data-testid="tax-from"]', '2026-01-01')
await page.fill('[data-testid="tax-to"]', '2026-06-30')
await page.fill('[data-testid="tax-label"]', `VAT ${RUN}`)
await page.click('[data-testid="tax-submit"]')
await page.waitForTimeout(1500)
let rulesNow = (await api('GET', `/api/admin/tax-rules?country_code=${TAX_COUNTRY}`, null, TOKEN)).body?.data?.items || []
const created = rulesNow.find((r) => r.label === `VAT ${RUN}`)
check(!!created, '税率规则已创建（后端可查）')
check(created?.rate_scaled === 1250 && Number(created?.threshold_usd) === 100, 'rate_scaled=1250（12.5%）且起征额 100')
await pickOption('[data-testid="tax-filter-country"]', `${taxCountryName}（${TAX_COUNTRY}）`)
await page.waitForTimeout(500)
const tableText = await page.locator('[data-testid="tax-rules-table"]').innerText()
check(tableText.includes(`VAT ${RUN}`) && tableText.includes('12.5%'), '表格显示新规则 12.5% + 标签')

console.log('[3] 重叠窗口规则 → 422907 提示')
await page.click('[data-testid="tax-add"]')
await page.waitForSelector('[data-testid="tax-submit"]', { timeout: 5000 })
await pickOption('[data-testid="tax-country"]', `${taxCountryName}（${TAX_COUNTRY}）`)
await pickOption('[data-testid="tax-type"]', 'VAT（增值税）')
await page.fill('[data-testid="tax-rate"]', '15')
await page.fill('[data-testid="tax-from"]', '2026-06-01')
await page.fill('[data-testid="tax-to"]', '2026-12-31')
await page.click('[data-testid="tax-submit"]')
await page.waitForTimeout(1500)
const drawerText = await page.locator('div.fixed.inset-0').last().innerText()
check(drawerText.includes('重叠'), '抽屉内提示「生效窗口与现有规则重叠」')
check(await page.locator('[data-testid="tax-submit"]').isVisible(), '抽屉保持打开（未误关闭）')
await page.keyboard.press('Escape')
await page.locator('div.fixed.inset-0').last().locator('button').first().click().catch(() => undefined)
await page.waitForTimeout(500)
if (await page.locator('[data-testid="tax-submit"]').isVisible().catch(() => false)) {
  await page.locator('div.fixed.inset-0').last().getByRole('button', { name: '取消' }).click()
  await page.waitForTimeout(400)
}

console.log('[4] 启用开关切换')
const row = page.locator(`[data-testid="tax-rule-row-${created?.id}"]`)
await row.locator('[role="switch"]').click()
await page.waitForTimeout(1200)
let after = (await api('GET', `/api/admin/tax-rules?country_code=${TAX_COUNTRY}`, null, TOKEN)).body?.data?.items?.find((r) => r.id === created?.id)
check(after?.enabled === false, '启用开关 → 后端 enabled=false')
await row.locator('[role="switch"]').click()
await page.waitForTimeout(1200)
after = (await api('GET', `/api/admin/tax-rules?country_code=${TAX_COUNTRY}`, null, TOKEN)).body?.data?.items?.find((r) => r.id === created?.id)
check(after?.enabled === true, '再次切换 → enabled=true')

console.log('[5] 目的国政策 AU 改为 DDU')
const auRow = page.locator('[data-testid="policy-row-AU"]')
check(await auRow.isVisible(), 'AU 政策行存在')
await auRow.locator('[data-testid="policy-edit"]').click()
await page.waitForTimeout(300)
await pickOption('[data-testid="policy-incoterm-select"]', 'DDU（到付关税）')
await page.fill('[data-testid="policy-notice-input"]', `Duties may apply ${RUN}`)
await page.click('[data-testid="policy-save"]')
await page.waitForTimeout(1500)
const auAfter = (await api('GET', '/api/admin/tax-destination-policies/AU', null, TOKEN)).body?.data
check(auAfter?.incoterm === 2 && auAfter?.notice_text === `Duties may apply ${RUN}`, '后端 AU incoterm=DDU + 文案已保存')
check((await auRow.locator('[data-testid="policy-incoterm"]').innerText()).includes('DDU'), '行内显示 DDU')

console.log('[6] 物流页：运费选项新建 + 试算')
await page.goto(`${ADMIN}/shipping`)
await page.waitForSelector('[data-testid="options-table"]', { timeout: 15000 })
await page.waitForTimeout(1000)
check((await page.locator('[data-testid="carriers-panel"]').innerText()).includes('FEDEX'), '承运方面板显示编码徽章')
await page.click('[data-testid="add-option"]')
await page.waitForSelector('[data-testid="option-submit"]', { timeout: 5000 })
const drawer = page.locator('div.fixed.inset-0').last()
await drawer.locator('button.field').nth(0).click()
await page.getByRole('option', { name: /MEA/ }).click()
await drawer.locator('button.field').nth(1).click()
await page.getByRole('option', { name: /UPS/ }).click()
await drawer.locator('button.field').nth(2).click()
await page.getByRole('option', { name: /加急/ }).click()
await page.fill('[data-testid="option-fee-under"]', '66')
await page.fill('[data-testid="option-transit-min"]', '3')
await page.fill('[data-testid="option-transit-max"]', '6')
await page.click('[data-testid="option-submit"]')
await page.waitForTimeout(1500)
const optsNow = (await api('GET', '/api/admin/shipping/options', null, TOKEN)).body?.data?.items || []
const createdOpt = optsNow.find((o) => o.zone === 'MEA' && o.carrier_code === 'UPS' && o.service_level === 2)
check(!!createdOpt && Number(createdOpt.fee_under) === 66 && createdOpt.transit_days_min === 3, '运费选项 MEA/UPS/加急 已创建（fee 66，3~6 天）')
const optTable = await page.locator('[data-testid="options-table"]').innerText()
check(optTable.includes('中东非洲') && optTable.includes('$66.00'), '运费选项表格显示新行')

// 试算：AE（MEA）
await page.waitForSelector('[data-testid="quote-run"]:not([disabled])', { timeout: 15000 })
await page.click('[data-testid="quote-country"] button')
await page.getByRole('option', { name: /United Arab Emirates/ }).click()
await page.fill('[data-testid="quote-subtotal"]', '500')
await page.click('[data-testid="quote-run"]')
await page.waitForSelector('[data-testid="quote-result"]', { timeout: 10000 })
const qr = await page.locator('[data-testid="quote-result"]').innerText()
check(qr.includes('MEA'), '试算结果显示分区 MEA')
check(qr.includes('UPS') && qr.includes('加急') && qr.includes('$66.00'), '试算列出新建 UPS 加急 $66.00')
check(qr.includes('~'), '试算显示预计送达区间')
check(await page.locator('[data-testid="quote-tax"]').isVisible(), '试算税费卡片渲染（DDP/DDU）')

console.log('[7] 汇率 tab：刷新（manual 模式）+ 历史弹层 + 新列')
await page.goto(`${ADMIN}/settings`)
await page.waitForSelector('[data-testid="exchange-rates-table"]', { timeout: 15000 })
await page.waitForTimeout(1200)
const rateHeader = await page.locator('[data-testid="exchange-rates-table"] thead').innerText()
check(rateHeader.includes('来源') && rateHeader.includes('同步时间') && rateHeader.includes('有效汇率') && rateHeader.includes('手工锁定'), '汇率表头含来源/同步时间/有效汇率/手工锁定')
await page.click('[data-testid="rates-refresh"]')
await page.waitForTimeout(1500)
const note = await page.locator('[data-testid="rates-refresh-note"]').innerText().catch(() => '')
check(note.includes('手工') && note.includes('供应商'), `manual 模式刷新提示：${note}`)
await page.locator('[data-testid="rate-row-EUR"] [data-testid="rate-history"]').click()
await page.waitForSelector('[data-testid="rate-history-dialog"]', { timeout: 5000 })
const hist = await page.locator('[data-testid="rate-history-dialog"]').innerText()
check(hist.includes('EUR 汇率历史') && (hist.includes('报价日') || hist.includes('无历史')), '历史弹层渲染（近 30 天表格）')
await page.keyboard.press('Escape')
await page.locator('[data-testid="rate-history-dialog"] button').first().click().catch(() => undefined)
await page.waitForTimeout(400)

console.log('[8] 结算配置：越界提示与保存')
await page.click('[data-testid="settings-tab-checkout"]')
await page.waitForSelector('[data-testid="cfg-save"]', { timeout: 10000 })
await page.waitForTimeout(800)
check((await page.locator('[data-testid="cfg-auto-complete"]').inputValue()) === String(cfgBefore.auto_complete_days), '自动完成天数回显现值')
await page.fill('[data-testid="cfg-auto-complete"]', '61')
await page.fill('[data-testid="cfg-pending-timeout"]', '4')
await page.click('[data-testid="cfg-save"]')
await page.waitForTimeout(600)
const panel = await page.locator('[data-testid="checkout-config-panel"]').innerText()
check(panel.includes('1~60'), '自动完成天数越界 → 行内提示 1~60')
check(panel.includes('5~1440'), '待付款超时越界 → 行内提示 5~1440')
const cfgUnchanged = (await api('GET', '/api/admin/checkout-config', null, TOKEN)).body?.data
check(cfgUnchanged.auto_complete_days === cfgBefore.auto_complete_days, '越界时未提交（后端值不变）')
// 后端 422601 fields 兜底（绕过前端预校验直接调接口）
const bad = await api('PUT', '/api/admin/checkout-config', { ...cfgBefore, auto_complete_days: 61 }, TOKEN)
check(bad.status === 422 && bad.body?.code === 422601 && bad.body?.data?.fields?.auto_complete_days, '后端 422601 + fields.auto_complete_days（前端 extractFieldErrors 可回显）')
// 有效值保存
await page.fill('[data-testid="cfg-auto-complete"]', '9')
await page.fill('[data-testid="cfg-pending-timeout"]', '45')
await page.fill('[data-testid="cfg-spread"]', '150')
await page.click('[data-testid="cfg-save"]')
await page.waitForTimeout(1500)
const cfgSaved = (await api('GET', '/api/admin/checkout-config', null, TOKEN)).body?.data
check(cfgSaved.auto_complete_days === 9 && cfgSaved.pending_timeout_minutes === 45 && cfgSaved.exchange_rate_spread_scaled === 150, '结算配置新字段已保存（9 / 45 / 150）')
check((await page.locator('[data-testid="checkout-config-panel"]').innerText()).includes('1.50%'), '加价率提示显示 1.50%')
await page.click('[data-testid="settings-tab-rates"]')
await page.waitForTimeout(800)
const eurRow = await page.locator('[data-testid="rate-row-EUR"]').innerText()
check(eurRow.includes('+1.50%'), '汇率表有效汇率列反映加价 +1.50%')

check(consoleErrors.length === 0, `页面 console 零错误（${consoleErrors.length}）`)
consoleErrors.forEach((e) => console.log('  [console.error]', e))

// ---------- 清理 / 恢复 ----------
console.log('[9] 清理')
if (created) await api('DELETE', `/api/admin/tax-rules/${created.id}`, null, TOKEN)
if (createdOpt) await api('DELETE', `/api/admin/shipping/options/${createdOpt.id}`, null, TOKEN)
if (auPolicyBefore) await api('PUT', '/api/admin/tax-destination-policies/AU', { incoterm: auPolicyBefore.incoterm, duties_notice: auPolicyBefore.duties_notice, notice_text: auPolicyBefore.notice_text }, TOKEN)
if (cfgBefore) await api('PUT', '/api/admin/checkout-config', cfgBefore, TOKEN)
const restored = (await api('GET', '/api/admin/checkout-config', null, TOKEN)).body?.data
check(restored.auto_complete_days === cfgBefore.auto_complete_days, '结算配置已恢复原值')

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length}` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
