import { chromium } from 'playwright'
const BASE = process.env.SITE ?? 'http://localhost:5199'
const browser = await chromium.launch()
for (const w of [1024, 1280, 1366, 1440, 1536, 1920]) {
  const page = await browser.newPage({ viewport: { width: w, height: 400 } })
  await page.goto(BASE + '/', { waitUntil: 'networkidle' })
  const info = await page.evaluate(() => {
    const links = [...document.querySelectorAll('header nav a')].filter(a => a.offsetParent)
    const rects = links.map(a => a.getBoundingClientRect())
    const lines = new Set(rects.map(r => Math.round(r.top)))
    const heights = rects.map(r => Math.round(r.height))
    return { n: links.length, lines: lines.size, maxH: Math.max(...heights), lastRight: Math.round(Math.max(...rects.map(r=>r.right))) }
  })
  console.log(w, JSON.stringify(info))
  await page.screenshot({ path: `nav-pages-shots/header-${w}.png`, clip: { x: 0, y: 0, width: w, height: 120 } })
  await page.close()
}
await browser.close()
