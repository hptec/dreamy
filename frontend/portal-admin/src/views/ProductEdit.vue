<script setup lang="ts">
// PAGE-CAT-A02 / COMP-CAT-A02：商品编辑（按原型 699 行版 copy-adapt 锚点布局：sec-basic/attrs/media/
// sku/size/price/content/seo + IntersectionObserver 高亮；数据层全接 API E-CAT-09/10/11/35 + 品类级联/
// 属性配置/标签选择器；三语 tab；FORM-CAT-A01 错误分发：409501 slug inline / 409504 SKU inline /
// 409508 刷新弹窗 / 422501 字段分发）
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import { useCategoriesStore } from '@/stores/categories'
import { useAttributeStore } from '@/stores/attributes'
import { useTagsStore } from '@/stores/tags'
import { useToastStore } from '@/stores/toast'
import { catalogApi } from '@/api'
import { BizError } from '@/api/client'
import { PRODUCT_COLORS, FALLBACK_SWATCH_HEX, colorHexOf, isPresetColor } from '@/constants/productColors'
import { extractFieldErrors, validateProductForm, type FieldErrors } from '@/utils/validators'
import {
  PlusIcon, XMarkIcon, RocketLaunchIcon, ArrowLeftIcon, InformationCircleIcon, TrashIcon,
} from '@heroicons/vue/24/outline'
import { AttrVisibility, AttributeDefType, ImageKind, ProductStatus } from '@/api/types'
import type { AdminProductUpsert, ProductImage, ProductTranslation, SizeChartRow } from '@/api/types'

const route = useRoute()
const router = useRouter()
const categories = useCategoriesStore()
const attributes = useAttributeStore()
const tags = useTagsStore()
const toast = useToastStore()

const editing = computed(() => route.name === 'product-edit')
const productId = computed(() => (editing.value ? Number(route.params.id) : null))

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

/* ===================== 锚点目录（原型同构：scroll-spy 高亮） ===================== */

const sections = [
  { key: 'basic', label: '基础信息' },
  { key: 'attrs', label: '版型属性' },
  { key: 'media', label: '媒体素材' },
  { key: 'sku', label: 'SKU 矩阵' },
  { key: 'size', label: '尺码表' },
  { key: 'price', label: '定价库存' },
  { key: 'content', label: '内容详情' },
  { key: 'seo', label: 'SEO' },
]
const active = ref('basic')

function scrollTo(key: string) {
  const el = document.getElementById('sec-' + key)
  if (el) {
    active.value = key
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

let observer: IntersectionObserver | null = null
function setupObserver() {
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible.length) active.value = visible[0].target.id.replace('sec-', '')
    },
    { rootMargin: '-96px 0px -60% 0px', threshold: 0 },
  )
  sections.forEach((s) => {
    const el = document.getElementById('sec-' + s.key)
    if (el) observer!.observe(el)
  })
}
onBeforeUnmount(() => observer && observer.disconnect())

/* ===================== 表单状态 ===================== */

const loading = ref(false)
const saving = ref(false)
const errors = ref<FieldErrors>({})
const conflictModal = ref(false) // 409508
const serverUpdatedAt = ref<string | null>(null)

const form = ref({
  name: '',
  slug: '',
  subtitle: '',
  categoryId: null as number | null,
  productType: '',
  description: '',
  designerNote: '',
  price: '' as number | string,
  compareAt: '' as number | string,
  installment: true,
  status: ProductStatus.DRAFT as ProductStatus,
  isNew: false,
  isBest: false,
  recommend: false,
  sort: 0,
  leadTimeDays: 14,
  rushAvailable: false,
  customSizeAvailable: false,
  styleNo: '',
  seoTitle: '',
  seoDesc: '',
  tagIds: [] as number[],
})

// 多币种覆盖价（decision 14：可覆盖，空 = 自动换算）
const CURRENCIES = ['EUR', 'CAD', 'AUD', 'GBP']
const multiCurrency = ref<Record<string, string>>({ EUR: '', CAD: '', AUD: '', GBP: '' })

// 三语 translations（EN 主字段；ES/FR 进 translations[]）
const contentLocale = ref<'en' | 'es' | 'fr'>('en')
type Trans = { name: string; subtitle: string; description: string; seoTitle: string; seoDescription: string }
const emptyTrans = (): Trans => ({ name: '', subtitle: '', description: '', seoTitle: '', seoDescription: '' })
const trans = ref<Record<'es' | 'fr', Trans>>({ es: emptyTrans(), fr: emptyTrans() })
const transFilled = computed(() => ({
  en: !!(form.value.name || form.value.description),
  es: Object.values(trans.value.es).some(Boolean),
  fr: Object.values(trans.value.fr).some(Boolean),
}))

/* ===================== 品类级联（STORE-CAT-A02.cascadeOptions） ===================== */

const parentCategoryIdLocal = ref<number | null>(null)
const parentCategory = computed(() => categories.cascadeOptions.find((r) => r.id === parentCategoryIdLocal.value))
const subcategories = computed(() => parentCategory.value?.children ?? [])
const parentCategoryOptions = computed(() =>
  categories.cascadeOptions.map((c) => ({ value: c.id, label: c.name }))
)
const subCategoryOptions = computed(() => [
  { value: parentCategoryIdLocal.value as number, label: `全部 ${parentCategory.value?.name ?? ''}` },
  ...subcategories.value.map((c) => ({ value: c.id, label: c.name })),
])

function onParentChange() {
  form.value.categoryId = parentCategoryIdLocal.value
  // 父级切换重置品类专属动态属性（原型 onParentChange 同义）
  attrValues.value = {}
}

function syncCascadeFromCategoryId() {
  const root = categories.rootOf(form.value.categoryId)
  parentCategoryIdLocal.value = root?.id ?? categories.cascadeOptions[0]?.id ?? null
  if (form.value.categoryId == null) form.value.categoryId = parentCategoryIdLocal.value
}

/* ===================== 属性配置（STORE-CAT-A03.resolveAttributeConfig） ===================== */

const attrSet = computed(() => attributes.resolveAttributeConfig(form.value.categoryId))
const show = (key: string) => (attrSet.value.attrs[key] ?? AttrVisibility.HIDDEN) !== AttrVisibility.HIDDEN
const required = (key: string) => attrSet.value.attrs[key] === AttrVisibility.VISIBLE
const isOverridden = (key: string) => Object.prototype.hasOwnProperty.call(attrSet.value.overrides, key)
const overrideCount = computed(() => Object.keys(attrSet.value.overrides).length)
const visibleFieldCount = computed(() => Object.values(attrSet.value.attrs).filter((v) => v !== AttrVisibility.HIDDEN).length)

function optionsFor(key: string): string[] {
  return attributes.defByKey(key)?.options ?? []
}

function toggleArr(arr: string[], val: string) {
  const i = arr.indexOf(val)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(val)
}

/* ===================== 动态属性值（EAV：attribute_def 驱动渲染） ===================== */

// 动态属性值：key → values（select/text/toggle 单元素；multiselect 多元素）
const attrValues = ref<Record<string, string[]>>({})
// 显隐 gate 伪 key：控制固定区块（SKU 定制开关），不渲染为动态属性
const PSEUDO_GATE_KEYS = new Set(['custom_size'])

/** 动态渲染行：生效配置非 hidden 行 × 字典 def（保持属性集顺序） */
const dynamicAttrs = computed(() =>
  attrSet.value.items
    .filter((it) => it.visibility !== AttrVisibility.HIDDEN && !PSEUDO_GATE_KEYS.has(it.key))
    .map((it) => ({ ...it, def: attributes.defByKey(it.key)! }))
    .filter((it) => !!it.def),
)

// 服务端已持久化的存量 key（迁移遗留/属性集收缩仍保留——后端编辑校验同口径放行）
const loadedAttrKeys = ref<Set<string>>(new Set())

// 子品类切换：清理新配置下不可见且非存量的属性值（防带着上个品类的值提交被 422 拒绝）
watch(() => form.value.categoryId, () => {
  const visible = new Set(dynamicAttrs.value.map((r) => r.key))
  for (const key of Object.keys(attrValues.value)) {
    if (!visible.has(key) && !loadedAttrKeys.value.has(key)) delete attrValues.value[key]
  }
})

function attrSingle(key: string): string {
  return attrValues.value[key]?.[0] ?? ''
}
function setAttrSingle(key: string, v: string) {
  if (v) attrValues.value[key] = [v]
  else delete attrValues.value[key]
}
function attrMulti(key: string): string[] {
  return attrValues.value[key] ?? []
}
function toggleAttrMulti(key: string, v: string) {
  const arr = attrValues.value[key] ?? (attrValues.value[key] = [])
  toggleArr(arr, v)
  if (!arr.length) delete attrValues.value[key]
}
function attrToggle(key: string): boolean {
  return attrValues.value[key]?.[0] === 'true'
}
function setAttrToggle(key: string, v: boolean) {
  attrValues.value[key] = [v ? 'true' : 'false']
}

/** 已保存值不在 options 内（option 已收缩）的遗留值，渲染为禁用项保留可见 */
function legacyValues(key: string): string[] {
  const opts = optionsFor(key)
  return attrMulti(key).filter((v) => !opts.includes(v))
}

/* ===================== 媒体（COMP-CAT-A06：四区块 + 拖拽排序写 sort） ===================== */

const gallery = ref<ProductImage[]>([]) // kind=gallery（第一张即主图 sort=0）
const lifestyle = ref<ProductImage[]>([])
const videos = ref<ProductImage[]>([])
const swatches = ref<ProductImage[]>([])
const newUploadUrl = ref('')
const newLifestyleUrl = ref('')
const newVideoUrl = ref('')
const newSwatchUrl = ref('')
const newSwatchColor = ref('')

watch(newUploadUrl, (url) => {
  if (url) {
    gallery.value.push({ url, kind: ImageKind.GALLERY })
    newUploadUrl.value = ''
  }
})
watch(newLifestyleUrl, (url) => {
  if (url) {
    lifestyle.value.push({ url, kind: ImageKind.LIFESTYLE })
    newLifestyleUrl.value = ''
  }
})
watch(newVideoUrl, (url) => {
  if (url) {
    videos.value.push({ url, kind: ImageKind.VIDEO })
    newVideoUrl.value = ''
  }
})
watch(newSwatchUrl, (url) => {
  if (url) {
    swatches.value.push({ url, kind: ImageKind.SWATCH, colorName: newSwatchColor.value.trim() || null })
    newSwatchUrl.value = ''
    newSwatchColor.value = ''
  }
})

// 拖拽排序（HTML5 DnD，drop 后数组重排，提交时 index 写 sort）
const dragIndex = ref<number | null>(null)
function onDragStart(i: number) {
  dragIndex.value = i
}
function onDrop(i: number) {
  if (dragIndex.value == null || dragIndex.value === i) return
  const list = gallery.value
  const [moved] = list.splice(dragIndex.value, 1)
  list.splice(i, 0, moved)
  dragIndex.value = null
}

/* ===================== SKU 矩阵 ===================== */

const skuColors = ref<string[]>([])
const skuSizes = ref<string[]>([])
const SIZE_PRESET = ['US 0', 'US 2', 'US 4', 'US 6', 'US 8', 'US 10', 'US 12', 'US 14', 'US 16']
const newColorInput = ref('')

// key = `${color}@@${size}` → sku cell（已有行隐藏携带 id+version）
interface SkuCell {
  id?: number | null
  version?: number | null
  skuCode: string
  stock: string
}
const skuMap = ref<Record<string, SkuCell>>({})

function cellKey(color: string, size: string) {
  return `${color}@@${size}`
}

/** sku_code 自动生成 `DRM-{颜色缩写}-{尺码}` 可改 */
function autoSkuCode(color: string, size: string): string {
  const c = color.replace(/[^A-Za-z]/g, '').substring(0, 3).toUpperCase() || 'CLR'
  const s = size.replace(/[^0-9A-Za-z]/g, '').toUpperCase()
  return `DRM-${c}-${s}`
}

function cellOf(color: string, size: string): SkuCell {
  const key = cellKey(color, size)
  if (!skuMap.value[key]) {
    skuMap.value[key] = { skuCode: autoSkuCode(color, size), stock: '' }
  }
  return skuMap.value[key]
}

function addColor() {
  const v = newColorInput.value.trim()
  if (v && !skuColors.value.includes(v)) skuColors.value.push(v)
  newColorInput.value = ''
}
// COMP-CAT-E01（ALIGN-018）：预设 swatch 点击切换（已选→移除，未选→追加）；skuColors 仍为 string[]
function toggleColor(name: string) {
  const i = skuColors.value.indexOf(name)
  if (i >= 0) skuColors.value.splice(i, 1)
  else skuColors.value.push(name)
}
// 编辑已有商品时 SKU 中不在预设表的颜色名 → 渲染为自定义 chip，不丢失（ALIGN-018 约束）
const customColors = computed(() => skuColors.value.filter((c) => !isPresetColor(c)))
// SKU 矩阵行色点：预设 hex，无则灰点占位（对照原型 L548-549）
function swatchHexOf(name: string): string {
  return colorHexOf(name) ?? FALLBACK_SWATCH_HEX
}
function toggleSize(s: string) {
  const i = skuSizes.value.indexOf(s)
  if (i >= 0) skuSizes.value.splice(i, 1)
  else skuSizes.value.push(s)
}
function removeColor(c: string) {
  const i = skuColors.value.indexOf(c)
  if (i >= 0) skuColors.value.splice(i, 1)
}

/** 矩阵 → skus[]（仅当前选中的 color×size 组合；空库存默认 0） */
function buildSkus() {
  const out = []
  for (const color of skuColors.value) {
    for (const size of skuSizes.value) {
      const cell = cellOf(color, size)
      out.push({
        id: cell.id ?? null,
        version: cell.version ?? null,
        skuCode: cell.skuCode.trim(),
        color,
        size,
        stock: cell.stock === '' ? 0 : Number(cell.stock),
      })
    }
  }
  return out
}

/* ===================== 尺码表 ===================== */

const sizeChart = ref<SizeChartRow[]>([])
function addSizeRow() {
  sizeChart.value.push({ us: '', uk: '', au: '', bust: '', waist: '', hips: '', hollowToFloor: '' })
}
function removeSizeRow(i: number) {
  sizeChart.value.splice(i, 1)
}

/* ===================== 回读（编辑态） ===================== */

async function loadProduct() {
  if (productId.value == null) {
    syncCascadeFromCategoryId()
    return
  }
  loading.value = true
  try {
    const p = await catalogApi.getProduct(productId.value)
    serverUpdatedAt.value = p.updatedAt ?? null
    Object.assign(form.value, {
      name: p.name,
      slug: p.slug,
      subtitle: p.subtitle || '',
      categoryId: p.categoryId,
      productType: p.productType || '',
      description: p.description || '',
      designerNote: p.designerNote || '',
      price: p.price ?? '',
      compareAt: p.compareAt ?? '',
      installment: p.installment ?? true,
      status: p.status,
      isNew: !!p.isNew,
      isBest: !!p.isBest,
      recommend: !!p.recommend,
      sort: p.sort ?? 0,
      leadTimeDays: p.leadTimeDays ?? 14,
      rushAvailable: !!p.rushAvailable,
      customSizeAvailable: !!p.customSizeAvailable,
      styleNo: p.styleNo || '',
      seoTitle: p.seoTitle || '',
      seoDesc: p.seoDesc || '',
      tagIds: [...(p.tagIds || [])],
    })
    // 动态属性 entries 回读 → attrValues
    const loadedAttrs: Record<string, string[]> = {}
    for (const entry of p.attributes || []) {
      if (entry.key && entry.values?.length) loadedAttrs[entry.key] = [...entry.values]
    }
    attrValues.value = loadedAttrs
    loadedAttrKeys.value = new Set(Object.keys(loadedAttrs))
    for (const cur of CURRENCIES) {
      multiCurrency.value[cur] = p.multiCurrencyPrices?.[cur] != null ? String(p.multiCurrencyPrices[cur]) : ''
    }
    const byLocale = (l: 'es' | 'fr') => p.translations?.find((t) => t.locale === l)
    const toTrans = (l: 'es' | 'fr'): Trans => {
      const t = byLocale(l)
      return {
        name: t?.name || '',
        subtitle: t?.subtitle || '',
        description: t?.description || '',
        seoTitle: t?.seoTitle || '',
        seoDescription: t?.seoDescription || '',
      }
    }
    trans.value = { es: toTrans('es'), fr: toTrans('fr') }

    // 媒体分区
    const imgs = (p.images || []).slice().sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    gallery.value = imgs.filter((i) => i.kind === ImageKind.GALLERY)
    lifestyle.value = imgs.filter((i) => i.kind === ImageKind.LIFESTYLE)
    videos.value = imgs.filter((i) => i.kind === ImageKind.VIDEO)
    swatches.value = imgs.filter((i) => i.kind === ImageKind.SWATCH)

    // SKU 矩阵回读（携带 id+version 防并发——V-CAT-038）
    const colors: string[] = []
    const sizes: string[] = []
    const map: Record<string, SkuCell> = {}
    for (const s of p.skus || []) {
      if (!colors.includes(s.color)) colors.push(s.color)
      if (!sizes.includes(s.size)) sizes.push(s.size)
      map[cellKey(s.color, s.size)] = {
        id: s.id,
        version: s.version,
        skuCode: s.skuCode,
        stock: String(s.stock ?? 0),
      }
    }
    skuColors.value = colors
    skuSizes.value = sizes
    skuMap.value = map

    sizeChart.value = (p.sizeChart || []).map((r) => ({ ...r }))
    syncCascadeFromCategoryId()
  } catch (e) {
    toast.error(bizMsg(e, '加载商品失败'))
  } finally {
    loading.value = false
  }
}

/* ===================== 提交（FORM-CAT-A01） ===================== */

function buildTranslations(): ProductTranslation[] {
  const rows: ProductTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (Object.values(t).some((v) => v.trim())) {
      rows.push({
        locale: l,
        name: t.name.trim() || null,
        subtitle: t.subtitle.trim() || null,
        description: t.description.trim() || null,
        seoTitle: t.seoTitle.trim() || null,
        seoDescription: t.seoDescription.trim() || null,
      })
    }
  }
  return rows
}

function buildImages(): ProductImage[] {
  const all: ProductImage[] = []
  gallery.value.forEach((img, i) => all.push({ ...img, kind: ImageKind.GALLERY, sort: i })) // 第一张 gallery 即 sort=0 主图
  lifestyle.value.forEach((img, i) => all.push({ ...img, kind: ImageKind.LIFESTYLE, sort: i }))
  videos.value.forEach((img, i) => all.push({ ...img, kind: ImageKind.VIDEO, sort: i }))
  swatches.value.forEach((img, i) => all.push({ ...img, kind: ImageKind.SWATCH, sort: i }))
  return all
}

function buildPayload(status: ProductStatus): AdminProductUpsert {
  const mcp: Record<string, string> = {}
  for (const cur of CURRENCIES) {
    if (multiCurrency.value[cur] !== '') mcp[cur] = multiCurrency.value[cur]
  }
  return {
    name: form.value.name.trim(),
    slug: form.value.slug.trim(),
    subtitle: form.value.subtitle.trim() || null,
    categoryId: form.value.categoryId,
    productType: form.value.productType.trim() || null,
    description: form.value.description || null,
    designerNote: form.value.designerNote || null,
    price: form.value.price,
    compareAt: form.value.compareAt === '' ? null : form.value.compareAt,
    installment: form.value.installment,
    multiCurrencyPrices: Object.keys(mcp).length ? mcp : null,
    status,
    isNew: form.value.isNew,
    isBest: form.value.isBest,
    recommend: form.value.recommend,
    sort: form.value.sort,
    leadTimeDays: form.value.leadTimeDays,
    rushAvailable: form.value.rushAvailable,
    customSizeAvailable: form.value.customSizeAvailable,
    attributes: Object.entries(attrValues.value)
      .filter(([, values]) => values.length > 0)
      .map(([key, values]) => ({ key, values })),
    styleNo: form.value.styleNo || null,
    seoTitle: form.value.seoTitle || null,
    seoDesc: form.value.seoDesc || null,
    images: buildImages(),
    skus: buildSkus(),
    sizeChart: sizeChart.value,
    tagIds: form.value.tagIds,
    translations: buildTranslations(),
    updatedAt: serverUpdatedAt.value, // 并发防丢失比对（409508）
  }
}

async function save() {
  // 前端预校验（name/slug/category/price/lead_time 必填、compare_at>=price、SKU 码非空唯一）
  errors.value = validateProductForm({ ...form.value, skus: buildSkus() })
  if (Object.keys(errors.value).length) {
    toast.error('请先修正表单中的错误')
    const first = Object.keys(errors.value)[0]
    const sectionByField: Record<string, string> = {
      name: 'basic', slug: 'seo', categoryId: 'basic', price: 'price', compareAt: 'price',
      leadTimeDays: 'sku', skus: 'sku',
    }
    scrollTo(sectionByField[first] || 'basic')
    return
  }
  saving.value = true
  try {
    // 所有保存都默认 PUBLISHED，自动清除 CDN 缓存
    const payload = buildPayload(ProductStatus.PUBLISHED)
    const saved = productId.value == null
      ? await catalogApi.createProduct(payload)
      : await catalogApi.updateProduct(productId.value, payload)
    serverUpdatedAt.value = saved.updatedAt ?? null

    // 保存成功，停留在当前页，提示缓存已清除
    toast.success('已保存，CDN 缓存已自动清除，消费端即可看到最新内容')

    if (productId.value == null) {
      // 新建商品：替换 URL 为编辑态
      router.replace(`/products/${saved.id}/edit`)
    } else {
      // 编辑商品：重新加载数据
      await loadProduct()
    }
  } catch (e) {
    if (e instanceof BizError) {
      if (e.code === 409501) {
        errors.value = { slug: 'slug 已存在' }
        scrollTo('seo')
        return
      }
      if (e.code === 409504) {
        // details.sku_codes 定位行
        const codes = (e.details as Record<string, unknown> | undefined)?.sku_codes
        errors.value = { skus: `SKU 码冲突：${Array.isArray(codes) ? codes.join(', ') : '请检查 SKU 码'}` }
        scrollTo('sku')
        return
      }
      if (e.code === 409508) {
        conflictModal.value = true // 商品已被他人修改 → 「重新加载」
        return
      }
      if (e.code === 422501) {
        errors.value = extractFieldErrors(e)
        if (!Object.keys(errors.value).length) toast.error(e.message)
        return
      }
      toast.error(e.message)
    } else {
      toast.error('保存失败')
    }
  } finally {
    saving.value = false
  }
}

/** 409508「重新加载」：丢弃本地改动 refetch */
async function reloadAfterConflict() {
  conflictModal.value = false
  await loadProduct()
  toast.info('已加载最新数据，本地改动已丢弃')
}

onMounted(async () => {
  await Promise.all([
    categories.fetch().catch(() => undefined),
    attributes.fetchAll().catch(() => undefined),
    tags.fetchDimensions().catch(() => undefined),
    tags.fetchTags().catch(() => undefined),
  ])
  await loadProduct()
  nextTick(setupObserver)
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader
      :eyebrow="editing ? 'Edit Product' : 'New Product'"
      :title="editing ? form.name || '编辑商品' : '新增商品'"
      subtitle="左侧目录快速定位，所有信息区块同屏拉通，可任意顺序编辑"
    >
      <template #actions>
        <button class="btn-ghost" @click="router.push('/products')"><ArrowLeftIcon class="h-4 w-4" />返回列表</button>
        <button class="btn-gold" :disabled="saving" @click="save()">
          <RocketLaunchIcon class="h-4 w-4" />{{ saving ? '保存中…' : '保存并清除缓存' }}
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="panel p-12 text-center text-ink-faint">加载中…</div>

    <div v-else class="flex items-start gap-6">
      <!-- 左侧 sticky 锚点目录 -->
      <aside class="sticky top-20 hidden w-48 shrink-0 lg:block">
        <nav class="panel p-2">
          <button
            v-for="s in sections"
            :key="s.key"
            class="flex w-full items-center gap-2.5 rounded-luxe px-3 py-2 text-left text-[13px] transition-colors"
            :class="active === s.key ? 'bg-ink text-canvas' : 'text-ink-soft hover:bg-canvas-warm'"
            @click="scrollTo(s.key)"
          >
            <span class="h-1.5 w-1.5 shrink-0 rounded-full transition-colors" :class="active === s.key ? 'bg-gold' : 'bg-line'"></span>
            {{ s.label }}
          </button>
        </nav>
      </aside>

      <!-- 右侧拉通内容 -->
      <div class="min-w-0 flex-1 space-y-4">
        <!-- ① 基础信息 -->
        <section id="sec-basic" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>基础信息
          </h2>
          <div class="grid max-w-3xl gap-5">
            <div>
              <label class="field-label">商品名称 *</label>
              <input v-model="form.name" class="field" placeholder="如 Aurelia A-Line Tulle Gown" />
              <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
            </div>
            <div>
              <label class="field-label">副标题 / 卖点</label>
              <input v-model="form.subtitle" class="field" placeholder="一句话卖点，显示在 PDP 价格下方" />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="field-label">标准品类 *（决定属性表单字段配置）</label>
                <div class="flex gap-3">
                  <SelectMenu v-model="parentCategoryIdLocal" :options="parentCategoryOptions" class="flex-1" @change="onParentChange" />
                  <SelectMenu v-if="subcategories.length" v-model="form.categoryId" :options="subCategoryOptions" class="flex-1" />
                </div>
                <p class="mt-1.5 text-[11px] text-ink-faint">
                  属性集：<strong>{{ attrSet.label }}</strong> · {{ visibleFieldCount }} 个字段
                </p>
                <p v-if="errors.categoryId" class="mt-1 text-[11px] text-danger">{{ errors.categoryId }}</p>
              </div>
              <div>
                <label class="field-label">商品类型（自由填写，用于筛选规则）</label>
                <input v-model="form.productType" class="field" placeholder="如 Bridal Gown / Party Dress" />
                <p class="mt-1.5 text-[11px] text-ink-faint">不影响属性表单，仅用于内部分组和自动化规则。</p>
              </div>
            </div>
            <!-- 标签选择器（STORE-CAT-A04.tagsByDimension 按维度分组多选 chip） -->
            <div>
              <label class="field-label">自定义标签（营销/导航用，不影响属性表单，可多选）</label>
              <div class="space-y-3">
                <div v-for="dim in tags.dimensions" :key="dim.id">
                  <p class="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-ink-faint">{{ dim.name }}</p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="tag in tags.tagsByDimension(dim.id)"
                      :key="tag.id"
                      type="button"
                      class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
                      :class="form.tagIds.includes(tag.id) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'"
                      @click="form.tagIds.includes(tag.id) ? form.tagIds.splice(form.tagIds.indexOf(tag.id), 1) : form.tagIds.push(tag.id)"
                    >{{ tag.name }}</button>
                    <span v-if="!tags.tagsByDimension(dim.id).length" class="text-[12px] italic text-ink-faint">暂无，请在「分类管理 › 自定义标签」添加</span>
                  </div>
                </div>
              </div>
            </div>
            <div>
              <label class="field-label">商品介绍（EN）</label>
              <textarea v-model="form.description" rows="5" class="field resize-none" placeholder="描述商品的面料、版型、适用场景…"></textarea>
            </div>
            <div>
              <label class="field-label">设计师/品牌故事（可选）</label>
              <textarea v-model="form.designerNote" rows="3" class="field resize-none" placeholder="这件礼服背后的灵感故事，显示在 PDP 品牌故事区块…"></textarea>
            </div>
          </div>
        </section>

        <!-- ② 版型属性（attribute_def 字典 + 属性集动态渲染：select/multiselect/text/toggle） -->
        <section id="sec-attrs" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>版型属性
          </h2>
          <div class="max-w-3xl space-y-6">
            <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5">
              <InformationCircleIcon class="h-4 w-4 shrink-0 text-ink-faint" />
              <div class="text-[12.5px] text-ink-soft">
                <p>
                  基础属性集：<strong class="text-ink">{{ attrSet.label }}</strong>
                  <template v-if="attrSet.isChild">
                    · 子品类 <strong class="text-ink">{{ attrSet.childName }}</strong>
                    <span v-if="overrideCount" class="ml-1 rounded-full bg-info/12 px-2 py-0.5 text-[11px] text-info">{{ overrideCount }} 项覆盖</span>
                    <span v-else class="ml-1 text-ink-faint">完全继承父级</span>
                  </template>
                </p>
                <p class="mt-0.5 text-ink-faint">属性由「属性字典 + 品类属性集」动态驱动，新增属性后此处自动出现；隐藏字段不渲染；带 <span class="rounded bg-info/12 px-1 text-info">覆盖</span> 标记的字段由当前子品类调整。</p>
              </div>
            </div>

            <div v-if="!dynamicAttrs.length" class="rounded-luxe border border-dashed border-line px-4 py-6 text-center text-[12.5px] text-ink-faint">
              当前品类暂未配置属性。请先在「分类管理」为品类绑定属性集，并在「属性字典」中维护属性。
            </div>

            <!-- 按属性集顺序渲染：select/text/toggle 占半列，multiselect 整行 chips -->
            <div v-else class="grid grid-cols-2 gap-4">
              <template v-for="row in dynamicAttrs" :key="row.key">
                <!-- select -->
                <div v-if="row.def.type === AttributeDefType.SELECT">
                  <label class="field-label">
                    {{ row.def.label }}
                    <span v-if="row.visibility === AttrVisibility.VISIBLE" class="text-danger">*</span>
                    <span v-if="row.overridden" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
                  </label>
                  <AppSelect
                    :model-value="attrSingle(row.key)"
                    :options="optionsFor(row.key)"
                    :legacy="legacyValues(row.key)"
                    @update:model-value="setAttrSingle(row.key, $event)"
                  />
                </div>

                <!-- text -->
                <div v-else-if="row.def.type === AttributeDefType.TEXT">
                  <label class="field-label">
                    {{ row.def.label }}
                    <span v-if="row.visibility === AttrVisibility.VISIBLE" class="text-danger">*</span>
                    <span v-if="row.overridden" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
                  </label>
                  <input class="field" :value="attrSingle(row.key)" maxlength="255" :placeholder="row.def.label" @input="setAttrSingle(row.key, ($event.target as HTMLInputElement).value)" />
                </div>

                <!-- toggle -->
                <div v-else-if="row.def.type === AttributeDefType.TOGGLE" class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
                  <p class="text-[13px] font-medium text-ink">
                    {{ row.def.label }}
                    <span v-if="row.visibility === AttrVisibility.VISIBLE" class="text-danger">*</span>
                    <span v-if="row.overridden" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
                  </p>
                  <Toggle :model-value="attrToggle(row.key)" @update:model-value="setAttrToggle(row.key, $event)" />
                </div>

                <!-- multiselect（整行 chips） -->
                <div v-else class="col-span-2">
                  <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">
                    {{ row.def.label }}（可多选）
                    <span v-if="row.visibility === AttrVisibility.VISIBLE" class="text-danger">*</span>
                    <span v-if="row.overridden" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="o in optionsFor(row.key)"
                      :key="o"
                      type="button"
                      class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
                      :class="attrMulti(row.key).includes(o) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'"
                      @click="toggleAttrMulti(row.key, o)"
                    >{{ o }}</button>
                    <button
                      v-for="o in legacyValues(row.key)"
                      :key="'legacy-' + o"
                      type="button"
                      class="rounded-full border border-gold bg-gold/12 px-3 py-1 text-[12.5px] text-gold-deep line-through"
                      title="选项已停用，点击移除"
                      @click="toggleAttrMulti(row.key, o)"
                    >{{ o }}</button>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </section>

        <!-- ③ 媒体素材（四区块；presign 直传） -->
        <section id="sec-media" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>媒体素材
          </h2>
          <div class="max-w-3xl">
            <label class="field-label">商品图廊（拖拽排序，第一张为主图）</label>
            <div class="grid grid-cols-3 gap-4 sm:grid-cols-4">
              <div
                v-for="(g, i) in gallery"
                :key="g.url + i"
                class="group relative aspect-[3/4] overflow-hidden rounded-luxe border border-line"
                draggable="true"
                @dragstart="onDragStart(i)"
                @dragover.prevent
                @drop="onDrop(i)"
              >
                <img :src="g.url" class="h-full w-full object-cover" />
                <span v-if="i === 0" class="absolute left-2 top-2 rounded bg-ink/80 px-1.5 py-0.5 text-[10px] text-white">主图</span>
                <button class="absolute right-2 top-2 hidden rounded-full bg-white/90 p-1 group-hover:block" @click="gallery.splice(i, 1)">
                  <XMarkIcon class="h-3.5 w-3.5" />
                </button>
              </div>
              <MediaUploadCard v-model="newUploadUrl" scope="product" aspect="aspect-[3/4]" label="上传图片" />
            </div>
            <div class="mt-6 grid grid-cols-2 gap-4">
              <div class="rounded-luxe border border-line p-4">
                <p class="field-label">Lifestyle 户外场景图</p>
                <p class="mb-2 text-[11px] text-ink-faint">模特穿着婚纱在真实场景（海滩/森林/花园）中的生活化照片</p>
                <div class="flex flex-wrap gap-2">
                  <div v-for="(img, i) in lifestyle" :key="img.url" class="group relative h-24 w-20 overflow-hidden rounded-luxe border border-line">
                    <img :src="img.url" class="h-full w-full object-cover" />
                    <button class="absolute right-1 top-1 hidden rounded-full bg-white/90 p-0.5 group-hover:block" @click="lifestyle.splice(i, 1)"><XMarkIcon class="h-3 w-3" /></button>
                  </div>
                  <div class="w-20"><MediaUploadCard v-model="newLifestyleUrl" scope="product" aspect="aspect-[5/6]" label="上传" /></div>
                </div>
              </div>
              <div class="rounded-luxe border border-line p-4">
                <p class="field-label">走秀 / Walking 视频</p>
                <p class="mb-2 text-[11px] text-ink-faint">展示裙摆飘动效果，建议 15-30 秒竖屏视频</p>
                <div class="flex flex-wrap gap-2">
                  <div v-for="(v, i) in videos" :key="v.url" class="group relative flex h-24 w-20 items-center justify-center overflow-hidden rounded-luxe border border-line bg-ink/5 text-[10px] text-ink-soft">
                    视频
                    <button class="absolute right-1 top-1 hidden rounded-full bg-white/90 p-0.5 group-hover:block" @click="videos.splice(i, 1)"><XMarkIcon class="h-3 w-3" /></button>
                  </div>
                  <div class="w-20"><MediaUploadCard v-model="newVideoUrl" scope="product" aspect="aspect-[5/6]" label="上传" allow-video /></div>
                </div>
              </div>
            </div>
            <!-- 颜色色样 -->
            <div class="mt-6 rounded-luxe border border-line p-4">
              <div class="flex items-center justify-between">
                <div>
                  <p class="field-label">颜色色样展示图 / Swatch Gallery</p>
                  <p class="text-[11px] text-ink-faint">每个颜色对应一张悬挂/平铺面料照片</p>
                </div>
              </div>
              <div class="mt-3 flex flex-wrap items-end gap-3">
                <div v-for="(s, i) in swatches" :key="s.url" class="group relative flex flex-col items-center gap-1 rounded-luxe border border-line p-2 text-[11px] text-ink-soft">
                  <img :src="s.url" class="h-14 w-14 rounded-full border border-line object-cover" />
                  <span>{{ s.colorName || '—' }}</span>
                  <button class="absolute right-1 top-1 hidden rounded-full bg-white/90 p-0.5 group-hover:block" @click="swatches.splice(i, 1)"><XMarkIcon class="h-3 w-3" /></button>
                </div>
                <div class="flex items-end gap-2">
                  <div class="w-20"><MediaUploadCard v-model="newSwatchUrl" scope="product" aspect="aspect-square" label="上传色样" /></div>
                  <input v-model="newSwatchColor" class="field w-28" placeholder="颜色名" />
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- ④ SKU 矩阵 -->
        <section id="sec-sku" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>SKU 矩阵
          </h2>
          <div>
            <div class="mb-6 grid grid-cols-3 gap-4">
              <div>
                <label class="field-label">标准发货周期 *</label>
                <div class="flex items-center gap-2">
                  <input v-model.number="form.leadTimeDays" type="number" class="field w-24 text-center" min="1" />
                  <span class="text-[13px] text-ink-soft">天</span>
                </div>
                <p class="mt-1 text-[11px] text-ink-faint">MTO（按需生产）建议 14–21 天</p>
                <p v-if="errors.leadTimeDays" class="mt-1 text-[11px] text-danger">{{ errors.leadTimeDays }}</p>
              </div>
              <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
                <div>
                  <p class="text-[13px] font-medium text-ink">支持加急（Rush Order）</p>
                  <p class="text-[12px] text-ink-faint">可选加急费，缩短至 7 天</p>
                </div>
                <Toggle v-model="form.rushAvailable" />
              </div>
              <div v-if="show('custom_size')" class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
                <div>
                  <p class="text-[13px] font-medium text-ink">支持定制尺寸</p>
                  <p class="text-[12px] text-ink-faint">买家填写三围自定义版型（伴娘服核心功能）</p>
                </div>
                <Toggle v-model="form.customSizeAvailable" />
              </div>
            </div>

            <div class="mb-4 flex flex-wrap gap-6">
              <div>
                <!-- COMP-CAT-E01（ALIGN-018）：预设 swatch 选择（原型 L515-523）+ 自定义颜色输入（实现增强保留） -->
                <p class="field-label">颜色 swatch（可选多个）</p>
                <div class="flex flex-wrap items-center gap-2">
                  <button
                    v-for="c in PRODUCT_COLORS"
                    :key="c.name"
                    type="button"
                    class="flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[12px] transition-colors"
                    :class="skuColors.includes(c.name) ? 'border-gold' : 'border-line'"
                    @click="toggleColor(c.name)"
                  >
                    <span class="h-3.5 w-3.5 rounded-full border border-line" :style="{ background: c.hex }"></span>{{ c.name }}
                  </button>
                  <!-- 自定义颜色 chips（不在预设表的已有 SKU 颜色不丢失；灰点占位） -->
                  <span v-for="c in customColors" :key="c" class="flex items-center gap-1.5 rounded-full border border-gold px-2.5 py-1 text-[12px]">
                    <span class="h-3.5 w-3.5 rounded-full border border-line" :style="{ background: FALLBACK_SWATCH_HEX }"></span>
                    {{ c }}
                    <button type="button" class="text-ink-faint hover:text-danger" @click="removeColor(c)"><XMarkIcon class="h-3 w-3" /></button>
                  </span>
                  <input v-model="newColorInput" class="field w-32" placeholder="自定义颜色" @keyup.enter="addColor" />
                  <button type="button" class="btn-ghost text-[12px]" @click="addColor"><PlusIcon class="h-3 w-3" />添加</button>
                </div>
              </div>
              <div>
                <p class="field-label">尺码</p>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="s in SIZE_PRESET"
                    :key="s"
                    type="button"
                    class="rounded-luxe border px-2.5 py-1 text-[12px] transition-colors"
                    :class="skuSizes.includes(s) ? 'border-gold bg-gold/10 text-gold-deep' : 'border-line text-ink-soft'"
                    @click="toggleSize(s)"
                  >{{ s }}</button>
                </div>
              </div>
            </div>

            <div v-if="skuColors.length && skuSizes.length" class="overflow-x-auto rounded-luxe border border-line">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>颜色 \ 尺码（单元格=库存）</th>
                    <th v-for="s in skuSizes" :key="s" class="text-center">{{ s }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in skuColors" :key="c">
                    <td class="font-medium text-ink">
                      <!-- SKU 矩阵行色点（ALIGN-018：预设 hex / 自定义灰点占位，原型 L548-549） -->
                      <div class="flex items-center gap-2">
                        <span class="h-3.5 w-3.5 shrink-0 rounded-full border border-line" :style="{ background: swatchHexOf(c) }"></span>
                        {{ c }}
                      </div>
                    </td>
                    <td v-for="s in skuSizes" :key="s" class="text-center">
                      <input v-model="cellOf(c, s).stock" type="number" min="0" class="field w-20 px-2 py-1 text-center text-[12px]" placeholder="0" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-else class="rounded-luxe border border-dashed border-line py-6 text-center text-[12px] text-ink-faint">选择颜色与尺码后生成 SKU 矩阵（纯定制商品可不配置 SKU）。</p>

            <!-- SKU 码明细（自动生成 DRM-{颜色缩写}-{尺码}，可改） -->
            <div v-if="skuColors.length && skuSizes.length" class="mt-4 overflow-x-auto rounded-luxe border border-line">
              <table class="data-table">
                <thead><tr><th>颜色</th><th>尺码</th><th>SKU 码（可改）</th><th class="text-right">库存</th></tr></thead>
                <tbody>
                  <template v-for="c in skuColors" :key="c">
                    <tr v-for="s in skuSizes" :key="c + s">
                      <td class="text-ink-soft">{{ c }}</td>
                      <td class="text-ink-soft">{{ s }}</td>
                      <td><input v-model="cellOf(c, s).skuCode" class="field w-44 px-2 py-1 font-mono text-[11px]" /></td>
                      <td class="text-right text-ink-soft">{{ cellOf(c, s).stock || 0 }}</td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
            <p v-if="errors.skus" class="mt-2 text-[12px] text-danger">{{ errors.skus }}</p>
            <p class="mt-2 text-[12px] text-ink-faint">SKU 码格式建议：品牌-颜色缩写-尺码；已有 SKU 编辑保留 id/version 防并发覆盖。</p>
          </div>
        </section>

        <!-- ⑤ 尺码表 -->
        <section id="sec-size" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>尺码表
          </h2>
          <div class="max-w-3xl">
            <label class="field-label">尺码对照表（US / UK / AU）</label>
            <div class="overflow-x-auto rounded-luxe border border-line">
              <table class="data-table">
                <thead><tr><th>US</th><th>UK</th><th>AU</th><th>胸围 (in)</th><th>腰围 (in)</th><th>臀围 (in)</th><th>中空到地 (in)</th><th></th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in sizeChart" :key="i">
                    <td><input v-model="r.us" class="field w-14 px-2 py-1 text-[12px]" /></td>
                    <td><input v-model="r.uk" class="field w-14 px-2 py-1 text-[12px]" /></td>
                    <td><input v-model="r.au" class="field w-14 px-2 py-1 text-[12px]" /></td>
                    <td><input v-model="r.bust" type="number" step="0.5" class="field w-20 px-2 py-1 text-[12px]" /></td>
                    <td><input v-model="r.waist" type="number" step="0.5" class="field w-20 px-2 py-1 text-[12px]" /></td>
                    <td><input v-model="r.hips" type="number" step="0.5" class="field w-20 px-2 py-1 text-[12px]" /></td>
                    <td><input v-model="r.hollowToFloor" type="number" step="0.5" class="field w-20 px-2 py-1 text-[12px]" /></td>
                    <td><button class="btn-danger-ghost" @click="removeSizeRow(i)"><TrashIcon class="h-3.5 w-3.5" /></button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <button class="btn-ghost mt-3" @click="addSizeRow"><PlusIcon class="h-4 w-4" />添加一行</button>
            <p class="mt-4 text-[12px] text-ink-faint">「中空到地」即颈部中心到地面距离，用于确认礼服版型是否需要加长/缩短；同时是尺码推荐 API 的匹配数据源。</p>
          </div>
        </section>

        <!-- ⑥ 定价库存 -->
        <section id="sec-price" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>定价库存
          </h2>
          <div class="grid max-w-2xl gap-5">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="field-label">现价 (USD) *</label>
                <input v-model="form.price" type="number" min="0" step="0.01" class="field" />
                <p v-if="errors.price" class="mt-1 text-[11px] text-danger">{{ errors.price }}</p>
              </div>
              <div>
                <label class="field-label">原价 (划线价)</label>
                <input v-model="form.compareAt" type="number" min="0" step="0.01" class="field" />
                <!-- js_guard：compare_at>=price 即时提示 -->
                <p
                  v-if="form.compareAt !== '' && Number(form.compareAt) > 0 && Number(form.compareAt) < Number(form.price)"
                  class="mt-1 text-[11px] text-danger"
                >划线价需不低于现价</p>
                <p v-else-if="errors.compareAt" class="mt-1 text-[11px] text-danger">{{ errors.compareAt }}</p>
              </div>
            </div>
            <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
              <div>
                <p class="text-[13px] font-medium text-ink">支持 Klarna / Afterpay 分期</p>
                <p class="text-[12px] text-ink-faint">前台 PDP 显示「Pay in 4」</p>
              </div>
              <Toggle v-model="form.installment" />
            </div>
            <div>
              <label class="field-label">多币种价格（自动按汇率换算，可覆盖）</label>
              <div class="grid grid-cols-5 gap-3">
                <div>
                  <span class="mb-1 block text-[11px] text-ink-faint">USD</span>
                  <input class="field text-[13px]" :value="form.price" disabled />
                </div>
                <div v-for="cur in CURRENCIES" :key="cur">
                  <span class="mb-1 block text-[11px] text-ink-faint">{{ cur }}</span>
                  <input v-model="multiCurrency[cur]" type="number" min="0" step="0.01" class="field text-[13px]" placeholder="auto" />
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- ⑦ 内容详情（三语 tab + 款式编号） -->
        <section id="sec-content" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>内容详情
          </h2>
          <div class="max-w-2xl space-y-6">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="field-label">款式编号 / Style No.</label>
                <input v-model="form.styleNo" class="field" placeholder="如 DRM-WD-001" />
              </div>
            </div>

            <!-- 三语 tab（FORM-CAT-A06：ES/FR 空字段允许提交，缺翻译消费端回退 EN） -->
            <div class="rounded-luxe border border-line p-4">
              <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">多语言内容（EN 主字段 / ES / FR）</p>
              <LocaleTabs v-model="contentLocale" :filled="transFilled" />
              <div v-show="contentLocale === 'en'" class="space-y-1 text-[12px] text-ink-faint">
                <p>EN 内容即上方主字段（名称/副标题/介绍/SEO），无需重复填写。</p>
              </div>
              <div v-for="l in ['es', 'fr'] as const" v-show="contentLocale === l" :key="l" class="space-y-3">
                <input v-model="trans[l].name" class="field" :placeholder="`商品名称（${l.toUpperCase()}）`" />
                <input v-model="trans[l].subtitle" class="field" :placeholder="`副标题（${l.toUpperCase()}）`" />
                <textarea v-model="trans[l].description" rows="4" class="field resize-none" :placeholder="`商品介绍（${l.toUpperCase()}）`"></textarea>
                <div class="grid grid-cols-2 gap-3">
                  <input v-model="trans[l].seoTitle" class="field" :placeholder="`SEO Title（${l.toUpperCase()}）`" />
                  <input v-model="trans[l].seoDescription" class="field" :placeholder="`SEO Description（${l.toUpperCase()}）`" />
                </div>
                <p class="text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13，可部分提交）。</p>
              </div>
            </div>
          </div>
        </section>

        <!-- ⑧ SEO -->
        <section id="sec-seo" class="panel scroll-mt-24 p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
            <span class="h-4 w-1 rounded-full bg-gold"></span>SEO
          </h2>
          <div class="grid max-w-2xl gap-5">
            <div>
              <label class="field-label">SEO Title</label>
              <input v-model="form.seoTitle" class="field" placeholder="商品 meta 标题" />
            </div>
            <div>
              <label class="field-label">URL Slug *</label>
              <div class="flex items-center rounded-luxe border border-line" :class="errors.slug && 'border-danger'">
                <span class="px-3 text-[12px] text-ink-faint">dreamy.com/product/</span>
                <input v-model="form.slug" class="flex-1 bg-transparent py-2 pr-3 text-[13px] outline-none" placeholder="aurelia-aline-tulle" />
              </div>
              <p v-if="errors.slug" class="mt-1 text-[11px] text-danger">{{ errors.slug }}</p>
            </div>
            <div>
              <label class="field-label">Meta Description</label>
              <textarea v-model="form.seoDesc" rows="3" class="field resize-none" placeholder="搜索引擎摘要文案（建议 150 字以内）"></textarea>
            </div>
            <div class="rounded-luxe border border-line bg-canvas/40 p-4">
              <p class="mb-1 text-[11px] uppercase tracking-wide text-ink-faint">搜索结果预览</p>
              <p class="text-[15px] text-info">{{ form.seoTitle || form.name || '商品标题' }} | Dreamy</p>
              <p class="text-[12px] text-ok">dreamy.com › product › {{ form.slug || 'slug' }}</p>
              <p class="text-[12.5px] text-ink-soft">{{ form.seoDesc || '商品的 meta 描述将显示在这里…' }}</p>
            </div>
          </div>
        </section>

      </div>
    </div>

    <!-- 409508 并发冲突弹窗（FORM-CAT-A01） -->
    <Teleport to="body">
      <div v-if="conflictModal" class="fixed inset-0 z-[60] flex items-center justify-center bg-ink/40">
        <div class="panel w-96 p-6">
          <h3 class="mb-3 text-[15px] font-medium text-ink">商品已被他人修改</h3>
          <p class="text-[13px] leading-relaxed text-ink-soft">该商品在你编辑期间已被其他人保存。请刷新后重试（本地未保存的改动将丢弃）。</p>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="conflictModal = false">继续编辑</button>
            <button class="btn-gold" @click="reloadAfterConflict">重新加载</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
