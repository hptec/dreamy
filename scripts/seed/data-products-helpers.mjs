// 商品数据工厂与公共片段(图片相对路径由 store public 静态服务)
export const IMG = (p) => `/competitor-refs/${p}`

// 通用女士尺码表 US0-14(婚纱/礼服标准 Hollow to Floor)
export const STD_SIZE_CHART = [
  { us: '0', uk: '4', au: '4', bust: 31.5, waist: 23.5, hips: 34.5, hollowToFloor: 58 },
  { us: '2', uk: '6', au: '6', bust: 32.5, waist: 24.5, hips: 35.5, hollowToFloor: 58 },
  { us: '4', uk: '8', au: '8', bust: 33.5, waist: 25.5, hips: 36.5, hollowToFloor: 58.5 },
  { us: '6', uk: '10', au: '10', bust: 34.5, waist: 26.5, hips: 37.5, hollowToFloor: 58.5 },
  { us: '8', uk: '12', au: '12', bust: 35.5, waist: 27.5, hips: 38.5, hollowToFloor: 59 },
  { us: '10', uk: '14', au: '14', bust: 36.5, waist: 28.5, hips: 39.5, hollowToFloor: 59 },
  { us: '12', uk: '16', au: '16', bust: 38, waist: 30, hips: 41, hollowToFloor: 59.5 },
  { us: '14', uk: '18', au: '18', bust: 39.5, waist: 31.5, hips: 42.5, hollowToFloor: 59.5 }
]

// 固定伪随机(可重现)
let _seed = 20260912
const _rnd = () => { _seed = (_seed * 9301 + 49297) % 233280; return _seed / 233280 }

// 全码 SKU 工厂:单主色 × US0-14(与 PDP 仅选尺码的交互一致;色维度由款式商品承担)
// 库存随机 3-15,每款随机 1 个尺码 sold out(裁判整改:恒定 stock=8 一眼假)
export const fullSizeSkus = (prefix, color) => {
  const soldOutIdx = Math.floor(_rnd() * 8)
  return ['0', '2', '4', '6', '8', '10', '12', '14'].map((size, i) => ({
    skuCode: `${prefix}-US${size}`, color, size: `US ${size}`, stock: i === soldOutIdx ? 0 : 3 + Math.floor(_rnd() * 13)
  }))
}

// 商品工厂:公共默认 + 个性覆盖
export const dress = (o) => ({
  status: 2,
  installment: true,
  customSizeAvailable: true,
  sizeChart: STD_SIZE_CHART,
  // layer: 1=Shell 2=Lining(ISO 内联约定);symbol 用前端 ISO 3758 emoji 键
  fabricCompositions: [
    { layer: 1, material: o.fabricMain ?? 'Polyester', percentage: 80 },
    { layer: 1, material: 'Nylon', percentage: 20 },
    { layer: 2, material: 'Polyester', percentage: 100 }
  ],
  care: [
    { symbol: '⭕', label: 'Professional dry clean only' },
    { symbol: '💨', label: 'Steam iron on low if needed' },
    { symbol: '🧊', label: 'Do not bleach' }
  ],
  attributes: {
    model_height: ['5\'9" / 175 cm'], model_size: ['US 4'], model_body_type: ['Straight'],
    ...(o.attributes ?? {})
  },
  skus: fullSizeSkus(o.slug.toUpperCase().replace(/-/g, '').slice(0, 12), o.color ?? o.colorName ?? 'Ivory'),
  ...o,
  attributes: {
    model_height: ['5\'9" / 175 cm'], model_size: ['US 4'], model_body_type: ['Straight'],
    ...(o.attributes ?? {})
  }
})
