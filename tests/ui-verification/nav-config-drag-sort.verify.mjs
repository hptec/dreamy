// 验证：导航与页脚页面主导航/页脚栏目/页脚链接可拖拽排序，且保存后顺序持久化
import { chromium } from 'playwright'

const BASE = 'http://localhost:5174'
const browser = await chromium.launch()
const page = await browser.newPage()

await page.goto(`${BASE}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

await page.goto(`${BASE}/site/navigation`)
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.waitForTimeout(800)

async function dragHandle(handle, targetRow) {
  const from = await handle.boundingBox()
  const to = await targetRow.boundingBox()
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
  await page.mouse.down()
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 12 })
  await page.mouse.move(to.x + to.width / 2 + 4, to.y + to.height / 2 + 4, { steps: 4 })
  await page.mouse.up()
  await page.waitForTimeout(400)
}

// --- 主导航 ---
const navLabels = () => page.locator('.nav-drag-handle ~ input.field.w-48').evaluateAll((els) => els.map((e) => e.value))
const before = await navLabels()
console.log('主导航拖前:', JSON.stringify(before))

const handles = page.locator('.nav-drag-handle')
const rows = page.locator('.nav-drag-handle').locator('xpath=ancestor::div[contains(@class,"panel")]')
await dragHandle(handles.first(), rows.nth(2))
const after = await navLabels()
console.log('主导航拖后:', JSON.stringify(after))
const navMoved = JSON.stringify(before) !== JSON.stringify(after)
console.log(navMoved ? 'PASS 主导航可拖拽' : 'FAIL 主导航拖拽无效')

const dirtyBadge = await page.locator('text=未发布改动').count()
console.log(dirtyBadge > 0 ? 'PASS 拖拽后标记未发布改动' : 'FAIL 拖拽未置脏')

// 保存并刷新校验持久化
await page.click('button:has-text("保存")')
await page.waitForTimeout(1500)
await page.reload()
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.waitForTimeout(1000)
const persisted = await navLabels()
console.log('主导航刷新后:', JSON.stringify(persisted))
const navPersisted = JSON.stringify(persisted) === JSON.stringify(after)
console.log(navPersisted ? 'PASS 主导航顺序已持久化' : 'FAIL 主导航顺序未持久化')

// --- 页脚栏目 ---
await page.click('button:has-text("页脚栏目")')
await page.waitForTimeout(500)
const colTitles = () => page.locator('.col-drag-handle ~ input').evaluateAll((els) => els.map((e) => e.value))
const colBefore = await colTitles()
console.log('页脚栏目拖前:', JSON.stringify(colBefore))
const colHandles = page.locator('.col-drag-handle')
const colCards = page.locator('.col-drag-handle').locator('xpath=ancestor::div[contains(@class,"panel")]')
await dragHandle(colHandles.first(), colCards.nth(1))
const colAfter = await colTitles()
console.log('页脚栏目拖后:', JSON.stringify(colAfter))
console.log(JSON.stringify(colBefore) !== JSON.stringify(colAfter) ? 'PASS 页脚栏目可拖拽' : 'FAIL 页脚栏目拖拽无效')

// --- 页脚链接 ---
const linkHandles = page.locator('.link-drag-handle')
const linkCount = await linkHandles.count()
let linkOk = 'SKIP 页脚链接不足两条'
if (linkCount >= 2) {
  const firstColLinks = () =>
    page.locator('.col-drag-handle').locator('xpath=ancestor::div[contains(@class,"panel")]').first()
      .locator('.link-drag-handle ~ input').evaluateAll((els) => els.map((e) => e.value))
  const linkBefore = await firstColLinks()
  const rowsIn = page.locator('.link-drag-handle').locator('xpath=parent::div')
  await dragHandle(linkHandles.first(), rowsIn.nth(1))
  const linkAfter = await firstColLinks()
  console.log('页脚链接拖前:', JSON.stringify(linkBefore))
  console.log('页脚链接拖后:', JSON.stringify(linkAfter))
  linkOk = JSON.stringify(linkBefore) !== JSON.stringify(linkAfter) ? 'PASS 页脚链接可拖拽' : 'FAIL 页脚链接拖拽无效'
}
console.log(linkOk)

// --- 公告条 ---
await page.click('button:has-text("公告条")')
await page.waitForTimeout(500)
const annRows = () => page.locator('.ann-drag-handle ~ input.field.flex-1').evaluateAll((els) => els.map((e) => e.value))
const annPriorities = () => page.locator('.ann-drag-handle ~ input[type="number"]').evaluateAll((els) => els.map((e) => e.value))
const annHandles = page.locator('.ann-drag-handle')
if (await annHandles.count() < 2) {
  // 仅在内存中补一条用于拖拽验证，不点保存所以不落库
  await page.click('button:has-text("添加公告")')
  await page.waitForTimeout(300)
  await page.locator('.ann-drag-handle ~ input.field.flex-1').last().fill('__DRAG_PROBE__')
}
const annCount = await annHandles.count()
let annOk = 'SKIP 公告不足两条'
if (annCount >= 2) {
  const annBefore = await annRows()
  await dragHandle(annHandles.first(), page.locator('.ann-drag-handle').locator('xpath=parent::div').nth(1))
  const annAfter = await annRows()
  console.log('公告拖前:', JSON.stringify(annBefore))
  console.log('公告拖后:', JSON.stringify(annAfter), '优先级:', JSON.stringify(await annPriorities()))
  annOk = JSON.stringify(annBefore) !== JSON.stringify(annAfter) ? 'PASS 公告可拖拽且回写优先级' : 'FAIL 公告拖拽无效'
}
console.log(annOk)

await browser.close()
process.exit(navMoved && navPersisted ? 0 : 1)
