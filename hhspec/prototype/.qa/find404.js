const { chromium } = require('playwright')
;(async () => {
  const b = await chromium.launch()
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } })
  const pg = await ctx.newPage()
  const bad = []
  pg.on('response', (r) => { if (r.status() === 404) bad.push(r.url()) })
  for (const p of ['/', '/wedding-dresses', '/special-occasion', '/outdoor-weddings', '/inspiration', '/account/login', '/about']) {
    await pg.goto('http://localhost:5173' + p, { waitUntil: 'networkidle', timeout: 20000 })
    await pg.waitForTimeout(500)
  }
  await b.close()
  console.log('404 URLs:')
  ;[...new Set(bad)].forEach((u) => console.log(' ', u.replace('http://localhost:5173', '')))
})()
