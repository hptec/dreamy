// EAV 动态属性全链路联调验证（attr-eav 变更）
// 场景：管理员在 Wedding Dresses 根品类属性集新增属性 → 商品编辑动态出现/保存/回读 → 消费端 PDP 展示 + PLP 筛选
// 运行：node tests/ui-verification/attr-eav.verify.mjs
import { createRequire } from 'node:module'

const PW_ROOT = '/Users/harryhe/.npm/_npx/1ceecc7911b2a271/node_modules'
const req = createRequire(PW_ROOT + '/_resolve.js')
const { chromium } = req('playwright')

const ADMIN = 'http://localhost:5174'
const STORE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const CREDS = { email: 'admin@dreamy.com', password: 'Admin@123456' }
const SHOTS = '/Volumes/MAC/workspace/dreamy/tests/ui-verification/attr-eav-shots'

import fs from 'node:fs'
fs.mkdirSync(SHOTS, { recursive: true })

const results = []
function rec(id, status, detail = '') {
  results.push({ id, status, detail })
  console.log(`[${status}] ${id} ${detail ? '- ' + String(detail).slice(0, 220) : ''}`)
}
async function check(id, fn) {
  try { const d = await fn(); rec(id, 'PASS', d || '') } catch (e) { rec(id, 'FAIL', String(e?.message || e)) }
}
const ok = (c, m) => { if (!c) throw new Error(m) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const TEST_KEY = 'qa_train_style'

async function api(path, opts = {}, token) {
  const r = await fetch(API + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(opts.headers || {}) },
  })
  return r.json()
}

async function main() {
  const login = await api('/api/admin/auth/login', { method: 'POST', body: JSON.stringify(CREDS) })
  const token = login?.data?.token
  if (!token) { console.error('FATAL: 登录失败'); process.exit(2) }

  // ===== 1. 准备：新增属性 def + 加入 Wedding Dresses 根分类的属性集 =====
  let defId
  await check('API-01 新增属性定义（select qa_train_style）', async () => {
    const defs = await api('/api/admin/attribute-defs', {}, token)
    const existing = (defs.data?.items || defs.data || []).find?.((d) => d.key === TEST_KEY)
    if (existing) { defId = existing.id; return '已存在复用 id=' + defId }
    const res = await api('/api/admin/attribute-defs', {
      method: 'POST',
      body: JSON.stringify({ key: TEST_KEY, label: 'QA Train Style', type: 'select', options: ['Sweep', 'Chapel', 'Cathedral'] }),
    }, token)
    ok(res.code === 0 && res.data?.id, 'create def 失败: ' + JSON.stringify(res).slice(0, 200))
    defId = res.data.id
    return 'def id=' + defId
  })

  let setId
  await check('API-02 属性加入 Wedding Dresses 生效属性集', async () => {
    const cats = await api('/api/admin/categories', {}, token)
    const tree = cats.data?.items || cats.data || []
    const wedding = tree.find((c) => c.name === 'Wedding Dresses')
    ok(wedding, '找不到 Wedding Dresses 分类')
    setId = wedding.attribute_set_id
    ok(setId, '根分类未绑定属性集')
    const sets = await api('/api/admin/attribute-sets', {}, token)
    const set = (sets.data?.items || sets.data || []).find((s) => s.id === setId)
    ok(set, '找不到属性集 ' + setId)
    const items = set.items.map((i) => ({ attribute_id: i.attribute_id, visibility: i.visibility }))
    if (!items.some((i) => i.attribute_id === defId)) items.push({ attribute_id: defId, visibility: 'optional' })
    const res = await api('/api/admin/attribute-sets/' + setId, {
      method: 'PUT', body: JSON.stringify({ label: set.label, items }),
    }, token)
    ok(res.code === 0, 'update set 失败: ' + JSON.stringify(res).slice(0, 200))
    return `set ${setId} items=${items.length}`
  })

  // 找一个 Wedding Dresses 商品
  const list = await api('/api/admin/products?search=Aurelia', {}, token)
  const product = (list.data?.data || [])[0]
  ok(product, '找不到 Aurelia 商品')
  const pid = product.id

  // ===== 2. Admin UI：商品编辑动态出现新属性 → 选择保存 → 回读 =====
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const consoleErrors = []
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)) })

  await check('UI-01 admin 登录', async () => {
    await page.goto(ADMIN + '/login', { waitUntil: 'domcontentloaded' })
    await page.fill('input[type="email"], input[placeholder*="邮箱"], input[name="email"]', CREDS.email)
    await page.fill('input[type="password"]', CREDS.password)
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard**', { timeout: 10000 }).catch(() => {})
    await sleep(800)
  })

  await check('UI-02 商品编辑页出现动态属性 QA Train Style', async () => {
    await page.goto(`${ADMIN}/products/${pid}/edit`, { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(800)
    const label = page.locator('#sec-attrs label', { hasText: 'QA Train Style' })
    ok(await label.count() > 0, '属性区未渲染 QA Train Style')
    await page.screenshot({ path: SHOTS + '/admin-attrs-section.png', fullPage: false })
  })

  await check('UI-03 选择属性值并保存（保存并生成静态页，维持 published）', async () => {
    const select = page.locator('#sec-attrs select').filter({ has: page.locator('option', { hasText: 'Chapel' }) }).first()
    ok(await select.count() > 0, '未找到 QA Train Style 下拉')
    await select.selectOption('Chapel')
    const publishBtn = page.locator('button', { hasText: '保存并生成静态页' }).first()
    ok(await publishBtn.count() > 0, '未找到保存按钮')
    await publishBtn.click()
    await sleep(2500)
  })

  await check('UI-04 回读：编辑页重新打开后属性值保留', async () => {
    await page.goto(`${ADMIN}/products/${pid}/edit`, { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(800)
    const select = page.locator('#sec-attrs select').filter({ has: page.locator('option', { hasText: 'Chapel' }) }).first()
    const val = await select.inputValue()
    ok(val === 'Chapel', '回读值=' + val)
    await page.screenshot({ path: SHOTS + '/admin-attrs-saved.png', fullPage: false })
  })

  // ===== 3. 后端出参验证 =====
  await check('API-03 PDP detail attributes 含新属性', async () => {
    const d = await api('/api/store/products/' + product.slug)
    const attr = (d.data?.attributes || []).find((a) => a.key === TEST_KEY)
    ok(attr, 'PDP attributes 缺少 ' + TEST_KEY + '：' + JSON.stringify(d.data?.attributes?.map((a) => a.key)))
    ok(attr.values[0]?.value === 'Chapel', '值=' + JSON.stringify(attr.values))
    return JSON.stringify(attr)
  })

  await check('API-04 filters 端点含新维度 + attrs 筛选命中', async () => {
    const f = await api('/api/store/products/filters?category_id=29')
    ok((f.data?.items || []).some((i) => i.key === TEST_KEY), 'filters 缺少 ' + TEST_KEY)
    const r = await api(`/api/store/products?category_id=29&attr=${TEST_KEY}:Chapel`)
    const slugs = (r.data?.data || []).map((p) => p.slug)
    ok(slugs.includes(product.slug), '筛选未命中：' + JSON.stringify(slugs))
    return 'hits=' + JSON.stringify(slugs)
  })

  // ===== 4. Store UI：PDP 展示 + PLP 筛选 =====
  await check('UI-05 store PDP Details 显示新属性', async () => {
    await page.goto(`${STORE}/product/${product.slug}`, { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(1000)
    // Details accordion 默认收起，点开
    const detailsBtn = page.locator('button', { hasText: 'Details' }).first()
    if (await detailsBtn.count()) await detailsBtn.click()
    await sleep(400)
    const text = await page.locator('body').innerText()
    ok(/QA Train Style:\s*Chapel/.test(text), 'PDP 页面未见 QA Train Style: Chapel')
    await page.screenshot({ path: SHOTS + '/store-pdp-details.png', fullPage: false })
  })

  await check('UI-06 store PLP 动态筛选组渲染并可筛选', async () => {
    await page.goto(`${STORE}/wedding-dresses`, { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(1200)
    const group = page.locator('aside button', { hasText: 'QA Train Style' }).first()
    ok(await group.count() > 0, 'PLP 侧栏未见 QA Train Style 筛选组')
    const chapelRow = page.locator('aside button', { hasText: 'Chapel' }).first()
    ok(await chapelRow.count() > 0, '未见 Chapel 选项')
    await chapelRow.click()
    await page.waitForURL('**a_qa_train_style=Chapel**', { timeout: 8000 })
    await page.waitForLoadState('networkidle').catch(() => {})
    await sleep(1500)
    const text = await page.locator('body').innerText()
    ok(text.includes('Aurelia'), '筛选后未见 Aurelia 商品')
    await page.screenshot({ path: SHOTS + '/store-plp-filtered.png', fullPage: false })
    return page.url()
  })

  await check('UI-07 无 console error（admin+store 流程）', async () => {
    const fatal = consoleErrors.filter((e) => !/favicon|404|Failed to load resource|hydrat/i.test(e))
    ok(fatal.length === 0, JSON.stringify(fatal.slice(0, 3)))
  })

  await browser.close()

  const fail = results.filter((r) => r.status === 'FAIL').length
  console.log(`\n===== 结果：${results.length - fail}/${results.length} PASS =====`)
  process.exit(fail ? 1 : 0)
}

main().catch((e) => { console.error('FATAL', e); process.exit(2) })
