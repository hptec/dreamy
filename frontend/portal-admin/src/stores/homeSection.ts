// useHomeSectionStore：首页区块管理（HomeBuilder 页面）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listHomeSections,
  createHomeSection,
  updateHomeSection,
  deleteHomeSection,
  sortHomeSections,
  toggleHomeSection,
  type HomePageSection,
  type HomePageSectionUpsert,
  type SortItem,
} from '@/api/siteBuilder'

export const useHomeSectionStore = defineStore('homeSection', () => {
  const sections = ref<HomePageSection[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetch(enabledOnly?: boolean) {
    loading.value = true
    error.value = null
    try {
      const res = await listHomeSections(enabledOnly)
      sections.value = res.items
    } catch (e: any) {
      error.value = e.message ?? 'Failed to load home sections'
    } finally {
      loading.value = false
    }
  }

  async function create(upsert: HomePageSectionUpsert) {
    const created = await createHomeSection(upsert)
    sections.value.push(created)
    return created
  }

  async function update(id: number, upsert: HomePageSectionUpsert) {
    const updated = await updateHomeSection(id, upsert)
    const idx = sections.value.findIndex((s) => s.id === id)
    if (idx >= 0) sections.value[idx] = updated
    return updated
  }

  async function remove(id: number) {
    await deleteHomeSection(id)
    sections.value = sections.value.filter((s) => s.id !== id)
  }

  async function sort(items: SortItem[]) {
    await sortHomeSections(items)
    for (const item of items) {
      const idx = sections.value.findIndex((s) => s.id === item.id)
      if (idx >= 0) sections.value[idx].sortOrder = item.sortOrder
    }
    sections.value.sort((a, b) => a.sortOrder - b.sortOrder)
  }

  async function toggle(id: number, enabled: boolean) {
    const updated = await toggleHomeSection(id, enabled)
    const idx = sections.value.findIndex((s) => s.id === id)
    if (idx >= 0) sections.value[idx] = updated
    return updated
  }

  return { sections, loading, error, fetch, create, update, remove, sort, toggle }
})
