// usage: node _unsplash-search.mjs out.tsv "query1" "query2" ...   (harvests free-license photo CDN urls via Unsplash napi in a real browser)
import { chromium } from 'playwright'
import fs from 'fs'
const [out, ...queries] = process.argv.slice(2)
const b = await chromium.launch({ headless: false, args: ['--disable-blink-features=AutomationControlled'] })
const ctx = await b.newContext({ viewport: { width: 1400, height: 900 } })
const p = await ctx.newPage()
await p.goto('https://unsplash.com/s/photos/bride', { waitUntil: 'commit', timeout: 60000 })
for (let i = 0; i < 20; i++) { await p.waitForTimeout(2000); let t = ''; try { t = await p.title() } catch {} ; if (t && !/not a bot|Loading/i.test(t)) { console.log('passed:', t.slice(0,50)); break } }
const seen = new Map()
for (const q of queries) {
  for (let page = 1; page <= (Number(process.env.PAGES)||3); page++) {
    let js
    try {
      js = await p.evaluate(async ({ q, page }) => { const r = await fetch(`/napi/search/photos?query=${encodeURIComponent(q)}&per_page=30&page=${page}&order_by=relevant`); return r.status === 200 ? await r.json() : { status: r.status } }, { q, page })
    } catch (e) { console.log('err', q, page, e.message.slice(0,80)); break }
    if (!js.results) { console.log('bad', q, page, js.status); break }
    for (const r of js.results) {
      if (r.premium || r.plus || /plus\.unsplash\.com/.test(r.urls?.raw || '')) continue
      const m = (r.urls?.raw || '').match(/images\.unsplash\.com\/(photo-[0-9]+-[0-9a-f]+)/)
      if (!m) continue
      if (!seen.has(r.id)) seen.set(r.id, { id: r.id, cdn: m[1], w: r.width, h: r.height, alt: (r.alt_description || r.description || '').replace(/\s+/g, ' ').slice(0, 140), q, tags: (r.tags || []).map(t => t.title).slice(0, 8).join(',') })
    }
    console.log(q, page, js.total, 'seen', seen.size)
    if (page * 30 >= js.total) break
    await p.waitForTimeout(400)
  }
}
fs.writeFileSync(out, [...seen.values()].map(r => [r.id, r.cdn, r.w, r.h, r.q, r.alt, r.tags].join('\t')).join('\n') + '\n')
console.log('wrote', seen.size)
await b.close()
