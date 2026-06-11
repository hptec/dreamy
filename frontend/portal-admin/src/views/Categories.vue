<script setup lang="ts">
// PAGE-CAT-A03 / COMP-CAT-A03/A05 + COMP-CAT-M01（admin-prototype-alignment ALIGN-001/005）：
// 品类与标签 3-Tab（标准品类 / 属性集与字典 / 自定义标签——对照原型 Categories.vue L250-257）；
// Tab 2 自独立 AttributeSets 页迁入（dict/「品类×属性矩阵」sub-tab + 子品类覆盖卡片区），
// 矩阵整单保存 E-CAT-21 豁免沿用（ALIGN-006）；三语 name tab 增强保留（ALIGN-002 豁免）；
// 数据层接 E-CAT-15~18 / 27~34；维度删除收紧为 409506 引导——E-CAT-30 显式偏离（ALIGN-003 豁免）
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import AttributeDictPanel from '@/components/AttributeDictPanel.vue'
import AttributeMatrixPanel from '@/components/AttributeMatrixPanel.vue'
import { useAttributeMatrix } from '@/composables/useAttributeMatrix'
import { useCategoriesStore } from '@/stores/categories'
import { useAttributeStore } from '@/stores/attributes'
import { useTagsStore } from '@/stores/tags'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  PlusIcon, Bars3Icon, PencilSquareIcon, TrashIcon, ChevronRightIcon, TagIcon, XMarkIcon,
  ExclamationTriangleIcon, SwatchIcon,
} from '@heroicons/vue/24/outline'
import type { AdminCategoryNode, AttrVisibility, CategoryTranslation, Tag, TagDimension } from '@/api/types'

const categories = useCategoriesStore()
const attributes = useAttributeStore()
const tags = useTagsStore()
const toast = useToastStore()

// COMP-CAT-M01：主 Tab 由 2 值扩为 3 值（标准品类 / 属性集与字典 / 自定义标签，顺序文案对照原型）
const mainTab = ref<'taxonomy' | 'attributes' | 'tags'>('taxonomy')
// COMP-CAT-M01：Tab 2 内层 sub-tab（默认 dict；matrix 文案为「品类×属性矩阵」——ALIGN-005）
const attrSubTab = ref<'dict' | 'matrix'>('dict')
// COMP-CAT-M02-1：矩阵可编辑副本（composable；hasUnsavedChanges 供 Tab 切换 guard 共享）
const matrixCtl = useAttributeMatrix()

const route = useRoute()

/** STORE-CAT-M01：Tab 切换防丢失——矩阵有未保存变更时确认后才允许离开 */
function guardMatrixLeave(): boolean {
  if (mainTab.value === 'attributes' && attrSubTab.value === 'matrix' && matrixCtl.hasUnsavedChanges) {
    return window.confirm('有未保存的矩阵变更，离开将丢失，确认切换？')
  }
  return true
}

function switchMainTab(next: 'taxonomy' | 'attributes' | 'tags') {
  if (next === mainTab.value || !guardMatrixLeave()) return
  mainTab.value = next
}

function switchAttrSubTab(next: 'dict' | 'matrix') {
  if (next === attrSubTab.value || !guardMatrixLeave()) return
  attrSubTab.value = next
}

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

function load() {
  Promise.all([categories.fetch(), attributes.fetchAll()]).catch((e) => toast.error(bizMsg(e, '加载分类失败')))
  tags.fetchDimensions().then(() => {
    if (!activeTagDim.value && tags.dimensions.length) activeTagDim.value = tags.dimensions[0].id
    tags.fetchTags().catch(() => undefined)
  }).catch(() => undefined)
}

/* ===================== Tab 1：品类树 ===================== */

const STATES: AttrVisibility[] = ['visible', 'optional', 'hidden']
const STATE_LABELS: Record<AttrVisibility, string> = { visible: '必填', optional: '可选', hidden: '隐藏' }
const STATE_CLASSES: Record<AttrVisibility, string> = {
  visible: 'bg-gold/15 text-gold-deep',
  optional: 'bg-info/10 text-info',
  hidden: 'bg-canvas-warm text-ink-faint',
}

function attrSetInfo(setId: number | null) {
  if (setId == null) return null
  const set = attributes.sets.find((s) => s.id === setId)
  if (!set) return null
  return { label: set.label, count: set.items.filter((i) => i.visibility !== 'hidden').length }
}

/** FORM-CAT-A03：canDelete 预判（product_count===0 且无子级；后端 409502 兜底） */
function canDelete(cat: AdminCategoryNode): boolean {
  return (cat.productCount ?? 0) === 0 && !(cat.children?.length)
}

// ----- 编辑抽屉（根=改名/绑属性集；子=改名 + attr_overrides delta；均含三语 name tab） -----
const drawer = ref<{ node: AdminCategoryNode; parent: AdminCategoryNode | null } | null>(null)
const drawerLocale = ref<'en' | 'es' | 'fr'>('en')
const drawerName = ref('')
const drawerSetId = ref<number | null>(null)
const drawerAttrs = ref<Record<string, AttrVisibility>>({})
const drawerTrans = ref<Record<'es' | 'fr', string>>({ es: '', fr: '' })
const drawerError = ref('')
const drawerSaving = ref(false)

const drawerFilled = computed(() => ({
  en: !!drawerName.value,
  es: !!drawerTrans.value.es,
  fr: !!drawerTrans.value.fr,
}))

/** 仅展示有选项/toggle 的属性（原型同口径） */
const editableAttrs = computed(() => attributes.defs.filter((a) => a.type !== 'text'))

function openDrawer(node: AdminCategoryNode, parent: AdminCategoryNode | null) {
  drawer.value = { node, parent }
  drawerLocale.value = 'en'
  drawerError.value = ''
  drawerName.value = node.name
  drawerSetId.value = node.attributeSetId
  const byLocale = (l: 'es' | 'fr') => node.translations?.find((t) => t.locale === l)?.name || ''
  drawerTrans.value = { es: byLocale('es'), fr: byLocale('fr') }
  if (parent) {
    // 子分类：父级基础属性集 ⊕ 自身 delta
    const base = attributes.setAttrs(parent.attributeSetId)
    drawerAttrs.value = { ...base, ...(node.attrOverrides || {}) }
  } else {
    drawerAttrs.value = {}
  }
}

function cycleDrawerState(key: string) {
  const cur = drawerAttrs.value[key] || 'hidden'
  drawerAttrs.value = { ...drawerAttrs.value, [key]: STATES[(STATES.indexOf(cur) + 1) % STATES.length] }
}

function isInherited(key: string): boolean {
  const node = drawer.value?.node
  const parent = drawer.value?.parent
  if (!node || !parent) return true
  const base = attributes.setAttrs(parent.attributeSetId)
  return (drawerAttrs.value[key] || 'hidden') === (base[key] || 'hidden')
}

function buildDrawerTranslations(): CategoryTranslation[] {
  const rows: CategoryTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    if (drawerTrans.value[l].trim()) rows.push({ locale: l, name: drawerTrans.value[l].trim() })
  }
  return rows
}

async function saveDrawer() {
  if (!drawer.value) return
  const { node, parent } = drawer.value
  if (!drawerName.value.trim()) {
    drawerError.value = '名称必填'
    return
  }
  drawerSaving.value = true
  try {
    if (parent) {
      // 子分类：只保存与父级不同的 delta（V-CAT-047）
      const base = attributes.setAttrs(parent.attributeSetId)
      const delta: Record<string, AttrVisibility> = {}
      for (const key of Object.keys(drawerAttrs.value)) {
        if ((drawerAttrs.value[key] || 'hidden') !== (base[key] || 'hidden')) delta[key] = drawerAttrs.value[key]
      }
      await categories.update(node.id, {
        name: drawerName.value.trim(),
        parentId: parent.id,
        attrOverrides: delta,
        sort: node.sort,
        translations: buildDrawerTranslations(),
      })
    } else {
      // 根分类：改名/绑属性集（V-CAT-044 根须绑属性集）
      if (drawerSetId.value == null) {
        drawerError.value = '根分类需绑定属性集'
        drawerSaving.value = false
        return
      }
      await categories.update(node.id, {
        name: drawerName.value.trim(),
        parentId: null,
        attributeSetId: drawerSetId.value,
        sort: node.sort,
        translations: buildDrawerTranslations(),
      })
    }
    toast.success('已保存')
    drawer.value = null
  } catch (e) {
    drawerError.value = bizMsg(e, '保存失败')
  } finally {
    drawerSaving.value = false
  }
}

// ----- 新增根分类 -----
const showNewRoot = ref(false)
const newRootName = ref('')
const newRootSetId = ref<number | null>(null)
const newRootError = ref('')

async function confirmAddRoot() {
  const v = newRootName.value.trim()
  if (!v) return
  if (newRootSetId.value == null) {
    newRootError.value = '根分类需绑定属性集（V-CAT-044）'
    return
  }
  try {
    await categories.create({ name: v, parentId: null, attributeSetId: newRootSetId.value })
    toast.success('根品类已创建')
    showNewRoot.value = false
    newRootName.value = ''
    newRootSetId.value = null
    newRootError.value = ''
  } catch (e) {
    newRootError.value = bizMsg(e, '创建失败')
  }
}

// ----- 新增/改名子分类 -----
const addingChildFor = ref<number | null>(null)
const newChildName = ref('')

async function confirmAddChild(cat: AdminCategoryNode) {
  const v = newChildName.value.trim()
  if (!v) return
  try {
    await categories.create({ name: v, parentId: cat.id })
    toast.success('子类目已创建')
  } catch (e) {
    toast.error(bizMsg(e, '创建失败'))
  } finally {
    addingChildFor.value = null
    newChildName.value = ''
  }
}

// ----- 删除分类 -----
const confirmDeleteCat = ref<AdminCategoryNode | null>(null)
const confirmBusy = ref(false)

async function doDeleteCat() {
  if (!confirmDeleteCat.value) return
  confirmBusy.value = true
  try {
    await categories.remove(confirmDeleteCat.value.id)
    toast.success('已删除')
    confirmDeleteCat.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409502) toast.error('分类下仍有商品/子分类，不可删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

/* ===================== Tab 3：标签维度 / 标签 ===================== */

const activeTagDim = ref<number | ''>('')
const tagsByActiveDim = computed(() => (activeTagDim.value === '' ? [] : tags.tagsByDimension(activeTagDim.value)))

// 新建维度
const showNewDim = ref(false)
const newDimLabel = ref('')
async function confirmAddDim() {
  const v = newDimLabel.value.trim()
  if (!v) return
  try {
    const dim = await tags.saveDimension({ name: v })
    activeTagDim.value = dim.id
    showNewDim.value = false
    newDimLabel.value = ''
    toast.success('维度已创建')
  } catch (e) {
    toast.error(bizMsg(e, '创建失败'))
  }
}

// 删除维度（FORM-CAT-A05：409506 引导先清空标签——较原型级联删除收紧）
const confirmDeleteDim = ref<TagDimension | null>(null)
async function doDeleteDim() {
  if (!confirmDeleteDim.value) return
  confirmBusy.value = true
  try {
    await tags.removeDimension(confirmDeleteDim.value.id)
    if (activeTagDim.value === confirmDeleteDim.value.id) activeTagDim.value = tags.dimensions[0]?.id ?? ''
    toast.success('维度已删除')
    confirmDeleteDim.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409506) toast.error('维度下仍有标签，请先清空标签后再删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

// 新增/编辑标签（封面上传 scope=tag）
const tagModal = ref<{ editing: Tag | null } | null>(null)
const tagName = ref('')
const tagCover = ref('')
const tagSaving = ref(false)

function openTagModal(t?: Tag) {
  tagModal.value = { editing: t ?? null }
  tagName.value = t?.name || ''
  tagCover.value = t?.cover || ''
}

async function confirmSaveTag() {
  const v = tagName.value.trim()
  if (!v || activeTagDim.value === '') return
  tagSaving.value = true
  try {
    const editing = tagModal.value?.editing
    await tags.saveTag(
      {
        dimensionId: editing?.dimensionId ?? activeTagDim.value,
        name: v,
        cover: tagCover.value || null,
        status: editing?.status ?? 'enabled',
        translations: editing?.translations || [],
      },
      editing?.id,
    )
    toast.success('标签已保存')
    tagModal.value = null
  } catch (e) {
    toast.error(bizMsg(e, '保存失败'))
  } finally {
    tagSaving.value = false
  }
}

/** 标签 Toggle enabled↔status（乐观更新由 API 回写） */
async function toggleTag(t: Tag, on: boolean) {
  try {
    await tags.saveTag(
      {
        dimensionId: t.dimensionId,
        name: t.name,
        cover: t.cover,
        status: on ? 'enabled' : 'disabled',
        translations: t.translations || [],
      },
      t.id,
    )
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

const confirmDeleteTag = ref<Tag | null>(null)
async function doDeleteTag() {
  if (!confirmDeleteTag.value) return
  confirmBusy.value = true
  try {
    await tags.removeTag(confirmDeleteTag.value.id)
    toast.success('标签已删除')
    confirmDeleteTag.value = null
  } catch (e) {
    toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

onMounted(() => {
  // COMP-CAT-M01：深链支持 /categories?tab=attributes[&sub=matrix]（ALIGN-004 redirect 落点）
  const t = route.query.tab
  if (t === 'attributes' || t === 'tags') mainTab.value = t
  if (route.query.sub === 'matrix') attrSubTab.value = 'matrix'
  load()
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="品类与标签" subtitle="管理商品品类树和自定义营销标签" />

    <!-- Main Tabs（COMP-CAT-M01：3-Tab，顺序文案严格对照原型 Categories.vue L250-257——ALIGN-001） -->
    <div class="mb-4 flex items-center gap-1 border-b border-line">
      <button
        v-for="[key, label] in [['taxonomy', '标准品类'], ['attributes', '属性集与字典'], ['tags', '自定义标签']] as const"
        :key="key"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="mainTab === key ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="switchMainTab(key)"
      >{{ label }}</button>
    </div>

    <!-- ==================== Tab 1: 标准品类 ==================== -->
    <div v-show="mainTab === 'taxonomy'">
      <div class="mb-4 flex items-center justify-between gap-2">
        <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
          <SwatchIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
          <span>标准品类决定商品表单中出现哪些属性字段。每个商品只能属于一个品类。</span>
        </div>
        <button class="btn-outline shrink-0" @click="showNewRoot = true"><PlusIcon class="h-4 w-4" />添加根品类</button>
      </div>

      <EmptyState v-if="!categories.loading && !categories.tree.length" title="暂无品类" hint="添加根品类后才能创建商品。" />
      <div v-else class="flex flex-wrap gap-4">
        <div v-for="cat in categories.tree" :key="cat.id" class="panel flex w-72 shrink-0 flex-col p-4">
          <!-- 品类头 -->
          <div class="flex items-center gap-2">
            <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
            <span class="font-display text-base font-medium text-ink">{{ cat.name }}</span>
            <button class="btn-ghost ml-auto" @click="openDrawer(cat, null)"><PencilSquareIcon class="h-4 w-4" /></button>
            <button
              class="btn-danger-ghost disabled:opacity-40"
              :disabled="!canDelete(cat)"
              :title="canDelete(cat) ? '删除' : '分类下仍有商品/子分类'"
              @click="confirmDeleteCat = cat"
            ><TrashIcon class="h-4 w-4" /></button>
          </div>
          <!-- 属性集徽章 + 商品数 -->
          <div class="mt-2 flex flex-wrap items-center gap-1.5">
            <button
              v-if="cat.attributeSetId && attrSetInfo(cat.attributeSetId)"
              class="inline-flex items-center gap-1 rounded-full bg-gold/14 px-2 py-0.5 text-[11px] text-gold-deep transition-colors hover:bg-gold/25"
              @click="openDrawer(cat, null)"
            >
              <PencilSquareIcon class="h-3 w-3" />
              {{ attrSetInfo(cat.attributeSetId)!.label }} · {{ attrSetInfo(cat.attributeSetId)!.count }} 项
            </button>
            <span v-else class="flex items-center gap-1 rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">
              <ExclamationTriangleIcon class="h-3 w-3" />未配置属性集
            </span>
            <span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">{{ cat.productCount ?? 0 }} 件商品</span>
          </div>
          <!-- 子类目 chips -->
          <div class="mt-3 flex flex-1 flex-col gap-2 border-t border-line pt-3">
            <p class="text-[11px] font-medium uppercase tracking-wider text-ink-faint">子类目 ({{ cat.children?.length ?? 0 }})</p>
            <div class="flex flex-wrap gap-2">
              <span v-for="ch in cat.children || []" :key="ch.id" class="flex items-center gap-1 rounded-luxe border border-line px-2.5 py-1 text-[12px] text-ink-soft">
                <ChevronRightIcon class="h-3 w-3 text-ink-faint" />
                <span class="cursor-pointer" @dblclick="openDrawer(ch, cat)">{{ ch.name }}</span>
                <button
                  class="rounded px-1 py-0.5 text-[10px] leading-none transition-colors hover:bg-info/20"
                  :class="Object.keys(ch.attrOverrides || {}).length ? 'bg-info/12 text-info' : 'bg-canvas-warm text-ink-faint'"
                  @click="openDrawer(ch, cat)"
                >{{ Object.keys(ch.attrOverrides || {}).length ? Object.keys(ch.attrOverrides || {}).length + '覆盖' : '继承' }}</button>
                <button
                  class="text-ink-faint hover:text-danger disabled:opacity-40"
                  :disabled="!canDelete(ch)"
                  :title="canDelete(ch) ? '删除' : '分类下仍有商品'"
                  @click="confirmDeleteCat = ch"
                ><XMarkIcon class="h-3 w-3" /></button>
              </span>
              <span v-if="addingChildFor === cat.id" class="flex items-center gap-1 rounded-luxe border border-gold px-2.5 py-1">
                <input v-model="newChildName" class="w-24 bg-transparent text-[12px] outline-none" placeholder="子类目名" @keyup.enter="confirmAddChild(cat)" @keyup.escape="addingChildFor = null" />
              </span>
            </div>
            <button class="btn-ghost mt-auto self-start text-[12px]" @click="addingChildFor = cat.id; newChildName = ''"><PlusIcon class="h-3 w-3" />添加子类目</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== Tab 2: 属性集与字典（COMP-CAT-M02：自 AttributeSets 页迁入——ALIGN-001） ==================== -->
    <div v-show="mainTab === 'attributes'">
      <!-- sub-tab 切换条（样式同主 Tab 条；矩阵文案「品类×属性矩阵」——ALIGN-005/COMP-CAT-M02-5） -->
      <div class="mb-4 flex items-center gap-1 border-b border-line">
        <button
          v-for="[key, label] in [['dict', '属性字典'], ['matrix', '品类×属性矩阵']] as const"
          :key="key"
          class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
          :class="attrSubTab === key ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
          @click="switchAttrSubTab(key)"
        >{{ label }}</button>
      </div>
      <AttributeDictPanel v-show="attrSubTab === 'dict'" />
      <AttributeMatrixPanel v-show="attrSubTab === 'matrix'" :ctl="matrixCtl" />
    </div>

    <!-- ==================== Tab 3: 自定义标签 ==================== -->
    <div v-show="mainTab === 'tags'">
      <div class="mb-4 flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
        <TagIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
        <span>自定义标签仅用于前台导航和营销聚合，不影响商品属性表单。商品可多选标签。</span>
      </div>

      <!-- Dimension sub-tabs（删除收紧：409506 引导） -->
      <div class="mb-4 flex items-center gap-1 border-b border-line">
        <button
          v-for="d in tags.dimensions"
          :key="d.id"
          class="group flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-[13px] transition-colors"
          :class="activeTagDim === d.id ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
          @click="activeTagDim = d.id"
        >
          {{ d.name }}
          <span
            class="invisible ml-0.5 cursor-pointer rounded p-0.5 text-ink-faint hover:text-danger group-hover:visible"
            @click.stop="confirmDeleteDim = d"
          ><XMarkIcon class="h-3 w-3" /></span>
        </button>
        <button class="ml-1 flex items-center gap-1 rounded-luxe px-3 py-2 text-[12px] text-ink-faint hover:bg-canvas-warm hover:text-ink" @click="showNewDim = true">
          <PlusIcon class="h-3.5 w-3.5" />新建维度
        </button>
      </div>

      <EmptyState v-if="!tags.dimensions.length" title="暂无标签维度" hint="先创建维度（如 风格 Style），再添加标签。" />
      <div v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        <div v-for="t in tagsByActiveDim" :key="t.id" class="panel overflow-hidden">
          <div class="relative aspect-[3/4]">
            <img v-if="t.cover" :src="t.cover" class="h-full w-full object-cover" />
            <div v-else class="flex h-full w-full items-center justify-center bg-canvas-warm text-ink-faint"><TagIcon class="h-8 w-8" /></div>
            <div class="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent"></div>
            <p class="absolute bottom-2 left-3 font-display text-lg text-white">{{ t.name }}</p>
          </div>
          <div class="flex items-center justify-between p-3">
            <span class="text-[12px] text-ink-faint">{{ t.productCount ?? 0 }} 件</span>
            <div class="flex items-center gap-2">
              <Toggle :model-value="t.status === 'enabled'" @update:model-value="toggleTag(t, $event)" />
              <button class="btn-ghost" @click="openTagModal(t)"><PencilSquareIcon class="h-3.5 w-3.5" /></button>
              <button class="btn-danger-ghost" @click="confirmDeleteTag = t"><TrashIcon class="h-3.5 w-3.5" /></button>
            </div>
          </div>
        </div>
        <button class="panel flex aspect-[3/4] flex-col items-center justify-center gap-2 border-2 border-dashed text-ink-faint hover:border-gold" @click="openTagModal()">
          <PlusIcon class="h-6 w-6" /><span class="text-[12px]">新增标签</span>
        </button>
      </div>
    </div>

    <!-- ===== Modal: 添加根品类 ===== -->
    <Teleport to="body">
      <div v-if="showNewRoot" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showNewRoot = false)">
        <div class="panel w-96 p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">添加根品类</h3>
            <button class="btn-ghost" @click="showNewRoot = false"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">品类名称 *</label>
              <input v-model="newRootName" class="field" placeholder="如：Flower Girl" @keyup.enter="confirmAddRoot" />
            </div>
            <div>
              <label class="field-label">绑定属性集 *</label>
              <select v-model="newRootSetId" class="field">
                <option :value="null" disabled>请选择…</option>
                <option v-for="s in attributes.sets" :key="s.id" :value="s.id">{{ s.label }}</option>
              </select>
            </div>
            <p v-if="newRootError" class="text-[11px] text-danger">{{ newRootError }}</p>
            <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-3 py-2 text-[11px] text-ink-soft">
              <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gold-deep" />
              <span>根品类必须绑定属性集，否则商品编辑表单将缺少属性字段。</span>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="showNewRoot = false">取消</button>
            <button class="btn-gold" :disabled="!newRootName.trim()" @click="confirmAddRoot">创建</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===== Modal: 新增/编辑标签（封面上传 scope=tag） ===== -->
    <Teleport to="body">
      <div v-if="tagModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (tagModal = null)">
        <div class="panel w-96 p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">{{ tagModal.editing ? '编辑标签' : '新增标签' }}</h3>
            <button class="btn-ghost" @click="tagModal = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">标签名称 *</label>
              <input v-model="tagName" class="field" placeholder="如：Boho、Spring 2026" @keyup.enter="confirmSaveTag" />
            </div>
            <div>
              <label class="field-label">封面图片</label>
              <div class="w-32">
                <MediaUploadCard v-model="tagCover" scope="tag" aspect="aspect-[3/4]" label="点击上传" />
              </div>
              <p class="mt-1.5 text-[11px] text-ink-faint">用于前台导航卡片展示，建议竖图 3:4。不上传则以纯文字标签展示。</p>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="tagModal = null">取消</button>
            <button class="btn-gold" :disabled="!tagName.trim() || tagSaving" @click="confirmSaveTag">{{ tagSaving ? '保存中…' : tagModal.editing ? '保存' : '添加' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===== Modal: 新建维度 ===== -->
    <Teleport to="body">
      <div v-if="showNewDim" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showNewDim = false)">
        <div class="panel w-96 p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">新建标签维度</h3>
            <button class="btn-ghost" @click="showNewDim = false"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">维度名称 *</label>
              <input v-model="newDimLabel" class="field" placeholder="如：风格 Style、用途 Usage" @keyup.enter="confirmAddDim" />
            </div>
            <p class="text-[11px] text-ink-faint">标签维度仅用于前台营销聚合与导航，不影响商品属性表单。</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="showNewDim = false">取消</button>
            <button class="btn-gold" :disabled="!newDimLabel.trim()" @click="confirmAddDim">创建维度</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===== 分类编辑抽屉（COMP-CAT-A05：根=改名/绑属性集；子=delta 三态 + 三语 name tab） ===== -->
    <Teleport to="body">
      <div v-if="drawer" class="fixed inset-0 z-50 flex justify-end bg-ink/40" v-dismiss="() => (drawer = null)">
        <div class="flex h-full w-[480px] flex-col bg-canvas shadow-2xl">
          <div class="flex items-center justify-between border-b border-line px-6 py-4">
            <div>
              <p class="text-[11px] text-ink-faint">{{ drawer.parent ? drawer.parent.name : '根品类' }}</p>
              <h3 class="text-[15px] font-medium text-ink">{{ drawer.node.name }} · {{ drawer.parent ? '属性覆盖配置' : '品类配置' }}</h3>
            </div>
            <button class="btn-ghost" @click="drawer = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>

          <div class="flex-1 space-y-4 overflow-y-auto px-6 py-4">
            <LocaleTabs v-model="drawerLocale" :filled="drawerFilled" />

            <div v-show="drawerLocale === 'en'" class="space-y-4">
              <div>
                <label class="field-label">名称 *</label>
                <input v-model="drawerName" class="field" />
              </div>
              <div v-if="!drawer.parent">
                <label class="field-label">绑定属性集 *</label>
                <select v-model="drawerSetId" class="field">
                  <option :value="null" disabled>请选择…</option>
                  <option v-for="s in attributes.sets" :key="s.id" :value="s.id">{{ s.label }}</option>
                </select>
              </div>

              <!-- 子分类 delta 三态 -->
              <template v-if="drawer.parent">
                <div class="rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
                  点击状态循环切换：
                  <span class="rounded bg-gold/15 px-1.5 py-0.5 text-[10px] text-gold-deep">必填</span>
                  <span class="mx-1">→</span>
                  <span class="rounded bg-info/10 px-1.5 py-0.5 text-[10px] text-info">可选</span>
                  <span class="mx-1">→</span>
                  <span class="rounded bg-canvas-warm px-1.5 py-0.5 text-[10px] text-ink-faint">隐藏</span>
                  · 灰色底 = 继承父级，有色底 = 已覆盖
                </div>
                <div class="space-y-2">
                  <div
                    v-for="attr in editableAttrs"
                    :key="attr.id"
                    class="flex items-center justify-between rounded-luxe px-3 py-2.5 transition-colors"
                    :class="isInherited(attr.key) ? 'bg-canvas-warm/30' : 'bg-info/5 ring-1 ring-info/20'"
                  >
                    <div class="flex items-center gap-2">
                      <span class="text-[13px] text-ink">{{ attr.label }}</span>
                      <span v-if="!isInherited(attr.key)" class="rounded-full bg-info/12 px-1.5 py-0.5 text-[10px] leading-none text-info">覆盖</span>
                      <span v-else class="text-[11px] text-ink-faint">继承父级</span>
                    </div>
                    <button
                      class="min-w-[3.5rem] rounded px-2.5 py-1 text-[11px] font-medium transition-colors"
                      :class="STATE_CLASSES[drawerAttrs[attr.key] || 'hidden']"
                      @click="cycleDrawerState(attr.key)"
                    >{{ STATE_LABELS[drawerAttrs[attr.key] || 'hidden'] }}</button>
                  </div>
                </div>
              </template>
            </div>

            <div v-for="l in ['es', 'fr'] as const" v-show="drawerLocale === l" :key="l">
              <label class="field-label">名称（{{ l.toUpperCase() }}）</label>
              <input v-model="drawerTrans[l]" class="field" />
              <p class="mt-1.5 text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13）。</p>
            </div>

            <p v-if="drawerError" class="text-[11px] text-danger">{{ drawerError }}</p>
          </div>

          <div class="flex items-center justify-between border-t border-line px-6 py-4">
            <span class="text-[12px] text-ink-faint">
              <template v-if="drawer.parent">{{ Object.keys(drawer.node.attrOverrides || {}).length }} 项覆盖（保存后生效）</template>
            </span>
            <div class="flex gap-2">
              <button class="btn-outline" @click="drawer = null">取消</button>
              <button class="btn-gold" :disabled="drawerSaving" @click="saveDrawer">{{ drawerSaving ? '保存中…' : '保存' }}</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="!!confirmDeleteCat"
      title="删除分类"
      :message="`确认删除分类「${confirmDeleteCat?.name}」？`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteCat"
      @cancel="confirmDeleteCat = null"
    />
    <ConfirmDialog
      :open="!!confirmDeleteDim"
      title="删除标签维度"
      :message="`确认删除维度「${confirmDeleteDim?.name}」？维度下仍有标签时将被拒绝（需先清空标签）。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteDim"
      @cancel="confirmDeleteDim = null"
    />
    <ConfirmDialog
      :open="!!confirmDeleteTag"
      title="删除标签"
      :message="`确认删除标签「${confirmDeleteTag?.name}」？商品上的该标签将被摘除。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteTag"
      @cancel="confirmDeleteTag = null"
    />
  </div>
</template>
