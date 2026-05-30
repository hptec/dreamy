<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import SparkArea from '@/components/SparkArea.vue'
import { gmvTrend, gmvLabels, funnel, trafficSources, categorySales, products } from '@/data/mock'
import { ArrowDownTrayIcon } from '@heroicons/vue/24/outline'

const tab = ref('sales')
const maxFunnel = funnel[0].value
const topProducts = [...products].sort((a, b) => b.sales - a.sales).slice(0, 6)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Analytics" title="数据看板" subtitle="销售、流量、转化与商品热度分析">
      <template #actions><button class="btn-outline"><ArrowDownTrayIcon class="h-4 w-4" />导出报表</button></template>
    </PageHeader>
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['sales','销售分析'],['traffic','流量分析'],['funnel','转化漏斗'],['hot','商品热度']]" :key="t[0]" @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <!-- 销售 -->
    <div v-show="tab === 'sales'" class="space-y-6">
      <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <div class="panel p-5"><p class="text-[12px] text-ink-faint">本月 GMV</p><p class="mt-1 font-display text-2xl font-semibold text-ink">$1.24M</p><p class="text-[12px] text-ok">+14.2%</p></div>
        <div class="panel p-5"><p class="text-[12px] text-ink-faint">订单数</p><p class="mt-1 font-display text-2xl font-semibold text-ink">4,820</p><p class="text-[12px] text-ok">+9.1%</p></div>
        <div class="panel p-5"><p class="text-[12px] text-ink-faint">客单价</p><p class="mt-1 font-display text-2xl font-semibold text-ink">$257</p><p class="text-[12px] text-ok">+4.6%</p></div>
        <div class="panel p-5"><p class="text-[12px] text-ink-faint">退款率</p><p class="mt-1 font-display text-2xl font-semibold text-ink">2.3%</p><p class="text-[12px] text-danger">+0.4%</p></div>
      </div>
      <div class="panel p-6"><h3 class="mb-4 font-display text-lg font-semibold text-ink">GMV 趋势</h3><SparkArea :data="gmvTrend" :labels="gmvLabels" :height="260" /></div>
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">品类销售占比</h3>
        <div class="space-y-3">
          <div v-for="c in categorySales" :key="c.name">
            <div class="mb-1 flex justify-between text-[13px]"><span class="text-ink-soft">{{ c.name }}</span><span class="font-medium text-ink">{{ c.value }}%</span></div>
            <div class="h-2.5 overflow-hidden rounded-full bg-line"><div class="h-full rounded-full" :style="{ width: c.value + '%', background: c.color }"></div></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 流量 -->
    <div v-show="tab === 'traffic'" class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">访客来源</h3>
        <div class="space-y-3">
          <div v-for="s in trafficSources" :key="s.name">
            <div class="mb-1 flex justify-between text-[13px]"><span class="text-ink-soft">{{ s.name }}</span><span class="font-medium text-ink">{{ s.value }}%</span></div>
            <div class="h-2.5 overflow-hidden rounded-full bg-line"><div class="h-full rounded-full bg-gold" :style="{ width: s.value + '%' }"></div></div>
          </div>
        </div>
      </div>
      <div class="panel p-6">
        <h3 class="mb-4 font-display text-lg font-semibold text-ink">设备占比</h3>
        <div class="flex items-center justify-around py-8">
          <div class="text-center"><p class="font-display text-3xl font-semibold text-gold-deep">68%</p><p class="text-[12px] text-ink-faint">移动端</p></div>
          <div class="text-center"><p class="font-display text-3xl font-semibold text-sage">27%</p><p class="text-[12px] text-ink-faint">桌面端</p></div>
          <div class="text-center"><p class="font-display text-3xl font-semibold text-blush">5%</p><p class="text-[12px] text-ink-faint">平板</p></div>
        </div>
      </div>
    </div>

    <!-- 漏斗 -->
    <div v-show="tab === 'funnel'" class="panel p-6">
      <h3 class="mb-6 font-display text-lg font-semibold text-ink">转化漏斗</h3>
      <div class="mx-auto max-w-2xl space-y-2">
        <div v-for="(f, i) in funnel" :key="f.stage" class="flex items-center gap-4">
          <span class="w-24 text-right text-[13px] text-ink-soft">{{ f.stage }}</span>
          <div class="relative flex-1">
            <div class="flex h-12 items-center justify-center rounded-luxe text-[13px] font-medium text-white" :style="{ width: (f.value / maxFunnel * 100) + '%', background: f.color, marginLeft: ((1 - f.value/maxFunnel) * 50) + '%' }">{{ f.value.toLocaleString() }}</div>
          </div>
          <span class="w-14 text-[12px] text-ink-faint">{{ i === 0 ? '100%' : (f.value / funnel[0].value * 100).toFixed(1) + '%' }}</span>
        </div>
      </div>
    </div>

    <!-- 商品热度 -->
    <div v-show="tab === 'hot'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>排名</th><th>商品</th><th class="text-right">销量</th><th class="text-right">库存</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="(p, i) in topProducts" :key="p.id">
            <td><span class="flex h-6 w-6 items-center justify-center rounded-full text-[12px] font-medium" :class="i < 3 ? 'bg-gold text-white' : 'bg-canvas-warm text-ink-soft'">{{ i + 1 }}</span></td>
            <td><div class="flex items-center gap-3"><img :src="p.img" class="h-10 w-8 rounded-luxe object-cover" /><span class="font-medium text-ink">{{ p.name }}</span></div></td>
            <td class="text-right font-medium text-ink">{{ p.sales }}</td>
            <td class="text-right text-ink-soft">{{ p.stock }}</td>
            <td><span class="text-[12px]" :class="p.status === 'published' ? 'text-ok' : 'text-ink-faint'">{{ p.status === 'published' ? '在售' : '草稿' }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
