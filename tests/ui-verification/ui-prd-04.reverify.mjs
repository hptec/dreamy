// L4 UI 聚焦复验：UI-PRD-04（ISS-L4U-001 / ALIGN-007）—— Products「更多筛选」5 组筛选
// 运行方式：node tests/ui-verification/ui-prd-04.reverify.mjs
// 产出：stdout 逐条结果 + l4-state/raw-results-reverify-ui-prd-04.json
import { createRequire } from 'node:module'
import fs from 'node:fs'

const PW_ROOT = '/Users/harryhe/.npm/_npx/1ceecc7911b2a271/node_modules'
const req = createRequire(PW_ROOT + '/_resolve.js')
const { chromium } = req('playwright')

const BASE = 'http://localhost:5174'
const OUT = '/Volumes/MAC/workspace/dreamy/hhspec/pd-sessions/admin-prototype-alignment/l4-state'
const CREDS = { email: 'admin@dreamy.com', password: 'Admin@123456' }

const results = []
const consoleErrors = []
function rec(id, status, detail = '') {
  results.push({ id, status, detail })
  console.log(`[${status}] ${id} ${detail ? '- ' + String(detail).slice(0, 300) : ''}`)
}
async function check(id, fn) {
  try { rec(id, 'PASS', (await fn()) || '') }
  catch (e) { rec(id, 'FAIL', String((e && e.message) || e).replace(/\n/g, ' ').slice(0, 400)) }
}
function ok(cond, msg) { if (!cond) throw new Error(msg) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)) })
  page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + String(e).slice(0, 300)))

  // ---- 登录（复用上轮流程） ----
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.locator('#login-email').fill(CREDS.email)
  await page.locator('input[type="password"]').fill(CREDS.password)
  await page.locator('button[type="submit"]').click()
  await page.waitForURL((u) => u.pathname === '/dashboard' || u.pathname === '/', { timeout: 10000 })
  console.log('login OK ->', new URL(page.url()).pathname)

  // ---- /products → 展开更多筛选 ----
  await page.goto(BASE + '/products', { waitUntil: 'domcontentloaded' })
  await page.waitForLoadState('networkidle').catch(() => {})
  await sleep(300)
  const moreBtn = page.getByRole('button', { name: /更多筛选/ })
  await moreBtn.click()
  // 等 rowMeta 懒加载（详情接口逐行拉取）
  await page.waitForLoadState('networkidle').catch(() => {})
  await sleep(800)

  const panel = page.locator('div[title="高级筛选为当前页过滤"]')
  const badge = moreBtn.locator('span.bg-gold')
  const groupDiv = (title) => panel.locator('div').filter({ has: page.locator(`p:has-text("${title}")`) }).last()

  // ---- 断言 1：5 组标题齐备 ----
  await check('UI-PRD-04/groups', async () => {
    ok(await panel.isVisible(), '更多筛选展开面板不可见')
    const missing = []
    for (const g of ['商品类型', '库存状态', '标记', '价格区间', '主题标签']) {
      if ((await panel.locator(`p:has-text("${g}")`).count()) === 0) missing.push(g)
    }
    ok(missing.length === 0, `面板缺筛选组: ${missing.join('、')}（期望 5 组）`)
    const titles = await panel.locator('p.mb-2').allInnerTexts()
    return '5 组标题齐备: ' + titles.map((t) => t.replace(/\s+/g, '')).join(' | ')
  })

  // ---- 断言 2：各组控件存在 ----
  let productTypeChips = 0
  await check('UI-PRD-04/controls', async () => {
    productTypeChips = await groupDiv('商品类型').locator('button.rounded-full').count()
    const typeFallback = await groupDiv('商品类型').locator('span.text-ink-faint').count()
    ok(productTypeChips > 0 || typeFallback > 0, '商品类型组无 chip 也无降级提示')
    const stockChips = await groupDiv('库存状态').locator('button.rounded-full').count()
    ok(stockChips >= 3, `库存状态 chip 过少: ${stockChips}`)
    const flagChips = await groupDiv('标记').locator('button.rounded-full').count()
    ok(flagChips >= 2, `标记 chip 过少: ${flagChips}`)
    ok((await panel.locator('input[placeholder="最低 $"]').count()) === 1, '价格区间缺 最低 $ 输入')
    ok((await panel.locator('input[placeholder="最高 $"]').count()) === 1, '价格区间缺 最高 $ 输入')
    const tagChips = await groupDiv('主题标签').locator('button.rounded-full').count()
    ok(tagChips > 0, '主题标签组无可选标签 chip')
    return `chips: 商品类型=${productTypeChips} 库存=${stockChips} 标记=${flagChips} 价格=2 inputs 主题标签=${tagChips}`
  })

  // ---- 断言 3：商品类型交互 → 徽章 +1 ----
  await check('UI-PRD-04/interact-productType', async () => {
    ok((await badge.count()) === 0, '初始态不应有 activeMoreCount 徽章')
    if (productTypeChips === 0) {
      // 数据降级：本页商品未填写类型 → 仅核对降级文案，交互改由主题标签覆盖
      const fb = (await groupDiv('商品类型').locator('span.text-ink-faint').first().innerText()).trim()
      return `本页无商品类型数据（降级文案「${fb}」），chip 交互由主题标签组覆盖`
    }
    const chip = groupDiv('商品类型').locator('button.rounded-full').first()
    const label = (await chip.innerText()).trim()
    await chip.click()
    await sleep(200)
    ok((await badge.innerText()).trim() === '1', `选商品类型「${label}」后徽章应=1，实际: ${await badge.innerText().catch(() => '(无)')}`)
    ok((await chip.getAttribute('class')).includes('border-gold'), '选中 chip 未高亮')
    return `选商品类型「${label}」→ 徽章=1 + chip 高亮`
  })

  // ---- 断言 4：主题标签交互 → 徽章累加 ----
  let expectedCount = productTypeChips > 0 ? 1 : 0
  await check('UI-PRD-04/interact-tag', async () => {
    const chip = groupDiv('主题标签').locator('button.rounded-full').first()
    const label = (await chip.innerText()).trim()
    await chip.click()
    await sleep(200)
    expectedCount++
    const v = (await badge.innerText()).trim()
    ok(v === String(expectedCount), `选主题标签「${label}」后徽章应=${expectedCount}，实际: ${v}`)
    ok((await chip.getAttribute('class')).includes('border-gold'), '选中标签 chip 未高亮')
    return `选主题标签「${label}」→ 徽章=${expectedCount} + chip 高亮`
  })

  // ---- 断言 5：清除筛选 → 徽章消失 + chips 复位 ----
  await check('UI-PRD-04/reset', async () => {
    await panel.getByRole('button', { name: /清除筛选/ }).click()
    await sleep(200)
    ok((await badge.count()) === 0, '清除筛选后徽章未消失')
    const selected = await panel.locator('button.rounded-full.border-gold, button.rounded-full[class*="bg-gold/10"]').count()
    // stockLevel=all 的「全部」chip 复位后为选中态，属正常；只验证多选组无残留
    const typeSel = await groupDiv('商品类型').locator('button[class*="bg-gold/10"]').count()
    const tagSel = await groupDiv('主题标签').locator('button[class*="bg-gold/10"]').count()
    ok(typeSel === 0 && tagSel === 0, `重置后仍有选中 chip: 商品类型=${typeSel} 主题标签=${tagSel}（面板内总高亮=${selected}）`)
    return '清除筛选 → 徽章消失，商品类型/主题标签选中态清空'
  })

  await page.screenshot({ path: OUT + '/screenshots/products-more-filters-reverify.png', fullPage: true })

  // ---- 断言 6（补强）：商品类型 chip 交互——库内本页商品 product_type 全空，
  //      经 route 拦截详情接口注入 product_type（响应改写，不落库）验证 chip 渲染/选中/徽章/重置 ----
  await check('UI-PRD-04/interact-productType-mocked', async () => {
    await page.route(/\/api\/admin\/products\/\d+$/, async (route) => {
      if (route.request().method() !== 'GET') return route.fallback()
      const res = await route.fetch()
      const body = await res.json()
      if (body && body.data) body.data.product_type = 'L4 Mock 类型'
      await route.fulfill({ response: res, body: JSON.stringify(body) })
    })
    await page.goto(BASE + '/products', { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(300)
    await page.getByRole('button', { name: /更多筛选/ }).click()
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(800)
    const chip = groupDiv('商品类型').locator('button.rounded-full', { hasText: 'L4 Mock 类型' }).first()
    ok((await chip.count()) > 0, '注入 product_type 后商品类型组仍无 chip')
    await chip.click()
    await sleep(200)
    const b = page.getByRole('button', { name: /更多筛选/ }).locator('span.bg-gold')
    ok((await b.innerText()).trim() === '1', `选商品类型后徽章应=1，实际: ${await b.innerText().catch(() => '(无)')}`)
    ok((await chip.getAttribute('class')).includes('border-gold'), '选中商品类型 chip 未高亮')
    await panel.getByRole('button', { name: /清除筛选/ }).click()
    await sleep(200)
    ok((await b.count()) === 0, '清除筛选后徽章未消失')
    ok(!(await chip.getAttribute('class')).includes('bg-gold/10'), '重置后商品类型 chip 选中态未清空')
    await page.unroute(/\/api\/admin\/products\/\d+$/)
    return '注入 product_type 后：chip 渲染 → 点击徽章=1+高亮 → 清除筛选复位 [详情响应 mock 改写，未落库]'
  })

  await browser.close()

  const summary = { PASS: 0, FAIL: 0 }
  for (const r of results) summary[r.status]++
  fs.writeFileSync(OUT + '/raw-results-reverify-ui-prd-04.json',
    JSON.stringify({ date: '2026-06-11', target: 'UI-PRD-04 (ISS-L4U-001)', summary, results, consoleErrors }, null, 2))
  console.log('\n==== SUMMARY ====')
  console.log(JSON.stringify(summary))
  console.log('console errors:', consoleErrors.length ? consoleErrors.join(' || ') : '(none)')
  process.exit(summary.FAIL ? 1 : 0)
}

main().catch((e) => { console.error('FATAL', e); process.exit(2) })
