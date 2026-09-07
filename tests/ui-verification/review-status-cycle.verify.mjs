// 验证：管理端评价状态闭环 —— approved 行可拒绝、rejected 行可恢复（UI 按钮 + 后端联动）
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
page.on('console', (m) => { if (m.type() === 'error') console.log('  [console.error]', m.text().slice(0, 160)) })

console.log('[1] 登录并打开 Reviews')
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForTimeout(2500)
await page.goto(`${ADMIN}/reviews`)
await page.waitForSelector('input[placeholder="搜索商品 / 买家…"]', { timeout: 15000 })
await page.fill('input[placeholder="搜索商品 / 买家…"]', 'a-lin')
await page.waitForTimeout(1500)

const row = page.locator('table tbody tr').first()
const rowText = () => row.innerText()
console.log('[2] 定位 approved 行（review#1 Emma Johnson）')
check((await rowText()).includes('Aurelia'), '首行是 Aurelia 评价')
check((await row.innerText()).includes('拒绝'), 'approved 行有拒绝按钮')

console.log('[3] 点击拒绝 → 行变已拒绝 + 出现恢复通过')
await row.getByRole('button', { name: /拒绝/ }).click()
await page.waitForTimeout(1200)
let t = await rowText()
check(t.includes('已拒绝') || t.includes('恢复通过'), '状态变更为已拒绝（或显示恢复按钮）')
check(t.includes('恢复通过'), '出现恢复通过按钮')

console.log('[4] 点击恢复通过 → 行回到已通过 + 拒绝按钮仍在（闭环）')
await row.getByRole('button', { name: /恢复通过/ }).click()
await page.waitForTimeout(1200)
t = await rowText()
check(t.includes('已通过') || t.includes('设为精选'), '状态回到已通过')
check(t.includes('拒绝'), '拒绝按钮仍在（状态环完整）')

console.log('\n[5] 详情抽屉闭环抽查')
await row.click()
await page.waitForTimeout(600)
const drawer = page.locator('div.fixed.inset-0').first()
const dtext = await drawer.innerText()
check(dtext.includes('拒绝下架'), 'approved 详情抽屉有拒绝下架按钮')
await page.keyboard.press('Escape')
await page.waitForTimeout(400)

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length}` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
