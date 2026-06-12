/**
 * PDP 数据派生纯函数（StoreProductDetail → 渲染模型）。
 * 颜色选项：skus 颜色去重 + kind=swatch 色样图 + gallery 主图回退。
 */

import type { ProductImage, Sku, StoreProductDetail } from '@/lib/api/store-types'
import { ImageKind } from '@/lib/api/store-types'

export interface ColorOptionVM {
  name: string
  /** 色样图（kind=swatch）或回退主图 */
  image?: string
}

export function galleryOf(detail: StoreProductDetail): ProductImage[] {
  return detail.images
    .filter((img) => img.kind === ImageKind.GALLERY)
    .sort((a, b) => a.sort - b.sort)
}

export function lifestyleOf(detail: StoreProductDetail): string | undefined {
  return detail.images.find((img) => img.kind === ImageKind.LIFESTYLE)?.url
}

export function hasVideoOf(detail: StoreProductDetail): boolean {
  return detail.images.some((img) => img.kind === ImageKind.VIDEO)
}

export function primaryImageOf(detail: StoreProductDetail): string | undefined {
  return galleryOf(detail)[0]?.url ?? detail.imageUrl ?? detail.images[0]?.url
}

export function swatchImageOf(detail: StoreProductDetail, colorName: string): string | undefined {
  return detail.images.find((img) => img.kind === ImageKind.SWATCH && img.colorName === colorName)?.url
}

export function colorOptionsOf(detail: StoreProductDetail): ColorOptionVM[] {
  const names = [...new Set(detail.skus.map((s) => s.color).filter(Boolean))]
  if (names.length === 0) {
    // 无 SKU（纯定制款）时以色样图为颜色源
    return detail.images
      .filter((img) => img.kind === ImageKind.SWATCH && img.colorName)
      .map((img) => ({ name: img.colorName as string, image: img.url }))
  }
  return names.map((name) => ({ name, image: swatchImageOf(detail, name) ?? primaryImageOf(detail) }))
}

export function sizesFor(detail: StoreProductDetail, colorName: string): { size: string; inStock: boolean }[] {
  return detail.skus
    .filter((s) => !colorName || s.color === colorName)
    .map((s) => ({ size: s.size, inStock: (s.stock ?? 0) > 0 }))
}

export function skuFor(detail: StoreProductDetail, colorName: string, size: string): Sku | undefined {
  return detail.skus.find((s) => s.color === colorName && s.size === size)
}

/** price searchParam 编码：`{min}-{max}`（空端留空，如 `-200`、`1000-`），priceMin>priceMax 前端预换位（FORM-CAT-S03） */
export function parsePriceParam(price?: string | null): { priceMin?: number; priceMax?: number } {
  if (!price) return {}
  const [rawMin, rawMax] = price.split('-')
  let min = rawMin ? Number(rawMin) : undefined
  let max = rawMax ? Number(rawMax) : undefined
  if (min !== undefined && Number.isNaN(min)) min = undefined
  if (max !== undefined && Number.isNaN(max)) max = undefined
  if (min !== undefined && max !== undefined && min > max) [min, max] = [max, min]
  return { priceMin: min, priceMax: max }
}
