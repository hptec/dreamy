// 共享类型定义 — Dreamy 商城原型数据层

export type Currency = 'USD' | 'CAD' | 'AUD' | 'GBP'

export interface ColorOption {
  name: string
  hex: string
  /** 该颜色对应的商品图（public 相对路径） */
  image: string
}

export interface SizeStock {
  size: string
  inStock: boolean
}

export type Category = 'wedding-dresses' | 'special-occasion' | 'accessories'

export interface Product {
  id: string
  slug: string
  name: string
  category: Category
  subCategory: string
  price: number
  compareAtPrice?: number
  rating: number
  reviewCount: number
  /** 主图廊（按角色：正/侧/背/lifestyle） */
  gallery: string[]
  /** 一张户外 lifestyle 场景图 */
  lifestyle?: string
  /** 走秀视频占位（原型用图片代替） */
  hasVideo?: boolean
  colors: ColorOption[]
  sizes: SizeStock[]
  silhouette?: string
  fabric?: string
  neckline?: string
  sleeve?: string
  length?: string
  occasion: string[]
  /** Outdoor 主题标签 */
  themes?: string[]
  badges?: string[]
  description: string
  details: string[]
  fabricCare: string[]
  isNew?: boolean
  isBestSeller?: boolean
  /** 推荐搭配的商品 id */
  pairsWith?: string[]
}

export interface PaletteSwatch {
  name: string
  hex: string
  count: number
  theme: string
}

export interface RealWedding {
  id: string
  slug: string
  couple: string
  location: string
  theme: string
  date: string
  cover: string
  gallery: string[]
  excerpt: string
  story: string[]
  shopTheLook: string[]
}

export interface BlogPost {
  id: string
  slug: string
  title: string
  category: string
  author: string
  date: string
  readMinutes: number
  cover: string
  excerpt: string
  body: string[]
}

export interface WeddingGuide {
  id: string
  phase: string
  timeframe: string
  title: string
  description: string
  tasks: string[]
}
