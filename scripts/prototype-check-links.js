#!/usr/bin/env node
/**
 * prototype-check-links.js — 用 Playwright 遍历 out/ 所有 HTML 页面，
 * 收集每个页面上的所有 <a href> 链接并尝试导航，记录失败项。
 */
const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const OUT_DIR   = path.resolve(__dirname, '../hhspec/prototype/out');
const INDEX     = 'file://' + path.join(OUT_DIR, 'index.html');
const REPORT    = path.resolve(__dirname, '../hhspec/prototype/.qa/link-check.json');

// 收集 out/ 下所有 HTML 文件的 file:// URL
function allHtmlUrls() {
  const urls = [];
  function walk(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) walk(full);
      else if (e.name.endsWith('.html')) urls.push('file://' + full);
    }
  }
  walk(OUT_DIR);
  return urls;
}

async function main() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext();
  const page    = await context.newPage();

  const results  = [];   // { page, href, resolved, status }
  const visited  = new Set();
  const pageUrls = allHtmlUrls();

  console.log(`检查 ${pageUrls.length} 个页面...`);

  for (const pageUrl of pageUrls) {
    const pageRel = pageUrl.replace('file://' + OUT_DIR, '');
    console.log(`\n── ${pageRel}`);

    try {
      await page.goto(pageUrl, { waitUntil: 'domcontentloaded', timeout: 10000 });
    } catch (e) {
      results.push({ page: pageRel, href: '', resolved: pageUrl, status: 'PAGE_LOAD_FAIL', error: e.message });
      continue;
    }

    // 收集页面上所有内部链接
    const links = await page.$$eval('a[href]', els =>
      els.map(a => ({ href: a.getAttribute('href'), text: a.innerText.trim().slice(0, 40) }))
    );

    for (const { href, text } of links) {
      if (!href) continue;
      // 跳过外部链接、锚点、mailto
      if (href.startsWith('http') || href.startsWith('mailto') || href.startsWith('#')) continue;
      // 跳过 query-only
      if (href.startsWith('?')) continue;

      // 解析相对路径为绝对 file:// URL
      let resolved;
      try {
        resolved = new URL(href, pageUrl).href;
      } catch {
        results.push({ page: pageRel, href, resolved: '', status: 'INVALID_HREF', text });
        continue;
      }

      // 只检查 file:// 协议
      if (!resolved.startsWith('file://')) continue;

      // 去掉 query/hash 后检查文件是否存在
      const filePath = resolved.replace('file://', '').split('?')[0].split('#')[0];
      const exists   = fs.existsSync(filePath);
      const status   = exists ? 'OK' : 'NOT_FOUND';

      if (status !== 'OK') {
        console.log(`  ✗ ${status}  ${href}  →  ${filePath.replace(OUT_DIR, '')}`);
      }

      results.push({ page: pageRel, href, resolved: filePath.replace(OUT_DIR, ''), status, text });
    }
  }

  await browser.close();

  // 写报告
  fs.mkdirSync(path.dirname(REPORT), { recursive: true });
  fs.writeFileSync(REPORT, JSON.stringify(results, null, 2));

  // 汇总
  const failed = results.filter(r => r.status !== 'OK');
  const ok     = results.filter(r => r.status === 'OK');
  console.log(`\n══════════════════════════════`);
  console.log(`总链接数：${results.length}  OK：${ok.length}  失败：${failed.length}`);
  if (failed.length) {
    console.log('\n失败列表：');
    const uniq = [...new Map(failed.map(f => [f.href + f.page, f])).values()];
    for (const f of uniq) {
      console.log(`  [${f.status}]  ${f.page}  →  href="${f.href}"  →  ${f.resolved}`);
    }
  }
  console.log(`\n报告已写入：${REPORT}`);
}

main().catch(e => { console.error(e); process.exit(1); });
