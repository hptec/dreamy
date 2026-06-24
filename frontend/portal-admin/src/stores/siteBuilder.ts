// useNavigationStore + useFooterStore + useAnnouncementStore（NavigationConfig 页面三 Tab）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getNavigation,
  saveNavigation,
  getFooter,
  saveFooter,
  listAnnouncements,
  createAnnouncement,
  updateAnnouncement,
  deleteAnnouncement,
  toggleAnnouncement,
  type NavigationItem,
  type NavigationItemUpsert,
  type FooterColumn,
  type FooterColumnUpsert,
  type Announcement,
  type AnnouncementUpsert,
} from '@/api/siteBuilder'

export const useNavigationStore = defineStore('navigation', () => {
  const items = ref<NavigationItem[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetch() {
    loading.value = true
    error.value = null
    try {
      const res = await getNavigation()
      items.value = res.items
    } catch (e: any) {
      error.value = e.message ?? 'Failed to load navigation'
    } finally {
      loading.value = false
    }
  }

  async function save(upserts: NavigationItemUpsert[]) {
    const res = await saveNavigation(upserts)
    items.value = res.items
    return res.items
  }

  return { items, loading, error, fetch, save }
})

export const useFooterStore = defineStore('footer', () => {
  const columns = ref<FooterColumn[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetch() {
    loading.value = true
    error.value = null
    try {
      const res = await getFooter()
      columns.value = res.columns
    } catch (e: any) {
      error.value = e.message ?? 'Failed to load footer'
    } finally {
      loading.value = false
    }
  }

  async function save(upserts: FooterColumnUpsert[]) {
    const res = await saveFooter(upserts)
    columns.value = res.columns
    return res.columns
  }

  return { columns, loading, error, fetch, save }
})

export const useAnnouncementStore = defineStore('announcement', () => {
  const announcements = ref<Announcement[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetch(enabledOnly?: boolean) {
    loading.value = true
    error.value = null
    try {
      const res = await listAnnouncements({ enabledOnly, page: 1, pageSize: 100 })
      announcements.value = res.data ?? []
    } catch (e: any) {
      error.value = e.message ?? 'Failed to load announcements'
    } finally {
      loading.value = false
    }
  }

  async function create(upsert: AnnouncementUpsert) {
    const created = await createAnnouncement(upsert)
    announcements.value.push(created)
    return created
  }

  async function update(id: number, upsert: AnnouncementUpsert) {
    const updated = await updateAnnouncement(id, upsert)
    const idx = announcements.value.findIndex((a) => a.id === id)
    if (idx >= 0) announcements.value[idx] = updated
    return updated
  }

  async function remove(id: number) {
    await deleteAnnouncement(id)
    announcements.value = announcements.value.filter((a) => a.id !== id)
  }

  async function toggle(id: number, enabled: boolean) {
    const updated = await toggleAnnouncement(id, enabled)
    const idx = announcements.value.findIndex((a) => a.id === id)
    if (idx >= 0) announcements.value[idx] = updated
    return updated
  }

  return { announcements, loading, error, fetch, create, update, remove, toggle }
})
