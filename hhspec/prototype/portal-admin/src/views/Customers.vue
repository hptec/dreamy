<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import { customers } from '@/data/mock'
import { MagnifyingGlassIcon, EyeIcon } from '@heroicons/vue/24/outline'

const search = ref('')
const tier = ref('all')
const filtered = computed(() => customers.filter((c) => {
  if (tier.value !== 'all' && c.tier !== tier.value) return false
  if (search.value && !c.name.toLowerCase().includes(search.value.toLowerCase()) && !c.email.includes(search.value)) return false
  return true
}))
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Customers" title="用户列表" :subtitle="`共 ${customers.length} 名注册用户`" />
    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap gap-3">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="search" class="field pl-9" placeholder="搜索姓名 / 邮箱…" />
        </div>
        <select v-model="tier" class="field w-auto"><option value="all">全部等级</option><option value="VIP">VIP</option><option value="常规">常规</option></select>
      </div>
    </div>
    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>用户</th><th>注册时间</th><th class="text-right">订单数</th><th class="text-right">累计消费</th><th>等级</th><th>状态</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-for="c in filtered" :key="c.id">
            <td>
              <div class="flex items-center gap-3">
                <span class="flex h-9 w-9 items-center justify-center rounded-full bg-ink text-[12px] font-medium text-canvas">{{ c.avatar }}</span>
                <div><p class="font-medium text-ink">{{ c.name }}</p><p class="text-[11px] text-ink-faint">{{ c.email }}</p></div>
              </div>
            </td>
            <td class="text-[12px] text-ink-soft">{{ c.joined }}</td>
            <td class="text-right">{{ c.orders }}</td>
            <td class="text-right font-medium text-ink">${{ c.spent.toLocaleString() }}</td>
            <td><StatusBadge :tone="c.tier === 'VIP' ? 'warn' : 'neutral'" :label="c.tier" :dot="false" /></td>
            <td><StatusBadge :tone="c.status === 'active' ? 'ok' : 'danger'" :label="c.status === 'active' ? '正常' : '已禁用'" /></td>
            <td class="text-right"><RouterLink :to="`/customers/${c.id}`" class="btn-ghost"><EyeIcon class="h-4 w-4" />详情</RouterLink></td>
          </tr>
        </tbody>
      </table>
      <Pagination :total="customers.length" :per-page="10" />
    </div>
  </div>
</template>
