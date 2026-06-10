<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Toggle from '@/components/Toggle.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import { products as rawProducts, findCategory, standardTaxonomy, customTags } from '@/data/mock'
import {
  PlusIcon, MagnifyingGlassIcon, FunnelIcon, ArrowDownTrayIcon,
  PencilSquareIcon, DocumentDuplicateIcon, TrashIcon, XMarkIcon, ChevronDownIcon
} from '@heroicons/vue/24/outline'

const products = ref(rawProducts.map((p) => ({ ...p })))
const search = ref('')
const cat = ref('all')
const statusFilter = ref('all')

// 更多筛选状态
const moreFilters = ref({
  productTypes: [],   // 商品类型多选
  priceMin: '',
  priceMax: '',
  stockLevel: 'all',  // all | inStock | low | out
  flags: [],          // isNew | recommend | onSale
  tagIds: []          // 自定义标签多选
})

// 商品类型选项：从数据动态去重
const productTypeOptions = [...new Set(rawProducts.map(p => p.productType))]
const stockLevels = [
  { value: 'all', label: '全部' },
  { value: 'inStock', label: '有货 (≥10)' },
  { value: 'low', label: '低库存 (<10)' },
  { value: 'out', label: '缺货 (0)' }
]
const flagOptions = [
  { value: 'isNew', label: '新品' },
  { value: 'recommend', label: '推荐' },
  { value: 'onSale', label: '促销中' }
]

// 解析 categoryId → { 主品类, 子品类 } 用于展示和筛选
function catInfo(categoryId) {
  const found = findCategory(categoryId)
  if (!found) return { root: '—', leaf: '' }
  if (found.parent) return { root: found.parent.name, leaf: found.name }
  return { root: found.name, leaf: '' }
}

function tagName(id) {
  const t = customTags.find(t => t.id === id)
  return t ? t.name : id
}

function toggleIn(list, value) {
  const i = list.indexOf(value)
  if (i === -1) list.push(value)
  else list.splice(i, 1)
}

// 当前生效的更多筛选条数（用于按钮角标）
const activeMoreCount = computed(() => {
  let n = 0
  n += moreFilters.value.productTypes.length
  n += moreFilters.value.flags.length
  n += moreFilters.value.tagIds.length
  if (moreFilters.value.priceMin !== '' || moreFilters.value.priceMax !== '') n++
  if (moreFilters.value.stockLevel !== 'all') n++
  return n
})

function resetMoreFilters() {
  moreFilters.value = { productTypes: [], priceMin: '', priceMax: '', stockLevel: 'all', flags: [], tagIds: [] }
}

const showMoreFilters = ref(false)

const cats = ['all', ...standardTaxonomy.map(r => r.name)]
const filtered = computed(() => products.value.filter((p) => {
  if (cat.value !== 'all' && catInfo(p.categoryId).root !== cat.value) return false
  if (statusFilter.value !== 'all' && p.status !== statusFilter.value) return false
  if (search.value && !p.name.toLowerCase().includes(search.value.toLowerCase())) return false

  const f = moreFilters.value
  if (f.productTypes.length && !f.productTypes.includes(p.productType)) return false
  if (f.priceMin !== '' && p.price < Number(f.priceMin)) return false
  if (f.priceMax !== '' && p.price > Number(f.priceMax)) return false
  if (f.stockLevel === 'inStock' && p.stock < 10) return false
  if (f.stockLevel === 'low' && (p.stock === 0 || p.stock >= 10)) return false
  if (f.stockLevel === 'out' && p.stock !== 0) return false
  if (f.flags.includes('isNew') && !p.isNew) return false
  if (f.flags.includes('recommend') && !p.recommend) return false
  if (f.flags.includes('onSale') && !p.compareAt) return false
  if (f.tagIds.length && !f.tagIds.some(id => (p.tags || []).includes(id))) return false
  return true
}))

const selected = ref([])
const allChecked = computed({
  get: () => filtered.value.length > 0 && selected.value.length === filtered.value.length,
  set: (v) => { selected.value = v ? filtered.value.map((p) => p.id) : [] }
})
function fmt(n) { return '$' + n.toLocaleString() }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="商品列表" :subtitle="`共 ${products.length} 件商品 · 86 件已上架`">
      <template #actions>
        <button class="btn-outline"><ArrowDownTrayIcon class="h-4 w-4" />导出</button>
        <RouterLink to="/products/new" class="btn-primary"><PlusIcon class="h-4 w-4" />新增商品</RouterLink>
      </template>
    </PageHeader>

    <!-- 筛选栏 -->
    <div class="panel mb-4">
      <div class="flex flex-wrap items-center gap-3 p-4">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="search" class="field pl-9" placeholder="搜索商品名称 / 货号…" />
        </div>
        <select v-model="cat" class="field w-auto">
          <option v-for="c in cats" :key="c" :value="c">{{ c === 'all' ? '全部品类' : c }}</option>
        </select>
        <select v-model="statusFilter" class="field w-auto">
          <option value="all">全部状态</option>
          <option value="published">已上架</option>
          <option value="draft">草稿</option>
        </select>
        <button class="btn-ghost" :class="showMoreFilters && 'text-gold'" @click="showMoreFilters = !showMoreFilters">
          <FunnelIcon class="h-4 w-4" />
          更多筛选
          <span v-if="activeMoreCount" class="ml-1 flex h-4 w-4 items-center justify-center rounded-full bg-gold text-[10px] font-semibold text-white">{{ activeMoreCount }}</span>
          <ChevronDownIcon class="h-3 w-3 transition-transform" :class="showMoreFilters && 'rotate-180'" />
        </button>
      </div>

      <!-- 展开卡片 -->
      <div v-if="showMoreFilters" class="border-t border-line px-4 pb-4 pt-4">
        <div class="grid grid-cols-2 gap-x-6 gap-y-4 md:grid-cols-4">
          <!-- 商品类型 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">商品类型</p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="t in productTypeOptions" :key="t"
                :class="moreFilters.productTypes.includes(t) ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
                class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
                @click="toggleIn(moreFilters.productTypes, t)">{{ t }}</button>
            </div>
          </div>

          <!-- 库存状态 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">库存状态</p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="s in stockLevels" :key="s.value"
                :class="moreFilters.stockLevel === s.value ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
                class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
                @click="moreFilters.stockLevel = s.value">{{ s.label }}</button>
            </div>
          </div>

          <!-- 标记 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">标记</p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="f in flagOptions" :key="f.value"
                :class="moreFilters.flags.includes(f.value) ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
                class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
                @click="toggleIn(moreFilters.flags, f.value)">{{ f.label }}</button>
            </div>
          </div>

          <!-- 价格区间 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">价格区间</p>
            <div class="flex items-center gap-2">
              <input v-model="moreFilters.priceMin" type="number" min="0" placeholder="最低 $" class="field w-full" />
              <span class="shrink-0 text-ink-faint">—</span>
              <input v-model="moreFilters.priceMax" type="number" min="0" placeholder="最高 $" class="field w-full" />
            </div>
          </div>
        </div>

        <!-- 主题标签（独占一行） -->
        <div class="mt-4">
          <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">主题标签</p>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="t in customTags" :key="t.id"
              :class="moreFilters.tagIds.includes(t.id) ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
              class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
              @click="toggleIn(moreFilters.tagIds, t.id)">{{ t.name }}</button>
          </div>
        </div>

        <div class="mt-4 flex items-center justify-between border-t border-line pt-3">
          <button class="text-[12px] text-ink-faint hover:text-ink" @click="resetMoreFilters">
            <XMarkIcon class="mr-0.5 inline h-3 w-3" />清除筛选
          </button>
          <span class="text-[12px] text-ink-soft">{{ filtered.length }} 件商品</span>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table">
          <thead>
            <tr>
              <th class="w-10"><input type="checkbox" v-model="allChecked" class="h-4 w-4 rounded border-line accent-gold" /></th>
              <th>商品</th>
              <th>品类</th>
              <th>价格</th>
              <th class="text-center">上架 / 新品 / 推荐</th>
              <th class="text-right">库存</th>
              <th class="text-right">销量</th>
              <th>状态</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in filtered" :key="p.id">
              <td><input type="checkbox" :value="p.id" v-model="selected" class="h-4 w-4 rounded border-line accent-gold" /></td>
              <td>
                <div class="flex items-center gap-3">
                  <img :src="p.img" :alt="p.name" class="h-12 w-10 shrink-0 rounded-luxe object-cover" />
                  <div class="min-w-0">
                    <p class="truncate font-medium text-ink">{{ p.name }}</p>
                    <p class="text-[11px] text-ink-faint">{{ p.slug }}</p>
                  </div>
                </div>
              </td>
              <td><span class="text-ink-soft">{{ catInfo(p.categoryId).root }}</span><br /><span class="text-[11px] text-ink-faint">{{ catInfo(p.categoryId).leaf || p.productType }}</span></td>
              <td>
                <span class="font-medium text-ink">{{ fmt(p.price) }}</span>
                <br v-if="p.compareAt" /><span v-if="p.compareAt" class="text-[11px] text-ink-faint line-through">{{ fmt(p.compareAt) }}</span>
              </td>
              <td>
                <div class="flex items-center justify-center gap-3">
                  <Toggle :model-value="p.status === 'published'" @update:model-value="p.status = $event ? 'published' : 'draft'" />
                  <Toggle v-model="p.isNew" />
                  <Toggle v-model="p.recommend" />
                </div>
              </td>
              <td class="text-right">
                <span :class="p.stock === 0 ? 'text-danger' : p.stock < 10 ? 'text-warn' : 'text-ink'">{{ p.stock }}</span>
              </td>
              <td class="text-right text-ink-soft">{{ p.sales }}</td>
              <td><StatusBadge :tone="p.status === 'published' ? 'ok' : 'neutral'" :label="p.status === 'published' ? '已上架' : '草稿'" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <RouterLink :to="`/products/${p.id}/edit`" class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</RouterLink>
                  <button class="btn-ghost"><DocumentDuplicateIcon class="h-4 w-4" /></button>
                  <button class="btn-danger-ghost"><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <EmptyState v-if="filtered.length === 0" title="没有符合条件的商品" hint="试着调整筛选条件，或新增一件商品。" />

      <!-- 批量操作栏 -->
      <div v-if="selected.length" class="flex items-center gap-3 border-t border-line bg-canvas-warm/50 px-4 py-3 text-[13px]">
        <span class="text-ink-soft">已选 {{ selected.length }} 项</span>
        <button class="btn-outline py-1">批量上架</button>
        <button class="btn-outline py-1">批量下架</button>
        <button class="btn-outline py-1">设为推荐</button>
        <button class="btn-danger-ghost ml-auto">批量删除</button>
      </div>

      <Pagination :total="products.length" :per-page="10" />
    </div>
  </div>
</template>
