// 验证：评价审核图片驳回/恢复全链路
// 修复背景：契约 PATCH /api/admin/reviews/{id}/images/{imageId} 200 返回 ReviewImage（单图），
// 前端曾把它当 AdminReview 消费 → replaceRow 匹配失败 + lightbox.review 被替换为无 images 的 DTO → 渲染崩溃。
// 本脚本覆盖：驳回 → 恢复（原 bug 场景）→ UI 即时反馈 → 刷新持久化 → 前台展示联动 → 现场还原。
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const API = 'http://localhost:18081'
const STORE = 'http://localhost:5173'
const REVIEW_ID = 3          // Ava Chen · Celeste V-Neck Lace Gown（approved，2 图：id 4/5）
const SLUG = 'celeste-lace-gown'
const CUSTOMER = 'Ava Chen'  // admin 侧（不脱敏）
const STORE_CUSTOMER = 'Ava C.' // 前台侧（customer_name 脱敏）

const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ---------- API 工具 ----------
const token = (await (await fetch(`${API}/api/admin/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@dreamy.com', password: 'Admin@123456' }),
})).json()).data.token
const authed = (path, init = {}) =>
  fetch(`${API}${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(init.headers || {}) } })

/** API 视角读取评价 3 的图片列表 */
async function apiImages() {
  const list = (await (await authed(`/api/admin/reviews?page=1&page_size=50&status=2`)).json()).data.data
  const r = list.find((x) => x.id === REVIEW_ID)
  if (!r) throw new Error('API 找不到评价 ' + REVIEW_ID)
  return r.images
}
const apiRejected = async (imageId) => (await apiImages()).find((i) => i.id === imageId)?.rejected

// ---------- 基线还原（脚本可重复执行） ----------
console.log('\n[0] 基线准备')
for (const img of await apiImages()) {
  if (img.rejected) await authed(`/api/admin/reviews/${REVIEW_ID}/images/${img.id}`, { method: 'PATCH', body: JSON.stringify({ rejected: false }) })
}
check((await apiImages()).every((i) => !i.rejected), 'API 基线：评价 3 两图均 rejected=false')

const browser = await chromium.launch()

// ---------- 前台基线（驳回生效前的可见图片数） ----------
console.log('\n[1] 前台基线')
const storeCtx = await browser.newContext()
const storePage = await storeCtx.newPage()
async function storeImageCount() {
  // 随机 query 强制真实导航：goto 相同 URL（含同 hash）是同文档导航，页面不会重新加载
  await storePage.goto(`${STORE}/product/${SLUG}?_r=${Date.now()}#reviews`, { waitUntil: 'domcontentloaded' })
  await storePage.waitForSelector('#reviews', { timeout: 20000 })
  // 客户端 fetch 评价列表后图片才渲染；轮询直到 Ava 的评价块出现且图片数稳定（驳回场景至少剩 1 张）
  for (let i = 0; i < 20; i++) {
    const block = storePage.locator('div.border-b').filter({ hasText: STORE_CUSTOMER })
    if (await block.count()) {
      const n = await block.first().locator('img[alt="Customer photo"]').count()
      if (n > 0) return n
    } else {
      const more = storePage.getByRole('button', { name: 'Load more reviews' })
      if (await more.count()) { await more.click(); continue }
    }
    await sleep(800)
  }
  return 0
}
const baseCount = await storeImageCount()
check(baseCount === 2, `前台基线：${CUSTOMER} 评价块可见图片 2 张（实际 ${baseCount}）`)

// ---------- Admin 登录 ----------
console.log('\n[2] Admin 登录与页面打开')
const page = await browser.newPage()
page.on('pageerror', (err) => failures.push(`页面 JS 错误: ${err.message}`))
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })
check(true, '登录成功')

await page.goto(`${ADMIN}/reviews`)
const row = page.locator('tbody tr').filter({ hasText: CUSTOMER })
// 等确切数据行出现（waitForSelector('tbody tr') 会匹配到「加载中…」占位行造成竞态）
await row.first().waitFor({ timeout: 10000 })
check(await row.count() === 1, `列表定位到 ${CUSTOMER} 的评价行`)
await row.first().click()
await page.locator('h3:has-text("评价详情")').waitFor({ timeout: 5000 })
check(true, '打开评价详情抽屉')

const thumbs = page.locator('div.grid button:has(img)')
await thumbs.first().waitFor({ timeout: 5000 })
check(await thumbs.count() === 2, '抽屉显示 2 张买家秀缩略图')

/** 打开 lightbox 并等待就绪 */
async function openLightbox() {
  await thumbs.first().click()
  await page.locator('button:has-text("驳回此图"), button:has-text("恢复展示")').first().waitFor({ timeout: 5000 })
}
/** lightbox 容器（z-[60] 仅 lightbox 使用，避免匹配到抽屉 z-50） */
const lightboxRoot = () => page.locator('div[class*="z-[60]"]')
async function closeLightbox() {
  await lightboxRoot().locator('button').first().click()
  await page.locator('button:has-text("驳回此图"), button:has-text("恢复展示")').first().waitFor({ state: 'detached', timeout: 5000 })
}
async function toastSays(text) {
  const t = page.locator('[role="status"]').filter({ hasText: text })
  try { await t.first().waitFor({ state: 'visible', timeout: 4000 }); return true } catch { return false }
}
/** 等指定图片的 PATCH 响应到达（click 后立即查 API 有竞态；toast waitFor 会匹配残留旧 toast，均不可靠） */
const patchDone = (imageId) => page.waitForResponse(
  (res) => res.request().method() === 'PATCH' && res.url().includes(`/images/${imageId}`),
  { timeout: 8000 },
)

// ---------- 测试 A：驳回 ----------
console.log('\n[3] 驳回图片（rejected=true）')
await openLightbox()
{
  const done = patchDone(4)
  await page.locator('button:has-text("驳回此图")').click()
  await done
}
check(await toastSays('已驳回该图片'), 'toast 提示「已驳回该图片，前台将不再展示」')
check(await lightboxRoot().count() === 1, 'lightbox 仍正常渲染（未崩溃）')
check(await page.locator('text=已驳回 · 前台不展示').count() === 1, 'lightbox 显示「已驳回 · 前台不展示」徽标')
const lbImg = lightboxRoot().locator('img')
const visualGray = async (el, expectGray, label) => {
  const cs = await el.evaluate((n) => { const s = getComputedStyle(n); return { filter: s.filter, opacity: s.opacity } })
  const gray = cs.filter.includes('grayscale') && Number(cs.opacity) < 1
  check(gray === expectGray, `${label}视觉${expectGray ? '置灰（filter=' + cs.filter + ', opacity=' + cs.opacity + '）' : '正常（filter=' + cs.filter + '）'}`)
}
await visualGray(lbImg, true, 'lightbox 图片')
check(await page.locator('button:has-text("恢复展示")').count() === 1, '按钮切换为「恢复展示」')
check(await apiRejected(4) === true, 'API 确认 image#4 rejected=true（落库）')
await closeLightbox()
check(await thumbs.first().locator('span:text("已驳回")').count() === 1, '抽屉缩略图带「已驳回」标记')
check(await row.first().locator('span').filter({ hasText: /^驳\d/ }).count() === 1, '列表行出现「驳1」角标')

// ---------- 测试 B：恢复展示（原 bug 场景） ----------
console.log('\n[4] 恢复展示（原 bug 场景：点击后 UI 无反应/崩溃）')
await openLightbox()
check(await page.locator('button:has-text("恢复展示")').count() === 1, '已驳回图打开 lightbox 显示「恢复展示」按钮')
{
  const done = patchDone(4)
  await page.locator('button:has-text("恢复展示")').click()
  await done
}
check(await toastSays('已恢复展示该图片'), 'toast 提示「已恢复展示该图片」')
check(await lightboxRoot().count() === 1, 'lightbox 仍正常渲染（修复点：不再崩溃）')
check(await page.locator('text=已驳回 · 前台不展示').count() === 0, '「已驳回」徽标消失')
await visualGray(lbImg, false, 'lightbox 图片')
check(await page.locator('button:has-text("驳回此图")').count() === 1, '按钮切换回「驳回此图」')
check(await apiRejected(4) === false, 'API 确认 image#4 rejected=false（落库）')
await closeLightbox()
check(await thumbs.first().locator('span:text("已驳回")').count() === 0, '抽屉缩略图「已驳回」标记消失')
check(await row.first().locator('span').filter({ hasText: /^驳\d/ }).count() === 0, '列表行「驳N」角标消失')

// ---------- 测试 B2：第二张图驳回 + 多图状态隔离 ----------
console.log('\n[4.5] 第二张图驳回（多图状态隔离）')
await thumbs.nth(1).click()
await page.locator('button:has-text("驳回此图")').first().waitFor({ timeout: 5000 })
{
  const done = patchDone(5)
  await page.locator('button:has-text("驳回此图")').click()
  await done
}
check(await apiRejected(5) === true, 'API 确认 image#5 rejected=true')
check(await apiRejected(4) === false, 'image#4 状态不受影响（仍 false）')
await closeLightbox()
check(await thumbs.nth(1).locator('span:text("已驳回")').count() === 1, '第二张缩略图带「已驳回」标记')
check(await thumbs.first().locator('span:text("已驳回")').count() === 0, '第一张缩略图无「已驳回」标记')
check((await row.first().locator('span').filter({ hasText: /^驳\d/ }).innerText()) === '驳1', '列表角标为「驳1」（仅 1 张被驳回）')
await thumbs.nth(1).click()
await page.locator('button:has-text("恢复展示")').first().waitFor({ timeout: 5000 })
{
  const done = patchDone(5)
  await page.locator('button:has-text("恢复展示")').click()
  await done
}
check(await apiRejected(5) === false, 'image#5 恢复 rejected=false')
await closeLightbox()
check(await row.first().locator('span').filter({ hasText: /^驳\d/ }).count() === 0, '角标消失（多图互不干扰）')

// ---------- 测试 C：刷新持久化 ----------
console.log('\n[5] 刷新持久化')
await openLightbox()
{
  const done = patchDone(4)
  await page.locator('button:has-text("驳回此图")').click()
  await done
}
await page.reload()
const row2 = page.locator('tbody tr').filter({ hasText: CUSTOMER })
await row2.first().waitFor({ timeout: 10000 })
await row2.first().click()
await page.locator('h3:has-text("评价详情")').waitFor({ timeout: 5000 })
const thumbs2 = page.locator('div.grid button:has(img)')
await thumbs2.first().waitFor({ timeout: 5000 })
check(await thumbs2.first().locator('span:text("已驳回")').count() === 1, '驳回后刷新：仍显示「已驳回」')
await thumbs2.first().click()
await page.locator('button:has-text("恢复展示")').first().waitFor({ timeout: 5000 })
{
  const done = patchDone(4)
  await page.locator('button:has-text("恢复展示")').click()
  await done
}
await page.reload()
const row3 = page.locator('tbody tr').filter({ hasText: CUSTOMER })
await row3.first().waitFor({ timeout: 10000 })
await row3.first().click()
const thumbs3 = page.locator('div.grid button:has(img)')
await thumbs3.first().waitFor({ timeout: 5000 })
check(await thumbs3.first().locator('span:text("已驳回")').count() === 0, '恢复后刷新：无「已驳回」标记')

// ---------- 测试 D：前台展示联动 ----------
console.log('\n[6] 前台展示联动（后台驳回 → 前台隐藏）')
// page 仍停留在恢复后的详情抽屉（lightbox 已关）。重新打开 lightbox 驳回 image#4
await thumbs3.first().click()
await page.locator('button:has-text("驳回此图")').first().waitFor({ timeout: 5000 })
{
  const done = patchDone(4)
  await page.locator('button:has-text("驳回此图")').click()
  await done
}
check(await apiRejected(4) === true, '后台再次驳回 image#4')

let hiddenOk = false
for (let i = 0; i < 10 && !hiddenOk; i++) {
  await sleep(2000)
  hiddenOk = (await storeImageCount()) === 1
  if (!hiddenOk) console.log(`    …等待前台缓存失效（第 ${i + 1} 次）`)
}
check(hiddenOk, '前台该评价图片数 2 → 1（驳回图不再展示）')

// 恢复 → 前台回到 2 张
await page.locator('button:has-text("恢复展示")').first().waitFor({ timeout: 5000 })
{
  const done = patchDone(4)
  await page.locator('button:has-text("恢复展示")').click()
  await done
}
check(await apiRejected(4) === false, '后台恢复 image#4')
let restoredOk = false
for (let i = 0; i < 10 && !restoredOk; i++) {
  await sleep(2000)
  restoredOk = (await storeImageCount()) === 2
}
check(restoredOk, '前台该评价图片数 1 → 2（恢复后重新展示）')

// ---------- 现场还原断言 ----------
console.log('\n[7] 现场还原')
const finalImages = await apiImages()
check(finalImages.every((i) => !i.rejected), '评价 3 所有图片 rejected=false（现场已还原）')
check(failures.filter((f) => f.startsWith('页面 JS 错误')).length === 0, '全程无页面 JS 错误')

await storeCtx.close()
await browser.close()

console.log(failures.length ? `\n✗ 失败 ${failures.length} 项` : '\n✓ 全部通过')
process.exit(failures.length ? 1 : 0)
