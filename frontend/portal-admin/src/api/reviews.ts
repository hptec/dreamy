// review 域后台 API（PAGE-REV-A01；E-REV-06~15）
import { get, post, put, patch, del } from './client'
import type {
  AdminQuestion,
  AdminQuestionPage,
  AdminReview,
  AdminReviewPage,
  BatchResult,
  QuestionVisible,
  ReviewImage,
  ReviewModerationStatus,
} from './types'

export function listReviews(params: {
  page?: number
  pageSize?: number
  status?: ReviewModerationStatus
  rating?: number
  featured?: boolean
  productId?: number
  search?: string
}): Promise<AdminReviewPage> {
  return get<AdminReviewPage>('/api/admin/reviews', { params })
}

export function patchReviewStatus(id: number, status: ReviewModerationStatus): Promise<AdminReview> {
  return patch<AdminReview>(`/api/admin/reviews/${id}/status`, { status })
}

export function patchReviewFeatured(id: number, featured: boolean): Promise<AdminReview> {
  return patch<AdminReview>(`/api/admin/reviews/${id}/featured`, { featured })
}

export function batchReviews(ids: number[], action: 'approve' | 'reject'): Promise<BatchResult> {
  return post<BatchResult>('/api/admin/reviews/batch', { ids, action })
}

export function putReviewReply(id: number, replyContent: string): Promise<AdminReview> {
  return put<AdminReview>(`/api/admin/reviews/${id}/reply`, { replyContent })
}

export function deleteReviewReply(id: number): Promise<AdminReview> {
  return del<AdminReview>(`/api/admin/reviews/${id}/reply`)
}

export function patchReviewImage(id: number, imageId: number, rejected: boolean): Promise<ReviewImage> {
  return patch<ReviewImage>(`/api/admin/reviews/${id}/images/${imageId}`, { rejected })
}

export function listQuestions(params: {
  page?: number
  pageSize?: number
  productId?: number
  answered?: string
  search?: string
}): Promise<AdminQuestionPage> {
  return get<AdminQuestionPage>('/api/admin/questions', { params })
}

export function putQuestionAnswer(id: number, answer: string): Promise<AdminQuestion> {
  return put<AdminQuestion>(`/api/admin/questions/${id}/answer`, { answer })
}

export function deleteQuestionAnswer(id: number): Promise<void> {
  return del<void>(`/api/admin/questions/${id}/answer`)
}

export function patchQuestionVisibility(id: number, visible: QuestionVisible): Promise<AdminQuestion> {
  return patch<AdminQuestion>(`/api/admin/questions/${id}/visibility`, { visible })
}

export function batchQuestions(ids: number[], action: 'hide' | 'show'): Promise<BatchResult> {
  return post<BatchResult>('/api/admin/questions/batch', { ids, action })
}
