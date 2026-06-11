<script setup lang="ts">
// PAGE-ANA-A02 / COMP-ANA-A07~A11：数据看板（四 tab 结构零改动；销售/热度 ← E-ANA-02；流量/漏斗 ← E-ANA-03）
// DEC-ANA-FE-6 range 固定 30d；FE-7 热度表列调整；FE-8 GA4 降级占位 + fetchedAt 角标；A11 导出 CSV 前端本地序列化
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import SparkArea from '@/components/SparkArea.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAnalyticsStore } from '@/stores/analytics'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { ArrowDownTrayIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'

const store = useAnalyticsStore()
const toast = useToastStore()

const tab = ref<'sales' | 'traffic' | 'funnel' | 'hot'>('sales')

const SOURCE_LABEL: Record<string, string> = {
  organic: '自然搜索',
  direct: '直接访问',
  social: '社交',
  referral: '外链',
  paid: '付费',
  email: '邮件',
}
const DEVICE_LABEL: Record<string, string> = { mobile: '移动端', desktop: '桌面端', tablet: '平板' }
const STAGE_LABEL: Record<string, string> = {
  page_view: '商品浏览',
  view_item: '商品详情',
  add_to_cart: '加入购物车',
  begin_checkout: '进入结算',
  purchase: '完成支付',
}
const BAR_COLORS = ['#C19A6B', '#8B9D83', '#D8A7A0', '#9C8E7E', '#6B7280']

const kpiCards = computed(() => {
  const k = store.overview?.kpis
  return [
    { label: '本月 GMV', value: k ? '$' + Number(k.gmvMonth).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '—' },
    { label: '订单数', value: k ? Number(k.orderCount).toLocaleString() : '—' },
    { label: '客单价', value: k ? '$' + Number(k.avgOrderValue).toFixed(2) : '—' },
    { label: '退款率', value: k ? Number(k.refundRate).toFixed(1) + '%' : '—' },
  ]
})

const fetchedAtText = computed(() => {
  const f = store.traffic?.fetchedAt
  if (!f) return ''
  const d = new Date(f)
  if (Number.isNaN(d.getTime())) return f
  const pad = (n: number) => String(n).padStart(2, '0')
  return `数据时间 ${pad(d.getHours())}:${pad(d.getMinutes())}`
})

// 流量/漏斗 tab 首次激活时懒加载（STORE-ANA-02）
watch(tab, (t) => {
  if (t === 'traffic' || t === 'funnel') store.fetchTraffic().catch(() => undefined)
})

function retry() {
  store.retryTraffic().catch(() => undefined)
}

/** COMP-ANA-A11：导出当前 tab 数据为 CSV（前端本地序列化，无新端点） */
function exportCsv() {
  let rows: string[][] = []
  let name = `analytics-${tab.value}`
  if (tab.value === 'sales') {
    rows = [['date', 'gmv'], ...(store.overview?.gmvTrend.labels.map((l, i) => [l, String(store.overview!.gmvTrend.values[i])]) ?? [])]
  } else if (tab.value === 'traffic') {
    rows = [['source', 'sessions', 'share'], ...((store.traffic?.trafficSources ?? []).map((s) => [s.source, String(s.sessions), String(s.share)]))]
  } else if (tab.value === 'funnel') {
    rows = [['stage', 'value'], ...((store.traffic?.funnel ?? []).map((f) => [f.stage, String(f.value)]))]
  } else {
    rows = [['rank', 'product', 'sales', 'amount'], ...((store.overview?.topProducts ?? []).map((p, i) => [String(i + 1), p.name, String(p.sales), String(p.amount)]))]
  }
  if (rows.length <= 1) {
    toast.info('当前 tab 暂无可导出数据')
    return
  }
  const csv = rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${name}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(() => {
  store.fetchOverview().catch((e) => toast.error(e instanceof BizError ? e.message : '加载看板失败'))
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Analytics" title="数据看板" subtitle="销售、流量、转化与商品热度分析">
      <template #actions><button class="btn-outline" @click="exportCsv"><ArrowDownTrayIcon class="h-4 w-4" />导出报表</button></template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in [['sales', '销售分析'], ['traffic', '流量分析'], ['funnel', '转化漏斗'], ['hot', '商品热度']] as const"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="tab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- 销售 tab（COMP-ANA-A07） -->
    <div v-show="tab === 'sales'" class="space-y-6">
      <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div v-for="k in kpiCards" :key="k.label" class="panel p-5">
          <p class="text-[12px] text-ink-faint">{{ k.label }}</p>
          <p v-if="store.loadingOverview" class="mt-1 h-8 w-24 animate-pulse rounded bg-canvas-warm"></p>
          <p v-else class="mt-1 font-display text-2xl font-semibold text-ink">{{ k.value }}</p>
        </div>
      </div>
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">GMV 趋势</h3>
        <SparkArea v-if="store.overview?.gmvTrend.values.length" :data="store.overview.gmvTrend.values.map(Number)" :labels="store.overview.gmvTrend.labels" :height="260" />
        <div v-else class="flex h-64 items-center justify-center text-[13px] text-ink-faint">{{ store.loadingOverview ? '加载中…' : '暂无数据' }}</div>
      </div>
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">品类销售占比</h3>
        <div class="space-y-3">
          <div v-for="(c, i) in store.overview?.categorySales ?? []" :key="c.categoryId">
            <div class="mb-1 flex justify-between text-[13px]">
              <span class="text-ink-soft">{{ c.categoryName }}</span>
              <span class="font-medium text-ink">{{ Number(c.share).toFixed(1) }}%</span>
            </div>
            <div class="h-2.5 overflow-hidden rounded-full bg-line">
              <div class="h-full rounded-full" :style="{ width: Number(c.share) + '%', background: BAR_COLORS[i % BAR_COLORS.length] }"></div>
            </div>
          </div>
          <p v-if="!store.overview?.categorySales?.length" class="py-4 text-center text-[13px] text-ink-faint">暂无销售数据</p>
        </div>
      </div>
    </div>

    <!-- 流量 tab（COMP-ANA-A08：GA4 降级占位 + fetchedAt 角标） -->
    <div v-show="tab === 'traffic'">
      <p v-if="fetchedAtText" class="mb-2 text-right text-[11px] text-ink-faint">{{ fetchedAtText }}</p>
      <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">访客来源</h3>
          <div v-if="store.trafficUnavailable">
            <EmptyState title="数据暂不可用" hint="流量数据服务（GA4）暂时不可用，交易数据不受影响。">
              <template #action><button class="btn-outline" @click="retry"><ArrowPathIcon class="h-4 w-4" />重试</button></template>
            </EmptyState>
          </div>
          <div v-else-if="store.loadingTraffic" class="space-y-3">
            <div v-for="i in 5" :key="i" class="h-8 animate-pulse rounded bg-canvas-warm/60"></div>
          </div>
          <div v-else class="space-y-3">
            <div v-for="s in store.traffic?.trafficSources ?? []" :key="s.source">
              <div class="mb-1 flex justify-between text-[13px]">
                <span class="text-ink-soft">{{ SOURCE_LABEL[s.source] || s.source }}</span>
                <span class="font-medium text-ink">{{ Number(s.share).toFixed(1) }}%</span>
              </div>
              <div class="h-2.5 overflow-hidden rounded-full bg-line"><div class="h-full rounded-full bg-gold" :style="{ width: Number(s.share) + '%' }"></div></div>
            </div>
          </div>
        </div>
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">设备占比</h3>
          <div v-if="store.trafficUnavailable">
            <EmptyState title="数据暂不可用">
              <template #action><button class="btn-outline" @click="retry"><ArrowPathIcon class="h-4 w-4" />重试</button></template>
            </EmptyState>
          </div>
          <div v-else class="flex items-center justify-around py-8">
            <div v-for="(d, i) in store.traffic?.deviceShare ?? []" :key="d.device" class="text-center">
              <p class="font-display text-3xl font-semibold" :class="['text-gold-deep', 'text-sage', 'text-blush'][i % 3]">{{ Number(d.share).toFixed(0) }}%</p>
              <p class="text-[12px] text-ink-faint">{{ DEVICE_LABEL[d.device] || d.device }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 漏斗 tab（COMP-ANA-A09） -->
    <div v-show="tab === 'funnel'" class="panel p-6">
      <h3 class="mb-6 font-display text-lg font-semibold text-ink">转化漏斗</h3>
      <div v-if="store.trafficUnavailable">
        <EmptyState title="数据暂不可用" hint="流量数据服务（GA4）暂时不可用。">
          <template #action><button class="btn-outline" @click="retry"><ArrowPathIcon class="h-4 w-4" />重试</button></template>
        </EmptyState>
      </div>
      <div v-else class="mx-auto max-w-2xl space-y-2">
        <div v-for="(f, i) in store.traffic?.funnel ?? []" :key="f.stage" class="flex items-center gap-4">
          <span class="w-24 text-right text-[13px] text-ink-soft">{{ STAGE_LABEL[f.stage] || f.stage }}</span>
          <div class="relative flex-1">
            <div
              class="flex h-12 items-center justify-center rounded-luxe text-[13px] font-medium text-white"
              :style="{ width: (f.value / store.maxFunnel * 100) + '%', background: BAR_COLORS[i % BAR_COLORS.length], marginLeft: ((1 - f.value / store.maxFunnel) * 50) + '%' }"
            >{{ f.value.toLocaleString() }}</div>
          </div>
          <span class="w-14 text-[12px] text-ink-faint">{{ i === 0 ? '100%' : (f.value / store.maxFunnel * 100).toFixed(1) + '%' }}</span>
        </div>
      </div>
    </div>

    <!-- 商品热度 tab（COMP-ANA-A10：列=排名/商品/销量/销售额——DEC-ANA-FE-7） -->
    <div v-show="tab === 'hot'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>排名</th><th>商品</th><th class="text-right">销量</th><th class="text-right">销售额</th></tr></thead>
        <tbody>
          <tr v-if="store.loadingOverview"><td colspan="4" class="py-10 text-center text-ink-faint">加载中…</td></tr>
          <tr v-for="(p, i) in store.overview?.topProducts ?? []" v-else :key="p.productId">
            <td><span class="flex h-6 w-6 items-center justify-center rounded-full text-[12px] font-medium" :class="i < 3 ? 'bg-gold text-white' : 'bg-canvas-warm text-ink-soft'">{{ i + 1 }}</span></td>
            <td>
              <div class="flex items-center gap-3">
                <img v-if="p.imageUrl" :src="p.imageUrl" class="h-10 w-8 rounded-luxe object-cover" />
                <span v-else class="flex h-10 w-8 items-center justify-center rounded-luxe bg-canvas-warm text-[10px] text-ink-faint">N/A</span>
                <span class="font-medium text-ink">{{ p.name }}</span>
              </div>
            </td>
            <td class="text-right font-medium text-ink">{{ p.sales.toLocaleString() }}</td>
            <td class="text-right text-ink-soft">${{ Number(p.amount).toLocaleString('en-US', { minimumFractionDigits: 2 }) }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!store.loadingOverview && !(store.overview?.topProducts?.length)" title="暂无销量数据" />
    </div>
  </div>
</template>
