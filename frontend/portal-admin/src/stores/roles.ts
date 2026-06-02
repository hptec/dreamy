// STORE-A04 rolesStore：角色列表 + 权限字典 + 矩阵保存（PAGE-A05）
// 约束: FORM-A03 保存后 fetchMe 重渲菜单（FLOW-11）；EDGE-019 is_locked 禁编辑；FORM-A04 删角色
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { rolesApi } from '@/api'
import type { Permission, Role } from '@/api/types'
import { useAuthStore } from './auth'

export interface PermissionGroup {
  group: string
  items: Permission[]
}

export const useRolesStore = defineStore('roles', () => {
  const roles = ref<Role[]>([])
  const permissions = ref<Permission[]>([])
  const loading = ref(false)

  async function fetchRoles() {
    const res = await rolesApi.listRoles()
    roles.value = res.items
  }

  async function fetchPermissions() {
    const res = await rolesApi.listPermissions()
    permissions.value = res.items
  }

  async function fetchAll() {
    loading.value = true
    try {
      await Promise.all([fetchRoles(), fetchPermissions()])
    } finally {
      loading.value = false
    }
  }

  // 按 group 聚合权限字典（保持后端返回顺序）
  function groupedPermissions(): PermissionGroup[] {
    const map = new Map<string, PermissionGroup>()
    for (const p of permissions.value) {
      if (!map.has(p.group)) map.set(p.group, { group: p.group, items: [] })
      map.get(p.group)!.items.push(p)
    }
    return Array.from(map.values())
  }

  async function create(name: string) {
    const created = await rolesApi.createRole(name)
    await fetchRoles()
    return created
  }

  // FORM-A03：保存权限矩阵 → 保存后刷新当前管理员权限（菜单重渲）
  async function update(id: string, payload: { name?: string; permissionKeys?: string[] }) {
    const updated = await rolesApi.updateRole(id, payload)
    await fetchRoles()
    const auth = useAuthStore()
    await auth.fetchMe().catch(() => undefined)
    return updated
  }

  async function remove(id: string) {
    await rolesApi.deleteRole(id)
    await fetchRoles()
  }

  return {
    roles,
    permissions,
    loading,
    fetchRoles,
    fetchPermissions,
    fetchAll,
    groupedPermissions,
    create,
    update,
    remove,
  }
})
