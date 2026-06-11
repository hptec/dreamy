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
import type { Banner, BannerStatus } from '@/api/types'

const banner = (id: number, status: BannerStatus = 'draft'): Banner => ({
  id,
  name: `B${id}`,
  imageUrl: `https://cdn.example.com/b${id}.jpg`,
  position: 'hero',
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
    const b = banner(1, 'draft')
    store.list = [b]
    toggleBannerStatus.mockResolvedValue({ ...b, status: 'published' })
    await store.toggleStatus(b, 'published')
    expect(toggleBannerStatus).toHaveBeenCalledWith(1, 'published')
    expect(b.status).toBe('published')
  })

  it('published 关闭 Toggle → archived（下线成功路径）', async () => {
    const store = useBannersStore()
    const b = banner(2, 'published')
    toggleBannerStatus.mockResolvedValue({ ...b, status: 'archived' })
    await store.toggleStatus(b, 'archived')
    expect(toggleBannerStatus).toHaveBeenCalledWith(2, 'archived')
    expect(b.status).toBe('archived')
  })

  it('409703 失败 → 回滚 b.status 不变并抛 BizError（失败路径）', async () => {
    const store = useBannersStore()
    const b = banner(3, 'published')
    toggleBannerStatus.mockRejectedValue(new BizError(409703, '当前发布状态不允许该操作'))
    await expect(store.toggleStatus(b, 'archived')).rejects.toMatchObject({ code: 409703 })
    expect(b.status).toBe('published')
  })

  it('draft → published 失败同样回滚为 draft（Toggle 视觉态还原 off）', async () => {
    const store = useBannersStore()
    const b = banner(4, 'draft')
    toggleBannerStatus.mockRejectedValue(new BizError(409703, '当前发布状态不允许该操作'))
    await expect(store.toggleStatus(b, 'published')).rejects.toBeInstanceOf(BizError)
    expect(b.status).toBe('draft')
  })

  it('乐观更新：请求挂起期间 status 已切到目标态，失败后才还原', async () => {
    const store = useBannersStore()
    const b = banner(5, 'published')
    let reject!: (e: unknown) => void
    toggleBannerStatus.mockReturnValue(new Promise((_, r) => { reject = r }))
    const pending = store.toggleStatus(b, 'archived')
    expect(b.status).toBe('archived') // 乐观态
    reject(new BizError(409703, '当前发布状态不允许该操作'))
    await expect(pending).rejects.toBeInstanceOf(BizError)
    expect(b.status).toBe('published') // 回滚态
  })

  it('同态幂等：目标态与当前态一致时不发请求', async () => {
    const store = useBannersStore()
    const b = banner(6, 'published')
    await store.toggleStatus(b, 'published')
    expect(toggleBannerStatus).not.toHaveBeenCalled()
  })
})
