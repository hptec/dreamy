// 表单校验纯函数单测（FORM-CAT-A01 / FORM-MKT-A01 / FORM-TRD-A01~A05 / FORM-SHP-01~02 镜像）
import { describe, expect, it } from 'vitest'
import {
  validateProductForm,
  validateCouponForm,
  validateCouponValue,
  validateFlashSaleForm,
  validateBannerForm,
  validateBlogForm,
  validateShipForm,
  validateAdminRefundForm,
  validateExchangeRate,
  validateCheckoutConfig,
  validateCarrierForm,
  validateRateForm,
  normalizeFilter,
  toIsoDateTime,
  dateToStartOfDay,
  dateToEndOfDay,
} from '@/utils/validators'

describe('validateProductForm（FORM-CAT-A01 预校验）', () => {
  const valid = {
    name: 'Aurelia Gown',
    slug: 'aurelia-gown',
    categoryId: 1,
    price: 299,
    compareAt: 399,
    leadTimeDays: 14,
    skus: [{ skuCode: 'DRM-IVO-2' }, { skuCode: 'DRM-IVO-4' }],
  }

  it('合法表单零错误', () => {
    expect(validateProductForm(valid)).toEqual({})
  })

  it('slug pattern 校验', () => {
    expect(validateProductForm({ ...valid, slug: 'Bad Slug!' }).slug).toBeTruthy()
    expect(validateProductForm({ ...valid, slug: 'ok-slug-2' }).slug).toBeUndefined()
  })

  it('compare_at >= price js_guard', () => {
    expect(validateProductForm({ ...valid, compareAt: 100 }).compareAt).toBe('划线价需不低于现价')
    expect(validateProductForm({ ...valid, compareAt: '' }).compareAt).toBeUndefined()
  })

  it('SKU 码非空且唯一', () => {
    expect(validateProductForm({ ...valid, skus: [{ skuCode: '' }] }).skus).toBe('SKU 码不能为空')
    expect(validateProductForm({ ...valid, skus: [{ skuCode: 'A' }, { skuCode: 'A' }] }).skus).toBe('SKU 码不能重复')
  })

  it('必填字段缺失逐项报错', () => {
    const errors = validateProductForm({ name: '', slug: '', categoryId: null, price: '', leadTimeDays: 0, skus: [] })
    expect(errors.name).toBeTruthy()
    expect(errors.slug).toBeTruthy()
    expect(errors.categoryId).toBeTruthy()
    expect(errors.price).toBeTruthy()
    expect(errors.leadTimeDays).toBeTruthy()
  })
})

describe('validateCouponForm（FORM-MKT-A01 / DEC-MKT-4）', () => {
  it('value 按 type 校验', () => {
    expect(validateCouponValue('discount', '15% OFF')).toBeNull()
    expect(validateCouponValue('discount', '$50 OFF')).toBeTruthy()
    expect(validateCouponValue('fixed_amount', '$50 OFF')).toBeNull()
    expect(validateCouponValue('fixed_amount', '50 OFF')).toBeTruthy()
    expect(validateCouponValue('free_shipping', 'Free Shipping')).toBeNull()
  })

  it('end > start js_guard', () => {
    const errors = validateCouponForm({
      code: 'WELCOME15',
      name: 'n',
      type: 'discount',
      value: '15% OFF',
      startAt: '2026-06-10T10:00',
      endAt: '2026-06-09T10:00',
    })
    expect(errors.endAt).toBe('结束时间需晚于开始时间')
  })

  it('券码 pattern（大写/数字/-_，2~32；小写输入自动归一不报错）', () => {
    expect(validateCouponForm({ code: 'BAD CODE!', name: 'n', type: 'discount', value: '15% OFF' }).code).toBeTruthy()
    expect(validateCouponForm({ code: 'X', name: 'n', type: 'discount', value: '15% OFF' }).code).toBeTruthy()
    expect(validateCouponForm({ code: 'ok-code', name: 'n', type: 'discount', value: '15% OFF' }).code).toBeUndefined()
    expect(validateCouponForm({ code: 'OK-CODE_1', name: 'n', type: 'discount', value: '15% OFF' }).code).toBeUndefined()
  })
})

describe('validateFlashSaleForm / validateBannerForm / validateBlogForm', () => {
  it('闪购 start/end 必填 + js_guard', () => {
    expect(validateFlashSaleForm({ name: 'f', discount: '-30%', startAt: '', endAt: '' }).startAt).toBeTruthy()
    expect(
      validateFlashSaleForm({ name: 'f', discount: '-30%', startAt: '2026-06-10T10:00', endAt: '2026-06-10T09:00' }).endAt,
    ).toBeTruthy()
  })

  it('Banner image/position 必填，时间窗 js_guard', () => {
    const errors = validateBannerForm({ name: '', imageUrl: '', position: '', startTime: '2026-06-10T10:00', endTime: '2026-06-09T10:00' })
    expect(errors.name).toBeTruthy()
    expect(errors.imageUrl).toBeTruthy()
    expect(errors.position).toBeTruthy()
    expect(errors.endTime).toBeTruthy()
  })

  it('Blog published 时 slug 必填（FORM-MKT-A04 预判）', () => {
    expect(validateBlogForm({ title: 't', slug: '', status: 'published' }).slug).toBe('发布前需填写 slug')
    expect(validateBlogForm({ title: 't', slug: '', status: 'draft' }).slug).toBeUndefined()
  })
})

describe('trading 校验（FORM-TRD-A01/A02/A04/A05）', () => {
  it('发货 carrier 必选 + trackingNo 必填 ≤64', () => {
    expect(validateShipForm({ carrier: '', trackingNo: ' ' })).toMatchObject({ carrier: expect.any(String), trackingNo: expect.any(String) })
    expect(validateShipForm({ carrier: 'FedEx International Priority', trackingNo: 'TRK123' })).toEqual({})
    expect(validateShipForm({ carrier: 'DHL Express', trackingNo: 'x'.repeat(65) }).trackingNo).toBeTruthy()
  })

  it('代客退款 amount>0 且 ≤totalAmount、reason 必填 ≤255', () => {
    expect(validateAdminRefundForm({ amount: 0, reason: '' }, 100)).toMatchObject({ amount: expect.any(String), reason: expect.any(String) })
    expect(validateAdminRefundForm({ amount: 150, reason: 'r' }, 100).amount).toBe('超出可退上限')
    expect(validateAdminRefundForm({ amount: 50, reason: 'r' }, 100)).toEqual({})
  })

  it('汇率 rate>0', () => {
    expect(validateExchangeRate(0)).toBeTruthy()
    expect(validateExchangeRate('')).toBeTruthy()
    expect(validateExchangeRate(0.92)).toBeNull()
  })

  it('结算配置 fee≥0、grace 1..168', () => {
    expect(validateCheckoutConfig({ giftWrapFeeUsd: -1, customRefundGraceHours: 24 }).giftWrapFeeUsd).toBeTruthy()
    expect(validateCheckoutConfig({ giftWrapFeeUsd: 15, customRefundGraceHours: 169 }).customRefundGraceHours).toBeTruthy()
    expect(validateCheckoutConfig({ giftWrapFeeUsd: 15, customRefundGraceHours: 24 })).toEqual({})
  })
})

describe('shipping 校验（V-SHP-003~010 镜像）', () => {
  it('承运方 name ≤64 必填、status 枚举', () => {
    expect(validateCarrierForm({ name: '', status: 'enabled' }).name).toBeTruthy()
    expect(validateCarrierForm({ name: 'FedEx', status: 'bad' }).status).toBeTruthy()
    expect(validateCarrierForm({ name: 'FedEx', status: 'enabled' })).toEqual({})
  })

  it('规则行 zone 必填、费用两位小数', () => {
    expect(validateRateForm({ zone: '', feeUnder: '12.5' }).zone).toBeTruthy()
    expect(validateRateForm({ zone: 'US', feeUnder: '12.345' }).feeUnder).toBeTruthy()
    expect(validateRateForm({ zone: 'US', feeUnder: '12.34', feeOver: '0', threshold: '' })).toEqual({})
  })
})

describe('分页/筛选参数纯函数', () => {
  it("normalizeFilter：'all'/空 → undefined", () => {
    expect(normalizeFilter('all')).toBeUndefined()
    expect(normalizeFilter('')).toBeUndefined()
    expect(normalizeFilter(null)).toBeUndefined()
    expect(normalizeFilter('published')).toBe('published')
  })

  it('datetime-local 补秒 / date 起止扩展', () => {
    expect(toIsoDateTime('2026-06-10T10:00')).toBe('2026-06-10T10:00:00')
    expect(toIsoDateTime('')).toBeUndefined()
    expect(dateToStartOfDay('2026-06-10')).toBe('2026-06-10T00:00:00')
    expect(dateToEndOfDay('2026-06-10')).toBe('2026-06-10T23:59:59')
  })
})
