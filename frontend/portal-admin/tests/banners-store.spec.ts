// STORE-MKT-A02 useBannersStore 单测：Toggle 三态映射回滚（ALIGN-035 / E-MKT-25 / FORM-MKT-A03）
// 设计依据：verify-pages-frontend-detail.md §D —— 乐观更新失败须还原 b.status，并测成功/失败两种路径
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listBanners = vi.fn()
const toggleBannerStatus = vi.fn()
const updateBanner = vi.fn()

vi.mock('@/api', () => ({
  marketingApi: {
    listBanners: (...args: unknown[]) => listBanners(...args),
    toggleBannerStatus: (...args: unknown[]) => toggleBannerStatus(...args),
    updateBanner: (...args: unknown[]) => updateBanner(...args),
    createBanner: vi.fn(),
    deleteBanner: vi.fn(),
  },
}))

import { useBannersStore } from '@/stores/banners'
import { BizError } from '@/api/client'
import { BannerPosition, BannerStatus } from '@/api/types'
import type { Banner } from '@/api/types'

const banner = (id: number, status: BannerStatus = BannerStatus.DRAFT): Banner => ({
  id,
  name: `B${id}`,
  imageUrl: `https://cdn.example.com/b${id}.jpg`,
  position: BannerPosition.HERO,
  startTime: '2026-06-01T00:00:00Z',
  endTime: '2026-06-30T23:59:59Z',
  status,
  sort: 0,
})

describe('useBannersStore.toggleStatus（E-MKT-25 三态映射）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('draft 开启 Toggle → published（开启即发布，成功路径合并服务端返回）', async () => {
    const store = useBannersStore()
    const b = banner(1, BannerStatus.DRAFT)
    store.list = [b]
    toggleBannerStatus.mockResolvedValue({ ...b, status: BannerStatus.PUBLISHED })
    await store.toggleStatus(b, BannerStatus.PUBLISHED)
    expect(toggleBannerStatus).toHaveBeenCalledWith(1, BannerStatus.PUBLISHED)
    expect(b.status).toBe(BannerStatus.PUBLISHED)
  })

  it('published 关闭 Toggle → archived（下线成功路径）', async () => {
    const store = useBannersStore()
    const b = banner(2, BannerStatus.PUBLISHED)
    toggleBannerStatus.mockResolvedValue({ ...b, status: BannerStatus.ARCHIVED })
    await store.toggleStatus(b, BannerStatus.ARCHIVED)
    expect(toggleBannerStatus).toHaveBeenCalledWith(2, BannerStatus.ARCHIVED)
    expect(b.status).toBe(BannerStatus.ARCHIVED)
  })

  it('409703 失败 → 回滚 b.status 不变并抛 BizError（失败路径）', async () => {
    const store = useBannersStore()
    const b = banner(3, BannerStatus.PUBLISHED)
    toggleBannerStatus.mockRejectedValue(new BizError(409703, '当前发布状态不允许该操作'))
    await expect(store.toggleStatus(b, BannerStatus.ARCHIVED)).rejects.toMatchObject({ code: 409703 })
    expect(b.status).toBe(BannerStatus.PUBLISHED)
  })

  it('draft → published 失败同样回滚为 draft（Toggle 视觉态还原 off）', async () => {
    const store = useBannersStore()
    const b = banner(4, BannerStatus.DRAFT)
    toggleBannerStatus.mockRejectedValue(new BizError(409703, '当前发布状态不允许该操作'))
    await expect(store.toggleStatus(b, BannerStatus.PUBLISHED)).rejects.toBeInstanceOf(BizError)
    expect(b.status).toBe(BannerStatus.DRAFT)
  })

  it('乐观更新：请求挂起期间 status 已切到目标态，失败后才还原', async () => {
    const store = useBannersStore()
    const b = banner(5, BannerStatus.PUBLISHED)
    let reject!: (e: unknown) => void
    toggleBannerStatus.mockReturnValue(new Promise((_, r) => { reject = r }))
    const pending = store.toggleStatus(b, BannerStatus.ARCHIVED)
    expect(b.status).toBe(BannerStatus.ARCHIVED) // 乐观态
    reject(new BizError(409703, '当前发布状态不允许该操作'))
    await expect(pending).rejects.toBeInstanceOf(BizError)
    expect(b.status).toBe(BannerStatus.PUBLISHED) // 回滚态
  })

  it('同态幂等：目标态与当前态一致时不发请求', async () => {
    const store = useBannersStore()
    const b = banner(6, BannerStatus.PUBLISHED)
    await store.toggleStatus(b, BannerStatus.PUBLISHED)
    expect(toggleBannerStatus).not.toHaveBeenCalled()
  })
})
