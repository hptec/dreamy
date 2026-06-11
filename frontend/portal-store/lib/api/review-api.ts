/**
 * review 域 — PDP 评价/Q&A（review-frontend B.1）。
 * 读端点匿名（首屏 RSC + 翻页客户端）；写端点经 client.ts 401→refresh 重放链路。
 * 买家秀图片：presign → PUT 直传（FLOW-P17，不经后端）。
 */

import { request } from './client'
import { serverGet } from './server-fetch'
import type {
  MyReview,
  Paginated,
  PresignResponse,
  ReviewSort,
  StoreQuestion,
  StoreReview,
  StoreReviewListResponse
} from './store-types'

// ===== 服务端首屏（随 PDP ISR） =====

/** E-REV-01 评价列表 首屏（RSC，revalidate 300） */
export function fetchStoreReviewsServer(
  productId: number,
  params: { sort?: ReviewSort; page?: number; pageSize?: number } = {}
): Promise<StoreReviewListResponse | null> {
  return serverGet<StoreReviewListResponse>('/api/store/reviews', {
    revalidate: 300,
    query: { productId, ...params }
  })
}

/** E-REV-03 Q&A 列表 首屏（RSC） */
export function fetchStoreQuestionsServer(
  productId: number,
  params: { page?: number; pageSize?: number } = {}
): Promise<Paginated<StoreQuestion> | null> {
  return serverGet<Paginated<StoreQuestion>>('/api/store/questions', {
    revalidate: 300,
    query: { productId, ...params }
  })
}

// ===== 客户端（翻页/排序/提交） =====

/** E-REV-01 评价列表（翻页/排序客户端直连，匿名） */
export function fetchStoreReviews(
  productId: number,
  params: { sort?: ReviewSort; page?: number; pageSize?: number } = {}
): Promise<StoreReviewListResponse> {
  return request<StoreReviewListResponse>('/api/store/reviews', { query: { productId, ...params } })
}

/** E-REV-02 提交评价（403801 资格 / 409801 已评价） */
export function createStoreReview(body: {
  productId: number
  rating: number
  content?: string
  images?: { url: string }[]
}): Promise<StoreReview> {
  return request<StoreReview>('/api/store/reviews', { method: 'POST', auth: true, body })
}

/** E-REV-03 Q&A 列表（翻页客户端） */
export function fetchStoreQuestions(
  productId: number,
  params: { page?: number; pageSize?: number } = {}
): Promise<Paginated<StoreQuestion>> {
  return request<Paginated<StoreQuestion>>('/api/store/questions', { query: { productId, ...params } })
}

/** E-REV-04 提交提问（默认 hidden 待回答） */
export function createStoreQuestion(productId: number, question: string): Promise<StoreQuestion> {
  return request<StoreQuestion>('/api/store/questions', {
    method: 'POST',
    auth: true,
    body: { productId, question }
  })
}

/** F-049 我的评价列表（GET /api/store/reviews/mine，按当前 user_id 过滤，分页） */
export function listMyReviews(
  params: { page?: number; pageSize?: number } = {}
): Promise<Paginated<MyReview>> {
  return request<Paginated<MyReview>>('/api/store/reviews/mine', { auth: true, query: params })
}

/** E-REV-05 买家秀预签名（502801 → 上传降级提示） */
export function presignReviewUpload(fileName: string, contentType: string): Promise<PresignResponse> {
  return request<PresignResponse>('/api/store/uploads/presign', {
    method: 'POST',
    auth: true,
    body: { fileName, contentType }
  })
}

/** presign → PUT 直传（FLOW-P17 不经后端）→ public_url */
export async function uploadReviewImage(file: File): Promise<string> {
  const presign = await presignReviewUpload(file.name, file.type)
  const res = await fetch(presign.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file
  })
  if (!res.ok) throw new Error('upload failed')
  return presign.publicUrl
}
