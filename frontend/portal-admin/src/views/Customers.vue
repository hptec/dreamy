<script setup lang="ts">
// PAGE-A02 / COMP-A03：用户列表，对接 listUsers（分页 + status/tier/email 筛选）
import { onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useUsersStore } from '@/stores/users'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  tierLabel, accountStatusLabel, accountStatusTone, formatDate, initialsOf,
} from '@/utils/format'
import { MagnifyingGlassIcon, EyeIcon } from '@heroicons/vue/24/outline'

const store = useUsersStore()
const toast = useToastStore()

async function load() {
  try {
    await store.fetchList()
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载用户列表失败')
  }
}

function onSearch() {
  store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

function gotoPage(p: number) {
  store.setPage(p).catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

const totalPages = () => Math.max(1, Math.ceil(store.total / store.pageSize))

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Customers" title="用户列表" :subtitle="`共 ${store.total} 名注册用户`" />

    <!-- 筛选栏（下拉固定宽度，避免抖动 · 项目记忆） -->
    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap gap-3">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input
            v-model="store.filterEmail"
            class="field pl-9"
            placeholder="搜索邮箱…"
            @keyup.enter="onSearch"
          />
        </div>
        <SelectMenu
          v-model="store.filterTier"
          :options="[
            { value: 'all', label: '全部等级' },
            { value: 2, label: 'VIP' },
            { value: 1, label: '常规' },
          ]"
          class="w-36 shrink-0"
          @change="onSearch"
        />
        <SelectMenu
          v-model="store.filterStatus"
          :options="[
            { value: 'all', label: '全部状态' },
            { value: 1, label: '正常' },
            { value: 2, label: '已禁用' },
            { value: 3, label: '已注销' },
            { value: 4, label: '已匿名' },
          ]"
          class="w-36 shrink-0"
          @change="onSearch"
        />
        <button class="btn-outline shrink-0" @click="onSearch">查询</button>
      </div>
    </div>

    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead>
          <tr>
            <th>用户</th>
            <th>注册时间</th>
            <th>等级</th>
            <th>状态</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading">
            <td colspan="5" class="py-12 text-center text-ink-faint">加载中…</td>
          </tr>
          <tr v-else-if="store.list.length === 0">
            <td colspan="5" class="py-12 text-center text-ink-faint">暂无匹配的用户</td>
          </tr>
          <tr v-for="c in store.list" v-else :key="c.id">
            <td>
              <div class="flex items-center gap-3">
                <span class="flex h-9 w-9 items-center justify-center rounded-full bg-ink text-[12px] font-medium text-canvas">{{ initialsOf(c.name || c.email) }}</span>
                <div>
                  <p class="font-medium text-ink">{{ c.name || '（未设置姓名）' }}</p>
                  <p class="text-[11px] text-ink-faint">{{ c.email || '—' }}</p>
                </div>
              </div>
            </td>
            <td class="text-[12px] text-ink-soft">{{ formatDate(c.joinedAt) }}</td>
            <td><StatusBadge :tone="c.tier === 2 ? 'warn' : 'neutral'" :label="tierLabel(c.tier)" :dot="false" /></td>
            <td><StatusBadge :tone="accountStatusTone((c as any).status)" :label="accountStatusLabel((c as any).status)" /></td>
            <td class="text-right">
              <RouterLink :to="`/customers/${c.id}`" class="btn-ghost"><EyeIcon class="h-4 w-4" />详情</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div v-if="totalPages() > 1" class="flex items-center justify-between border-t border-line px-4 py-3 text-[13px] text-ink-faint">
        <span>共 {{ store.total }} 条，第 {{ store.page }} / {{ totalPages() }} 页</span>
        <div class="flex gap-1">
          <button class="rounded-luxe border border-line px-2.5 py-1 hover:bg-canvas-warm disabled:opacity-40" :disabled="store.page <= 1" @click="gotoPage(store.page - 1)">上一页</button>
          <button class="rounded-luxe border border-line px-2.5 py-1 hover:bg-canvas-warm disabled:opacity-40" :disabled="store.page >= totalPages()" @click="gotoPage(store.page + 1)">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>
