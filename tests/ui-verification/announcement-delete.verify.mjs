// 验证：后台删除公告后保存，确实从后端删除（此前删除按钮只改本地数组，保存后公告会原样回来）
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const API = 'http://localhost:18081'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const token = (await (await fetch(`${API}/api/admin/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@dreamy.com', password: 'Admin@123456' }),
})).json()).data.token
const authed = (path, init = {}) =>
  fetch(`${API}${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(init.headers || {}) } })
const listIds = async () =>
  (await (await authed('/api/admin/site-builder/announcements?page=1&page_size=50')).json()).data.data.map((a) => a.id)

// 造一条一次性公告（disabled 避免影响消费端展示，优先级取足够低避开时间窗冲突）
const created = (await (await authed('/api/admin/site-builder/announcements', {
  method: 'POST',
  body: JSON.stringify({ enabled: false, priority: 0, content_i18n_json: { en: { content: 'delete probe' } } }),
})).json()).data
console.log('  已创建探针公告 id =', created.id)
check((await listIds()).includes(created.id), '探针公告已入库')

const browser = await chromium.launch()
const page = await browser.newPage()
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

await page.goto(`${ADMIN}/site/navigation`)
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.click('button:has-text("公告条")')
await page.waitForTimeout(800)

const probeRow = page.locator('.ann-drag-handle')
  .locator('xpath=ancestor::div[1]')
  .filter({ has: page.locator('input[value="delete probe"]') })
check(await probeRow.count() > 0, '页面上能看到探针公告')
await probeRow.locator('button').last().click()
await page.waitForTimeout(200)
await page.click('button:has-text("保存")')

const toast = page.locator('[role="status"]').last()
await toast.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {})
console.log('  保存提示:', (await toast.innerText().catch(() => '')).trim())

await page.waitForTimeout(800)
check(!(await listIds()).includes(created.id), '删除已落库（后端不再返回该公告）')

await page.reload()
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.click('button:has-text("公告条")')
await page.waitForTimeout(600)
check(await page.locator('input[value="delete probe"]').count() === 0, '刷新后探针公告未复活')

await browser.close()

// 兜底清理：万一没删掉，直接调 API 删
if ((await listIds()).includes(created.id)) {
  await authed(`/api/admin/site-builder/announcements/${created.id}`, { method: 'DELETE' })
  console.log('  [兜底清理] 已通过 API 删除探针公告')
}

if (failures.length > 0) {
  console.error(`\n失败 ${failures.length} 项:`)
  failures.forEach((f) => console.error(' -', f))
  process.exit(1)
}
console.log('\n全部通过')
