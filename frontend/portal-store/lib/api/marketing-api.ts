/**
 * marketing 域 — 客户端端点（lib/api/marketing-api.ts B.1 客户端子集）。
 * E-MKT-07 Lookbook 详情（页内展开拉关联商品）/ E-MKT-10 券校验 / E-MKT-11 Newsletter / E-MKT-12 联系表单。
 */

import { request } from './client'
import type { CouponValidateResponse, StoreLookbook } from './store-types'

/** E-MKT-07 Lookbook 详情（COMP-MKT-S07 卡片展开取关联商品） */
export function getStoreLookbook(id: number): Promise<StoreLookbook> {
  return request<StoreLookbook>(`/api/store/content/lookbooks/${id}`)
}

/** E-MKT-10 券校验（StoreBearerAuth；不可用 200+valid=false+reason_code 不抛错） */
export function validateCoupon(code: string, subtotal: number): Promise<CouponValidateResponse> {
  return request<CouponValidateResponse>('/api/store/promotions/coupons/validate', {
    method: 'POST',
    auth: true,
    body: { code, subtotal }
  })
}

/** E-MKT-11 Newsletter 订阅（决策 26 纯订阅确认；source=footer/modal/exit_intent） */
export async function subscribeNewsletter(
  email: string,
  source: 'footer' | 'modal' | 'exit_intent',
  locale: 'en' | 'es' | 'fr'
): Promise<boolean> {
  const res = await request<{ subscribed: boolean }>('/api/store/newsletter', {
    method: 'POST',
    body: { email, source, locale }
  })
  return res.subscribed
}

/** E-MKT-12 联系表单（决策 30 落表） */
export async function submitContactMessage(input: {
  name: string
  email: string
  subject?: string
  message: string
}): Promise<boolean> {
  const res = await request<{ submitted: boolean }>('/api/store/contact', {
    method: 'POST',
    body: input
  })
  return res.submitted
}
