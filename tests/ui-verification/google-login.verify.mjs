import { chromium } from '/Users/harryhe/.npm/_npx/1ceecc7911b2a271/node_modules/playwright/index.mjs'

const STORE = 'http://localhost:5173'
const OUT = '/Volumes/MAC/workspace/dreamy/tests/ui-verification'

const browser = await chromium.launch()
const page = await browser.newPage()
const results = {}

try {
  await page.goto(`${STORE}/account/login`, { waitUntil: 'networkidle' })

  // 等 authConfig 拉取 + OAuthButtons 渲染
  const googleBtn = page.getByRole('button', { name: /Continue with Google/i })
  await googleBtn.waitFor({ state: 'visible', timeout: 10000 })
  results.googleButtonVisible = await googleBtn.isVisible()

  await page.screenshot({ path: `${OUT}/login-with-google.png`, fullPage: true })

  // 点击按钮，断言跳转到 Google 授权页（拦截导航，不真去 google）
  let navUrl = null
  page.on('framenavigated', (f) => {
    if (f === page.mainFrame()) {
      const u = f.url()
      if (u.includes('accounts.google.com')) navUrl = u
    }
  })
  // 拦截 google 域，避免真实外网请求挂起
  await page.route('**/accounts.google.com/**', (route) =>
    route.fulfill({ status: 200, body: 'intercepted' })
  )

  await googleBtn.click()
  await page.waitForURL(/accounts\.google\.com/, { timeout: 8000 }).catch(() => {})
  const finalUrl = page.url()
  results.navigatedToGoogle = finalUrl.includes('accounts.google.com')
  results.finalUrl = finalUrl
  const params = new URL(finalUrl.includes('accounts.google.com') ? finalUrl : (navUrl ?? 'http://x'))
  results.clientId = params.searchParams.get('client_id')
  results.responseType = params.searchParams.get('response_type')
  results.scope = params.searchParams.get('scope')
  results.redirectUri = params.searchParams.get('redirect_uri')
} catch (e) {
  results.error = String(e)
} finally {
  console.log(JSON.stringify(results, null, 2))
  await browser.close()
}
