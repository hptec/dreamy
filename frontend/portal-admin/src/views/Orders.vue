<script setup lang="ts">
// PAGE-TRD-A01 / COMP-TRD-A01：订单列表（mock → listAdminOrders；tabs 补 cancelled/refunded；
// 搜索防抖 300ms 服务端；新增币种/时间范围筛选；服务端分页）
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useOrdersStore } from '@/stores/orders'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { currencySymbol, formatDateTime } from '@/utils/format'
import { MagnifyingGlassIcon, EyeIcon } from '@heroicons/vue/24/outline'

const store = useOrdersStore()
const toast = useToastStore()
const route = useRoute()

const tabs = [
  { k: 'all', label: '全部' },
  { k: 'pending', label: '待付款' },
  { k: 'paid', label: '待发货' },
  { k: 'shipped', label: '已发货' },
  { k: 'completed', label: '已完成' },
  { k: 'refunding', label: '退款中' },
  { k: 'cancelled', label: '已取消' },
  { k: 'refunded', label: '已退款' },
]

type OrderTone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral'
const ORDER_STATUS: Record<string, { tone: OrderTone; label: string }> = {
  pending: { tone: 'warn', label: '待付款' },
  paid: { tone: 'info', label: '待发货' },
  shipped: { tone: 'info', label: '已发货' },
  completed: { tone: 'ok', label: '已完成' },
  cancelled: { tone: 'neutral', label: '已取消' },
  refunding: { tone: 'danger', label: '退款中' },
  refunded: { tone: 'neutral', label: '已退款' },
}

function load() {
  store.fetchList().catch((e) => toast.error(e instanceof BizError ? e.message : '加载订单失败'))
}

function selectTab(k: string) {
  store.status = k
  store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
  }, 300)
}

function applyFilters() {
  store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

function gotoPage(p: number) {
  store.setPage(p).catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

onMounted(() => {
  // 待办瓦片 /orders?status=paid 直达
  const qsStatus = route.query.status
  if (typeof qsStatus === 'string' && tabs.some((t) => t.k === qsStatus)) {
    store.status = qsStatus
  }
  load()
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Orders" title="订单列表" subtitle="管理全平台订单与履约状态" />

    <!-- 状态 Tab（补 cancelled/refunded，与 API status 枚举对齐） -->
    <div class="mb-4 flex flex-wrap gap-1 border-b border-line">
      <button
        v-for="t in tabs"
        :key="t.k"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="store.status === t.k ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="selectTab(t.k)"
      >{{ t.label }}</button>
    </div>

    <!-- 筛选栏：搜索（订单号/客户邮箱）+ 币种 + 时间范围 -->
    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap items-center gap-3">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="store.search" class="field pl-9" placeholder="搜索订单号 / 客户邮箱…" @input="onSearchInput" />
        </div>
        <select v-model="store.currency" class="field w-32 shrink-0" @change="applyFilters">
          <option value="all">全部币种</option>
          <option v-for="c in ['USD', 'EUR', 'CAD', 'AUD', 'GBP']" :key="c" :value="c">{{ c }}</option>
        </select>
        <input v-model="store.from" type="date" class="field w-40 shrink-0" @change="applyFilters" />
        <span class="text-ink-faint">—</span>
        <input v-model="store.to" type="date" class="field w-40 shrink-0" @change="applyFilters" />
      </div>
    </div>

    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table">
          <thead>
            <!-- 列调整（数据驱动）：admin 列表契约无 address_snapshot/line_count → 地区/商品数列改 币种/承运（详情页保留地址/明细） -->
            <tr><th>订单号</th><th>客户</th><th>币种</th><th>承运</th><th class="text-right">金额</th><th>支付方式</th><th>状态</th><th>下单时间</th><th class="text-right">操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="store.loading">
              <td colspan="9" class="py-12 text-center text-ink-faint">加载中…</td>
            </tr>
            <tr v-for="o in store.list" v-else :key="o.id">
              <td><RouterLink :to="`/orders/${o.id}`" class="font-mono text-[12px] font-medium text-gold-deep hover:underline">{{ o.orderNo }}</RouterLink></td>
              <td>
                <span class="font-medium text-ink">{{ o.customerName || '—' }}</span><br />
                <span class="text-[11px] text-ink-faint">{{ o.customerEmail || '—' }}</span>
              </td>
              <td class="text-ink-soft">{{ o.currency }}</td>
              <td class="text-[12px] text-ink-soft">{{ o.carrier || '—' }}</td>
              <td class="text-right font-medium text-ink">{{ currencySymbol(o.currency) }}{{ Number(o.totalAmount).toLocaleString() }}</td>
              <td class="text-ink-soft">{{ o.paymentMethod || '—' }}</td>
              <td><StatusBadge :tone="ORDER_STATUS[o.status]?.tone || 'neutral'" :label="ORDER_STATUS[o.status]?.label || o.status" /></td>
              <td class="text-[12px] text-ink-faint">{{ formatDateTime(o.createdAt) }}</td>
              <td class="text-right"><RouterLink :to="`/orders/${o.id}`" class="btn-ghost"><EyeIcon class="h-4 w-4" />详情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-if="!store.loading && store.list.length === 0" title="暂无该状态的订单" hint="切换其他状态标签查看。" />
      <Pagination :total="store.total" :page="store.page" :per-page="store.pageSize" @change="gotoPage" />
    </div>
  </div>
</template>
