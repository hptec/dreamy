<script setup lang="ts">
// PAGE-CAT-A01 / COMP-CAT-A01：商品列表（layout-keep + data-swap：mock→API + 服务端分页；
// 「更多筛选」面板按原型补齐——productType/库存档/flags/tagIds 为当前页过滤（tooltip 标注），价格区间同处置）
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Toggle from '@/components/Toggle.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useProductsStore } from '@/stores/products'
import { useCategoriesStore } from '@/stores/categories'
import { useTagsStore } from '@/stores/tags'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  PlusIcon, MagnifyingGlassIcon, FunnelIcon, PencilSquareIcon, TrashIcon, XMarkIcon, ChevronDownIcon,
} from '@heroicons/vue/24/outline'
import type { AdminProductListItem } from '@/api/types'

const store = useProductsStore()
const categories = useCategoriesStore()
const tags = useTagsStore()
const toast = useToastStore()

// 更多筛选状态（COMP-CAT-A01：当前页内存过滤，与原型行为一致并 tooltip 标注）
const showMoreFilters = ref(false)
const moreFilters = ref({
  priceMin: '' as string,
  priceMax: '' as string,
  stockLevel: 'all' as 'all' | 'inStock' | 'low' | 'out',
  flags: [] as string[],
  tagIds: [] as number[],
})
const stockLevels = [
  { value: 'all', label: '全部' },
  { value: 'inStock', label: '有货 (≥10)' },
  { value: 'low', label: '低库存 (<10)' },
  { value: 'out', label: '缺货 (0)' },
] as const
const flagOptions = [
  { value: 'isNew', label: '新品' },
  { value: 'recommend', label: '推荐' },
  { value: 'onSale', label: '促销中' },
]

function toggleIn<T>(list: T[], value: T) {
  const i = list.indexOf(value)
  if (i === -1) list.push(value)
  else list.splice(i, 1)
}

const activeMoreCount = computed(() => {
  let n = 0
  n += moreFilters.value.flags.length
  n += moreFilters.value.tagIds.length
  if (moreFilters.value.priceMin !== '' || moreFilters.value.priceMax !== '') n++
  if (moreFilters.value.stockLevel !== 'all') n++
  return n
})

function resetMoreFilters() {
  moreFilters.value = { priceMin: '', priceMax: '', stockLevel: 'all', flags: [], tagIds: [] }
}

/** 当前页内存过滤（高级项；tagIds 因 admin 列表契约无 tag_id 参数，同为当前页过滤——设计标注） */
const filtered = computed(() =>
  store.list.filter((p) => {
    const f = moreFilters.value
    if (f.priceMin !== '' && Number(p.price) < Number(f.priceMin)) return false
    if (f.priceMax !== '' && Number(p.price) > Number(f.priceMax)) return false
    const stock = p.stockTotal ?? 0
    if (f.stockLevel === 'inStock' && stock < 10) return false
    if (f.stockLevel === 'low' && (stock === 0 || stock >= 10)) return false
    if (f.stockLevel === 'out' && stock !== 0) return false
    if (f.flags.includes('isNew') && !p.isNew) return false
    if (f.flags.includes('recommend') && !p.recommend) return false
    if (f.flags.includes('onSale') && !p.compareAt) return false
    return true
  }),
)

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

function load() {
  store.fetchList().catch((e) => toast.error(bizMsg(e, '加载商品失败')))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
  }, 300)
}

function applyFilters() {
  store.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
}

// FORM-CAT-A02：行内 Toggle 乐观更新（失败回滚 + toast；同态幂等）
async function onToggleStatus(p: AdminProductListItem, on: boolean) {
  try {
    await store.toggleStatus(p, on ? 'published' : 'draft')
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

async function onFlagToggle(p: AdminProductListItem, key: 'isNew' | 'recommend', on: boolean) {
  try {
    await store.patchFlags(p, { [key]: on })
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

async function onSortBlur(p: AdminProductListItem, e: Event) {
  const v = Number((e.target as HTMLInputElement).value)
  if (Number.isNaN(v) || v === p.sort) return
  try {
    await store.patchFlags(p, { sort: v })
  } catch (err) {
    toast.error(bizMsg(err, '排序保存失败'))
  }
}

// 删除（409509 toast「已发布商品需先下架」）
const confirmDelete = ref<AdminProductListItem | null>(null)
const confirmBusy = ref(false)
async function doDelete() {
  if (!confirmDelete.value) return
  confirmBusy.value = true
  try {
    await store.remove(confirmDelete.value.id)
    toast.success('已删除')
    confirmDelete.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409509) toast.error('已发布商品需先下架')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

function fmt(n?: number | string | null) {
  if (n == null) return '—'
  return '$' + Number(n).toLocaleString()
}

onMounted(() => {
  load()
  categories.fetch().catch(() => undefined)
  tags.fetchTags().catch(() => undefined)
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="商品列表" :subtitle="`共 ${store.totalElements} 件商品`">
      <template #actions>
        <RouterLink to="/products/new" class="btn-primary"><PlusIcon class="h-4 w-4" />新增商品</RouterLink>
      </template>
    </PageHeader>

    <!-- 筛选栏（search/category/status 服务端） -->
    <div class="panel mb-4">
      <div class="flex flex-wrap items-center gap-3 p-4">
        <div class="relative min-w-[220px] flex-1">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="store.search" class="field pl-9" placeholder="搜索商品名称 / 货号…" @input="onSearchInput" />
        </div>
        <select v-model="store.filterCategoryId" class="field w-auto" @change="applyFilters">
          <option value="all">全部品类</option>
          <optgroup v-for="root in categories.cascadeOptions" :key="root.id" :label="root.name">
            <option :value="root.id">{{ root.name }}（全部）</option>
            <option v-for="c in root.children" :key="c.id" :value="c.id">{{ c.name }}</option>
          </optgroup>
        </select>
        <select v-model="store.filterStatus" class="field w-auto" @change="applyFilters">
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

      <!-- 展开卡片（高级项为当前页过滤——tooltip 标注） -->
      <div v-if="showMoreFilters" class="border-t border-line px-4 pb-4 pt-4" title="高级筛选为当前页过滤">
        <div class="grid grid-cols-2 gap-x-6 gap-y-4 md:grid-cols-3">
          <!-- 库存状态 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">库存状态<span class="ml-1 normal-case tracking-normal">（当前页过滤）</span></p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="s in stockLevels"
                :key="s.value"
                :class="moreFilters.stockLevel === s.value ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
                class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
                @click="moreFilters.stockLevel = s.value"
              >{{ s.label }}</button>
            </div>
          </div>

          <!-- 标记 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">标记<span class="ml-1 normal-case tracking-normal">（当前页过滤）</span></p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="f in flagOptions"
                :key="f.value"
                :class="moreFilters.flags.includes(f.value) ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
                class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
                @click="toggleIn(moreFilters.flags, f.value)"
              >{{ f.label }}</button>
            </div>
          </div>

          <!-- 价格区间 -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">价格区间<span class="ml-1 normal-case tracking-normal">（当前页过滤）</span></p>
            <div class="flex items-center gap-2">
              <input v-model="moreFilters.priceMin" type="number" min="0" placeholder="最低 $" class="field w-full" />
              <span class="shrink-0 text-ink-faint">—</span>
              <input v-model="moreFilters.priceMax" type="number" min="0" placeholder="最高 $" class="field w-full" />
            </div>
          </div>
        </div>

        <div class="mt-4 flex items-center justify-between border-t border-line pt-3">
          <button class="text-[12px] text-ink-faint hover:text-ink" @click="resetMoreFilters">
            <XMarkIcon class="mr-0.5 inline h-3 w-3" />清除筛选
          </button>
          <span class="text-[12px] text-ink-soft">本页 {{ filtered.length }} 件商品</span>
        </div>
      </div>
    </div>

    <!-- 表格 -->
    <div class="panel overflow-hidden">
      <div class="overflow-x-auto">
        <table class="data-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>品类</th>
              <th>价格</th>
              <th class="text-center">上架 / 新品 / 推荐</th>
              <th class="text-right">库存</th>
              <th>排序</th>
              <th>状态</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="store.loading"><td colspan="8" class="py-12 text-center text-ink-faint">加载中…</td></tr>
            <tr v-for="p in filtered" v-else :key="p.id">
              <td>
                <div class="flex items-center gap-3">
                  <img v-if="p.imageUrl" :src="p.imageUrl" :alt="p.name" class="h-12 w-10 shrink-0 rounded-luxe object-cover" />
                  <span v-else class="h-12 w-10 shrink-0 rounded-luxe bg-canvas-warm"></span>
                  <div class="min-w-0">
                    <p class="truncate font-medium text-ink">{{ p.name }}</p>
                    <p class="text-[11px] text-ink-faint">{{ p.slug }}</p>
                  </div>
                </div>
              </td>
              <td><span class="text-ink-soft">{{ p.categoryName || '—' }}</span><br /><span class="text-[11px] text-ink-faint">{{ p.styleNo || '' }}</span></td>
              <td>
                <span class="font-medium text-ink">{{ fmt(p.price) }}</span>
                <br v-if="p.compareAt" /><span v-if="p.compareAt" class="text-[11px] text-ink-faint line-through">{{ fmt(p.compareAt) }}</span>
              </td>
              <td>
                <div class="flex items-center justify-center gap-3">
                  <Toggle :model-value="p.status === 'published'" @update:model-value="onToggleStatus(p, $event)" />
                  <Toggle :model-value="!!p.isNew" @update:model-value="onFlagToggle(p, 'isNew', $event)" />
                  <Toggle :model-value="!!p.recommend" @update:model-value="onFlagToggle(p, 'recommend', $event)" />
                </div>
              </td>
              <td class="text-right">
                <span :class="(p.stockTotal ?? 0) === 0 ? 'text-danger' : (p.stockTotal ?? 0) < 10 ? 'text-warn' : 'text-ink'">{{ p.stockTotal ?? 0 }}</span>
              </td>
              <td><input class="field w-16 px-2 py-1 text-center text-[12px]" type="number" :value="p.sort ?? 0" @blur="onSortBlur(p, $event)" /></td>
              <td><StatusBadge :tone="p.status === 'published' ? 'ok' : 'neutral'" :label="p.status === 'published' ? '已上架' : '草稿'" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <RouterLink :to="`/products/${p.id}/edit`" class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</RouterLink>
                  <button
                    class="btn-danger-ghost disabled:opacity-40"
                    :disabled="p.status === 'published'"
                    :title="p.status === 'published' ? '已发布商品需先下架' : '删除'"
                    @click="confirmDelete = p"
                  ><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <EmptyState v-if="!store.loading && filtered.length === 0" title="没有符合条件的商品" hint="试着调整筛选条件，或新增一件商品。" />

      <Pagination :total="store.totalElements" :page="store.page" :per-page="store.pageSize" @change="(p) => store.setPage(p)" />
    </div>

    <ConfirmDialog
      :open="!!confirmDelete"
      title="删除确认"
      :message="`确认删除商品「${confirmDelete?.name}」？删除后不可恢复。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirmDelete = null"
    />
  </div>
</template>
