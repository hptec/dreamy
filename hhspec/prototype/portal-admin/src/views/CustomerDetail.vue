<script setup>
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { customerDetail, ORDER_STATUS } from '@/data/mock'
import { ArrowLeftIcon, NoSymbolIcon, KeyIcon, TagIcon, MapPinIcon } from '@heroicons/vue/24/outline'

const router = useRouter()
const c = customerDetail
const stats = [
  { label: '订单数', value: c.stats.orders }, { label: '累计消费', value: '$' + c.stats.spent.toLocaleString() },
  { label: '收藏', value: c.stats.wishlist }, { label: '评价', value: c.stats.reviews }
]
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader :eyebrow="c.id" title="用户详情">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/customers')"><ArrowLeftIcon class="h-4 w-4" />返回</button>
        <button class="btn-outline"><TagIcon class="h-4 w-4" />打标签</button>
        <button class="btn-outline"><KeyIcon class="h-4 w-4" />重置密码</button>
        <button class="btn-danger-ghost"><NoSymbolIcon class="h-4 w-4" />禁用账户</button>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[320px_1fr]">
      <!-- 左：资料卡 -->
      <div class="space-y-6">
        <div class="panel p-6 text-center">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-ink text-xl font-medium text-canvas">{{ c.avatar }}</span>
          <p class="mt-3 font-display text-xl font-semibold text-ink">{{ c.name }}</p>
          <p class="text-[12px] text-ink-soft">{{ c.email }}</p>
          <div class="mt-2 flex justify-center gap-2">
            <StatusBadge tone="warn" :label="c.tier" :dot="false" />
            <StatusBadge :tone="c.status === 'active' ? 'ok' : 'danger'" :label="c.status === 'active' ? '正常' : '已禁用'" />
          </div>
          <div class="mt-4 grid grid-cols-2 gap-3 border-t border-line pt-4">
            <div v-for="s in stats" :key="s.label"><p class="font-display text-lg font-semibold text-gold-deep">{{ s.value }}</p><p class="text-[11px] text-ink-faint">{{ s.label }}</p></div>
          </div>
          <p class="mt-4 text-[11px] text-ink-faint">注册于 {{ c.joined }} · {{ c.phone }}</p>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><MapPinIcon class="h-4 w-4 text-gold-deep" />收货地址</h3>
          <div v-for="a in c.addresses" :key="a.line" class="rounded-luxe border border-line p-3 text-[12px] text-ink-soft">
            <p class="mb-1 text-[11px] font-medium text-gold-deep">{{ a.label }}</p>{{ a.line }}<br />{{ a.country }}
          </div>
        </div>
      </div>

      <!-- 右：订单 + Wishlist + 行为 -->
      <div class="space-y-6">
        <div class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">最近订单</h3></div>
          <table class="data-table">
            <thead><tr><th>订单号</th><th class="text-right">金额</th><th>状态</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="o in c.recentOrders" :key="o.id">
                <td class="font-mono text-[12px] text-gold-deep">{{ o.id }}</td>
                <td class="text-right font-medium text-ink">${{ o.total.toLocaleString() }}</td>
                <td><StatusBadge :tone="ORDER_STATUS[o.status].tone" :label="ORDER_STATUS[o.status].label" /></td>
                <td class="text-[12px] text-ink-faint">{{ o.date }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">Wishlist 收藏</h3>
          <div class="grid grid-cols-4 gap-3">
            <div v-for="p in c.wishlist" :key="p.id"><img :src="p.img" class="aspect-[3/4] w-full rounded-luxe object-cover" /><p class="mt-1 truncate text-[11px] text-ink-soft">{{ p.name }}</p></div>
          </div>
        </div>
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">行为时间线</h3>
          <div class="space-y-3">
            <div v-for="(a, i) in c.activity" :key="i" class="relative pl-5">
              <span class="absolute left-0 top-1.5 h-2 w-2 rounded-full bg-gold"></span>
              <span v-if="i < c.activity.length - 1" class="absolute left-[3px] top-3.5 h-full w-px bg-line"></span>
              <p class="text-[13px] text-ink">{{ a.label }}</p>
              <p class="text-[11px] text-ink-faint">{{ a.time }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
