// STORE-A01 useAuthStore + STORE-A03 token 持久化（localStorage，8h 无 refresh，401→跳登录）
// 约束: GUARD-01 fetchMe 缓存 permissionKeys；GUARD-04 isSuper 短路放行
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import { clearToken, getToken, setToken } from '@/api/client'
import type { AdminProfile } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const admin = ref<AdminProfile | null>(null)
  const roleName = ref<string>('')
  const isSuper = ref<boolean>(false)
  const permissionKeys = ref<string[]>([])
  const loaded = ref<boolean>(false)

  const isAuthenticated = computed(() => !!token.value)

  // FORM-A01 登录：存 token + permissionKeys
  async function login(payload: { email: string; password: string; redirect?: string }) {
    const result = await authApi.adminLogin(payload)
    token.value = result.token
    setToken(result.token)
    admin.value = result.admin
    roleName.value = result.admin.roleName || ''
    permissionKeys.value = result.permissionKeys || []
    isSuper.value = result.isSuper ?? false
    loaded.value = true
    return result
  }

  // GUARD-01：守卫拉取当前管理员 + 权限集
  async function fetchMe() {
    const me = await authApi.adminMe()
    admin.value = me.admin
    roleName.value = me.roleName
    isSuper.value = me.isSuper
    permissionKeys.value = me.permissionKeys || []
    loaded.value = true
    return me
  }

  async function ensureLoaded() {
    if (loaded.value) return
    await fetchMe()
  }

  // 实时刷新权限：每次导航调用，角色权限被改后刷新/跳转即生效（无需重登）。
  // 超管(is_super)不依赖 permissionKeys（hasPermission 短路），可跳过以省请求。
  async function refreshPermissions() {
    if (isSuper.value) return
    permissionKeys.value = await authApi.adminPermissions()
  }

  // GUARD-02/04：是否可访问某权限 key（超管短路放行）
  function hasPermission(key?: string): boolean {
    if (isSuper.value) return true
    if (!key) return true
    return permissionKeys.value.includes(key)
  }

  async function logout() {
    try {
      await authApi.adminLogout()
    } catch {
      /* 即便后端登出失败也清理本地态 */
    }
    reset()
  }

  function reset() {
    token.value = null
    admin.value = null
    roleName.value = ''
    isSuper.value = false
    permissionKeys.value = []
    loaded.value = false
    clearToken()
  }

  return {
    token,
    admin,
    roleName,
    isSuper,
    permissionKeys,
    loaded,
    isAuthenticated,
    login,
    fetchMe,
    ensureLoaded,
    refreshPermissions,
    hasPermission,
    logout,
    reset,
  }
})
