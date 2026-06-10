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
  /** 是否支持免费定制尺寸（后台 A-007 SKU 开关，前台 PDP Custom Size 选项依赖） */
  customSizeEnabled: boolean
  /** 标准生产周期（天） */
  leadTimeDays: number
  /** 是否支持加急生产（Rush Fee） */
  rushAvailable: boolean
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

// ============ 迭代 4：Showroom / 定制尺寸 ============

/** 定制尺寸量体数据（inch） */
export interface CustomSizeMeasurements {
  bust: string
  waist: string
  hips: string
  hollowToFloor: string
  height: string
  heelHeight: string
}

export interface ShowroomComment {
  author: string
  text: string
  date: string
}

/** Showroom 内收藏的一个款式（款式 + 颜色组合） */
export interface ShowroomItem {
  productId: string
  color: string
  upVotes: string[]
  downVotes: string[]
  comments: ShowroomComment[]
}

export interface ShowroomMember {
  id: string
  name: string
  role: 'bride' | 'bridesmaid'
  assignedProductId?: string
  assignedColor?: string
  /** mock：是否已下单（触发 24h dye lot 保证提示） */
  hasOrdered?: boolean
}

export interface Showroom {
  id: string
  name: string
  /** YYYY-MM-DD */
  weddingDate: string
  createdAt: string
  items: ShowroomItem[]
  members: ShowroomMember[]
}

export interface WeddingGuide {
  id: string
  phase: string
  timeframe: string
  title: string
  description: string
  tasks: string[]
}
