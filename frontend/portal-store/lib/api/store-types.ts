/**
 * 七域 store API 类型定义（camelCase 边界，对照 specs/architecture/api-contracts/*.openapi.yml）。
 * 线上 JSON 为 snake_case，经 lib/api/case deepCamelize 转换为以下形态。
 * 域：catalog / trading / marketing / review / showroom。
 */

// ===== 通用 =====

/** huihao.page.Paginated 形状（R<Paginated<T>> data 载荷） */
export interface Paginated<T> {
  data: T[]
  totalElements: number
  pageNumber: number
  pageSize: number
  numberOfElements: number
  totalPages: number
}

export type CurrencyCode = 'USD' | 'EUR' | 'CAD' | 'AUD' | 'GBP'

// ===== catalog =====

export interface ProductImage {
  id?: number
  url: string
  kind: 'gallery' | 'lifestyle' | 'video' | 'swatch'
  colorName?: string | null
  sort: number
}

export interface Sku {
  id?: number
  skuCode: string
  color: string
  size: string
  stock?: number
  version?: number
}

export interface SizeChartRow {
  id?: number
  us: string
  uk?: string
  au?: string
  bust?: number
  waist?: number
  hips?: number
  hollowToFloor?: number
}

export interface StoreProductCard {
  id: number
  slug: string
  name: string
  subtitle?: string
  price: number
  compareAt?: number | null
  multiCurrencyPrices?: Record<string, number> | null
  installment?: boolean
  isNew?: boolean
  isBest?: boolean
  imageUrl?: string
  swatches?: { colorName?: string; url?: string }[]
  ratingAvg?: number
  ratingCount?: number
}

/** 动态属性值/可选值（value=EN 规范值——筛选参数口径；label=locale 译文） */
export interface StoreAttributeOption {
  value: string
  label: string
}

/** PDP 动态属性行（按品类生效属性集顺序，hidden 已被后端排除） */
export interface StoreAttribute {
  key: string
  label: string
  type: 'select' | 'multiselect' | 'text' | 'toggle'
  values: StoreAttributeOption[]
}

/** PLP 动态筛选维度（非 hidden 的 select/multiselect） */
export interface StoreFilterDim {
  key: string
  label: string
  type: 'select' | 'multiselect'
  options: StoreAttributeOption[]
}

export interface StoreProductDetail extends StoreProductCard {
  categoryId: number
  categoryName?: string
  productType?: string
  description?: string
  designerNote?: string
  leadTimeDays: number
  rushAvailable?: boolean
  customSizeAvailable?: boolean
  /** 动态属性（EAV，attribute_def 字典驱动） */
  attributes?: StoreAttribute[]
  fabricComposition?: string
  modelHeight?: string
  modelSize?: string
  modelBodyType?: string
  careInstructions?: string
  countryOfOrigin?: string
  styleNo?: string
  seoTitle?: string
  seoDesc?: string
  images: ProductImage[]
  skus: Sku[]
  sizeChart?: SizeChartRow[]
  tags?: { id: number; dimensionId?: number; name: string }[]
}

export type RecommendationBlock =
  | 'new_arrivals'
  | 'best_sellers'
  | 'shop_by_color'
  | 'you_may_also_like'
  | 'complete_the_look'

export interface SizeRecommendationRequest {
  height: number
  bust: number
  waist: number
  hips: number
  fitPreference?: 'snug' | 'regular' | 'relaxed'
}

export interface SizeRecommendationResponse {
  matched: boolean
  recommendedRow?: SizeChartRow
  explanation?: string
  dimensionNotes?: { dimension: string; matchedUs?: string }[]
}

export interface StoreCategoryNode {
  id: number
  name: string
  parentId?: number | null
  level: number
  sort?: number
  productCount?: number
  children?: StoreCategoryNode[]
}

export interface StoreTagDimensionGroup {
  id: number
  name: string
  description?: string
  tags: { id: number; name: string; cover?: string | null; productCount?: number }[]
}

// ===== trading =====

export interface CustomSizeData {
  bust: number
  waist: number
  hips: number
  hollowToFloor: number
  height?: number
}

export interface ProductBrief {
  id: number
  slug: string
  name: string
  subtitle?: string
  price: number
  compareAt?: number | null
  multiCurrencyPrices?: Record<string, number> | null
  imageUrl?: string
  leadTimeDays?: number
  rushAvailable?: boolean
  customSizeAvailable?: boolean
  status?: 'draft' | 'published'
}

export interface CartItemCreate {
  productId: number
  skuId?: number
  qty: number
  customSizeData?: CustomSizeData
}

export interface CartItem {
  id: number
  productId: number
  skuId?: number | null
  qty: number
  customSizeData?: CustomSizeData | null
  product: ProductBrief
  sku?: { id?: number; skuCode?: string; color?: string; size?: string; stock?: number } | null
}

export interface CartResponse {
  items: CartItem[]
  dyeLotProductIds?: number[]
  mergedTruncatedItemIds?: number[]
}

export interface AddressUpsert {
  receiver: string
  phone?: string
  line: string
  city: string
  state?: string
  zip: string
  country: string
  isDefault?: boolean
}

export interface Address extends AddressUpsert {
  id: number
}

export interface CheckoutQuoteRequest {
  addressId?: number
  country?: string
  currency: CurrencyCode
  carrier?: string
  couponCode?: string
  giftWrap?: boolean
  weddingDate?: string
}

export interface ShippingOption {
  carrier: string
  fee: number
  leadTime?: string
  selected: boolean
}

export interface CheckoutQuoteResponse {
  currency: CurrencyCode
  exchangeRate: number
  subtotal: number
  shippingOptions: ShippingOption[]
  shippingFee: number
  giftWrapFee: number
  discountAmount: number
  totalAmount: number
  couponValid?: boolean
  couponReasonCode?: number
  leadTimeWarning?: boolean
  maxLeadTimeDays?: number
  dyeLotProductIds?: number[]
}

export type PaymentMethod = 'Stripe' | 'Apple Pay' | 'Google Pay' | 'Klarna' | 'Afterpay'

export interface OrderCreateRequest {
  idempotencyKey: string
  addressId: number
  currency: CurrencyCode
  carrier: string
  couponCode?: string
  giftWrap?: boolean
  weddingDate?: string
  paymentMethod: PaymentMethod
  locale?: 'en' | 'es' | 'fr'
}

export interface PaymentCredential {
  paymentIntentId: string
  clientSecret: string
}

export type OrderStatus =
  | 'pending'
  | 'paid'
  | 'shipped'
  | 'completed'
  | 'cancelled'
  | 'refunding'
  | 'refunded'

export interface OrderLine {
  id: number
  productId: number
  skuId?: number | null
  productName: string
  skuCode?: string
  color?: string
  size?: string
  qty: number
  unitPrice: number
  img?: string
  customSizeData?: CustomSizeData | null
  refundable?: boolean
}

export interface PaymentSummary {
  provider: 'stripe'
  paymentIntentId?: string
  amount: number
  currency: CurrencyCode
  status: 'created' | 'processing' | 'succeeded' | 'failed' | 'refunded'
  cardSummary?: string
  paidAt?: string
}

export interface OrderBase {
  id: number
  orderNo: string
  status: OrderStatus
  currency: CurrencyCode
  exchangeRate?: number
  weddingDate?: string
  subtotal: number
  shippingFee?: number
  giftWrap?: boolean
  giftWrapFee?: number
  discountAmount?: number
  totalAmount: number
  couponId?: number
  paymentMethod?: string
  carrier?: string
  trackingNo?: string
  expiresAt?: string
  paidAt?: string
  shippedAt?: string
  completedAt?: string
  createdAt: string
}

export interface StoreOrderListItem extends OrderBase {
  lineCount?: number
  firstLineImg?: string
}

export interface StoreRefund {
  id: number
  refundNo: string
  orderId: number
  amount: number
  currency: CurrencyCode
  reason?: string
  status: 'pending' | 'approved' | 'rejected'
  appliedAt: string
}

export interface StoreOrderDetail extends OrderBase {
  lines: OrderLine[]
  addressSnapshot: AddressUpsert
  payment?: PaymentSummary
  refundEligible?: boolean
  refundBlockReasonCode?: number
  refunds?: StoreRefund[]
}

export interface OrderCreateResponse {
  order: StoreOrderDetail
  payment: PaymentCredential
}

export interface WishlistItem {
  id?: number
  productId: number
  product: ProductBrief
}

export interface BrowseHistoryItem {
  id?: number
  productId: number
  viewedAt: string
  product: ProductBrief
}

export interface ExchangeRate {
  id?: number
  currency: CurrencyCode
  rate: number
  updatedAt?: string
}

// ===== marketing =====

export interface StoreBanner {
  id: number
  name: string
  imageUrl: string
  position: 'hero' | 'featured' | 'topbar'
  sort: number
  title?: string
  subtitle?: string
  ctaText?: string
}

export interface StoreBlogPostCard {
  id: number
  title: string
  slug: string
  cover?: string
  category?: string
  author?: string
  excerpt?: string
  publishedAt?: string
  views?: number
}

export interface StoreBlogPostDetail extends StoreBlogPostCard {
  content: string
  seoTitle?: string
  seoDescription?: string
}

export interface ProductRef {
  id: number
  slug: string
  name: string
  price: number
  imageUrl?: string
  customSizeAvailable?: boolean
  leadTimeDays?: number
}

export interface StoreRealWedding {
  id: number
  couple: string
  location?: string
  theme?: string
  weddingDate?: string
  cover?: string
  status: 'published'
  title?: string
  story?: string
  products?: ProductRef[]
}

export interface StoreLookbook {
  id: number
  title: string
  theme?: string
  description?: string
  products?: ProductRef[]
}

export interface StoreGuide {
  id: number
  phase: string
  timeframe?: string
  title: string
  body?: string
  tasksCount?: number
}

export interface StoreFlashSale {
  id: number
  name: string
  discount: string
  startAt: string
  endAt: string
  products?: ProductRef[]
}

export interface CouponValidateResponse {
  valid: boolean
  reasonCode?: number
  discountAmount?: number
  freeShipping?: boolean
  coupon?: {
    code?: string
    name?: string
    type?: 'discount' | 'fixed_amount' | 'free_shipping'
    value?: string
    minAmount?: number
  }
}

// ===== review =====

export interface ReviewImage {
  id: number
  url: string
  rejected: boolean
}

export interface StoreReview {
  id: number
  productId: number
  customerName?: string
  rating: number
  content?: string
  status: 'pending' | 'approved' | 'rejected'
  featured?: boolean
  submittedAt?: string
  images?: ReviewImage[]
  replyAuthor?: string
  replyContent?: string
  replyTime?: string
}

export interface StoreReviewListResponse extends Paginated<StoreReview> {
  ratingAvg: number
  ratingCount: number
  ratingBreakdown?: Record<string, number>
}

/**
 * F-049 我的评价行（GET /api/store/reviews/mine，按当前 user_id 过滤）：
 * store 评价卡片字段 + 审核状态徽标（pending/approved/rejected）+ 商品摘要（跳 PDP）。
 */
export interface MyReview {
  id: number
  productId: number
  productName?: string
  productSlug?: string
  productImg?: string
  rating: number
  content?: string
  status: 'pending' | 'approved' | 'rejected'
  submittedAt?: string
  images?: ReviewImage[]
  replyAuthor?: string
  replyContent?: string
  replyTime?: string
}

export interface StoreQuestion {
  id: number
  productId: number
  asker?: string
  question: string
  askedAt?: string
  answer?: string
  answerTime?: string
}

export interface PresignResponse {
  uploadUrl: string
  objectKey: string
  publicUrl: string
  expiresAt?: string
}

export type ReviewSort = 'newest' | 'rating_desc' | 'rating_asc' | 'featured_first'

// ===== showroom =====

export interface ShowroomUpsert {
  name: string
  weddingDate?: string
}

export interface ShowroomSummary {
  id: number
  ownerId: number
  name: string
  weddingDate?: string
  itemCount?: number
  memberCount?: number
}

export interface ShowroomComment {
  id: number
  showroomItemId: number
  memberId: number
  nickname: string
  content: string
  createdAt?: string
}

export interface ShowroomItem {
  id: number
  productId: number
  color?: string
  product: ProductRef
  likeCount: number
  dislikeCount: number
  myVote?: 'like' | 'dislike' | null
  comments?: ShowroomComment[]
  dyeLotNotice?: boolean
}

export type AssignStatus = 'unassigned' | 'assigned' | 'reminded' | 'ordered'

export interface ShowroomMember {
  id: number
  showroomId: number
  nickname: string
  email?: string
  assignedItemId?: number | null
  assignStatus: AssignStatus
  linkedCustomerId?: number | null
}

export interface ShowroomDetail extends ShowroomSummary {
  inviteToken?: string
  isOwner: boolean
  myMemberId?: number
  items: ShowroomItem[]
  members: ShowroomMember[]
}

export interface GuestSession {
  guestToken: string
  expiresAt?: string
  showroomId: number
  member: ShowroomMember
}

export interface VoteResult {
  likeCount: number
  dislikeCount: number
  myVote: 'like' | 'dislike'
}
