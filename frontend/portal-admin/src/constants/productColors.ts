// COMP-CAT-E01（ALIGN-018）：SKU 颜色预设 swatch 常量
// 名称/hex 全量镜像原型 hhspec/prototype/portal-admin/src/data/mock.js productColors（16 色逐项一致）
// 预设列表仅是输入捷径：skuColors 仍为 string[] 自由字符串，提交 payload 不受影响
export interface ProductColor {
  name: string
  hex: string
}

export const PRODUCT_COLORS: ProductColor[] = [
  { name: 'Ivory', hex: '#F4EFE6' },
  { name: 'Champagne', hex: '#E8D5B0' },
  { name: 'White', hex: '#FAFAF8' },
  { name: 'Blush', hex: '#D8A7A0' },
  { name: 'Dusty Rose', hex: '#C49A9A' },
  { name: 'Sage', hex: '#8B9D83' },
  { name: 'Dusty Blue', hex: '#9DB0C4' },
  { name: 'Steel Blue', hex: '#6C8EAD' },
  { name: 'Lavender', hex: '#C3B6D6' },
  { name: 'Lilac', hex: '#D6BBDB' },
  { name: 'Terracotta', hex: '#C17A56' },
  { name: 'Rust', hex: '#A85D3A' },
  { name: 'Burgundy', hex: '#7B2D42' },
  { name: 'Navy', hex: '#2B3A5C' },
  { name: 'Forest Green', hex: '#3D5A42' },
  { name: 'Espresso', hex: '#5A4636' },
]

/** 自定义颜色（不在预设表）色点灰色占位 */
export const FALLBACK_SWATCH_HEX = '#D9D9D9'

/** 颜色名 → 预设 hex；不在预设表返回 undefined（调用方用 FALLBACK_SWATCH_HEX 占位） */
export function colorHexOf(name: string): string | undefined {
  return PRODUCT_COLORS.find((c) => c.name === name)?.hex
}

/** 是否预设颜色（用于区分预设 swatch 按钮选中态与自定义 chip 渲染） */
export function isPresetColor(name: string): boolean {
  return PRODUCT_COLORS.some((c) => c.name === name)
}
