// 站点装修多语言编辑修复的端到端验证（临时脚本，验证后可删除）
// 运行：node tests/ui-verification/site-builder-i18n.verify.mjs
import { chromium } from 'playwright'

const BASE = process.env.ADMIN_BASE || 'http://localhost:5174'
const API = process.env.API_BASE || 'http://localhost:18081'
const results = []
const ok = (name, pass, extra = '') => {
  results.push({ name, pass })
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${extra ? ' — ' + extra : ''}`)
}

const browser = await chromium.launch()
const page = await browser.newPage()

// 1. 登录
await page.goto(`${BASE}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'TempVerify!2026')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })
ok('登录', true)

// 2. 导航与页脚 — LocaleTabs 出现
await page.goto(`${BASE}/site/navigation`)
await page.waitForSelector('text=主导航 & Mega Menu', { timeout: 10000 })
await page.waitForTimeout(1500)
ok('NavigationConfig LocaleTabs 渲染', await page.locator('button:has-text("EN（主）")').first().isVisible())

// 3. 主导航：ES 下编辑翻译，EN 基准不受污染
const navInput = page.locator('div.panel:visible input.field:visible').first()
await page.click('button:has-text("ES")')
await navInput.fill('Tienda ES')
await page.click('button:has-text("EN（主）")')
ok('导航 EN 基准不受 ES 编辑影响', (await navInput.inputValue()) === 'Home', `EN=${await navInput.inputValue()}`)
await page.click('button:has-text("ES")')
ok('导航 ES 翻译保留', (await navInput.inputValue()) === 'Tienda ES')

// 4. 公告条：ES 内容编辑
await page.click('button:has-text("公告条")')
await page.waitForTimeout(400)
const annInput = page.locator('div.max-w-2xl input.field:visible').first()
await annInput.fill('Envío gratis ES verificación')
ok('公告 ES 可编辑', (await annInput.inputValue()) === 'Envío gratis ES verificación')

// 5. 页脚：ES 标题编辑
await page.click('button:has-text("页脚栏目")')
await page.waitForTimeout(400)
const footerTitleInput = page.locator('div.panel:visible input.field:visible').first()
await footerTitleInput.fill('Tienda Footer ES')
ok('页脚栏目 ES 标题可编辑', (await footerTitleInput.inputValue()) === 'Tienda Footer ES')

// 6. 保存并验证持久化（覆盖此前页脚相对 URL 必 422 的修复）
await page.click('button:has-text("保存")')
await page.waitForTimeout(2500)
await page.reload()
await page.waitForTimeout(1500)
await page.click('button:has-text("ES")')
const navEsAfter = await page.locator('div.panel:visible input.field:visible').first().inputValue()
ok('保存后导航 ES 持久化', navEsAfter === 'Tienda ES', `ES=${navEsAfter}`)
await page.click('button:has-text("页脚栏目")')
await page.waitForTimeout(400)
const footerEsAfter = await page.locator('div.panel:visible input.field:visible').first().inputValue()
ok('保存后页脚 ES 持久化', footerEsAfter === 'Tienda Footer ES', `ES=${footerEsAfter}`)

// 7. 消费端 API 验证
const navApi = await (await fetch(`${API}/api/store/content/navigation?locale=es`)).json()
ok('消费端导航 ES 返回翻译', navApi?.data?.items?.[0]?.label === 'Tienda ES', navApi?.data?.items?.[0]?.label)
const navApiEn = await (await fetch(`${API}/api/store/content/navigation?locale=en`)).json()
ok('消费端导航 EN 仍为基准', navApiEn?.data?.items?.[0]?.label === 'Home')
const annApi = await (await fetch(`${API}/api/store/content/announcements?locale=es`)).json()
ok('消费端公告 ES 返回新翻译', annApi?.data?.announcements?.[0]?.content === 'Envío gratis ES verificación', annApi?.data?.announcements?.[0]?.content)
const footerApi = await (await fetch(`${API}/api/store/content/footer?locale=es`)).json()
ok('消费端页脚 ES 返回翻译', footerApi?.data?.columns?.[0]?.title === 'Tienda Footer ES', footerApi?.data?.columns?.[0]?.title)

// 8. 首页装修：新增 custom 区块验证 ctaText 多语言
await page.goto(`${BASE}/site/home`)
await page.waitForSelector('text=页面区块', { timeout: 10000 })
await page.waitForTimeout(1500)
await page.locator('button:has-text("添加")').first().click()
await page.waitForTimeout(500)
await page.locator('.fixed button.field').first().click()
await page.waitForTimeout(400)
await page.locator('li:has-text("自定义内容")').first().click()
await page.locator('.fixed button:has-text("添加")').first().click()
await page.waitForTimeout(2000)

await page.locator('xpath=//label[contains(text(),"标题（")]/following-sibling::input').first().fill('Custom Block EN')
await page.locator('xpath=//label[contains(text(),"按钮文案")]/following-sibling::input').first().fill('Shop Now EN')
await page.locator('xpath=//label[text()="按钮链接"]/following-sibling::input').first().fill('/products')
await page.click('button:has-text("ES")')
await page.waitForTimeout(300)
await page.locator('xpath=//label[contains(text(),"按钮文案")]/following-sibling::input').first().fill('Comprar ES')
await page.click('button:has-text("保存并生效")')
await page.waitForTimeout(2500)

// 9. 消费端首页 API 验证 ctaText 按语言覆盖（API 返回 snake_case）
const homeEs = await (await fetch(`${API}/api/store/content/home?locale=es`)).json()
const customEs = homeEs?.data?.sections?.find((s) => s.section_type === 'custom')
ok('消费端 custom ES ctaText', customEs?.data?.cta_text === 'Comprar ES', JSON.stringify(customEs?.data))
const homeEn = await (await fetch(`${API}/api/store/content/home?locale=en`)).json()
const customEn = homeEn?.data?.sections?.find((s) => s.section_type === 'custom')
ok('消费端 custom EN ctaText', customEn?.data?.cta_text === 'Shop Now EN', JSON.stringify(customEn?.data))

// 10. 清理：删除 custom 测试区块
await page.goto(`${BASE}/site/home`)
await page.waitForTimeout(1500)
const customBlockItem = page.locator('span:text-is("自定义内容")').first()
if (await customBlockItem.isVisible().catch(() => false)) {
  await customBlockItem.click()
  await page.waitForTimeout(400)
  await page.locator('button[title="删除区块"]').click()
  await page.waitForTimeout(400)
  await page.locator('.fixed button:has-text("删除"), div[role="dialog"] button:has-text("删除")').first().click()
  await page.waitForTimeout(2000)
  ok('清理 custom 测试区块', true)
} else {
  ok('清理 custom 测试区块', false, '未找到区块')
}

// 11. 还原测试数据（清空 ES 翻译 → 回退 EN；公告 ES 恢复 seed 文案）
await page.goto(`${BASE}/site/navigation`)
await page.waitForTimeout(1500)
await page.click('button:has-text("ES")')
await page.locator('div.panel:visible input.field:visible').first().fill('')
await page.click('button:has-text("页脚栏目")')
await page.waitForTimeout(400)
await page.locator('div.panel:visible input.field:visible').first().fill('')
await page.click('button:has-text("公告条")')
await page.waitForTimeout(400)
await page.locator('div.max-w-2xl input.field:visible').first().fill('Envío gratis en todos los pedidos superiores a $500 — tiempo limitado.')
await page.click('button:has-text("保存")')
await page.waitForTimeout(2500)

// 12. 验证还原后消费端回退 EN
const navApiAfter = await (await fetch(`${API}/api/store/content/navigation?locale=es`)).json()
ok('还原后导航 ES 回退基准', navApiAfter?.data?.items?.[0]?.label === 'Home', navApiAfter?.data?.items?.[0]?.label)
const footerApiAfter = await (await fetch(`${API}/api/store/content/footer?locale=es`)).json()
ok('还原后页脚 ES 回退基准', footerApiAfter?.data?.columns?.[0]?.title === 'Shop', footerApiAfter?.data?.columns?.[0]?.title)

await browser.close()
const failed = results.filter((r) => !r.pass)
console.log(`\n===== ${results.length - failed.length}/${results.length} 通过 =====`)
process.exit(failed.length ? 1 : 0)
