<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { lookbooks, guides } from '@/data/mock'
import { PlusIcon, PencilSquareIcon, TrashIcon, PhotoIcon, ClockIcon } from '@heroicons/vue/24/outline'

const tab = ref('lookbook')
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Content · CMS" title="Lookbook 与指南" subtitle="管理主题画册与婚礼筹备时间轴指南">
      <template #actions><button class="btn-primary"><PlusIcon class="h-4 w-4" />新增</button></template>
    </PageHeader>
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['lookbook','Lookbook 画册'],['guide','Wedding Guides 指南']]" :key="t[0]" @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <div v-show="tab === 'lookbook'" class="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <div v-for="l in lookbooks" :key="l.id" class="panel p-5">
        <div class="flex items-center justify-between"><PhotoIcon class="h-8 w-8 text-gold-deep" /><StatusBadge :tone="l.status === 'published' ? 'ok' : 'neutral'" :label="l.status === 'published' ? '已发布' : '草稿'" /></div>
        <h3 class="mt-3 font-display text-lg font-medium text-ink">{{ l.title }}</h3>
        <p class="text-[12px] text-ink-faint">{{ l.theme }} · {{ l.items }} 件商品锚点</p>
        <div class="mt-3 flex gap-1 border-t border-line pt-3"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</button><button class="btn-danger-ghost ml-auto"><TrashIcon class="h-4 w-4" /></button></div>
      </div>
    </div>

    <div v-show="tab === 'guide'" class="space-y-3">
      <div v-for="(g, i) in guides" :key="g.id" class="panel flex items-center gap-4 p-4">
        <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold/12 font-display font-semibold text-gold-deep">{{ i + 1 }}</span>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2"><span class="font-display text-base font-medium text-ink">{{ g.title }}</span><span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">{{ g.timeframe }}</span></div>
          <p class="text-[12px] text-ink-soft">{{ g.phase }} · {{ g.tasks }} 个待办任务</p>
        </div>
        <StatusBadge :tone="g.status === 'published' ? 'ok' : 'neutral'" :label="g.status === 'published' ? '已发布' : '草稿'" />
        <div class="flex gap-1"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" /></button><button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button></div>
      </div>
    </div>
  </div>
</template>
