<script setup lang="ts">
// PAGE-CAT-A01 / COMP-CAT-A01：商品列表（layout-keep + data-swap：mock→API + 服务端分页；
// 「更多筛选」面板 5 组对照原型 L141-208——商品类型/库存档/flags/价格区间/主题标签 均为当前页过滤（tooltip 标注）；
// 商品类型与标签非列表契约字段，经详情接口按页懒加载缓存（ISS-L4U-001 修复，UI-PRD-04）
// admin-prototype-alignment ALIGN-007：勾选列+批量操作栏（COMP-CAT-P01/P02，FORM-CAT-P01）、
// 导出按钮（COMP-CAT-P03，FORM-CAT-P02）、销量列（sales_total）；ALIGN-008 EXEMPT：排序列保留（决策 9）
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Toggle from '@/components/Toggle.vue'
import Pagination from '@/components/Pagination.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useProductsStore } from '@/stores/products'
import { useCategoriesStore } from '@/stores/categories'
import { useCollectionsStore } from '@/stores/collections'
import { useToastStore } from '@/stores/toast'
import { catalogApi } from '@/api'
import { BizError } from '@/api/client'
import {
  PlusIcon, MagnifyingGlassIcon, FunnelIcon, ArrowDownTrayIcon,
  PencilSquareIcon, TrashIcon, XMarkIcon, ChevronDownIcon, ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { ProductStatus } from '@/api/types'
import type { AdminProductListItem, ProductBatchAction, ProductBatchFailure } from '@/api/types'

const store = useProductsStore()
const categories = useCategoriesStore()
const collections = useCollectionsStore()
const toast = useToastStore()

// 更多筛选状态（COMP-CAT-A01：当前页内存过滤，与原型行为一致并 tooltip 标注；
// ISS-L4U-001 修复：补 productTypes 字段并接通 商品类型/主题标签 两组 UI——原型 L143-153/L190-200）
const showMoreFilters = ref(false)
const moreFilters = ref({
  productTypes: [] as string[],
  priceMin: '' as string,
  priceMax: '' as string,
  stockLevel: 'all' as 'all' | 'inStock' | 'low' | 'out',
  flags: [] as string[],
  collectionIds: [] as number[],
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

// SelectMenu options（品类带分组、状态不分组）
const categoryOptions = computed(() => {
  const opts: { value: number | 'all'; label: string; group?: string }[] = [{ value: 'all', label: '全部品类' }]
  for (const root of categories.cascadeOptions) {
    opts.push({ value: root.id, label: `${root.name}（全部）`, group: root.name })
    for (const c of root.children ?? []) {
      opts.push({ value: c.id, label: c.name, group: root.name })
    }
  }
  return opts
})

const statusOptions = [
  { value: 'all', label: '全部状态' },
  { value: ProductStatus.PUBLISHED, label: '已上架' },
  { value: ProductStatus.DRAFT, label: '草稿' },
]

function toggleIn<T>(list: T[], value: T) {
  const i = list.indexOf(value)
  if (i === -1) list.push(value)
  else list.splice(i, 1)
}

// ISS-L4U-001：productType/collectionIds 非 AdminProductListItem 契约字段（catalog-api listAdminProducts），
// 面板展开时经详情接口（GET /api/admin/products/{id}）按页懒加载缓存，沿用现有 api 层；
// 行级失败静默降级（该行不参与两组新筛选），不阻断面板其余筛选
const rowMeta = ref<Record<number, { productType: string | null; collectionIds: number[] }>>({})
const metaLoading = ref(false)
async function ensureRowMeta() {
  const missing = store.list.filter((p) => !(p.id in rowMeta.value))
  if (!missing.length) return
  metaLoading.value = true
  try {
    const details = await Promise.all(
      missing.map((p) => catalogApi.getProduct(p.id).catch(() => null)),
    )
    for (const d of details) {
      if (d) rowMeta.value[d.id] = { productType: d.productType ?? null, collectionIds: d.collectionIds ?? [] }
    }
  } finally {
    metaLoading.value = false
  }
}
watch([showMoreFilters, () => store.list], ([open]) => {
  if (open) ensureRowMeta()
})

// 商品类型选项：自当前页数据去重（原型 L31 同口径；服务端分页下以本页为界，与「当前页过滤」语义一致）
const productTypeOptions = computed(() => {
  const set = new Set<string>()
  for (const p of store.list) {
    const t = rowMeta.value[p.id]?.productType
    if (t) set.add(t)
  }
  return [...set]
})

const activeMoreCount = computed(() => {
  let n = 0
  n += moreFilters.value.productTypes.length
  n += moreFilters.value.flags.length
  n += moreFilters.value.collectionIds.length
  if (moreFilters.value.priceMin !== '' || moreFilters.value.priceMax !== '') n++
  if (moreFilters.value.stockLevel !== 'all') n++
  return n
})

function resetMoreFilters() {
  moreFilters.value = { productTypes: [], priceMin: '', priceMax: '', stockLevel: 'all', flags: [], collectionIds: [] }
}

/** 当前页内存过滤（高级项；productType/collectionIds 因 admin 列表契约无对应字段/参数，经 rowMeta 同为当前页过滤——设计标注） */
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
    // ISS-L4U-001：meta 未就绪（懒加载中/行级失败）时该行暂不被两组新筛选排除，加载完成后响应式重算
    const meta = rowMeta.value[p.id]
    if (f.productTypes.length && meta && !f.productTypes.includes(meta.productType ?? '')) return false
    if (f.collectionIds.length && meta && !f.collectionIds.some((id) => meta.collectionIds.includes(id))) return false
    return true
  }),
)

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

// COMP-CAT-P01（ALIGN-007）：勾选列（selected 留在组件层；服务端分页下不跨页保留，翻页/筛选变更即清空）
const selected = ref<number[]>([])
const allChecked = computed({
  get: () => filtered.value.length > 0 && selected.value.length === filtered.value.length,
  set: (v: boolean) => { selected.value = v ? filtered.value.map((p) => p.id) : [] },
})

function onPageChange(p: number) {
  selected.value = []
  store.setPage(p).catch((e) => toast.error(bizMsg(e, '加载失败')))
}

// COMP-CAT-P02 / FORM-CAT-P01（ALIGN-007）：批量操作（逐条容错；失败明细面板含商品名+错误码文案）
const batchVerbs: Record<ProductBatchAction, string> = {
  publish: '上架',
  unpublish: '下架',
  recommend: '设为推荐',
  delete: '删除',
}
const batchBusy = ref(false)
const confirmBatchDelete = ref(false)
const batchFailures = ref<{ name: string; reason: string }[]>([])
const showBatchFailures = ref(false)

/** 行级错误码→中文文案（沿用现有 bizMsg 表；409509→已发布需先下架，其余取后端 message） */
function batchFailureReason(f: ProductBatchFailure): string {
  if (f.errorCode === 409509) return '已发布商品需先下架'
  return f.message || '操作失败'
}

function onBatch(action: ProductBatchAction) {
  if (!selected.value.length || batchBusy.value) return
  if (action === 'delete') {
    confirmBatchDelete.value = true
    return
  }
  runBatch(action)
}

async function runBatch(action: ProductBatchAction) {
  batchBusy.value = true
  try {
    const ids = [...selected.value]
    const resp = await store.batchOperate(action, ids)
    if (resp.failures.length === 0) {
      toast.success(`已${batchVerbs[action]} ${resp.successIds.length} 件商品`)
    } else {
      toast.warn(`成功 ${resp.successIds.length} 件，失败 ${resp.failures.length} 件`)
      // 商品名由 id 反查当前页（需在整页刷新前快照）
      batchFailures.value = resp.failures.map((f) => ({
        name: store.list.find((p) => p.id === f.id)?.name ?? `#${f.id}`,
        reason: batchFailureReason(f),
      }))
      showBatchFailures.value = true
    }
    selected.value = []
    confirmBatchDelete.value = false
    load() // 整页刷新保证派生字段一致
  } catch (e) {
    toast.error(bizMsg(e, '批量操作失败'))
  } finally {
    batchBusy.value = false
  }
}

// COMP-CAT-P03 / FORM-CAT-P02（ALIGN-007）：导出 CSV（按搜索/品类/状态服务端口径；截断 toast.warn）
async function onExport() {
  try {
    const { truncated } = await store.exportCsv()
    if (truncated) toast.warn('已达 10000 行上限，结果已截断')
  } catch (e) {
    toast.error(e instanceof Error && e.message ? e.message : '导出失败，请稍后重试')
  }
}

function load() {
  store.fetchList().catch((e) => toast.error(bizMsg(e, '加载商品失败')))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    selected.value = [] // 筛选变更 → 勾选清空（COMP-CAT-P01）
    store.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
  }, 300)
}

function applyFilters() {
  selected.value = [] // 筛选变更 → 勾选清空（COMP-CAT-P01）
  store.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
}

// FORM-CAT-A02：行内 Toggle 乐观更新（失败回滚 + toast；同态幂等）
async function onToggleStatus(p: AdminProductListItem, on: boolean) {
  try {
    await store.toggleStatus(p, on ? ProductStatus.PUBLISHED : ProductStatus.DRAFT)
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
  collections.fetchCollections().catch(() => undefined)
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="商品列表" :subtitle="`共 ${store.totalElements} 件商品`">
      <template #actions>
        <!-- COMP-CAT-P03（ALIGN-007）：导出按钮（原型 L112；服务端筛选口径，title 标注） -->
        <button
          class="btn-outline disabled:opacity-50"
          :disabled="store.exporting"
          title="按搜索/品类/状态条件导出"
          @click="onExport"
        ><ArrowDownTrayIcon class="h-4 w-4" />{{ store.exporting ? '导出中…' : '导出' }}</button>
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
        <SelectMenu v-model="store.filterCategoryId" :options="categoryOptions" @change="applyFilters" />
        <SelectMenu v-model="store.filterStatus" :options="statusOptions" @change="applyFilters" />
        <button class="btn-ghost" :class="showMoreFilters && 'text-gold'" @click="showMoreFilters = !showMoreFilters">
          <FunnelIcon class="h-4 w-4" />
          更多筛选
          <span v-if="activeMoreCount" class="ml-1 flex h-4 w-4 items-center justify-center rounded-full bg-gold text-[10px] font-semibold text-white">{{ activeMoreCount }}</span>
          <ChevronDownIcon class="h-3 w-3 transition-transform" :class="showMoreFilters && 'rotate-180'" />
        </button>
      </div>

      <!-- 展开卡片（高级项为当前页过滤——tooltip 标注；5 组对照原型 L141-208，ISS-L4U-001 修复） -->
      <div v-if="showMoreFilters" class="border-t border-line px-4 pb-4 pt-4" title="高级筛选为当前页过滤">
        <div class="grid grid-cols-2 gap-x-6 gap-y-4 md:grid-cols-4">
          <!-- 商品类型（原型 L143-153；选项自当前页数据去重，详情接口懒加载） -->
          <div>
            <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">商品类型<span class="ml-1 normal-case tracking-normal">（当前页过滤）</span></p>
            <div class="flex flex-wrap gap-1.5">
              <span v-if="metaLoading && !productTypeOptions.length" class="text-[12px] text-ink-faint">加载中…</span>
              <span v-else-if="!productTypeOptions.length" class="text-[12px] text-ink-faint">本页商品未填写类型</span>
              <button
                v-for="t in productTypeOptions"
                :key="t"
                :class="moreFilters.productTypes.includes(t) ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
                class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
                @click="toggleIn(moreFilters.productTypes, t)"
              >{{ t }}</button>
            </div>
          </div>

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

        <!-- 主题集合（独占一行，原型 L190-200；选项自集合 store，筛选经 rowMeta 当前页过滤） -->
        <div class="mt-4">
          <p class="mb-2 text-[11px] font-semibold uppercase tracking-wide text-ink-faint">主题集合<span class="ml-1 normal-case tracking-normal">（当前页过滤）</span></p>
          <div class="flex flex-wrap gap-1.5">
            <span v-if="!collections.collections.length" class="text-[12px] text-ink-faint">{{ collections.loading ? '加载中…' : '暂无集合' }}</span>
            <button
              v-for="c in collections.collections"
              :key="c.id"
              :class="moreFilters.collectionIds.includes(c.id) ? 'bg-gold/10 border-gold text-ink font-medium' : 'border-line text-ink-soft hover:border-gold/50'"
              class="rounded-full border px-2.5 py-0.5 text-[12px] transition"
              @click="toggleIn(moreFilters.collectionIds, c.id)"
            >{{ c.name }}</button>
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
              <!-- COMP-CAT-P01（ALIGN-007）：全选勾选列（原型 L217） -->
              <th class="w-10"><input v-model="allChecked" type="checkbox" class="h-4 w-4 rounded border-line accent-gold" /></th>
              <th>商品</th>
              <th>品类</th>
              <th>价格</th>
              <th class="text-center">上架 / 新品 / 推荐</th>
              <th class="text-right">库存</th>
              <th class="text-right">销量</th>
              <!-- ALIGN-008 EXEMPT(决策 9)：排序列为实现增强（行内 blur 保存），保留 -->
              <th>排序</th>
              <th>状态</th>
              <th class="text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="store.loading"><td colspan="10" class="py-12 text-center text-ink-faint">加载中…</td></tr>
            <tr v-for="p in filtered" v-else :key="p.id">
              <td><input v-model="selected" type="checkbox" :value="p.id" class="h-4 w-4 rounded border-line accent-gold" /></td>
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
                  <Toggle :model-value="p.status === ProductStatus.PUBLISHED" @update:model-value="onToggleStatus(p, $event)" />
                  <Toggle :model-value="!!p.isNew" @update:model-value="onFlagToggle(p, 'isNew', $event)" />
                  <Toggle :model-value="!!p.recommend" @update:model-value="onFlagToggle(p, 'recommend', $event)" />
                </div>
              </td>
              <td class="text-right">
                <span :class="(p.stockTotal ?? 0) === 0 ? 'text-danger' : (p.stockTotal ?? 0) < 10 ? 'text-warn' : 'text-ink'">{{ p.stockTotal ?? 0 }}</span>
              </td>
              <!-- COMP-CAT-P01（ALIGN-007）：销量列（sales_total 派生字段，缺省显示 0，原型 L255） -->
              <td class="text-right text-ink-soft">{{ p.salesTotal ?? 0 }}</td>
              <td><input class="field w-16 px-2 py-1 text-center text-[12px]" type="number" :value="p.sort ?? 0" @blur="onSortBlur(p, $event)" /></td>
              <td><StatusBadge :tone="p.status === ProductStatus.PUBLISHED ? 'ok' : 'neutral'" :label="p.status === ProductStatus.PUBLISHED ? '已上架' : '草稿'" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <RouterLink :to="`/products/${p.id}/edit`" class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</RouterLink>
                  <button
                    class="btn-danger-ghost disabled:opacity-40"
                    :disabled="p.status === ProductStatus.PUBLISHED"
                    :title="p.status === ProductStatus.PUBLISHED ? '已发布商品需先下架' : '删除'"
                    @click="confirmDelete = p"
                  ><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <EmptyState v-if="!store.loading && filtered.length === 0" title="没有符合条件的商品" hint="试着调整筛选条件，或新增一件商品。" />

      <!-- COMP-CAT-P02 / FORM-CAT-P01（ALIGN-007）：批量操作栏（原型 L272-278；逐条容错，无前端预判置灰） -->
      <div v-if="selected.length" class="flex items-center gap-3 border-t border-line bg-canvas-warm/50 px-4 py-3 text-[13px]">
        <span class="text-ink-soft">已选 {{ selected.length }} 项</span>
        <button class="btn-outline py-1 disabled:opacity-50" :disabled="batchBusy" @click="onBatch('publish')">批量上架</button>
        <button class="btn-outline py-1 disabled:opacity-50" :disabled="batchBusy" @click="onBatch('unpublish')">批量下架</button>
        <button class="btn-outline py-1 disabled:opacity-50" :disabled="batchBusy" @click="onBatch('recommend')">设为推荐</button>
        <button class="btn-danger-ghost ml-auto disabled:opacity-50" :disabled="batchBusy" @click="onBatch('delete')">批量删除</button>
      </div>

      <Pagination :total="store.totalElements" :page="store.page" :per-page="store.pageSize" @change="onPageChange" />
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

    <!-- FORM-CAT-P01（ALIGN-007）：批量删除二次确认 -->
    <ConfirmDialog
      :open="confirmBatchDelete"
      title="批量删除确认"
      :message="`确认批量删除 ${selected.length} 件商品？已上架/被订单引用的商品将跳过`"
      confirm-text="删除"
      danger
      :busy="batchBusy"
      @confirm="runBatch('delete')"
      @cancel="confirmBatchDelete = false"
    />

    <!-- FORM-CAT-P01（ALIGN-007）：批量操作失败明细面板（商品名 + 错误码→中文文案） -->
    <Teleport to="body">
      <div
        v-if="showBatchFailures"
        v-dismiss="() => (showBatchFailures = false)"
        class="fixed inset-0 z-[60] flex items-center justify-center bg-ink/40"
      >
        <div class="panel w-[28rem] max-w-[calc(100vw-2rem)] p-6">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="flex items-center gap-2 text-[15px] font-medium text-ink">
              <ExclamationTriangleIcon class="h-5 w-5 text-warn" />
              部分商品操作失败
            </h3>
            <button class="btn-ghost" @click="showBatchFailures = false"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <ul class="max-h-72 divide-y divide-line overflow-y-auto text-[13px]">
            <li v-for="(f, i) in batchFailures" :key="i" class="flex items-start justify-between gap-4 py-2.5">
              <span class="min-w-0 truncate font-medium text-ink">{{ f.name }}</span>
              <span class="shrink-0 text-danger">{{ f.reason }}</span>
            </li>
          </ul>
          <div class="mt-6 flex justify-end">
            <button class="btn-outline" @click="showBatchFailures = false">知道了</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
