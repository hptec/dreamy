<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { Menu, MenuButton, MenuItems, MenuItem } from '@headlessui/vue'
import { menuGroups } from '@/data/menu'
import { pendingChanges } from '@/data/mock'
import { useAuth } from '@/composables/useAuth'
import {
  Bars3Icon, MagnifyingGlassIcon, BellIcon, RocketLaunchIcon, ChevronDownIcon,
  Cog6ToothIcon, ArrowRightOnRectangleIcon
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const { user, logout } = useAuth()
const collapsed = ref(false)
const pendingCount = computed(() => pendingChanges.length)

function handleLogout() {
  logout()
  router.replace({ name: 'login' })
}

const crumbs = computed(() => {
  const g = route.meta?.group
  const t = route.meta?.title
  return [g, t].filter(Boolean)
})

function isActive(to) {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(to + '/')
}
function groupActive(group) {
  return group.items.some((i) => isActive(i.to))
}
</script>

<template>
  <div class="flex min-h-screen bg-canvas">
    <!-- 侧边栏 -->
    <aside
      class="sticky top-0 flex h-screen flex-col bg-sidebar text-canvas/80 transition-all duration-300"
      :class="collapsed ? 'w-[68px]' : 'w-[244px]'"
    >
      <!-- 品牌 -->
      <div class="flex h-16 items-center gap-3 border-b border-white/8 px-5">
        <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-luxe bg-gold font-display text-lg font-semibold text-sidebar">D</div>
        <div v-if="!collapsed" class="leading-tight">
          <p class="font-display text-base font-semibold text-canvas">Dreamy</p>
          <p class="text-[10px] uppercase tracking-luxe text-gold-soft">Admin Console</p>
        </div>
      </div>

      <!-- 菜单 -->
      <nav class="flex-1 overflow-y-auto px-3 py-4">
        <div v-for="group in menuGroups" :key="group.label" class="mb-1">
          <!-- 单项分组 -->
          <template v-if="group.items.length === 1">
            <RouterLink
              :to="group.items[0].to"
              class="group flex items-center gap-3 rounded-luxe px-3 py-2.5 text-[13px] transition-colors"
              :class="isActive(group.items[0].to) ? 'bg-sidebar-active text-canvas' : 'hover:bg-sidebar-hover hover:text-canvas'"
            >
              <component :is="group.icon" class="h-5 w-5 shrink-0" :class="isActive(group.items[0].to) ? 'text-gold' : ''" />
              <span v-if="!collapsed" class="truncate">{{ group.label }}</span>
            </RouterLink>
          </template>
          <!-- 多项分组 -->
          <template v-else>
            <div
              class="flex items-center gap-3 px-3 py-2.5 text-[13px]"
              :class="groupActive(group) ? 'text-canvas' : ''"
            >
              <component :is="group.icon" class="h-5 w-5 shrink-0" :class="groupActive(group) ? 'text-gold' : 'text-canvas/55'" />
              <span v-if="!collapsed" class="truncate font-medium">{{ group.label }}</span>
              <span v-if="!collapsed && group.badge" class="ml-auto rounded-full bg-gold/20 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-gold-soft">{{ group.badge }}</span>
            </div>
            <div v-if="!collapsed" class="mb-1 ml-[26px] space-y-0.5 border-l border-white/8 pl-3">
              <RouterLink
                v-for="item in group.items"
                :key="item.to"
                :to="item.to"
                class="block rounded-luxe px-3 py-1.5 text-[12.5px] transition-colors"
                :class="isActive(item.to) ? 'bg-sidebar-active text-canvas' : 'text-canvas/60 hover:bg-sidebar-hover hover:text-canvas'"
              >{{ item.title }}</RouterLink>
            </div>
          </template>
        </div>
      </nav>

      <!-- 发布快捷 -->
      <div class="border-t border-white/8 p-3">
        <RouterLink to="/publish" class="flex items-center gap-2 rounded-luxe bg-gold/15 px-3 py-2.5 text-[12.5px] font-medium text-gold-soft transition-colors hover:bg-gold/25">
          <RocketLaunchIcon class="h-5 w-5 shrink-0" />
          <span v-if="!collapsed">发布中心</span>
          <span v-if="!collapsed && pendingCount" class="ml-auto rounded-full bg-gold px-1.5 py-0.5 text-[10px] font-semibold text-sidebar">{{ pendingCount }}</span>
        </RouterLink>
      </div>
    </aside>

    <!-- 右侧主区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <!-- 顶栏 -->
      <header class="sticky top-0 z-20 flex h-16 items-center gap-4 border-b border-line bg-canvas/85 px-6 backdrop-blur">
        <button class="rounded-luxe p-1.5 text-ink-soft hover:bg-canvas-warm" @click="collapsed = !collapsed">
          <Bars3Icon class="h-5 w-5" />
        </button>
        <!-- 面包屑 -->
        <nav class="flex items-center gap-2 text-[13px] text-ink-faint">
          <span>Dreamy</span>
          <template v-for="(c, i) in crumbs" :key="i">
            <span>/</span>
            <span :class="i === crumbs.length - 1 ? 'font-medium text-ink' : ''">{{ c }}</span>
          </template>
        </nav>

        <div class="ml-auto flex items-center gap-3">
          <!-- 搜索 -->
          <div class="relative hidden md:block">
            <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
            <input class="field w-56 pl-9" placeholder="搜索商品 / 订单 / 用户…" />
          </div>
          <!-- 发布按钮 -->
          <RouterLink to="/publish" class="btn-gold hidden sm:inline-flex">
            <RocketLaunchIcon class="h-4 w-4" /> 发布 · {{ pendingCount }}
          </RouterLink>
          <!-- 通知 -->
          <button class="relative rounded-luxe p-2 text-ink-soft hover:bg-canvas-warm">
            <BellIcon class="h-5 w-5" />
            <span class="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-danger"></span>
          </button>
          <!-- 用户 -->
          <Menu as="div" class="relative">
            <MenuButton class="flex items-center gap-2 rounded-luxe py-1 pl-1 pr-2 hover:bg-canvas-warm">
              <span class="flex h-8 w-8 items-center justify-center rounded-full bg-ink text-[12px] font-medium text-canvas">{{ user?.initials || 'SA' }}</span>
              <span class="hidden text-[13px] font-medium lg:block">{{ user?.name || 'Super Admin' }}</span>
              <ChevronDownIcon class="hidden h-4 w-4 text-ink-faint lg:block" />
            </MenuButton>
            <transition
              enter-active-class="transition duration-100 ease-out"
              enter-from-class="transform scale-95 opacity-0"
              enter-to-class="transform scale-100 opacity-100"
              leave-active-class="transition duration-75 ease-in"
              leave-from-class="transform scale-100 opacity-100"
              leave-to-class="transform scale-95 opacity-0"
            >
              <MenuItems class="absolute right-0 mt-2 w-56 origin-top-right rounded-luxe border border-line bg-white py-1.5 shadow-panel focus:outline-none">
                <div class="border-b border-line px-4 py-3">
                  <p class="text-[13px] font-medium text-ink">{{ user?.name || 'Super Admin' }}</p>
                  <p class="mt-0.5 truncate text-[12px] text-ink-faint">{{ user?.email || 'admin@dreamy.com' }}</p>
                  <span class="mt-1.5 inline-block rounded-full bg-gold/15 px-2 py-0.5 text-[10px] font-medium text-gold-deep">{{ user?.role || '超级管理员' }}</span>
                </div>
                <MenuItem v-slot="{ active }">
                  <RouterLink
                    to="/settings"
                    class="flex items-center gap-2.5 px-4 py-2 text-[13px] text-ink-soft"
                    :class="active ? 'bg-canvas-warm text-ink' : ''"
                  >
                    <Cog6ToothIcon class="h-4 w-4" /> 系统设置
                  </RouterLink>
                </MenuItem>
                <MenuItem v-slot="{ active }">
                  <button
                    type="button"
                    class="flex w-full items-center gap-2.5 px-4 py-2 text-left text-[13px] text-danger"
                    :class="active ? 'bg-danger/10' : ''"
                    @click="handleLogout"
                  >
                    <ArrowRightOnRectangleIcon class="h-4 w-4" /> 退出登录
                  </button>
                </MenuItem>
              </MenuItems>
            </transition>
          </Menu>
        </div>
      </header>

      <!-- 内容 -->
      <main class="flex-1 overflow-x-hidden p-6">
        <slot />
      </main>
    </div>
  </div>
</template>
