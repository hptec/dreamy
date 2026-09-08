// 验证：商品详情页 Add to Bag 交互反馈
// 修复回归：未选尺码时 CTA 显示 "Select a Size" 并禁用（对齐 quick-view 模式）；
// 校验/API 错误就近显示在 CTA 按钮下方；正常加购后 cart drawer 打开。
// 用法：node add-to-bag.verify.mjs [storeBase]（默认 http://localhost:5173）
import { chromium } from 'playwright'
import { loginStore, injectSession, collectConsoleErrors, STORE as DEFAULT_STORE } from './helpers/store-api.mjs'

const STORE = process.argv[2] ?? process.env.STORE_BASE ?? DEFAULT_STORE
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

async function runScenario(browser, { name, auth }) {
  console.log(`\n[${name}] ${STORE}/product/celeste-lace-gown`)
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await context.newPage()
  const consoleErrors = collectConsoleErrors(page)
  // 已知噪音：server-fetch 被共享进客户端 bundle 的构建期告警（见 docker-deploy.verify.mjs）
  const cleanErrors = () => consoleErrors.filter((e) => !e.includes('[server-fetch] API_ORIGIN'))
  if (auth) await injectSession(page, auth.tokens)

  await page.goto(`${STORE}/product/celeste-lace-gown`, { waitUntil: 'networkidle', timeout: 60000 })
  const buyBtn = page.locator('button', { hasText: /^Add to Bag$|^Select a Size$|^Adding…$/ }).last()
  await buyBtn.waitFor({ timeout: 30000 })

  // 1) 初始未选尺码：CTA 应显示 "Select a Size" 且禁用（用户不再遭遇"点击无效果"）
  const initialText = (await buyBtn.innerText()).trim()
  check(initialText.toLowerCase() === 'select a size', `未选尺码 → CTA 显示 "Select a Size"（实际: "${initialText}"）`)
  check(await buyBtn.isDisabled(), '未选尺码 → CTA 禁用')

  // 2) 选中尺码：CTA 应变为 "Add to Bag" 可点击
  await page.locator('button', { hasText: /^US 4$/ }).click()
  await page.waitForTimeout(300)
  const afterText = (await buyBtn.innerText()).trim()
  check(afterText.toLowerCase() === 'add to bag', `选尺码后 → CTA 变为 "Add to Bag"（实际: "${afterText}"）`)
  check(!(await buyBtn.isDisabled()), '选尺码后 → CTA 可点击')

  // 3) 点击加购：cart drawer 打开（aside + Your Bag 标题）
  await buyBtn.click()
  let drawerVisible = false
  try {
    const drawer = page.locator('aside', { hasText: /your bag|shopping bag/i })
    await drawer.waitFor({ state: 'visible', timeout: 5000 })
    drawerVisible = (await drawer.count()) > 0
  } catch { /* timeout */ }
  check(drawerVisible, '点击加购 → cart drawer 打开')

  const errs = cleanErrors()
  if (errs.length > 0) failures.push(`[${name}] console errors: ${errs.join(' | ')}`)
  console.log(`  · console errors: ${errs.length === 0 ? '无' : JSON.stringify(errs)}`)

  await context.close()
}

const browser = await chromium.launch({ headless: true })
await runScenario(browser, { name: '匿名态' })
const { tokens } = await loginStore(process.env.STORE_EMAIL ?? 'fe-verify-store@dreamy.com')
await runScenario(browser, { name: '登录态', auth: { tokens } })
await browser.close()

console.log(`\n===== ${failures.length === 0 ? 'ALL PASS' : `FAIL x${failures.length}`} =====`)
failures.forEach((f) => console.log(` - ${f}`))
process.exit(failures.length === 0 ? 0 : 1)
