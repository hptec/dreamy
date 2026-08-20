import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
const LANG_ARIA = /^(Language|Idioma|Langue)$/

const browser = await chromium.launch()
const page = await browser.newPage()
const results = []

async function switchLang(optionLabel) {
  await page.getByRole('button', { name: LANG_ARIA }).first().click()
  await page.getByRole('button', { name: optionLabel, exact: true }).click()
}

try {
  await page.goto(`${BASE}/fr/products?cat=Shoes`, { waitUntil: 'networkidle' })

  await switchLang('Español')
  await page.waitForURL(/\/es\/products/, { timeout: 15000 })
  const afterEs = page.url()
  results.push(['FR→ES 保留 query', afterEs, afterEs === `${BASE}/es/products?cat=Shoes`])

  await switchLang('English')
  await page.waitForURL((u) => /^\/products/.test(u.pathname), { timeout: 15000 })
  const afterEn = page.url()
  results.push(['ES→EN 保留 query', afterEn, afterEn === `${BASE}/products?cat=Shoes`])

  // 无 query 页面不应多出 "?"
  await page.goto(`${BASE}/products`, { waitUntil: 'networkidle' })
  await switchLang('Français')
  await page.waitForURL(/\/fr\/products/, { timeout: 15000 })
  const noQuery = page.url()
  results.push(['无 query 时不追加 ?', noQuery, noQuery === `${BASE}/fr/products`])
} catch (err) {
  console.error('执行异常:', err.message)
} finally {
  console.log('\n--- 结果 ---')
  for (const [name, url, ok] of results) {
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}\n      ${url}`)
  }
  await browser.close()
  if (results.length < 3 || results.some(([, , ok]) => !ok)) process.exit(1)
}
