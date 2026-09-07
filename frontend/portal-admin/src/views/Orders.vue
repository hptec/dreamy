<script setup lang="ts">
// PAGE-TRD-A01 / COMP-TRD-O01：订单列表（mock → listAdminOrders；tabs 补 cancelled/refunded/delivered；
// 搜索防抖 300ms 服务端；币种/时间范围筛选为超集保留（决策 9）；服务端分页）
// admin-prototype-alignment：ALIGN-012 导出订单 / ALIGN-013 地区+商品数列 / ALIGN-015 搜索回对客户名
// order-flow-complete J：筛选增制作阶段 / 婚期早于 / 有无包裹；列增制作阶段徽章、距婚期天数（≤14 天红色）
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useOrdersStore } from '@/stores/orders'
import { useToastStore } from '@/stores/toast'
import { describeError } from '@/constants/tradingErrors'
import { OrderStatus, ProductionStage } from '@/api/types'
import { currencySymbol, formatDateTime } from '@/utils/format'
import { orderStatusMeta, productionStageMeta } from '@/utils/tradingLabels'
import { MagnifyingGlassIcon, ArrowDownTrayIcon, EyeIcon, XMarkIcon } from '@heroicons/vue/24/outline'

const store = useOrdersStore()
const toast = useToastStore()
const route = useRoute()

const tabs: { k: OrderStatus | 'all'; label: string }[] = [
  { k: 'all', label: '全部' },
  { k: OrderStatus.PENDING, label: '待付款' },
  { k: OrderStatus.PAID, label: '已付款/制作中' },
  { k: OrderStatus.SHIPPED, label: '已发货' },
  { k: OrderStatus.DELIVERED, label: '已签收' },
  { k: OrderStatus.COMPLETED, label: '已完成' },
  { k: OrderStatus.REFUNDING, label: '退款中' },
  { k: OrderStatus.CANCELLED, label: '已取消' },
  { k: OrderStatus.REFUNDED, label: '已退款' },
]

const STAGE_OPTIONS = [
  { value: 'all', label: '全部制作阶段' },
  { value: ProductionStage.PENDING_REVIEW, label: '待审核' },
  { value: ProductionStage.IN_PRODUCTION, label: '制作中' },
  { value: ProductionStage.QUALITY_CHECK, label: '质检中' },
  { value: ProductionStage.READY_TO_SHIP, label: '待发货' },
]
const SHIPMENT_OPTIONS = [
  { value: 'all', label: '有无包裹' },
  { value: 'yes', label: '已创建包裹' },
  { value: 'no', label: '尚无包裹' },
]

function load() {
  store.fetchList().catch((e) => toast.error(describeError(e, '加载订单失败')))
}

function selectTab(k: OrderStatus | 'all') {
  store.status = k
  store.applyFilters().catch((e) => toast.error(describeError(e, '加载失败')))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.applyFilters().catch((e) => toast.error(describeError(e, '加载失败')))
  }, 300)
}

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

function applyFilters() {
  store.applyFilters().catch((e) => toast.error(describeError(e, '加载失败')))
}

function clearExtraFilters() {
  store.productionStage = 'all'
  store.weddingBefore = ''
  store.hasShipment = 'all'
  applyFilters()
}

function gotoPage(p: number) {
  store.setPage(p).catch((e) => toast.error(describeError(e, '加载失败')))
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

const extraActive = () => store.productionStage !== 'all' || !!store.weddingBefore || store.hasShipment !== 'all'

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

    <!-- 状态 Tab：9 状态（含 delivered）为实现超集，ALIGN-014 EXEMPT（决策 9）保留 -->
    <div class="mb-4 flex flex-wrap gap-1 border-b border-line">
      <button
        v-for="t in tabs"
        :key="t.k"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="store.status === t.k ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="selectTab(t.k)"
      >{{ t.label }}</button>
    </div>

    <!-- 筛选栏：搜索 + 币种 + 时间范围 + 制作阶段 + 婚期早于 + 有无包裹 -->
    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap items-center gap-3">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="store.search" class="field pl-9" placeholder="搜索订单号 / 客户名…" @input="onSearchInput" />
        </div>
        <SelectMenu
          v-model="store.currency"
          :options="[
            { value: 'all', label: '全部币种' },
            { value: 'USD', label: 'USD' },
            { value: 'EUR', label: 'EUR' },
            { value: 'CAD', label: 'CAD' },
            { value: 'AUD', label: 'AUD' },
            { value: 'GBP', label: 'GBP' },
          ]"
          class="w-32 shrink-0"
          @change="applyFilters"
        />
        <input v-model="store.from" type="date" class="field w-40 shrink-0" title="下单起始日" @change="applyFilters" />
        <span class="text-ink-faint">—</span>
        <input v-model="store.to" type="date" class="field w-40 shrink-0" title="下单截止日" @change="applyFilters" />
      </div>
      <div class="mt-3 flex flex-wrap items-center gap-3 border-t border-line pt-3">
        <SelectMenu
          v-model="store.productionStage"
          :options="STAGE_OPTIONS"
          class="w-40 shrink-0"
          data-testid="filter-production-stage"
          @change="applyFilters"
        />
        <label class="flex items-center gap-2 text-[12px] text-ink-soft">
          婚期早于
          <input v-model="store.weddingBefore" type="date" class="field w-40 shrink-0" data-testid="filter-wedding-before" @change="applyFilters" />
        </label>
        <SelectMenu
          v-model="store.hasShipment"
          :options="SHIPMENT_OPTIONS"
          class="w-36 shrink-0"
          data-testid="filter-has-shipment"
          @change="applyFilters"
        />
        <button v-if="extraActive()" class="btn-ghost text-[12px]" @click="clearExtraFilters"><XMarkIcon class="h-3.5 w-3.5" />清除履约筛选</button>
      </div>
    </div>

    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table" data-testid="orders-table">
          <thead>
            <!-- COMP-TRD-O01（ALIGN-013）：地区（country）/商品数（item_count）；order-flow-complete：制作阶段 / 婚期 -->
            <tr><th>订单号</th><th>客户</th><th>地区</th><th class="text-right">商品数</th><th class="text-right">金额</th><th>状态</th><th>制作阶段</th><th>婚期</th><th>下单时间</th><th class="text-right">操作</th></tr>
          </thead>
          <tbody>
            <tr v-if="store.loading">
              <td colspan="10" class="py-12 text-center text-ink-faint">加载中…</td>
            </tr>
            <tr v-for="o in store.list" v-else :key="o.id">
              <td><RouterLink :to="`/orders/${o.id}`" class="font-mono text-[12px] font-medium text-gold-deep hover:underline">{{ o.orderNo }}</RouterLink></td>
              <td>
                <span class="font-medium text-ink">{{ o.customerName || '—' }}</span><br />
                <span class="text-[11px] text-ink-faint">{{ o.customerEmail || '—' }}</span>
              </td>
              <td>{{ o.country || '—' }}</td>
              <td class="text-right">{{ o.itemCount ?? '—' }}</td>
              <td class="text-right font-medium text-ink">
                {{ currencySymbol(o.currency) }}{{ Number(o.totalAmount).toLocaleString() }}
                <span v-if="Number(o.refundedAmount) > 0" class="block text-[10px] font-normal text-danger">已退 {{ currencySymbol(o.currency) }}{{ Number(o.refundedAmount).toLocaleString() }}</span>
              </td>
              <td><StatusBadge :tone="orderStatusMeta(o.status).tone" :label="orderStatusMeta(o.status).label" /></td>
              <td>
                <StatusBadge v-if="o.status === OrderStatus.PAID && o.productionStage" :tone="productionStageMeta(o.productionStage).tone" :label="productionStageMeta(o.productionStage).label" :dot="false" />
                <span v-else class="text-ink-faint">—</span>
              </td>
              <td class="text-[12px]">
                <template v-if="o.weddingDate">
                  <span class="text-ink-soft">{{ o.weddingDate }}</span>
                  <span
                    v-if="o.weddingDaysLeft != null"
                    class="ml-1 rounded px-1.5 py-0.5 text-[10px] font-medium"
                    :class="o.weddingDaysLeft <= 14 ? 'bg-danger/12 text-danger' : 'bg-ink/6 text-ink-soft'"
                    :title="o.weddingDaysLeft <= 14 ? '距婚期不足 14 天，请优先履约' : ''"
                  >{{ o.weddingDaysLeft < 0 ? `已过 ${-o.weddingDaysLeft} 天` : `${o.weddingDaysLeft} 天` }}</span>
                </template>
                <span v-else class="text-ink-faint">—</span>
              </td>
              <td class="text-[12px] text-ink-faint">{{ formatDateTime(o.createdAt) }}</td>
              <td class="text-right"><RouterLink :to="`/orders/${o.id}`" class="btn-ghost"><EyeIcon class="h-4 w-4" />详情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-if="!store.loading && store.list.length === 0" title="暂无匹配的订单" hint="切换状态标签或调整筛选条件。" />
      <Pagination :total="store.total" :page="store.page" :per-page="store.pageSize" @change="gotoPage" />
    </div>
  </div>
</template>
