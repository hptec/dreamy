// 验证：公告增量提交、版本冲突自动重拉、target 新标签打开（后台条件暴露 + 消费端渲染）
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const STORE = 'http://localhost:5173'
const API = 'http://localhost:18081'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

// --- 拿一个 admin token，供带外改数据用 ---
const loginRes = await fetch(`${API}/api/admin/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@dreamy.com', password: 'Admin@123456' }),
})
const token = (await loginRes.json()).data.token
const authed = (path, init = {}) =>
  fetch(`${API}${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(init.headers || {}) } })

const firstAnnouncement = async () =>
  (await (await authed('/api/admin/site-builder/announcements?page=1&page_size=5')).json()).data.data[0]

const browser = await chromium.launch()
const page = await browser.newPage()
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

const toastText = async () => {
  const toast = page.locator('[role="status"]').last()
  try {
    await toast.waitFor({ state: 'visible', timeout: 6000 })
    return (await toast.innerText()).trim()
  } catch { return '' }
}
// 上一条 toast 未消失时 last() 会抓到旧文案，断言前先等干净
const waitToastCleared = async () => {
  await page.locator('[role="status"]').first().waitFor({ state: 'detached', timeout: 8000 }).catch(() => {})
}
const openNavPage = async () => {
  await page.goto(`${ADMIN}/site/navigation`)
  await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
  await page.waitForTimeout(800)
}

// ================= 1. 公告增量提交 =================
console.log('\n[1] 只改导航项，公告 version 不应被推进')
await openNavPage()
const vBefore = (await firstAnnouncement()).version
await page.click('button:has-text("添加主导航项")')
await page.locator('input[placeholder="/path 或 https://..."]').last().fill('/verify-incremental')
await page.click('button:has-text("保存")')
check((await toastText()) === '保存成功', '导航保存成功')
const vAfter = (await firstAnnouncement()).version
check(vAfter === vBefore, `公告 version 未被推进（${vBefore} → ${vAfter}）`)

// ================= 2. target 条件暴露 =================
console.log('\n[2] 仅外域链接出现「新标签打开」')
await openNavPage()
const lastUrl = () => page.locator('input[placeholder="/path 或 https://..."]').last()
const newTabBox = () => lastUrl().locator('xpath=following::label[1]')
await lastUrl().fill('/verify-incremental')
await page.waitForTimeout(200)
check(await newTabBox().count() === 0 || !(await newTabBox().isVisible()), '站内路径不显示「新标签打开」')
await lastUrl().fill('https://example.com/partner')
await page.waitForTimeout(200)
check(await newTabBox().isVisible(), '外域链接显示「新标签打开」')
await newTabBox().locator('input[type="checkbox"]').check()
await page.click('button:has-text("保存")')
check((await toastText()) === '保存成功', '勾选新标签后保存成功')

const navItems = (await (await authed('/api/admin/site-builder/navigation')).json()).data.items
const externalItem = navItems.find((i) => i.url === 'https://example.com/partner')
check(externalItem?.target === 'blank', `外链导航 target 落库为 blank（实际 ${externalItem?.target}）`)

// 切回站内路径应自动降回 self
await openNavPage()
await lastUrl().fill('/verify-incremental')
await page.waitForTimeout(200)
await page.click('button:has-text("保存")')
await toastText()
const navItems2 = (await (await authed('/api/admin/site-builder/navigation')).json()).data.items
const backToInternal = navItems2.find((i) => i.url === '/verify-incremental')
check(backToInternal?.target === 'self', `改回站内路径后 target 降回 self（实际 ${backToInternal?.target}）`)

// ================= 3. 消费端渲染 =================
console.log('\n[3] 消费端按 target 渲染并带 rel')
// 重新配成外链 + blank
await openNavPage()
await lastUrl().fill('https://example.com/partner')
await page.waitForTimeout(200)
await newTabBox().locator('input[type="checkbox"]').check()
await page.click('button:has-text("保存")')
await toastText()

const storePage = await browser.newPage()
await storePage.goto(STORE, { waitUntil: 'domcontentloaded' })
await storePage.waitForTimeout(2500)
const anchor = storePage.locator('nav a[href="https://example.com/partner"]').first()
if (await anchor.count() > 0) {
  check(await anchor.getAttribute('target') === '_blank', '消费端渲染 target="_blank"')
  check((await anchor.getAttribute('rel') || '').includes('noopener'), '消费端渲染 rel 含 noopener')
} else {
  failures.push('消费端未找到该外链导航项（可能被缓存，需确认缓存失效策略）')
  console.log('  ✗ 消费端未找到该外链导航项')
}
await storePage.close()

// ================= 4. 版本冲突自动重拉 =================
console.log('\n[4] 版本冲突后自动载入最新数据，重试可成功')
await openNavPage()
await page.click('button:has-text("公告条")')
await page.waitForTimeout(400)
const annInput = page.locator('.ann-drag-handle').locator('xpath=following::input[1]').first()
await annInput.fill('conflict probe A')
// 带外把 version 推进一格，制造冲突
const cur = await firstAnnouncement()
await authed(`/api/admin/site-builder/announcements/${cur.id}`, {
  method: 'PUT',
  body: JSON.stringify({
    enabled: cur.enabled, priority: cur.priority, start_at: cur.start_at, end_at: cur.end_at,
    content_i18n_json: cur.content_i18n_json, version: cur.version,
  }),
})
await page.click('button:has-text("保存")')
const conflictMsg = await toastText()
console.log('  冲突提示:', conflictMsg)
check(conflictMsg.includes('已载入最新内容'), '给出冲突并已重载的中文提示')

await waitToastCleared()
await page.click('button:has-text("公告条")')
await page.waitForTimeout(300)
await page.locator('.ann-drag-handle').locator('xpath=following::input[1]').first().fill('conflict probe B')
const retryEnabled = await page.locator('button:has-text("保存")').isEnabled()
check(retryEnabled, '重载后保存按钮可用')
await page.click('button:has-text("保存")')
check((await toastText()) === '保存成功', '重载后重试保存成功（未卡死）')

// ================= 清理 =================
console.log('\n[清理] 移除验证用导航项')
await openNavPage()
const probes = page.locator('input[placeholder="/path 或 https://..."]')
for (let i = (await probes.count()) - 1; i >= 0; i -= 1) {
  const v = await probes.nth(i).inputValue()
  if (v.includes('verify-incremental') || v.includes('example.com/partner')) {
    await probes.nth(i).locator('xpath=ancestor::div[contains(@class,"panel")]').locator('button').last().click()
  }
}
await page.click('button:has-text("保存")')
console.log('  清理结果:', await toastText())

await browser.close()
if (failures.length > 0) {
  console.error(`\n失败 ${failures.length} 项:`)
  failures.forEach((f) => console.error(' -', f))
  process.exit(1)
}
console.log('\n全部通过')
