// Dreamy 生产数据灌入主编排:admin REST API(经公网网关) + 评价 SQL(经 SSH 直插)
// 用法:API_BASE=https://dreamy.cerestech.cn:60080/api node scripts/seed/seed.mjs
// 前置:scripts/seed/reset.sh 已执行(空库);服务器 mysql 容器运行中
import { execFileSync } from 'node:child_process'
import { login, post, put, get } from './api.mjs'
import { attributeDefs, attributeSets, categories, collectionGroups, carriers, shippingOptions } from './data-catalog.mjs'
import { weddingDresses } from './data-products-wedding.mjs'
import { bridesmaidDresses, occasionDresses, accessories } from './data-products-party.mjs'
import { blogPosts, realWeddings, lookbooks, banners, coupons, announcements } from './data-content.mjs'
import { buildReviews, ratingSummary } from './data-reviews.mjs'

const PUBLISHED = 2, ACTIVE = 3
const DEPLOY_SSH = process.env.DEPLOY_SSH ?? 'root@47.238.216.69'
// SEED_TARGET=local:评价 SQL 与缓存清理走本机 docker(pd-mysql/pd-redis),配合 API_BASE=http://localhost:18081/api
const LOCAL = process.env.SEED_TARGET === 'local'
const log = (m) => console.log(`[seed] ${m}`)

// ── 翻译映射 ──
const CAT_I18N = {
  'Wedding Dresses': ['Vestidos de Novia', 'Robes de Mariée'],
  'Beach & Destination': ['Playa y Destino', 'Plage & Destination'],
  'Garden & Boho': ['Jardín y Boho', 'Jardin & Boho'],
  'Classic Elegance': ['Elegancia Clásica', 'Élégance Classique'],
  'Bridesmaids': ['Damas de Honor', 'Demoiselles d\'Honneur'],
  'Long Bridesmaid Dresses': ['Vestidos Largos', 'Robes Longues'],
  'Short & Convertible': ['Cortos y Convertibles', 'Courtes & Convertibles'],
  'Occasion & Party': ['Ocasión y Fiesta', 'Occasion & Fête'],
  'Prom & Evening': ['Gala y Noche', 'Gala & Soirée'],
  'Wedding Guest': ['Invitada de Boda', 'Invitée de Mariage'],
  'Accessories': ['Accesorios', 'Accessoires'],
  'Jewelry & Headpieces': ['Joyería y Tocados', 'Bijoux & Ornements'],
  'Flower Girl': ['Niña de las Flores', "Demoiselle d'Honneur Fille"],
  'Wraps & Cover-Ups': ['Chales y Abrigos', 'Châles & Couvertures']
}
const COLL_I18N = {
  'Coastal Bride': ['Novia Costera', 'Mariée Côtière'],
  'Garden Romance': ['Romance de Jardín', 'Romance du Jardin'],
  'Boho Wildflower': ['Flor Silvestre Boho', 'Fleur Sauvage Boho'],
  'Modern Minimal': ['Minimal Moderno', 'Minimaliste Moderne'],
  'Ivory & Champagne': ['Marfil y Champaña', 'Ivoire & Champagne'],
  'Blush & Dusty Rose': ['Rubor y Rosa Pálido', 'Blush & Rose Poudrée'],
  'Sage & Olive': ['Salvia y Oliva', 'Sauge & Olive'],
  'Blue Hues': ['Tonos Azules', 'Tons Bleus'],
  'Black & Espresso': ['Negro y Espresso', 'Noir & Espresso']
}
const tr = (map, name) => (map[name] ? [
  { locale: 'es', name: map[name][0] },
  { locale: 'fr', name: map[name][1] }
] : undefined)

// hero 款 es/fr 商品翻译(其余 EN 兜底,对齐 demo 3 款策略)
const PRODUCT_I18N = {
  'aria-lace-aline-wedding-dress': {
    es: { name: 'Vestido de Novia Aire Línea A en Encaje', description: 'Encaje floral festoneado y falda línea A ligera como el aire — para la novia que quiere ser ella misma, solo que más.' },
    fr: { name: 'Robe de Mariée Aria Ligne A en Dentelle', description: 'Dentelle florale festonnée et jupe ligne A légère — pour la mariée qui veut être elle-même, en mieux.' }
  },
  'wren-tulle-aline-wedding-dress': {
    es: { name: 'Vestido de Novia Wren Tul Línea A', description: 'Once capas de tul suave y escote cuadrado moderno. Nuestro estilo más pedido para ceremonias al aire libre.' },
    fr: { name: 'Robe de Mariée Wren Tulle Ligne A', description: 'Onze couches de tulle souple et encolure carrée moderne. Notre style le plus demandé pour les cérémonies en plein air.' }
  },
  'cove-short-beach-wedding-dress': {
    es: { name: 'Vestido de Novia Corto Cove para Playa', description: 'Ceremonia descalza, sal en el aire y un vestido que cabe en el equipaje de mano. El tul se seca en minutos.' },
    fr: { name: 'Robe de Mariée Courte Cove pour la Plage', description: 'Cérémonie pieds nus, air marin et une robe qui tient en bagage à main. Le chiffon sèche en quelques minutes.' }
  }
}

// ── 商品 payload 组装 ──
function toProductPayload(p, catIdByPath, colIdByName, sort) {
  const t = PRODUCT_I18N[p.slug]
  return {
    name: p.name, slug: p.slug,
    category_id: catIdByPath[p.categoryPath.join('/')],
    description: p.description,
    designer_note: p.designerNote,
    selling_points: p.sellingPoints,
    price: p.price, compare_at: p.compareAt,
    installment: p.installment, status: p.status,
    is_new: !!p.isNew, is_best: !!p.isBest, recommend: !!p.recommend,
    sort,
    lead_time_days: p.leadTimeDays, rush_available: !!p.rushAvailable,
    custom_size_available: p.customSizeAvailable,
    style_no: `DR-${String(sort).padStart(3, '0')}`,
    seo_title: `${p.name} | Dreamy Outdoor Wedding Atelier`,
    seo_desc: (p.description ?? '').slice(0, 155),
    attributes: Object.entries(p.attributes).map(([key, values]) => ({ key, values })),
    images: (p.images ?? []).map((im) => ({ url: im.url, kind: im.kind, color_name: im.colorName ?? null, sort: im.sort })),
    skus: p.skus.map((s) => ({ sku_code: s.skuCode, color: s.color, size: s.size, stock: s.stock })),
    size_chart: (p.sizeChart ?? []).map((r) => ({ us: r.us, uk: r.uk, au: r.au, bust: r.bust, waist: r.waist, hips: r.hips, hollow_to_floor: r.hollowToFloor ?? null })),
    collection_ids: (p.collections ?? []).map((c) => colIdByName[c]).filter(Boolean),
    translations: t ? [
      { locale: 'es', ...t.es },
      { locale: 'fr', ...t.fr }
    ] : [],
    fabric_compositions: p.fabricCompositions,
    care: p.care,
    fabric_care_note: 'Professional cleaning recommended after wear; store in the garment bag provided.'
  }
}

// ── 评价 SQL(直插 review + user,并同步 product 冗余评分列) ──
const esc = (s) => String(s).replace(/\\/g, '\\\\').replace(/'/g, "''")
const iso = (d) => {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
function buildReviewSql(rows, productIdBySlug, summary) {
  const lines = ['SET FOREIGN_KEY_CHECKS=0;']
  const emails = new Map()
  for (const r of rows) {
    const email = r.name.toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '') + '@seed.dreamy.com'
    emails.set(r.name, email)
  }
  for (const [name, email] of emails) {
    lines.push(`INSERT INTO user (email, name, email_verified, tier, status, joined_at) VALUES ('${esc(email)}', '${esc(name)}', 1, 1, 1, NOW() - INTERVAL 320 DAY);`)
  }
  const featuredSeen = new Set()
  for (const r of rows) {
    const pid = productIdBySlug[r.slug]
    if (!pid) continue
    const email = emails.get(r.name)
    const featured = r.rating === 5 && !featuredSeen.has(r.slug) ? 1 : 0
    if (featured) featuredSeen.add(r.slug)
    const replyCols = r.reply
      ? `, 'Dreamy Team', '${esc(r.reply)}', '${iso(r.replyAt)}'`
      : ', NULL, NULL, NULL'
    lines.push(`INSERT INTO review (product_id, user_id, customer_name, rating, content, status, featured, submitted_at, reply_author, reply_content, reply_time) SELECT ${pid}, id, '${esc(r.name)}', ${r.rating}, '${esc(r.content)}', 2, ${featured}, '${iso(r.submittedAt)}'${replyCols} FROM user WHERE email='${esc(email)}';`)
  }
  for (const [slug, s] of Object.entries(summary)) {
    const pid = productIdBySlug[slug]
    if (pid) lines.push(`UPDATE product SET rating_avg=${s.avg}, rating_count=${s.n}, sales_30d=${Math.floor(s.n * 2.3)} WHERE id=${pid};`)
  }
  lines.push('SET FOREIGN_KEY_CHECKS=1;')
  return lines.join('\n')
}

async function main() {
  log('登录 admin API ...')
  await login()

  // ① 属性定义
  const attrIdByKey = {}
  for (const d of attributeDefs) {
    const r = await post('/admin/attribute-defs', { key: d.key, label: d.label, type: d.type, options: d.options, translations: d.translations })
    attrIdByKey[d.key] = r.id
  }
  log(`属性定义 ${Object.keys(attrIdByKey).length} 个`)

  // ② 属性集
  const setIdByLabel = {}
  for (const s of attributeSets) {
    const r = await post('/admin/attribute-sets', {
      label: s.label,
      items: s.items.map(([key, vis]) => ({ attribute_id: attrIdByKey[key], visibility: vis }))
    })
    setIdByLabel[s.label] = r.id
  }
  log(`属性集 ${Object.keys(setIdByLabel).length} 个`)

  // ③ 分类树
  const catIdByPath = {}
  for (const c of categories) {
    const r = await post('/admin/categories', {
      name: c.name, parent_id: null, attribute_set_id: setIdByLabel[c.attributeSetLabel],
      attr_overrides: null, sort: c.sort, translations: tr(CAT_I18N, c.name)
    })
    catIdByPath[c.name] = r.id
    for (const ch of c.children) {
      const r2 = await post('/admin/categories', {
        name: ch.name, parent_id: r.id, attribute_set_id: null,
        attr_overrides: null, sort: ch.sort, translations: tr(CAT_I18N, ch.name)
      })
      catIdByPath[`${c.name}/${ch.name}`] = r2.id
    }
  }
  log(`分类树 ${Object.keys(catIdByPath).length} 个节点`)

  // ④ 集合组 + 集合
  const colIdByName = {}
  for (const g of collectionGroups) {
    const rg = await post('/admin/collection-groups', { name: g.name, description: g.description })
    for (const c of g.collections) {
      const rc = await post('/admin/collections', { collection_group_id: rg.id, name: c.name, status: c.status, translations: tr(COLL_I18N, c.name) })
      colIdByName[c.name] = rc.id
    }
  }
  log(`集合 ${Object.keys(colIdByName).length} 个`)

  // ⑤ 商品(32 款)
  // 创建顺序 = created_at 序:婚纱最后创建,New Arrivals(created_at DESC)呈现婚纱新款
  const allProducts = [...accessories, ...occasionDresses, ...bridesmaidDresses, ...weddingDresses]
  const productIdBySlug = {}
  // PLP 默认按 sort ASC:深底商品图(Elowen/Juno)排到货架尾部,与 created_at(新品位)解耦
  const ELOWEN = 'elowen-aline-cold-shoulder-wedding-dress', JUNO = 'juno-two-piece-wedding-dress-set'
  const ranked = allProducts.filter((p) => p.slug !== ELOWEN && p.slug !== JUNO)
  // Elowen 落婚纱货架第 9 位、Juno 第 12 位:两张深底图不同排并列
  ranked.splice(ranked.length - 2, 0, allProducts.find((p) => p.slug === ELOWEN))
  ranked.push(allProducts.find((p) => p.slug === JUNO))
  const sortOf = new Map(ranked.map((p, i) => [p.slug, i + 1]))
  let n0 = 0
  for (const p of allProducts) {
    process.stdout.write(`  商品 ${++n0}/${allProducts.length}: ${p.slug} ... `)
    const r = await post('/admin/products', toProductPayload(p, catIdByPath, colIdByName, sortOf.get(p.slug)))
    productIdBySlug[p.slug] = r.id
    console.log(`#${r.id}`)
  }
  log(`商品 ${Object.keys(productIdBySlug).length} 款`)

  // ⑥ blog
  let n = 0
  for (const b of blogPosts) {
    await post('/admin/content/blogs', {
      title: b.title, cover: b.cover, category: b.category, author: b.author, content: b.content,
      slug: b.slug, status: PUBLISHED, excerpt: b.excerpt,
      seo_title: `${b.title} | Dreamy Journal`, seo_description: b.excerpt, translations: []
    })
    n++
  }
  log(`blog ${n} 篇`)

  // ⑦ real weddings
  for (const w of realWeddings) {
    await post('/admin/content/weddings', {
      couple: w.couple, location: w.location, theme: w.theme, wedding_date: w.weddingDate,
      cover: w.cover, status: PUBLISHED, title: w.title, story: w.story,
      product_ids: w.productSlugs.map((s) => productIdBySlug[s]).filter(Boolean), translations: []
    })
  }
  log(`real weddings ${realWeddings.length} 个`)

  // ⑧ lookbooks
  for (const lk of lookbooks) {
    await post('/admin/content/lookbooks', {
      title: lk.title, theme: lk.theme, status: PUBLISHED, description: lk.description, cover: lk.cover,
      product_ids: lk.productSlugs.map((s) => productIdBySlug[s]).filter(Boolean), translations: []
    })
  }
  log(`lookbook ${lookbooks.length} 本`)

  // ⑨ banner(position: 1=HERO 2=FEATURED)
  for (const b of banners) {
    await post('/admin/banners', {
      name: b.name, image_url: b.imageUrl, position: b.position === 'HERO' ? 1 : 2,
      start_time: '2026-01-01T00:00:00', end_time: '2027-12-31T23:59:59',
      status: PUBLISHED, sort: b.sort, title: b.title, subtitle: b.subtitle,
      cta_text: b.ctaText, cta_link: b.ctaLink, cta_text_secondary: null, cta_link_secondary: null,
      translations: []
    })
  }
  log(`banner ${banners.length} 张`)

  // ⑩ coupon(type: 1=折扣 2=固定金额 3=免运费)
  for (const c of coupons) {
    await post('/admin/promotions/coupons', {
      code: c.code, name: c.name, type: c.type, value: c.value, min_amount: c.minAmount,
      total_limit: c.totalLimit, start_at: '2026-01-01T00:00:00', end_at: '2027-12-31T23:59:59',
      status: ACTIVE, description: c.description, translations: []
    })
  }
  log(`coupon ${coupons.length} 张`)

  // ⑪ 公告
  for (const a of announcements) {
    await post('/admin/site-builder/announcements', {
      enabled: true, priority: 100,
      start_at: '2026-09-01T00:00:00', end_at: '2027-12-31T23:59:59',
      content_i18n_json: {
        en: { content: a.content },
        es: { content: 'Vestidos hechos a medida en 8-12 semanas · Producción expr disponible · Envío gratis en EE.UU. desde $199' },
        fr: { content: 'Robes sur mesure en 8-12 semaines · Production express disponible · Livraison gratuite aux É.-U. dès 199 $' }
      }
    })
  }
  log(`公告 ${announcements.length} 条`)

  // ⑫ 首页六区块(结构沿用前端既有约定:hero/banner 前端默认渲染,其余带 data+i18n)
  const homeSections = [
    { section_type: 'hero', sort_order: 1, enabled: true, data_json: null, i18n_json: null, label: 'Hero 主视觉' },
    { section_type: 'featured_banner', sort_order: 2, enabled: true, data_json: null, i18n_json: null, label: '活动 Banner' },
    { section_type: 'theme_cards', sort_order: 3, enabled: true, label: '主题分类卡片',
      data_json: { mode: 'auto', limit: 6 },
      i18n_json: { en: { eyebrow: 'Explore', heading: 'Shop by Theme', description: 'Find the setting that feels like your story.' }, es: { eyebrow: 'Explorar', heading: 'Comprar por Tema', description: 'Encuentra el escenario que se parece a tu historia.' }, fr: { eyebrow: 'Explorer', heading: 'Acheter par Thème', description: 'Trouvez le décor qui ressemble à votre histoire.' } } },
    { section_type: 'product_rail', sort_order: 4, enabled: true, label: '新品推荐',
      data_json: { source: 'new_arrival', limit: 4, sort: 'newest' },
      i18n_json: { en: { eyebrow: 'Just in', heading: 'New Arrivals', description: 'Fresh silhouettes for celebrations under open skies.' }, es: { eyebrow: 'Novedades', heading: 'Recién Llegados', description: 'Nuevas siluetas para celebraciones al aire libre.' }, fr: { eyebrow: 'Nouveautés', heading: 'Nouveaux Arrivages', description: 'De nouvelles silhouettes pour célébrer à ciel ouvert.' } } },
    { section_type: 'editorial_feature', sort_order: 5, enabled: true, label: '真实婚礼故事',
      data_json: { limit: 3 },
      i18n_json: { en: { eyebrow: 'Real love stories', heading: 'Real Outdoor Weddings', description: 'Celebrations, details, and dresses from real Dreamy couples.' }, es: { eyebrow: 'Historias de amor reales', heading: 'Bodas Reales al Aire Libre', description: 'Celebraciones, detalles y vestidos de parejas Dreamy reales.' }, fr: { eyebrow: 'Vraies histoires d\'amour', heading: 'Vrais Mariages en Plein Air', description: 'Célébrations, détails et robes de vrais couples Dreamy.' } } },
    { section_type: 'newsletter', sort_order: 6, enabled: true, label: '邮件订阅',
      data_json: null,
      i18n_json: { en: { eyebrow: 'Stay in touch', heading: 'Join the Dreamy List', description: 'New collections, planning inspiration, and private offers — sent thoughtfully.', placeholder: 'Your email', cta: 'Subscribe' }, es: { eyebrow: 'Sigamos en contacto', heading: 'Únete a la Lista Dreamy', description: 'Nuevas colecciones, inspiración y ofertas privadas.', placeholder: 'Tu correo', cta: 'Suscribirse' }, fr: { eyebrow: 'Restons en contact', heading: 'Rejoindre la Liste Dreamy', description: 'Nouvelles collections, inspiration et offres privées.', placeholder: 'Votre e-mail', cta: 'S\'abonner' } } }
  ]
  for (const s of homeSections) {
    await post('/admin/site-builder/home-sections', { ...s, version: 0 })
  }
  log(`首页区块 ${homeSections.length} 个`)

  // ⑬ 导航(顶级 6 项:CATEGORY 型 ref_id 关联分类,PAGE 型用系统页键)
  const navItems = [
    { label: 'Home', link_type: 2, page_key: 'home', sort_order: 1, target: 'self', enabled: true },
    { label: 'Wedding Dresses', link_type: 3, ref_id: catIdByPath['Wedding Dresses'], sort_order: 2, target: 'self', enabled: true },
    { label: 'Bridesmaids', link_type: 3, ref_id: catIdByPath['Bridesmaids'], sort_order: 3, target: 'self', enabled: true },
    { label: 'Occasion & Party', link_type: 3, ref_id: catIdByPath['Occasion & Party'], sort_order: 4, target: 'self', enabled: true },
    { label: 'Real Weddings', link_type: 2, page_key: 'real-weddings', sort_order: 5, target: 'self', enabled: true },
    { label: 'The Journal', link_type: 1, url: '/blog', sort_order: 6, target: 'self', enabled: true }
  ]
  await put('/admin/site-builder/navigation', { items: navItems, version: 0 })
  log(`导航 ${navItems.length} 项`)

  // ⑭ 页脚(4 栏)
  await put('/admin/site-builder/footer', {
    version: 0,
    columns: [
      { title: 'Shop', sort_order: 1, enabled: true, links: [
        { label: 'All Wedding Dresses', url: '/products?cat=Wedding%20Dresses', sort_order: 1, target: 'self' },
        { label: 'Bridesmaids', url: '/products?cat=Bridesmaids', sort_order: 2, target: 'self' },
        { label: 'New Arrivals', url: '/products?sort=new', sort_order: 3, target: 'self' },
        { label: 'Best Sellers', url: '/products?sort=best', sort_order: 4, target: 'self' }
      ] },
      { title: 'Inspiration', sort_order: 2, enabled: true, links: [
        { label: 'Real Weddings', url: '/real-weddings', sort_order: 1, target: 'self' },
        { label: 'Lookbooks', url: '/lookbooks', sort_order: 2, target: 'self' },
        { label: 'The Journal', url: '/blog', sort_order: 3, target: 'self' },
        { label: 'When to Order Your Gown', url: '/blog/made-to-order-timeline-guide', sort_order: 4, target: 'self' }
      ] },
      { title: 'Help', sort_order: 3, enabled: true, links: [
        { label: 'Shipping & Delivery', url: '/blog/made-to-order-timeline-guide', sort_order: 1, target: 'self' },
        { label: 'Contact Us', url: '/contact', sort_order: 2, target: 'self' },
        { label: 'Track Order', url: '/track-order', sort_order: 3, target: 'self' }
      ] },
      { title: 'Company', sort_order: 4, enabled: true, links: [
        { label: 'About Us', url: '/about', sort_order: 1, target: 'self' },
        { label: 'Our Craft', url: '/about', sort_order: 2, target: 'self' }
      ] }
    ]
  })
  log('页脚 4 栏')

  // ⑮ 运费
  for (const c of carriers) {
    await post('/admin/shipping/carriers', {
      name: c.name, zones: c.zones, lead_time: c.leadTime, status: c.status,
      code: c.code, tracking_url_template: c.trackingUrlTemplate
    })
  }
  for (const o of shippingOptions) {
    await post('/admin/shipping/options', {
      zone: o.zone, carrier_code: o.carrierCode, service_level: o.serviceLevel,
      fee_under: o.feeUnder, fee_over: o.feeOver, threshold: o.threshold,
      transit_days_min: o.transitDaysMin, transit_days_max: o.transitDaysMax, enabled: true
    })
  }
  log(`承运商 ${carriers.length} + 运费选项 ${shippingOptions.length} 条`)

  // ⑯ 评价(SQL 直插 + 商品冗余评分同步)
  const rows = buildReviews()
  const summary = ratingSummary(rows)
  const sql = buildReviewSql(rows, productIdBySlug, summary)
  log(`评价 ${rows.length} 条(SQL 经 SSH 直插)...`)
  if (LOCAL) {
    execFileSync('docker', ['exec', '-i', 'pd-mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot', '-proot', 'identity'],
      { input: sql, stdio: ['pipe', 'inherit', 'pipe'] })
  } else {
    execFileSync('ssh', ['-o', 'ConnectTimeout=15', DEPLOY_SSH,
      `PW=$(grep "^MYSQL_ROOT_PASSWORD=" /opt/dreamy/.env.deploy | cut -d= -f2); docker exec -i dreamy-mysql-1 mysql --default-character-set=utf8mb4 -uroot -p"$PW" identity`],
      { input: sql, stdio: ['pipe', 'inherit', 'inherit'] })
  }
  log(`评价插入完成(评分覆盖 ${Object.keys(summary).length} 款)`)

  // ⑰ 缓存刷新(评价/评分不走 admin API 的失效链,重启 backend 清 caffeine)
  if (LOCAL) {
    execFileSync('docker', ['exec', 'pd-redis', 'redis-cli', 'flushall'], { stdio: ['ignore', 'inherit', 'inherit'] })
    log('本机 Redis 已清空;评价评分依赖进程内缓存,需手动重启本地 backend(bootRun)')
  } else {
    execFileSync('ssh', ['-o', 'ConnectTimeout=15', DEPLOY_SSH,
      'docker exec dreamy-redis-1 redis-cli flushall >/dev/null && cd /opt/dreamy && docker compose --env-file .env.deploy restart backend 2>&1 | tail -1'],
      { stdio: ['ignore', 'inherit', 'inherit'] })
    log('Redis 已清空,backend 重启中(约 60s 就绪)')
  }

  log('=== 全部完成 ===')
  const counts = await get('/admin/products?page=1&page_size=1')
  log(`商品总数确认: ${counts.total ?? '?'}`)
}

main().catch((e) => { console.error('[seed] 失败:', e.message); process.exit(1) })
