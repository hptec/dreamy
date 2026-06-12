// STORE-MKT-A05 useLookbookStore：lookbook + guide 双列表 CRUD 与状态流转
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { marketingApi } from '@/api'
import type { Guide, GuideUpsert, Lookbook, LookbookUpsert, PublishStatus } from '@/api/types'
import { normalizeEnumFilter } from '@/utils/validators'

export const useLookbookStore = defineStore('lookbook', () => {
  const lookbooks = ref<Lookbook[]>([])
  const guides = ref<Guide[]>([])
  const statusFilter = ref<PublishStatus | 'all'>('all')
  const loadingLookbooks = ref(false)
  const loadingGuides = ref(false)

  async function fetchLookbooks() {
    loadingLookbooks.value = true
    try {
      const res = await marketingApi.listLookbooks(normalizeEnumFilter(statusFilter.value))
      lookbooks.value = res.items
    } finally {
      loadingLookbooks.value = false
    }
  }

  async function fetchGuides() {
    loadingGuides.value = true
    try {
      const res = await marketingApi.listGuides(normalizeEnumFilter(statusFilter.value))
      guides.value = res.items
    } finally {
      loadingGuides.value = false
    }
  }

  async function saveLookbook(body: LookbookUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createLookbook(body) : await marketingApi.updateLookbook(id, body)
    await fetchLookbooks()
    return saved
  }

  async function removeLookbook(id: number) {
    await marketingApi.deleteLookbook(id)
    lookbooks.value = lookbooks.value.filter((l) => l.id !== id)
  }

  async function patchLookbookStatus(id: number, status: PublishStatus) {
    const updated = await marketingApi.patchLookbookStatus(id, status)
    const idx = lookbooks.value.findIndex((l) => l.id === id)
    if (idx >= 0) lookbooks.value[idx] = updated
    return updated
  }

  async function saveGuide(body: GuideUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createGuide(body) : await marketingApi.updateGuide(id, body)
    await fetchGuides()
    return saved
  }

  async function removeGuide(id: number) {
    await marketingApi.deleteGuide(id)
    guides.value = guides.value.filter((g) => g.id !== id)
  }

  async function patchGuideStatus(id: number, status: PublishStatus) {
    const updated = await marketingApi.patchGuideStatus(id, status)
    const idx = guides.value.findIndex((g) => g.id === id)
    if (idx >= 0) guides.value[idx] = updated
    return updated
  }

  return {
    lookbooks,
    guides,
    statusFilter,
    loadingLookbooks,
    loadingGuides,
    fetchLookbooks,
    fetchGuides,
    saveLookbook,
    removeLookbook,
    patchLookbookStatus,
    saveGuide,
    removeGuide,
    patchGuideStatus,
  }
})
