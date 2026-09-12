import { chromium } from 'playwright'
const base = process.argv[2] || 'https://dreamy.cerestech.cn:60080'
const out = process.argv[3]
const pages = [
  ['home','/'], ['plp-all','/products'], ['plp-wedding','/products?cat=Wedding+Dresses'], ['plp-bridesmaids','/products?cat=Bridesmaids'],
  ['plp-occasion','/products?cat=Occasion+%26+Party'], ['pdp-noelle','/product/noelle-mermaid-wedding-dress'], ['pdp-aria','/product/aria-lace-aline-wedding-dress'], ['pdp-danny','/product/danny-cowl-neck-bridesmaid-dress-blush'], ['pdp-alex','/product/alex-bridesmaid-dress-black'], ['login','/account/login'],
  ['real-weddings','/real-weddings'], ['blog','/blog'], ['blog-timeline','/blog/made-to-order-timeline-guide'], ['about','/about'], ['outdoor','/outdoor-weddings'], ['inspiration','/inspiration']
]
const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
const p = await ctx.newPage()
for (const [n, path] of pages) {
  try {
    await p.goto(base + path, { waitUntil: 'networkidle', timeout: 60000 })
    await p.waitForTimeout(1200)
    await p.screenshot({ path: `${out}/${n}-fold.jpg`, quality: 70, type: 'jpeg' })
    await p.screenshot({ path: `${out}/${n}-full.jpg`, quality: 60, type: 'jpeg', fullPage: true })
    console.log('ok', n)
  } catch (e) { console.log('FAIL', n, e.message.slice(0,100)) }
}
await b.close()
