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
  saveHomeSections,
  type HomePageSection,
  type HomePageSectionUpsert,
  type SortItem,
  type HomePageSaveItem,
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

  async function saveAll(items: HomePageSaveItem[]) {
    const result = await saveHomeSections(items)
    sections.value = result.items
    return result.items
  }

  async function remove(id: number) {
    await deleteHomeSection(id)
    sections.value = sections.value.filter((s) => s.id !== id)
  }

  async function sort(items: SortItem[]) {
    await sortHomeSections(items)
    // 后端排序会同时递增每行 optimistic version；重新拉取，避免下一次保存携带旧版本。
    await fetch()
  }

  async function toggle(id: number, enabled: boolean) {
    const updated = await toggleHomeSection(id, enabled)
    const idx = sections.value.findIndex((s) => s.id === id)
    if (idx >= 0) sections.value[idx] = updated
    return updated
  }

  return { sections, loading, error, fetch, create, update, saveAll, remove, sort, toggle }
})
