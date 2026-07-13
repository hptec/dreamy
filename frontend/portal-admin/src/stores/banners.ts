// STORE-MKT-A02 useBannersStore：列表 + Toggle 乐观更新（失败回滚）+ sort blur 整单提交
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { marketingApi } from '@/api'
import type { Banner, BannerPosition, BannerStatus, BannerUpsert } from '@/api/types'
import { normalizeEnumFilter } from '@/utils/validators'

function toUpsert(row: Banner): BannerUpsert {
  return {
    name: row.name,
    imageUrl: row.imageUrl,
    position: row.position,
    startTime: row.startTime,
    endTime: row.endTime,
    status: row.status,
    sort: row.sort,
    title: row.title,
    subtitle: row.subtitle,
    ctaText: row.ctaText,
    ctaLink: row.ctaLink,
    ctaTextSecondary: row.ctaTextSecondary,
    ctaLinkSecondary: row.ctaLinkSecondary,
    translations: row.translations,
  }
}

export const useBannersStore = defineStore('banners', () => {
  const list = ref<Banner[]>([])
  const positionFilter = ref<BannerPosition | 'all'>('all')
  const loading = ref(false)

  async function fetch() {
    loading.value = true
    try {
      const res = await marketingApi.listBanners(normalizeEnumFilter(positionFilter.value))
      list.value = res.items
    } finally {
      loading.value = false
    }
  }

  async function save(body: BannerUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createBanner(body) : await marketingApi.updateBanner(id, body)
    await fetch()
    return saved
  }

  async function remove(id: number) {
    await marketingApi.deleteBanner(id)
    list.value = list.value.filter((b) => b.id !== id)
  }

  /** FORM-MKT-A03：Toggle 乐观更新（同态幂等），失败回滚后抛错 */
  async function toggleStatus(row: Banner, status: BannerStatus) {
    const prev = row.status
    if (prev === status) return
    row.status = status
    try {
      const updated = await marketingApi.toggleBannerStatus(row.id, status)
      Object.assign(row, updated)
    } catch (e) {
      row.status = prev
      throw e
    }
  }

  /** COMP-MKT-A04：排序 blur 提交（updateBanner 整单——携带行现值） */
  async function patchSort(row: Banner, sort: number) {
    const prev = row.sort
    if (prev === sort) return
    row.sort = sort
    try {
      const updated = await marketingApi.updateBanner(row.id, { ...toUpsert(row), sort })
      Object.assign(row, updated)
    } catch (e) {
      row.sort = prev
      throw e
    }
  }

  return { list, positionFilter, loading, fetch, save, remove, toggleStatus, patchSort }
})
