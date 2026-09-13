// 导航 ↔ 目录一致性审计：站点里每一条「商品列表类」链接（导航/mega menu/页脚/静态兜底/outdoor tiles）
// 都必须落到 ≥1 款商品，且返回的每一款都命中链接语义（分类子树 / 属性值 / 色板集合）。
// 同时抓每个链接页面的 HTTP 状态，确认没有 404。
//
// 用法：
//   node tests/ui-verification/nav-catalog-audit.mjs                    # 审生产（API + 页面同域）
//   SITE=http://localhost:5199 API=https://dreamy.cerestech.cn:60080 node tests/ui-verification/nav-catalog-audit.mjs
// 退出码：有任一 FAIL → 1。

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const API = (process.env.API ?? 'https://dreamy.cerestech.cn:60080').replace(/\/$/, '')
const SITE = (process.env.SITE ?? API).replace(/\/$/, '')
const here = path.dirname(fileURLToPath(import.meta.url))
const repo = path.resolve(here, '../..')

async function api(p) {
  const res = await fetch(`${API}/api${p}`)
  const json = await res.json()
  if (json.code !== 0) throw new Error(`${p} → ${JSON.stringify(json).slice(0, 200)}`)
  return json.data
}

// ── 目录快照 ──
const tree = (await api('/store/categories')).items
const byName = new Map()
const parentOf = new Map()
for (const c of tree) {
  byName.set(c.name, c)
  for (const ch of c.children ?? []) { byName.set(ch.name, ch); parentOf.set(ch.id, c.id) }
}
const subtreeIds = (id) => {
  const top = tree.find((c) => c.id === id)
  return new Set(top ? [top.id, ...(top.children ?? []).map((c) => c.id)] : [id])
}

const collections = new Map()
for (const g of (await api('/store/collections')).items) for (const c of g.collections) collections.set(String(c.id), c.name)

// 全量商品（含属性/集合/分类）作为语义核对基准
const cards = []
for (let p = 1; ; p++) {
  const d = await api(`/store/products?page=${p}&page_size=20`)
  cards.push(...d.data)
  if (p >= d.total_pages) break
}
const detail = new Map()
await Promise.all(cards.map(async (c) => detail.set(c.id, await api(`/store/products/${c.slug}`))))

// ── 收集链接 ──
/** @type {{source:string,label:string,href:string}[]} */
const links = []
const nav = await api('/store/content/navigation?locale=en')
for (const i of nav.items) {
  if (i.url) links.push({ source: 'nav', label: i.label, href: i.url })
  for (const col of i.mega_menu?.columns ?? []) for (const l of col.links) links.push({ source: `mega:${i.label}/${col.title}`, label: l.label, href: l.href })
  if (i.mega_menu?.featured) links.push({ source: `mega:${i.label}/featured`, label: i.mega_menu.featured.label, href: i.mega_menu.featured.href })
}
const footer = await api('/store/content/footer?locale=en')
for (const col of footer.columns) for (const l of col.links) links.push({ source: `footer:${col.title}`, label: l.label, href: l.url })

// 静态兜底导航 + outdoor tiles（源码抓取，保证 API 为空时的回退也不说谎）
const navTs = readFileSync(path.join(repo, 'frontend/portal-store/data/navigation.ts'), 'utf8')
for (const m of navTs.matchAll(/label:\s*'([^']+)',\s*href:\s*'([^']+)'/g)) links.push({ source: 'static', label: m[1], href: m[2] })
const outdoor = readFileSync(path.join(repo, 'frontend/portal-store/app/[locale]/outdoor-weddings/page.tsx'), 'utf8')
for (const m of outdoor.matchAll(/theme:\s*'([^']+)',\s*href:\s*'([^']+)'/g)) links.push({ source: 'outdoor-tile', label: m[1], href: m[2] })

// ── 逐条解析（与 CollectionPage 同口径）──
const ROUTE_PARENT = { '/wedding-dresses': 'Wedding Dresses', '/accessories': 'Accessories', '/special-occasion': 'Occasion & Party', '/products': null }
const results = []
const seen = new Set()
for (const link of links) {
  const key = `${link.source}|${link.href}`
  if (seen.has(key)) continue
  seen.add(key)
  const u = new URL(link.href, 'http://x')
  const listing = u.pathname in ROUTE_PARENT
  // 页面可达性
  let status
  try { status = (await fetch(SITE + link.href, { redirect: 'manual' })).status } catch { status = 0 }
  if (!listing) {
    results.push({ ...link, ok: status === 200 || (status >= 300 && status < 400), status, note: status === 404 ? '死链' : '' })
    continue
  }
  const cat = u.searchParams.get('cat')
  const parentName = ROUTE_PARENT[u.pathname]
  const parent = parentName ? byName.get(parentName) : null
  const catNode = cat ? byName.get(cat) : null
  const unknownCat = !!cat && !catNode
  const categoryId = catNode?.id ?? parent?.id
  const attrs = []
  for (const [k, v] of u.searchParams) if (k.startsWith('a_')) for (const val of v.split('|')) attrs.push([k.slice(2), decodeURIComponent(val)])
  const collectionId = u.searchParams.get('collection')

  const q = new URLSearchParams({ page_size: '50' })
  if (categoryId) q.set('category_id', String(categoryId))
  if (collectionId) q.set('collection_id', collectionId)
  for (const [k, v] of attrs) q.append('attr', `${k}:${v}`)
  const total = unknownCat ? 0 : (await api(`/store/products?${q}`)).total_elements
  const got = unknownCat ? [] : (await api(`/store/products?${q}`)).data

  // 语义核对：分类子树 / 属性值（同 key OR）/ 色板集合
  const problems = []
  if (unknownCat) problems.push(`cat='${cat}' 不在分类树`)
  if (total === 0 && !unknownCat) problems.push('0 款商品')
  if (categoryId) {
    const allowed = catNode && parentOf.has(catNode.id) ? new Set([catNode.id]) : subtreeIds(categoryId)
    for (const p of got) if (!allowed.has(detail.get(p.id).category_id)) problems.push(`'${p.name}' 分类 ${detail.get(p.id).category_name} 越界`)
  }
  const byKey = {}
  for (const [k, v] of attrs) (byKey[k] ??= []).push(v)
  for (const p of got) for (const [k, vals] of Object.entries(byKey)) {
    const have = detail.get(p.id).attributes.find((a) => a.key === k)?.values.map((x) => x.value) ?? []
    if (!vals.some((v) => have.includes(v))) problems.push(`'${p.name}' 无属性 ${k}∈{${vals}}`)
  }
  if (collectionId) for (const p of got) if (!detail.get(p.id).collections.some((c) => String(c.id) === collectionId)) problems.push(`'${p.name}' 不在集合 ${collections.get(collectionId)}`)
  // 空页面链接（生产 /accessories?cat=Shoes 一类）—— 允许 unknownCat 只在“旧链接兼容检查”里出现，导航里不允许
  results.push({ ...link, ok: problems.length === 0 && status === 200, status, total, note: problems.slice(0, 3).join('; ') })
}

// ── 报告 ──
const pad = (s, n) => String(s ?? '').padEnd(n).slice(0, n)
let fails = 0
for (const r of results) {
  if (!r.ok) fails++
  console.log(`${r.ok ? 'PASS' : 'FAIL'} ${pad(r.source, 34)} ${pad(r.label, 26)} ${pad(r.href, 62)} http=${r.status} n=${r.total ?? '-'} ${r.note ?? ''}`)
}
console.log(`\n${results.length} 条链接，FAIL ${fails}`)
process.exit(fails ? 1 : 0)
