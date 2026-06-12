const { chromium } = require('playwright')

const pages = [
  ['home', '/'], ['wedding-dresses', '/wedding-dresses'], ['special-occasion', '/special-occasion'],
  ['accessories', '/accessories'], ['outdoor-weddings', '/outdoor-weddings'], ['pdp', '/product/aurelia-gown'],
  ['pdp-bridesmaid', '/product/seabreeze-bridesmaid'], ['pdp-veil', '/product/cathedral-veil'],
  ['search', '/search?q=sage'], ['cart', '/cart'], ['checkout', '/checkout'], ['order-success', '/order-success'],
  ['login', '/account/login'], ['account', '/account'], ['account-orders', '/account/orders'],
  ['account-order-detail', '/account/orders/1001'], ['account-addresses', '/account/addresses'],
  ['account-wishlist', '/account/wishlist'], ['account-settings', '/account/settings'],
  ['inspiration', '/inspiration'], ['real-weddings', '/real-weddings'],
  ['real-wedding-detail', '/real-weddings/coastal-emma-james'], ['blog', '/blog'],
  ['blog-post', '/blog/outdoor-wedding-guide'], ['wedding-guides', '/wedding-guides'],
  ['about', '/about'], ['contact', '/contact'], ['faq', '/faq'], ['notfound', '/nope-404']
]

;(async () => {
  const b = await chromium.launch()
  const realErrors = []
  const imageErrors = []
  for (const [vpName, w, h] of [['desktop', 1440, 900], ['mobile', 390, 844]]) {
    const ctx = await b.newContext({ viewport: { width: w, height: h } })
    for (const [name, path] of pages) {
      const pg = await ctx.newPage()
      const errs = []
      pg.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('status of 404')) errs.push(m.text()) })
      pg.on('pageerror', (e) => errs.push(e.message))
      pg.on('response', (r) => { if (r.status() === 404 && /\.(jpg|png|webp)/.test(r.url())) imageErrors.push(`${name}: ${r.url().split('/').pop()}`) })
      await pg.goto('http://localhost:5175' + path, { waitUntil: 'networkidle', timeout: 20000 })
      await pg.waitForTimeout(500)
      await pg.screenshot({ path: `.qa/${vpName}-${name}.png`, fullPage: vpName === 'desktop' })
      if (errs.length) realErrors.push({ vp: vpName, name, errs })
      await pg.close()
    }
    await ctx.close()
  }
  await b.close()
  console.log('REAL JS ERRORS:', JSON.stringify(realErrors))
  console.log('IMAGE 404s:', JSON.stringify([...new Set(imageErrors)]))
})()
