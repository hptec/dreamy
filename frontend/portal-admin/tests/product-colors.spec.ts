// COMP-CAT-E01（ALIGN-018）颜色预设常量单测：名称/hex 逐项镜像原型 mock.js productColors
// 组件级断言（swatch 点击切换 / 自定义 chip 渲染 / 保存后跳转 /publish）→ deferred-to-L3-test
import { describe, expect, it } from 'vitest'
import {
  PRODUCT_COLORS,
  FALLBACK_SWATCH_HEX,
  colorHexOf,
  isPresetColor,
} from '@/constants/productColors'

// 原型 ground truth：hhspec/prototype/portal-admin/src/data/mock.js L95-104
const PROTOTYPE_COLORS: Array<[string, string]> = [
  ['Ivory', '#F4EFE6'],
  ['Champagne', '#E8D5B0'],
  ['White', '#FAFAF8'],
  ['Blush', '#D8A7A0'],
  ['Dusty Rose', '#C49A9A'],
  ['Sage', '#8B9D83'],
  ['Dusty Blue', '#9DB0C4'],
  ['Steel Blue', '#6C8EAD'],
  ['Lavender', '#C3B6D6'],
  ['Lilac', '#D6BBDB'],
  ['Terracotta', '#C17A56'],
  ['Rust', '#A85D3A'],
  ['Burgundy', '#7B2D42'],
  ['Navy', '#2B3A5C'],
  ['Forest Green', '#3D5A42'],
  ['Espresso', '#5A4636'],
]

describe('PRODUCT_COLORS（ALIGN-018 预设 swatch 常量）', () => {
  it('16 色逐项与原型 productColors 一致（名称+hex）', () => {
    expect(PRODUCT_COLORS.map((c) => [c.name, c.hex])).toEqual(PROTOTYPE_COLORS)
  })

  it('名称无重复', () => {
    const names = PRODUCT_COLORS.map((c) => c.name)
    expect(new Set(names).size).toBe(names.length)
  })

  it('hex 均为合法 #RRGGBB 格式', () => {
    for (const c of PRODUCT_COLORS) {
      expect(c.hex).toMatch(/^#[0-9A-F]{6}$/)
    }
  })
})

describe('colorHexOf / isPresetColor（SKU 矩阵行色点解析）', () => {
  it('预设颜色名返回对应 hex', () => {
    expect(colorHexOf('Ivory')).toBe('#F4EFE6')
    expect(colorHexOf('Burgundy')).toBe('#7B2D42')
  })

  it('非预设颜色名返回 undefined（调用方回退灰点占位）', () => {
    expect(colorHexOf('My Custom Pink')).toBeUndefined()
    expect(colorHexOf('')).toBeUndefined()
  })

  it('isPresetColor 区分预设与自定义（编辑回读不丢自定义颜色）', () => {
    expect(isPresetColor('Sage')).toBe(true)
    expect(isPresetColor('Antique Gold')).toBe(false)
  })

  it('灰点占位 hex 为合法颜色且不与预设色冲突', () => {
    expect(FALLBACK_SWATCH_HEX).toMatch(/^#[0-9A-F]{6}$/)
    expect(PRODUCT_COLORS.some((c) => c.hex === FALLBACK_SWATCH_HEX)).toBe(false)
  })
})
