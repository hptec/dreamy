// 生产 Google OIDC 链路验证：登录页 → 点击 Continue with Google → 真实跳转 Google 登录页
// 断言无 redirect_uri_mismatch（走到 identifier 页即证明 redirect_uri 已注册）
import { chromium } from './node_modules/playwright/index.mjs'

const STORE = 'https://dreamy.cerestech.cn:60080'
const OUT = '/Volumes/MAC/workspace/dreamy/tests/ui-verification'
const PROXY = process.env.VERIFY_PROXY ?? 'socks5://127.0.0.1:1080'

const browser = await chromium.launch({
  proxy: { server: PROXY },
})
const page = await browser.newPage()
const results = { store: STORE }

try {
  await page.goto(`${STORE}/account/login`, { waitUntil: 'domcontentloaded', timeout: 45000 })

  const googleBtn = page.getByRole('button', { name: /Continue with Google/i })
  await googleBtn.waitFor({ state: 'visible', timeout: 15000 })
  results.googleButtonVisible = true

  await page.screenshot({ path: `${OUT}/prod-login-page.png`, fullPage: true })

  await googleBtn.click()
  await page.waitForURL(/accounts\.google\.com/, { timeout: 30000 })
  results.navigatedToGoogle = true

  // 等待 Google 登录页主体渲染（邮箱输入框），排除错误页
  await page.waitForSelector('input[type="email"]', { timeout: 30000 })
  results.googleIdentifierPage = true

  const finalUrl = page.url()
  results.finalUrlHost = new URL(finalUrl).host
  const params = new URL(finalUrl)
  results.clientId = params.searchParams.get('client_id')
  results.redirectUri = params.searchParams.get('redirect_uri')
  results.responseType = params.searchParams.get('response_type')

  const body = await page.content()
  results.mismatchError = /redirect_uri_mismatch|错误\s*400|Error\s*400|invalid_request/i.test(body)

  await page.screenshot({ path: `${OUT}/prod-google-authorize.png` })
} catch (e) {
  results.error = String(e).slice(0, 500)
  results.pageUrlAtFailure = page.url()
  try { await page.screenshot({ path: `${OUT}/prod-google-verify-fail.png` }) } catch {}
} finally {
  console.log(JSON.stringify(results, null, 2))
  await browser.close()
}
