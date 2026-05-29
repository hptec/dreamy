#!/usr/bin/env node
/**
 * click-all-nav.js — 打开 index.html，点击所有顶部导航链接，截图验证跳转
 */
const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const OUT_DIR = path.resolve(__dirname, 'out');
const QA_DIR  = path.resolve(__dirname, '.qa/nav-clicks');
const INDEX   = 'file://' + path.join(OUT_DIR, 'index.html');

fs.mkdirSync(QA_DIR, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 300 });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page    = await context.newPage();

  const results = [];

  console.log('打开首页...');
  await page.goto(INDEX, { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.waitForTimeout(1500);

  // 收集所有顶部导航链接（nav 或 header 内的 <a>）
  const navLinks = await page.$$eval('header a[href], nav a[href]', els =>
    els.map(a => ({ text: a.innerText.trim(), href: a.getAttribute('href') }))
       .filter(l => l.text && l.href && !l.href.startsWith('http') && !l.href.startsWith('mailto') && l.href !== '#')
  );

  // 去重
  const seen = new Set();
  const unique = navLinks.filter(l => { const k = l.href; if (seen.has(k)) return false; seen.add(k); return true; });

  console.log(`找到 ${unique.length} 个导航链接:\n`);
  unique.forEach(l => console.log(`  "${l.text}" → ${l.href}`));
  console.log('');

  for (const link of unique) {
    // 每次从首页开始点击
    await page.goto(INDEX, { waitUntil: 'domcontentloaded', timeout: 15000 });
    await page.waitForTimeout(1000);

    console.log(`点击 "${link.text}" (${link.href})...`);

    // 重新找到该链接并点击
    const el = await page.locator(`header a[href="${link.href}"], nav a[href="${link.href}"]`).first();
    await el.click();
    await page.waitForTimeout(1500);

    const url   = page.url();
    const title = await page.title().catch(() => '');
    const ok    = !url.includes('chrome-error') && !url.includes('file:///Volumes') || url.includes(link.href.replace('.html','').replace('./',''));

    const shotName = link.text.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '') + '.png';
    await page.screenshot({ path: path.join(QA_DIR, shotName), fullPage: false });

    const status = url.includes('chrome-error') ? '✗ FAIL' : '✓ OK';
    console.log(`  ${status}  url: ${url.replace('file://'+OUT_DIR, '')}`);
    console.log(`  title: "${title}"`);

    results.push({ text: link.text, href: link.href, url, title, status: url.includes('chrome-error') ? 'FAIL' : 'OK' });
  }

  await browser.close();

  console.log('\n══════════════════════════════');
  const failed = results.filter(r => r.status === 'FAIL');
  console.log(`总计: ${results.length}  失败: ${failed.length}`);
  if (failed.length) {
    console.log('\n失败项:');
    failed.forEach(r => console.log(`  "${r.text}" → ${r.href}`));
  } else {
    console.log('所有导航链接点击正常 ✓');
  }
  console.log(`\n截图: ${QA_DIR}`);
})().catch(e => { console.error(e); process.exit(1); });
