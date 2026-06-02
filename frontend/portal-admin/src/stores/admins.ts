// STORE-A04 adminsStore：管理员 CRUD 分页/筛选/loading（PAGE-A04）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminsApi } from '@/api'
import type { Admin, AdminCreatePayload, AdminUpdatePayload } from '@/api/types'

export const useAdminsStore = defineStore('admins', () => {
  const list = ref<Admin[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)

  const filterStatus = ref('')
  const filterRoleId = ref('')

  async function fetchList() {
    loading.value = true
    try {
      const res = await adminsApi.listAdmins({
        page: page.value,
        pageSize: pageSize.value,
        status: filterStatus.value || undefined,
        roleId: filterRoleId.value || undefined,
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

  async function create(payload: AdminCreatePayload) {
    const created = await adminsApi.createAdmin(payload)
    await fetchList()
    return created
  }

  async function update(id: string, payload: AdminUpdatePayload) {
    const updated = await adminsApi.updateAdmin(id, payload)
    await fetchList()
    return updated
  }

  async function remove(id: string) {
    await adminsApi.deleteAdmin(id)
    await fetchList()
  }

  async function toggleStatus(id: string, status: string) {
    await adminsApi.toggleAdminStatus(id, status)
    await fetchList()
  }

  async function resetPassword(id: string, newPassword: string) {
    await adminsApi.resetAdminPassword(id, newPassword)
  }

  return {
    list,
    total,
    page,
    pageSize,
    loading,
    filterStatus,
    filterRoleId,
    fetchList,
    setPage,
    applyFilters,
    create,
    update,
    remove,
    toggleStatus,
    resetPassword,
  }
})
