// STORE-MKT-A03 useBlogStore：分页/筛选 + openEdit 全量回读 + 状态流转
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { marketingApi } from '@/api'
import type { BlogPost, BlogPostUpsert, ContentStatus } from '@/api/types'
import { normalizeEnumFilter } from '@/utils/validators'

export const useBlogStore = defineStore('blog', () => {
  const list = ref<BlogPost[]>([])
  const totalElements = ref(0)
  const page = ref(1)
  const pageSize = ref(9)
  const filterStatus = ref<ContentStatus | 'all'>('all')
  const search = ref('')
  const loading = ref(false)
  const editing = ref<BlogPost | null>(null)
  const editingLoading = ref(false)

  async function fetch() {
    loading.value = true
    try {
      const res = await marketingApi.listBlogs({
        page: page.value,
        pageSize: pageSize.value,
        status: normalizeEnumFilter(filterStatus.value),
        search: search.value.trim() || undefined,
      })
      list.value = res.data
      totalElements.value = res.totalElements
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
    return fetch()
  }

  /** id 给定先 getBlog 全量回读（含正文/translations） */
  async function openEdit(id?: number) {
    if (id == null) {
      editing.value = null
      return null
    }
    editingLoading.value = true
    try {
      editing.value = await marketingApi.getBlog(id)
      return editing.value
    } finally {
      editingLoading.value = false
    }
  }

  async function save(body: BlogPostUpsert, id?: number) {
    const saved = id == null ? await marketingApi.createBlog(body) : await marketingApi.updateBlog(id, body)
    await fetch()
    return saved
  }

  async function remove(id: number) {
    await marketingApi.deleteBlog(id)
    await fetch()
  }

  /** 发布 / 下线 / 重新发布（FORM-MKT-A04：slug 空 422704 由视图预判 + 兜底） */
  async function patchStatus(id: number, status: ContentStatus) {
    const updated = await marketingApi.patchBlogStatus(id, status)
    const idx = list.value.findIndex((b) => b.id === id)
    if (idx >= 0) list.value[idx] = updated
    return updated
  }

  return {
    list,
    totalElements,
    page,
    pageSize,
    filterStatus,
    search,
    loading,
    editing,
    editingLoading,
    fetch,
    setPage,
    applyFilters,
    openEdit,
    save,
    remove,
    patchStatus,
  }
})
