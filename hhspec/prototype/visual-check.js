#!/usr/bin/env node
/**
 * visual-check.js — 用 Playwright 打开 file:// 本地 HTML，
 * 截图记录每个页面，报告加载失败项。
 *
 * 链接收集策略：直接读 HTML 文件（不依赖 DOM），避开 React hydration 改写 href 的问题。
 */
const { chromium } = require('playwright');
const path = require('path');
const fs   = require('fs');

const OUT_DIR  = path.resolve(__dirname, 'out');
const QA_DIR   = path.resolve(__dirname, '.qa/visual');
const INDEX    = 'file://' + path.join(OUT_DIR, 'index.html');
const REPORT   = path.resolve(__dirname, '.qa/visual-report.json');

fs.mkdirSync(QA_DIR, { recursive: true });

// 从 HTML 文件内容中提取内部链接（不依赖 DOM，避开 React hydration）
function extractLinksFromFile(filePath) {
  const html = fs.readFileSync(filePath, 'utf8');
  const links = new Set();
  const re = /href="([^"#?]+)/g;
  let m;
  while ((m = re.exec(html)) !== null) {
    const href = m[1];
    if (!href) continue;
    if (href.startsWith('http') || href.startsWith('mailto') || href.startsWith('//')) continue;
    if (href.startsWith('_next') || href.startsWith('./_next') || href.startsWith('../')) {
      // 跳过资源链接，只保留页面链接
      if (!href.endsWith('.html')) continue;
    }
    // 只保留 .html 结尾的相对路径
    if (!href.endsWith('.html')) continue;
    // 解析为绝对文件路径
    const dir = path.dirname(filePath);
    const abs = path.resolve(dir, href);
    if (abs.startsWith(OUT_DIR)) {
      links.add('file://' + abs);
    }
  }
  return [...links];
}

// 收集 out/ 下所有 HTML 文件
function allHtmlFiles() {
  const files = [];
  function walk(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) walk(full);
      else if (e.name.endsWith('.html')) files.push(full);
    }
  }
  walk(OUT_DIR);
  return files;
}

async function main() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page    = await context.newPage();

  // 忽略 React hydration 错误（#418 是 file:// 下的预期错误，不影响渲染）
  page.on('pageerror', () => {});

  const results  = [];
  const allFiles = allHtmlFiles();
  const fileUrls = new Set(allFiles.map(f => 'file://' + f));

  console.log(`检查 ${allFiles.length} 个页面...\n`);

  for (const filePath of allFiles) {
    const url    = 'file://' + filePath;
    const rel    = filePath.replace(OUT_DIR, '') || '/index.html';
    const shotName = rel.replace(/^\//, '').replace(/\//g, '_').replace(/\.html$/, '') || 'index';
    const shotPath = path.join(QA_DIR, shotName + '.png');

    console.log(`[${results.length + 1}/${allFiles.length}] ${rel}`);

    let title = '';
    let status = 'OK';
    let errMsg = '';

    try {
      // 禁用 JS 执行前先注入：阻止 pushState 在 goto 期间触发额外跳转
      await context.addInitScript(() => {
        // 在 Next.js 脚本执行前冻结 pushState，防止 goto 期间被劫持
        const _push = history.pushState.bind(history);
        history.pushState = function(s, t, u) {
          // 只允许 .html 结尾或空的 pushState
          if (u && typeof u === 'string' && !u.endsWith('.html') && u.startsWith('/')) return;
          return _push(s, t, u);
        };
      });

      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 15000 });
      // 等待页面稳定，但不等 networkidle（file:// 下可能永远不触发）
      await page.waitForTimeout(800);
      title = await page.title().catch(() => '');
    } catch (e) {
      status = 'LOAD_FAIL';
      errMsg = e.message.split('\n')[0];
      console.log(`  ✗ ${errMsg}`);
    }

    if (status === 'OK') {
      try {
        await page.screenshot({ path: shotPath, fullPage: true });
        console.log(`  ✓ "${title}"`);
      } catch (e) {
        console.log(`  ⚠ 截图失败: ${e.message.split('\n')[0]}`);
      }
    }

    // 从 HTML 文件提取链接，验证目标文件存在
    const links = extractLinksFromFile(filePath);
    const broken = links.filter(l => !fileUrls.has(l.split('?')[0].split('#')[0]));

    if (broken.length) {
      console.log(`  ⚠ 断链 ${broken.length} 个:`);
      broken.forEach(b => console.log(`    ${b.replace('file://' + OUT_DIR, '')}`));
    }

    results.push({ url: rel, title, screenshot: shotName + '.png', links: links.length, broken: broken.map(b => b.replace('file://' + OUT_DIR, '')), status, error: errMsg });
  }

  await browser.close();

  fs.writeFileSync(REPORT, JSON.stringify(results, null, 2));

  const failed  = results.filter(r => r.status !== 'OK');
  const hasBroken = results.filter(r => r.broken && r.broken.length > 0);
  console.log(`\n══════════════════════════════`);
  console.log(`总页面：${results.length}  加载失败：${failed.length}  有断链：${hasBroken.length}`);
  if (failed.length) {
    console.log('\n加载失败：');
    failed.forEach(r => console.log(`  ${r.url}  ${r.error}`));
  }
  if (hasBroken.length) {
    console.log('\n断链：');
    hasBroken.forEach(r => r.broken.forEach(b => console.log(`  ${r.url} → ${b}`)));
  }
  console.log(`\n截图：${QA_DIR}`);
  console.log(`报告：${REPORT}`);
}

main().catch(e => { console.error(e); process.exit(1); });
