<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import { orders, ORDER_STATUS } from '@/data/mock'
import { MagnifyingGlassIcon, ArrowDownTrayIcon, EyeIcon } from '@heroicons/vue/24/outline'

const search = ref('')
const status = ref('all')
const tabs = [
  { k: 'all', label: '全部' }, { k: 'pending', label: '待付款' }, { k: 'paid', label: '待发货' },
  { k: 'shipped', label: '已发货' }, { k: 'completed', label: '已完成' }, { k: 'refunding', label: '退款中' }
]
const filtered = computed(() => orders.filter((o) => {
  if (status.value !== 'all' && o.status !== status.value) return false
  if (search.value && !o.id.toLowerCase().includes(search.value.toLowerCase()) && !o.customer.toLowerCase().includes(search.value.toLowerCase())) return false
  return true
}))
const curSym = { USD: '$', CAD: 'C$', AUD: 'A$', GBP: '£' }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Orders" title="订单列表" subtitle="管理全平台订单与履约状态">
      <template #actions><button class="btn-outline"><ArrowDownTrayIcon class="h-4 w-4" />导出订单</button></template>
    </PageHeader>

    <!-- 状态 Tab -->
    <div class="mb-4 flex flex-wrap gap-1 border-b border-line">
      <button v-for="t in tabs" :key="t.k" @click="status = t.k"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="status === t.k ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t.label }}</button>
    </div>

    <div class="panel mb-4 p-4">
      <div class="relative max-w-md">
        <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
        <input v-model="search" class="field pl-9" placeholder="搜索订单号 / 客户名…" />
      </div>
    </div>

    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table">
          <thead><tr><th>订单号</th><th>客户</th><th>地区</th><th class="text-right">商品数</th><th class="text-right">金额</th><th>支付方式</th><th>状态</th><th>下单时间</th><th class="text-right">操作</th></tr></thead>
          <tbody>
            <tr v-for="o in filtered" :key="o.id">
              <td><RouterLink :to="`/orders/${o.id}`" class="font-mono text-[12px] font-medium text-gold-deep hover:underline">{{ o.id }}</RouterLink></td>
              <td><span class="font-medium text-ink">{{ o.customer }}</span><br /><span class="text-[11px] text-ink-faint">{{ o.email }}</span></td>
              <td>{{ o.country }}</td>
              <td class="text-right">{{ o.items }}</td>
              <td class="text-right font-medium text-ink">{{ curSym[o.currency] }}{{ o.total.toLocaleString() }}</td>
              <td class="text-ink-soft">{{ o.payment }}</td>
              <td><StatusBadge :tone="ORDER_STATUS[o.status].tone" :label="ORDER_STATUS[o.status].label" /></td>
              <td class="text-[12px] text-ink-faint">{{ o.date }}</td>
              <td class="text-right"><RouterLink :to="`/orders/${o.id}`" class="btn-ghost"><EyeIcon class="h-4 w-4" />详情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-if="filtered.length === 0" title="暂无该状态的订单" hint="切换其他状态标签查看。" />
      <Pagination :total="filtered.length" :per-page="10" />
    </div>
  </div>
</template>
