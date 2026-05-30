<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { coupons, flashSales } from '@/data/mock'
import { PlusIcon, PencilSquareIcon, TrashIcon, ClockIcon } from '@heroicons/vue/24/outline'

const tab = ref('coupon')
const tone = { active: 'ok', expiring: 'warn', draft: 'neutral', scheduled: 'info' }
const label = { active: '进行中', expiring: '即将到期', draft: '草稿', scheduled: '已排期' }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Marketing" title="优惠券与促销" subtitle="管理优惠券、促销规则与限时秒杀">
      <template #actions><button class="btn-primary"><PlusIcon class="h-4 w-4" />新建</button></template>
    </PageHeader>
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['coupon','优惠券'],['flash','Flash Sale 秒杀']]" :key="t[0]" @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <!-- 优惠券 -->
    <div v-show="tab === 'coupon'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="c in coupons" :key="c.id" class="panel overflow-hidden">
        <div class="relative bg-gradient-to-br from-gold/15 to-blush/15 p-5">
          <div class="flex items-start justify-between">
            <div>
              <p class="font-display text-2xl font-semibold text-gold-deep">{{ c.value }}</p>
              <p class="mt-1 text-[13px] text-ink">{{ c.name }}</p>
            </div>
            <StatusBadge :tone="tone[c.status]" :label="label[c.status]" />
          </div>
          <div class="mt-3 inline-block rounded-luxe border border-dashed border-gold bg-white/60 px-3 py-1 font-mono text-[13px] font-medium text-ink">{{ c.code }}</div>
          <span class="absolute -left-2 top-1/2 h-4 w-4 rounded-full bg-canvas"></span>
          <span class="absolute -right-2 top-1/2 h-4 w-4 rounded-full bg-canvas"></span>
        </div>
        <div class="p-4 text-[12px] text-ink-soft">
          <p>门槛：{{ c.min > 0 ? '满 $' + c.min : '无门槛' }} · 类型：{{ c.type }}</p>
          <p v-if="c.start">有效期：{{ c.start }} → {{ c.end }}</p>
          <div class="mt-2 flex items-center justify-between">
            <span>已用 {{ c.used.toLocaleString() }} / {{ c.total > 9999 ? '不限' : c.total.toLocaleString() }}</span>
            <div class="flex gap-1"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" /></button><button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Flash Sale -->
    <div v-show="tab === 'flash'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>活动名称</th><th class="text-right">商品数</th><th>折扣</th><th>开始</th><th>结束</th><th>状态</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-for="f in flashSales" :key="f.id">
            <td class="flex items-center gap-2 font-medium text-ink"><ClockIcon class="h-4 w-4 text-gold-deep" />{{ f.name }}</td>
            <td class="text-right">{{ f.products }}</td>
            <td class="text-gold-deep">{{ f.discount }}</td>
            <td class="text-[12px] text-ink-soft">{{ f.start }}</td>
            <td class="text-[12px] text-ink-soft">{{ f.end }}</td>
            <td><StatusBadge :tone="tone[f.status]" :label="label[f.status]" /></td>
            <td class="text-right"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
