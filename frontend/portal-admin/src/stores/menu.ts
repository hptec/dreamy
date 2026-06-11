// STORE-A02 useMenuStore：基于 authStore.permissionKeys 计算可见菜单（GUARD-03 菜单渲染过滤）
import { defineStore } from 'pinia'
import { computed } from 'vue'
import { menuGroups, type MenuGroup } from '@/config/menu'
import { useAuthStore } from './auth'

export const useMenuStore = defineStore('menu', () => {
  const auth = useAuthStore()

  // 超管看全部；否则按 permissionKeys 过滤菜单项，空组不展示
  const visibleGroups = computed<MenuGroup[]>(() => {
    if (auth.isSuper) return menuGroups
    const keys = new Set(auth.permissionKeys)
    return menuGroups
      .map((g) => ({ ...g, items: g.items.filter((item) => keys.has(item.permission ?? item.to)) }))
      .filter((g) => g.items.length > 0)
  })

  return { visibleGroups }
})
