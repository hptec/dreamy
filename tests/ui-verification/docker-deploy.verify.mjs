// 验证:Docker 全栈部署链路(本地 compose 验证,端口可用环境变量覆盖)
// - store:首页 RSC 渲染商品数据、/api middleware 同源代理、console 零错误
// - admin:登录(bootstrap 管理员)、/api nginx 反代、进入工作台
import { chromium } from 'playwright'

// 注意:访问地址必须与 .env.deploy 的 PUBLIC_STORE_URL/PUBLIC_ADMIN_URL 完全一致
// (同源 POST 带 Origin 头,127.0.0.1 与 localhost 是不同源)
const STORE = process.env.VERIFY_STORE_URL || 'http://localhost:5175'
const ADMIN = process.env.VERIFY_ADMIN_URL || 'http://localhost:5176'
const ADMIN_EMAIL = process.env.VERIFY_ADMIN_EMAIL || 'verify@dreamy.local'
const ADMIN_PASSWORD = process.env.VERIFY_ADMIN_PASSWORD || 'Verify-Admin-2026'

const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const browser = await chromium.launch({ headless: true })

function newPageWithConsoleWatch() {
  const page = { errors: [] }
  const p = browser.newPage()
  return p.then(realPage => {
    realPage.on('console', (m) => {
      if (m.type() !== 'error') return
      // 已知非阻断告警:server-fetch 被 review-api 共用进客户端 bundle,production 缺 API_ORIGIN 时打印
      if (m.text().includes('[server-fetch] API_ORIGIN')) return
      page.errors.push(m.text().slice(0, 160))
    })
    realPage.on('pageerror', (e) => page.errors.push(String(e).slice(0, 160)))
    page.handle = realPage
    return page
  })
}

console.log('[1] store 首页:RSC 商品数据 + console 零错误')
{
  const page = await newPageWithConsoleWatch()
  await page.handle.goto(`${STORE}/`, { waitUntil: 'networkidle', timeout: 45000 })
  await page.handle.waitForTimeout(1000)
  const body = await page.handle.content()
  check(/hair vine|dress|gown|Fleur/i.test(body), '首页渲染出商品内容(RSC 服务端取数)')
  check(page.errors.length === 0, `console 零错误${page.errors.length ? ':' + page.errors[0] : ''}`)
  await page.handle.close()
}

console.log('[2] store 商品详情页:中间件代理 + 页面交互正常')
{
  const page = await newPageWithConsoleWatch()
  const apiStatuses = []
  page.handle.on('response', (r) => { if (r.url().includes('/api/')) apiStatuses.push(r.status()) })
  await page.handle.goto(`${STORE}/`, { waitUntil: 'networkidle', timeout: 45000 })
  const link = page.handle.locator('a[href^="/product/"]').first()
  await link.click()
  await page.handle.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {})
  await page.handle.waitForTimeout(1500)
  check(page.handle.url().includes('/product/'), `进入商品详情(${page.handle.url()})`)
  check(apiStatuses.every((s) => s < 500), `API 请求无 5xx(${apiStatuses.join(',') || '无'})`)
  check(page.errors.length === 0, `console 零错误${page.errors.length ? ':' + page.errors[0] : ''}`)
  await page.handle.close()
}

console.log('[3] store 同源 POST(OTP 发送):CORS 链路验证')
{
  const page = await newPageWithConsoleWatch()
  await page.handle.goto(`${STORE}/`, { waitUntil: 'domcontentloaded', timeout: 45000 })
  const otpStatus = await page.handle.evaluate(async () => {
    const res = await fetch('/api/store/auth/otp/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'deploy-verify@dreamy.local' })
    })
    return res.status
  })
  check(otpStatus === 200 || otpStatus === 400, `OTP POST 返回业务响应(${otpStatus},403=CORS 拒绝)`)
  await page.handle.close()
}

console.log('[4] admin 登录(nginx /api 反代)+ 工作台加载')
{
  const page = await newPageWithConsoleWatch()
  await page.handle.goto(`${ADMIN}/`, { waitUntil: 'networkidle', timeout: 45000 })
  await page.handle.fill('input[type="email"], input[name="email"]', ADMIN_EMAIL)
  await page.handle.fill('input[type="password"], input[name="password"]', ADMIN_PASSWORD)
  await page.handle.click('button[type="submit"]')
  await page.handle.waitForURL((u) => !u.pathname.endsWith('/login'), { timeout: 30000 }).catch(() => {})
  await page.handle.waitForTimeout(2000)
  const url = page.handle.url()
  check(!/login/.test(url), `登录跳转成功(${url})`)
  const body = await page.handle.content()
  check(body.length > 500, '工作台内容渲染')
  check(page.errors.length === 0, `console 零错误${page.errors.length ? ':' + page.errors[0] : ''}`)
  await page.handle.close()
}

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length} 项未通过` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
