import { chromium, devices } from 'playwright'
const base = process.argv[2]; const out = process.argv[3]
const pages = [['home','/'],['plp-wedding','/products?cat=Wedding+Dresses'],['plp-bridesmaids','/products?cat=Bridesmaids'],['pdp-noelle','/product/noelle-mermaid-wedding-dress'],['real-weddings','/real-weddings'],['about','/about']]
const b = await chromium.launch(); const ctx = await b.newContext({ ...devices['iPhone 13'] })
const p = await ctx.newPage()
for (const [n, path] of pages) { await p.goto(base + path, { waitUntil: 'networkidle', timeout: 60000 }); await p.waitForTimeout(800); await p.screenshot({ path: `${out}/m-${n}.jpg`, quality: 70, type: 'jpeg' }); console.log('ok', n) }
await b.close()
