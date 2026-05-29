#!/usr/bin/env node
/**
 * click-nav.js — 打开 index.html，点击 Accessories 导航，截图验证跳转
 */
const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const OUT_DIR = path.resolve(__dirname, 'out');
const QA_DIR  = path.resolve(__dirname, '.qa/visual');
const INDEX   = 'file://' + path.join(OUT_DIR, 'index.html');

fs.mkdirSync(QA_DIR, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 500 });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page    = await context.newPage();

  console.log('打开首页...');
  await page.goto(INDEX, { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(QA_DIR, 'click-01-index.png'), fullPage: false });
  console.log('截图: click-01-index.png  url:', page.url());

  // 找到 Accessories 导航链接并点击
  console.log('\n查找 Accessories 链接...');
  const link = await page.locator('a', { hasText: /^Accessories$/i }).first();
  const href  = await link.getAttribute('href');
  console.log('找到链接, href =', href);

  await link.click();
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(QA_DIR, 'click-02-after-accessories.png'), fullPage: false });
  console.log('点击后 url:', page.url());
  console.log('截图: click-02-after-accessories.png');

  // 验证是否跳到了 accessories 页面
  const url = page.url();
  if (url.includes('accessories')) {
    console.log('\n✓ 跳转成功，当前页面包含 accessories');
  } else {
    console.log('\n✗ 跳转失败，当前 url:', url);
  }

  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
