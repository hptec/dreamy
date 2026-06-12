// STORE-REV-A01 useReviewsStore：评价列表（含 pendingCount 角标）+ 审核/精选/批量/回复/图片驳回
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { reviewsApi } from '@/api'
import { ReviewModerationStatus } from '@/api/types'
import type { AdminReview } from '@/api/types'

export const useReviewsStore = defineStore('reviews', () => {
  const list = ref<AdminReview[]>([])
  const totalElements = ref(0)
  const pendingCount = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  // chips 映射：全部=all、待审核=pending、已通过=approved、精选=all&featured、已拒绝=rejected
  const chip = ref<'all' | 'pending' | 'approved' | 'featured' | 'rejected'>('all')
  /** chip（UI 态）→ 后端 ReviewStatus 整数（all/featured 不传 status） */
  const CHIP_STATUS: Record<string, ReviewModerationStatus | undefined> = {
    pending: ReviewModerationStatus.PENDING,
    approved: ReviewModerationStatus.APPROVED,
    rejected: ReviewModerationStatus.REJECTED,
  }
  const rating = ref('all')
  const search = ref('')
  const selectedIds = ref<number[]>([])

  async function fetch() {
    loading.value = true
    try {
      const res = await reviewsApi.listReviews({
        page: page.value,
        pageSize: pageSize.value,
        status: CHIP_STATUS[chip.value],
        featured: chip.value === 'featured' ? true : undefined,
        rating: rating.value === 'all' ? undefined : Number(rating.value),
        search: search.value.trim() || undefined,
      })
      list.value = res.data
      totalElements.value = res.totalElements
      pendingCount.value = res.pendingCount
    } finally {
      loading.value = false
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetch()
  }

  function applyFilters() {
    page.value = 1
    selectedIds.value = []
    return fetch()
  }

  function replaceRow(updated: AdminReview) {
    const idx = list.value.findIndex((r) => r.id === updated.id)
    if (idx >= 0) list.value[idx] = updated
  }

  /** FORM-REV-A01：单条审核；409802 由视图 toast + refetch */
  async function moderate(row: AdminReview, status: ReviewModerationStatus) {
    const updated = await reviewsApi.patchReviewStatus(row.id, status)
    replaceRow(updated)
    if (row.status === ReviewModerationStatus.PENDING && status !== ReviewModerationStatus.PENDING) {
      pendingCount.value = Math.max(0, pendingCount.value - 1)
    }
    return updated
  }

  /** FORM-REV-A03：精选乐观翻转，409803 回滚 */
  async function setFeatured(row: AdminReview, featured: boolean) {
    const prev = row.featured
    if (prev === featured) return row
    row.featured = featured
    try {
      const updated = await reviewsApi.patchReviewFeatured(row.id, featured)
      replaceRow(updated)
      return updated
    } catch (e) {
      row.featured = prev
      throw e
    }
  }

  /** FORM-REV-A02：批量后清选 + refetch；返回 {updatedIds, skippedIds} 供 toast 汇总 */
  async function batch(action: 'approve' | 'reject') {
    const result = await reviewsApi.batchReviews([...selectedIds.value], action)
    selectedIds.value = []
    await fetch()
    return result
  }

  async function saveReply(id: number, content: string) {
    const updated = await reviewsApi.putReviewReply(id, content)
    replaceRow(updated)
    return updated
  }

  async function removeReply(id: number) {
    const updated = await reviewsApi.deleteReviewReply(id)
    replaceRow(updated)
    return updated
  }

  /** FORM-REV-A05：图片驳回/恢复（成功后局部更新 images） */
  async function toggleImage(id: number, imageId: number, rejected: boolean) {
    const updated = await reviewsApi.patchReviewImage(id, imageId, rejected)
    replaceRow(updated)
    return updated
  }

  return {
    list,
    totalElements,
    pendingCount,
    page,
    pageSize,
    loading,
    chip,
    rating,
    search,
    selectedIds,
    fetch,
    setPage,
    applyFilters,
    moderate,
    setFeatured,
    batch,
    saveReply,
    removeReply,
    toggleImage,
  }
})
