// 站点导航 / 页脚定义（site_builder 域），seed.mjs 与 apply-site-nav.mjs 共用。
//
// 硬约束：每条商品类链接必须落到目录里真实存在且有货的筛选口径 ——
//   分类   → /products?cat=<后台分类名>（顶级或子分类均可，CollectionPage 全树解析）
//   款式   → &a_<attribute_key>=<value>（多值用 %7C 分隔，同 key OR）
//   色系   → &collection=<色板集合 id>（后端 color 参数是 SKU 单色精确匹配，不能用色组名）
// 禁止写目录里不存在的伪分类（cat=Shoes / cat=A-Line / cat=Cocktail 之类原型遗留）；
// 之前正是这类链接让「Shoes」标题下倒出配饰全集、「Special Occasion」下倒出婚纱。
// 每条链接由 tests/ui-verification/nav-catalog-audit.mjs 对生产 API 逐条复核（≥1 款且全部命中语义）。

const enc = (s) => encodeURIComponent(s).replace(/%20/g, '+')
const catUrl = (name, extra = '') => `/products?cat=${enc(name)}${extra}`

/**
 * @param {Record<string, number>} colIdByName 色板集合名 → id（seed 创建后 / apply 时从 store API 读取）
 * @returns mega_menu_json 表，key 为顶级导航 label
 */
export function buildMegaMenus(colIdByName) {
  const col = (name) => {
    const id = colIdByName[name]
    if (id == null) throw new Error(`色板集合不存在: ${name}`)
    return id
  }
  return {
    'Wedding Dresses': {
      columns: [
        { title: 'Shop by Setting', links: [
          { label: 'Beach & Destination', href: catUrl('Beach & Destination') },
          { label: 'Garden & Boho', href: catUrl('Garden & Boho') },
          { label: 'Classic Elegance', href: catUrl('Classic Elegance') }
        ] },
        { title: 'Shop by Silhouette', links: [
          { label: 'A-Line', href: catUrl('Wedding Dresses', '&a_silhouette=A-Line') },
          { label: 'Sheath', href: catUrl('Wedding Dresses', '&a_silhouette=Sheath') },
          { label: 'Mermaid', href: catUrl('Wedding Dresses', '&a_silhouette=Mermaid') },
          { label: 'Ballgown', href: catUrl('Wedding Dresses', '&a_silhouette=Ballgown') },
          { label: 'Short & Tea-Length', href: catUrl('Wedding Dresses', '&a_length=Tea-Length%7CHigh-Low') }
        ] },
        { title: 'Shop by Detail', links: [
          { label: 'Long Sleeve', href: catUrl('Wedding Dresses', '&a_sleeve=Long+Sleeve') },
          { label: 'Lace', href: catUrl('Wedding Dresses', '&a_embellishment=Lace') },
          { label: 'Beaded', href: catUrl('Wedding Dresses', '&a_embellishment=Beading') }
        ] }
      ],
      featured: { label: 'New Arrivals', href: catUrl('Wedding Dresses', '&sort=newest'), image: '/photography/plp-wedding-dresses.jpg' }
    },
    Bridesmaids: {
      columns: [
        { title: 'Shop by Style', links: [
          { label: 'Long Bridesmaid Dresses', href: catUrl('Long Bridesmaid Dresses') },
          { label: 'Short & Convertible', href: catUrl('Short & Convertible') },
          { label: 'One-Shoulder', href: catUrl('Bridesmaids', '&a_neckline=One-Shoulder') },
          { label: 'Cowl Neck', href: catUrl('Bridesmaids', '&a_neckline=Cowl') }
        ] },
        { title: 'Shop by Color', links: [
          { label: 'Blush & Dusty Rose', href: catUrl('Bridesmaids', `&collection=${col('Blush & Dusty Rose')}`) },
          { label: 'Sage & Olive', href: catUrl('Bridesmaids', `&collection=${col('Sage & Olive')}`) },
          { label: 'Blue Hues', href: catUrl('Bridesmaids', `&collection=${col('Blue Hues')}`) },
          { label: 'Black & Espresso', href: catUrl('Bridesmaids', `&collection=${col('Black & Espresso')}`) }
        ] }
      ],
      featured: { label: 'Bridesmaid Edit', href: catUrl('Bridesmaids'), image: '/photography/featured-bridesmaids.jpg' }
    },
    'Occasion & Party': {
      columns: [
        { title: 'Shop by Occasion', links: [
          { label: 'Prom & Evening', href: catUrl('Prom & Evening') },
          { label: 'Ballgowns', href: catUrl('Occasion & Party', '&a_silhouette=Ballgown') },
          { label: 'One-Shoulder', href: catUrl('Occasion & Party', '&a_neckline=One-Shoulder') }
        ] },
        { title: 'Shop by Fabric', links: [
          { label: 'Lace', href: catUrl('Occasion & Party', '&a_fabric=Lace') },
          { label: 'Tulle', href: catUrl('Occasion & Party', '&a_fabric=Tulle') }
        ] }
      ],
      featured: { label: 'Party Season', href: catUrl('Occasion & Party', '&sort=newest'), image: '/photography/plp-occasion.jpg' }
    },
    Accessories: {
      columns: [
        { title: 'Complete the Look', links: [
          { label: 'Jewelry & Headpieces', href: catUrl('Jewelry & Headpieces') },
          { label: 'Getting Ready', href: catUrl('Getting Ready') },
          { label: 'Flower Girl', href: catUrl('Flower Girl') }
        ] }
      ],
      featured: { label: 'Complete the Look', href: catUrl('Accessories'), image: '/photography/plp-accessories.jpg' }
    }
  }
}

/**
 * 顶级导航（PUT /admin/site-builder/navigation 整体替换）。
 * link_type：1 CUSTOM / 2 PAGE / 3 CATEGORY —— 分类项走 ref_id，后端解析为 /products?cat=<name>。
 * @param {Record<string, number>} catIdByPath 顶级分类名 → id
 * @param {Record<string, number>} colIdByName 色板集合名 → id
 */
export function buildNavItems(catIdByPath, colIdByName) {
  const mega = buildMegaMenus(colIdByName)
  const cat = (name) => {
    const id = catIdByPath[name]
    if (id == null) throw new Error(`顶级分类不存在: ${name}`)
    return id
  }
  const NAV_I18N = {
    'Wedding Dresses': ['Vestidos de Novia', 'Robes de Mariée'],
    Bridesmaids: ['Damas de Honor', "Demoiselles d'Honneur"],
    'Occasion & Party': ['Ocasión y Fiesta', 'Occasion & Fête'],
    Accessories: ['Accesorios', 'Accessoires'],
    'Real Weddings': ['Bodas Reales', 'Vrais Mariages'],
    'The Journal': ['El Diario', 'Le Journal'],
    Home: ['Inicio', 'Accueil']
  }
  const i18n = (label) => NAV_I18N[label] ? { es: { label: NAV_I18N[label][0] }, fr: { label: NAV_I18N[label][1] } } : undefined
  const item = (label, extra, sort) => ({
    label, sort_order: sort, target: 'self', enabled: true, i18n_json: i18n(label),
    mega_menu_json: mega[label] ?? null, ...extra
  })
  return [
    item('Home', { link_type: 2, page_key: 'home' }, 1),
    item('Wedding Dresses', { link_type: 3, ref_id: cat('Wedding Dresses') }, 2),
    item('Bridesmaids', { link_type: 3, ref_id: cat('Bridesmaids') }, 3),
    item('Occasion & Party', { link_type: 3, ref_id: cat('Occasion & Party') }, 4),
    item('Accessories', { link_type: 3, ref_id: cat('Accessories') }, 5),
    item('Real Weddings', { link_type: 2, page_key: 'real-weddings' }, 6),
    item('The Journal', { link_type: 1, url: '/blog' }, 7)
  ]
}

/** 页脚（PUT /admin/site-builder/footer 整体替换）。所有 url 必须是站内真实路由（/lookbooks 曾是 404）。 */
export function buildFooterColumns() {
  return [
    { title: 'Shop', sort_order: 1, enabled: true, links: [
      { label: 'All Wedding Dresses', url: catUrl('Wedding Dresses'), sort_order: 1, target: 'self' },
      { label: 'Bridesmaids', url: catUrl('Bridesmaids'), sort_order: 2, target: 'self' },
      { label: 'Occasion & Party', url: catUrl('Occasion & Party'), sort_order: 3, target: 'self' },
      { label: 'Accessories', url: catUrl('Accessories'), sort_order: 4, target: 'self' },
      { label: 'Outdoor Weddings', url: '/outdoor-weddings', sort_order: 5, target: 'self' },
      { label: 'New Arrivals', url: '/products?sort=new', sort_order: 6, target: 'self' },
      { label: 'Best Sellers', url: '/products?sort=best', sort_order: 7, target: 'self' }
    ] },
    { title: 'Inspiration', sort_order: 2, enabled: true, links: [
      { label: 'Real Weddings', url: '/real-weddings', sort_order: 1, target: 'self' },
      { label: 'Lookbooks', url: '/inspiration', sort_order: 2, target: 'self' },
      { label: 'The Journal', url: '/blog', sort_order: 3, target: 'self' },
      { label: 'Planning Guides', url: '/wedding-guides', sort_order: 4, target: 'self' },
      { label: 'When to Order Your Gown', url: '/blog/made-to-order-timeline-guide', sort_order: 5, target: 'self' }
    ] },
    { title: 'Help', sort_order: 3, enabled: true, links: [
      { label: 'Shipping & Delivery', url: '/faq#shipping', sort_order: 1, target: 'self' },
      { label: 'Size Guide', url: '/faq#size', sort_order: 2, target: 'self' },
      { label: 'FAQ', url: '/faq', sort_order: 3, target: 'self' },
      { label: 'Contact Us', url: '/contact', sort_order: 4, target: 'self' },
      { label: 'Track Order', url: '/track-order', sort_order: 5, target: 'self' }
    ] },
    { title: 'Company', sort_order: 4, enabled: true, links: [
      { label: 'About Us', url: '/about', sort_order: 1, target: 'self' },
      { label: 'Our Craft', url: '/about', sort_order: 2, target: 'self' }
    ] }
  ]
}
