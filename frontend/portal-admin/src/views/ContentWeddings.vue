<script setup>
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { realWeddings } from '@/data/mock'
import { PlusIcon, PencilSquareIcon, TrashIcon, ShoppingBagIcon, MapPinIcon } from '@heroicons/vue/24/outline'
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Content · CMS" title="Real Weddings" subtitle="管理真实婚礼故事画廊与 Shop the Look 关联">
      <template #actions><button class="btn-primary"><PlusIcon class="h-4 w-4" />新增婚礼故事</button></template>
    </PageHeader>
    <div class="grid grid-cols-1 gap-5 sm:grid-cols-2">
      <div v-for="w in realWeddings" :key="w.id" class="panel overflow-hidden">
        <div class="grid grid-cols-[140px_1fr]">
          <div class="relative"><img :src="w.cover" class="h-full w-full object-cover" /></div>
          <div class="p-4">
            <div class="flex items-start justify-between">
              <div>
                <span class="rounded-full bg-sage/15 px-2 py-0.5 text-[11px] text-sage">{{ w.theme }}</span>
                <h3 class="mt-1.5 font-display text-xl font-medium text-ink">{{ w.couple }}</h3>
                <p class="flex items-center gap-1 text-[12px] text-ink-soft"><MapPinIcon class="h-3.5 w-3.5" />{{ w.location }}</p>
                <p class="text-[11px] text-ink-faint">{{ w.date }}</p>
              </div>
              <StatusBadge :tone="w.status === 'published' ? 'ok' : 'neutral'" :label="w.status === 'published' ? '已发布' : '草稿'" />
            </div>
            <div class="mt-3 flex items-center gap-1 border-t border-line pt-3">
              <span class="flex items-center gap-1 text-[12px] text-ink-faint"><ShoppingBagIcon class="h-3.5 w-3.5" />Shop the Look · {{ w.shopItems }} 件</span>
              <div class="ml-auto flex gap-1"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" /></button><button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
