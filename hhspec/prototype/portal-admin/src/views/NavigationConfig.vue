<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { navConfig, taxonomies, taxonomiesByType } from '@/data/mock'
import {
  Bars3Icon, PlusIcon, TrashIcon, RocketLaunchIcon, EyeIcon, ChevronRightIcon, LinkIcon, LockClosedIcon
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

// 分类下拉选项：导航项可链接到任意分类（品类或主题），结构来自「分类管理」
const taxonomyOptions = taxonomies.map(t => ({ id: t.id, name: t.name, href: t.href, type: t.type }))

const linkTargetLabel = (item) => {
  if (item.linkType === 'taxonomy') {
    const tax = taxonomies.find(t => t.id === item.taxonomyId)
    if (!tax) return '未关联分类'
    return `${tax.type === 'category' ? '品类' : '主题'}「${tax.name}」 → ${tax.href}`
  }
  return `自定义 → ${item.href}`
}

// Mega Menu 列的子链接从被引用对象派生（只读），不再手敲
const columnLinks = (col) => {
  if (col.source === 'category-children') {
    const cat = taxonomies.find(t => t.id === col.refId)
    return cat?.children ? cat.children.map(ch => ch.name) : []
  }
  if (col.source === 'taxonomy-type') return taxonomiesByType(col.refType).filter(t => t.online).map(t => t.name)
  return col.links || []
}

const sourceLabel = (col) => {
  if (col.source === 'category-children') {
    const cat = taxonomies.find(t => t.id === col.refId)
    return `引用品类「${cat ? cat.name : '?'}」子类`
  }
  if (col.source === 'taxonomy-type') return col.refType === 'theme' ? '引用全部上线主题' : '引用全部上线品类'
  return '自定义链接'
}
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
      <div class="panel flex items-start gap-2 bg-info/8 p-3 text-[12px] text-ink-soft">
        <LinkIcon class="mt-0.5 h-4 w-4 shrink-0 text-info" />
        <span>导航结构引用<strong>分类管理</strong>中的品类与主题，此处只配置「展示哪些、顺序、文案」。子链接由分类派生，需增删请前往 <strong>商品管理 › 分类管理</strong>。</span>
      </div>
      <div v-for="(item, i) in main" :key="item.id" class="panel p-4">
        <div class="flex items-center gap-3">
          <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
          <input v-model="item.label" @input="touch" class="field w-48 font-medium" />
          <div class="flex items-center gap-2 text-[12px] text-ink-soft">
            <span class="px-1">链接到</span>
            <select v-if="item.linkType === 'taxonomy'" v-model="item.taxonomyId" @change="touch" class="field w-48 py-1 text-[12px]">
              <optgroup label="品类">
                <option v-for="o in taxonomyOptions.filter(x=>x.type==='category')" :key="o.id" :value="o.id">{{ o.name }}</option>
              </optgroup>
              <optgroup label="主题">
                <option v-for="o in taxonomyOptions.filter(x=>x.type==='theme')" :key="o.id" :value="o.id">{{ o.name }}</option>
              </optgroup>
            </select>
            <span v-else class="rounded-luxe bg-canvas-warm px-2 py-1 text-[11px] text-ink-faint">{{ linkTargetLabel(item) }}</span>
          </div>
          <div class="ml-auto flex items-center gap-2">
            <span class="badge bg-canvas-warm text-[10px] text-ink-faint">{{ linkTargetLabel(item) }}</span>
            <button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button>
          </div>
        </div>
        <!-- Mega Menu 列：数据源派生，子链接只读 -->
        <div v-if="item.megaMenu && item.megaMenu.length" class="mt-3 grid gap-3 border-t border-line pt-3" :style="{ gridTemplateColumns: `repeat(${item.megaMenu.length}, 1fr)` }">
          <div v-for="(col, ci) in item.megaMenu" :key="ci" class="rounded-luxe border border-dashed border-line p-3">
            <input v-model="col.title" @input="touch" class="field mb-1.5 text-[12px] font-medium" placeholder="列标题" />
            <div class="mb-2 flex items-center gap-1 text-[10px] text-ink-faint"><LockClosedIcon class="h-3 w-3" />{{ sourceLabel(col) }}</div>
            <div class="space-y-1">
              <div v-for="link in columnLinks(col)" :key="link" class="flex items-center gap-1.5 text-[12px] text-ink-soft">
                <ChevronRightIcon class="h-3 w-3 text-ink-faint" />{{ link }}
              </div>
              <p v-if="!columnLinks(col).length" class="text-[11px] italic text-ink-faint">（来源为空，请在品类/主题中维护）</p>
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
