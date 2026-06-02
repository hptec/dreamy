<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import { refunds } from '@/data/mock'
import { CheckIcon, XMarkIcon } from '@heroicons/vue/24/outline'

const status = ref('all')
const tabs = [['all','全部'],['pending','待审批'],['approved','已同意'],['rejected','已拒绝']]
const tone = { pending: 'warn', approved: 'ok', rejected: 'danger' }
const label = { pending: '待审批', approved: '已同意', rejected: '已拒绝' }
const cur = { USD: '$', CAD: 'C$', AUD: 'A$', GBP: '£' }
const filtered = computed(() => refunds.filter((r) => status.value === 'all' || r.status === status.value))
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="After-Sales" title="退款工单" subtitle="审批退款申请并记录处理日志" />
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in tabs" :key="t[0]" @click="status = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="status === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>
    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>工单号</th><th>关联订单</th><th>客户</th><th class="text-right">退款金额</th><th>退款原因</th><th>申请时间</th><th>状态</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-for="r in filtered" :key="r.id">
            <td class="font-mono text-[12px] font-medium text-gold-deep">{{ r.id }}</td>
            <td class="font-mono text-[12px] text-ink-soft">{{ r.order }}</td>
            <td class="text-ink">{{ r.customer }}</td>
            <td class="text-right font-medium text-ink">{{ cur[r.currency] }}{{ r.amount.toLocaleString() }}</td>
            <td class="text-ink-soft">{{ r.reason }}</td>
            <td class="text-[12px] text-ink-faint">{{ r.date }}</td>
            <td><StatusBadge :tone="tone[r.status]" :label="label[r.status]" /></td>
            <td>
              <div v-if="r.status === 'pending'" class="flex items-center justify-end gap-1">
                <button class="btn-ghost text-ok"><CheckIcon class="h-4 w-4" />同意</button>
                <button class="btn-danger-ghost"><XMarkIcon class="h-4 w-4" />拒绝</button>
              </div>
              <span v-else class="text-[12px] text-ink-faint">已处理</span>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="filtered.length === 0" title="暂无退款工单" />
    </div>
  </div>
</template>
