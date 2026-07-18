// 验证：首页装修区块列表标题不随语言 Tab 变化（临时脚本）
import { chromium } from 'playwright'

const BASE = 'http://localhost:5174'
const browser = await chromium.launch()
const page = await browser.newPage()

await page.goto(`${BASE}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'TempVerify!2026')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

await page.goto(`${BASE}/site/home`)
await page.waitForSelector('text=页面区块', { timeout: 10000 })
await page.waitForTimeout(1500)

// 选中一个非 hero 区块（分类卡片），语言 Tab 出现
await page.locator('span:text-is("分类卡片")').first().click()
await page.waitForTimeout(400)

const listTitles = async () =>
  page.locator('aside span.block.truncate.font-medium').allTextContents()

const enTitles = await listTitles()
await page.click('button:has-text("ES")')
await page.waitForTimeout(300)
const esTitles = await listTitles()
await page.click('button:has-text("FR")')
await page.waitForTimeout(300)
const frTitles = await listTitles()

const stable = JSON.stringify(enTitles) === JSON.stringify(esTitles) && JSON.stringify(enTitles) === JSON.stringify(frTitles)
console.log('EN 列表:', JSON.stringify(enTitles))
console.log('ES 列表:', JSON.stringify(esTitles))
console.log(stable ? 'PASS 区块列表标题在 EN/ES/FR 下保持稳定' : 'FAIL 列表标题仍随语言变化')

// 编辑区字段仍随语言切换（确认没误伤编辑功能）
const headingInput = page.locator('xpath=//label[contains(text(),"区块标题")]/following-sibling::input').first()
const esHeading = await headingInput.inputValue()
await page.click('button:has-text("EN")')
await page.waitForTimeout(300)
const enHeading = await headingInput.inputValue()
console.log(`编辑区标题 EN=${enHeading} ES=${esHeading}`)
console.log(enHeading !== esHeading ? 'PASS 编辑区字段仍随语言切换' : 'WARN 编辑区字段未切换（可能该区块无 ES 翻译）')

await browser.close()
process.exit(stable ? 0 : 1)
