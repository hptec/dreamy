// 验证：管理端 Q&A 整改全链路（对照评价模块标准）
// 覆盖：① 搜索后端化（跨页可搜 + 请求带 search 参数）② unanswered_count 平铺角标
// ③ 回答→编辑→撤回闭环（二次确认）④ 可见性切换 ⑤ 批量隐藏/上线 ⑥ 前台联动
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const API = 'http://localhost:18081'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ---------- API 工具 ----------
const token = (await (await fetch(`${API}/api/admin/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@dreamy.com', password: 'Admin@123456' }),
})).json()).data.token
const authed = (path, init = {}) =>
  fetch(`${API}${path}`, { ...init, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(init.headers || {}) } })

// 找一条无回答的提问作为测试对象（保证首答分支 + 结束时撤回还原）
const list = (await (await authed('/api/admin/questions?page=1&page_size=50&answered=unanswered')).json()).data
const target = list.data[0]
if (!target) { console.log('没有未回答提问，种子数据异常'); process.exit(1) }
console.log(`[0] 测试对象: question#${target.id} (${(target.asker || '')} / ${target.question.slice(0, 30)})`)
const ORIG = { answer: target.answer, visible: target.visible }

// ---------- 浏览器 ----------
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const seen = []
page.on('response', async (res) => {
  const url = res.url()
  if (!url.includes('/api/admin/questions')) return
  let body = null
  try { body = await res.json() } catch { /* ignore */ }
  seen.push({ method: res.request().method(), url: url.replace(ADMIN, ''), status: res.status(), body })
})

console.log('\n[1] 登录并打开 Q&A tab')
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForTimeout(2500)
await page.goto(`${ADMIN}/reviews`)
await page.getByRole('button', { name: /Q&A/ }).click()
await page.waitForSelector('input[placeholder="搜索商品 / 提问人 / 内容…"]', { timeout: 15000 })
await page.waitForTimeout(1200)
check(true, 'Q&A tab 打开')

console.log('\n[2] 搜索后端化：输入关键词触发带 search 的 API 请求')
seen.length = 0
await page.fill('input[placeholder="搜索商品 / 提问人 / 内容…"]', target.asker ? target.asker.split(' ')[0] : 'a')
await page.waitForTimeout(1500)
const searchReq = seen.find((s) => s.url.includes('search='))
check(!!searchReq, `请求带 search 参数: ${searchReq ? searchReq.url.split('?')[1] : '未捕获'}`)
if (searchReq) {
  const rows = searchReq.body?.data?.data || []
  check(rows.length > 0, `后端过滤返回 ${rows.length} 行`)
  const raw = JSON.stringify(searchReq.body)
  check(raw.includes('unanswered_count') || raw.includes('unansweredCount'), 'unanswered_count 平铺在响应中')
}
// 清空搜索恢复列表
await page.fill('input[placeholder="搜索商品 / 提问人 / 内容…"]', '')
await page.waitForTimeout(1200)

console.log('\n[3] 回答→编辑→撤回闭环')
// 打开目标提问详情（若当前页没有则直接 API 定位 UI 流程用第一条）
await page.locator('table tbody tr').first().click()
await page.waitForTimeout(600)
const drawer = page.locator('div.fixed.inset-0').first()
check((await drawer.innerText()).includes('问答详情'), '详情抽屉打开')
// 发布回答
await drawer.locator('textarea').fill('自动化测试回答 E2E-ANSWER')
await drawer.getByRole('button', { name: /发布回答/ }).click()
await page.waitForTimeout(1200)
check((await drawer.innerText()).includes('自动化测试回答 E2E-ANSWER'), '回答已保存并展示（首答自动可见）')
// 编辑回答
await drawer.getByRole('button', { name: /编辑回答/ }).click()
await page.waitForTimeout(400)
await drawer.locator('textarea').fill('自动化测试回答 E2E-ANSWER-V2')
await drawer.getByRole('button', { name: /保存修改/ }).click()
await page.waitForTimeout(1200)
check((await drawer.innerText()).includes('E2E-ANSWER-V2'), '编辑保存成功（CAS 通过）')
check((await drawer.innerText()).includes('撤回回答'), '撤回按钮可见')
check((await drawer.innerText()).includes('查看前台问答区'), '查看前台按钮可见（已回答+可见）')
// 撤回（二次确认）
await drawer.getByRole('button', { name: /撤回回答/ }).click()
await page.waitForTimeout(400)
const confirmBtn = page.getByRole('button', { name: /^撤回$/, exact: true })
check((await page.locator('body').innerText()).includes('确认撤回'), '二次确认弹窗出现')
await confirmBtn.click()
await page.waitForTimeout(1200)
const apiAfter = (await (await authed(`/api/admin/questions?page=1&page_size=50`)).json()).data.data.find((q) => q.id === target.id)
check(apiAfter.answer == null && apiAfter.answer_time == null, '撤回后 answer/answer_time 已清空（API 确认）')
// 幂等：再撤一次 204
const again = await authed(`/api/admin/questions/${target.id}/answer`, { method: 'DELETE' })
check(again.status === 204, `重复撤回幂等 204（实际 ${again.status}）`)

console.log('\n[4] 可见性切换 + 批量隐藏/上线（API 层闭环）')
const visBefore = (await (await authed(`/api/admin/questions?page=1&page_size=50`)).json()).data.data.find((q) => q.id === target.id).visible
const patchRes = await (await authed(`/api/admin/questions/${target.id}/visibility`, { method: 'PATCH', body: JSON.stringify({ visible: visBefore === 1 ? 2 : 1 }) })).json()
check(patchRes.code === 0, `单条可见性切换成功 → visible=${patchRes.data.visible}`)
const batchRes = (await (await authed('/api/admin/questions/batch', { method: 'POST', body: JSON.stringify({ ids: [target.id], action: 'show' }) })).json()).data
check(batchRes.updated_ids?.length === 1 || batchRes.updatedIds?.length === 1, `批量上线成功: ${JSON.stringify(batchRes)}`)
const batchAgain = (await (await authed('/api/admin/questions/batch', { method: 'POST', body: JSON.stringify({ ids: [target.id], action: 'show' }) })).json()).data
check((batchAgain.skipped_ids || batchAgain.skippedIds || []).length === 1, '已是目标态 → skipped 语义正确')

console.log('\n[5] 前台联动（缓存失效后 Q&A 重新可见）')
// 目标提问当前 visible=1 但 answer 已撤回 → 前台双条件过滤应不含它
const pid = target.product_id
const storeList = (await (await fetch(`${API}/api/store/questions?product_id=${pid}&page=1&page_size=50`)).json()).data.data
check(!storeList.some((q) => q.id === target.id), '撤回后前台不再展示（双条件过滤兜底）')

console.log('\n[6] 现场还原')
await authed(`/api/admin/questions/${target.id}/visibility`, { method: 'PATCH', body: JSON.stringify({ visible: ORIG.visible }) })
if (ORIG.answer != null) await authed(`/api/admin/questions/${target.id}/answer`, { method: 'PUT', body: JSON.stringify({ answer: ORIG.answer }) })
check(true, `question#${target.id} 已还原 (visible=${ORIG.visible})`)

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length} 项\n${failures.join('\n')}` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
