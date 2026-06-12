// STORE-REV-A01 useReviewsStore 单测：chips→参数映射 / 批量清选 / 精选乐观回滚
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listReviews = vi.fn()
const batchReviews = vi.fn()
const patchReviewFeatured = vi.fn()

vi.mock('@/api', () => ({
  reviewsApi: {
    listReviews: (...args: unknown[]) => listReviews(...args),
    batchReviews: (...args: unknown[]) => batchReviews(...args),
    patchReviewFeatured: (...args: unknown[]) => patchReviewFeatured(...args),
    patchReviewStatus: vi.fn(),
    putReviewReply: vi.fn(),
    deleteReviewReply: vi.fn(),
    patchReviewImage: vi.fn(),
  },
}))

import { useReviewsStore } from '@/stores/reviews'
import { ReviewModerationStatus } from '@/api/types'
import type { AdminReview } from '@/api/types'

const page = (data: AdminReview[] = [], pendingCount = 0) => ({
  data,
  totalElements: data.length,
  pageNumber: 1,
  pageSize: 10,
  totalPages: 1,
  numberOfElements: data.length,
  pendingCount,
})

const review = (id: number, status: AdminReview['status'] = ReviewModerationStatus.APPROVED): AdminReview => ({
  id,
  productId: 1,
  rating: 5,
  status,
  featured: false,
  images: [],
})

describe('useReviewsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it("chips 映射：featured → status 不下发 + featured=true（COMP-REV-A02）", async () => {
    listReviews.mockResolvedValue(page())
    const store = useReviewsStore()
    store.chip = 'featured'
    await store.fetch()
    expect(listReviews).toHaveBeenCalledWith(
      expect.objectContaining({ status: undefined, featured: true }),
    )
  })

  it("chips 映射：pending → status=pending；pendingCount 平铺解出（MAP-REV-007）", async () => {
    listReviews.mockResolvedValue(page([], 7))
    const store = useReviewsStore()
    store.chip = 'pending'
    await store.fetch()
    expect(listReviews).toHaveBeenCalledWith(expect.objectContaining({ status: ReviewModerationStatus.PENDING, featured: undefined }))
    expect(store.pendingCount).toBe(7)
  })

  it('batch 提交后清空选择并 refetch（FORM-REV-A02）', async () => {
    listReviews.mockResolvedValue(page())
    batchReviews.mockResolvedValue({ updatedIds: [1, 2], skippedIds: [3] })
    const store = useReviewsStore()
    store.selectedIds = [1, 2, 3]
    const result = await store.batch('approve')
    expect(batchReviews).toHaveBeenCalledWith([1, 2, 3], 'approve')
    expect(store.selectedIds).toEqual([])
    expect(result.skippedIds).toEqual([3])
  })

  it('setFeatured 乐观翻转失败回滚（FORM-REV-A03 / 409803）', async () => {
    const store = useReviewsStore()
    const r = review(1)
    store.list = [r]
    patchReviewFeatured.mockRejectedValue(new Error('409803'))
    await expect(store.setFeatured(r, true)).rejects.toThrow()
    expect(r.featured).toBe(false)
  })
})
