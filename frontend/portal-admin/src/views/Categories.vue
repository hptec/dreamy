<script setup lang="ts">
// PAGE-CAT-A03 / COMP-CAT-A03/A05（对照原型 Categories.vue「分类管理」）：
// 品类与集合 3-Tab（标准品类 / 属性集与字典 / 集合）；
// 属性集三态配置走 Tab 1 品类卡片徽章 → 属性集配置抽屉（原型 openCatSetDrawer 同款交互），
// Tab 2 仅保留属性字典 + 属性集管理入口（矩阵 sub-tab 已按原型移除）；
// 三语 name tab 增强保留（收进抽屉内，交互入口按原型）；
// 数据层接 E-CAT-15~18 / 27~34；分组删除收紧为 409506 引导——E-CAT-30 显式偏离（ALIGN-003 豁免）
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import AttributeDictPanel from '@/components/AttributeDictPanel.vue'
import AiTranslateButton from '@/components/ai/AiTranslateButton.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useCategoriesStore } from '@/stores/categories'
import { useAttributeStore } from '@/stores/attributes'
import { useCollectionsStore } from '@/stores/collections'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import {
  PlusIcon, Bars3Icon, PencilSquareIcon, TrashIcon, ChevronRightIcon, TagIcon, XMarkIcon,
  ExclamationTriangleIcon, SwatchIcon,
} from '@heroicons/vue/24/outline'
import { AttrVisibility, AttributeDefType, CollectionStatus } from '@/api/types'
import type { AdminCategoryNode, AttributeDef, AttributeSet, CategoryTranslation, Collection, CollectionGroup } from '@/api/types'

const categories = useCategoriesStore()
const attributes = useAttributeStore()
const collections = useCollectionsStore()
const toast = useToastStore()

// 主 Tab：标准品类 / 属性集与字典 / 集合（顺序文案对照原型）
const mainTab = ref<'taxonomy' | 'attributes' | 'collections'>('taxonomy')

const route = useRoute()

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

function load() {
  Promise.all([categories.fetch(), attributes.fetchAll()]).catch((e) => toast.error(bizMsg(e, '加载分类失败')))
  collections.fetchGroups().then(() => {
    if (!activeGroup.value && collections.groups.length) activeGroup.value = collections.groups[0].id
    collections.fetchCollections().catch(() => undefined)
  }).catch(() => undefined)
}

/* ===================== Tab 1：品类树 ===================== */

const STATES: AttrVisibility[] = [AttrVisibility.VISIBLE, AttrVisibility.OPTIONAL, AttrVisibility.HIDDEN]
const STATE_LABELS: Record<AttrVisibility, string> = {
  [AttrVisibility.VISIBLE]: '必填',
  [AttrVisibility.OPTIONAL]: '可选',
  [AttrVisibility.HIDDEN]: '隐藏',
}
const STATE_CLASSES: Record<AttrVisibility, string> = {
  [AttrVisibility.VISIBLE]: 'bg-gold/15 text-gold-deep',
  [AttrVisibility.OPTIONAL]: 'bg-info/10 text-info',
  [AttrVisibility.HIDDEN]: 'bg-canvas-warm text-ink-faint',
}

function attrSetInfo(setId: number | null) {
  if (setId == null) return null
  const set = attributes.sets.find((s) => s.id === setId)
  if (!set) return null
  return { label: set.label, count: set.items.filter((i) => i.visibility !== AttrVisibility.HIDDEN).length }
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

/** 所有属性定义均可在属性集中配置 */
const editableAttrs = computed(() => attributes.defs)

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
  const cur = drawerAttrs.value[key] || AttrVisibility.HIDDEN
  drawerAttrs.value = { ...drawerAttrs.value, [key]: STATES[(STATES.indexOf(cur) + 1) % STATES.length] }
}

function isInherited(key: string): boolean {
  const node = drawer.value?.node
  const parent = drawer.value?.parent
  if (!node || !parent) return true
  const base = attributes.setAttrs(parent.attributeSetId)
  return (drawerAttrs.value[key] || AttrVisibility.HIDDEN) === (base[key] || AttrVisibility.HIDDEN)
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
        if ((drawerAttrs.value[key] || AttrVisibility.HIDDEN) !== (base[key] || AttrVisibility.HIDDEN)) delta[key] = drawerAttrs.value[key]
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

// 子类目双击行内改名（原型 startEditChild/confirmEditChild 同款交互）
const editingChild = ref<{ catId: number | null; childId: number | null }>({ catId: null, childId: null })
const editChildName = ref('')

function startEditChild(cat: AdminCategoryNode, ch: AdminCategoryNode) {
  editingChild.value = { catId: cat.id, childId: ch.id }
  editChildName.value = ch.name
}

async function confirmEditChild(cat: AdminCategoryNode, ch: AdminCategoryNode) {
  const v = editChildName.value.trim()
  editingChild.value = { catId: null, childId: null }
  if (!v || v === ch.name) return
  try {
    await categories.update(ch.id, {
      name: v,
      parentId: cat.id,
      attrOverrides: ch.attrOverrides || {},
      sort: ch.sort,
      translations: ch.translations || [],
    })
    toast.success('已重命名')
  } catch (e) {
    toast.error(bizMsg(e, '保存失败'))
  }
}

// ----- 属性集配置抽屉（原型 openCatSetDrawer/openSetDrawer 同款：三态循环 + 绑定品类 chips + 拖动排序） -----
const setDrawer = ref<{ set: AttributeSet | null } | null>(null)
const setDrawerLabel = ref('')
const setDrawerAttrs = ref<Record<string, AttrVisibility>>({})
const setDrawerError = ref('')
const setDrawerSaving = ref(false)

// 拖拽排序（HTML5 DnD，参考 ProductEdit.vue L266-277）
const setDrawerDragIndex = ref<number | null>(null)
const setDrawerOverIndex = ref<number | null>(null)

const setDrawerCategories = computed(() => {
  const set = setDrawer.value?.set
  if (!set) return []
  return categories.tree.filter((c) => c.attributeSetId === set.id).map((c) => c.name)
})

// 有序属性列表（按当前排序）
const setDrawerOrderedAttrs = ref<Array<{ key: string; def: AttributeDef }>>([])

function openSetDrawer(set?: AttributeSet) {
  setDrawer.value = { set: set ?? null }
  setDrawerLabel.value = set?.label || ''
  const existing = set ? attributes.setAttrs(set.id) : {}
  const all: Record<string, AttrVisibility> = {}
  for (const d of attributes.defs) all[d.key] = existing[d.key] ?? AttrVisibility.HIDDEN
  setDrawerAttrs.value = all
  setDrawerError.value = ''

  // 初始化有序列表：从属性集已有顺序恢复，新增属性追加尾部
  const existingKeys = set?.items.map((i) => attributes.defById(i.attributeId)?.key).filter(Boolean) as string[] ?? []
  const allKeys = new Set(attributes.defs.map((d) => d.key))
  const newKeys = attributes.defs.filter((d) => !existingKeys.includes(d.key)).map((d) => d.key)
  const ordered = [...existingKeys, ...newKeys].filter((k) => allKeys.has(k))
  setDrawerOrderedAttrs.value = ordered.map((k) => {
    const def = attributes.defs.find((d) => d.key === k)!
    return { key: k, def }
  })
}

/** 主品类卡片点徽章 → 解析绑定的属性集后打开抽屉（原型 openCatSetDrawer） */
function openCatSetDrawer(cat: AdminCategoryNode) {
  const set = attributes.sets.find((s) => s.id === cat.attributeSetId)
  if (set) openSetDrawer(set)
}

function cycleSetDrawerState(key: string) {
  const cur = setDrawerAttrs.value[key] || AttrVisibility.HIDDEN
  setDrawerAttrs.value = { ...setDrawerAttrs.value, [key]: STATES[(STATES.indexOf(cur) + 1) % STATES.length] }
}

function onSetDrawerDragStart(i: number) {
  setDrawerDragIndex.value = i
}

function onSetDrawerDrop(i: number) {
  if (setDrawerDragIndex.value == null || setDrawerDragIndex.value === i) return
  const list = setDrawerOrderedAttrs.value
  const [moved] = list.splice(setDrawerDragIndex.value, 1)
  list.splice(i, 0, moved)
  setDrawerDragIndex.value = null
  setDrawerOverIndex.value = null
}

async function saveSetDrawer() {
  if (!setDrawer.value) return
  const label = setDrawerLabel.value.trim()
  if (!label) { setDrawerError.value = '名称必填'; return }
  setDrawerSaving.value = true
  try {
    // 按拖动后的顺序组装 items（非 hidden 的按新顺序提交）
    const items = setDrawerOrderedAttrs.value
      .filter((row) => (setDrawerAttrs.value[row.key] ?? AttrVisibility.HIDDEN) !== AttrVisibility.HIDDEN)
      .map((row) => ({ attributeId: row.def.id, visibility: setDrawerAttrs.value[row.key] }))
    await attributes.saveSet({ label, items }, setDrawer.value.set?.id)
    toast.success('属性集已保存')
    setDrawer.value = null
  } catch (e) {
    setDrawerError.value = bizMsg(e, '保存失败')
  } finally {
    setDrawerSaving.value = false
  }
}

// 删除属性集（409503：被分类引用时拒绝）
const confirmDeleteSet = ref<AttributeSet | null>(null)
async function doDeleteSet() {
  if (!confirmDeleteSet.value) return
  confirmBusy.value = true
  try {
    await attributes.removeSet(confirmDeleteSet.value.id)
    toast.success('已删除')
    confirmDeleteSet.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409503) toast.error('该属性集被分类引用，不可删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
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

/* ===================== Tab 3：集合分组 / 集合 ===================== */

const activeGroup = ref<number | ''>('')
const collectionsByActiveGroup = computed(() => (activeGroup.value === '' ? [] : collections.collectionsByGroup(activeGroup.value)))

// 新建分组
const showNewGroup = ref(false)
const newGroupLabel = ref('')
async function confirmAddGroup() {
  const v = newGroupLabel.value.trim()
  if (!v) return
  try {
    const group = await collections.saveGroup({ name: v })
    activeGroup.value = group.id
    showNewGroup.value = false
    newGroupLabel.value = ''
    toast.success('分组已创建')
  } catch (e) {
    toast.error(bizMsg(e, '创建失败'))
  }
}

// 删除分组（FORM-CAT-A05：409506 引导先清空集合——较原型级联删除收紧）
const confirmDeleteGroup = ref<CollectionGroup | null>(null)
async function doDeleteGroup() {
  if (!confirmDeleteGroup.value) return
  confirmBusy.value = true
  try {
    await collections.removeGroup(confirmDeleteGroup.value.id)
    if (activeGroup.value === confirmDeleteGroup.value.id) activeGroup.value = collections.groups[0]?.id ?? ''
    toast.success('分组已删除')
    confirmDeleteGroup.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409506) toast.error('分组下仍有集合，请先清空集合后再删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

// 新增/编辑集合（封面上传 scope=collection）
const collectionModal = ref<{ editing: Collection | null } | null>(null)
const collectionName = ref('')
const collectionCover = ref('')
const collectionSaving = ref(false)

function openCollectionModal(c?: Collection) {
  collectionModal.value = { editing: c ?? null }
  collectionName.value = c?.name || ''
  collectionCover.value = c?.cover || ''
}

async function confirmSaveCollection() {
  const v = collectionName.value.trim()
  if (!v || activeGroup.value === '') return
  collectionSaving.value = true
  try {
    const editing = collectionModal.value?.editing
    await collections.saveCollection(
      {
        collectionGroupId: editing?.collectionGroupId ?? activeGroup.value,
        name: v,
        cover: collectionCover.value || null,
        status: editing?.status ?? CollectionStatus.ENABLED,
        translations: editing?.translations || [],
      },
      editing?.id,
    )
    toast.success('集合已保存')
    collectionModal.value = null
  } catch (e) {
    toast.error(bizMsg(e, '保存失败'))
  } finally {
    collectionSaving.value = false
  }
}

/** 集合 Toggle enabled↔status（乐观更新由 API 回写） */
async function toggleCollection(c: Collection, on: boolean) {
  try {
    await collections.saveCollection(
      {
        collectionGroupId: c.collectionGroupId,
        name: c.name,
        cover: c.cover,
        status: on ? CollectionStatus.ENABLED : CollectionStatus.DISABLED,
        translations: c.translations || [],
      },
      c.id,
    )
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

const confirmDeleteCollection = ref<Collection | null>(null)
async function doDeleteCollection() {
  if (!confirmDeleteCollection.value) return
  confirmBusy.value = true
  try {
    await collections.removeCollection(confirmDeleteCollection.value.id)
    toast.success('集合已删除')
    confirmDeleteCollection.value = null
  } catch (e) {
    toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

onMounted(() => {
  // 深链支持 /categories?tab=attributes|collections（/attribute-sets 旧路由 redirect 落点）
  const t = route.query.tab
  if (t === 'attributes' || t === 'collections') mainTab.value = t
  load()
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="品类与集合" subtitle="管理商品品类树和营销集合" />

    <!-- Main Tabs（3-Tab，顺序文案严格对照原型 Categories.vue L250-257） -->
    <div class="mb-4 flex items-center gap-1 border-b border-line">
      <button
        v-for="[key, label] in [['taxonomy', '标准品类'], ['attributes', '属性集与字典'], ['collections', '集合']] as const"
        :key="key"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="mainTab === key ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="mainTab = key"
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
          <!-- 品类头（对照原型：无独立编辑按钮，双击名称进入品类配置抽屉） -->
          <div class="flex items-center gap-2">
            <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
            <span class="cursor-pointer font-display text-base font-medium text-ink" title="双击编辑品类" @dblclick="openDrawer(cat, null)">{{ cat.name }}</span>
            <button
              class="btn-danger-ghost ml-auto disabled:opacity-40"
              :disabled="!canDelete(cat)"
              :title="canDelete(cat) ? '删除' : '分类下仍有商品/子分类'"
              @click="confirmDeleteCat = cat"
            ><TrashIcon class="h-4 w-4" /></button>
          </div>
          <!-- 属性集徽章（点击打开属性集三态配置抽屉——原型 openCatSetDrawer）+ 商品数 -->
          <div class="mt-2 flex flex-wrap items-center gap-1.5">
            <button
              v-if="cat.attributeSetId && attrSetInfo(cat.attributeSetId)"
              class="inline-flex items-center gap-1 rounded-full bg-gold/14 px-2 py-0.5 text-[11px] text-gold-deep transition-colors hover:bg-gold/25"
              @click="openCatSetDrawer(cat)"
            >
              <PencilSquareIcon class="h-3 w-3" />
              {{ attrSetInfo(cat.attributeSetId)!.label }}属性集 · {{ attrSetInfo(cat.attributeSetId)!.count }} 项
            </button>
            <span v-else class="flex items-center gap-1 rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">
              <ExclamationTriangleIcon class="h-3 w-3" />未配置属性集
            </span>
            <span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">{{ cat.productCount ?? 0 }} 件商品</span>
          </div>
          <!-- 子类目 chips（双击行内改名；「继承/N覆盖」开属性覆盖抽屉——原型同款） -->
          <div class="mt-3 flex flex-1 flex-col gap-2 border-t border-line pt-3">
            <p class="text-[11px] font-medium uppercase tracking-wider text-ink-faint">子类目 ({{ cat.children?.length ?? 0 }})</p>
            <div class="flex flex-wrap gap-2">
              <span v-for="ch in cat.children || []" :key="ch.id" class="group flex items-center gap-1 rounded-luxe border border-line px-2.5 py-1 text-[12px] text-ink-soft">
                <template v-if="editingChild.catId === cat.id && editingChild.childId === ch.id">
                  <input
                    v-model="editChildName"
                    class="w-20 border-b border-gold bg-transparent text-[12px] outline-none"
                    @keyup.enter="confirmEditChild(cat, ch)"
                    @keyup.escape="editingChild = { catId: null, childId: null }"
                  />
                </template>
                <template v-else>
                  <ChevronRightIcon class="h-3 w-3 text-ink-faint" />
                  <span class="cursor-pointer" @dblclick="startEditChild(cat, ch)">{{ ch.name }}</span>
                  <button
                    class="rounded px-1 py-0.5 text-[10px] leading-none transition-colors hover:bg-info/20"
                    :class="Object.keys(ch.attrOverrides || {}).length ? 'bg-info/12 text-info' : 'bg-canvas-warm text-ink-faint'"
                    @click="openDrawer(ch, cat)"
                  >{{ Object.keys(ch.attrOverrides || {}).length ? Object.keys(ch.attrOverrides || {}).length + '覆盖' : '继承' }}</button>
                  <button
                    class="invisible text-ink-faint hover:text-danger disabled:opacity-40 group-hover:visible"
                    :disabled="!canDelete(ch)"
                    :title="canDelete(ch) ? '删除' : '分类下仍有商品'"
                    @click="confirmDeleteCat = ch"
                  ><XMarkIcon class="h-3 w-3" /></button>
                </template>
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

    <!-- ==================== Tab 2: 属性集与字典（对照原型：仅属性字典；属性集三态配置入口在 Tab 1 徽章） ==================== -->
    <div v-show="mainTab === 'attributes'">
      <div class="mb-4 flex items-center justify-between gap-2">
        <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
          <SwatchIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
          <span>属性字典维护全局可选值。各品类启用哪些属性、必填或可选，在「标准品类」Tab 的品类卡片上点击属性集徽章配置。</span>
        </div>
        <button class="btn-outline shrink-0" @click="openSetDrawer()"><PlusIcon class="h-4 w-4" />新增属性集</button>
      </div>

      <!-- 属性集 chips（功能保留区：点击开三态配置抽屉；hover 显删除，被引用时禁用） -->
      <div v-if="attributes.sets.length" class="mb-4 flex flex-wrap gap-2">
        <span v-for="s in attributes.sets" :key="s.id" class="group flex items-center gap-1 rounded-full border border-line px-1 py-0.5">
          <button class="inline-flex items-center gap-1 rounded-full bg-gold/14 px-2 py-0.5 text-[11px] text-gold-deep transition-colors hover:bg-gold/25" @click="openSetDrawer(s)">
            <PencilSquareIcon class="h-3 w-3" />
            {{ s.label }} · {{ s.categoryCount ?? 0 }} 个分类引用
          </button>
          <button
            class="invisible mr-1 text-ink-faint hover:text-danger disabled:opacity-40 group-hover:visible"
            :disabled="(s.categoryCount ?? 0) > 0"
            :title="(s.categoryCount ?? 0) > 0 ? '被分类引用，不可删除' : '删除'"
            @click="confirmDeleteSet = s"
          ><XMarkIcon class="h-3 w-3" /></button>
        </span>
      </div>

      <AttributeDictPanel />
    </div>

    <!-- ==================== Tab 3: 集合 ==================== -->
    <div v-show="mainTab === 'collections'">
      <div class="mb-4 flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
        <TagIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
        <span>集合仅用于前台导航和营销聚合，不影响商品属性表单。商品可多选集合。</span>
      </div>

      <!-- Group sub-tabs（删除收紧：409506 引导） -->
      <div class="mb-4 flex items-center gap-1 border-b border-line">
        <button
          v-for="g in collections.groups"
          :key="g.id"
          class="group flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-[13px] transition-colors"
          :class="activeGroup === g.id ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
          @click="activeGroup = g.id"
        >
          {{ g.name }}
          <span
            class="invisible ml-0.5 cursor-pointer rounded p-0.5 text-ink-faint hover:text-danger group-hover:visible"
            @click.stop="confirmDeleteGroup = g"
          ><XMarkIcon class="h-3 w-3" /></span>
        </button>
        <button class="ml-1 flex items-center gap-1 rounded-luxe px-3 py-2 text-[12px] text-ink-faint hover:bg-canvas-warm hover:text-ink" @click="showNewGroup = true">
          <PlusIcon class="h-3.5 w-3.5" />新建分组
        </button>
      </div>

      <EmptyState v-if="!collections.groups.length" title="暂无集合分组" hint="先创建分组（如 风格 Style），再添加集合。" />
      <div v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        <div v-for="c in collectionsByActiveGroup" :key="c.id" class="panel overflow-hidden">
          <div class="relative aspect-[3/4]">
            <img v-if="c.cover" :src="c.cover" class="h-full w-full object-cover" />
            <div v-else class="flex h-full w-full items-center justify-center bg-canvas-warm text-ink-faint"><TagIcon class="h-8 w-8" /></div>
            <div class="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent"></div>
            <p class="absolute bottom-2 left-3 font-display text-lg text-white">{{ c.name }}</p>
          </div>
          <div class="flex items-center justify-between p-3">
            <span class="text-[12px] text-ink-faint">{{ c.productCount ?? 0 }} 件</span>
            <div class="flex items-center gap-2">
              <Toggle :model-value="c.status === CollectionStatus.ENABLED" @update:model-value="toggleCollection(c, $event)" />
              <button class="btn-ghost" @click="openCollectionModal(c)"><PencilSquareIcon class="h-3.5 w-3.5" /></button>
              <button class="btn-danger-ghost" @click="confirmDeleteCollection = c"><TrashIcon class="h-3.5 w-3.5" /></button>
            </div>
          </div>
        </div>
        <button class="panel flex aspect-[3/4] flex-col items-center justify-center gap-2 border-2 border-dashed text-ink-faint hover:border-gold" @click="openCollectionModal()">
          <PlusIcon class="h-6 w-6" /><span class="text-[12px]">新增集合</span>
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
              <SelectMenu
                v-model="newRootSetId"
                :options="attributes.sets.map((s) => ({ value: s.id, label: s.label }))"
                placeholder="请选择…"
              />
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

    <!-- ===== Modal: 新增/编辑集合（封面上传 scope=collection） ===== -->
    <Teleport to="body">
      <div v-if="collectionModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (collectionModal = null)">
        <div class="panel w-96 p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">{{ collectionModal.editing ? '编辑集合' : '新增集合' }}</h3>
            <button class="btn-ghost" @click="collectionModal = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">集合名称 *</label>
              <input v-model="collectionName" class="field" placeholder="如：Boho、Spring 2026" @keyup.enter="confirmSaveCollection" />
            </div>
            <div>
              <label class="field-label">封面图片</label>
              <div class="w-32">
                <MediaUploadCard v-model="collectionCover" scope="collection" aspect="aspect-[3/4]" label="点击上传" />
              </div>
              <p class="mt-1.5 text-[11px] text-ink-faint">用于前台导航卡片展示，建议竖图 3:4。不上传则以纯文字集合展示。</p>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="collectionModal = null">取消</button>
            <button class="btn-gold" :disabled="!collectionName.trim() || collectionSaving" @click="confirmSaveCollection">{{ collectionSaving ? '保存中…' : collectionModal.editing ? '保存' : '添加' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===== Modal: 新建分组 ===== -->
    <Teleport to="body">
      <div v-if="showNewGroup" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showNewGroup = false)">
        <div class="panel w-96 p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">新建集合分组</h3>
            <button class="btn-ghost" @click="showNewGroup = false"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">分组名称 *</label>
              <input v-model="newGroupLabel" class="field" placeholder="如：风格 Style、用途 Usage" @keyup.enter="confirmAddGroup" />
            </div>
            <p class="text-[11px] text-ink-faint">集合分组仅用于前台营销聚合与导航，不影响商品属性表单。</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="showNewGroup = false">取消</button>
            <button class="btn-gold" :disabled="!newGroupLabel.trim()" @click="confirmAddGroup">创建分组</button>
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
              <!-- 名称仅根品类可改（子类目改名走 chip 双击行内编辑——原型覆盖抽屉无名称字段） -->
              <div v-if="!drawer.parent">
                <label class="field-label">名称 *</label>
                <input v-model="drawerName" class="field" />
              </div>
              <div v-if="!drawer.parent">
                <label class="field-label">绑定属性集 *</label>
              <SelectMenu
                v-model="drawerSetId"
                :options="attributes.sets.map((s) => ({ value: s.id, label: s.label }))"
                placeholder="请选择…"
              />
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
                      :class="STATE_CLASSES[drawerAttrs[attr.key] || AttrVisibility.HIDDEN]"
                      @click="cycleDrawerState(attr.key)"
                    >{{ STATE_LABELS[drawerAttrs[attr.key] || AttrVisibility.HIDDEN] }}</button>
                  </div>
                </div>
              </template>
            </div>

            <div v-for="l in ['es', 'fr'] as const" v-show="drawerLocale === l" :key="l">
              <div class="mb-1.5 flex items-center justify-between">
                <label class="field-label mb-0">名称（{{ l.toUpperCase() }}）</label>
                <AiTranslateButton
                  v-model="drawerTrans[l]"
                  :source-text="drawerName"
                  :target-lang="l"
                  biz-type="category"
                  :biz-ref="drawer != null ? String(drawer.node.id) : null"
                  field-label="名称"
                  compact
                />
              </div>
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

    <!-- ===== 属性集配置抽屉（原型 Categories.vue 属性集抽屉同款：三态循环 + 绑定品类 chips；label 可编辑为功能保留） ===== -->
    <Teleport to="body">
      <div v-if="setDrawer" class="fixed inset-0 z-50 flex justify-end bg-ink/40" v-dismiss="() => (setDrawer = null)">
        <div class="flex h-full w-[480px] flex-col bg-canvas shadow-2xl">
          <div class="flex items-center justify-between border-b border-line px-6 py-4">
            <div>
              <p class="text-[11px] text-ink-faint">属性集</p>
              <h3 class="text-[15px] font-medium text-ink">{{ setDrawer.set ? setDrawer.set.label + ' · 属性配置' : '新增属性集' }}</h3>
              <div v-if="setDrawerCategories.length" class="mt-1 flex flex-wrap gap-1">
                <span v-for="c in setDrawerCategories" :key="c" class="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">{{ c }}</span>
              </div>
            </div>
            <button class="btn-ghost" @click="setDrawer = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="border-b border-line bg-canvas-warm/60 px-6 py-2.5 text-[12px] text-ink-soft">
            点击状态循环切换：
            <span class="rounded bg-gold/15 px-1.5 py-0.5 text-[10px] text-gold-deep">必填</span>
            <span class="mx-1">→</span>
            <span class="rounded bg-info/10 px-1.5 py-0.5 text-[10px] text-info">可选</span>
            <span class="mx-1">→</span>
            <span class="rounded bg-canvas-warm px-1.5 py-0.5 text-[10px] text-ink-faint">隐藏</span>
            · 拖动左侧手柄调整顺序
          </div>
          <div class="flex-1 overflow-y-auto px-6 py-4">
            <div class="mb-4">
              <label class="field-label">属性集名称 *</label>
              <input v-model="setDrawerLabel" class="field" placeholder="如：婚纱属性集" />
            </div>
            <div class="space-y-2">
              <div
                v-for="(row, i) in setDrawerOrderedAttrs"
                :key="row.key"
                draggable="true"
                class="flex items-center gap-2 rounded-luxe bg-canvas-warm/30 px-3 py-2.5 transition-all"
                :class="[
                  setDrawerDragIndex === i ? 'opacity-30 scale-[0.98]' : '',
                  setDrawerOverIndex === i && setDrawerDragIndex !== i ? 'ring-2 ring-gold/60 bg-gold/8 -translate-y-px shadow-sm' : '',
                ]"
                @dragstart="onSetDrawerDragStart(i)"
                @dragover.prevent="setDrawerOverIndex = i"
                @drop="onSetDrawerDrop(i)"
                @dragend="setDrawerDragIndex = null; setDrawerOverIndex = null"
              >
                <Bars3Icon class="h-4 w-4 shrink-0 cursor-grab text-ink-faint" />
                <span class="flex-1 text-[13px] text-ink">{{ row.def.label }}</span>
                <button
                  class="min-w-[3.5rem] rounded px-2.5 py-1 text-[11px] font-medium transition-colors"
                  :class="STATE_CLASSES[setDrawerAttrs[row.key] || AttrVisibility.HIDDEN]"
                  @click="cycleSetDrawerState(row.key)"
                >{{ STATE_LABELS[setDrawerAttrs[row.key] || AttrVisibility.HIDDEN] }}</button>
              </div>
            </div>
            <p v-if="setDrawerError" class="mt-3 text-[11px] text-danger">{{ setDrawerError }}</p>
          </div>
          <div class="flex justify-end gap-2 border-t border-line px-6 py-4">
            <button class="btn-outline" @click="setDrawer = null">取消</button>
            <button class="btn-gold" :disabled="setDrawerSaving" @click="saveSetDrawer">{{ setDrawerSaving ? '保存中…' : '保存' }}</button>
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
      :open="!!confirmDeleteSet"
      title="删除属性集"
      :message="`确认删除属性集「${confirmDeleteSet?.label}」？被分类引用时将被拒绝（409）。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteSet"
      @cancel="confirmDeleteSet = null"
    />
    <ConfirmDialog
      :open="!!confirmDeleteGroup"
      title="删除集合分组"
      :message="`确认删除分组「${confirmDeleteGroup?.name}」？分组下仍有集合时将被拒绝（需先清空集合）。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteGroup"
      @cancel="confirmDeleteGroup = null"
    />
    <ConfirmDialog
      :open="!!confirmDeleteCollection"
      title="删除集合"
      :message="`确认删除集合「${confirmDeleteCollection?.name}」？商品上的该集合将被摘除。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteCollection"
      @cancel="confirmDeleteCollection = null"
    />
  </div>
</template>
