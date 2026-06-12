const { chromium } = require('playwright')
const fs = require('fs')

const routes = [
  ['login', '/login'], ['dashboard', '/'], ['products', '/products'],
  ['product-edit', '/products/p-aurelia/edit'], ['categories', '/categories'],
  ['orders', '/orders'], ['order-detail', '/orders/DRM-20260529-1042'], ['refunds', '/refunds'],
  ['customers', '/customers'], ['customer-detail', '/customers/u-1001'],
  ['home-builder', '/site/home'], ['navigation', '/site/navigation'], ['banners', '/site/banners'],
  ['promotions', '/marketing/promotions'], ['email', '/marketing/email'],
  ['content-blog', '/content/blog'], ['content-weddings', '/content/weddings'], ['content-lookbook', '/content/lookbook'],
  ['analytics', '/analytics'], ['publish', '/publish'], ['shipping', '/shipping'], ['settings', '/settings']
]

;(async () => {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const out = '/Volumes/MAC/workspace/dreamy/hhspec/prototype/.qa/admin-shots'
  fs.mkdirSync(out, { recursive: true })
  const errors = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(`[console] ${m.text()}`) })
  page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`))

  for (const [name, path] of routes) {
    try {
      await page.goto('http://localhost:5176' + path, { waitUntil: 'networkidle', timeout: 20000 })
      await page.waitForTimeout(700)
      await page.screenshot({ path: `${out}/${name}.png`, fullPage: false })
      console.log('OK', name)
    } catch (e) { console.log('FAIL', name, e.message); errors.push(`[nav ${name}] ${e.message}`) }
  }
  // 触发发布动画截图
  try {
    await page.goto('http://localhost:5176/publish', { waitUntil: 'networkidle' })
    await page.click('button:has-text("一键发布")')
    await page.waitForTimeout(6500)
    await page.screenshot({ path: `${out}/publish-done.png`, fullPage: false })
    console.log('OK publish-done')
  } catch (e) { console.log('FAIL publish-anim', e.message) }

  fs.writeFileSync(`${out}/errors.json`, JSON.stringify([...new Set(errors)], null, 2))
  console.log('\n=== ERRORS (' + new Set(errors).size + ' unique) ===')
  ;[...new Set(errors)].slice(0, 20).forEach((e) => console.log(e))
  await browser.close()
})()
