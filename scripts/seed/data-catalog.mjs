// 基础目录数据:属性定义/属性集/分类树/集合组/运费
// 体系沿用产品既有属性模型(silhouette/neckline/...),分类按竞品导航习惯重组(户外婚礼定位)

// ── 属性定义(type: 1=单选 2=多选 3=文本;仅关键属性配 es/fr 翻译) ──
export const attributeDefs = [
  { key: 'silhouette', label: 'Silhouette', type: 1, options: ['A-Line', 'Mermaid', 'Sheath', 'Fit & Flare', 'Ballgown'],
    translations: [
      { locale: 'es', label: 'Silueta', options: ['Línea A', 'Sirena', 'Recto', 'Evasé', 'Princesa'] },
      { locale: 'fr', label: 'Silhouette', options: ['Ligne A', 'Sirène', 'Fourreau', 'Évasée', 'Princesse'] }
    ] },
  { key: 'neckline', label: 'Neckline', type: 1, options: ['One-Shoulder', 'V-Neck', 'Deep-V', 'Strapless', 'Halter', 'Sweetheart', 'Off-Shoulder', 'Square'],
    translations: [
      { locale: 'es', label: 'Escote', options: ['Un hombro', 'Cuello V', 'V profundo', 'Palabra de honor', 'Halter', 'Corazón', 'Hombros descubiertos', 'Cuadrado'] },
      { locale: 'fr', label: 'Encolure', options: ['Asymétrique', 'Col V', 'V plongeant', 'Bustier', 'Dos-nu', 'Cœur', 'Épaules dénudées', 'Carré'] }
    ] },
  { key: 'sleeve', label: 'Sleeve', type: 1, options: ['Sleeveless', 'Long Sleeve', 'Short Sleeve', 'Strap', 'Off-Shoulder'] },
  { key: 'back_style', label: 'Back Style', type: 1, options: ['Open Back', 'Lace-Up', 'Zipper', 'Button', 'Keyhole'] },
  { key: 'waistline', label: 'Waistline', type: 1, options: ['Natural', 'Empire', 'Drop', 'Basque'] },
  { key: 'train', label: 'Train', type: 1, options: ['None', 'Sweep', 'Chapel', 'Cathedral', 'Detachable'] },
  { key: 'length', label: 'Length', type: 1, options: ['Floor', 'Short', 'Tea-Length', 'High-Low'] },
  { key: 'fabric', label: 'Fabric', type: 1, options: ['Tulle', 'Lace', 'Chiffon', 'Satin', 'Crepe', 'Organza', 'Sequin', 'Mikado'] },
  { key: 'support', label: 'Support', type: 1, options: ['Built-in Bra', 'Boning', 'Padded Cups', 'None'] },
  { key: 'season', label: 'Season', type: 1, options: ['Spring', 'Summer', 'Fall', 'Winter'] },
  { key: 'embellishment', label: 'Embellishments', type: 2, options: ['Lace', 'Beading', 'Sequins', 'Embroidery', 'Appliqué', 'Pearls', 'Ruffles'] },
  { key: 'occasion', label: 'Occasions', type: 2, options: ['Beach', 'Garden', 'Vineyard', 'Forest', 'Courthouse', 'Elopement'] },
  { key: 'style_tag', label: 'Style Tags', type: 2, options: ['Boho', 'Classic', 'Modern', 'Romantic', 'Minimalist', 'Glam', 'Vintage'] },
  { key: 'model_height', label: 'Model Height', type: 3, options: null },
  { key: 'model_size', label: 'Model Size', type: 3, options: null },
  { key: 'model_body_type', label: 'Model Body Type', type: 3, options: null },
  { key: 'country_of_origin', label: 'Country of Origin', type: 3, options: null }
]

// ── 属性集(visibility: 1=显示 2=可选) ──
export const attributeSets = [
  { label: 'Bridal Set', items: [
    ['silhouette', 1], ['neckline', 1], ['fabric', 1], ['occasion', 1],
    ['sleeve', 2], ['back_style', 2], ['waistline', 2], ['train', 2],
    ['length', 2], ['support', 2], ['embellishment', 2], ['style_tag', 2],
    ['season', 2], ['model_height', 2], ['model_size', 2], ['model_body_type', 2]
  ] },
  { label: 'Occasion Set', items: [
    ['silhouette', 1], ['neckline', 1], ['fabric', 1],
    ['sleeve', 2], ['length', 2], ['embellishment', 2], ['style_tag', 2],
    ['back_style', 2], ['occasion', 2], ['season', 2],
    ['model_height', 2], ['model_size', 2], ['model_body_type', 2]
  ] },
  { label: 'Accessory Set', items: [
    ['fabric', 1], ['style_tag', 2], ['embellishment', 2],
    ['silhouette', 2], ['neckline', 2], ['length', 2], ['occasion', 2],
    ['model_height', 2], ['model_size', 2], ['model_body_type', 2]
  ] }
]

// ── 分类树(parent: null=根;attributeSetLabel 绑根级) ──
export const categories = [
  { name: 'Wedding Dresses', attributeSetLabel: 'Bridal Set', sort: 1, children: [
    { name: 'Beach & Destination', sort: 1 },
    { name: 'Garden & Boho', sort: 2 },
    { name: 'Classic Elegance', sort: 3 }
  ] },
  { name: 'Bridesmaids', attributeSetLabel: 'Occasion Set', sort: 2, children: [
    { name: 'Long Bridesmaid Dresses', sort: 1 },
    { name: 'Short & Convertible', sort: 2 }
  ] },
  { name: 'Occasion & Party', attributeSetLabel: 'Occasion Set', sort: 3, children: [
    { name: 'Prom & Evening', sort: 1 },
    { name: 'Homecoming', sort: 2 }
  ] },
  { name: 'Accessories', attributeSetLabel: 'Accessory Set', sort: 4, children: [
    { name: 'Jewelry & Headpieces', sort: 1 },
    { name: 'Wraps & Cover-Ups', sort: 2 },
    { name: 'Flower Girl', sort: 3 }
  ] }
]

// ── 集合组与集合(营销聚合:PDP 挂载/首页 theme cards) ──
export const collectionGroups = [
  { name: 'Shop by Theme', description: 'Curated edits for every outdoor setting', collections: [
    { name: 'Coastal Bride', status: 1 },
    { name: 'Garden Romance', status: 1 },
    { name: 'Boho Wildflower', status: 1 },
    { name: 'Modern Minimal', status: 1 }
  ] },
  { name: 'Shop by Color', description: 'Find your palette', collections: [
    { name: 'Ivory & Champagne', status: 1 },
    { name: 'Blush & Dusty Rose', status: 1 },
    { name: 'Sage & Olive', status: 1 },
    { name: 'Blue Hues', status: 1 },
    { name: 'Black & Espresso', status: 1 }
  ] }
]

// ── 承运商与运费(zone 阈值语义:fee_under 阈值以下运费,fee_over 及以上运费,threshold=免邮门槛) ──
export const carriers = [
  { name: 'FedEx International Priority', zones: 'US,CA,GB,EU,AU,Worldwide', leadTime: '3-7 business days', status: 1, code: 'FEDEX_IP', trackingUrlTemplate: 'https://www.fedex.com/fedextrack/?trknbr={tracking_no}' },
  { name: 'DHL Express Worldwide', zones: 'Worldwide', leadTime: '4-8 business days', status: 1, code: 'DHL_EXP', trackingUrlTemplate: 'https://www.dhl.com/track?tracking-id={tracking_no}' },
  { name: 'UPS Worldwide Saver', zones: 'US,CA,GB,EU,AU', leadTime: '4-9 business days', status: 1, code: 'UPS_WW', trackingUrlTemplate: 'https://www.ups.com/track?tracknum={tracking_no}' }
]

// ── 运费选项(ShippingOption:zone 规范名 × carrier code × service_level 1=标准 2=加急;
//    fee_under 阈值以下运费 / fee_over 及以上 / threshold 免邮门槛) ──
export const shippingOptions = [
  { zone: 'North America', carrierCode: 'FEDEX_IP', serviceLevel: 1, feeUnder: 12.95, feeOver: 0, threshold: 199, transitDaysMin: 3, transitDaysMax: 7 },
  { zone: 'North America', carrierCode: 'DHL_EXP', serviceLevel: 2, feeUnder: 24.95, feeOver: 24.95, threshold: 999999, transitDaysMin: 2, transitDaysMax: 4 },
  { zone: 'Europe', carrierCode: 'FEDEX_IP', serviceLevel: 1, feeUnder: 21.95, feeOver: 0, threshold: 250, transitDaysMin: 4, transitDaysMax: 8 },
  { zone: 'Europe', carrierCode: 'DHL_EXP', serviceLevel: 2, feeUnder: 32.95, feeOver: 32.95, threshold: 999999, transitDaysMin: 3, transitDaysMax: 5 },
  { zone: 'UK', carrierCode: 'FEDEX_IP', serviceLevel: 1, feeUnder: 19.95, feeOver: 0, threshold: 250, transitDaysMin: 3, transitDaysMax: 6 },
  { zone: 'Oceania', carrierCode: 'UPS_WW', serviceLevel: 1, feeUnder: 24.95, feeOver: 0, threshold: 300, transitDaysMin: 4, transitDaysMax: 9 },
  { zone: 'Asia', carrierCode: 'UPS_WW', serviceLevel: 1, feeUnder: 19.95, feeOver: 0, threshold: 300, transitDaysMin: 3, transitDaysMax: 7 },
  { zone: 'Rest of World', carrierCode: 'DHL_EXP', serviceLevel: 1, feeUnder: 29.95, feeOver: 12.95, threshold: 400, transitDaysMin: 5, transitDaysMax: 10 }
]
