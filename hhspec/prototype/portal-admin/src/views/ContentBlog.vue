<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { blogPosts } from '@/data/mock'
import { PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, EyeIcon } from '@heroicons/vue/24/outline'

const filter = ref('all')
const list = computed(() => blogPosts.filter((p) => filter.value === 'all' || p.status === filter.value))
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Content · CMS" title="Blog 文章" subtitle="撰写婚礼策划文章，发布后生成静态文章页">
      <template #actions><button class="btn-primary"><PlusIcon class="h-4 w-4" />写文章</button></template>
    </PageHeader>
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['all','全部'],['published','已发布'],['draft','草稿']]" :key="t[0]" @click="filter = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="filter === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="p in list" :key="p.id" class="panel overflow-hidden">
        <div class="relative aspect-[16/10]"><img :src="p.cover" class="h-full w-full object-cover" /><StatusBadge class="absolute left-3 top-3" :tone="p.status === 'published' ? 'ok' : 'neutral'" :label="p.status === 'published' ? '已发布' : '草稿'" /></div>
        <div class="p-4">
          <span class="text-[11px] uppercase tracking-wide text-gold-deep">{{ p.category }}</span>
          <h3 class="mt-1 font-display text-lg font-medium leading-snug text-ink">{{ p.title }}</h3>
          <p class="mt-2 text-[12px] text-ink-faint">{{ p.author }} · {{ p.date }} · {{ p.views.toLocaleString() }} 阅读</p>
          <div class="mt-3 flex items-center gap-1 border-t border-line pt-3">
            <button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</button>
            <button class="btn-ghost"><EyeIcon class="h-4 w-4" />预览</button>
            <button v-if="p.status === 'draft'" class="btn-ghost text-gold-deep"><RocketLaunchIcon class="h-4 w-4" />发布</button>
            <button class="btn-danger-ghost ml-auto"><TrashIcon class="h-4 w-4" /></button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
