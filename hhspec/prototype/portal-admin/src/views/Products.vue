<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Toggle from '@/components/Toggle.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import { products as rawProducts } from '@/data/mock'
import {
  PlusIcon, MagnifyingGlassIcon, FunnelIcon, ArrowDownTrayIcon,
  PencilSquareIcon, DocumentDuplicateIcon, TrashIcon
} from '@heroicons/vue/24/outline'

const products = ref(rawProducts.map((p) => ({ ...p })))
const search = ref('')
const cat = ref('all')
const statusFilter = ref('all')

const cats = ['all', 'Wedding Dresses', 'Special Occasion', 'Accessories']
const filtered = computed(() => products.value.filter((p) => {
  if (cat.value !== 'all' && p.category !== cat.value) return false
  if (statusFilter.value !== 'all' && p.status !== statusFilter.value) return false
  if (search.value && !p.name.toLowerCase().includes(search.value.toLowerCase())) return false
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
    <div class="panel mb-4 p-4">
      <div class="flex flex-wrap items-center gap-3">
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
        <button class="btn-ghost"><FunnelIcon class="h-4 w-4" />更多筛选</button>
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
              <td><span class="text-ink-soft">{{ p.category }}</span><br /><span class="text-[11px] text-ink-faint">{{ p.sub }}</span></td>
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
