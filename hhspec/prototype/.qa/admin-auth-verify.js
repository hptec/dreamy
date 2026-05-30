const { chromium } = require('playwright');

(async () => {
  const base = 'http://localhost:5175';
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const step = (s) => console.log(s);

  await page.goto(base + '/products', { waitUntil: 'networkidle' });
  step('1. 未登录访问 /products -> ' + new URL(page.url()).pathname +
    (page.url().includes('redirect') ? ' (含 redirect)' : ''));

  await page.fill('input[type=email]', 'admin@dreamy.com');
  await page.click('button[type=submit]');
  await page.waitForTimeout(300);
  const err = await page.locator('.text-danger').first().textContent().catch(() => '');
  step('2. 空密码提交错误提示 -> ' + (err ? err.trim() : '无'));

  await page.fill('input[type=password]', 'anything');
  await page.click('button[type=submit]');
  await page.waitForTimeout(900);
  step('3. 登录后落地 -> ' + new URL(page.url()).pathname);

  await page.reload({ waitUntil: 'networkidle' });
  step('4. 刷新后路径 -> ' + new URL(page.url()).pathname + ' (登录态持久化)');

  await page.goto(base + '/login', { waitUntil: 'networkidle' });
  step('5. 已登录访问 /login -> ' + new URL(page.url()).pathname);

  await page.getByRole('button', { name: /Super Admin/ }).click();
  await page.waitForTimeout(400);
  await page.getByRole('menuitem', { name: /退出登录/ }).click();
  await page.waitForTimeout(600);
  step('6. 登出后 -> ' + new URL(page.url()).pathname);

  await page.goto(base + '/orders', { waitUntil: 'networkidle' });
  step('7. 登出后访问 /orders -> ' + new URL(page.url()).pathname);

  await browser.close();
})();
