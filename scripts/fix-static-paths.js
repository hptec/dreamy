#!/usr/bin/env node
/**
 * fix-static-paths.js
 * 修复 Next.js 静态导出，使 out/ 目录下的 HTML 可通过 file:// 协议直接打开。
 *
 * 修复内容：
 *   1. /_next/ 资源路径 → 相对路径（按目录深度）
 *   2. /competitor-refs/ 图片路径 → 相对路径
 *   3. href="/page" 导航链接 → 相对 .html 文件路径
 *   4. webpack runtime r.p → 动态计算 base path
 *   5. 注入客户端导航拦截脚本（拦截 Next.js pushState 跳转）
 */

const fs   = require('fs');
const path = require('path');

const OUT_DIR = path.resolve(__dirname, '../hhspec/prototype/out');

// 收集所有已生成的 HTML 文件路径（相对于 out/），用于判断目标页面是否存在
function collectHtmlFiles() {
  const set = new Set();
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.html')) {
        set.add('/' + path.relative(OUT_DIR, full).replace(/\\/g, '/'));
      }
    }
  }
  walk(OUT_DIR);
  return set;
}

// ── 1. Patch webpack runtime ──────────────────────────────────────────────────
function patchWebpackRuntime() {
  const chunksDir = path.join(OUT_DIR, '_next', 'static', 'chunks');
  const files = fs.readdirSync(chunksDir).filter(f => f.startsWith('webpack-') && f.endsWith('.js'));

  for (const file of files) {
    const filePath = path.join(chunksDir, file);
    let content = fs.readFileSync(filePath, 'utf8');

    const dynamicP = [
      '(function(){',
        'var s=document.currentScript;',
        'if(s&&s.src){',
          'var m=s.src.match(/^(.*\\/)_next\\/static\\/chunks\\//);',
          'if(m)return m[1]+"_next/";',
        '}',
        'return"/_next/";',
      '})()'
    ].join('');

    const before = content;
    content = content.replace(/r\.p="\/\_next\/"/, `r.p=${dynamicP}`);

    if (content !== before) {
      fs.writeFileSync(filePath, content);
      console.log(`  ✓ patched webpack runtime: ${file}`);
    } else {
      console.warn(`  ⚠ webpack runtime pattern not found in: ${file}`);
    }
  }
}

// ── 2. 客户端导航拦截脚本 ─────────────────────────────────────────────────────
// 注入到每个 HTML 的 <head> 最前面，在所有 Next.js 脚本之前执行。
// 策略：
//   - 不修改 pushState/replaceState，避免破坏 React/Next.js hydration
//   - 仅在 capture 阶段拦截 <a> 点击，强制按 .html 文件跳转
//   - 动态计算当前页面深度，生成正确的相对路径
function navInterceptScript() {
  return `<script>
(function(){
  function getPrefix() {
    var p = location.pathname;
    var dir = p.substring(0, p.lastIndexOf('/') + 1);
    var m = dir.match(/\\/out(\\/.*)?$/);
    if (!m || !m[1]) return './';
    var depth = m[1].replace(/^\\//,'').split('/').filter(Boolean).length;
    return depth === 0 ? './' : '../'.repeat(depth);
  }

  function toHtmlUrl(href) {
    if (!href) return null;
    if (href.startsWith('http') || href.startsWith('mailto') || href === '#') return null;

    // 已经是相对 .html 链接，直接使用
    if (href.endsWith('.html') || href.includes('.html?') || href.includes('.html#')) {
      return href;
    }

    // 绝对路径 /foo → ./foo.html
    if (href.startsWith('/')) {
      var p = href.split('?')[0].split('#')[0].replace(/\\/$/, '') || '/';
      var suffix = href.slice(p.length);
      var prefix = getPrefix();
      return (p === '/' ? prefix + 'index.html' : prefix + p.replace(/^\\//, '') + '.html') + suffix;
    }

    return null;
  }

  // 在 capture 阶段拦截所有页面导航链接，绕过 Next.js Link 的客户端路由
  document.addEventListener('click', function(e) {
    var a = e.target.closest('a[href]');
    if (!a) return;
    var href = a.getAttribute('href');
    var target = toHtmlUrl(href);
    if (!target) return;

    e.preventDefault();
    e.stopImmediatePropagation();
    location.assign(target);
  }, true);
})();
</script>`;
}

// ── 3. Patch JS chunks 里的 /competitor-refs/ 绝对路径 ─────────────────────────
// JS bundle（客户端渲染）里的图片 src 是硬编码绝对路径，需要替换为相对路径。
// 策略：把完整的 "/competitor-refs/xxx" 字符串替换为使用 window.__dreamy_base__ 的表达式。
// 只替换形如 "/competitor-refs/" 开头的完整字符串字面量，不拆引号，不改 module 常量。
function patchJsChunks() {
  const chunksDir = path.join(OUT_DIR, '_next', 'static', 'chunks');
  let total = 0;

  function patchDir(dir) {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const fp = path.join(dir, entry.name);
      if (entry.isDirectory()) { patchDir(fp); continue; }
      if (!entry.name.endsWith('.js')) continue;
      let c = fs.readFileSync(fp, 'utf8');
      if (!c.includes('"/competitor-refs/') && !c.includes('"/competitor-refs"')) continue;
      const before = c;
      // ① 完整字符串字面量 "/competitor-refs/..."（直到下一个双引号）
      c = c.replace(/"\/competitor-refs\/([^"]+)"/g,
        (_, rest) => `(window.__dreamy_base__||"./")+\"competitor-refs/${rest}\"`
      );
      // ② 裸的 base 常量 "/competitor-refs"（无尾斜杠，源码里 const C="/competitor-refs" 再 concat/模板拼接）
      //    替换为 (window.__dreamy_base__||"/")+"competitor-refs"，使后续 +"/davidsbridal/.." 拼出相对路径
      c = c.replace(/"\/competitor-refs"/g,
        `((window.__dreamy_base__||"/")+"competitor-refs")`
      );
      if (c !== before) {
        fs.writeFileSync(fp, c);
        console.log(`  ✓ patched js chunk: ${path.relative(OUT_DIR, fp)}`);
        total++;
      }
    }
  }

  patchDir(chunksDir);
  if (total === 0) console.log('  (no js chunks needed patching)');
}

function fixAllHtml(htmlFiles) {
  let count = 0;

  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.html')) { fixHtml(full, htmlFiles); count++; }
    }
  }

  walk(OUT_DIR);
  console.log(`  ✓ fixed ${count} HTML files`);
}

function fixHtml(filePath, htmlFiles) {
  const rel   = path.relative(OUT_DIR, filePath).replace(/\\/g, '/');
  const depth = rel.split('/').length - 1;
  const pre   = depth === 0 ? './' : '../'.repeat(depth);

  let html = fs.readFileSync(filePath, 'utf8');

  // /_next/ → relative
  html = html.replace(/"\/_next\//g, `"${pre}_next/`);
  html = html.replace(/'\/_next\//g, `'${pre}_next/`);

  // /competitor-refs/ → relative
  html = html.replace(/"\/competitor-refs\//g, `"${pre}competitor-refs/`);
  html = html.replace(/'\/competitor-refs\//g, `'${pre}competitor-refs/`);

  // href="/page" → href="./page.html"（不要求 > 紧跟，兼容 href 后有其他属性的情况）
  html = html.replace(/href="(\/[^"#?]*)([#?][^"]*)?"/g, (match, p, suffix) => {
    const clean = p.replace(/\/$/, '') || '/';
    const target = clean === '/' ? `${pre}index.html` : `${pre}${clean.replace(/^\//, '')}.html`;
    // 只替换已知存在的页面
    const exists = htmlFiles.has(clean + '.html') || htmlFiles.has(clean + '/index.html') || clean === '/';
    if (!exists) return match;
    return `href="${target}${suffix || ''}"`;
  });

  // 注入：① 全局 base 变量（供 JS chunk 用）② 导航拦截脚本
  const baseScript = `<script>window.__dreamy_base__="${pre}";</script>`;
  html = html.replace('<head>', '<head>' + baseScript + navInterceptScript());

  fs.writeFileSync(filePath, html);
}

// ── main ──────────────────────────────────────────────────────────────────────
console.log('\n[fix-static-paths] 修复 file:// 路径...');
const htmlFiles = collectHtmlFiles();
console.log(`  收集到 ${htmlFiles.size} 个 HTML 文件`);
patchWebpackRuntime();
patchJsChunks();
fixAllHtml(htmlFiles);
console.log('[fix-static-paths] 完成\n');
console.log('直接用浏览器打开：');
console.log(`  open ${path.join(OUT_DIR, 'index.html')}\n`);
