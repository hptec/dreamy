<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { navConfig } from '@/data/mock'
import {
  Bars3Icon, PlusIcon, TrashIcon, RocketLaunchIcon, EyeIcon, ChevronRightIcon
} from '@heroicons/vue/24/outline'

const tab = ref('main')
const main = ref(JSON.parse(JSON.stringify(navConfig.main)))
const footer = ref(JSON.parse(JSON.stringify(navConfig.footer)))
const announcements = ref([
  'Complimentary worldwide shipping on orders over $200',
  'Pay in 4 interest-free installments with Klarna & Afterpay',
  'Order fabric swatches — try your colors before you commit'
])
const dirty = ref(false)
const touch = () => (dirty.value = true)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Site Builder" title="导航与页脚" subtitle="配置全站主导航、Mega Menu、页脚栏目与顶部公告条">
      <template #actions>
        <span v-if="dirty" class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>未发布改动</span>
        <button class="btn-outline"><EyeIcon class="h-4 w-4" />前台预览</button>
        <button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存并重新生成 header</button>
      </template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['main','主导航 & Mega Menu'],['footer','页脚栏目'],['announce','公告条']]" :key="t[0]"
        @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <!-- 主导航 -->
    <div v-show="tab === 'main'" class="space-y-3">
      <div v-for="(item, i) in main" :key="i" class="panel p-4">
        <div class="flex items-center gap-3">
          <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
          <input v-model="item.label" @input="touch" class="field w-48 font-medium" />
          <div class="flex items-center text-[12px] text-ink-faint"><span class="px-2">链接</span><input v-model="item.href" @input="touch" class="field w-56 text-[12px]" /></div>
          <div class="ml-auto flex items-center gap-2 text-[12px] text-ink-soft">
            <span>Mega Menu 列：</span>
            <select v-model="item.columns" @change="touch" class="field w-16 py-1 text-[12px]"><option :value="0">无</option><option :value="1">1</option><option :value="2">2</option><option :value="3">3</option></select>
            <button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button>
          </div>
        </div>
        <div v-if="item.columns > 0" class="mt-3 grid gap-3 border-t border-line pt-3" :style="{ gridTemplateColumns: `repeat(${item.columns}, 1fr)` }">
          <div v-for="col in item.columns" :key="col" class="rounded-luxe border border-dashed border-line p-3">
            <input class="field mb-2 text-[12px] font-medium" :placeholder="`列 ${col} 标题（如 Shop by Silhouette）`" @input="touch" />
            <div class="space-y-1.5">
              <div class="flex items-center gap-1.5 text-[12px] text-ink-soft"><ChevronRightIcon class="h-3 w-3" /><input class="flex-1 border-b border-line bg-transparent py-0.5 outline-none focus:border-gold" placeholder="子链接文本" @input="touch" /></div>
              <button class="btn-ghost text-[11px]"><PlusIcon class="h-3 w-3" />添加链接</button>
            </div>
          </div>
        </div>
      </div>
      <button class="btn-outline w-full"><PlusIcon class="h-4 w-4" />添加主导航项</button>
    </div>

    <!-- 页脚 -->
    <div v-show="tab === 'footer'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="(col, i) in footer" :key="i" class="panel p-4">
        <input v-model="col.title" @input="touch" class="field mb-3 font-medium" />
        <div class="space-y-1.5">
          <div v-for="n in col.links" :key="n" class="flex items-center gap-1.5 text-[12px]">
            <Bars3Icon class="h-3 w-3 text-ink-faint" />
            <input class="flex-1 border-b border-line bg-transparent py-1 text-ink-soft outline-none focus:border-gold" :placeholder="`链接 ${n}`" @input="touch" />
          </div>
          <button class="btn-ghost text-[11px]"><PlusIcon class="h-3 w-3" />添加</button>
        </div>
      </div>
    </div>

    <!-- 公告条 -->
    <div v-show="tab === 'announce'" class="panel max-w-2xl p-6">
      <p class="field-label">顶部滚动公告（前台自动轮播）</p>
      <div class="space-y-2">
        <div v-for="(a, i) in announcements" :key="i" class="flex items-center gap-2">
          <span class="text-[12px] text-ink-faint">{{ i + 1 }}</span>
          <input v-model="announcements[i]" @input="touch" class="field text-[13px]" />
          <button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button>
        </div>
      </div>
      <button class="btn-ghost mt-3"><PlusIcon class="h-4 w-4" />添加公告</button>
      <div class="mt-5 rounded-luxe bg-ink px-4 py-2 text-center text-[12px] text-canvas">{{ announcements[0] }}</div>
    </div>
  </div>
</template>
