// 验证：商品页 #qa 锚点联动修复后无 hydration mismatch，且 hash 直达 Q&A tab 仍工作
import { chromium } from 'playwright'

const STORE = 'http://localhost:5173'
const failures = []
const check = (ok, msg) => { console.log(ok ? `  ✓ ${msg}` : `  ✗ ${msg}`); if (!ok) failures.push(msg) }

const browser = await chromium.launch({ headless: true })

async function openProduct(hash) {
  const page = await browser.newPage()
  const hydrationErrors = []
  page.on('console', (m) => {
    if (m.type() === 'error' && /hydrat|didn't match|server rendered/i.test(m.text())) {
      hydrationErrors.push(m.text().slice(0, 120))
    }
  })
  await page.goto(`${STORE}/product/meadow-bridesmaid${hash}`, { waitUntil: 'networkidle', timeout: 30000 })
  await page.waitForTimeout(1500)
  return { page, hydrationErrors }
}

console.log('[1] 不带 hash：默认 Reviews tab + 无 hydration 错误')
{
  const { page, hydrationErrors } = await openProduct('')
  const qaBtn = page.locator('section#reviews button', { hasText: 'Q&A' })
  const qaClass = await qaBtn.getAttribute('class')
  check((qaClass || '').includes('border-transparent'), 'Reviews 为激活 tab')
  check(hydrationErrors.length === 0, `无 hydration 错误${hydrationErrors.length ? '：' + hydrationErrors[0] : ''}`)
  await page.close()
}

console.log('\n[2] 带 #qa：自动切 Q&A tab + 无 hydration 错误')
{
  const { page, hydrationErrors } = await openProduct('#qa')
  const qaBtn = page.locator('section#reviews button', { hasText: 'Q&A' })
  const qaClass = await qaBtn.getAttribute('class')
  check((qaClass || '').includes('border-gold'), 'Q&A 为激活 tab（hash 联动生效）')
  check(hydrationErrors.length === 0, `无 hydration 错误${hydrationErrors.length ? '：' + hydrationErrors[0] : ''}`)
  await page.close()
}

await browser.close()
console.log(failures.length ? `\nFAILED: ${failures.length}` : '\nALL PASS')
process.exit(failures.length ? 1 : 0)
