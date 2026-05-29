#!/usr/bin/env node
/**
 * check-mega-menu.js — 打开首页，hover Special Occasion 菜单，截图验证图片加载
 */
const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const OUT_DIR = path.resolve(__dirname, 'out');
const QA_DIR  = path.resolve(__dirname, '.qa/visual');
const INDEX   = 'file://' + path.join(OUT_DIR, 'index.html');
fs.mkdirSync(QA_DIR, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 400 });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page    = await context.newPage();

  // 收集图片加载失败记录
  const failedImgs = [];
  page.on('response', r => {
    if (r.url().includes('competitor-refs') && r.status() !== 200) {
      failedImgs.push({ url: r.url(), status: r.status() });
    }
  });

  console.log('打开首页...');
  await page.goto(INDEX, { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.waitForTimeout(1500);

  // 截图首页
  await page.screenshot({ path: path.join(QA_DIR, 'mega-01-homepage.png') });
  console.log('截图: mega-01-homepage.png');

  // Hover Special Occasion 菜单（含 featured 图片）
  console.log('Hover Special Occasion...');
  const soLink = page.locator('text=SPECIAL OCCASION').first();
  await soLink.hover();
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(QA_DIR, 'mega-02-special-occasion-hover.png') });
  console.log('截图: mega-02-special-occasion-hover.png');

  // Hover Wedding Dresses
  console.log('Hover Wedding Dresses...');
  const wdLink = page.locator('text=WEDDING DRESSES').first();
  await wdLink.hover();
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(QA_DIR, 'mega-03-wedding-dresses-hover.png') });
  console.log('截图: mega-03-wedding-dresses-hover.png');

  // 滚动首页查看图片
  console.log('滚动首页...');
  await page.mouse.move(0, 0); // 收起 mega menu
  await page.waitForTimeout(500);
  await page.evaluate(() => window.scrollTo(0, 600));
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(QA_DIR, 'mega-04-homepage-scroll.png') });
  console.log('截图: mega-04-homepage-scroll.png');

  await browser.close();

  console.log('\n══════════════════════════════');
  if (failedImgs.length === 0) {
    console.log('✓ 所有 competitor-refs 图片加载成功');
  } else {
    console.log(`✗ 失败图片 ${failedImgs.length} 个:`);
    failedImgs.forEach(f => console.log(`  [${f.status}] ${f.url}`));
  }
  console.log(`\n截图保存在: ${QA_DIR}`);
})().catch(e => { console.error(e); process.exit(1); });
