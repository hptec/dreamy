<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { emailSubscribers, emailCampaigns } from '@/data/mock'
import { PlusIcon, PaperAirplaneIcon, EnvelopeIcon } from '@heroicons/vue/24/outline'

const tab = ref('campaign')
const cTone = { sent: 'ok', draft: 'neutral' }
const cLabel = { sent: '已发送', draft: '草稿' }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Marketing" title="邮件营销" subtitle="管理订阅用户、邮件模板与发送记录">
      <template #actions><button class="btn-primary"><PlusIcon class="h-4 w-4" />新建邮件</button></template>
    </PageHeader>
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div class="panel p-5"><p class="text-[12px] text-ink-faint">订阅用户</p><p class="mt-1 font-display text-2xl font-semibold text-ink">42,680</p></div>
      <div class="panel p-5"><p class="text-[12px] text-ink-faint">本月新增订阅</p><p class="mt-1 font-display text-2xl font-semibold text-ok">+3,240</p></div>
      <div class="panel p-5"><p class="text-[12px] text-ink-faint">平均打开率</p><p class="mt-1 font-display text-2xl font-semibold text-gold-deep">39.6%</p></div>
      <div class="panel p-5"><p class="text-[12px] text-ink-faint">平均点击率</p><p class="mt-1 font-display text-2xl font-semibold text-sage">7.8%</p></div>
    </div>

    <div class="mb-4 mt-6 flex gap-1 border-b border-line">
      <button v-for="t in [['campaign','发送记录'],['sub','订阅用户']]" :key="t[0]" @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <div v-show="tab === 'campaign'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>邮件主题</th><th class="text-right">收件人</th><th class="text-right">打开率</th><th class="text-right">点击率</th><th>发送时间</th><th>状态</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-for="c in emailCampaigns" :key="c.id">
            <td class="flex items-center gap-2 font-medium text-ink"><EnvelopeIcon class="h-4 w-4 text-gold-deep" />{{ c.name }}</td>
            <td class="text-right">{{ c.recipients.toLocaleString() }}</td>
            <td class="text-right text-ok">{{ c.openRate }}</td>
            <td class="text-right text-sage">{{ c.clickRate }}</td>
            <td class="text-[12px] text-ink-faint">{{ c.sent || '—' }}</td>
            <td><StatusBadge :tone="cTone[c.status]" :label="cLabel[c.status]" /></td>
            <td class="text-right"><button class="btn-ghost"><PaperAirplaneIcon class="h-4 w-4" />{{ c.status === 'draft' ? '发送' : '查看' }}</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-show="tab === 'sub'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>邮箱</th><th>订阅来源</th><th>订阅时间</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="s in emailSubscribers" :key="s.email">
            <td class="text-ink">{{ s.email }}</td>
            <td class="text-ink-soft">{{ s.source }}</td>
            <td class="text-[12px] text-ink-faint">{{ s.date }}</td>
            <td><StatusBadge :tone="s.status === 'subscribed' ? 'ok' : 'neutral'" :label="s.status === 'subscribed' ? '已订阅' : '已退订'" /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
