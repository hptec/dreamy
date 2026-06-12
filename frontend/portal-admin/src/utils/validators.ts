// 表单前端预校验纯函数（FORM-CAT-A01 / FORM-MKT-A01~A04 / FORM-TRD-A01~A05 / FORM-SHP-01~02 镜像）
// 纯函数无副作用，Vitest 可测（任务产出要求：表单校验单测）

import { BizError } from '@/api/client'

export type FieldErrors = Record<string, string>

/** 422 details.fields reason_key → 中文兜底文案（后端已有 message 时优先 message） */
const REASON_LABEL: Record<string, string> = {
  required: '必填',
  invalid: '格式不正确',
  invalid_enum: '取值不合法',
  too_long: '超出长度限制',
  too_short: '长度不足',
  duplicate: '已存在',
  out_of_range: '超出可用范围',
  pattern: '格式不正确',
  not_found: '引用的数据不存在',
}

export function reasonLabel(key: string): string {
  return REASON_LABEL[key] || key
}

/** 从 BizError.details 提取 { fields: { snakeField: reasonKey } } → camel 字段名 → 中文 */
export function extractFieldErrors(e: unknown): FieldErrors {
  if (!(e instanceof BizError) || !e.details) return {}
  const fields = (e.details as Record<string, unknown>).fields
  if (!fields || typeof fields !== 'object') return {}
  const out: FieldErrors = {}
  for (const [k, v] of Object.entries(fields as Record<string, string>)) {
    const camel = k.replace(/_([a-z0-9])/g, (_m, c: string) => c.toUpperCase())
    out[camel] = reasonLabel(String(v))
  }
  return out
}

export function isBizCode(e: unknown, code: number): boolean {
  return e instanceof BizError && e.code === code
}

// ===== catalog（FORM-CAT-A01） =====

export const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

/** V-CAT-053 镜像：属性定义 key（小写字母开头，仅小写字母/数字/下划线，≤64） */
export const ATTR_KEY_PATTERN = /^[a-z][a-z0-9_]*$/

export function validateProductForm(form: {
  name?: string | null
  slug?: string | null
  categoryId?: number | null
  price?: number | string | null
  compareAt?: number | string | null
  leadTimeDays?: number | null
  skus?: { skuCode?: string | null }[]
}): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.name?.trim()) errors.name = '商品名称必填'
  if (!form.slug?.trim()) errors.slug = 'slug 必填'
  else if (!SLUG_PATTERN.test(form.slug.trim())) errors.slug = 'slug 仅支持小写字母、数字与中划线'
  if (!form.categoryId) errors.categoryId = '请选择标准品类'
  const price = Number(form.price)
  if (form.price == null || form.price === '' || Number.isNaN(price) || price <= 0) {
    errors.price = '现价必填且需大于 0'
  }
  if (form.compareAt != null && form.compareAt !== '') {
    const compareAt = Number(form.compareAt)
    if (Number.isNaN(compareAt) || compareAt < 0) errors.compareAt = '划线价不合法'
    else if (!errors.price && compareAt > 0 && compareAt < price) errors.compareAt = '划线价需不低于现价'
  }
  if (form.leadTimeDays == null || form.leadTimeDays < 1) errors.leadTimeDays = '发货周期必填（≥1 天）'
  const codes = (form.skus || []).map((s) => (s.skuCode || '').trim())
  if (codes.some((c) => !c)) errors.skus = 'SKU 码不能为空'
  else if (new Set(codes).size !== codes.length) errors.skus = 'SKU 码不能重复'
  return errors
}

// ===== marketing（FORM-MKT-A01/A02） =====

export const COUPON_CODE_PATTERN = /^[A-Z0-9_-]{2,32}$/

/** value 按 type 校验（DEC-MKT-4：discount→'15% OFF' 型，fixed_amount→'$50 OFF' 型，free_shipping 任意 ≤32） */
export function validateCouponValue(type: string, value: string): string | null {
  const v = (value || '').trim()
  if (!v) return '面额必填'
  if (v.length > 32) return '不超过 32 字符'
  if (type === 'discount' && !/^\d{1,2}%/.test(v)) return "折扣类格式如 '15% OFF'"
  if (type === 'fixed_amount' && !/^\$\d+/.test(v)) return "满减类格式如 '$50 OFF'"
  return null
}

export function validateCouponForm(form: {
  code?: string | null
  name?: string | null
  type?: string | null
  value?: string | null
  startAt?: string | null
  endAt?: string | null
}): FieldErrors {
  const errors: FieldErrors = {}
  const code = (form.code || '').trim().toUpperCase()
  if (!code) errors.code = '券码必填'
  else if (!COUPON_CODE_PATTERN.test(code)) errors.code = '仅支持大写字母、数字、-、_（2~32 位）'
  if (!form.name?.trim()) errors.name = '名称必填'
  if (!form.type) errors.type = '请选择类型'
  const valueError = validateCouponValue(form.type || '', form.value || '')
  if (valueError) errors.value = valueError
  if (form.startAt && form.endAt && form.endAt <= form.startAt) errors.endAt = '结束时间需晚于开始时间'
  return errors
}

export function validateFlashSaleForm(form: {
  name?: string | null
  discount?: string | null
  startAt?: string | null
  endAt?: string | null
}): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.name?.trim()) errors.name = '活动名称必填'
  if (!form.discount?.trim()) errors.discount = '折扣描述必填'
  if (!form.startAt) errors.startAt = '开始时间必填'
  if (!form.endAt) errors.endAt = '结束时间必填'
  else if (form.startAt && form.endAt <= form.startAt) errors.endAt = '结束时间需晚于开始时间'
  return errors
}

export function validateBannerForm(form: {
  name?: string | null
  imageUrl?: string | null
  position?: string | null
  startTime?: string | null
  endTime?: string | null
}): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.name?.trim()) errors.name = '名称必填'
  if (!form.imageUrl?.trim()) errors.imageUrl = '请上传 Banner 图'
  if (!form.position) errors.position = '请选择广告位置'
  if (form.startTime && form.endTime && form.endTime <= form.startTime) {
    errors.endTime = '下线时间需晚于上线时间'
  }
  return errors
}

/** FORM-MKT-A04：发布前 slug 预判（published 必填 + pattern） */
export function validateBlogForm(form: {
  title?: string | null
  slug?: string | null
  status?: string | null
}): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.title?.trim()) errors.title = '标题必填'
  const slug = (form.slug || '').trim()
  if (slug && !SLUG_PATTERN.test(slug)) errors.slug = 'slug 仅支持小写字母、数字与中划线'
  if (form.status === 'published' && !slug) errors.slug = '发布前需填写 slug'
  return errors
}

// ===== trading（FORM-TRD-A01~A05） =====

export function validateShipForm(
  form: { carrier?: string | null; trackingNo?: string | null },
  hasCarriers = true,
): FieldErrors {
  const errors: FieldErrors = {}
  // COMP-TRD-D02 空列表兜底（ALIGN-022，关联 s-879 前置条件）：未配置启用承运方时给出可操作指引
  if (!hasCarriers) errors.carrier = '请先在物流配置启用承运方'
  else if (!form.carrier) errors.carrier = '请选择承运方'
  const trackingNo = (form.trackingNo || '').trim()
  if (!trackingNo) errors.trackingNo = '运单号必填'
  else if (trackingNo.length > 64) errors.trackingNo = '运单号不超过 64 字符'
  return errors
}

export function validateAdminRefundForm(
  form: { amount?: number | string | null; reason?: string | null },
  totalAmount: number,
): FieldErrors {
  const errors: FieldErrors = {}
  const amount = Number(form.amount)
  if (form.amount == null || form.amount === '' || Number.isNaN(amount) || amount <= 0) {
    errors.amount = '退款金额需大于 0'
  } else if (amount > totalAmount) {
    errors.amount = '超出可退上限'
  }
  const reason = (form.reason || '').trim()
  if (!reason) errors.reason = '退款原因必填'
  else if (reason.length > 255) errors.reason = '不超过 255 字符'
  return errors
}

export function validateExchangeRate(rate: number | string | null | undefined): string | null {
  const r = Number(rate)
  if (rate == null || rate === '' || Number.isNaN(r) || r <= 0) return '汇率需为大于 0 的数值'
  return null
}

export function validateCheckoutConfig(form: {
  giftWrapFeeUsd?: number | string | null
  customRefundGraceHours?: number | null
}): FieldErrors {
  const errors: FieldErrors = {}
  const fee = Number(form.giftWrapFeeUsd)
  if (form.giftWrapFeeUsd == null || form.giftWrapFeeUsd === '' || Number.isNaN(fee) || fee < 0) {
    errors.giftWrapFeeUsd = '礼品包装费需 ≥ 0'
  }
  const grace = Number(form.customRefundGraceHours)
  if (!Number.isInteger(grace) || grace < 1 || grace > 168) {
    errors.customRefundGraceHours = '宽限期需为 1~168 小时'
  }
  return errors
}

// ===== shipping（FORM-SHP-01/02 镜像 V-SHP-003~010） =====

export function validateCarrierForm(form: {
  name?: string | null
  zones?: string | null
  leadTime?: string | null
  status?: string | null
}): FieldErrors {
  const errors: FieldErrors = {}
  const name = (form.name || '').trim()
  if (!name) errors.name = '承运方名称必填'
  else if (name.length > 64) errors.name = '不超过 64 字符'
  if ((form.zones || '').length > 255) errors.zones = '不超过 255 字符'
  if ((form.leadTime || '').length > 64) errors.leadTime = '不超过 64 字符'
  if (form.status !== 'enabled' && form.status !== 'disabled') errors.status = '请选择状态'
  return errors
}

function validFee(v: number | string | null | undefined): boolean {
  if (v == null || v === '') return true
  const n = Number(v)
  if (Number.isNaN(n) || n < 0) return false
  return /^\d+(\.\d{1,2})?$/.test(String(v))
}

export function validateRateForm(form: {
  zone?: string | null
  feeUnder?: number | string | null
  feeOver?: number | string | null
  threshold?: number | string | null
}): FieldErrors {
  const errors: FieldErrors = {}
  const zone = (form.zone || '').trim()
  if (!zone) errors.zone = '区域必填'
  else if (zone.length > 128) errors.zone = '不超过 128 字符'
  if (!validFee(form.feeUnder)) errors.feeUnder = '金额需 ≥ 0 且最多两位小数'
  if (!validFee(form.feeOver)) errors.feeOver = '金额需 ≥ 0 且最多两位小数'
  if (!validFee(form.threshold)) errors.threshold = '金额需 ≥ 0 且最多两位小数'
  return errors
}

// ===== 分页参数（STORE 层复用；Vitest 可测） =====

/** 'all' 哨兵值与空串转 undefined，避免污染查询串 */
export function normalizeFilter(value: string | number | null | undefined): string | undefined {
  if (value == null) return undefined
  const s = String(value).trim()
  if (!s || s === 'all') return undefined
  return s
}

/** datetime-local（YYYY-MM-DDTHH:mm）→ 后端 LocalDateTime ISO（补秒） */
export function toIsoDateTime(value?: string | null): string | undefined {
  if (!value) return undefined
  return value.length === 16 ? `${value}:00` : value
}

/** date（YYYY-MM-DD）→ 起始/截止 LocalDateTime */
export function dateToStartOfDay(value?: string | null): string | undefined {
  return value ? `${value}T00:00:00` : undefined
}
export function dateToEndOfDay(value?: string | null): string | undefined {
  return value ? `${value}T23:59:59` : undefined
}
