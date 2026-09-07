import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
const results = []

function record(name, ok, detail = '') {
  results.push([name, ok, detail])
}

const browser = await chromium.launch()
const page = await browser.newPage()

try {
  /* ============ 1. 商品页评价排序下拉 ============ */
  await page.goto(`${BASE}/product/hair-vine`, { waitUntil: 'networkidle' })

  // 自绘触发器（button[role=combobox]）替代原生 select
  const nativeSelect = await page.locator('select#review-sort').count()
  const trigger = page.locator('button#review-sort')
  const triggerCount = await trigger.count()
  record('评价排序：原生 <select> 已移除', nativeSelect === 0)
  record('评价排序：自绘触发器存在（role=combobox）', triggerCount === 1)

  if (triggerCount === 1) {
    // 展开弹层
    await trigger.click()
    const listbox = page.locator('ul[role="listbox"]')
    await listbox.waitFor({ state: 'visible', timeout: 3000 })
    const optionCount = await listbox.locator('li[role="option"]').count()
    record('评价排序：弹层展开 4 个选项', optionCount === 4, `实际 ${optionCount}`)

    // 弹层 luxe 样式（portal 到 body、shadow-lift、无原生渲染）
    const popupClass = await listbox.getAttribute('class')
    const inBody = await listbox.evaluate((el) => el.parentElement === document.body)
    record('评价排序：弹层 portal 到 body 且带 shadow-lift', inBody && /shadow-lift/.test(popupClass ?? ''), popupClass)

    // 选中项视觉：当前 Featured 高亮 + 勾选
    const selectedLabel = await listbox.locator('li[aria-selected="true"]').textContent()
    record('评价排序：当前选中项勾选', (selectedLabel ?? '').includes('Featured'), `选中: ${selectedLabel}`)

    // 键盘导航：ArrowDown 高亮第二项 → Enter 选择
    await trigger.focus()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')
    const triggerText = (await trigger.textContent()) ?? ''
    record('评价排序：键盘 ↑↓+Enter 选中 Newest', /newest/i.test(triggerText), `触发器: ${triggerText}`)

    // Escape 关闭
    await trigger.click()
    await listbox.waitFor({ state: 'visible', timeout: 3000 })
    await page.keyboard.press('Escape')
    record('评价排序：Escape 关闭弹层', (await listbox.count()) === 0 || !(await listbox.first().isVisible().catch(() => false)))

    // 点击外部关闭
    await trigger.click()
    await listbox.waitFor({ state: 'visible', timeout: 3000 })
    await page.locator('body').click({ position: { x: 30, y: 400 } })
    await page.waitForTimeout(200)
    record('评价排序：点击外部关闭弹层', !(await listbox.first().isVisible().catch(() => false)))
  }

  /* ============ 2. 集合页排序下拉（URL 驱动） ============ */
  await page.goto(`${BASE}/products`, { waitUntil: 'networkidle' })
  const sortTrigger = page.locator('button#sort')
  if ((await sortTrigger.count()) === 1) {
    await sortTrigger.click()
    const listBox2 = page.locator('ul[role="listbox"]')
    await listBox2.waitFor({ state: 'visible', timeout: 3000 })
    const optTexts = await listBox2.locator('li[role="option"]').allTextContents()
    record('集合排序：弹层 4 选项', optTexts.length === 4, optTexts.join(' / '))
    await listBox2.locator('li[role="option"]').filter({ hasText: 'Price: Low to High' }).click()
    await page.waitForURL(/sort=price_asc/, { timeout: 5000 })
    record('集合排序：选 Price Low→High 后 URL 带 sort=price_asc', /sort=price_asc/.test(page.url()), page.url())
    const afterText = (await sortTrigger.textContent()) ?? ''
    record('集合排序：触发器标签同步更新', /low to high/i.test(afterText), `触发器: ${afterText}`)
  } else {
    record('集合排序：自绘触发器存在', false, `count=${await sortTrigger.count()}`)
  }

  /* ============ 3. Contact 页主题下拉（表单） ============ */
  await page.goto(`${BASE}/contact`, { waitUntil: 'networkidle' })
  const subjectTrigger = page.locator('button#subject')
  if ((await subjectTrigger.count()) === 1) {
    await subjectTrigger.click()
    const listBox3 = page.locator('ul[role="listbox"]')
    await listBox3.waitFor({ state: 'visible', timeout: 3000 })
    const subjCount = await listBox3.locator('li[role="option"]').count()
    record('Contact 主题：弹层 5 个选项', subjCount === 5, `实际 ${subjCount}`)
    await listBox3.locator('li[role="option"]').filter({ hasText: 'Wholesale' }).click()
    const subjText = (await subjectTrigger.textContent()) ?? ''
    record('Contact 主题：选择后触发器更新为 Wholesale', /wholesale/i.test(subjText), `触发器: ${subjText}`)
  } else {
    record('Contact 主题：自绘触发器存在', false)
  }
} catch (err) {
  console.error('执行异常:', err.message)
} finally {
  console.log('\n--- 结果 ---')
  let fail = 0
  for (const [name, ok, detail] of results) {
    if (!ok) fail++
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `\n      ${detail}` : ''}`)
  }
  await browser.close()
  if (fail > 0) process.exit(1)
}
