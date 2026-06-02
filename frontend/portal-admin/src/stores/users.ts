// STORE-A04 usersStore：用户列表分页/筛选/loading + 详情（PAGE-A02/A03）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { usersApi } from '@/api'
import type { UserDetail, UserListItem } from '@/api/types'

export const useUsersStore = defineStore('users', () => {
  const list = ref<UserListItem[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)

  // 筛选条件（FUNC-022）
  const filterStatus = ref('all')
  const filterTier = ref('all')
  const filterEmail = ref('')

  const detail = ref<UserDetail | null>(null)
  const detailLoading = ref(false)

  async function fetchList() {
    loading.value = true
    try {
      const res = await usersApi.listUsers({
        page: page.value,
        pageSize: pageSize.value,
        status: filterStatus.value === 'all' ? undefined : filterStatus.value,
        tier: filterTier.value === 'all' ? undefined : filterTier.value,
        email: filterEmail.value || undefined,
      })
      list.value = res.items
      total.value = res.total
    } finally {
      loading.value = false
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetchList()
  }

  function applyFilters() {
    page.value = 1
    return fetchList()
  }

  async function fetchDetail(id: string) {
    detailLoading.value = true
    try {
      detail.value = await usersApi.getUserDetail(id)
      return detail.value
    } finally {
      detailLoading.value = false
    }
  }

  async function toggleStatus(id: string, status: string) {
    await usersApi.toggleUserStatus(id, status)
    await fetchDetail(id)
  }

  async function forceLogout(id: string, scope: 'single' | 'all', sessionId?: string) {
    await usersApi.forceLogout(id, { scope, sessionId })
    await fetchDetail(id)
  }

  return {
    list,
    total,
    page,
    pageSize,
    loading,
    filterStatus,
    filterTier,
    filterEmail,
    detail,
    detailLoading,
    fetchList,
    setPage,
    applyFilters,
    fetchDetail,
    toggleStatus,
    forceLogout,
  }
})
