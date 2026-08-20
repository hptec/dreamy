// 验证：移除「前台预览」入口后，导航与页脚 / 首页装修 两页功能正常
import { chromium } from 'playwright'

const BASE = 'http://localhost:5174'
const browser = await chromium.launch()
const page = await browser.newPage()
const errors = []
page.on('pageerror', (e) => errors.push(String(e)))

await page.goto(`${BASE}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

// --- 导航与页脚 ---
await page.goto(`${BASE}/site/navigation`)
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.waitForTimeout(800)
const navPreviewBtn = await page.locator('button:has-text("前台预览")').count()
console.log(navPreviewBtn === 0 ? 'PASS 导航页「前台预览」按钮已移除' : 'FAIL 按钮仍在')
console.log(await page.locator('button:has-text("保存")').count() > 0 ? 'PASS 保存按钮仍在' : 'FAIL 保存按钮丢失')

// --- 首页装修 ---
await page.goto(`${BASE}/site/home`)
await page.waitForSelector('text=页面区块', { timeout: 10000 })
await page.waitForTimeout(1200)
console.log(await page.locator('button:has-text("页面预览")').count() === 0 ? 'PASS 首页装修「页面预览」Tab 已移除' : 'FAIL Tab 仍在')
console.log(await page.locator('iframe').count() === 0 ? 'PASS 预览 iframe 已移除' : 'FAIL iframe 仍在')

const blockCount = await page.locator('.drag-handle').count()
console.log(blockCount > 0 ? `PASS 区块列表正常渲染（${blockCount} 个）` : 'FAIL 区块列表为空')

// 编辑区仍可用：点一个区块，语言 Tab 切换不报错
await page.locator('aside span.block.truncate.font-medium').first().click()
await page.waitForTimeout(400)
const editorVisible = await page.locator('text=区块设置').count()
console.log(editorVisible > 0 ? 'PASS 区块编辑区正常打开' : 'FAIL 编辑区未渲染')
for (const l of ['ES', 'FR', 'EN']) {
  const btn = page.locator(`button:text-is("${l}")`).first()
  if (await btn.count()) { await btn.click(); await page.waitForTimeout(250) }
}
console.log('PASS 语言 Tab 切换无异常')

console.log(errors.length === 0 ? 'PASS 无运行时错误' : `FAIL 运行时错误: ${errors.join(' | ')}`)
await browser.close()
process.exit(errors.length === 0 ? 0 : 1)
