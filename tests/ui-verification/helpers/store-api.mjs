// 消费端验证脚本共用：OTP stub 登录（从 logs/identity.log 抓验证码）、REST 调用、浏览器 token 注入
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const API = process.env.API_BASE ?? 'http://localhost:18081'
export const STORE = process.env.STORE_BASE ?? 'http://localhost:5173'
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
export const LOG_FILE = process.env.OTP_LOG ?? path.join(ROOT, 'logs/identity.log')

export async function api(method, p, { body, token } = {}) {
  const res = await fetch(`${API}${p}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json = null
  try { json = await res.json() } catch { /* empty */ }
  return { status: res.status, json, data: json?.data, code: json?.code }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function latestOtpCode() {
  const text = fs.readFileSync(LOG_FILE, 'utf8')
  const lines = text.split('\n').filter((l) => l.includes('[MAIL-STUB]') && l.includes('code=otp'))
  const last = lines[lines.length - 1] ?? ''
  const m = last.match(/vars=\{code=(\d{4,8})/)
  return m ? m[1] : null
}

/** OTP stub 登录 → { tokens, user }；频控 429 时按 retry_after_seconds 等待一次 */
export async function loginStore(email) {
  let r = await api('POST', '/api/store/auth/otp/send', { body: { email, locale: 'en' } })
  if (r.status === 429) {
    const wait = Number(r.data?.retry_after_seconds ?? 61)
    console.log(`  (OTP 频控 429，等待 ${wait}s)`)
    await sleep(wait * 1000)
    r = await api('POST', '/api/store/auth/otp/send', { body: { email, locale: 'en' } })
  }
  if (r.status !== 200) throw new Error(`otp/send failed: ${r.status} ${JSON.stringify(r.json)}`)
  await sleep(800)
  const code = latestOtpCode()
  if (!code) throw new Error('OTP code not found in identity.log')
  const v = await api('POST', '/api/store/auth/otp/verify', { body: { email, code } })
  if (v.status !== 200) throw new Error(`otp/verify failed: ${v.status} ${JSON.stringify(v.json)}`)
  return { tokens: v.data.tokens, user: v.data.user }
}

/** 把 TokenPair 注入浏览器 localStorage（lib/api/token-store 键约定），并可设置展示币种 */
export async function injectSession(page, tokens, { currency } = {}) {
  await page.addInitScript(({ tokens, currency }) => {
    // 仅首次注入：refresh token 会被浏览器续期轮换，后续硬导航不可用原始值覆盖
    if (!localStorage.getItem('dreamy_store_refresh')) {
      localStorage.setItem('dreamy_store_access', tokens.access_token)
      localStorage.setItem('dreamy_store_access_exp', tokens.access_expires_at)
      localStorage.setItem('dreamy_store_refresh', tokens.refresh_token)
      localStorage.setItem('dreamy_store_refresh_exp', tokens.refresh_expires_at)
    }
    if (currency && !localStorage.getItem('dreamy_currency')) localStorage.setItem('dreamy_currency', JSON.stringify(currency))
    localStorage.setItem('dreamy_cookie_consent', 'denied')
    sessionStorage.setItem('dreamy_newsletter_seen', '1')
  }, { tokens, currency })
}

/** 浏览器续期后 access 会轮换：从 localStorage 取当前 access token 供后续 API 调用 */
export async function currentAccessToken(page) {
  return page.evaluate(() => localStorage.getItem('dreamy_store_access'))
}

export async function adminLogin() {
  const r = await api('POST', '/api/admin/auth/login', { body: { email: 'admin@dreamy.com', password: 'Admin@123456' } })
  if (r.status !== 200) throw new Error(`admin login failed ${r.status}`)
  return r.data.token
}

/** 挂 console error 收集器（排除已知噪音：资源 404、favicon） */
export function collectConsoleErrors(page) {
  const errors = []
  page.on('console', (m) => {
    if (m.type() !== 'error') return
    const text = m.text()
    // 预期 4xx（负向用例）与 dev server 偶发连接重置属于网络噪声，不算应用错误
    if (/favicon|Failed to load resource: the server responded with a status of 4\d\d|net::ERR_CONNECTION_RESET/.test(text)) return
    errors.push(text.slice(0, 200))
  })
  page.on('pageerror', (e) => errors.push(`pageerror: ${String(e).slice(0, 200)}`))
  return errors
}
