<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import { PlusIcon, Bars3Icon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'

const tab = ref('category')
const categories = ref([
  { name: 'Wedding Dresses', count: 48, children: ['A-Line', 'Mermaid', 'Ball Gown', 'Sheath', 'Short'] },
  { name: 'Special Occasion', count: 36, children: ['Bridesmaid', 'Mother of the Bride', 'Prom', 'Cocktail', 'Wedding Guest'] },
  { name: 'Accessories', count: 28, children: ['Veils', 'Shoes', 'Jewelry', 'Headpieces'] }
])
const themes = ref([
  { name: 'Beach', products: 18, cover: '/competitor-refs/kissprom/wedding-beach-short-05.jpg', online: true },
  { name: 'Garden', products: 24, cover: '/competitor-refs/davidsbridal/bridesmaid-sage-01.jpg', online: true },
  { name: 'Vineyard', products: 15, cover: '/competitor-refs/kissprom/prom-champagne-lace-05.jpg', online: true },
  { name: 'Forest', products: 12, cover: '/competitor-refs/kissprom/wedding-aline-longsleeve-06.jpg', online: true },
  { name: 'Boho', products: 9, cover: '/competitor-refs/birdygrey/bridesmaid-pink-bryten-02.jpg', online: false }
])
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="品类与主题" subtitle="管理商品品类树与 Outdoor 婚礼主题">
      <template #actions><button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存并发布</button></template>
    </PageHeader>
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['category','品类树'],['theme','Outdoor 主题']]" :key="t[0]" @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <!-- 品类树 -->
    <div v-show="tab === 'category'" class="space-y-3">
      <div v-for="cat in categories" :key="cat.name" class="panel p-4">
        <div class="flex items-center gap-3">
          <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
          <span class="font-display text-base font-medium text-ink">{{ cat.name }}</span>
          <span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">{{ cat.count }} 件</span>
          <div class="ml-auto flex gap-1"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" /></button><button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button></div>
        </div>
        <div class="mt-3 flex flex-wrap gap-2 border-t border-line pt-3">
          <span v-for="ch in cat.children" :key="ch" class="flex items-center gap-1 rounded-luxe border border-line px-2.5 py-1 text-[12px] text-ink-soft">
            <ChevronRightIcon class="h-3 w-3 text-ink-faint" />{{ ch }}
          </span>
          <button class="btn-ghost text-[12px]"><PlusIcon class="h-3 w-3" />子类目</button>
        </div>
      </div>
      <button class="btn-outline w-full"><PlusIcon class="h-4 w-4" />添加一级品类</button>
    </div>

    <!-- 主题 -->
    <div v-show="tab === 'theme'" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
      <div v-for="t in themes" :key="t.name" class="panel overflow-hidden">
        <div class="relative aspect-[3/4]"><img :src="t.cover" class="h-full w-full object-cover" /><div class="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent"></div><p class="absolute bottom-2 left-3 font-display text-lg text-white">{{ t.name }}</p></div>
        <div class="flex items-center justify-between p-3"><span class="text-[12px] text-ink-faint">{{ t.products }} 件</span><Toggle v-model="t.online" /></div>
      </div>
      <button class="panel flex aspect-[3/4] flex-col items-center justify-center gap-2 border-2 border-dashed text-ink-faint hover:border-gold"><PlusIcon class="h-6 w-6" /><span class="text-[12px]">新增主题</span></button>
    </div>
  </div>
</template>
