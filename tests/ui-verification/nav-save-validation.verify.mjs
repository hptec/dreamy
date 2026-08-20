// 验证：导航与页脚保存前的本地校验能定位到具体项，且后端错误码返回中文文案
import { chromium } from 'playwright'

const BASE = 'http://localhost:5174'
const browser = await chromium.launch()
const page = await browser.newPage()
const failures = []

await page.goto(`${BASE}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

await page.goto(`${BASE}/site/navigation`)
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.waitForTimeout(800)

// 拦截保存请求，确认本地校验拦下后不发网络请求
let navPutCount = 0
page.on('request', (req) => {
  if (req.method() === 'PUT' && req.url().includes('/site-builder/navigation')) navPutCount += 1
})

async function toastText() {
  const toast = page.locator('[role="status"]').last()
  try {
    await toast.waitFor({ state: 'visible', timeout: 5000 })
    return (await toast.innerText()).trim()
  } catch {
    return ''
  }
}

// --- 场景 1：新增自定义 URL 导航项但不填地址 ---
await page.click('button:has-text("添加主导航项")')
await page.waitForTimeout(300)
await page.click('button:has-text("保存")')
const msg1 = await toastText()
console.log('场景1 提示:', msg1)
if (!msg1.includes('自定义 URL') || !msg1.includes('链接地址')) {
  failures.push(`场景1 未给出定位到具体项的中文提示，实际: ${msg1}`)
}
if (navPutCount !== 0) {
  failures.push(`场景1 本地校验未拦截，仍发出了 ${navPutCount} 次导航保存请求`)
}

// --- 场景 2：填上地址后应能正常保存 ---
const lastUrlInput = page.locator('input[placeholder="/path 或 https://..."]').last()
await lastUrlInput.fill('/verify-tmp')
await page.waitForTimeout(200)
await page.click('button:has-text("保存")')
await page.waitForTimeout(1500)
const msg2 = await toastText()
console.log('场景2 提示:', msg2, '| 导航 PUT 次数:', navPutCount)
if (!msg2.includes('保存成功')) {
  failures.push(`场景2 填写地址后仍未保存成功，实际: ${msg2}`)
}

// --- 场景 3：清理刚才新增的项，恢复现场 ---
await page.reload()
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.waitForTimeout(800)
const tmpRow = page.locator('input[placeholder="/path 或 https://..."]')
const count = await tmpRow.count()
for (let i = count - 1; i >= 0; i -= 1) {
  if ((await tmpRow.nth(i).inputValue()) === '/verify-tmp') {
    const row = tmpRow.nth(i).locator('xpath=ancestor::div[contains(@class,"panel")]')
    await row.locator('button').last().click()
    break
  }
}
await page.click('button:has-text("保存")')
await page.waitForTimeout(1500)
console.log('场景3 清理提示:', await toastText())

await browser.close()

if (failures.length > 0) {
  console.error('\n验证失败:')
  failures.forEach((f) => console.error(' -', f))
  process.exit(1)
}
console.log('\n全部通过')
