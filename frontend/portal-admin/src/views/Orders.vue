<script setup lang="ts">
// PAGE-TRD-A01 / COMP-TRD-O01：订单列表（mock → listAdminOrders；tabs 补 cancelled/refunded；
// 搜索防抖 300ms 服务端；币种/时间范围筛选为超集保留（决策 9）；服务端分页）
// admin-prototype-alignment：ALIGN-012 导出订单 / ALIGN-013 地区+商品数列 / ALIGN-015 搜索回对客户名
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useOrdersStore } from '@/stores/orders'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { OrderStatus } from '@/api/types'
import { currencySymbol, formatDateTime } from '@/utils/format'
import { MagnifyingGlassIcon, ArrowDownTrayIcon, EyeIcon } from '@heroicons/vue/24/outline'

const store = useOrdersStore()
const toast = useToastStore()
const route = useRoute()

const tabs: { k: OrderStatus | 'all'; label: string }[] = [
  { k: 'all', label: '全部' },
  { k: OrderStatus.PENDING, label: '待付款' },
  { k: OrderStatus.PAID, label: '待发货' },
  { k: OrderStatus.SHIPPED, label: '已发货' },
  { k: OrderStatus.COMPLETED, label: '已完成' },
  { k: OrderStatus.REFUNDING, label: '退款中' },
  { k: OrderStatus.CANCELLED, label: '已取消' },
  { k: OrderStatus.REFUNDED, label: '已退款' },
]

type OrderTone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral'
const ORDER_STATUS: Record<number, { tone: OrderTone; label: string }> = {
  [OrderStatus.PENDING]: { tone: 'warn', label: '待付款' },
  [OrderStatus.PAID]: { tone: 'info', label: '待发货' },
  [OrderStatus.SHIPPED]: { tone: 'info', label: '已发货' },
  [OrderStatus.COMPLETED]: { tone: 'ok', label: '已完成' },
  [OrderStatus.CANCELLED]: { tone: 'neutral', label: '已取消' },
  [OrderStatus.REFUNDING]: { tone: 'danger', label: '退款中' },
  [OrderStatus.REFUNDED]: { tone: 'neutral', label: '已退款' },
}

function load() {
  store.fetchList().catch((e) => toast.error(e instanceof BizError ? e.message : '加载订单失败'))
}

function selectTab(k: OrderStatus | 'all') {
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

/** FORM-TRD-O01（ALIGN-012）：导出当前筛选订单 CSV；X-Export-Truncated → toast.warn 截断提示 */
async function onExport() {
  if (store.exporting) return
  try {
    const { truncated } = await store.exportList()
    if (truncated) toast.warn('已达 10000 行上限，结果已截断')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : '导出失败，请稍后重试')
  }
}

onMounted(() => {
  // 待办瓦片 /orders?status=2（paid）直达（整数契约：query 串转数字后匹配 tab）
  const qsStatus = route.query.status
  if (typeof qsStatus === 'string' && qsStatus) {
    const parsed = Number(qsStatus)
    if (tabs.some((t) => t.k === parsed)) store.status = parsed as OrderStatus
  }
  load()
})
</script>

<template>
  <div class="animate-fadeup">
    <!-- COMP-TRD-O02（ALIGN-012）：PageHeader actions 导出订单（原型 L28 btn-outline + ArrowDownTrayIcon） -->
    <PageHeader eyebrow="Orders" title="订单列表" subtitle="管理全平台订单与履约状态">
      <template #actions>
        <button class="btn-outline" :disabled="store.exporting" @click="onExport">
          <ArrowDownTrayIcon class="h-4 w-4" />{{ store.exporting ? '导出中…' : '导出订单' }}
        </button>
      </template>
    </PageHeader>

    <!-- 状态 Tab：8 状态（含 cancelled/refunded）为实现超集，ALIGN-014 EXEMPT（决策 9）保留 -->
    <div class="mb-4 flex flex-wrap gap-1 border-b border-line">
      <button
        v-for="t in tabs"
        :key="t.k"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="store.status === t.k ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="selectTab(t.k)"
      >{{ t.label }}</button>
    </div>

    <!-- 筛选栏：搜索（订单号/客户名，FORM-TRD-O02/ALIGN-015；服务端 search 兼容邮箱为超集）+ 币种 + 时间范围（超集保留，决策 9） -->
    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap items-center gap-3">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="store.search" class="field pl-9" placeholder="搜索订单号 / 客户名…" @input="onSearchInput" />
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
            <!-- COMP-TRD-O01（ALIGN-013）：列回对原型 L48 —— 地区（country）/商品数（item_count）；币种留金额符号与筛选下拉，承运留详情页 -->
            <tr><th>订单号</th><th>客户</th><th>地区</th><th class="text-right">商品数</th><th class="text-right">金额</th><th>支付方式</th><th>状态</th><th>下单时间</th><th class="text-right">操作</th></tr>
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
              <td>{{ o.country || '—' }}</td>
              <td class="text-right">{{ o.itemCount ?? '—' }}</td>
              <td class="text-right font-medium text-ink">{{ currencySymbol(o.currency) }}{{ Number(o.totalAmount).toLocaleString() }}</td>
              <td class="text-ink-soft">{{ o.paymentMethod || '—' }}</td>
              <td><StatusBadge :tone="ORDER_STATUS[o.status]?.tone || 'neutral'" :label="ORDER_STATUS[o.status]?.label || String(o.status)" /></td>
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
