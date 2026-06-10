<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import {
  productColors, productSizes, products,
  silhouetteOptions, necklineOptions, sleeveOptions, backStyleOptions,
  waistlineOptions, trainOptions, embellishmentOptions, fabricOptions,
  supportOptions, occasionOptions, styleTagOptions, seasonOptions, lengthOptions,
  getAttributeSetForCategory, resolveAttributeConfig, standardTaxonomy, customTags, tagDimensions, tagsByDimension
} from '@/data/mock'
import {
  PhotoIcon, PlusIcon, XMarkIcon, ArrowUpTrayIcon,
  RocketLaunchIcon, ArrowLeftIcon, Bars3BottomLeftIcon, InformationCircleIcon
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const editing = computed(() => route.name === 'product-edit')
const existing = computed(() => products.find((p) => p.id === route.params.id))

// 锚点区块（左侧目录 + 右侧拉通，scroll-spy 高亮）
const sections = [
  { key: 'basic', label: '基础信息' },
  { key: 'attrs', label: '版型属性' },
  { key: 'media', label: '媒体素材' },
  { key: 'sku', label: 'SKU 矩阵' },
  { key: 'size', label: '尺码表' },
  { key: 'price', label: '定价库存' },
  { key: 'content', label: '内容详情' },
  { key: 'seo', label: 'SEO' }
]
const active = ref('basic')

// 点击目录平滑滚动到对应区块
function scrollTo(key) {
  const el = document.getElementById('sec-' + key)
  if (el) {
    active.value = key
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

// scroll-spy：滚动时高亮当前可见区块
let observer = null
onMounted(() => {
  nextTick(() => {
    observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
        if (visible.length) active.value = visible[0].target.id.replace('sec-', '')
      },
      { rootMargin: '-96px 0px -60% 0px', threshold: 0 }
    )
    sections.forEach((s) => {
      const el = document.getElementById('sec-' + s.key)
      if (el) observer.observe(el)
    })
  })
})
onBeforeUnmount(() => observer && observer.disconnect())

const form = ref({
  name: existing.value?.name || '',
  categoryId: existing.value?.categoryId || 'cat-wd-aline',
  productType: existing.value?.productType || '',
  selectedTags: existing.value?.tags || [],
  subtitle: '',
  description: '',
  price: existing.value?.price || 0,
  compareAt: existing.value?.compareAt || 0,
  installment: true,
  seoTitle: '',
  seoDesc: '',
  slug: existing.value?.slug || '',
  status: existing.value?.status || 'draft',
  // 版型属性
  silhouette: '',
  neckline: '',
  sleeve: '',
  backStyle: '',
  waistline: '',
  train: '',
  length: '',
  embellishments: [],
  fabric: '',
  fabricComposition: '',
  support: '',
  occasions: [],
  styleTags: [],
  season: '',
  // SKU / 发货
  leadTimeDays: 14,
  rushAvailable: false,
  customSizeAvailable: false,
  // 模特 / 内容
  modelHeight: '',
  modelSize: '',
  modelBodyType: '',
  careInstructions: 'Dry clean only. Store in a cool, dry place.',
  countryOfOrigin: 'China',
  designerNote: ''
})

// 标准品类级联选择：从 categoryId 派生父品类与子品类列表
const parentCategoryId = computed(() => {
  for (const root of standardTaxonomy) {
    if (root.id === form.value.categoryId) return root.id
    if (root.children?.some(c => c.id === form.value.categoryId)) return root.id
  }
  return standardTaxonomy[0].id
})
const parentCategory = computed(() => standardTaxonomy.find(r => r.id === parentCategoryId.value))
const subcategories = computed(() => parentCategory.value?.children || [])
const selectedSubcategory = computed(() => {
  const parent = parentCategory.value
  if (parent?.id === form.value.categoryId) return ''
  return form.value.categoryId
})

// 本地父品类选择（用于级联第一级 select 双向绑定）
const parentCategoryIdLocal = ref(parentCategoryId.value)
watch(parentCategoryId, (v) => { parentCategoryIdLocal.value = v })
function onParentChange() {
  form.value.categoryId = parentCategoryIdLocal.value
}

// 属性集：按最具体品类（子品类优先）解析 = 父级基础属性集 ⊕ 子品类覆盖
const attrSet = computed(() => resolveAttributeConfig(form.value.categoryId))
const show = (key) => attrSet.value.attrs[key] !== 'hidden'
const required = (key) => attrSet.value.attrs[key] === 'visible'
// 该属性是否被当前子品类覆盖（用于表单标注 delta）
const isOverridden = (key) => Object.prototype.hasOwnProperty.call(attrSet.value.overrides, key)
const overrideCount = computed(() => Object.keys(attrSet.value.overrides).length)

// SKU 矩阵
const skuColors = ref(productColors.slice(0, 3).map((c) => c.name))
const skuSizes = ref(['US 2', 'US 4', 'US 6', 'US 8'])
const gallery = ref([existing.value?.img].filter(Boolean))

// 多选 toggle 工具
function toggleArr(arr, val) {
  const i = arr.indexOf(val)
  i >= 0 ? arr.splice(i, 1) : arr.push(val)
}

// 父品类切换时重置品类专属字段
watch(parentCategoryId, () => {
  form.value.train = ''
})

function saveAndPublish() { router.push('/publish') }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader
      :eyebrow="editing ? 'Edit Product' : 'New Product'"
      :title="editing ? form.name || '编辑商品' : '新增商品'"
      subtitle="左侧目录快速定位，所有信息区块同屏拉通，可任意顺序编辑">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/products')"><ArrowLeftIcon class="h-4 w-4" />返回列表</button>
        <button class="btn-outline">保存草稿</button>
        <button class="btn-gold" @click="saveAndPublish"><RocketLaunchIcon class="h-4 w-4" />保存并生成静态页</button>
      </template>
    </PageHeader>

    <div class="flex items-start gap-6">
      <!-- 左侧 sticky 锚点目录 -->
      <aside class="sticky top-6 hidden w-48 shrink-0 lg:block">
        <nav class="panel p-2">
          <button v-for="s in sections" :key="s.key"
            @click="scrollTo(s.key)"
            class="flex w-full items-center gap-2.5 rounded-luxe px-3 py-2 text-left text-[13px] transition-colors"
            :class="active === s.key ? 'bg-ink text-canvas' : 'text-ink-soft hover:bg-canvas-warm'">
            <span class="h-1.5 w-1.5 shrink-0 rounded-full transition-colors"
              :class="active === s.key ? 'bg-gold' : 'bg-line'"></span>
            {{ s.label }}
          </button>
        </nav>
      </aside>

      <!-- 右侧拉通内容（所有区块垂直平铺） -->
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
        </div>
        <div>
          <label class="field-label">副标题 / 卖点</label>
          <input v-model="form.subtitle" class="field" placeholder="一句话卖点，显示在 PDP 价格下方" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="field-label">标准品类 *（决定属性表单字段配置）</label>
            <div class="flex gap-3">
              <select v-model="parentCategoryIdLocal" class="field flex-1" @change="onParentChange">
                <option v-for="cat in standardTaxonomy" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
              </select>
              <select v-model="form.categoryId" class="field flex-1" v-if="subcategories.length">
                <option :value="parentCategoryId">全部 {{ parentCategory?.name }}</option>
                <option v-for="sub in subcategories" :key="sub.id" :value="sub.id">{{ sub.name }}</option>
              </select>
            </div>
            <p class="mt-1.5 text-[11px] text-ink-faint">
              属性集：<strong>{{ attrSet?.label }}</strong> · {{ Object.values(attrSet?.attrs || {}).filter(v => v !== 'hidden').length }} 个字段
            </p>
          </div>
          <div>
            <label class="field-label">商品类型（自由填写，用于筛选规则）</label>
            <input v-model="form.productType" class="field" placeholder="如 Bridal Gown / Party Dress" />
            <p class="mt-1.5 text-[11px] text-ink-faint">不影响属性表单，仅用于内部分组和自动化规则。</p>
          </div>
        </div>
        <div>
          <label class="field-label">自定义标签（营销/导航用，不影响属性表单，可多选）</label>
          <div class="space-y-3">
            <div v-for="dim in tagDimensions" :key="dim.id">
              <p class="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-ink-faint">{{ dim.label }}</p>
              <div class="flex flex-wrap gap-2">
                <button v-for="tag in tagsByDimension(dim.id)" :key="tag.id" type="button"
                  @click="toggleArr(form.selectedTags, tag.id)"
                  class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
                  :class="form.selectedTags.includes(tag.id) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'">
                  {{ tag.name }}
                </button>
                <span v-if="!tagsByDimension(dim.id).length" class="text-[12px] italic text-ink-faint">暂无，请在「分类管理 › 自定义标签」添加</span>
              </div>
            </div>
          </div>
        </div>
        <div>
          <label class="field-label">商品介绍（富文本）</label>
          <div class="rounded-luxe border border-line">
            <div class="flex items-center gap-1 border-b border-line px-2 py-1.5 text-ink-faint">
              <button class="rounded px-2 py-1 text-[13px] font-bold hover:bg-canvas-warm">B</button>
              <button class="rounded px-2 py-1 text-[13px] italic hover:bg-canvas-warm">I</button>
              <button class="rounded px-2 py-1 hover:bg-canvas-warm"><Bars3BottomLeftIcon class="h-4 w-4" /></button>
              <span class="mx-1 h-4 w-px bg-line"></span>
              <button class="rounded px-2 py-1 text-[12px] hover:bg-canvas-warm">H2</button>
              <button class="rounded px-2 py-1 text-[12px] hover:bg-canvas-warm">链接</button>
            </div>
            <textarea v-model="form.description" rows="5" class="w-full resize-none px-3 py-2 text-[13px] outline-none" placeholder="描述商品的面料、版型、适用场景…"></textarea>
          </div>
        </div>
        <div>
          <label class="field-label">设计师/品牌故事（可选）</label>
          <textarea v-model="form.designerNote" rows="3" class="field resize-none" placeholder="这件礼服背后的灵感故事，显示在 PDP 品牌故事区块…"></textarea>
        </div>
        </div>
      </section>

      <!-- ② 版型属性（动态属性集）-->
      <section id="sec-attrs" class="panel scroll-mt-24 p-6">
        <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
          <span class="h-4 w-1 rounded-full bg-gold"></span>版型属性
        </h2>
        <div class="max-w-3xl space-y-6">
        <!-- 当前属性集提示 -->
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
            <p class="mt-0.5 text-ink-faint">属性集已按品类自动配置，灰色字段对该品类不适用；带 <span class="rounded bg-info/12 px-1 text-info">覆盖</span> 标记的字段由当前子品类调整。</p>
          </div>
        </div>

        <!-- P1 核心版型 -->
        <div>
          <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">核心版型</p>
          <div class="grid grid-cols-2 gap-4">
            <div v-if="show('silhouette')">
              <label class="field-label">
                廓形 / Silhouette <span v-if="required('silhouette')" class="text-danger">*</span>
                <span v-if="isOverridden('silhouette')" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
              </label>
              <select v-model="form.silhouette" class="field">
                <option value="">请选择…</option>
                <option v-for="o in silhouetteOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('length')">
              <label class="field-label">
                裙长 / Length <span v-if="required('length')" class="text-danger">*</span>
                <span v-if="isOverridden('length')" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
              </label>
              <select v-model="form.length" class="field">
                <option value="">请选择…</option>
                <option v-for="o in lengthOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('neckline')">
              <label class="field-label">领口 / Neckline <span v-if="required('neckline')" class="text-danger">*</span></label>
              <select v-model="form.neckline" class="field">
                <option value="">请选择…</option>
                <option v-for="o in necklineOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('sleeve')">
              <label class="field-label">袖型 / Sleeve</label>
              <select v-model="form.sleeve" class="field">
                <option value="">请选择…</option>
                <option v-for="o in sleeveOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('waistline')">
              <label class="field-label">腰线 / Waistline</label>
              <select v-model="form.waistline" class="field">
                <option value="">请选择…</option>
                <option v-for="o in waistlineOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('backStyle')">
              <label class="field-label">背部设计 / Back Style <span v-if="required('backStyle')" class="text-danger">*</span></label>
              <select v-model="form.backStyle" class="field">
                <option value="">请选择…</option>
                <option v-for="o in backStyleOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('train')">
              <label class="field-label">
                拖尾 / Train <span v-if="required('train')" class="text-danger">*</span>
                <span v-if="isOverridden('train')" class="ml-1 rounded bg-info/12 px-1 text-[10px] text-info">覆盖</span>
              </label>
              <select v-model="form.train" class="field">
                <option value="">请选择…</option>
                <option v-for="o in trainOptions" :key="o">{{ o }}</option>
              </select>
            </div>
            <div v-if="show('support')">
              <label class="field-label">内置支撑 / Built-in Support</label>
              <select v-model="form.support" class="field">
                <option value="">请选择…</option>
                <option v-for="o in supportOptions" :key="o">{{ o }}</option>
              </select>
            </div>
          </div>
        </div>

        <!-- P2 面料 -->
        <div v-if="show('fabric')">
          <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">面料</p>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="field-label">主面料 / Primary Fabric <span v-if="required('fabric')" class="text-danger">*</span></label>
              <select v-model="form.fabric" class="field">
                <option value="">请选择…</option>
                <optgroup v-for="cat in [...new Set(fabricOptions.map(f=>f.category))]" :key="cat" :label="cat">
                  <option v-for="f in fabricOptions.filter(x=>x.category===cat)" :key="f.name">{{ f.name }}</option>
                </optgroup>
              </select>
            </div>
            <div>
              <label class="field-label">面料成分 / Composition</label>
              <input v-model="form.fabricComposition" class="field" placeholder="如 100% Tulle / 60% Lace, 40% Satin" />
            </div>
          </div>
        </div>

        <!-- P3 装饰（多选）-->
        <div v-if="show('embellishment')">
          <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">装饰细节（可多选）</p>
          <div class="flex flex-wrap gap-2">
            <button v-for="o in embellishmentOptions" :key="o"
              @click="toggleArr(form.embellishments, o)"
              class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
              :class="form.embellishments.includes(o) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'">
              {{ o }}
            </button>
          </div>
        </div>

        <!-- P4 场合与风格（多选）-->
        <div class="grid grid-cols-2 gap-6">
          <div v-if="show('occasion')">
            <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">适合场合（可多选）</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="o in occasionOptions" :key="o"
                @click="toggleArr(form.occasions, o)"
                class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
                :class="form.occasions.includes(o) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'">
                {{ o }}
              </button>
            </div>
          </div>
          <div v-if="show('styleTag')">
            <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">风格标签（可多选）</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="o in styleTagOptions" :key="o"
                @click="toggleArr(form.styleTags, o)"
                class="rounded-full border px-3 py-1 text-[12.5px] transition-colors"
                :class="form.styleTags.includes(o) ? 'border-gold bg-gold/12 text-gold-deep' : 'border-line text-ink-soft hover:border-ink'">
                {{ o }}
              </button>
            </div>
          </div>
        </div>

        <!-- P5 季节 -->
        <div v-if="show('season')" class="max-w-xs">
          <label class="field-label">季节 / Season</label>
          <select v-model="form.season" class="field">
            <option value="">请选择…</option>
            <option v-for="o in seasonOptions" :key="o">{{ o }}</option>
          </select>
        </div>
        </div>
      </section>

      <!-- ③ 媒体素材 -->
      <section id="sec-media" class="panel scroll-mt-24 p-6">
        <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
          <span class="h-4 w-1 rounded-full bg-gold"></span>媒体素材
        </h2>
        <div class="max-w-3xl">
        <label class="field-label">商品图廊（拖拽排序，第一张为主图）</label>
        <div class="grid grid-cols-3 gap-4 sm:grid-cols-4">
          <div v-for="(g, i) in gallery" :key="i" class="group relative aspect-[3/4] overflow-hidden rounded-luxe border border-line">
            <img :src="g" class="h-full w-full object-cover" />
            <span v-if="i === 0" class="absolute left-2 top-2 rounded bg-ink/80 px-1.5 py-0.5 text-[10px] text-white">主图</span>
            <button class="absolute right-2 top-2 hidden rounded-full bg-white/90 p-1 group-hover:block"><XMarkIcon class="h-3.5 w-3.5" /></button>
          </div>
          <button class="flex aspect-[3/4] flex-col items-center justify-center gap-2 rounded-luxe border-2 border-dashed border-line text-ink-faint transition-colors hover:border-gold hover:text-gold-deep">
            <ArrowUpTrayIcon class="h-6 w-6" /><span class="text-[12px]">上传图片</span>
          </button>
        </div>
        <div class="mt-6 grid grid-cols-2 gap-4">
          <div class="rounded-luxe border border-line p-4">
            <p class="field-label">Lifestyle 户外场景图</p>
            <p class="mb-2 text-[11px] text-ink-faint">模特穿着婚纱在真实场景（海滩/森林/花园）中的生活化照片</p>
            <button class="flex h-24 w-full items-center justify-center rounded-luxe border-2 border-dashed border-line text-ink-faint hover:border-gold">
              <PhotoIcon class="h-6 w-6" />
            </button>
          </div>
          <div class="rounded-luxe border border-line p-4">
            <p class="field-label">走秀 / Walking 视频</p>
            <p class="mb-2 text-[11px] text-ink-faint">展示裙摆飘动效果，建议 15-30 秒竖屏视频</p>
            <button class="flex h-24 w-full items-center justify-center rounded-luxe border-2 border-dashed border-line text-ink-faint hover:border-gold">
              <PlusIcon class="h-6 w-6" />
            </button>
          </div>
        </div>
        <!-- 颜色色样展示 -->
        <div class="mt-6 rounded-luxe border border-line p-4">
          <div class="flex items-center justify-between">
            <div>
              <p class="field-label">颜色色样展示图 / Swatch Gallery</p>
              <p class="text-[11px] text-ink-faint">每个颜色对应一张悬挂/平铺面料照片（Azazie 80+ 色参考）</p>
            </div>
            <button class="btn-ghost text-[12px]"><PlusIcon class="h-3.5 w-3.5" />批量上传</button>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <div v-for="c in productColors.slice(0,6)" :key="c.name"
              class="flex flex-col items-center gap-1 rounded-luxe border border-line p-2 text-[11px] text-ink-soft">
              <span class="h-10 w-10 rounded-full border border-line" :style="{ background: c.hex }"></span>
              <span>{{ c.name }}</span>
              <span class="text-ink-faint">待上传</span>
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
        <!-- 发货周期 + 定制尺寸 -->
        <div class="mb-6 grid grid-cols-3 gap-4">
          <div>
            <label class="field-label">标准发货周期</label>
            <div class="flex items-center gap-2">
              <input v-model="form.leadTimeDays" type="number" class="field w-24 text-center" min="1" />
              <span class="text-[13px] text-ink-soft">天</span>
            </div>
            <p class="mt-1 text-[11px] text-ink-faint">MTO（按需生产）建议 14–21 天</p>
          </div>
          <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
            <div>
              <p class="text-[13px] font-medium text-ink">支持加急（Rush Order）</p>
              <p class="text-[12px] text-ink-faint">可选加急费，缩短至 7 天</p>
            </div>
            <Toggle v-model="form.rushAvailable" />
          </div>
          <div v-if="show('customSize')" class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
            <div>
              <p class="text-[13px] font-medium text-ink">支持定制尺寸</p>
              <p class="text-[12px] text-ink-faint">买家填写三围自定义版型（伴娘服核心功能）</p>
            </div>
            <Toggle v-model="form.customSizeAvailable" />
          </div>
        </div>

        <div class="mb-4 flex flex-wrap gap-6">
          <div>
            <p class="field-label">颜色 swatch（可选多个）</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="c in productColors" :key="c.name"
                @click="skuColors.includes(c.name) ? skuColors.splice(skuColors.indexOf(c.name),1) : skuColors.push(c.name)"
                class="flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[12px] transition-colors"
                :class="skuColors.includes(c.name) ? 'border-gold' : 'border-line'">
                <span class="h-3.5 w-3.5 rounded-full border border-line" :style="{ background: c.hex }"></span>{{ c.name }}
              </button>
            </div>
          </div>
          <div>
            <p class="field-label">尺码</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="s in productSizes" :key="s"
                @click="skuSizes.includes(s) ? skuSizes.splice(skuSizes.indexOf(s),1) : skuSizes.push(s)"
                class="rounded-luxe border px-2.5 py-1 text-[12px] transition-colors"
                :class="skuSizes.includes(s) ? 'border-gold bg-gold/10 text-gold-deep' : 'border-line text-ink-soft'">{{ s }}</button>
            </div>
          </div>
        </div>
        <div class="overflow-x-auto rounded-luxe border border-line">
          <table class="data-table">
            <thead>
              <tr>
                <th>颜色 \ 尺码</th>
                <th v-for="s in skuSizes" :key="s" class="text-center">{{ s }}</th>
                <th class="text-center">SKU 码</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in skuColors" :key="c">
                <td class="font-medium text-ink">
                  <div class="flex items-center gap-2">
                    <span class="h-3.5 w-3.5 rounded-full border border-line shrink-0"
                      :style="{ background: productColors.find(x=>x.name===c)?.hex }"></span>
                    {{ c }}
                  </div>
                </td>
                <td v-for="s in skuSizes" :key="s" class="text-center">
                  <input class="field w-20 px-2 py-1 text-center text-[12px]" :placeholder="String(Math.floor(Math.random()*40))" />
                </td>
                <td>
                  <input class="field w-32 px-2 py-1 text-[11px]"
                    :placeholder="`DRM-${c.substring(0,3).toUpperCase()}-2`" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="mt-2 text-[12px] text-ink-faint">单元格内填写库存数量；SKU 码格式建议：品牌-颜色缩写-尺码。</p>
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
            <thead><tr><th>US</th><th>UK</th><th>AU</th><th>胸围 (in)</th><th>腰围 (in)</th><th>臀围 (in)</th><th>中空到地 (in)</th></tr></thead>
            <tbody>
              <tr v-for="r in [['0','4','4','32','24','34','58'],['2','6','6','33','25','35','58'],['4','8','8','34','26','36','59'],['6','10','10','35','27','37','59'],['8','12','12','36','28','38','60'],['10','14','14','37.5','29.5','39.5','60'],['12','16','16','39','31','41','61']]" :key="r[0]">
                <td v-for="(v,i) in r" :key="i"><input class="field w-14 px-2 py-1 text-[12px]" :value="v" /></td>
              </tr>
            </tbody>
          </table>
        </div>
        <button class="btn-ghost mt-3"><PlusIcon class="h-4 w-4" />添加一行</button>
        <p class="mt-4 text-[12px] text-ink-faint">「中空到地」即颈部中心到地面距离，用于确认礼服版型是否需要加长/缩短。</p>
        </div>
      </section>

      <!-- ⑥ 定价库存 -->
      <section id="sec-price" class="panel scroll-mt-24 p-6">
        <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
          <span class="h-4 w-1 rounded-full bg-gold"></span>定价库存
        </h2>
        <div class="grid max-w-2xl gap-5">
        <div class="grid grid-cols-2 gap-4">
          <div><label class="field-label">现价 (USD) *</label><input v-model="form.price" type="number" class="field" /></div>
          <div><label class="field-label">原价 (划线价)</label><input v-model="form.compareAt" type="number" class="field" /></div>
        </div>
        <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
          <div><p class="text-[13px] font-medium text-ink">支持 Klarna / Afterpay 分期</p><p class="text-[12px] text-ink-faint">前台 PDP 显示「Pay in 4」</p></div>
          <Toggle v-model="form.installment" />
        </div>
        <div>
          <label class="field-label">多币种价格（自动按汇率换算，可覆盖）</label>
          <div class="grid grid-cols-4 gap-3">
            <div v-for="cur in ['USD','CAD','AUD','GBP']" :key="cur">
              <span class="mb-1 block text-[11px] text-ink-faint">{{ cur }}</span>
              <input class="field text-[13px]" :placeholder="cur === 'USD' ? String(form.price) : 'auto'" />
            </div>
          </div>
        </div>
        </div>
      </section>

      <!-- ⑦ 内容详情（模特信息 + 护理 + 产地）-->
      <section id="sec-content" class="panel scroll-mt-24 p-6">
        <h2 class="mb-5 flex items-center gap-2 text-[15px] font-medium text-ink">
          <span class="h-4 w-1 rounded-full bg-gold"></span>内容详情
        </h2>
        <div class="max-w-2xl space-y-6">
        <!-- 模特信息 -->
        <div v-if="show('modelInfo')">
          <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">模特信息（提升购买信心）</p>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="field-label">模特身高</label>
              <input v-model="form.modelHeight" class="field" placeholder='如 5&apos;10" / 178cm' />
            </div>
            <div>
              <label class="field-label">模特所穿尺码</label>
              <input v-model="form.modelSize" class="field" placeholder="如 US 6" />
            </div>
            <div>
              <label class="field-label">体型描述</label>
              <input v-model="form.modelBodyType" class="field" placeholder="如 Straight / Hourglass" />
            </div>
          </div>
          <p class="mt-1 text-[11px] text-ink-faint">前台 PDP 显示在尺码选择区下方，减少退货率（参考 BHLDN / Azazie 做法）。</p>
        </div>

        <!-- 护理说明 -->
        <div v-if="show('careInstructions')">
          <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">护理说明 / Care Instructions</p>
          <textarea v-model="form.careInstructions" rows="3" class="field resize-none"
            placeholder="如 Dry clean only. Do not bleach. Store in a garment bag." />
          <p class="mt-1 text-[11px] text-ink-faint">前台 PDP「Fabric & Care」折叠区展示。</p>
        </div>

        <!-- 产地 -->
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="field-label">生产地 / Country of Origin</label>
            <input v-model="form.countryOfOrigin" class="field" placeholder="如 China / Italy" />
          </div>
          <div>
            <label class="field-label">款式编号 / Style No.</label>
            <input class="field" :placeholder="`DRM-${parentCategory?.name?.substring(0,2).toUpperCase() || 'WD'}-001`" />
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
        <div><label class="field-label">SEO Title</label><input v-model="form.seoTitle" class="field" placeholder="商品 meta 标题" /></div>
        <div><label class="field-label">URL Slug</label>
          <div class="flex items-center rounded-luxe border border-line">
            <span class="px-3 text-[12px] text-ink-faint">dreamy.com/product/</span>
            <input v-model="form.slug" class="flex-1 bg-transparent py-2 pr-3 text-[13px] outline-none" placeholder="aurelia-aline-tulle" />
          </div>
        </div>
        <div><label class="field-label">Meta Description</label><textarea v-model="form.seoDesc" rows="3" class="field resize-none" placeholder="搜索引擎摘要文案（建议 150 字以内）"></textarea></div>
        <div class="rounded-luxe border border-line bg-canvas/40 p-4">
          <p class="mb-1 text-[11px] uppercase tracking-wide text-ink-faint">搜索结果预览</p>
          <p class="text-[15px] text-info">{{ form.seoTitle || form.name || '商品标题' }} | Dreamy</p>
          <p class="text-[12px] text-ok">dreamy.com › product › {{ form.slug || 'slug' }}</p>
          <p class="text-[12.5px] text-ink-soft">{{ form.seoDesc || '商品的 meta 描述将显示在这里…' }}</p>
        </div>
        </div>
      </section>

      <!-- 底部操作 -->
      <div class="panel flex items-center justify-between px-6 py-4">
        <p class="text-[12px] text-ink-faint">所有区块已展开，可任意编辑后一次保存。</p>
        <div class="flex items-center gap-2">
          <button class="btn-outline">保存草稿</button>
          <button class="btn-gold" @click="saveAndPublish"><RocketLaunchIcon class="h-4 w-4" />保存并生成静态页</button>
        </div>
      </div>

      </div>
    </div>
  </div>
</template>
