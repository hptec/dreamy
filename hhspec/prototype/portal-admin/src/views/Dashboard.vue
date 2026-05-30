<script setup>
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import SparkArea from '@/components/SparkArea.vue'
import { kpis, todos, overviewStats, gmvTrend, gmvLabels, categorySales, publishHistory } from '@/data/mock'
import {
  ArrowTrendingUpIcon, ArrowTrendingDownIcon, PlusIcon, ShoppingBagIcon,
  RocketLaunchIcon, SwatchIcon, TicketIcon, DocumentPlusIcon
} from '@heroicons/vue/24/outline'

const quickActions = [
  { label: '新增商品', icon: ShoppingBagIcon, to: '/products/new' },
  { label: '编辑首页', icon: SwatchIcon, to: '/site/home' },
  { label: '新建优惠券', icon: TicketIcon, to: '/marketing/promotions' },
  { label: '写一篇文章', icon: DocumentPlusIcon, to: '/content/blog' },
  { label: '发布站点', icon: RocketLaunchIcon, to: '/publish' }
]
// 饼图角度
let acc = 0
const donut = categorySales.map((s) => {
  const start = acc; acc += s.value
  return { ...s, start, end: acc }
})
function arc(start, end) {
  const r = 52, cx = 60, cy = 60
  const a0 = (start / 100) * 2 * Math.PI - Math.PI / 2
  const a1 = (end / 100) * 2 * Math.PI - Math.PI / 2
  const x0 = cx + r * Math.cos(a0), y0 = cy + r * Math.sin(a0)
  const x1 = cx + r * Math.cos(a1), y1 = cy + r * Math.sin(a1)
  const large = end - start > 50 ? 1 : 0
  return `M${cx},${cy} L${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1} Z`
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Overview" title="工作台" subtitle="2026 年 5 月 29 日 · 欢迎回来，Super Admin">
      <template #actions>
        <RouterLink to="/analytics" class="btn-outline">查看完整看板</RouterLink>
        <RouterLink to="/publish" class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />发布站点</RouterLink>
      </template>
    </PageHeader>

    <!-- KPI 卡 -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div v-for="k in kpis" :key="k.key" class="panel p-5">
        <p class="text-[12px] text-ink-faint">{{ k.label }}</p>
        <p class="mt-2 font-display text-3xl font-semibold text-ink">{{ k.value }}</p>
        <p class="mt-2 inline-flex items-center gap-1 text-[12px]" :class="k.up ? 'text-ok' : 'text-danger'">
          <component :is="k.up ? ArrowTrendingUpIcon : ArrowTrendingDownIcon" class="h-3.5 w-3.5" />
          {{ k.delta }} <span class="text-ink-faint">较昨日</span>
        </p>
      </div>
    </div>

    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
      <!-- GMV 趋势 -->
      <div class="panel p-6 lg:col-span-2">
        <div class="mb-4 flex items-center justify-between">
          <div>
            <p class="eyebrow">Sales Trend</p>
            <h3 class="font-display text-lg font-semibold text-ink">GMV 趋势 · 近 14 天</h3>
          </div>
          <div class="flex gap-2 text-[12px]">
            <button class="rounded-luxe bg-ink px-3 py-1 text-white">14 天</button>
            <button class="rounded-luxe border border-line px-3 py-1 text-ink-soft hover:bg-canvas-warm">30 天</button>
          </div>
        </div>
        <SparkArea :data="gmvTrend" :labels="gmvLabels" :height="240" />
      </div>

      <!-- 品类销售占比 -->
      <div class="panel p-6">
        <p class="eyebrow">Category Mix</p>
        <h3 class="font-display text-lg font-semibold text-ink">品类销售占比</h3>
        <div class="mt-4 flex items-center justify-center">
          <svg viewBox="0 0 120 120" class="h-36 w-36">
            <path v-for="d in donut" :key="d.name" :d="arc(d.start, d.end)" :fill="d.color" />
            <circle cx="60" cy="60" r="30" fill="#fff" />
          </svg>
        </div>
        <div class="mt-4 space-y-2">
          <div v-for="d in categorySales" :key="d.name" class="flex items-center gap-2 text-[12.5px]">
            <span class="h-2.5 w-2.5 rounded-full" :style="{ background: d.color }"></span>
            <span class="text-ink-soft">{{ d.name }}</span>
            <span class="ml-auto font-medium text-ink">{{ d.value }}%</span>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
      <!-- 待处理事务 -->
      <div class="panel p-6 lg:col-span-2">
        <p class="eyebrow">To-do</p>
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">待处理事务</h3>
        <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <RouterLink v-for="t in todos" :key="t.label" :to="t.to"
            class="group rounded-luxe border border-line bg-canvas/40 p-4 transition-colors hover:border-gold hover:bg-canvas-warm">
            <p class="font-display text-2xl font-semibold" :class="{
              'text-danger': t.tone === 'danger', 'text-warn': t.tone === 'warn', 'text-info': t.tone === 'info'
            }">{{ t.count }}</p>
            <p class="mt-1 text-[12px] text-ink-soft group-hover:text-ink">{{ t.label }}</p>
          </RouterLink>
        </div>
      </div>

      <!-- 快捷入口 -->
      <div class="panel p-6">
        <p class="eyebrow">Quick Actions</p>
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">快捷入口</h3>
        <div class="space-y-2">
          <RouterLink v-for="a in quickActions" :key="a.label" :to="a.to"
            class="flex items-center gap-3 rounded-luxe border border-line px-4 py-2.5 text-[13px] text-ink-soft transition-colors hover:border-gold hover:bg-canvas-warm hover:text-ink">
            <component :is="a.icon" class="h-4.5 w-4.5 text-gold-deep" />
            {{ a.label }}
            <PlusIcon class="ml-auto h-4 w-4 text-ink-faint" />
          </RouterLink>
        </div>
      </div>
    </div>

    <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      <!-- 商品/用户总览 -->
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">商品总览</h3>
        <div class="grid grid-cols-4 gap-3">
          <div v-for="s in overviewStats.product" :key="s.label" class="text-center">
            <p class="font-display text-2xl font-semibold text-gold-deep">{{ s.value }}</p>
            <p class="mt-1 text-[11px] text-ink-faint">{{ s.label }}</p>
          </div>
        </div>
        <hr class="my-5 border-line" />
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">用户总览</h3>
        <div class="grid grid-cols-4 gap-3">
          <div v-for="s in overviewStats.user" :key="s.label" class="text-center">
            <p class="font-display text-2xl font-semibold text-sage">{{ s.value.toLocaleString() }}</p>
            <p class="mt-1 text-[11px] text-ink-faint">{{ s.label }}</p>
          </div>
        </div>
      </div>

      <!-- 最近发布 -->
      <div class="panel p-6">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-display text-lg font-semibold text-ink">最近发布</h3>
          <RouterLink to="/publish" class="text-[12.5px] text-gold-deep hover:underline">查看全部</RouterLink>
        </div>
        <div class="space-y-3">
          <div v-for="p in publishHistory" :key="p.id" class="flex items-center gap-3 text-[13px]">
            <StatusBadge tone="ok" label="成功" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-ink">{{ p.note }}</p>
              <p class="text-[11px] text-ink-faint">{{ p.time }} · {{ p.author }} · {{ p.pages }} 页 · {{ p.duration }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
