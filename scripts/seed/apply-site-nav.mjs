// 对已有环境（不重灌）重放站点导航/页脚 + 配饰分类改名，让导航口径与真实目录对齐。
// 用法：API_BASE=https://dreamy.cerestech.cn:60080/api node scripts/seed/apply-site-nav.mjs [--dry-run]
//   本地：API_BASE=http://localhost:18081/api node scripts/seed/apply-site-nav.mjs
// 操作：
//   1. 分类 'Wraps & Cover-Ups' → 'Getting Ready'（里面挂的是 Getting Ready 睡衣套装，原名与商品不符）
//   2. PUT navigation（整体替换：7 顶级项 + 4 个 mega menu，链接全部指向真实分类/属性/色板集合）
//   3. PUT footer（整体替换：/lookbooks 死链 → /inspiration；补 Occasion & Party / Accessories / Outdoor Weddings）
// 幂等：重复执行结果一致；site_builder 缓存 TTL 600s 或由写入侧失效任务清理。
import { login, get, put } from './api.mjs'
import { buildNavItems, buildFooterColumns } from './data-site-nav.mjs'

const DRY = process.argv.includes('--dry-run')
const log = (m) => console.log(`[apply-site-nav] ${m}`)

const RENAMES = [{ from: 'Wraps & Cover-Ups', to: 'Getting Ready', translations: [
  { locale: 'es', name: 'Preparativos de la Novia' }, { locale: 'fr', name: 'Préparatifs de la Mariée' }
] }]

async function main() {
  await login()

  // 分类树（admin 视图含 attribute_set_id / translations，PUT 时需要原样带回）
  const tree = (await get('/admin/categories')).items
  const catIdByPath = {}
  const flat = []
  for (const c of tree) {
    catIdByPath[c.name] = c.id
    flat.push(c)
    for (const ch of c.children ?? []) {
      catIdByPath[`${c.name}/${ch.name}`] = ch.id
      flat.push(ch)
    }
  }

  // ① 分类改名
  for (const r of RENAMES) {
    const node = flat.find((n) => n.name === r.from)
    if (!node) {
      log(`分类改名跳过：'${r.from}' 不存在（可能已改为 '${r.to}'）`)
      continue
    }
    const body = {
      name: r.to, parent_id: node.parent_id ?? null, attribute_set_id: node.attribute_set_id ?? null,
      attr_overrides: node.attr_overrides ?? null, sort: node.sort ?? null, translations: r.translations
    }
    log(`分类改名 #${node.id} '${r.from}' → '${r.to}'${DRY ? '（dry-run）' : ''}`)
    if (!DRY) await put(`/admin/categories/${node.id}`, body)
    // 后续按名字取 id 的地方用新名
    catIdByPath[r.to] = node.id
  }

  // 色板集合 id（store 端点匿名可读）
  const groups = (await get('/store/collections')).items
  const colIdByName = {}
  for (const g of groups) for (const c of g.collections) colIdByName[c.name] = c.id

  // ② 导航
  const navItems = buildNavItems(catIdByPath, colIdByName)
  log(`导航 ${navItems.length} 项，mega menu ${navItems.filter((i) => i.mega_menu_json).length} 个${DRY ? '（dry-run）' : ''}`)
  if (DRY) console.log(JSON.stringify(navItems, null, 2))
  else await put('/admin/site-builder/navigation', { items: navItems, version: 0 })

  // ③ 页脚
  const columns = buildFooterColumns()
  log(`页脚 ${columns.length} 栏 / ${columns.reduce((n, c) => n + c.links.length, 0)} 链接${DRY ? '（dry-run）' : ''}`)
  if (!DRY) await put('/admin/site-builder/footer', { version: 0, columns })

  // 回读核验
  if (!DRY) {
    const nav = await get('/store/content/navigation?locale=en')
    const labels = nav.items.filter((i) => i.parent_id == null).map((i) => `${i.label}→${i.url}${i.mega_menu ? '*' : ''}`)
    log(`回读导航：${labels.join(' | ')}`)
    const cats = (await get('/store/categories')).items
    const acc = cats.find((c) => c.name === 'Accessories')
    log(`回读 Accessories 子类：${(acc?.children ?? []).map((c) => `${c.name}(${c.product_count})`).join(', ')}`)
  }
  log('完成')
}

main().catch((e) => {
  console.error('[apply-site-nav] 失败:', e.message)
  process.exit(1)
})
