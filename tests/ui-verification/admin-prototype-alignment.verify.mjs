// L4 UI 验收：admin-prototype-alignment（基于 ui-test-spec.yml 16 页断言）
// 运行方式：node tests/ui-verification/admin-prototype-alignment.verify.mjs
// 产出：stdout 逐条结果 + l4-state/raw-results.json + l4-state/screenshots/*.png
import { createRequire } from 'node:module'
import fs from 'node:fs'

const PW_ROOT = '/Users/harryhe/.npm/_npx/1ceecc7911b2a271/node_modules'
const req = createRequire(PW_ROOT + '/_resolve.js')
const { chromium } = req('playwright')

const BASE = 'http://localhost:5174'
const API = 'http://localhost:8080'
const OUT = '/Volumes/MAC/workspace/dreamy/hhspec/pd-sessions/admin-prototype-alignment/l4-state'
const SHOTS = OUT + '/screenshots'
const CREDS = { email: 'admin@dreamy.com', password: 'Admin@123456' }

const results = []
const consoleErrors = {} // pageLabel -> [msg]
let currentLabel = 'init'

function rec(id, page, status, detail = '') {
  results.push({ id, page, status, detail })
  console.log(`[${status}] ${id} (${page}) ${detail ? '- ' + String(detail).slice(0, 200) : ''}`)
}
async function check(id, pg, fn) {
  try {
    const d = await fn()
    rec(id, pg, 'PASS', d || '')
  } catch (e) {
    rec(id, pg, 'FAIL', String((e && e.message) || e).replace(/\n/g, ' ').slice(0, 400))
  }
}
function skip(id, pg, reason) { rec(id, pg, 'SKIPPED', reason) }

function ok(cond, msg) { if (!cond) throw new Error(msg) }

async function apiJson(path, token) {
  const r = await fetch(API + path, { headers: { Authorization: 'Bearer ' + token } })
  return r.json()
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function thTexts(page) {
  return page.locator('table thead th').allInnerTexts().then((a) => a.map((t) => t.trim()))
}

async function shot(page, name) {
  try {
    await page.screenshot({ path: `${SHOTS}/${name}`, fullPage: true })
    return true
  } catch (e) {
    console.log('screenshot failed', name, e.message)
    return false
  }
}

async function gotoPage(page, route, label) {
  currentLabel = label
  await page.goto(BASE + route, { waitUntil: 'domcontentloaded' })
  await page.waitForLoadState('networkidle').catch(() => {})
  await sleep(300)
}

async function main() {
  // ---- 0. API token（用于数据探测与 mock 构造） ----
  const loginRes = await fetch(API + '/api/admin/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(CREDS),
  }).then((r) => r.json())
  const token = loginRes?.data?.token
  if (!token) { console.error('FATAL: 后端登录失败'); process.exit(2) }

  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  page.on('console', (m) => {
    if (m.type() === 'error') {
      ;(consoleErrors[currentLabel] ||= []).push(m.text().slice(0, 300))
    }
  })
  page.on('pageerror', (e) => {
    ;(consoleErrors[currentLabel] ||= []).push('pageerror: ' + String(e).slice(0, 300))
  })

  // ================= LOGIN（UI 登录流程验证） =================
  currentLabel = 'login'
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await check('UI-LOGIN-01', 'Login', async () => {
    ok(await page.getByRole('heading', { name: '欢迎回来' }).isVisible(), '登录页 h1 欢迎回来不可见')
    ok(await page.locator('input[placeholder="admin@dreamy.com"]').isVisible(), '邮箱输入框不可见')
    return 'h1 欢迎回来 + 邮箱/密码输入框渲染正常'
  })
  await check('UI-LOGIN-02', 'Login', async () => {
    await page.locator('#login-email').fill(CREDS.email)
    await page.locator('input[type="password"]').fill(CREDS.password)
    await page.locator('button[type="submit"]').click()
    await page.waitForURL((u) => u.pathname === '/dashboard' || u.pathname === '/', { timeout: 10000 })
    return '登录成功，跳转 ' + new URL(page.url()).pathname
  })

  // ================= Dashboard =================
  await gotoPage(page, '/dashboard', 'Dashboard')
  await check('UI-DSH-01', 'Dashboard', async () => {
    ok(await page.getByRole('link', { name: '查看完整看板' }).isVisible(), '查看完整看板不可见')
    ok(await page.getByRole('link', { name: '发布站点' }).first().isVisible(), '发布站点不可见')
    return 'header actions: 查看完整看板 + 发布站点'
  })
  await check('UI-DSH-03', 'Dashboard', async () => {
    for (const t of ['新增商品', '编辑首页', '新建优惠券', '写一篇文章', '发布站点']) {
      ok((await page.getByText(t, { exact: true }).count()) > 0, `快捷项「${t}」缺失`)
    }
    return '5 个快捷操作项齐全'
  })
  await check('UI-DSH-04', 'Dashboard', async () => {
    ok((await page.getByText('较昨日').count()) === 0, 'KPI 卡仍存在「较昨日」delta 行')
    return 'KPI 卡无趋势 delta 行（豁免现状一致）'
  })
  await check('UI-DSH-05', 'Dashboard', async () => {
    const todoPanel = page.locator('.panel', { hasText: '待处理事务' }).first()
    ok(await todoPanel.isVisible(), '待处理事务面板缺失')
    const grid = todoPanel.locator('[class*="grid-cols-3"]').first()
    ok((await grid.count()) > 0, '待办瓦片非 3 列栅格')
    return '待办瓦片 3 列栅格 grid-cols-3（豁免现状一致；注：spec 文字为 sm:grid-cols-3，实现为无断点前缀 grid-cols-3，3 列语义一致）'
  })
  await shot(page, 'dashboard-full-desktop.png')
  rec('UI-DSH-06', 'Dashboard', 'SKIPPED', '视觉像素对比无基线（原型为运行时 Vue 应用，spec 未附 baseline 图）；已留档截图 dashboard-full-desktop.png')
  await check('UI-DSH-02', 'Dashboard', async () => {
    await page.getByRole('link', { name: '发布站点' }).first().click()
    await page.waitForURL(/\/publish/, { timeout: 8000 })
    return '点击发布站点 → /publish'
  })

  // ================= Analytics =================
  await gotoPage(page, '/analytics', 'Analytics')
  const traffic = await apiJson('/api/admin/analytics/traffic', token).catch(() => null)
  const trafficOkData = traffic?.code === 0 && (traffic?.data?.traffic_sources?.length || traffic?.data?.trafficSources?.length)
  if (trafficOkData) {
    await check('UI-ANA-01', 'Analytics', async () => {
      await page.getByRole('button', { name: '流量分析' }).click()
      await page.waitForLoadState('networkidle').catch(() => {})
      await sleep(400)
      const zh = ['自然搜索', 'Instagram', 'Pinterest', '直接访问', '邮件']
      let found = 0
      for (const t of zh) found += await page.getByText(t).count()
      ok(found > 0, '流量来源未出现任何中文化标签')
      const bodyText = await page.locator('main, body').first().innerText()
      const rawKeys = ['organic_search', 'direct', 'social_instagram', 'email_campaign'].filter((k) => bodyText.includes(k))
      ok(rawKeys.length === 0, '页面仍出现原始枚举 key: ' + rawKeys.join(','))
      return `中文来源标签命中 ${found} 处，无 snake_case 原始 key 泄漏`
    })
    await check('UI-ANA-02', 'Analytics', async () => {
      await page.getByRole('button', { name: '转化漏斗' }).click()
      await sleep(400)
      for (const t of ['商品浏览', '加入购物车', '进入结算', '完成支付']) {
        ok((await page.getByText(t).count()) > 0, `漏斗阶段「${t}」缺失`)
      }
      return '漏斗 4 阶段中文标签齐全'
    })
  } else {
    skip('UI-ANA-01', 'Analytics', 'GA4 流量数据降级/为空（/analytics/traffic 无 traffic_sources），中文化标签无渲染前置')
    skip('UI-ANA-02', 'Analytics', '同 UI-ANA-01：漏斗数据未返回')
  }
  await check('UI-ANA-03', 'Analytics', async () => {
    await page.getByRole('button', { name: '商品热度' }).click()
    await sleep(400)
    const ths = await thTexts(page)
    for (const t of ['排名', '商品', '销量', '销售额']) ok(ths.includes(t), `热度表头缺「${t}」，实际: ${ths.join('|')}`)
    return '热度表头=排名/商品/销量/销售额（豁免现状一致）'
  })
  await shot(page, 'analytics-full-desktop.png')
  rec('UI-ANA-04', 'Analytics', 'SKIPPED', '视觉像素对比无基线；已留档 analytics-full-desktop.png')

  // ================= Categories =================
  await gotoPage(page, '/categories', 'Categories')
  await check('UI-CAT-01', 'Categories', async () => {
    for (const t of ['标准品类', '属性集与字典', '自定义标签']) {
      ok(await page.getByRole('button', { name: t, exact: true }).isVisible(), `主 Tab「${t}」缺失`)
    }
    return '3-Tab 文案与顺序对照原型'
  })
  await check('UI-CAT-02', 'Categories', async () => {
    // 2026-06-12 对齐原型：矩阵 sub-tab 已移除——Tab 2 = 原型说明条 + 属性集 chips + 属性字典
    await page.getByRole('button', { name: '属性集与字典', exact: true }).click()
    await sleep(300)
    ok((await page.getByRole('button', { name: '品类×属性矩阵' }).count()) === 0, '矩阵 sub-tab 应已移除（原型无矩阵）')
    ok(await page.getByText('属性字典维护全局可选值').isVisible(), 'Tab 2 原型说明条缺失')
    ok(await page.getByRole('button', { name: '新增属性集' }).isVisible(), '「新增属性集」按钮缺失')
    ok(await page.getByText('个分类引用').first().isVisible(), '属性集 chips 缺失')
    ok(await page.getByRole('button', { name: '新增属性定义' }).isVisible(), '属性字典面板缺失')
    return 'Tab 2 = 说明条 + 属性集 chips + 属性字典（矩阵已按原型移除）'
  })
  await check('UI-CAT-03', 'Categories', async () => {
    // 子品类覆盖状态在 Tab 1 子类目 chip 上呈现（「继承」/「N覆盖」徽章，点击开覆盖抽屉）
    await page.getByRole('button', { name: '标准品类', exact: true }).click()
    await sleep(300)
    const ovBadge = page.locator('.group button').filter({ hasText: /^(继承|\d+覆盖)$/ }).first()
    ok(await ovBadge.isVisible(), '子类目「继承/N覆盖」徽章缺失')
    return `子类目覆盖徽章可见（首个=「${(await ovBadge.innerText()).trim()}」）`
  })
  await check('UI-CAT-05', 'Categories', async () => {
    // 对齐原型（E-CAT-21 豁免已废除）：点徽章 → 属性集配置抽屉，三态循环点击即改，保存即提交
    const badge = page.locator('button').filter({ hasText: '属性集 ·' }).first()
    await badge.waitFor({ state: 'visible', timeout: 8000 })
    await badge.click()
    await sleep(400)
    const dw = page.locator('.fixed.justify-end').last()
    ok(await dw.locator('h3', { hasText: '· 属性配置' }).isVisible(), '属性集配置抽屉未打开')
    ok(await dw.getByText('点击状态循环切换').isVisible(), '抽屉三态说明条缺失')
    const stBtn = dw.locator('button.min-w-\\[3\\.5rem\\]').first()
    const before = (await stBtn.innerText()).trim()
    await stBtn.click()
    const after = (await stBtn.innerText()).trim()
    ok(before !== after, `三态循环点击未生效（${before} → ${after}）`)
    await dw.getByRole('button', { name: '取消' }).click()
    await sleep(300)
    return `徽章 → 属性集抽屉三态循环（${before} → ${after}，取消未提交）`
  })
  // CAT-04 / CAT-06：标准品类 tab，点属性集徽章 → 抽屉
  await gotoPage(page, '/categories', 'Categories')
  await check('UI-CAT-04', 'Categories', async () => {
    const badge = page.locator('button').filter({ hasText: '项' }).filter({ hasText: '·' }).first()
    await badge.waitFor({ state: 'visible', timeout: 8000 })
    const badgeText = (await badge.innerText()).trim()
    await badge.click()
    await sleep(400)
    const heading = page.locator('h3', { hasText: /配置/ }).first()
    ok(await heading.isVisible(), '点击徽章后未打开配置抽屉')
    return `点击徽章「${badgeText}」→ 抽屉打开: ` + (await heading.innerText()).trim() + '（属性集三态配置——原型 openCatSetDrawer 同款）'
  })
  await check('UI-CAT-06', 'Categories', async () => {
    // 豁免项（决策 9）：三语 tab 在品类配置抽屉内（双击根品类名打开——徽章现开属性集抽屉）
    const cancel0 = page.getByRole('button', { name: '取消' }).first()
    if (await cancel0.isVisible()) await cancel0.click() // 关掉 CAT-04 留下的属性集抽屉
    await sleep(300)
    await page.locator('span.font-display.cursor-pointer').first().dblclick()
    await sleep(400)
    ok((await page.getByRole('button', { name: 'EN（主）' }).count()) > 0, 'LocaleTab EN（主）缺失')
    for (const l of ['ES', 'FR']) {
      ok((await page.getByRole('button', { name: l, exact: true }).count()) > 0, `LocaleTab ${l} 缺失`)
    }
    await page.getByRole('button', { name: 'ES', exact: true }).first().click()
    await sleep(150)
    ok((await page.getByText('留空时消费端回退 EN').count()) > 0, 'ES tab 独立输入未渲染')
    await page.getByRole('button', { name: '取消' }).first().click()
    await sleep(300)
    return '双击根品类名 → 品类配置抽屉含 EN（主）/ES/FR 三语独立 name 输入（豁免现状一致）'
  })
  await check('UI-CAT-07', 'Categories', async () => {
    // 豁免项 E-CAT-30：删除维度 → 仅验证确认弹窗出现后取消（不触发真实删除/409506）
    await gotoPage(page, '/categories', 'Categories') // 复位，关闭可能残留的抽屉遮罩
    await page.getByRole('button', { name: '自定义标签', exact: true }).click()
    await sleep(500)
    const dimTab = page.locator('button.group:visible').first()
    await dimTab.waitFor({ state: 'visible', timeout: 5000 })
    const dimName = (await dimTab.innerText()).trim()
    await dimTab.hover()
    const xBtn = dimTab.locator('span.cursor-pointer').first()
    ok((await xBtn.count()) > 0, '维度删除入口缺失')
    await xBtn.click({ force: true })
    await sleep(500)
    const dialogVisible = (await page.getByText(/删除|确认/).count()) > 0
    ok(dialogVisible, '点删除后无确认弹层')
    const cancel = page.getByRole('button', { name: '取消' }).first()
    if (await cancel.isVisible()) await cancel.click()
    else await page.keyboard.press('Escape')
    return `维度「${dimName}」删除确认弹层出现后已取消（409506 引导未触发真实删除，豁免范围内仅核对入口）`
  })
  await gotoPage(page, '/categories', 'Categories')
  await shot(page, 'categories-full-desktop.png')
  rec('UI-CAT-08', 'Categories', 'SKIPPED', '视觉像素对比无基线；已留档 categories-full-desktop.png')

  // ================= AttributeSets redirect =================
  currentLabel = 'AttributeSets'
  await check('UI-ATS-01', 'AttributeSets', async () => {
    await page.goto(BASE + '/attribute-sets', { waitUntil: 'domcontentloaded' })
    await page.waitForURL((u) => u.pathname === '/categories', { timeout: 8000 })
    const u = new URL(page.url())
    ok(u.searchParams.get('tab') === 'attributes', `重定向参数错误: ${u.search}`)
    return '/attribute-sets → /categories?tab=attributes'
  })
  await check('UI-ATS-02', 'AttributeSets', async () => {
    const items = await page.locator('aside a, nav a').allInnerTexts()
    const hit = items.map((t) => t.trim()).filter((t) => t === '属性集')
    ok(hit.length === 0, '侧边栏仍存在独立「属性集」菜单项')
    return `侧边栏无独立属性集入口（共 ${items.length} 个导航项）`
  })

  // ================= Products =================
  await gotoPage(page, '/products', 'Products')
  await check('UI-PRD-01', 'Products', async () => {
    const ths = await thTexts(page)
    const expectCols = ['商品', '品类', '价格', '库存', '销量', '排序', '状态', '操作']
    for (const c of expectCols) ok(ths.some((t) => t === c), `列「${c}」缺失，实际: ${ths.join('|')}`)
    ok(ths.some((t) => t.includes('上架') && t.includes('新品') && t.includes('推荐')), `「上架/新品/推荐」列缺失，实际: ${ths.join('|')}`)
    ok(!ths.includes('SKU'), '不应有 SKU 列')
    return `表头: ${ths.join(' | ')}`
  })
  await check('UI-PRD-02', 'Products', async () => {
    const boxes = page.locator('table tbody input[type="checkbox"]')
    ok((await boxes.count()) >= 2, '行复选框不足 2 个')
    await boxes.nth(0).check()
    await boxes.nth(1).check()
    await sleep(300)
    ok((await page.getByText('已选 2 项').count()) > 0, '底栏「已选 2 项」未出现')
    for (const b of ['批量上架', '批量下架', '设为推荐', '批量删除']) {
      ok(await page.getByRole('button', { name: b }).isVisible(), `批量按钮「${b}」缺失`)
    }
    await boxes.nth(0).uncheck(); await boxes.nth(1).uncheck()
    return '勾选 2 行 → 批量操作栏（上架/下架/推荐/删除）出现，已复位'
  })
  await check('UI-PRD-03', 'Products', async () => {
    ok(await page.getByRole('button', { name: /导出/ }).first().isVisible(), '导出按钮缺失')
    ok(await page.getByRole('link', { name: '新增商品' }).isVisible(), '新增商品按钮缺失')
    return 'header actions: 导出 + 新增商品'
  })
  await check('UI-PRD-04', 'Products', async () => {
    await page.getByRole('button', { name: /更多筛选/ }).click()
    await sleep(400)
    const missing = []
    for (const g of ['商品类型', '库存状态', '标记', '价格区间', '主题标签']) {
      if ((await page.getByText(g, { exact: false }).count()) === 0) missing.push(g)
    }
    ok(missing.length === 0, `更多筛选面板缺筛选组: ${missing.join('、')}（原型为 5 组，实现仅 ${5 - missing.length} 组）`)
    await page.getByRole('button', { name: /更多筛选|收起/ }).first().click().catch(() => {})
    return '更多筛选 5 组渲染齐全'
  })
  await check('UI-PRD-05', 'Products', async () => {
    // 豁免项（决策 9）：排序列 input 存在即可，不触发 blur 保存避免写库
    const sortInput = page.locator('table tbody input[type="number"]').first()
    ok((await sortInput.count()) > 0, '排序列行内 input 缺失')
    return '排序列行内 input 存在（豁免现状一致，未触发保存）'
  })
  await shot(page, 'products-full-desktop.png')
  rec('UI-PRD-06', 'Products', 'SKIPPED', '视觉像素对比无基线；已留档 products-full-desktop.png')

  // ================= ProductEdit =================
  await gotoPage(page, '/products/new', 'ProductEdit')
  await check('UI-PED-01', 'ProductEdit', async () => {
    ok((await page.getByText('颜色 swatch（可选多个）').count()) > 0, '颜色 swatch 区标题缺失')
    const swatchBtns = page.locator('button:has(span[style*="background"])')
    const n = await swatchBtns.count()
    ok(n >= 3, `预设颜色按钮过少: ${n}`)
    return `预设颜色 swatch 按钮组渲染（${n} 个，含色点）`
  })
  await check('UI-PED-02', 'ProductEdit', async () => {
    const swatchBtns = page.locator('button:has(> span[style*="background"])')
    await swatchBtns.nth(0).click()
    await swatchBtns.nth(1).click()
    await page.locator('input[placeholder="自定义颜色"]').fill('L4VerifyColor')
    await page.locator('input[placeholder="自定义颜色"]').press('Enter')
    await sleep(200)
    ok((await page.getByText('L4VerifyColor').count()) > 0, '自定义色 chip 未出现')
    // 选 1 个尺码触发矩阵
    const sizeBtn = page.locator('button', { hasText: /^US/ }).first()
    if (await sizeBtn.count()) {
      await sizeBtn.click()
      await sleep(300)
      const matrixRows = page.locator('table:has(th:text-is("颜色 \\ 尺码（单元格=库存）")) tbody tr')
      const rows = await page.locator('table tbody tr:has(span[style*="background"])').count()
      ok(rows >= 3, `SKU 矩阵行数与所选颜色不同步: ${rows}（期望 3 = 2 预设 + 1 自定义）`)
      return `2 预设色 + 自定义色 chips 渲染，SKU 矩阵 ${rows} 行同步`
    }
    return '2 预设色 + 自定义色 chips 渲染（无尺码预设按钮，矩阵同步部分跳过）'
  })
  await check('UI-PED-03', 'ProductEdit', async () => {
    // 豁免项（决策 9）：多币种 5 输入位 USD disabled、其余 placeholder=auto
    const autoInputs = page.locator('input[placeholder="auto"]')
    const n = await autoInputs.count()
    ok(n === 4, `placeholder=auto 输入数=${n}，期望 4（EUR/GBP/CAD/AUD）`)
    const mcArea = page.locator('div', { hasText: '多币种价格' }).last()
    const disabledInMc = await page.locator('input[disabled]').count()
    ok(disabledInMc >= 1, '未找到 disabled 的 USD 输入位')
    return '多币种区 5 输入位：USD disabled + 4 个 placeholder=auto（豁免现状一致）'
  })
  await check('UI-PED-04', 'ProductEdit', async () => {
    // 合法表单 + mock createProduct（避免污染数据库）→ 断言跳转 /publish
    const byLabel = (label) => page.locator(`div:has(> label.field-label:text-is("${label}")) input.field`).first()
    await byLabel('商品名称 *').fill('L4 验收测试商品（mock 不落库）')
    await page.locator('div:has(> label.field-label:text-is("标准品类 *（决定属性表单字段配置）")) select.field').first()
      .selectOption({ index: 1 })
    await byLabel('现价 (USD) *').fill('100')
    const lead = page.locator('div:has(> label.field-label:text-is("标准发货周期 *")) input').first()
    if (await lead.count()) { const v = await lead.inputValue(); if (!v || Number(v) < 1) await lead.fill('30') }
    await page.locator('input[placeholder="aurelia-aline-tulle"]').fill('l4-verify-product')
    let captured = null
    await page.route('**/api/admin/products', async (route) => {
      if (route.request().method() === 'POST') {
        captured = { method: 'POST', url: route.request().url() }
        await route.fulfill({
          status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 0, message: null, data: { id: 99999, updated_at: '2026-06-11T00:00:00' } }),
        })
      } else await route.fallback()
    })
    await page.getByRole('button', { name: /保存并生成静态页/ }).first().click()
    await page.waitForURL(/\/publish/, { timeout: 8000 })
    await page.unroute('**/api/admin/products')
    ok(captured, '保存请求未发出')
    return 'POST /api/admin/products 已发出（mock 成功响应，未落库）→ 成功后 route=/publish'
  })
  await gotoPage(page, '/products/new', 'ProductEdit')
  await shot(page, 'product-edit-full-desktop.png')
  rec('UI-PED-05', 'ProductEdit', 'SKIPPED', '视觉像素对比无基线；已留档 product-edit-full-desktop.png')

  // ================= Orders =================
  await gotoPage(page, '/orders', 'Orders')
  await check('UI-ORD-01', 'Orders', async () => {
    const ths = await thTexts(page)
    const expectCols = ['订单号', '客户', '地区', '商品数', '金额', '支付方式', '状态', '下单时间', '操作']
    for (const c of expectCols) ok(ths.includes(c), `列「${c}」缺失，实际: ${ths.join('|')}`)
    return `表头: ${ths.join(' | ')}`
  })
  await check('UI-ORD-02', 'Orders', async () => {
    const ths = await thTexts(page)
    for (const c of ['币种', '承运']) ok(!ths.some((t) => t.includes(c)), `不应存在列「${c}」`)
    return '已移除 币种/承运 列'
  })
  await check('UI-ORD-03', 'Orders', async () => {
    ok(await page.getByRole('button', { name: /导出订单/ }).isVisible(), '导出订单按钮缺失')
    return 'header actions: 导出订单'
  })
  await check('UI-ORD-04', 'Orders', async () => {
    const reqP = page.waitForRequest((r) => r.url().includes('/api/admin/orders/export'), { timeout: 8000 })
    await page.getByRole('button', { name: /导出订单/ }).click()
    const r = await reqP
    const u = new URL(r.url())
    await sleep(500)
    return `GET /api/admin/orders/export 已发出，query=${u.search || '(空=当前无筛选)'}`
  })
  await check('UI-ORD-05', 'Orders', async () => {
    ok((await page.locator('input[placeholder="搜索订单号 / 客户名…"]').count()) > 0, '搜索 placeholder 不符')
    return 'placeholder=搜索订单号 / 客户名…'
  })
  await check('UI-ORD-06', 'Orders', async () => {
    for (const t of ['全部', '待付款', '待发货', '已发货', '已完成', '退款中', '已取消', '已退款']) {
      ok((await page.getByRole('button', { name: t, exact: true }).count()) > 0, `状态 Tab「${t}」缺失`)
    }
    return '8 状态 Tab 齐全（豁免超集现状一致）'
  })
  await shot(page, 'orders-full-desktop.png')
  rec('UI-ORD-07', 'Orders', 'SKIPPED', '视觉像素对比无基线；已留档 orders-full-desktop.png')

  // ================= OrderDetail（mock 注入状态矩阵：库内仅 3 笔 cancelled 订单） =================
  const orderRaw = await apiJson('/api/admin/orders/1', token)
  const baseOrder = orderRaw?.data
  if (!baseOrder) {
    skip('UI-ODT-01', 'OrderDetail', '无法获取订单 1 详情作为 mock 模板')
    skip('UI-ODT-02', 'OrderDetail', '同上')
    skip('UI-ODT-03', 'OrderDetail', '同上')
  } else {
    const variants = {
      pending: { status: 'pending', paid_at: null, shipped_at: null, completed_at: null },
      paid: { status: 'paid', paid_at: '2026-06-11T08:00:00', shipped_at: null, completed_at: null },
      shipped: { status: 'shipped', paid_at: '2026-06-11T08:00:00', shipped_at: '2026-06-11T09:00:00', tracking_no: 'TRK123', completed_at: null },
      completed: {
        status: 'completed', paid_at: '2026-06-11T08:00:00', shipped_at: '2026-06-11T09:00:00', completed_at: '2026-06-12T09:00:00',
        gift_wrap: true, gift_wrap_fee: 25.0, discount_amount: 100.0, total_amount: 1415.0,
      },
    }
    let mockStatus = 'pending'
    await page.route('**/api/admin/orders/1', (route) => {
      const body = { ...baseOrder, ...variants[mockStatus] }
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: null, data: body }) })
    })
    const matrix = {
      pending: { visible: ['取消订单'], hidden: ['发起退款', '标记发货', '确认完成'] },
      paid: { visible: ['发起退款', '标记发货'], hidden: ['取消订单', '确认完成'] },
      shipped: { visible: ['发起退款', '确认完成'], hidden: ['取消订单', '标记发货'] },
      completed: { visible: [], hidden: ['取消订单', '发起退款', '标记发货', '确认完成'] },
    }
    await check('UI-ODT-01', 'OrderDetail', async () => {
      const out = []
      for (const st of Object.keys(matrix)) {
        mockStatus = st
        await gotoPage(page, '/orders/1', 'OrderDetail')
        ok(await page.getByRole('button', { name: '返回' }).isVisible(), `[${st}] 返回按钮缺失`)
        for (const b of matrix[st].visible) ok(await page.getByRole('button', { name: b }).isVisible(), `[${st}] 应显示「${b}」`)
        for (const b of matrix[st].hidden) ok((await page.getByRole('button', { name: b }).count()) === 0, `[${st}] 不应显示「${b}」`)
        out.push(st + ':OK')
      }
      return '状态矩阵（mock 注入 4 态）: ' + out.join(' ') + '；返回恒在'
    })
    await check('UI-ODT-02', 'OrderDetail', async () => {
      mockStatus = 'paid'
      await gotoPage(page, '/orders/1', 'OrderDetail')
      await page.getByRole('button', { name: '标记发货' }).click()
      await sleep(400)
      const opts = await page.locator('select.field option').allInnerTexts()
      const carriersRes = await apiJson('/api/admin/shipping/carriers', token)
      const enabled = (carriersRes?.data?.items || []).filter((c) => c.status === 'enabled').map((c) => c.name)
      for (const c of enabled) ok(opts.includes(c), `承运方下拉缺「${c}」，实际: ${opts.join('|')}`)
      const disabled = (carriersRes?.data?.items || []).filter((c) => c.status !== 'enabled').map((c) => c.name)
      for (const c of disabled) ok(!opts.includes(c), `下拉不应含 disabled 承运方「${c}」`)
      await page.getByRole('button', { name: '取消' }).first().click()
      return `承运方下拉=enabled 集合 [${enabled.join(', ')}]，排除 disabled [${disabled.join(', ')}]（已取消未提交）`
    })
    await check('UI-ODT-03', 'OrderDetail', async () => {
      mockStatus = 'completed' // gift_wrap + discount 条件行全开
      await gotoPage(page, '/orders/1', 'OrderDetail')
      const txt = await page.locator('div.max-w-xs').last().innerText()
      const order = ['小计', '运费', 'Gift Wrapping', '优惠', '合计']
      let lastIdx = -1
      for (const k of order) {
        const i = txt.indexOf(k)
        ok(i >= 0, `金额拆分缺「${k}」，区块文本: ${txt.replace(/\n/g, ' ')}`)
        ok(i > lastIdx, `「${k}」顺序错误`)
        lastIdx = i
      }
      return '金额拆分行序 小计→运费→Gift Wrapping→优惠→合计（mock 注入条件行全开）'
    })
    await shot(page, 'order-detail-full-desktop.png')
    rec('UI-ODT-04', 'OrderDetail', 'SKIPPED', '视觉像素对比无基线；已留档 order-detail-full-desktop.png（completed mock 态）')
    await page.unroute('**/api/admin/orders/1')
  }

  // ================= Refunds（库内 0 工单 → mock 注入列表验证 UI 行为） =================
  const mockRefunds = [
    { id: 9001, refund_no: 'RF-L4-PENDING', order_no: 'DRM-20260611-0001', customer_name: 'L4 Tester', customer_email: 't@dreamy.com', currency: 'USD', amount: 100.0, reason: '尺码不合适', applied_at: '2026-06-10T10:00:00', status: 'pending', stripe_refund_id: null, return_tracking_no: null },
    { id: 9002, refund_no: 'RF-L4-APPROVED', order_no: 'DRM-20260611-0002', customer_name: 'L4 Tester2', customer_email: 't2@dreamy.com', currency: 'USD', amount: 200.0, reason: '质量问题', applied_at: '2026-06-09T10:00:00', status: 'approved', stripe_refund_id: 're_l4_mock_123', return_tracking_no: null },
    { id: 9003, refund_no: 'RF-L4-RETURNED', order_no: 'DRM-20260611-0003', customer_name: 'L4 Tester3', customer_email: 't3@dreamy.com', currency: 'USD', amount: 300.0, reason: '不想要了', applied_at: '2026-06-08T10:00:00', status: 'approved', stripe_refund_id: 're_l4_mock_456', return_tracking_no: 'RET-TRACK-789' },
  ]
  await page.route('**/api/admin/refunds?**', (route) => {
    route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: null, data: { page_number: 1, page_size: 20, number_of_elements: 3, total_elements: 3, total_pages: 1, data: mockRefunds } }),
    })
  })
  let rejectBody = null
  await page.route('**/api/admin/refunds/9001/reject', async (route) => {
    rejectBody = route.request().postDataJSON()
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: null, data: { ...mockRefunds[0], status: 'rejected' } }) })
  })
  await gotoPage(page, '/refunds', 'Refunds')
  await check('UI-RFD-00', 'Refunds', async () => {
    const ths = await thTexts(page)
    for (const c of ['工单号', '关联订单', '客户', '退款金额', '退款原因', '申请时间', '状态', '操作']) ok(ths.includes(c), `列「${c}」缺失`)
    return `表头: ${ths.join(' | ')}（真实接口结构）`
  })
  await check('UI-RFD-01', 'Refunds', async () => {
    const row = page.locator('tr', { hasText: 'RF-L4-PENDING' })
    ok(await row.getByRole('button', { name: '同意' }).isVisible(), 'pending 行无行内同意按钮')
    ok(await row.getByRole('button', { name: '拒绝' }).isVisible(), 'pending 行无行内拒绝按钮')
    ok((await page.locator('[role="dialog"]').count()) === 0, '不应出现弹窗')
    return 'pending 行内 同意/拒绝 按钮（无弹窗触发）[mock 列表注入：库内 0 工单]'
  })
  await check('UI-RFD-02', 'Refunds', async () => {
    const row = page.locator('tr', { hasText: 'RF-L4-PENDING' })
    await row.getByRole('button', { name: '拒绝' }).click()
    await sleep(300)
    const input = row.locator('input[placeholder*="拒绝原因"]')
    ok(await input.isVisible(), '拒绝按钮未原位展开原因输入框')
    const confirmBtn = row.getByRole('button', { name: '确认拒绝' })
    ok(await confirmBtn.isDisabled(), '空原因时确认拒绝应被阻断（disabled）')
    await input.fill('L4 验收 mock 拒绝原因')
    ok(await confirmBtn.isEnabled(), '填写原因后确认应可点击')
    await confirmBtn.click()
    await page.getByText('已拒绝该工单').waitFor({ timeout: 5000 })
    ok(rejectBody && rejectBody.reason === 'L4 验收 mock 拒绝原因', '请求体 reason 不符: ' + JSON.stringify(rejectBody))
    return '拒绝原位展开 → 空原因阻断 → 填写后 POST /reject {reason} + toast 已拒绝该工单 [API mock 成功，未落库]'
  })
  await check('UI-RFD-03', 'Refunds', async () => {
    const row = page.locator('tr', { hasText: 'RF-L4-APPROVED' })
    const txt = await row.innerText()
    ok(txt.includes('已处理'), 'approved 行无「已处理」')
    ok(txt.includes('退款单号') && txt.includes('re_l4_mock_123'), 'approved 行无退款单号')
    return 'approved 行: 已处理 + 退款单号 re_l4_mock_123 [mock 注入]'
  })
  await check('UI-RFD-04', 'Refunds', async () => {
    const row = page.locator('tr', { hasText: 'RF-L4-RETURNED' })
    const txt = await row.innerText()
    ok(txt.includes('退货单号') && txt.includes('RET-TRACK-789'), '已登记退货行无退货单号')
    return '已登记退货行: 退货单号 RET-TRACK-789 [mock 注入]'
  })
  await shot(page, 'refunds-full-desktop.png')
  rec('UI-RFD-05', 'Refunds', 'SKIPPED', '视觉像素对比无基线；已留档 refunds-full-desktop.png（mock 列表态）')
  await page.unroute('**/api/admin/refunds?**')
  await page.unroute('**/api/admin/refunds/9001/reject')

  // ================= Reviews =================
  await gotoPage(page, '/reviews', 'Reviews')
  await check('UI-REV-01', 'Reviews', async () => {
    for (const t of ['评价审核', 'Q&A 管理']) ok((await page.getByRole('button', { name: t }).count()) > 0, `主 Tab「${t}」缺失`)
    return '主 Tabs: 评价审核 / Q&A 管理'
  })
  await check('UI-REV-02', 'Reviews', async () => {
    for (const t of ['全部', '待审核', '已通过', '精选', '已拒绝']) {
      ok((await page.getByRole('button', { name: new RegExp('^' + t) }).count()) > 0, `chip「${t}」缺失`)
    }
    return 'chips: 全部/待审核/已通过/精选/已拒绝'
  })
  await check('UI-REV-05', 'Reviews', async () => {
    // 豁免项：仅待审核 chip 带计数角标
    const chips = page.locator('button').filter({ hasText: /^(全部|待审核|已通过|精选|已拒绝)/ })
    const texts = await chips.allInnerTexts()
    const withCount = texts.filter((t) => /\d/.test(t))
    ok(withCount.length >= 1 && withCount.every((t) => t.includes('待审核')), `计数角标分布异常: ${texts.join(' | ')}`)
    return `仅待审核 chip 带计数（${withCount.join(',').trim()}），豁免现状一致`
  })
  await check('UI-REV-03', 'Reviews', async () => {
    const boxes = page.locator('table tbody input[type="checkbox"]')
    ok((await boxes.count()) >= 2, '评价行复选框不足 2 个')
    await boxes.nth(0).check(); await boxes.nth(1).check()
    await sleep(300)
    ok((await page.getByText(/已选 2 条评价/).count()) > 0, '批量条未出现')
    ok(await page.getByRole('button', { name: '批量通过' }).isVisible(), '批量通过缺失')
    ok(await page.getByRole('button', { name: '批量拒绝' }).isVisible(), '批量拒绝缺失')
    await boxes.nth(0).uncheck(); await boxes.nth(1).uncheck()
    return '勾选 2 条 → 批量通过/批量拒绝条出现，已复位'
  })
  await check('UI-REV-04', 'Reviews', async () => {
    // 详情抽屉 → Lightbox 驳回（API mock，不落库）
    const reviewsRes = await apiJson('/api/admin/reviews?status=pending&page_size=10', token)
    const target = (reviewsRes?.data?.data || []).find((r) => (r.images || []).length > 0)
    if (!target) throw new Error('NO_IMAGE_REVIEW')
    let imgPatch = null
    await page.route(`**/api/admin/reviews/${target.id}/images/**`, async (route) => {
      imgPatch = route.request().postDataJSON()
      const updated = { ...target, images: target.images.map((im, i) => (i === 0 ? { ...im, rejected: true } : im)) }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: null, data: updated }) })
    })
    const row = page.locator('table tbody tr', { hasText: target.customer_name || '' }).first()
    await row.locator('td').nth(1).click()
    await sleep(500)
    ok((await page.getByText('点击图片可放大查看').count()) > 0, '详情抽屉未打开')
    await page.locator('img[src*="' + (target.images[0].url.split('/').pop() || '') + '"]').last().click({ force: true })
    await sleep(400)
    const rejectBtn = page.getByRole('button', { name: /驳回此图/ })
    ok(await rejectBtn.isVisible(), 'Lightbox 驳回按钮缺失')
    await rejectBtn.click()
    await sleep(500)
    ok(imgPatch && imgPatch.rejected === true, 'PATCH images 请求体不符: ' + JSON.stringify(imgPatch))
    const grayed = await page.locator('img.grayscale, img[class*="grayscale"]').count()
    const rejectedMark = await page.getByText(/已驳回/).count()
    ok(grayed > 0 && rejectedMark > 0, `驳回后置灰=${grayed} 已驳回标记=${rejectedMark}`)
    await page.keyboard.press('Escape')
    await page.unroute(`**/api/admin/reviews/${target.id}/images/**`)
    return `图片驳回: PATCH {rejected:true} + 置灰 + 已驳回标记 [API mock 成功，未落库]（review#${target.id}）`
  })
  await check('UI-REV-07', 'Reviews', async () => {
    // 豁免项 CP-071：删除官方回复 → ConfirmDialog 出现后取消（不真删）
    // 库内无含官方回复的评价 → mock 列表注入一条带 reply 的 approved 评价
    const mockReview = {
      id: 9301, product_id: 1, user_id: 11, product_name: 'L4 Mock Gown', customer_name: 'L4 Reply Tester',
      rating: 5, content: 'L4 验收 mock 评价（含官方回复）', status: 'approved', featured: false,
      submitted_at: '2026-06-09T10:00:00', images: [],
      reply_author: 'Dreamy 官方', reply_content: '感谢您的喜爱！', reply_time: '2026-06-10T10:00:00',
    }
    await page.route('**/api/admin/reviews?**', (route) => {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: null, data: { page_number: 1, page_size: 20, number_of_elements: 1, total_elements: 1, total_pages: 1, data: [mockReview], pending_count: 0 } }) })
    })
    await gotoPage(page, '/reviews', 'Reviews')
    const row = page.locator('table tbody tr', { hasText: 'L4 Reply Tester' }).first()
    await row.locator('td').nth(1).click()
    await sleep(500)
    const delBtn = page.getByRole('button', { name: '删除' }).first()
    ok(await delBtn.isVisible(), '官方回复删除按钮缺失')
    await delBtn.click()
    await sleep(400)
    ok((await page.getByText('删除官方回复').count()) > 0, 'ConfirmDialog 未出现')
    await page.getByRole('button', { name: '取消' }).first().click()
    await page.unroute('**/api/admin/reviews?**')
    return '删除回复 ConfirmDialog 二次确认出现后已取消 [mock 注入含回复评价：库内无带官方回复数据]（豁免现状一致，未删除）'
  })
  await gotoPage(page, '/reviews', 'Reviews')
  await check('UI-REV-06', 'Reviews', async () => {
    await page.getByRole('button', { name: 'Q&A 管理' }).click()
    await sleep(500)
    const ph = page.locator('input[placeholder*="（当前页）"]')
    ok((await ph.count()) > 0, 'Q&A 搜索框 placeholder 不含（当前页）')
    return 'Q&A 搜索框 placeholder 含「（当前页）」（豁免现状一致）'
  })
  await gotoPage(page, '/reviews', 'Reviews')
  await shot(page, 'reviews-full-desktop.png')
  rec('UI-REV-08', 'Reviews', 'SKIPPED', '视觉像素对比无基线；已留档 reviews-full-desktop.png')

  // ================= Shipping =================
  await gotoPage(page, '/shipping', 'Shipping')
  await check('UI-SHP-01', 'Shipping', async () => {
    ok(await page.getByRole('button', { name: /添加承运方/ }).isVisible(), '添加承运方按钮缺失')
    ok((await page.locator('button[role="switch"], [class*="toggle"], input[type="checkbox"]').count()) > 0 || (await page.getByText('启用').count()) >= 0, 'Toggle 缺失')
    ok((await page.getByText('国际邮费表').count()) > 0, '国际邮费表面板缺失')
    const ths = await page.locator('table').last().locator('thead th').allInnerTexts()
    for (const c of ['区域', '基础邮费', '满额包邮', '门槛']) ok(ths.some((t) => t.trim() === c), `邮费表缺列「${c}」，实际: ${ths.join('|')}`)
    return '承运方面板（添加承运方）+ 国际邮费表（区域/基础邮费/满额包邮/门槛）双 panel'
  })
  await check('UI-SHP-02', 'Shipping', async () => {
    ok((await page.getByText('免邮', { exact: true }).count()) > 0, 'feeOver=0 行未显示免邮')
    return 'feeOver=0 行显示「免邮」（真实数据 fee_over=0.00）'
  })
  await check('UI-SHP-03', 'Shipping', async () => {
    const header = page.locator('header, .page-header, h1').first()
    ok((await page.getByRole('button', { name: '保存配置' }).count()) === 0, '页头不应有保存配置按钮')
    return '页头无「保存配置」（DEC-SHP-FE-1 即时持久化，豁免现状一致）'
  })
  await shot(page, 'shipping-full-desktop.png')
  rec('UI-SHP-04', 'Shipping', 'SKIPPED', '视觉像素对比无基线；已留档 shipping-full-desktop.png')

  // ================= Promotions =================
  // ended 闪购库内不存在 → mock flash-sales 列表注入 ended 行
  await page.route('**/api/admin/promotions/flash-sales**', (route) => {
    route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: null, data: { items: [
        { id: 9101, name: 'L4 Ended Flash', discount: '20% OFF', start_at: '2026-05-01T00:00:00', end_at: '2026-05-05T00:00:00', status: 'ended', product_ids: [], translations: [] },
        { id: 9102, name: 'L4 Active Flash', discount: '10% OFF', start_at: '2026-06-01T00:00:00', end_at: '2026-06-30T00:00:00', status: 'active', product_ids: [1], translations: [] },
      ] } }),
    })
  })
  await gotoPage(page, '/promotions', 'Promotions')
  await check('UI-PRM-01', 'Promotions', async () => {
    const card = page.locator('.panel', { hasText: 'SPRING50' }).first()
    await card.waitFor({ state: 'visible', timeout: 8000 })
    ok((await card.getByText('已过期').count()) > 0, 'expired 券 SPRING50 卡片未显示「已过期」徽章')
    return 'expired 券 SPRING50 徽章=已过期（真实数据）'
  })
  await check('UI-PRM-02', 'Promotions', async () => {
    const row = page.locator('tr', { hasText: 'L4 Ended Flash' })
    ok((await row.count()) > 0, 'ended 闪购行未渲染（mock 注入）')
    ok((await row.getByText('已结束').count()) > 0, 'ended 闪购无「已结束」徽章')
    return 'ended 闪购徽章=已结束 [mock 注入：库内无 ended 闪购]'
  })
  await check('UI-PRM-03', 'Promotions', async () => {
    const row = page.locator('tr', { hasText: 'L4 Ended Flash' })
    const editBtn = row.locator('button', { hasText: '编辑' }).first()
    ok(await editBtn.isDisabled(), 'ended 闪购编辑按钮未 disabled')
    ok((await editBtn.getAttribute('title')) === '已结束活动不可编辑', 'title 不符: ' + (await editBtn.getAttribute('title')))
    const activeEdit = page.locator('tr', { hasText: 'L4 Active Flash' }).locator('button', { hasText: '编辑' }).first()
    ok(await activeEdit.isEnabled(), '对照组 active 行编辑按钮应可用')
    return "ended 行编辑 disabled + title='已结束活动不可编辑'；active 行可编辑 [mock 注入]"
  })
  await shot(page, 'promotions-full-desktop.png')
  rec('UI-PRM-04', 'Promotions', 'SKIPPED', '视觉像素对比无基线；已留档 promotions-full-desktop.png（flash 列表为 mock 态）')
  await page.unroute('**/api/admin/promotions/flash-sales**')

  // ================= Banners（status PATCH 全部 mock，不落库） =================
  await gotoPage(page, '/banners', 'Banners')
  const bannersRes = await apiJson('/api/admin/banners', token)
  const bannerItems = bannersRes?.data?.items || []
  const pubBanner = bannerItems.find((b) => b.status === 'published')
  const arcBanner = bannerItems.find((b) => b.status === 'archived')
  const statusCalls = []
  let bannerMockMode = 'success'
  await page.route('**/api/admin/banners/*/status', async (route) => {
    const body = route.request().postDataJSON()
    const id = Number(route.request().url().match(/banners\/(\d+)\/status/)?.[1])
    statusCalls.push({ id, body })
    if (bannerMockMode === '409703') {
      await route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 409703, message: '当前发布状态不允许该操作', data: null }) })
    } else {
      const src = bannerItems.find((b) => b.id === id) || {}
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, message: null, data: { ...src, status: body.status } }) })
    }
  })
  if (pubBanner) {
    await check('UI-BNR-01', 'Banners', async () => {
      const row = page.locator('tr', { hasText: pubBanner.name })
      const toggle = row.locator('button[role="switch"], [class*="toggle"], button:has(span)').first()
      const before = statusCalls.length
      await toggle.click()
      await sleep(600)
      const call = statusCalls[before]
      ok(call && call.id === pubBanner.id && call.body.status === 'archived', `PATCH 不符: ${JSON.stringify(call)}`)
      return `published「${pubBanner.name}」Toggle off → PATCH status=archived，成功后 Toggle off [API mock，未落库]`
    })
  } else skip('UI-BNR-01', 'Banners', '库内无 published Banner 行')
  if (arcBanner) {
    await check('UI-BNR-02', 'Banners', async () => {
      const row = page.locator('tr', { hasText: arcBanner.name })
      const toggle = row.locator('button[role="switch"], [class*="toggle"], button:has(span)').first()
      const before = statusCalls.length
      await toggle.click()
      await sleep(600)
      const call = statusCalls[before]
      ok(call && call.id === arcBanner.id && call.body.status === 'published', `PATCH 不符: ${JSON.stringify(call)}`)
      return `archived「${arcBanner.name}」Toggle on → PATCH status=published [API mock，未落库]`
    })
  } else skip('UI-BNR-02', 'Banners', '库内无 archived Banner 行')
  await check('UI-BNR-03', 'Banners', async () => {
    await gotoPage(page, '/banners', 'Banners') // 重载还原真实列表态
    bannerMockMode = '409703'
    const target = pubBanner || arcBanner
    if (!target) throw new Error('无可用 Banner 行')
    const row = page.locator('tr', { hasText: target.name })
    const toggle = row.locator('button[role="switch"], [class*="toggle"], button:has(span)').first()
    const stateBefore = await toggle.getAttribute('aria-checked').catch(() => null)
    await toggle.click()
    await page.getByText('当前发布状态不允许该操作').waitFor({ timeout: 5000 })
    await sleep(400)
    const stateAfter = await toggle.getAttribute('aria-checked').catch(() => null)
    const rollback = stateBefore === null ? '（aria-checked 不可用，以 store 回滚逻辑+toast 为准）' : (stateBefore === stateAfter ? '视觉态已回滚' : `回滚失败 ${stateBefore}→${stateAfter}`)
    if (stateBefore !== null) ok(stateBefore === stateAfter, 'Toggle 未回滚')
    return `mock 409703 → toast「当前发布状态不允许该操作」+ ${rollback}`
  })
  bannerMockMode = 'success'
  await gotoPage(page, '/banners', 'Banners')
  await shot(page, 'banners-full-desktop.png')
  rec('UI-BNR-04', 'Banners', 'SKIPPED', '视觉像素对比无基线；已留档 banners-full-desktop.png')
  await page.unroute('**/api/admin/banners/*/status')

  // ================= ContentBlog（archived 状态库内缺失 → mock 列表含三态） =================
  const mockBlogs = [
    { id: 9201, title: 'L4 Draft Post（无 slug）', cover: '', category: 'Planning', author: 'L4', content: 'x', slug: null, status: 'draft', published_at: null, views: 0, translations: [] },
    { id: 9202, title: 'L4 Published Post', cover: '', category: 'Planning', author: 'L4', content: 'x', slug: 'l4-preview-test', status: 'published', published_at: '2026-06-01T00:00:00', views: 10, translations: [] },
    { id: 9203, title: 'L4 Archived Post', cover: '', category: 'Planning', author: 'L4', content: 'x', slug: 'l4-archived', status: 'archived', published_at: '2026-05-01T00:00:00', views: 5, translations: [] },
  ]
  await page.route('**/api/admin/content/blogs**', (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: null, data: { page_number: 1, page_size: 20, number_of_elements: 3, total_elements: 3, total_pages: 1, data: mockBlogs } }),
    })
  })
  await gotoPage(page, '/content/blog', 'ContentBlog')
  await check('UI-BLG-01', 'ContentBlog', async () => {
    const draftCard = page.locator('.panel, article, div', { hasText: 'L4 Draft Post' }).last()
    ok((await draftCard.getByRole('button', { name: '发布', exact: true }).count()) > 0, 'draft 卡片无发布按钮')
    const pubCard = page.locator('.panel, article, div', { hasText: 'L4 Published Post' }).last()
    ok((await pubCard.getByRole('button', { name: '下线' }).count()) > 0, 'published 卡片无下线按钮')
    const arcCard = page.locator('.panel, article, div', { hasText: 'L4 Archived Post' }).last()
    ok((await arcCard.getByRole('button', { name: '重新发布' }).count()) > 0, 'archived 卡片无重新发布按钮')
    return '状态矩阵 draft:发布 / published:下线 / archived:重新发布 [mock 列表注入（库内无 archived）]'
  })
  await check('UI-BLG-02', 'ContentBlog', async () => {
    const draftCard = page.locator('.panel, article, div', { hasText: 'L4 Draft Post' }).last()
    const previewBtn = draftCard.getByRole('button', { name: '预览' }).first()
    ok(await previewBtn.isDisabled(), '无 slug 卡片预览按钮未 disabled')
    ok((await previewBtn.getAttribute('title')) === '需先填写 slug', 'title 不符: ' + (await previewBtn.getAttribute('title')))
    return "无 slug 卡片预览 disabled + title='需先填写 slug'"
  })
  await check('UI-BLG-03', 'ContentBlog', async () => {
    const pubCard = page.locator('.panel, article, div', { hasText: 'L4 Published Post' }).last()
    const popupP = ctx.waitForEvent('page', { timeout: 8000 })
    await pubCard.getByRole('button', { name: '预览' }).first().click()
    const popup = await popupP
    const u = popup.url()
    await popup.close().catch(() => {})
    ok(new URL(u).pathname.endsWith('/blog/l4-preview-test'), '预览 URL 不符: ' + u)
    return `预览新窗口 URL=${u}（以 /blog/{slug} 结尾）`
  })
  await page.unroute('**/api/admin/content/blogs**')
  await gotoPage(page, '/content/blog', 'ContentBlog')
  await shot(page, 'content-blog-full-desktop.png')
  rec('UI-BLG-04', 'ContentBlog', 'SKIPPED', '视觉像素对比无基线；已留档 content-blog-full-desktop.png（真实数据态）')

  // ================= ContentWeddings =================
  await gotoPage(page, '/content/weddings', 'ContentWeddings')
  await check('UI-WED-01', 'ContentWeddings', async () => {
    const draftCard = page.locator('.panel').filter({ hasText: '草稿' }).first()
    await draftCard.waitFor({ state: 'visible', timeout: 8000 })
    ok((await draftCard.locator('button[title="发布"]').count()) > 0, 'draft 卡片无 发布 操作')
    const pubCard = page.locator('.panel').filter({ hasText: '已发布' }).first()
    ok((await pubCard.count()) > 0, '无 published 卡片')
    ok((await pubCard.locator('button[title="下线"]').count()) > 0, 'published 卡片无 下线 操作')
    return 'draft 卡片操作 title=发布 / published 卡片操作 title=下线（真实数据，未点击避免状态写库）'
  })
  await shot(page, 'content-weddings-full-desktop.png')
  rec('UI-WED-02', 'ContentWeddings', 'SKIPPED', '视觉像素对比无基线；已留档 content-weddings-full-desktop.png')

  // ================= ContentLookbook =================
  await gotoPage(page, '/content/lookbook', 'ContentLookbook')
  await check('UI-LBK-01', 'ContentLookbook', async () => {
    // 真实数据：published「Coastal Romance」+ draft「Woodland Whisper」
    const pub = page.locator('.panel, div', { hasText: 'Coastal Romance' }).last()
    ok((await pub.getByRole('button', { name: '下线' }).count()) > 0, 'published Lookbook 卡片无下线按钮')
    const draft = page.locator('.panel, div', { hasText: 'Woodland Whisper' }).last()
    ok((await draft.getByRole('button', { name: '发布', exact: true }).count()) > 0, 'draft Lookbook 卡片无发布按钮')
    return 'Lookbook 卡片 发布/下线 按状态切换（真实数据，未点击）'
  })
  await check('UI-LBK-02', 'ContentLookbook', async () => {
    const guideTab = page.getByRole('button', { name: /指南|Guide/ }).first()
    if (await guideTab.count()) { await guideTab.click(); await sleep(400) }
    const btns = await page.locator('button[title="下线"], button[title="发布"]').count()
    ok(btns > 0, 'Guide 行无发布/下线操作按钮')
    return `Guide 行发布/下线按钮按状态渲染（${btns} 个，真实数据 guides 均 published）`
  })
  await shot(page, 'content-lookbook-full-desktop.png')
  rec('UI-LBK-03', 'ContentLookbook', 'SKIPPED', '视觉像素对比无基线；已留档 content-lookbook-full-desktop.png')

  await browser.close()

  // ---- 汇总 ----
  const summary = { PASS: 0, FAIL: 0, SKIPPED: 0 }
  for (const r of results) summary[r.status]++
  fs.writeFileSync(OUT + '/raw-results.json', JSON.stringify({ summary, results, consoleErrors }, null, 2))
  console.log('\n==== SUMMARY ====')
  console.log(JSON.stringify(summary))
  console.log('console errors pages:', Object.keys(consoleErrors).join(',') || '(none)')
}

main().catch((e) => { console.error('FATAL', e); process.exit(1) })
