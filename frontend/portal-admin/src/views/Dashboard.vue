<script setup lang="ts">
// PAGE-ANA-A01 / COMP-ANA-A01~A06：工作台（布局栅格零改动；mock → E-ANA-01 + E-ANA-02 donut）
// DEC-ANA-FE-2 KPI 本月口径 delta 移除；FE-3 待办 3 瓦片；FE-4 非契约面板空态；FE-5 趋势客户端切片
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import SparkArea from '@/components/SparkArea.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { PlusIcon, ShoppingBagIcon, TicketIcon, DocumentPlusIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'

const store = useDashboardStore()
const auth = useAuthStore()
const toast = useToastStore()

// DEC-ANA-FE-4 ②：快捷入口 3 项（剔除被推迟 CMS 范围的 /site/home、/publish）
const quickActions = [
  { label: '新增商品', icon: ShoppingBagIcon, to: '/products/new' },
  { label: '新建优惠券', icon: TicketIcon, to: '/promotions' },
  { label: '写一篇文章', icon: DocumentPlusIcon, to: '/content/blog' },
]

// KPI 卡（DEC-ANA-FE-2：本月口径，delta 行移除）
const kpiCards = computed(() => {
  const k = store.dashboard?.kpis
  return [
    { label: '本月 GMV', value: k ? '$' + Number(k.gmvMonth).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '—' },
    { label: '订单数', value: k ? Number(k.orderCount).toLocaleString() : '—' },
    { label: '客单价', value: k ? '$' + Number(k.avgOrderValue).toFixed(2) : '—' },
    { label: '退款率', value: k ? Number(k.refundRate).toFixed(1) + '%' : '—' },
  ]
})

// donut（arc 函数复刻原型；色板取既有 token 色数组，不引入新色值）
const DONUT_COLORS = ['#C19A6B', '#8B9D83', '#D8A7A0', '#9C8E7E', '#6B7280']
const donut = computed(() => {
  let acc = 0
  return store.categorySales.map((s, i) => {
    const start = acc
    acc += Number(s.share)
    return { ...s, start, end: acc, color: DONUT_COLORS[i % DONUT_COLORS.length] }
  })
})
function arc(start: number, end: number): string {
  const r = 52, cx = 60, cy = 60
  const a0 = (start / 100) * 2 * Math.PI - Math.PI / 2
  const a1 = (Math.min(end, 99.999) / 100) * 2 * Math.PI - Math.PI / 2
  const x0 = cx + r * Math.cos(a0), y0 = cy + r * Math.sin(a0)
  const x1 = cx + r * Math.cos(a1), y1 = cy + r * Math.sin(a1)
  const large = end - start > 50 ? 1 : 0
  return `M${cx},${cy} L${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1} Z`
}

// PageHeader subtitle 动态日期（替换 mock 写死日期）
const todayText = computed(() => {
  const d = new Date()
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 欢迎回来，${auth.admin?.name || '管理员'}`
})

function load() {
  store.fetch().catch(() => toast.error('加载工作台数据失败'))
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <!-- DEC-ANA-FE-4 ④：actions 仅「查看完整看板」（「发布站点」移除——Publish 范围外） -->
    <PageHeader eyebrow="Overview" title="工作台" :subtitle="todayText">
      <template #actions>
        <RouterLink to="/analytics" class="btn-outline">查看完整看板</RouterLink>
      </template>
    </PageHeader>

    <!-- 页面级错误态 -->
    <div v-if="store.pageError" class="panel p-12 text-center">
      <p class="text-[14px] text-ink-soft">看板数据加载失败</p>
      <button class="btn-outline mt-4" @click="load"><ArrowPathIcon class="h-4 w-4" />重试</button>
    </div>

    <template v-else>
      <!-- KPI 卡 ×4（COMP-ANA-A01） -->
      <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div v-for="k in kpiCards" :key="k.label" class="panel p-5">
          <p class="text-[12px] text-ink-faint">{{ k.label }}</p>
          <p v-if="store.loading" class="mt-2 h-9 w-28 animate-pulse rounded bg-canvas-warm"></p>
          <p v-else class="mt-2 font-display text-3xl font-semibold text-ink">{{ k.value }}</p>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <!-- GMV 趋势（COMP-ANA-A02：14/30 客户端切片——DEC-ANA-FE-5） -->
        <div class="panel p-6 lg:col-span-2">
          <div class="mb-4 flex items-center justify-between">
            <div>
              <p class="eyebrow">Sales Trend</p>
              <h3 class="font-display text-lg font-semibold text-ink">GMV 趋势 · 近 {{ store.trendWindow }} 天</h3>
            </div>
            <div class="flex gap-2 text-[12px]">
              <button
                v-for="w in [14, 30] as const"
                :key="w"
                class="rounded-luxe px-3 py-1"
                :class="store.trendWindow === w ? 'bg-ink text-white' : 'border border-line text-ink-soft hover:bg-canvas-warm'"
                @click="store.trendWindow = w"
              >{{ w }} 天</button>
            </div>
          </div>
          <SparkArea v-if="store.trendSlice.values.length" :data="store.trendSlice.values.map(Number)" :labels="store.trendSlice.labels" :height="240" />
          <div v-else class="flex h-60 items-center justify-center text-[13px] text-ink-faint">{{ store.loading ? '加载中…' : '暂无趋势数据' }}</div>
        </div>

        <!-- 品类销售占比（COMP-ANA-A03：E-ANA-02 数据源；403 → 空态） -->
        <div class="panel p-6">
          <p class="eyebrow">Category Mix</p>
          <h3 class="font-display text-lg font-semibold text-ink">品类销售占比</h3>
          <EmptyState v-if="store.donutState === 'forbidden'" title="暂无权限查看" hint="需要数据看板权限。" />
          <EmptyState v-else-if="store.donutState === 'error'" title="数据暂不可用" />
          <template v-else>
            <div class="mt-4 flex items-center justify-center">
              <svg viewBox="0 0 120 120" class="h-36 w-36">
                <path v-for="d in donut" :key="d.categoryId" :d="arc(d.start, d.end)" :fill="d.color" />
                <circle cx="60" cy="60" r="30" fill="#fff" />
              </svg>
            </div>
            <div class="mt-4 space-y-2">
              <div v-for="d in donut" :key="d.categoryId" class="flex items-center gap-2 text-[12.5px]">
                <span class="h-2.5 w-2.5 rounded-full" :style="{ background: d.color }"></span>
                <span class="text-ink-soft">{{ d.categoryName }}</span>
                <span class="ml-auto font-medium text-ink">{{ Number(d.share).toFixed(1) }}%</span>
              </div>
              <p v-if="!donut.length && !store.loading" class="text-center text-[12px] text-ink-faint">暂无销售数据</p>
            </div>
          </template>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <!-- 待办瓦片 ×3（COMP-ANA-A04 / DEC-ANA-FE-3） -->
        <div class="panel p-6 lg:col-span-2">
          <p class="eyebrow">To-do</p>
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">待处理事务</h3>
          <div class="grid grid-cols-3 gap-3">
            <RouterLink
              v-for="t in store.todoTiles"
              :key="t.label"
              :to="t.to"
              class="group rounded-luxe border border-line bg-canvas/40 p-4 transition-colors hover:border-gold hover:bg-canvas-warm"
            >
              <p class="font-display text-2xl font-semibold" :class="{ 'text-danger': t.tone === 'danger', 'text-warn': t.tone === 'warn', 'text-info': t.tone === 'info' }">{{ t.count }}</p>
              <p class="mt-1 text-[12px] text-ink-soft group-hover:text-ink">{{ t.label }}</p>
            </RouterLink>
            <p v-if="!store.todoTiles.length" class="col-span-3 py-6 text-center text-[13px] text-ink-faint">{{ store.loading ? '加载中…' : '暂无待办' }}</p>
          </div>
        </div>

        <!-- 快捷入口（COMP-ANA-A05：静态 3 项） -->
        <div class="panel p-6">
          <p class="eyebrow">Quick Actions</p>
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">快捷入口</h3>
          <div class="space-y-2">
            <RouterLink
              v-for="a in quickActions"
              :key="a.label"
              :to="a.to"
              class="flex items-center gap-3 rounded-luxe border border-line px-4 py-2.5 text-[13px] text-ink-soft transition-colors hover:border-gold hover:bg-canvas-warm hover:text-ink"
            >
              <component :is="a.icon" class="h-4.5 w-4.5 text-gold-deep" />
              {{ a.label }}
              <PlusIcon class="ml-auto h-4 w-4 text-ink-faint" />
            </RouterLink>
          </div>
        </div>
      </div>

      <!-- COMP-ANA-A06 / DEC-ANA-FE-4 ③：商品/用户总览、最近发布 → 卡片壳保留 + EmptyState（不展示 mock 假数） -->
      <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">商品 / 用户总览</h3>
          <EmptyState title="待运营统计接入" hint="该面板归属站点 CMS 与运营统计范围，由后续独立变更接入真实数据。" />
        </div>
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">最近发布</h3>
          <EmptyState title="待发布中心接入" hint="发布历史归属 Publish 范围，由后续独立变更接入。" />
        </div>
      </div>
    </template>
  </div>
</template>
