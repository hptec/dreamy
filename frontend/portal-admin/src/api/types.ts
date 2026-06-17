// API DTO 类型（边界处 camelCase；与 OpenAPI identity-api 对齐，后端返回 snake_case 由 client 统一转换）
// 约束: STORE-A01~A04 / PAGE-A01~A07 数据形状来源 = identity-api.openapi.yml + backend/admin DTO

// ===== 枚举常量（与后端 IntEnum 对齐，API 返回整数） =====

export const AdminStatus = { ACTIVE: 1, DISABLED: 2 } as const
export type AdminStatus = typeof AdminStatus[keyof typeof AdminStatus]

export const UserStatus = { ACTIVE: 1, DISABLED: 2, DELETED: 3, ANONYMIZED: 4 } as const
export type UserStatus = typeof UserStatus[keyof typeof UserStatus]

export const UserTier = { REGULAR: 1, VIP: 2 } as const
export type UserTier = typeof UserTier[keyof typeof UserTier]

export const RoleType = { PRESET: 1, CUSTOM: 2 } as const
export type RoleType = typeof RoleType[keyof typeof RoleType]

export const AuthProvider = { EMAIL: 1, GOOGLE: 2, APPLE: 3 } as const
export type AuthProvider = typeof AuthProvider[keyof typeof AuthProvider]

export const LoginOutcome = { SUCCESS: 1, FAILED: 2 } as const
export type LoginOutcome = typeof LoginOutcome[keyof typeof LoginOutcome]

// ===== 通用 =====
export interface PageResult<T> {
  data: T[]
  totalElements: number
  pageNumber: number
  pageSize: number
  totalPages: number
  numberOfElements: number
}

export interface ApiError {
  code: number
  message: string
  data?: Record<string, unknown>
}

// ===== 鉴权（PAGE-A01 / STORE-A01）=====
export interface AdminProfile {
  id: number
  name: string
  email: string
  roleId: number | null
  roleName: string | null
  status: AdminStatus
  lastLoginAt: string | null
}

export interface AdminLoginResult {
  token: string
  admin: AdminProfile
  permissionKeys: string[]
  isSuper: boolean
}

export interface AdminMe {
  admin: AdminProfile
  roleName: string
  isSuper: boolean
  permissionKeys: string[]
}

// ===== 管理员 CRUD（PAGE-A04）=====
export interface Admin {
  id: number
  name: string
  email: string
  roleId: number | null
  roleName: string | null
  status: AdminStatus
  lastLoginAt: string | null
}

export interface AdminCreatePayload {
  name: string
  email: string
  password: string
  roleId: number
}

export interface AdminUpdatePayload {
  name: string
  roleId: number
}

// ===== 角色 / 权限（PAGE-A05）=====
export interface Role {
  id: number
  name: string
  type: RoleType
  isLocked: boolean
  memberCount: number
  permissionKeys: string[]
}

export interface Permission {
  key: string
  group: string
  label: string
}

// ===== 用户身份运营（PAGE-A02 / A03）=====
export interface UserListItem {
  id: number
  email: string | null
  emailVerified?: boolean
  name: string | null
  phone?: string | null
  tier: UserTier
  avatar?: string | null
  joinedAt: string | null
  status: UserStatus
}

export interface Identity {
  id: number
  provider: AuthProvider
  identifier: string | null
  isPrimary: boolean
  verified: boolean
  hiddenEmail: boolean
  relayValid: boolean
  lastLoginAt: string | null
}

export interface Session {
  id: number
  device: string | null
  browser: string | null
  ip: string | null
  location: string | null
  isNewDevice: boolean
  isCurrent: boolean
  lastActiveAt: string | null
  createdAt: string | null
}

export interface LoginHistoryItem {
  id: number
  method: AuthProvider
  ip: string | null
  device: string | null
  location: string | null
  result: LoginOutcome
  isNewDevice?: boolean
  createdAt: string | null
}

export interface UserDetail {
  user: UserListItem
  identities: Identity[]
  sessions: Session[]
  loginHistory: LoginHistoryItem[]
}

// ===== 认证配置（PAGE-A06）=====
export interface AuthConfig {
  emailEnabled: boolean
  googleEnabled: boolean
  appleEnabled: boolean
  otpLength: number
  otpTtlMinutes: number
  otpResendSeconds: number
  otpMaxAttempts: number
  minMethods: number
  googleClientId?: string | null
  appleServiceId?: string | null
  updatedAt?: string | null
}

export interface AuthConfigUpdatePayload {
  googleEnabled: boolean
  appleEnabled: boolean
  otpLength: number
  otpTtlMinutes: number
  otpResendSeconds: number
  otpMaxAttempts: number
  minMethods: number
  googleClientId?: string | null
  appleServiceId?: string | null
}

// ===== 操作日志（PAGE-A07）=====
export interface OperationLog {
  id: number
  operatorName: string | null
  action: string
  target: string | null
  ip: string | null
  changes: string | null
  createdAt: string | null
}

// ============================================================================
// portal-api-integration：六域 DTO（camelCase；snake_case 由 client 拦截器转换）
// 来源 = specs/architecture/api-contracts/*.openapi.yml + backend DTO record
// ============================================================================

// ===== 通用（六域） =====

/** 三语翻译行 locale 枚举（EN 写主字段，ES/FR 进 translations[]——决策 13） */
export type TranslationLocale = 'es' | 'fr'

// ===== catalog（PAGE-CAT-A01~A04 / STORE-CAT-A01~A05） =====

export const ProductStatus = { DRAFT: 1, PUBLISHED: 2 } as const
export type ProductStatus = typeof ProductStatus[keyof typeof ProductStatus]

export interface ProductTranslation {
  locale: TranslationLocale
  name?: string | null
  description?: string | null
  sellingPoints?: string[] | null
  seoTitle?: string | null
  seoDescription?: string | null
  /** 设计师备注译文（决策 12，product_translation.designer_note；消费端 pick 回退 EN，FUNC-017） */
  designerNote?: string | null
}

export const ImageKind = { GALLERY: 1, LIFESTYLE: 2, VIDEO: 3, SWATCH: 4 } as const
export type ImageKind = typeof ImageKind[keyof typeof ImageKind]

export interface ProductImage {
  id?: number | null
  url: string
  kind: ImageKind
  colorName?: string | null
  sort?: number | null
}

export interface Sku {
  id?: number | null
  skuCode: string
  color: string
  size: string
  stock: number
  version?: number | null
}

export interface SizeChartRow {
  id?: number | null
  us?: string | null
  uk?: string | null
  au?: string | null
  bust?: number | string | null
  waist?: number | string | null
  hips?: number | string | null
  hollowToFloor?: number | string | null
}

export interface AdminProductListItem {
  id: number
  name: string
  slug: string
  styleNo?: string | null
  categoryId: number | null
  categoryName?: string | null
  price: number
  compareAt?: number | null
  status: ProductStatus
  isNew?: boolean | null
  isBest?: boolean | null
  recommend?: boolean | null
  sort?: number | null
  stockTotal?: number | null
  /** 累计销量派生列（wire: sales_total；ALIGN-007 / API-CAT-03，缺省按 0 展示） */
  salesTotal?: number | null
  imageUrl?: string | null
}

// ===== 商品批量操作（ALIGN-007 / API-CAT-01，STORE-CAT-P01） =====

export type ProductBatchAction = 'publish' | 'unpublish' | 'recommend' | 'delete'

/** 行级失败明细（wire: { id, error_code, message }；409509=已发布需先下架，500500=行级内部错误） */
export interface ProductBatchFailure {
  id: number
  errorCode: number
  message: string
}

/** 批量结果（逐条容错语义：部分/全部失败仍 200，wire: { success_ids, failures }） */
export interface ProductBatchResult {
  successIds: number[]
  failures: ProductBatchFailure[]
}

export interface AdminProductUpsert {
  name: string
  slug: string
  sellingPoints?: string[] | null
  categoryId: number | null
  productType?: string | null
  description?: string | null
  designerNote?: string | null
  price: number | string
  compareAt?: number | string | null
  installment?: boolean | null
  multiCurrencyPrices?: Record<string, number | string> | null
  status: ProductStatus
  isNew?: boolean | null
  isBest?: boolean | null
  recommend?: boolean | null
  sort?: number | null
  leadTimeDays?: number | null
  rushAvailable?: boolean | null
  customSizeAvailable?: boolean | null
  /** 动态属性 entries（key=attribute_def.key；select/text/toggle 单元素数组，multiselect 多元素） */
  attributes?: AttributeValueEntry[] | null
  styleNo?: string | null
  seoTitle?: string | null
  seoDesc?: string | null
  images?: ProductImage[]
  skus?: Sku[]
  sizeChart?: SizeChartRow[]
  tagIds?: number[]
  translations?: ProductTranslation[]
  /** 面料成分列表 */
  fabricCompositions?: FabricComposition[] | null
  /** 护理标签列表（内联 JSON） */
  care?: CareItem[] | null
  /** 护理备注（可选） */
  fabricCareNote?: string | null
  updatedAt?: string | null
}

/** 动态属性 entries 行（entries 数组而非 map：动态 key 是字段值，免疫 snake/camel 递归转换） */
export interface AttributeValueEntry {
  key: string
  values: string[]
}

export interface AdminProductDetail extends Omit<AdminProductUpsert, 'updatedAt'> {
  id: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface CategoryTranslation {
  locale: TranslationLocale
  name?: string | null
}

/** 三态可见性（后端 AttributeVisibility IntEnum） */
export const AttrVisibility = { VISIBLE: 1, OPTIONAL: 2, HIDDEN: 3 } as const
export type AttrVisibility = typeof AttrVisibility[keyof typeof AttrVisibility]

export interface AdminCategoryNode {
  id: number
  name: string
  parentId: number | null
  attributeSetId: number | null
  attrOverrides?: Record<string, AttrVisibility> | null
  sort?: number | null
  level?: number | null
  productCount?: number | null
  children?: AdminCategoryNode[] | null
  translations?: CategoryTranslation[] | null
}

export interface AdminCategoryUpsert {
  name: string
  parentId?: number | null
  attributeSetId?: number | null
  attrOverrides?: Record<string, AttrVisibility> | null
  sort?: number | null
  translations?: CategoryTranslation[] | null
}

export interface AttributeSetItem {
  attributeId: number
  visibility: AttrVisibility
}

export interface AttributeSet {
  id: number
  label: string
  items: AttributeSetItem[]
  categoryCount?: number | null
}

export interface AttributeSetUpsert {
  label: string
  items: AttributeSetItem[]
}

export const AttributeDefType = { SELECT: 1, MULTISELECT: 2, TEXT: 3, TOGGLE: 4 } as const
export type AttributeDefType = typeof AttributeDefType[keyof typeof AttributeDefType]

export interface AttributeDefTranslation {
  locale: TranslationLocale
  label?: string | null
  options?: string[] | null
}

export interface AttributeDef {
  id: number
  key: string
  label: string
  type: AttributeDefType
  options?: string[] | null
  translations?: AttributeDefTranslation[] | null
}

export interface AttributeDefUpsert {
  key: string
  label: string
  type: AttributeDefType
  options?: string[] | null
  translations?: AttributeDefTranslation[] | null
}

export interface TagDimensionTranslation {
  locale: TranslationLocale
  name?: string | null
}

export interface TagDimension {
  id: number
  name: string
  description?: string | null
  tagCount?: number | null
  translations?: TagDimensionTranslation[] | null
}

export interface TagDimensionUpsert {
  name: string
  description?: string | null
  translations?: TagDimensionTranslation[] | null
}

export interface TagTranslation {
  locale: TranslationLocale
  label?: string | null
}

export const TagStatus = { ENABLED: 1, DISABLED: 2 } as const
export type TagStatus = typeof TagStatus[keyof typeof TagStatus]

export interface Tag {
  id: number
  dimensionId: number
  name: string
  cover?: string | null
  status: TagStatus
  productCount?: number | null
  translations?: TagTranslation[] | null
}

export interface TagUpsert {
  dimensionId: number
  name: string
  cover?: string | null
  status: TagStatus
  translations?: TagTranslation[] | null
}

export type PresignScope = 'product' | 'category' | 'tag' | 'banner' | 'content'

export interface PresignRequest {
  fileName: string
  contentType: string
  scope?: PresignScope
}

export interface PresignResponse {
  uploadUrl: string
  objectKey: string
  publicUrl: string
  expiresAt?: string | null
}

// ===== trading（PAGE-TRD-A01~A04 / STORE-TRD-A01~A03） =====

export const OrderStatus = {
  PENDING: 1,
  PAID: 2,
  SHIPPED: 3,
  COMPLETED: 4,
  CANCELLED: 5,
  REFUNDING: 6,
  REFUNDED: 7,
} as const
export type OrderStatus = typeof OrderStatus[keyof typeof OrderStatus]

export const PaymentStatus = { CREATED: 1, PROCESSING: 2, SUCCEEDED: 3, FAILED: 4, REFUNDED: 5 } as const
export type PaymentStatus = typeof PaymentStatus[keyof typeof PaymentStatus]

export interface CustomSizeData {
  bust?: number | null
  waist?: number | null
  hips?: number | null
  hollowToFloor?: number | null
  height?: number | null
}

export interface OrderLine {
  id: number
  productId: number | null
  skuId: number | null
  productName: string
  skuCode?: string | null
  color?: string | null
  size?: string | null
  qty: number
  unitPrice: number
  img?: string | null
  customSizeData?: CustomSizeData | null
  refundable?: boolean | null
}

export interface PaymentSummary {
  provider?: string | null
  paymentIntentId?: string | null
  amount?: number | null
  currency?: string | null
  status?: PaymentStatus | null
  cardSummary?: string | null
  paidAt?: string | null
}

export interface AdminOrderListItem {
  id: number
  orderNo: string
  status: OrderStatus
  currency: string
  exchangeRate?: number | null
  weddingDate?: string | null
  subtotal?: number | null
  shippingFee?: number | null
  giftWrap?: boolean | null
  giftWrapFee?: number | null
  discountAmount?: number | null
  totalAmount: number
  couponId?: number | null
  paymentMethod?: string | null
  carrier?: string | null
  trackingNo?: string | null
  expiresAt?: string | null
  paidAt?: string | null
  shippedAt?: string | null
  completedAt?: string | null
  createdAt?: string | null
  customerId?: number | null
  customerName?: string | null
  customerEmail?: string | null
  // API-TRD-01（ALIGN-013）：DTO 末尾追加 地区/商品数（snake: country / item_count，非 breaking）
  country?: string | null
  itemCount?: number | null
}

export const RefundStatus = { PENDING: 1, APPROVED: 2, REJECTED: 3 } as const
export type RefundStatus = typeof RefundStatus[keyof typeof RefundStatus]

export interface AdminRefund {
  id: number
  refundNo: string
  orderId: number
  amount: number
  currency: string
  reason?: string | null
  rejectReason?: string | null
  status: RefundStatus
  appliedAt?: string | null
  orderNo?: string | null
  customerId?: number | null
  customerName?: string | null
  customerEmail?: string | null
  stripeRefundId?: string | null
  returnTrackingNo?: string | null
}

export interface AdminOrderDetail extends AdminOrderListItem {
  customerPhone?: string | null
  lines: OrderLine[]
  addressSnapshot?: Record<string, unknown> | null
  payment?: PaymentSummary | null
  refunds?: AdminRefund[] | null
}

export interface ExchangeRate {
  id: number
  currency: string
  rate: number
  updatedBy?: number | null
  updatedAt?: string | null
}

export interface CheckoutConfig {
  giftWrapFeeUsd: number | string
  customRefundGraceHours: number
}

// ===== marketing（PAGE-MKT-A01~A05 / STORE-MKT-A01~A06） =====

export const CouponStatus = { DRAFT: 1, SCHEDULED: 2, ACTIVE: 3, EXPIRING: 4, EXPIRED: 5 } as const
export type CouponStatus = typeof CouponStatus[keyof typeof CouponStatus]

export const CouponType = { DISCOUNT: 1, FIXED_AMOUNT: 2, FREE_SHIPPING: 3 } as const
export type CouponType = typeof CouponType[keyof typeof CouponType]

export interface CouponTranslation {
  locale: TranslationLocale
  name?: string | null
  description?: string | null
}

export interface Coupon {
  id: number
  code: string
  name: string
  type: CouponType
  value: string
  minAmount?: number | null
  totalLimit?: number | null
  usedCount?: number | null
  startAt?: string | null
  endAt?: string | null
  status: CouponStatus
  description?: string | null
  translations?: CouponTranslation[] | null
}

export interface CouponUpsert {
  code: string
  name: string
  type: CouponType
  value: string
  minAmount?: number | string | null
  totalLimit?: number | null
  startAt?: string | null
  endAt?: string | null
  status: CouponStatus
  description?: string | null
  translations?: CouponTranslation[] | null
}

export const FlashSaleStatus = { DRAFT: 1, SCHEDULED: 2, ACTIVE: 3, ENDED: 4 } as const
export type FlashSaleStatus = typeof FlashSaleStatus[keyof typeof FlashSaleStatus]

export interface FlashSaleTranslation {
  locale: TranslationLocale
  name?: string | null
}

export interface FlashSale {
  id: number
  name: string
  discount: string
  startAt?: string | null
  endAt?: string | null
  status: FlashSaleStatus
  productIds?: number[] | null
  translations?: FlashSaleTranslation[] | null
}

export interface FlashSaleUpsert {
  name: string
  discount: string
  startAt: string
  endAt: string
  status: FlashSaleStatus
  productIds?: number[] | null
  translations?: FlashSaleTranslation[] | null
}

/** Banner 状态复用后端 ContentStatus IntEnum */
export const BannerStatus = { DRAFT: 1, PUBLISHED: 2, ARCHIVED: 3 } as const
export type BannerStatus = typeof BannerStatus[keyof typeof BannerStatus]

export const BannerPosition = { HERO: 1, FEATURED: 2, TOPBAR: 3 } as const
export type BannerPosition = typeof BannerPosition[keyof typeof BannerPosition]

export interface BannerTranslation {
  locale: TranslationLocale
  title?: string | null
  subtitle?: string | null
  ctaText?: string | null
}

export interface Banner {
  id: number
  name: string
  imageUrl: string
  position: BannerPosition
  startTime?: string | null
  endTime?: string | null
  status: BannerStatus
  sort?: number | null
  clicks?: number | null
  title?: string | null
  subtitle?: string | null
  ctaText?: string | null
  translations?: BannerTranslation[] | null
}

export interface BannerUpsert {
  name: string
  imageUrl: string
  position: BannerPosition
  startTime?: string | null
  endTime?: string | null
  status: BannerStatus
  sort?: number | null
  title?: string | null
  subtitle?: string | null
  ctaText?: string | null
  translations?: BannerTranslation[] | null
}

export const ContentStatus = { DRAFT: 1, PUBLISHED: 2, ARCHIVED: 3 } as const
export type ContentStatus = typeof ContentStatus[keyof typeof ContentStatus]

export const PublishStatus = { DRAFT: 1, PUBLISHED: 2 } as const
export type PublishStatus = typeof PublishStatus[keyof typeof PublishStatus]

export interface BlogPostTranslation {
  locale: TranslationLocale
  title?: string | null
  excerpt?: string | null
  body?: string | null
  seoTitle?: string | null
  seoDescription?: string | null
}

export interface BlogPost {
  id: number
  title: string
  cover?: string | null
  category?: string | null
  author?: string | null
  content?: string | null
  slug?: string | null
  status: ContentStatus
  publishedAt?: string | null
  views?: number | null
  translations?: BlogPostTranslation[] | null
}

export interface BlogPostUpsert {
  title: string
  cover?: string | null
  category?: string | null
  author?: string | null
  content?: string | null
  slug?: string | null
  status: ContentStatus
  translations?: BlogPostTranslation[] | null
}

export interface RealWeddingTranslation {
  locale: TranslationLocale
  title?: string | null
  story?: string | null
}

export interface RealWedding {
  id: number
  couple: string
  location?: string | null
  theme?: string | null
  weddingDate?: string | null
  cover?: string | null
  status: PublishStatus
  title?: string | null
  story?: string | null
  productIds?: number[] | null
  translations?: RealWeddingTranslation[] | null
}

export interface RealWeddingUpsert {
  couple: string
  location?: string | null
  theme?: string | null
  weddingDate?: string | null
  cover?: string | null
  status: PublishStatus
  title?: string | null
  story?: string | null
  productIds?: number[] | null
  translations?: RealWeddingTranslation[] | null
}

export interface LookbookTranslation {
  locale: TranslationLocale
  title?: string | null
  description?: string | null
}

export interface Lookbook {
  id: number
  title: string
  theme?: string | null
  status: PublishStatus
  description?: string | null
  productIds?: number[] | null
  translations?: LookbookTranslation[] | null
}

export interface LookbookUpsert {
  title: string
  theme?: string | null
  status: PublishStatus
  description?: string | null
  productIds?: number[] | null
  translations?: LookbookTranslation[] | null
}

export interface GuideTranslation {
  locale: TranslationLocale
  title?: string | null
  body?: string | null
}

export interface Guide {
  id: number
  phase: string
  timeframe?: string | null
  title: string
  tasksCount?: number | null
  status: PublishStatus
  body?: string | null
  translations?: GuideTranslation[] | null
}

export interface GuideUpsert {
  phase: string
  timeframe?: string | null
  title: string
  tasksCount?: number | null
  status: PublishStatus
  body?: string | null
  translations?: GuideTranslation[] | null
}

// ===== review（PAGE-REV-A01 / STORE-REV-A01~A03） =====

/** 评价审核状态（后端 ReviewStatus IntEnum） */
export const ReviewModerationStatus = { PENDING: 1, APPROVED: 2, REJECTED: 3 } as const
export type ReviewModerationStatus = typeof ReviewModerationStatus[keyof typeof ReviewModerationStatus]

export interface ReviewImage {
  id: number
  url: string
  rejected: boolean
}

export interface AdminReview {
  id: number
  productId: number
  userId?: number | null
  productName?: string | null
  customerName?: string | null
  rating: number
  content?: string | null
  status: ReviewModerationStatus
  featured?: boolean | null
  submittedAt?: string | null
  images: ReviewImage[]
  replyAuthor?: string | null
  replyContent?: string | null
  replyTime?: string | null
}

/** MAP-REV-007：Paginated 子类 + pending_count 平铺同层 */
export interface AdminReviewPage extends PageResult<AdminReview> {
  pendingCount: number
}

export interface BatchResult {
  updatedIds: number[]
  skippedIds: number[]
}

/** Q&A 可见性（后端 QuestionVisibility IntEnum） */
export const QuestionVisible = { VISIBLE: 1, HIDDEN: 2 } as const
export type QuestionVisible = typeof QuestionVisible[keyof typeof QuestionVisible]

export interface AdminQuestion {
  id: number
  productId: number
  productName?: string | null
  asker?: string | null
  question: string
  askedAt?: string | null
  answer?: string | null
  answerTime?: string | null
  visible: QuestionVisible
}

// ===== shipping（PAGE-SHP-01 / STORE-SHP-01） =====

export const CarrierStatus = { ENABLED: 1, DISABLED: 2 } as const
export type CarrierStatus = typeof CarrierStatus[keyof typeof CarrierStatus]

export interface Carrier {
  id: number
  name: string
  zones?: string | null
  leadTime?: string | null
  status: CarrierStatus
}

export interface CarrierUpsert {
  name: string
  zones?: string | null
  leadTime?: string | null
  status: CarrierStatus
}

export interface ShippingRate {
  id: number
  zone: string
  feeUnder?: number | null
  feeOver?: number | null
  threshold?: number | null
}

export interface ShippingRateUpsert {
  zone: string
  feeUnder?: number | string | null
  feeOver?: number | string | null
  threshold?: number | string | null
}

// ===== analytics（PAGE-ANA-A01~A02 / STORE-ANA-01~02） =====

export interface Kpis {
  gmvMonth: number
  orderCount: number
  avgOrderValue: number
  refundRate: number
}

export interface Todos {
  pendingRefundCount: number
  pendingReviewCount: number
  unshippedOrderCount: number
}

export interface TrendSeries {
  labels: string[]
  values: number[]
}

export interface DashboardResponse {
  kpis: Kpis
  todos: Todos
  gmvTrend: TrendSeries
}

export interface CategorySalesItem {
  categoryId: number
  categoryName: string
  amount: number
  share: number
}

export interface TopProductItem {
  productId: number
  name: string
  imageUrl?: string | null
  sales: number
  amount: number
}

export interface AnalyticsOverviewResponse {
  kpis: Kpis
  gmvTrend: TrendSeries
  categorySales: CategorySalesItem[]
  topProducts: TopProductItem[]
}

export interface TrafficSourceItem {
  source: string
  sessions: number
  share: number
}

export interface DeviceShareItem {
  device: string
  share: number
}

export interface FunnelStage {
  stage: string
  value: number
}

export interface AnalyticsTrafficResponse {
  sourceStatus: 'ok' | 'unavailable'
  fetchedAt?: string | null
  trafficSources?: TrafficSourceItem[] | null
  deviceShare?: DeviceShareItem[] | null
  funnel?: FunnelStage[] | null
}

// ===== Fabric & Care（面料与护理标签）=====

/** Layer IntEnum 枚举 */
export const Layer = { Shell: 1, Lining: 2, Overlay: 3, Trim: 4 } as const
export type Layer = typeof Layer[keyof typeof Layer]

/** Material IntEnum 枚举 */
export const Material = {
  Cotton: 1,
  Polyester: 2,
  Lace: 3,
  Satin: 4,
  Chiffon: 5,
  Tulle: 6,
  Silk: 7,
  Organza: 8,
  Spandex: 9,
  Nylon: 10
} as const
export type Material = typeof Material[keyof typeof Material]

/** CareCategory IntEnum 枚举 */
export const CareCategory = {
  washing: 1,
  bleaching: 2,
  drying: 3,
  ironing: 4,
  dry_cleaning: 5
} as const
export type CareCategory = typeof CareCategory[keyof typeof CareCategory]

/** CareInstructionStatus IntEnum 枚举 */
export const CareInstructionStatus = { active: 1, disabled: 2 } as const
export type CareInstructionStatus = typeof CareInstructionStatus[keyof typeof CareInstructionStatus]

/** 面料成分（product_fabric_composition） */
export interface FabricComposition {
  layer: Layer  // 1=Shell 2=Lining 3=Overlay 4=Trim
  material: string  // 材质名称字符串（如 "Polyester"）
  percentage: number  // 0..100
}

/** 护理标签项（内联 JSON，取代字典表） */
export interface CareItem {
  symbol: string  // 行业通用护理 Unicode 符号
  label: string   // 展示文本
}

/** 护理标签定义（care_instruction_def，已废弃专用表） */
export interface CareInstruction {
  id: number
  code: string
  symbolUnicode: string
  labelEn: string
  labelZh: string
  category: CareCategory
  sortOrder: number
  status: CareInstructionStatus
}

/** 护理标签列表响应 */
export interface CareInstructionListResponse {
  items: CareInstruction[]
}

// ===== i18n-complete-with-ai-assist：网关配置 / AI 翻译 / 术语表 =====
// 约束: gateway-api.openapi.yml / ai-translation-api.openapi.yml / glossary-api.openapi.yml
// 边界 camelCase；后端 snake_case 由 client 统一转换（gateway_type → gatewayType 等）

/** 网关类型枚举（AI=1 / LOGISTICS=2 / PAYMENT=3，与后端 IntEnum 对齐） */
export const GatewayType = { AI: 1, LOGISTICS: 2, PAYMENT: 3 } as const
export type GatewayType = typeof GatewayType[keyof typeof GatewayType]

/** 网关协议枚举（openai=1，预留扩展） */
export const GatewayProtocol = { OPENAI: 1 } as const
export type GatewayProtocol = typeof GatewayProtocol[keyof typeof GatewayProtocol]

/** 模型刷新策略（manual=1 手动 / scheduled=2 定时） */
export const ModelRefreshStrategy = { MANUAL: 1, SCHEDULED: 2 } as const
export type ModelRefreshStrategy = typeof ModelRefreshStrategy[keyof typeof ModelRefreshStrategy]

/** 网关测试结果错误码（gateway 域 6 位） */
export const GatewayErrorCode = {
  CONFIG_NOT_FOUND: 404201,
  NAME_EXISTS: 409201,
  CONFIG_REFERENCED: 409202,
  FIELD_VALIDATION_FAILED: 422201,
  API_KEY_DECRYPTION_FAILED: 422202,
  NOT_AI_GATEWAY: 400201,
  GATEWAY_UNAVAILABLE: 502201,
  GATEWAY_AUTH_FAILED: 502202,
  GATEWAY_TIMEOUT: 504201,
} as const

/** AI 翻译域错误码（ai_translation 域 6 位） */
export const AiTranslateErrorCode = {
  // 后端 GatewayErrorCode.NO_AI_GATEWAY_CONFIGURED = 403202（无可用 AI 网关 → 引导前往配置）
  NO_ENABLED_GATEWAY: 403202,
  INVALID_MODEL: 400302,
  FIELD_VALIDATION_FAILED: 422301,
  GATEWAY_CALL_FAILED: 502301,
  GATEWAY_TIMEOUT: 504301,
} as const

/** 术语表域错误码（glossary 域 6 位） */
export const GlossaryErrorCode = {
  TERM_NOT_FOUND: 404401,
  TERM_EN_EXISTS: 409401,
  FIELD_VALIDATION_FAILED: 422401,
} as const

/** 网关可用模型（/v1/models 自动发现，GatewayModel schema） */
export interface GatewayModel {
  id: string
  name: string
  contextLength?: number | null
}

/** 网关配置写入体（GatewayConfigUpsert schema） */
export interface GatewayConfigUpsert {
  gatewayType: GatewayType
  name: string
  protocol: GatewayProtocol
  baseUrl: string
  /** 明文输入；更新时若传掩码格式（sk-****xxxx）后端保持原密文 */
  apiKey: string
  defaultModel?: string | null
  modelRefreshStrategy?: ModelRefreshStrategy | null
  modelRefreshIntervalMin?: number | null
  enabled: boolean
  extraConfig?: Record<string, unknown> | null
}

/** 网关配置详情（GatewayConfigDetail schema，含掩码 Key + 模型列表） */
export interface GatewayConfigDetail extends GatewayConfigUpsert {
  id: number
  /** API Key 掩码展示（前缀+后4位，如 sk-or-****3f8a） */
  apiKeyMasked: string
  /** 后端暂时返回字符串数组，前端兼容包装；TODO: 后端改为 GatewayModel[] */
  modelList: (GatewayModel | string)[]
  modelsSyncedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

/** 网关测试连接结果（GatewayTestResult schema，成功失败均 200） */
export interface GatewayTestResult {
  reachable: boolean
  availableModelsCount?: number | null
  errorCode?: number | null
  errorMessage?: string | null
  latencyMs?: number | null
}

/** AI 翻译请求体（TranslateRequest schema） */
export interface TranslateRequest {
  sourceLang: string
  targetLang: string
  sourceText: string
  customRequirement?: string | null
  model?: string | null
  bizType?: string | null
  bizRef?: string | null
}

/** token 用量（TokenUsage schema） */
export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

/** AI 翻译响应（TranslateResponse schema） */
export interface TranslateResponse {
  translatedText: string
  model: string
  latencyMs: number
  tokenUsage: TokenUsage
}

/** 术语表条目写入体（GlossaryTermUpsert schema） */
export interface GlossaryTermUpsert {
  termEn: string
  termEs?: string | null
  termFr?: string | null
  category?: string | null
  enabled: boolean
}

/** 术语表条目（GlossaryTerm schema） */
export interface GlossaryTerm extends GlossaryTermUpsert {
  id: number
  createdAt?: string | null
  updatedAt?: string | null
}

/** AI 翻译调用记录状态（与后端 AiTranslationStatus IntEnum 对齐，决策 10 / EDGE-016/017） */
export const AiTranslationStatus = {
  SUCCESS: 1,
  FAILED: 2,
  TIMEOUT: 3,
  EMPTY_RESULT: 4,
  RATE_LIMITED: 5,
} as const
export type AiTranslationStatus = typeof AiTranslationStatus[keyof typeof AiTranslationStatus]

/** AI 翻译调用记录条目（TranslationLogDto；source_text/translated_text 后端截断展示前 200 字符） */
export interface TranslationLog {
  id: number
  gatewayConfigId: number | null
  model: string | null
  sourceLang: string | null
  targetLang: string | null
  sourceText: string | null
  translatedText: string | null
  customRequirement: string | null
  bizType: string | null
  bizRef: string | null
  status: AiTranslationStatus
  errorMessage: string | null
  latencyMs: number | null
  operatorId: number | null
  createdAt: string | null
}
