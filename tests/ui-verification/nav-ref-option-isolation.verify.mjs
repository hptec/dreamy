// 验证：两个导航项同为「商品」类型时，各自的搜索互不干扰（下拉选项按导航项隔离）
import { chromium } from 'playwright'

const ADMIN = 'http://localhost:5174'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const browser = await chromium.launch()
const page = await browser.newPage()
await page.goto(`${ADMIN}/login`)
await page.fill('input[type="email"]', 'admin@dreamy.com')
await page.fill('input[type="password"]', 'Admin@123456')
await page.click('button[type="submit"]')
await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })

await page.goto(`${ADMIN}/site/navigation`)
await page.waitForSelector('.nav-drag-handle', { timeout: 10000 })
await page.waitForTimeout(800)

// 新增两个导航项，都设为「商品」类型
const panels = page.locator('.panel:has(.nav-drag-handle)')
const baseCount = await panels.count()
await page.click('button:has-text("添加主导航项")')
await page.click('button:has-text("添加主导航项")')
await page.waitForTimeout(200)

const panelA = panels.nth(baseCount)
const panelB = panels.nth(baseCount + 1)

const setToProduct = async (panel) => {
  await panel.locator('div.w-32 button').click()
  await page.locator('li[role="option"]:has-text("商品")').filter({ hasNotText: '分类' }).first().click()
  await page.waitForTimeout(150)
}
await setToProduct(panelA)
await setToProduct(panelB)
await page.waitForTimeout(1200) // 等基础列表拉回

// 读取某个 panel 的目标下拉选项文案（读完再点一次按钮收起，避免浮层挡住下一个 panel）
const readOptions = async (panel) => {
  const btn = panel.locator('div.w-56 button')
  await btn.click()
  await page.waitForSelector('ul[role="listbox"]', { timeout: 5000 })
  const texts = await page.locator('ul[role="listbox"] li[role="option"]').allInnerTexts()
  await btn.click()
  await page.locator('ul[role="listbox"]').waitFor({ state: 'detached', timeout: 3000 }).catch(() => {})
  return texts.map((t) => t.trim())
}

const aBefore = await readOptions(panelA)
const bBefore = await readOptions(panelB)
check(aBefore.length > 1, `A 初始加载到商品列表（${aBefore.length} 条）`)
check(bBefore.length === aBefore.length, `B 初始与 A 同为完整列表（${bBefore.length} 条）`)

// 只在 A 里搜索
console.log('\n[核心] 在 A 的搜索框输入 "Veil"')
await panelA.locator('input[placeholder="搜索..."]').fill('Veil')
await page.waitForTimeout(1200)

const aAfter = await readOptions(panelA)
const bAfter = await readOptions(panelB)
console.log('  A 搜索后:', aAfter)
console.log('  B 选项数:', bAfter.length)
check(aAfter.length < aBefore.length && aAfter.every((t) => /veil/i.test(t)), 'A 的下拉收敛为 Veil 命中项')
check(bAfter.length === bBefore.length, `B 的下拉未被 A 的搜索改动（${bBefore.length} → ${bAfter.length}）`)

// 反向：在 B 里搜索另一个词，A 保持自己的结果
console.log('\n[反向] 在 B 的搜索框输入 "Bridesmaid"')
await panelB.locator('input[placeholder="搜索..."]').fill('Bridesmaid')
await page.waitForTimeout(1200)

const aFinal = await readOptions(panelA)
const bFinal = await readOptions(panelB)
console.log('  A 选项:', aFinal)
console.log('  B 选项:', bFinal)
check(bFinal.length > 0 && bFinal.every((t) => /bridesmaid/i.test(t)), 'B 的下拉收敛为 Bridesmaid 命中项')
check(JSON.stringify(aFinal) === JSON.stringify(aAfter), 'A 的下拉保持自己的 Veil 结果，未被 B 覆盖')

// 各自选中不同目标后互不串改
await panelA.locator('div.w-56 button').click()
await page.locator('li[role="option"]').first().click()
await page.waitForTimeout(200)
await panelB.locator('div.w-56 button').click()
await page.locator('li[role="option"]').first().click()
await page.waitForTimeout(200)
const aLabel = (await panelA.locator('div.w-56 button').innerText()).trim()
const bLabel = (await panelB.locator('div.w-56 button').innerText()).trim()
console.log('  A 选中:', aLabel, '| B 选中:', bLabel)
check(aLabel !== bLabel && /veil/i.test(aLabel) && /bridesmaid/i.test(bLabel), '两项各自选中的目标互不影响')

// ============ 保存后回显：不能退化成 #id 占位 ============
console.log('\n[保存回显] 保存后下拉仍显示名称而非 #id')
await panelA.locator('input.w-48').fill('ISO-A-probe')
await panelB.locator('input.w-48').fill('ISO-B-probe')
await page.click('button:has-text("保存")')
const toast = page.locator('[role="status"]').last()
await toast.waitFor({ state: 'visible', timeout: 8000 })
console.log('  保存结果:', (await toast.innerText()).trim())
await page.waitForTimeout(1500)

const panelByLabel = async (text) => {
  const inputs = page.locator('.panel:has(.nav-drag-handle) input.w-48')
  for (let i = 0; i < (await inputs.count()); i += 1) {
    if ((await inputs.nth(i).inputValue()) === text) return panels.nth(i)
  }
  return null
}
const savedA = await panelByLabel('ISO-A-probe')
const savedB = await panelByLabel('ISO-B-probe')
check(savedA !== null && savedB !== null, '保存后两项都在列表中')
const savedALabel = (await savedA.locator('div.w-56 button').innerText()).trim()
const savedBLabel = (await savedB.locator('div.w-56 button').innerText()).trim()
console.log('  A 回显:', savedALabel, '| B 回显:', savedBLabel)
check(!savedALabel.startsWith('#') && /veil/i.test(savedALabel), `A 保存后仍显示商品名（实际 ${savedALabel}）`)
check(!savedBLabel.startsWith('#') && /bridesmaid/i.test(savedBLabel), `B 保存后仍显示商品名（实际 ${savedBLabel}）`)

// ============ 清理 ============
console.log('\n[清理] 删除验证用导航项')
for (const name of ['ISO-A-probe', 'ISO-B-probe']) {
  const p = await panelByLabel(name)
  if (p) await p.locator('.btn-danger-ghost').click()
}
await page.click('button:has-text("保存")')
await page.waitForTimeout(1500)
console.log('  清理完成')

await browser.close()
if (failures.length > 0) {
  console.error(`\n失败 ${failures.length} 项:`)
  failures.forEach((f) => console.error(' -', f))
  process.exit(1)
}
console.log('\n全部通过')
