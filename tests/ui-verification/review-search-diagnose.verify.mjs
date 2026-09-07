// 诊断 v2：管理端 Reviews 搜索 a-lin —— 浏览器实际请求/响应 vs 表格渲染行数
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()

const requests = []
page.on('request', (req) => {
  const u = req.url()
  if (u.includes('/api/')) requests.push(`REQ ${req.method()} ${u.replace('http://localhost:5174', '')}`)
})
const responses = []
page.on('response', async (res) => {
  const url = res.url()
  if (!url.includes('/api/admin/reviews')) return
  let body = null
  try { body = await res.json() } catch { /* ignore */ }
  const rows = body?.data?.data
  responses.push({
    url: url.replace('http://localhost:5174', ''),
    status: res.status(),
    total: body?.data?.total_elements,
    rows: Array.isArray(rows) ? rows.map((r) => `#${r.id}:${r.product_name}`) : null,
  })
})

console.log('[1] 登录并打开 Reviews')
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForTimeout(2500)
await page.goto(`${ADMIN}/reviews`)
const searchInput = page.locator('input[placeholder="搜索商品 / 买家…"]')
await searchInput.waitFor({ state: 'visible', timeout: 15000 })
await page.waitForTimeout(2000) // 初始加载
console.log('  搜索框 placeholder:', await searchInput.getAttribute('placeholder'))
console.log('  匹配到的搜索框数量:', await page.locator('input[placeholder*="搜索商品"]').count())
requests.length = 0
responses.length = 0

console.log('\n[2] 逐字符输入 a-lin（模拟真实键盘）')
await searchInput.click()
await page.keyboard.type('a-lin', { delay: 80 })
await page.waitForTimeout(2000) // 防抖 300ms + 请求 + 渲染

console.log('\n[3] 期间所有 /api 请求:')
requests.forEach((r) => console.log('  ' + r))
console.log('\n[4] reviews 响应详情:')
responses.forEach((s) => {
  console.log(`  ${s.status} ${s.url}`)
  console.log(`     total=${s.total} rows=${JSON.stringify(s.rows)}`)
})

console.log('\n[5] 表格实际渲染行数:')
const count = await page.locator('table tbody tr').count()
console.log('  rows=' + count)
const firstCells = await page.$$eval('table tbody tr td:first-child', (tds) => tds.map((t) => t.innerText.slice(0, 36)))
firstCells.forEach((t) => console.log('  | ' + t))

console.log('\n[6] 搜索框值:', await searchInput.inputValue())

// 控制台错误
page.on('console', (msg) => { if (msg.type() === 'error') console.log('  [console.error]', msg.text().slice(0, 200)) })

await browser.close()
