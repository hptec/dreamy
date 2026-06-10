// 后台原型 Mock 数据层 —— 业务语义贴近 Dreamy 婚纱电商
// 商品/订单/用户字段与前台 data/*.ts 对齐（slug/category/price/colors/sizes 等）

const REF = '/competitor-refs'

// 户外婚礼调色板（与前台 Shop by Color 同源）
export const palette = [
  { name: 'Sage', hex: '#8B9D83', count: 28 },
  { name: 'Dusty Blue', hex: '#9FB2C4', count: 24 },
  { name: 'Blush', hex: '#E5C1BC', count: 31 },
  { name: 'Champagne', hex: '#E8D9BE', count: 22 },
  { name: 'Lavender', hex: '#C3B6D6', count: 18 },
  { name: 'Terracotta', hex: '#C08763', count: 15 },
  { name: 'Ivory', hex: '#F4EEE2', count: 40 },
  { name: 'Espresso', hex: '#5A4636', count: 12 }
]

export const kpis = [
  { key: 'gmv', label: '今日 GMV', value: '$48,260', delta: '+12.4%', up: true },
  { key: 'orders', label: '今日订单', value: '186', delta: '+8.1%', up: true },
  { key: 'users', label: '今日新增用户', value: '312', delta: '+5.6%', up: true },
  { key: 'pending', label: '待发货', value: '47', delta: '-3.2%', up: false }
]

export const todos = [
  { label: '待付款订单', count: 23, tone: 'warn', to: '/orders?status=pending' },
  { label: '待发货订单', count: 47, tone: 'danger', to: '/orders?status=paid' },
  { label: '待确认收货', count: 18, tone: 'info', to: '/orders?status=shipped' },
  { label: '退款待审批', count: 6, tone: 'danger', to: '/refunds' },
  { label: '低库存预警', count: 12, tone: 'warn', to: '/products?stock=low' },
  { label: '待审核评价', count: 9, tone: 'info', to: '/customers' },
  { label: '内容草稿待发布', count: 4, tone: 'warn', to: '/content/blog' },
  { label: 'Banner 即将到期', count: 2, tone: 'danger', to: '/site/banners' }
]

export const overviewStats = {
  product: [
    { label: '已上架', value: 86 },
    { label: '草稿', value: 14 },
    { label: '库存紧张', value: 12 },
    { label: '全部商品', value: 112 }
  ],
  user: [
    { label: '今日新增', value: 312 },
    { label: '昨日新增', value: 287 },
    { label: '本月新增', value: 6840 },
    { label: '会员总数', value: 48260 }
  ]
}

// GMV 趋势（近 14 天，单位 $K），用于 SVG 折线/面积图
export const gmvTrend = [22, 28, 25, 34, 40, 38, 45, 42, 51, 48, 56, 53, 61, 58]
export const gmvLabels = ['5-16','5-17','5-18','5-19','5-20','5-21','5-22','5-23','5-24','5-25','5-26','5-27','5-28','5-29']

export const categorySales = [
  { name: 'Wedding Dresses', value: 52, color: '#C19A6B' },
  { name: 'Special Occasion', value: 31, color: '#8B9D83' },
  { name: 'Accessories', value: 17, color: '#D8A7A0' }
]

export const funnel = [
  { stage: '商品浏览', value: 100000, color: '#D4B896' },
  { stage: '加入购物车', value: 28400, color: '#C19A6B' },
  { stage: '进入结算', value: 12600, color: '#A8B6A1' },
  { stage: '完成支付', value: 8200, color: '#8B9D83' }
]

export const trafficSources = [
  { name: '自然搜索', value: 38 },
  { name: 'Instagram', value: 24 },
  { name: 'Pinterest', value: 16 },
  { name: '直接访问', value: 14 },
  { name: '邮件', value: 8 }
]

const colorset = (names) => names.map((n) => ({
  name: n.name, hex: n.hex
}))

export const products = [
  { id: 'p-aurelia', slug: 'aurelia-aline-tulle', name: 'Aurelia A-Line Tulle Gown', img: `${REF}/kissprom/wedding-aline-tulle-01.jpg`, categoryId: 'cat-wd-aline', productType: 'Bridal Gown', tags: ['tag-garden', 'tag-outdoor-edit-2026'], price: 1280, compareAt: 1480, stock: 24, sales: 132, status: 'published', isNew: true, isBest: true, recommend: true, sort: 100 },
  { id: 'p-celeste', slug: 'celeste-lace-gown', name: 'Celeste Lace Gown', img: `${REF}/kissprom/wedding-aline-lace-02.jpg`, categoryId: 'cat-wd-sheath', productType: 'Bridal Gown', tags: ['tag-vineyard'], price: 1560, compareAt: null, stock: 8, sales: 98, status: 'published', isNew: false, isBest: true, recommend: true, sort: 95 },
  { id: 'p-willow', slug: 'willow-longsleeve', name: 'Willow Long-Sleeve Gown', img: `${REF}/kissprom/wedding-aline-longsleeve-06.jpg`, categoryId: 'cat-wd-aline', productType: 'Bridal Gown', tags: ['tag-forest'], price: 1420, compareAt: 1620, stock: 16, sales: 76, status: 'published', isNew: true, isBest: false, recommend: false, sort: 80 },
  { id: 'p-marina', slug: 'marina-mermaid-chiffon', name: 'Marina Mermaid Chiffon', img: `${REF}/kissprom/wedding-mermaid-chiffon-03.jpg`, categoryId: 'cat-wd-mermaid', productType: 'Bridal Gown', tags: ['tag-beach'], price: 1680, compareAt: null, stock: 3, sales: 54, status: 'published', isNew: false, isBest: false, recommend: false, sort: 70 },
  { id: 'p-meadow', slug: 'meadow-bridesmaid', name: 'Meadow Bridesmaid Dress', img: `${REF}/birdygrey/bridesmaid-pink-bella-01.jpg`, categoryId: 'cat-so-bridesmaid', productType: 'Party Dress', tags: ['tag-garden', 'tag-outdoor-edit-2026'], price: 178, compareAt: 210, stock: 120, sales: 410, status: 'published', isNew: false, isBest: true, recommend: true, sort: 90 },
  { id: 'p-petal', slug: 'petal-bridesmaid', name: 'Petal Bridesmaid Dress', img: `${REF}/birdygrey/bridesmaid-pink-bryten-02.jpg`, categoryId: 'cat-so-bridesmaid', productType: 'Party Dress', tags: ['tag-boho'], price: 168, compareAt: null, stock: 86, sales: 280, status: 'published', isNew: true, isBest: false, recommend: false, sort: 75 },
  { id: 'p-luna', slug: 'luna-prom-champagne', name: 'Luna Prom Champagne', img: `${REF}/kissprom/prom-champagne-lace-05.jpg`, categoryId: 'cat-so-prom', productType: 'Prom Dress', tags: ['tag-golden-hour'], price: 248, compareAt: 298, stock: 42, sales: 156, status: 'published', isNew: false, isBest: false, recommend: true, sort: 60 },
  { id: 'p-beach', slug: 'beach-short-dress', name: 'Coraline Beach Short Dress', img: `${REF}/kissprom/wedding-beach-short-05.jpg`, categoryId: 'cat-wd-short', productType: 'Beach Wedding Dress', tags: ['tag-beach'], price: 680, compareAt: null, stock: 0, sales: 64, status: 'draft', isNew: false, isBest: false, recommend: false, sort: 40 },
  { id: 'a-cathedral-veil', slug: 'cathedral-veil', name: 'Cathedral Length Veil', img: `${REF}/birdygrey/accessory-jewelry-01.jpg`, categoryId: 'cat-acc-veils', productType: 'Bridal Veil', tags: [], price: 128, compareAt: null, stock: 200, sales: 320, status: 'published', isNew: false, isBest: true, recommend: false, sort: 85 },
  { id: 'a-drop-earrings', slug: 'crystal-drop-earrings', name: 'Crystal Drop Earrings', img: `${REF}/birdygrey/accessory-jewelry-01.jpg`, categoryId: 'cat-acc-jewelry', productType: 'Bridal Jewelry', tags: ['tag-golden-hour'], price: 64, compareAt: 84, stock: 340, sales: 480, status: 'published', isNew: true, isBest: false, recommend: true, sort: 65 },
  { id: 'a-hair-vine', slug: 'pearl-hair-vine', name: 'Pearl Hair Vine', img: `${REF}/birdygrey/accessory-jewelry-01.jpg`, categoryId: 'cat-acc-headpieces', productType: 'Hair Accessory', tags: ['tag-garden'], price: 88, compareAt: null, stock: 6, sales: 142, status: 'published', isNew: false, isBest: false, recommend: false, sort: 50 },
  { id: 'a-block-heels', slug: 'satin-block-heels', name: 'Satin Block Heels', img: `${REF}/birdygrey/accessory-jewelry-01.jpg`, categoryId: 'cat-acc-shoes', productType: 'Wedding Shoes', tags: [], price: 148, compareAt: null, stock: 58, sales: 96, status: 'draft', isNew: false, isBest: false, recommend: false, sort: 30 }
]

export const productColors = colorset([
  { name: 'Ivory', hex: '#F4EFE6' }, { name: 'Champagne', hex: '#E8D5B0' },
  { name: 'White', hex: '#FAFAF8' }, { name: 'Blush', hex: '#D8A7A0' },
  { name: 'Dusty Rose', hex: '#C49A9A' }, { name: 'Sage', hex: '#8B9D83' },
  { name: 'Dusty Blue', hex: '#9DB0C4' }, { name: 'Steel Blue', hex: '#6C8EAD' },
  { name: 'Lavender', hex: '#C3B6D6' }, { name: 'Lilac', hex: '#D6BBDB' },
  { name: 'Terracotta', hex: '#C17A56' }, { name: 'Rust', hex: '#A85D3A' },
  { name: 'Burgundy', hex: '#7B2D42' }, { name: 'Navy', hex: '#2B3A5C' },
  { name: 'Forest Green', hex: '#3D5A42' }, { name: 'Espresso', hex: '#5A4636' }
])

export const productSizes = ['US 0','US 2','US 4','US 6','US 8','US 10','US 12','US 14','US 16','US 18','US 20']

// ===== 商品属性字典（AttributeSets 页面消费，含 key/label/type/optionsKey） =====
// type: 'select' 单选 | 'multiselect' 多选 | 'toggle' 开关 | 'text' 文本
export const attributeDict = [
  { key: 'silhouette',         label: '廓形 / Silhouette',       type: 'select',      optionsKey: 'silhouetteOptions' },
  { key: 'neckline',           label: '领口 / Neckline',         type: 'select',      optionsKey: 'necklineOptions' },
  { key: 'sleeve',             label: '袖型 / Sleeve',           type: 'select',      optionsKey: 'sleeveOptions' },
  { key: 'backStyle',          label: '背部设计 / Back Style',   type: 'select',      optionsKey: 'backStyleOptions' },
  { key: 'waistline',          label: '腰线 / Waistline',        type: 'select',      optionsKey: 'waistlineOptions' },
  { key: 'train',              label: '拖尾 / Train',            type: 'select',      optionsKey: 'trainOptions' },
  { key: 'length',             label: '裙长 / Length',           type: 'select',      optionsKey: 'lengthOptions' },
  { key: 'fabric',             label: '面料 / Fabric',           type: 'select',      optionsKey: 'fabricOptions' },
  { key: 'embellishment',      label: '装饰细节 / Embellishment', type: 'multiselect', optionsKey: 'embellishmentOptions' },
  { key: 'support',            label: '内置支撑 / Support',      type: 'multiselect', optionsKey: 'supportOptions' },
  { key: 'occasion',           label: '场合 / Occasion',         type: 'multiselect', optionsKey: 'occasionOptions' },
  { key: 'styleTag',           label: '风格标签 / Style',        type: 'multiselect', optionsKey: 'styleTagOptions' },
  { key: 'season',             label: '季节 / Season',           type: 'multiselect', optionsKey: 'seasonOptions' },
  { key: 'customSize',         label: '定制尺寸',               type: 'toggle',      optionsKey: null },
  { key: 'leadTime',           label: '生产周期',               type: 'text',        optionsKey: null },
  { key: 'modelInfo',          label: '模特信息',               type: 'text',        optionsKey: null },
  { key: 'careInstructions',   label: '护理说明',               type: 'text',        optionsKey: null },
  { key: 'fabricComposition',  label: '面料成分',               type: 'text',        optionsKey: null }
]

// ===== 商品属性选项（竞品对齐：Azazie / BHLDN / Kleinfeld）=====

export const silhouetteOptions = ['A-Line', 'Ball Gown', 'Mermaid', 'Trumpet', 'Sheath / Column', 'Empire', 'Tea-Length', 'Mini / Short']

export const necklineOptions = ['V-Neck', 'Sweetheart', 'Square', 'Scoop', 'Off-Shoulder', 'One-Shoulder', 'Halter', 'High Neck', 'Cowl', 'Strapless', 'Bateau / Boat Neck', 'Portrait']

export const sleeveOptions = ['Sleeveless', 'Strapless', 'Cap Sleeve', 'Short Sleeve', '3/4 Sleeve', 'Long Sleeve', 'Off-Shoulder Flutter', 'Cold Shoulder']

export const backStyleOptions = ['Zipper', 'Lace-Up / Corset', 'Button', 'Open Back', 'Keyhole', 'Bow', 'Low V-Back', 'Illusion Back']

export const waistlineOptions = ['Natural', 'Empire', 'Basque / Drop', 'High Waist', 'Dropped Waist']

export const trainOptions = ['No Train', 'Sweep Train', 'Court Train', 'Chapel Train', 'Cathedral Train', 'Royal / Monarch Train', 'Detachable Train']

export const embellishmentOptions = ['Beading', 'Sequins', 'Lace Appliqué', '3D Florals', 'Embroidery', 'Crystal / Rhinestone', 'Bow', 'Ruching', 'Pleating', 'Plain / Minimal']

export const fabricOptions = [
  { name: 'Tulle', category: 'Light & Flowy' },
  { name: 'Chiffon', category: 'Light & Flowy' },
  { name: 'Organza', category: 'Light & Flowy' },
  { name: 'Lace', category: 'Texture' },
  { name: 'Satin', category: 'Structured' },
  { name: 'Mikado', category: 'Structured' },
  { name: 'Crepe', category: 'Structured' },
  { name: 'Taffeta', category: 'Structured' },
  { name: 'Charmeuse', category: 'Luxe' },
  { name: 'Silk', category: 'Luxe' },
  { name: 'Velvet', category: 'Statement' },
  { name: 'Sequin Fabric', category: 'Statement' },
]

export const supportOptions = ['Built-in Bra / Cups', 'Boning / Underwire', 'Corset Back', 'Fully Lined', 'Light Lining Only', 'No Built-in Support']

export const occasionOptions = ['Bridal / Wedding', 'Bridesmaid', 'Mother of the Bride', 'Wedding Guest', 'Flower Girl', 'Prom', 'Evening / Formal', 'Cocktail']

export const styleTagOptions = ['Romantic', 'Classic / Timeless', 'Bohemian / Boho', 'Modern Minimalist', 'Glamorous', 'Rustic', 'Garden / Floral', 'Beach / Coastal']

export const seasonOptions = ['Spring', 'Summer', 'Fall / Autumn', 'Winter', 'All Seasons']

export const lengthOptions = ['Mini (Above Knee)', 'Knee Length', 'Midi (Below Knee)', 'Maxi / Tea Length', 'Floor Length', 'Puddle / Sweep']

// ===== 属性集（Attribute Set）— Shopify 模式：基础属性集绑大品类，子品类继承并覆盖 =====
// visible: 显示 | optional: 显示但折叠 | hidden: 隐藏
// 大品类持有「基础属性集」，子品类通过 attrOverrides 仅声明与父级不同的字段（delta），
// 最终生效配置 = 父级基础属性集 ⊕ 子品类覆盖（见 resolveAttributeConfig）。
export const attributeSets = {
  'as-wedding-dresses': {
    id: 'as-wedding-dresses',
    label: '婚纱',
    categoryId: 'cat-wedding-dresses',
    attrs: {
      silhouette: 'visible', neckline: 'visible', sleeve: 'visible', backStyle: 'visible',
      waistline: 'visible', train: 'visible', embellishment: 'visible', fabric: 'visible',
      support: 'visible', occasion: 'optional', styleTag: 'visible', season: 'optional',
      length: 'optional', customSize: 'optional', leadTime: 'visible', modelInfo: 'visible',
      careInstructions: 'visible', fabricComposition: 'visible'
    }
  },
  'as-special-occasion': {
    id: 'as-special-occasion',
    label: '礼服（伴娘/晚礼服）',
    categoryId: 'cat-special-occasion',
    attrs: {
      silhouette: 'visible', neckline: 'visible', sleeve: 'visible', backStyle: 'optional',
      waistline: 'optional', train: 'hidden', embellishment: 'visible', fabric: 'visible',
      support: 'optional', occasion: 'visible', styleTag: 'optional', season: 'optional',
      length: 'visible', customSize: 'visible', leadTime: 'visible', modelInfo: 'visible',
      careInstructions: 'visible', fabricComposition: 'visible'
    }
  },
  'as-accessories': {
    id: 'as-accessories',
    label: '配饰',
    categoryId: 'cat-accessories',
    attrs: {
      silhouette: 'hidden', neckline: 'hidden', sleeve: 'hidden', backStyle: 'hidden',
      waistline: 'hidden', train: 'hidden', embellishment: 'visible', fabric: 'visible',
      support: 'hidden', occasion: 'optional', styleTag: 'optional', season: 'hidden',
      length: 'hidden', customSize: 'hidden', leadTime: 'visible', modelInfo: 'hidden',
      careInstructions: 'visible', fabricComposition: 'visible'
    }
  }
}

const ORDER_STATUS = {
  pending: { label: '待付款', tone: 'warn' },
  paid: { label: '待发货', tone: 'danger' },
  shipped: { label: '已发货', tone: 'info' },
  completed: { label: '已完成', tone: 'ok' },
  refunding: { label: '退款中', tone: 'warn' }
}
export { ORDER_STATUS }

export const orders = [
  { id: 'DRM-20260529-1042', customer: 'Emma Johnson', email: 'emma.j@example.com', country: 'US', items: 2, total: 1408, currency: 'USD', status: 'paid', payment: 'Stripe', date: '2026-05-29 14:02' },
  { id: 'DRM-20260529-1041', customer: 'Sofia Marco', email: 'sofia.m@example.com', country: 'US', items: 5, total: 1064, currency: 'USD', status: 'pending', payment: 'PayPal', date: '2026-05-29 12:48' },
  { id: 'DRM-20260529-1038', customer: 'Ava Chen', email: 'ava.chen@example.com', country: 'CA', items: 1, total: 1560, currency: 'CAD', status: 'shipped', payment: 'Apple Pay', date: '2026-05-29 10:15' },
  { id: 'DRM-20260528-0996', customer: 'Olivia Brown', email: 'olivia.b@example.com', country: 'AU', items: 3, total: 624, currency: 'AUD', status: 'completed', payment: 'Klarna', date: '2026-05-28 19:33' },
  { id: 'DRM-20260528-0981', customer: 'Mia Wilson', email: 'mia.w@example.com', country: 'GB', items: 2, total: 296, currency: 'GBP', status: 'refunding', payment: 'Stripe', date: '2026-05-28 16:20' },
  { id: 'DRM-20260528-0975', customer: 'Charlotte Lee', email: 'char.lee@example.com', country: 'US', items: 4, total: 712, currency: 'USD', status: 'completed', payment: 'Afterpay', date: '2026-05-28 11:07' },
  { id: 'DRM-20260527-0944', customer: 'Amelia Davis', email: 'amelia.d@example.com', country: 'US', items: 1, total: 1280, currency: 'USD', status: 'completed', payment: 'Stripe', date: '2026-05-27 20:51' },
  { id: 'DRM-20260527-0921', customer: 'Isabella Garcia', email: 'bella.g@example.com', country: 'CA', items: 6, total: 1188, currency: 'CAD', status: 'shipped', payment: 'Google Pay', date: '2026-05-27 15:42' }
]

export const orderDetail = {
  id: 'DRM-20260529-1042',
  status: 'paid',
  customer: { name: 'Emma Johnson', email: 'emma.j@example.com', phone: '+1 415 555 0142' },
  address: { line: '1280 Ocean View Blvd', city: 'Big Sur', state: 'CA', zip: '93920', country: 'United States' },
  payment: { method: 'Stripe · Visa ···4242', paid: 1408, currency: 'USD', time: '2026-05-29 14:03' },
  shipping: { method: 'FedEx International Priority', fee: 0, tracking: '—' },
  lines: [
    { name: 'Aurelia A-Line Tulle Gown', sku: 'AURELIA-IVORY-US6', color: 'Ivory', size: 'US 6', qty: 1, price: 1280, img: `${REF}/kissprom/wedding-aline-tulle-01.jpg` },
    { name: 'Cathedral Length Veil', sku: 'VEIL-CATH-IVORY', color: 'Ivory', size: 'One Size', qty: 1, price: 128, img: `${REF}/birdygrey/accessory-jewelry-01.jpg` }
  ],
  timeline: [
    { label: '订单创建', time: '2026-05-29 14:02', done: true },
    { label: '支付成功', time: '2026-05-29 14:03', done: true },
    { label: '已发货', time: '—', done: false },
    { label: '已完成', time: '—', done: false }
  ]
}

export const customers = [
  { id: 'u-1001', name: 'Emma Johnson', email: 'emma.j@example.com', avatar: 'EJ', joined: '2025-11-12', orders: 4, spent: 4820, tier: 'VIP', status: 'active' },
  { id: 'u-1002', name: 'Sofia Marco', email: 'sofia.m@example.com', avatar: 'SM', joined: '2026-01-08', orders: 2, spent: 1640, tier: '常规', status: 'active' },
  { id: 'u-1003', name: 'Ava Chen', email: 'ava.chen@example.com', avatar: 'AC', joined: '2026-02-21', orders: 1, spent: 1560, tier: '常规', status: 'active' },
  { id: 'u-1004', name: 'Olivia Brown', email: 'olivia.b@example.com', avatar: 'OB', joined: '2025-09-30', orders: 7, spent: 6240, tier: 'VIP', status: 'active' },
  { id: 'u-1005', name: 'Mia Wilson', email: 'mia.w@example.com', avatar: 'MW', joined: '2026-03-15', orders: 1, spent: 296, tier: '常规', status: 'disabled' },
  { id: 'u-1006', name: 'Charlotte Lee', email: 'char.lee@example.com', avatar: 'CL', joined: '2025-12-01', orders: 3, spent: 2130, tier: '常规', status: 'active' }
]

export const customerDetail = {
  id: 'u-1001', name: 'Emma Johnson', email: 'emma.j@example.com', avatar: 'EJ',
  phone: '+1 415 555 0142', joined: '2025-11-12', tier: 'VIP', status: 'active',
  stats: { orders: 4, spent: 4820, wishlist: 8, reviews: 3 },
  addresses: [
    { label: '默认地址', line: '1280 Ocean View Blvd, Big Sur, CA 93920', country: 'United States' }
  ],
  recentOrders: orders.slice(0, 3),
  wishlist: products.slice(0, 4),
  activity: [
    { label: '完成订单 DRM-20260527-0944', time: '2026-05-27 20:51' },
    { label: '收藏 Celeste Lace Gown', time: '2026-05-25 09:12' },
    { label: '提交评价 Aurelia A-Line', time: '2026-05-20 14:40' },
    { label: '注册账户', time: '2025-11-12 08:30' }
  ],
  // 登录方式（一个人 = 多个登录凭证）。provider_uid 为稳定标识；apple 支持 Hide My Email（relay 邮箱）
  loginMethods: [
    { provider: 'email', label: '邮箱', identifier: 'emma.j@example.com', uid: 'emma.j@example.com', connected: true, primary: true, verified: true, boundAt: '2025-11-12', lastLogin: '2026-05-29 14:02' },
    { provider: 'google', label: 'Google', identifier: 'emma.johnson@gmail.com', uid: 'google-oauth2|108…4471', connected: true, verified: true, boundAt: '2026-01-04', lastLogin: '2026-05-30 09:18' },
    { provider: 'apple', label: 'Apple', identifier: '隐藏邮箱（私密代理）', uid: '001932.f1c…a7e.0921', connected: true, verified: true, hiddenEmail: true, relay: 'emma_j8x@privaterelay.appleid.com', boundAt: '2026-03-22', lastLogin: '2026-05-25 21:40' }
  ],
  loginHistory: [
    { time: '2026-05-30 09:18', method: 'google', ip: '203.0.113.42', device: 'Chrome 125 / macOS', location: 'Santa Barbara, US', result: 'success' },
    { time: '2026-05-29 14:02', method: 'email', ip: '203.0.113.42', device: 'Safari / iOS 18', location: 'Santa Barbara, US', result: 'success' },
    { time: '2026-05-25 21:40', method: 'apple', ip: '198.51.100.7', device: 'Safari / iOS 18', location: 'Sonoma, US', result: 'success' },
    { time: '2026-05-22 10:05', method: 'email', ip: '45.77.12.9', device: 'Chrome 124 / Windows', location: 'Toronto, CA', result: 'failed' },
    { time: '2026-05-20 14:38', method: 'email', ip: '203.0.113.42', device: 'Safari / iOS 18', location: 'Santa Barbara, US', result: 'success' }
  ],
  activeSessions: [
    { id: 'cs-1', device: 'MacBook Pro · Chrome 125', method: 'google', ip: '203.0.113.42', location: 'Santa Barbara, US', lastActive: '当前在线' },
    { id: 'cs-2', device: 'iPhone 15 · Safari', method: 'email', ip: '203.0.113.42', location: 'Santa Barbara, US', lastActive: '2 小时前' }
  ]
}

// 登录方式展示元数据（图标色 + 名称），供客户详情/合并工具复用
export const authProviderMeta = {
  email: { label: '邮箱', tone: 'ink' },
  google: { label: 'Google', tone: 'info' },
  apple: { label: 'Apple', tone: 'ink' }
}

// 登录与认证配置（AuthSettings 消费）
export const authConfig = {
  methods: [
    { provider: 'email', label: '邮箱验证码（Passwordless）', enabled: true, locked: true, desc: '主登录方式，向用户邮箱发送一次性验证码，无需密码。' },
    { provider: 'google', label: 'Google 登录', enabled: true, locked: false, desc: 'OAuth 2.0 / OpenID Connect，按 Google sub 标识用户。' },
    { provider: 'apple', label: 'Apple 登录', enabled: true, locked: false, desc: '支持 Hide My Email，按 Apple sub 标识；首次授权才返回邮箱/姓名。' }
  ],
  otp: { length: 6, ttlMinutes: 10, resendSeconds: 30, maxAttempts: 5 },
  linking: {
    // 账户合并固定为系统自动行为（已验证邮箱），不提供后台开关
    minMethods: 1                    // 用户解绑时至少保留一种登录方式
  },
  oauth: {
    google: { clientId: '••••••••••.apps.googleusercontent.com', configured: true },
    apple: { serviceId: 'com.dreamy.web.auth', configured: true }
  }
}

// ===== 站点装修：首页区块（HomeBuilder 消费） =====
export const homeBlocks = [
  { id: 'hero', type: 'Hero', label: 'Hero 主视觉', enabled: true, data: { eyebrow: 'The Outdoor Wedding Edit · 2026', title: 'Dresses made\nfor golden hour', subtitle: 'Effortless gowns, bridesmaid dresses, and accessories designed for beaches, gardens, and everywhere your love story takes you.', cta1: 'Shop the Collection', cta2: 'Explore Outdoor', image: `${REF}/kissprom/wedding-aline-tulle-01.jpg` } },
  { id: 'announce', type: 'Announcement', label: '公告滚动条', enabled: true, data: { lines: ['Complimentary worldwide shipping on orders over $200', 'Pay in 4 interest-free installments with Klarna & Afterpay', 'Order fabric swatches — try your colors before you commit'] } },
  { id: 'palette', type: 'ShopByColor', label: 'Shop by Color 色板', enabled: true, data: { title: 'Shop by Color', count: 8 } },
  { id: 'themes', type: 'ThemeCards', label: 'Outdoor 主题卡片', enabled: true, data: { cards: ['Beach','Garden','Vineyard','Forest'] } },
  { id: 'newarr', type: 'ProductRail', label: '新品推荐位', enabled: true, data: { title: 'New Arrivals', source: 'isNew', limit: 4 } },
  { id: 'realwed', type: 'EditorialFeature', label: 'Real Weddings 故事位', enabled: true, data: { title: 'Real Outdoor Weddings' } },
  { id: 'newsletter', type: 'Newsletter', label: 'Footer 订阅区', enabled: false, data: { title: 'Join the Dreamy circle' } }
]

// ===== Standard Product Taxonomy (Shopify 模式三层分类) =====
// Layer 1: 标准品类树 — 系统预定义，每个节点绑定属性集，商品单选一个叶节点
// Layer 2: 品类属性 — 随品类自动出现的结构化属性字段及其预定义值
// Layer 3: 自定义标签 — 商户自建的营销/组织标签，不驱动系统行为

export const standardTaxonomy = [
  {
    id: 'cat-wedding-dresses',
    name: 'Wedding Dresses',
    slug: 'wedding-dresses',
    level: 0,
    children: [
      // attrOverrides：仅声明与父级基础属性集不同的字段（delta）。空对象 = 完全继承父级。
      { id: 'cat-wd-aline', name: 'A-Line', slug: 'a-line', attrOverrides: {} },
      { id: 'cat-wd-mermaid', name: 'Mermaid', slug: 'mermaid', attrOverrides: { train: 'visible', waistline: 'visible' } },
      { id: 'cat-wd-ballgown', name: 'Ball Gown', slug: 'ball-gown', attrOverrides: { train: 'visible', support: 'visible' } },
      { id: 'cat-wd-sheath', name: 'Sheath', slug: 'sheath', attrOverrides: { train: 'optional' } },
      { id: 'cat-wd-short', name: 'Short', slug: 'short', attrOverrides: { train: 'hidden', length: 'visible' } },
      { id: 'cat-wd-trumpet', name: 'Trumpet', slug: 'trumpet', attrOverrides: { train: 'visible' } }
    ],
    attributeSetId: 'as-wedding-dresses',
    count: 48,
    enabled: true
  },
  {
    id: 'cat-special-occasion',
    name: 'Special Occasion',
    slug: 'special-occasion',
    level: 0,
    children: [
      { id: 'cat-so-bridesmaid', name: 'Bridesmaid', slug: 'bridesmaid', attrOverrides: { customSize: 'visible', support: 'visible' } },
      { id: 'cat-so-mob', name: 'Mother of the Bride', slug: 'mother-of-the-bride', attrOverrides: { occasion: 'visible' } },
      { id: 'cat-so-prom', name: 'Prom', slug: 'prom', attrOverrides: { train: 'optional', length: 'visible' } },
      { id: 'cat-so-cocktail', name: 'Cocktail', slug: 'cocktail', attrOverrides: { train: 'hidden', length: 'visible' } },
      { id: 'cat-so-guest', name: 'Wedding Guest', slug: 'wedding-guest', attrOverrides: { train: 'hidden', length: 'visible' } }
    ],
    attributeSetId: 'as-special-occasion',
    count: 36,
    enabled: true
  },
  {
    id: 'cat-accessories',
    name: 'Accessories',
    slug: 'accessories',
    level: 0,
    children: [
      { id: 'cat-acc-veils', name: 'Veils', slug: 'veils', attrOverrides: { length: 'visible' } },
      { id: 'cat-acc-shoes', name: 'Shoes', slug: 'shoes', attrOverrides: {} },
      { id: 'cat-acc-jewelry', name: 'Jewelry', slug: 'jewelry', attrOverrides: {} },
      { id: 'cat-acc-headpieces', name: 'Headpieces', slug: 'headpieces', attrOverrides: {} }
    ],
    attributeSetId: 'as-accessories',
    count: 28,
    enabled: true
  }
]

// 自定义标签维度（商户自建，不绑定属性集）
export const tagDimensions = [
  { id: 'dim-theme', name: '主题', slug: 'theme', desc: '婚礼场景标签' },
  { id: 'dim-collection', name: '系列', slug: 'collection', desc: '产品系列/上新批次' },
  { id: 'dim-season', name: '季节', slug: 'season', desc: '季节标签' }
]

export const customTags = [
  // 主题
  { id: 'tag-beach', dimensionId: 'dim-theme', name: 'Beach', slug: 'beach', count: 18, enabled: true, cover: `${REF}/kissprom/wedding-beach-short-05.jpg` },
  { id: 'tag-garden', dimensionId: 'dim-theme', name: 'Garden', slug: 'garden', count: 24, enabled: true, cover: `${REF}/davidsbridal/bridesmaid-sage-01.jpg` },
  { id: 'tag-vineyard', dimensionId: 'dim-theme', name: 'Vineyard', slug: 'vineyard', count: 15, enabled: true, cover: `${REF}/kissprom/prom-champagne-lace-05.jpg` },
  { id: 'tag-forest', dimensionId: 'dim-theme', name: 'Forest', slug: 'forest', count: 12, enabled: true, cover: `${REF}/kissprom/wedding-aline-longsleeve-06.jpg` },
  { id: 'tag-boho', dimensionId: 'dim-theme', name: 'Boho', slug: 'boho', count: 9, enabled: false, cover: `${REF}/birdygrey/bridesmaid-pink-bryten-02.jpg` },
  // 系列
  { id: 'tag-outdoor-edit-2026', dimensionId: 'dim-collection', name: 'Outdoor Edit 2026', slug: 'outdoor-edit-2026', count: 32, enabled: true },
  { id: 'tag-golden-hour', dimensionId: 'dim-collection', name: 'Golden Hour', slug: 'golden-hour', count: 12, enabled: true }
]

// Helper functions
export const tagsByDimension = (dimId) => customTags.filter(t => t.dimensionId === dimId)
export const findCategory = (id) => {
  for (const root of standardTaxonomy) {
    if (root.id === id) return root
    const child = root.children?.find(c => c.id === id)
    if (child) return { ...child, parent: root }
  }
  return null
}
export const childrenOf = (parentId) => {
  const parent = standardTaxonomy.find(r => r.id === parentId)
  return parent?.children || []
}

// Get attribute set for a category (looks up parent if child selected)
export function getAttributeSetForCategory(categoryId) {
  // Direct match on root
  const root = standardTaxonomy.find(r => r.id === categoryId)
  if (root) return attributeSets[root.attributeSetId]
  // Child: inherit parent's attribute set
  for (const r of standardTaxonomy) {
    if (r.children?.some(c => c.id === categoryId)) {
      return attributeSets[r.attributeSetId]
    }
  }
  return attributeSets['as-wedding-dresses'] // fallback
}

// 查找品类节点所属的根品类（无论传入根 id 还是子品类 id）
function findRootByCategory(categoryId) {
  const root = standardTaxonomy.find(r => r.id === categoryId)
  if (root) return root
  return standardTaxonomy.find(r => r.children?.some(c => c.id === categoryId)) || null
}

// 取子品类相对父级基础属性集的覆盖项（delta），传入根 id 返回空对象
export function childOverridesOf(categoryId) {
  for (const r of standardTaxonomy) {
    const child = r.children?.find(c => c.id === categoryId)
    if (child) return child.attrOverrides || {}
  }
  return {}
}

// 解析某品类（最具体粒度）的最终生效属性配置：
//   基础属性集 ⊕ 子品类 attrOverrides。
// 返回 { id, label, baseSetId, categoryId, attrs, overrides }，
// 其中 attrs 为合并后的最终可见性映射，overrides 为该子品类声明的 delta（用于 UI 标注）。
export function resolveAttributeConfig(categoryId) {
  const root = findRootByCategory(categoryId)
  const baseSet = attributeSets[root?.attributeSetId] || attributeSets['as-wedding-dresses']
  const overrides = childOverridesOf(categoryId)
  const child = root?.children?.find(c => c.id === categoryId)
  return {
    id: baseSet.id,
    label: baseSet.label,
    baseSetId: baseSet.id,
    categoryId,
    attrs: { ...baseSet.attrs, ...overrides },
    overrides,
    isChild: !!child,
    childName: child?.name || null
  }
}

// 向后兼容：旧代码引用 categories / themes / taxonomyTypes / taxonomies / taxonomiesByType
export const taxonomyTypes = [
  { type: 'category', label: '标准品类', desc: '系统预定义，驱动品类属性', structural: true },
  { type: 'theme', label: '主题', desc: '自定义营销标签', structural: false }
]
export const taxonomies = [
  ...standardTaxonomy.map(r => ({ id: r.id, type: 'category', name: r.name, href: '/' + r.slug, count: r.count, online: r.enabled, children: r.children?.map(c => ({ id: c.id, name: c.name })) })),
  ...customTags.filter(t => t.dimensionId === 'dim-theme').map(t => ({ id: t.id, type: 'theme', name: t.name, href: '/outdoor/' + t.slug, count: t.count, online: t.enabled, cover: t.cover }))
]
export const taxonomiesByType = (type) => taxonomies.filter(t => t.type === type)
export const categories = taxonomies.filter(t => t.type === 'category')
export const themes = taxonomies.filter(t => t.type === 'theme').map(t => ({ ...t, products: t.count }))

// ===== 站点装修：导航 =====
// 导航项引用分类（taxonomy），按 type 选择目标，不复制结构：
//   linkType: 'taxonomy' → 链接到某分类（taxonomyId，href 由分类派生，品类/主题通用）
//   linkType: 'custom'   → 自定义 href（如 Inspiration 内容栏目）
// megaMenu 每列声明数据源，子链接从被引用对象派生（只读），不再手敲：
//   source: 'category-children' + refId → 取该品类的子类
//   source: 'taxonomy-type' + refType   → 取某 type 的全部上线分类（如全部主题）
//   source: 'custom' + links[]          → 自定义链接（仅 custom 列允许手填）
export const navConfig = {
  main: [
    { id: 'nav-wd', label: 'Wedding Dresses', linkType: 'taxonomy', taxonomyId: 'cat-wedding-dresses', megaMenu: [
      { title: 'Shop by Silhouette', source: 'category-children', refId: 'cat-wedding-dresses' }
    ]},
    { id: 'nav-so', label: 'Special Occasion', linkType: 'taxonomy', taxonomyId: 'cat-special-occasion', megaMenu: [
      { title: 'Shop by Occasion', source: 'category-children', refId: 'cat-special-occasion' }
    ]},
    { id: 'nav-acc', label: 'Accessories', linkType: 'taxonomy', taxonomyId: 'cat-accessories', megaMenu: [
      { title: 'Shop Accessories', source: 'category-children', refId: 'cat-accessories' }
    ]},
    { id: 'nav-outdoor', label: 'Outdoor Weddings', linkType: 'custom', href: '/outdoor-weddings', megaMenu: [
      { title: 'Shop by Setting', source: 'taxonomy-type', refType: 'theme' }
    ]},
    { id: 'nav-inspo', label: 'Inspiration', linkType: 'custom', href: '/inspiration', megaMenu: [] }
  ],
  footer: [
    { title: 'Shop', links: 4 },
    { title: 'Help', links: 4 },
    { title: 'Company', links: 4 },
    { title: 'Account', links: 4 }
  ]
}

// ===== 站点装修：Banner（mall 广告列表范式） =====
export const banners = [
  { id: 1, name: 'Outdoor Edit 2026 主 Banner', position: '首页 Hero', img: `${REF}/kissprom/wedding-aline-tulle-01.jpg`, start: '2026-05-01', end: '2026-08-31', online: true, clicks: 12840, sort: 100 },
  { id: 2, name: 'Bridesmaid Color 推广', position: '首页 推荐位', img: `${REF}/birdygrey/bridesmaid-pink-bella-01.jpg`, start: '2026-05-10', end: '2026-07-10', online: true, clicks: 8210, sort: 90 },
  { id: 3, name: 'Klarna 分期付款条', position: '全站 顶部条', img: `${REF}/davidsbridal/wedding-dress-04.jpg`, start: '2026-04-01', end: '2026-12-31', online: true, clicks: 24600, sort: 80 },
  { id: 4, name: 'Spring Sale 春季促销', position: '品类页 顶部', img: `${REF}/kissprom/prom-champagne-lace-05.jpg`, start: '2026-03-01', end: '2026-05-30', online: false, clicks: 5400, sort: 60 }
]

// ===== 营销：优惠券 =====
export const coupons = [
  { id: 'c-1', code: 'WELCOME15', name: '新客 85 折', type: '折扣', value: '15% OFF', min: 0, total: 5000, used: 1842, start: '2026-01-01', end: '2026-12-31', status: 'active' },
  { id: 'c-2', code: 'SHIP200', name: '满 $200 包邮', type: '包邮', value: 'Free Shipping', min: 200, total: 99999, used: 6240, start: '2026-01-01', end: '2026-12-31', status: 'active' },
  { id: 'c-3', code: 'SPRING50', name: '满 $500 减 $50', type: '满减', value: '$50 OFF', min: 500, total: 2000, used: 980, start: '2026-03-01', end: '2026-05-31', status: 'expiring' },
  { id: 'c-4', code: 'BRIDE10', name: '伴娘套装 9 折', type: '折扣', value: '10% OFF', min: 300, total: 1000, used: 0, status: 'draft' }
]

export const flashSales = [
  { id: 'f-1', name: 'Memorial Day Flash', products: 12, discount: '最高 40% OFF', start: '2026-05-25 00:00', end: '2026-05-29 23:59', status: 'active' },
  { id: 'f-2', name: 'Summer Kickoff', products: 8, discount: '最高 30% OFF', start: '2026-06-01 00:00', end: '2026-06-03 23:59', status: 'scheduled' }
]

export const emailSubscribers = [
  { email: 'emma.j@example.com', source: 'Newsletter 弹窗', date: '2026-05-28', status: 'subscribed' },
  { email: 'sofia.m@example.com', source: 'Footer 订阅', date: '2026-05-27', status: 'subscribed' },
  { email: 'ava.chen@example.com', source: 'Checkout', date: '2026-05-26', status: 'subscribed' },
  { email: 'guest.842@example.com', source: 'Exit Intent', date: '2026-05-25', status: 'unsubscribed' }
]
export const emailCampaigns = [
  { id: 'm-1', name: 'Outdoor Wedding Edit 上新', recipients: 42600, openRate: '38.2%', clickRate: '6.4%', sent: '2026-05-20', status: 'sent' },
  { id: 'm-2', name: 'Memorial Day Flash Sale', recipients: 41200, openRate: '41.0%', clickRate: '9.1%', sent: '2026-05-24', status: 'sent' },
  { id: 'm-3', name: 'Bridesmaid Color Guide', recipients: 0, openRate: '—', clickRate: '—', status: 'draft' }
]

// ===== 内容 CMS =====
export const blogPosts = [
  { id: 'b-1', title: 'How to Choose the Perfect Outdoor Wedding Dress', category: 'Planning', author: 'Dreamy Editorial', date: '2026-04-12', status: 'published', cover: `${REF}/davidsbridal/wedding-dress-04.jpg`, views: 8420 },
  { id: 'b-2', title: '8 Outdoor Bridesmaid Color Palettes for 2026', category: 'Inspiration', author: 'Dreamy Editorial', date: '2026-03-28', status: 'published', cover: `${REF}/birdygrey/bridesmaid-pink-bryten-02.jpg`, views: 12600 },
  { id: 'b-3', title: "A Bride's Guide to Wedding Dress Fabrics", category: 'Education', author: 'Dreamy Editorial', date: '2026-03-10', status: 'published', cover: `${REF}/kissprom/wedding-mermaid-chiffon-03.jpg`, views: 6240 },
  { id: 'b-4', title: 'Vineyard Wedding Styling Tips', category: 'Planning', author: 'Dreamy Editorial', date: '2026-05-28', status: 'draft', cover: `${REF}/kissprom/prom-champagne-lace-05.jpg`, views: 0 }
]

export const realWeddings = [
  { id: 'rw-1', couple: 'Emma & James', location: 'Big Sur, California', theme: 'Beach', date: '2025-06', cover: `${REF}/davidsbridal/wedding-dress-04.jpg`, status: 'published', shopItems: 3 },
  { id: 'rw-2', couple: 'Sofia & Marco', location: 'Sonoma Valley', theme: 'Vineyard', date: '2025-09', cover: `${REF}/kissprom/prom-champagne-lace-05.jpg`, status: 'published', shopItems: 3 },
  { id: 'rw-3', couple: 'Ava & Noah', location: 'Redwood Forest, Oregon', theme: 'Forest', date: '2025-05', cover: `${REF}/birdygrey/bridesmaid-pink-bryten-02.jpg`, status: 'published', shopItems: 3 },
  { id: 'rw-4', couple: 'Mia & Liam', location: 'Malibu Beach', theme: 'Beach', date: '2026-04', cover: `${REF}/kissprom/wedding-beach-short-05.jpg`, status: 'draft', shopItems: 0 }
]

export const lookbooks = [
  { id: 'lb-1', title: 'Golden Hour Collection', items: 12, theme: 'Vineyard', status: 'published' },
  { id: 'lb-2', title: 'Coastal Romance', items: 9, theme: 'Beach', status: 'published' },
  { id: 'lb-3', title: 'Woodland Whisper', items: 8, theme: 'Forest', status: 'draft' }
]
export const guides = [
  { id: 'g-1', phase: 'Phase 1', timeframe: '12+ months out', title: 'Dream & Discover', tasks: 4, status: 'published' },
  { id: 'g-2', phase: 'Phase 2', timeframe: '9–12 months out', title: 'Find Your Gown', tasks: 4, status: 'published' },
  { id: 'g-3', phase: 'Phase 3', timeframe: '6–9 months out', title: 'Style Your Party', tasks: 4, status: 'published' },
  { id: 'g-4', phase: 'Phase 4', timeframe: '3–6 months out', title: 'Accessorize', tasks: 4, status: 'published' },
  { id: 'g-5', phase: 'Phase 5', timeframe: '1–3 months out', title: 'Final Fittings', tasks: 4, status: 'draft' }
]

// ===== 退款工单 =====
export const refunds = [
  { id: 'RF-2051', order: 'DRM-20260528-0981', customer: 'Mia Wilson', amount: 296, currency: 'GBP', reason: '尺码不合适', date: '2026-05-28', status: 'pending' },
  { id: 'RF-2048', order: 'DRM-20260526-0903', customer: 'Grace Kim', amount: 168, currency: 'USD', reason: '颜色与图片不符', date: '2026-05-26', status: 'pending' },
  { id: 'RF-2042', order: 'DRM-20260524-0871', customer: 'Lily Park', amount: 1280, currency: 'USD', reason: '改变主意', date: '2026-05-24', status: 'approved' },
  { id: 'RF-2039', order: 'DRM-20260522-0840', customer: 'Zoe Adams', amount: 88, currency: 'USD', reason: '商品瑕疵', date: '2026-05-22', status: 'rejected' }
]

// ===== 物流 =====
export const carriers = [
  { name: 'FedEx International Priority', zones: '全球', leadTime: '3-5 天', enabled: true },
  { name: 'UPS Worldwide Express', zones: '北美 / 欧洲', leadTime: '4-6 天', enabled: true },
  { name: 'DHL Express', zones: '全球', leadTime: '3-6 天', enabled: true },
  { name: 'USPS Priority', zones: '美国境内', leadTime: '2-4 天', enabled: false }
]
export const shippingRates = [
  { zone: '美国境内', under: '$8.00', over: '免邮', threshold: '$200' },
  { zone: '加拿大', under: '$18.00', over: '免邮', threshold: '$300' },
  { zone: '欧洲 / 英国', under: '$28.00', over: '免邮', threshold: '$400' },
  { zone: '澳洲 / 新西兰', under: '$32.00', over: '免邮', threshold: '$400' }
]

// ===== 系统：菜单级权限 + 管理员 + 角色 + 操作日志 =====

// 菜单权限点 —— 与侧边栏 menuGroups 结构对齐，每个菜单项 = 一个权限点
export const menuPermissionKeys = [
  // 工作台
  { key: '/', group: '工作台', label: '工作台' },
  // 站点装修
  { key: '/site/home', group: '站点装修', label: '首页装修' },
  { key: '/site/navigation', group: '站点装修', label: '导航与页脚' },
  { key: '/site/banners', group: '站点装修', label: 'Banner 管理' },
  // 商品管理
  { key: '/products', group: '商品管理', label: '商品列表' },
  { key: '/categories', group: '商品管理', label: '分类管理' },
  { key: '/attributes', group: '商品管理', label: '属性集定义' },
  // 订单管理
  { key: '/orders', group: '订单管理', label: '订单列表' },
  { key: '/refunds', group: '订单管理', label: '退款工单' },
  // 用户管理
  { key: '/customers', group: '用户管理', label: '用户列表' },
  // 营销活动
  { key: '/marketing/promotions', group: '营销活动', label: '优惠券与促销' },
  { key: '/marketing/email', group: '营销活动', label: '邮件营销' },
  // 内容管理
  { key: '/content/blog', group: '内容管理', label: 'Blog 文章' },
  { key: '/content/weddings', group: '内容管理', label: 'Real Weddings' },
  { key: '/content/lookbook', group: '内容管理', label: 'Lookbook 与指南' },
  // 数据分析
  { key: '/analytics', group: '数据分析', label: '数据看板' },
  // 发布与系统
  { key: '/publish', group: '发布与系统', label: '发布中心' },
  { key: '/shipping', group: '发布与系统', label: '物流配置' },
  // 系统管理
  { key: '/system/admins', group: '系统管理', label: '管理员管理' },
  { key: '/system/roles', group: '系统管理', label: '角色权限' },
  { key: '/system/auth', group: '系统管理', label: '登录与认证' },
  { key: '/system/logs', group: '系统管理', label: '操作日志' }
]

export const adminUsers = [
  { id: 'a-1', name: 'Super Admin', email: 'admin@dreamy.com', role: '超级管理员', lastLogin: '2026-05-30 09:12', status: 'active', createdAt: '2025-06-01' },
  { id: 'a-2', name: 'Grace PIM', email: 'grace@dreamy.com', role: '商品运营', lastLogin: '2026-05-30 08:40', status: 'active', createdAt: '2025-08-15' },
  { id: 'a-3', name: 'Leo Orders', email: 'leo@dreamy.com', role: '订单客服', lastLogin: '2026-05-29 18:22', status: 'active', createdAt: '2025-09-01' },
  { id: 'a-4', name: 'Nina Editorial', email: 'nina@dreamy.com', role: '内容编辑', lastLogin: '2026-05-29 14:05', status: 'active', createdAt: '2025-10-10' },
  { id: 'a-5', name: 'Sam Data', email: 'sam@dreamy.com', role: '数据分析', lastLogin: '2026-05-28 11:30', status: 'disabled', createdAt: '2025-11-20' }
]

// 角色定义 —— 菜单级二元权限模型（有/无）
export const roles = [
  {
    id: 'r-super',
    name: '超级管理员',
    type: 'preset',
    isLocked: true,
    adminCount: 1,
    permissions: menuPermissionKeys.map(p => p.key) // 全部权限
  },
  {
    id: 'r-pim',
    name: '商品运营',
    type: 'preset',
    isLocked: false,
    adminCount: 1,
    permissions: [
      '/', '/products', '/categories', '/attributes',
      '/site/home', '/site/navigation', '/site/banners',
      '/orders', '/refunds', '/analytics', '/publish', '/shipping'
    ]
  },
  {
    id: 'r-oms',
    name: '订单客服',
    type: 'preset',
    isLocked: false,
    adminCount: 1,
    permissions: [
      '/', '/orders', '/refunds', '/customers', '/shipping'
    ]
  },
  {
    id: 'r-editor',
    name: '内容编辑',
    type: 'preset',
    isLocked: false,
    adminCount: 1,
    permissions: [
      '/', '/site/home', '/site/navigation', '/site/banners',
      '/marketing/promotions', '/marketing/email',
      '/content/blog', '/content/weddings', '/content/lookbook',
      '/publish'
    ]
  },
  {
    id: 'r-data',
    name: '数据分析',
    type: 'preset',
    isLocked: false,
    adminCount: 1,
    permissions: [
      '/', '/analytics', '/orders', '/refunds', '/customers', '/products'
    ]
  }
]

// 兼容旧代码：roleMatrix 保留用于过渡
export const roleMatrix = {
  modules: ['商品', '订单', '用户', '营销', '数据', '内容', '系统'],
  roles: [
    { role: '超级管理员', perms: ['full', 'full', 'full', 'full', 'full', 'full', 'full'] },
    { role: '商品运营', perms: ['full', 'read', 'none', 'read', 'read', 'none', 'none'] },
    { role: '订单客服', perms: ['read', 'full', 'read', 'none', 'none', 'none', 'none'] },
    { role: '内容编辑', perms: ['read', 'none', 'none', 'read', 'none', 'full', 'none'] },
    { role: '数据分析', perms: ['read', 'read', 'read', 'read', 'full', 'none', 'none'] }
  ]
}

export const auditLogs = [
  { id: 'log-0a', time: '2026-05-30 10:02:11', user: '系统（自动）', action: '账户合并', target: '自动归并 u-1071 → u-1001（Emma Johnson，已验证邮箱一致）', ip: '—', ua: '系统任务', changes: [
    { field: '保留账户', before: '—', after: 'u-1001 Emma Johnson' },
    { field: '合并账户', before: 'u-1071 Emma J.', after: '—（已删除）' },
    { field: '迁移订单', before: '—', after: '1 笔' },
    { field: '登录方式', before: 'email', after: 'email + google' }
  ]},
  { id: 'log-0b', time: '2026-05-30 09:58:40', user: 'Super Admin', action: '认证配置变更', target: '登录与认证设置', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: [
    { field: 'Apple 登录', before: '关闭', after: '开启' },
    { field: '验证码有效期', before: '5 分钟', after: '10 分钟' }
  ]},
  { id: 'log-0c', time: '2026-05-30 09:40:05', user: 'Leo Orders', action: '强制下线', target: 'Emma Johnson 的全部会话', ip: '198.51.100.9', ua: 'Firefox 126 / macOS', changes: null },
  { id: 'log-1', time: '2026-05-30 09:15:32', user: 'Super Admin', action: '登录', target: 'Super Admin 登录后台', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: null },
  { id: 'log-2', time: '2026-05-30 09:20:18', user: 'Super Admin', action: '创建管理员', target: '新增管理员 Grace PIM', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: [
    { field: '姓名', before: '—', after: 'Grace PIM' },
    { field: '邮箱', before: '—', after: 'grace@dreamy.com' },
    { field: '角色', before: '—', after: '商品运营' }
  ]},
  { id: 'log-3', time: '2026-05-30 08:52:05', user: 'Grace PIM', action: '编辑商品', target: 'Aurelia A-Line Tulle Gown', ip: '198.51.100.4', ua: 'Chrome 125 / Windows', changes: [
    { field: '价格', before: '$1,480', after: '$1,280' },
    { field: '库存', before: '12', after: '24' }
  ]},
  { id: 'log-4', time: '2026-05-30 08:41:50', user: 'Grace PIM', action: '上架商品', target: 'Petal Bridesmaid Dress', ip: '198.51.100.4', ua: 'Chrome 125 / Windows', changes: [
    { field: '状态', before: '草稿', after: '已上架' }
  ]},
  { id: 'log-5', time: '2026-05-29 18:25:14', user: 'Leo Orders', action: '订单发货', target: 'DRM-20260529-1038', ip: '198.51.100.9', ua: 'Firefox 126 / macOS', changes: [
    { field: '物流状态', before: '待发货', after: '已发货' },
    { field: '承运方', before: '—', after: 'FedEx International Priority' },
    { field: '运单号', before: '—', after: 'FX-8821-4492-3107' }
  ]},
  { id: 'log-6', time: '2026-05-29 14:08:41', user: 'Nina Editorial', action: '发布文章', target: 'Vineyard Wedding Styling Tips', ip: '198.51.100.12', ua: 'Chrome 125 / macOS', changes: [
    { field: '状态', before: '草稿', after: '已发布' }
  ]},
  { id: 'log-7', time: '2026-05-29 10:30:07', user: 'Super Admin', action: '权限变更', target: '商品运营角色权限调整', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: [
    { field: '营销活动权限', before: '只读', after: '完全权限' },
    { field: '数据分析权限', before: '无', after: '只读' }
  ]},
  { id: 'log-8', time: '2026-05-29 09:02:23', user: 'Super Admin', action: '禁用管理员', target: 'Sam Data 账号已禁用', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: [
    { field: '状态', before: '正常', after: '已禁用' }
  ]},
  { id: 'log-9', time: '2026-05-28 16:40:55', user: 'Super Admin', action: '发布站点', target: '23 个页面全量生成', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: [
    { field: '受影响页面', before: '—', after: '23' },
    { field: '构建耗时', before: '—', after: '34s' }
  ]},
  { id: 'log-10', time: '2026-05-28 11:20:31', user: 'Grace PIM', action: '重置密码', target: 'Leo Orders 密码已重置', ip: '198.51.100.4', ua: 'Chrome 125 / Windows', changes: null },
  { id: 'log-11', time: '2026-05-27 15:05:19', user: 'Leo Orders', action: '删除管理员', target: '临时账号 temp-ops 已删除', ip: '198.51.100.9', ua: 'Firefox 126 / macOS', changes: [
    { field: '姓名', before: 'Temp Ops', after: '—（已删除）' }
  ]},
  { id: 'log-12', time: '2026-05-27 09:00:04', user: 'Super Admin', action: '登录', target: 'Super Admin 登录后台', ip: '203.0.113.7', ua: 'Chrome 125 / macOS', changes: null }
]

// 操作类型枚举（供日志筛选下拉）
export const logActionTypes = ['登录', 'Google 登录', 'Apple 登录', '创建管理员', '编辑管理员', '删除管理员', '禁用管理员', '重置密码', '创建角色', '编辑角色', '删除角色', '权限变更', '账户合并', '强制下线', '认证配置变更', '编辑商品', '上架商品', '下架商品', '订单发货', '发布文章', '发布站点']

// ===== 发布中心：待发布改动 + 历史 =====
export const pendingChanges = [
  { id: 'ch-1', type: '首页装修', summary: '更新 Hero 标题与副文案', author: 'Nina Editorial', time: '2026-05-29 09:02', affects: ['/'] },
  { id: 'ch-2', type: '商品', summary: '上架 Petal Bridesmaid Dress', author: 'Grace PIM', time: '2026-05-29 08:41', affects: ['/special-occasion', '/product/petal-bridesmaid', '/'] },
  { id: 'ch-3', type: 'Banner', summary: '下线「Spring Sale 春季促销」', author: 'Grace PIM', time: '2026-05-29 08:30', affects: ['/wedding-dresses', '/special-occasion', '/accessories'] },
  { id: 'ch-4', type: '内容', summary: '发布文章 Vineyard Wedding Styling Tips', author: 'Nina Editorial', time: '2026-05-28 14:08', affects: ['/blog', '/blog/vineyard-wedding-styling-tips'] },
  { id: 'ch-5', type: '导航', summary: '调整 Accessories Mega Menu 列', author: 'Super Admin', time: '2026-05-28 11:20', affects: ['全站 header'] }
]

export const publishHistory = [
  { id: 'pub-128', time: '2026-05-28 17:40', author: 'Super Admin', pages: 21, duration: '34s', status: 'success', note: '发布 Memorial Day Flash 活动' },
  { id: 'pub-127', time: '2026-05-26 10:12', author: 'Grace PIM', pages: 8, duration: '19s', status: 'success', note: '商品价格批量更新' },
  { id: 'pub-126', time: '2026-05-24 09:05', author: 'Nina Editorial', pages: 5, duration: '14s', status: 'success', note: 'Blog 内容更新' },
  { id: 'pub-125', time: '2026-05-22 15:33', author: 'Super Admin', pages: 27, duration: '41s', status: 'success', note: '全站重新生成' }
]
