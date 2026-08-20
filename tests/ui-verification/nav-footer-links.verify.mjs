// 抓取消费端首页 header + footer 渲染出的所有链接，逐个打开验证可用性
import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
await page.goto(`${BASE}/en`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3000)

const links = await page.evaluate(() => {
  const grab = (sel, zone) =>
    Array.from(document.querySelectorAll(`${sel} a[href]`)).map((a) => ({
      zone,
      text: (a.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 40) || a.getAttribute('aria-label') || '(icon)',
      href: a.getAttribute('href'),
      target: a.getAttribute('target') || 'self',
    }))
  return [...grab('header', 'header'), ...grab('footer', 'footer')]
})

// 去重
const seen = new Set()
const uniq = links.filter((l) => {
  const k = l.zone + '|' + l.href
  if (seen.has(k)) return false
  seen.add(k)
  return true
})

console.log(`共采集 ${links.length} 条，去重后 ${uniq.length} 条\n`)

const bad = []
for (const l of uniq) {
  if (!l.href || l.href.startsWith('#') || l.href.startsWith('mailto:') || l.href.startsWith('tel:')) {
    console.log(`SKIP  [${l.zone}] ${l.text} -> ${l.href}`)
    continue
  }
  const url = l.href.startsWith('http') ? l.href : BASE + l.href
  const p = await ctx.newPage()
  const pageErrors = []
  p.on('pageerror', (e) => pageErrors.push(e.message.slice(0, 150)))
  let status = null
  let body = ''
  let final = ''
  let err = null
  try {
    const r = await p.goto(url, { waitUntil: 'domcontentloaded', timeout: 45000 })
    status = r ? r.status() : null
    await p.waitForTimeout(3000)
    final = p.url().replace(BASE, '')
    body = (await p.evaluate(() => document.body.innerText || '')).replace(/\s+/g, ' ')
  } catch (e) {
    err = e.message.slice(0, 150)
  }
  const notFound = /404|could not be found/i.test(body.slice(0, 300))
  const empty = body.trim().length < 120
  const ok = status === 200 && !notFound && !err && !empty && pageErrors.length === 0
  if (!ok) bad.push({ ...l, status, final, err, notFound, empty, pageErrors, snippet: body.slice(0, 160) })
  console.log(
    `${ok ? 'PASS' : 'FAIL'} [${l.zone}] ${l.text.padEnd(24)} ${String(l.href).padEnd(34)} http=${status} final=${final}` +
      (notFound ? ' 404文案' : '') + (empty ? ' 空内容' : '') + (pageErrors.length ? ` pageErr=${pageErrors[0]}` : '') + (err ? ` EX=${err}` : '')
  )
  await p.close()
}

console.log(`\n失败 ${bad.length} 条`)
bad.forEach((b) => console.log(`  [${b.zone}] ${b.text} ${b.href} -> ${b.final} :: ${b.snippet}`))
await browser.close()
