<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import {
  PlusIcon, Bars3Icon, PencilSquareIcon, TrashIcon, RocketLaunchIcon,
  ChevronRightIcon, TagIcon, XMarkIcon, ExclamationTriangleIcon,
  SwatchIcon, PhotoIcon
} from '@heroicons/vue/24/outline'
import {
  standardTaxonomy, tagDimensions, customTags, tagsByDimension,
  attributeSets, attributeDict, childOverridesOf, resolveAttributeConfig,
  silhouetteOptions, necklineOptions, sleeveOptions, backStyleOptions,
  waistlineOptions, trainOptions, lengthOptions, fabricOptions,
  embellishmentOptions, supportOptions, occasionOptions,
  styleTagOptions, seasonOptions
} from '@/data/mock'

// Main tabs: 标准品类 / 属性集与字典 / 自定义标签
const mainTab = ref('taxonomy')

// ===== Tab 1: Standard Taxonomy =====
const taxonomy = ref(standardTaxonomy.map(r => ({
  ...r,
  children: r.children ? r.children.map(c => ({ ...c, attrOverrides: { ...(c.attrOverrides || {}) } })) : []
})))

function attrSetInfo(attrSetKey) {
  const set = attributeSets[attrSetKey]
  if (!set) return null
  const count = Object.values(set.attrs).filter(v => v !== 'hidden').length
  return { label: set.label, count }
}

// ===== 子品类属性覆盖抽屉 =====
const drawerChild = ref(null)   // { child, parentCat }
const drawerAttrs = ref({})     // 当前编辑中的完整属性配置（含继承）
const STATES = ['visible', 'optional', 'hidden']
const STATE_LABELS = { visible: '必填', optional: '可选', hidden: '隐藏' }
const STATE_CLASSES = {
  visible:  'bg-gold/15 text-gold-deep',
  optional: 'bg-info/10 text-info',
  hidden:   'bg-canvas-warm text-ink-faint'
}
// 只展示有选项或 toggle 类型的属性（与矩阵一致）
const editableAttrs = attributeDict.filter(a => a.optionsKey || a.type === 'toggle')

function openDrawer(child, parentCat) {
  drawerChild.value = { child, parentCat }
  const resolved = resolveAttributeConfig(child.id)
  drawerAttrs.value = { ...resolved.attrs }
}
function cycleDrawerState(key) {
  const cur = drawerAttrs.value[key] || 'hidden'
  drawerAttrs.value[key] = STATES[(STATES.indexOf(cur) + 1) % STATES.length]
}
function isInherited(key) {
  // 判断当前值是否与父级基础属性集一致（即未覆盖）
  const child = drawerChild.value?.child
  if (!child) return true
  return !Object.prototype.hasOwnProperty.call(child.attrOverrides || {}, key)
}
function saveDrawer() {
  const child = drawerChild.value?.child
  if (!child) return
  // 找到父级基础属性集
  const parentCat = drawerChild.value.parentCat
  const baseSet = attributeSets[parentCat.attributeSetId]
  // 只保存与父级不同的 delta
  const delta = {}
  for (const key of Object.keys(drawerAttrs.value)) {
    if (drawerAttrs.value[key] !== baseSet?.attrs?.[key]) {
      delta[key] = drawerAttrs.value[key]
    }
  }
  child.attrOverrides = delta
  drawerChild.value = null
}

// Add child subcategory
const addingChildFor = ref(null)
const newChildName = ref('')
function startAddChild(catId) { addingChildFor.value = catId; newChildName.value = '' }
function confirmAddChild(cat) {
  const v = newChildName.value.trim()
  if (v) cat.children.push({ id: 'sub-' + Date.now(), name: v })
  addingChildFor.value = null; newChildName.value = ''
}

// Edit child inline
const editingChild = ref({ catId: null, idx: null })
const editChildName = ref('')
function startEditChild(catId, idx, name) { editingChild.value = { catId, idx }; editChildName.value = name }
function confirmEditChild(cat) {
  const v = editChildName.value.trim()
  if (v && editingChild.value.idx !== null) cat.children[editingChild.value.idx].name = v
  editingChild.value = { catId: null, idx: null }; editChildName.value = ''
}

// Add root category
const showNewRoot = ref(false)
const newRootName = ref('')
function confirmAddRoot() {
  const v = newRootName.value.trim()
  if (!v) return
  taxonomy.value.push({ id: 'cat-' + Date.now(), name: v, attributeSetId: null, count: 0, children: [] })
  showNewRoot.value = false; newRootName.value = ''
}

// Delete category (blocked if has products)
function canDelete(cat) { return cat.count === 0 }

// ===== Tab 2: Custom Tags =====
const dimensions = ref(tagDimensions.map(d => ({ ...d })))
const tags = ref(customTags.map(t => ({ ...t })))
const activeTagDim = ref(dimensions.value[0]?.id || '')

const tagsByActiveDim = computed(() => tags.value.filter(t => t.dimensionId === activeTagDim.value))

// Add tag
const showNewTag = ref(false)
const newTagName = ref('')
const newTagCover = ref(null)
function handleCoverUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => { newTagCover.value = reader.result }
  reader.readAsDataURL(file)
}
function resetNewTag() {
  showNewTag.value = false; newTagName.value = ''; newTagCover.value = null
}
function confirmAddTag() {
  const v = newTagName.value.trim()
  if (!v) return
  tags.value.push({ id: 'tag-' + Date.now(), dimensionId: activeTagDim.value, name: v, count: 0, enabled: true, cover: newTagCover.value })
  resetNewTag()
}

// Add dimension
const showNewDim = ref(false)
const newDimLabel = ref('')
function confirmAddDim() {
  const v = newDimLabel.value.trim()
  if (!v) return
  const id = 'dim-' + v.toLowerCase().replace(/\s+/g, '-') + '-' + Date.now()
  dimensions.value.push({ id, name: v, desc: '' })
  activeTagDim.value = id
  showNewDim.value = false; newDimLabel.value = ''
}

// Delete dimension
function removeDim(dimId) {
  dimensions.value = dimensions.value.filter(d => d.id !== dimId)
  tags.value = tags.value.filter(t => t.dimensionId !== dimId)
  if (activeTagDim.value === dimId) activeTagDim.value = dimensions.value[0]?.id || ''
}

// Delete tag
function removeTag(tagId) { tags.value = tags.value.filter(t => t.id !== tagId) }

// ===== Tab 3: 属性集与字典 =====
const optionsMap = {
  silhouetteOptions, necklineOptions, sleeveOptions, backStyleOptions,
  waistlineOptions, trainOptions, lengthOptions,
  fabricOptions: fabricOptions.map(f => f.name),
  embellishmentOptions, supportOptions, occasionOptions, styleTagOptions, seasonOptions
}

const dict = ref(attributeDict.map(a => ({
  ...a,
  options: a.optionsKey ? [...(optionsMap[a.optionsKey] || [])] : []
})))

const expandedAttr = ref(null)
const editingOption = ref({ attrKey: null, idx: null, val: '' })
const newOption = ref({ attrKey: null, val: '' })

function startAddOption(key) { newOption.value = { attrKey: key, val: '' } }
function confirmAddOption(attr) {
  const v = newOption.value.val.trim()
  if (v && newOption.value.attrKey === attr.key) { attr.options.push(v); newOption.value = { attrKey: null, val: '' } }
}
function removeOption(attr, i) { attr.options.splice(i, 1) }
function startEditOpt(key, i, val) { editingOption.value = { attrKey: key, idx: i, val } }
function confirmEditOpt(attr) {
  const v = editingOption.value.val.trim()
  if (v) attr.options[editingOption.value.idx] = v
  editingOption.value = { attrKey: null, idx: null, val: '' }
}

// 属性集抽屉（与子品类抽屉同款 UI）
const drawerSet = ref(null)   // attributeSet 对象
const drawerSetAttrs = ref({})
const attrSetsEditable = ref(
  Object.fromEntries(Object.values(attributeSets).map(set => [set.id, { ...set.attrs }]))
)

function openSetDrawer(set) {
  drawerSet.value = set
  drawerSetAttrs.value = { ...attrSetsEditable.value[set.id] }
}
// 主品类配置属性集：解析该品类绑定的属性集后打开抽屉（与子品类同款交互）
function openCatSetDrawer(cat) {
  const set = attributeSets[cat.attributeSetId]
  if (!set) return
  openSetDrawer({
    ...set,
    categories: standardTaxonomy.filter(c => c.attributeSetId === set.id).map(c => c.name)
  })
}
function cycleSetDrawerState(key) {
  const cur = drawerSetAttrs.value[key] || 'hidden'
  drawerSetAttrs.value[key] = STATES[(STATES.indexOf(cur) + 1) % STATES.length]
}
function saveSetDrawer() {
  attrSetsEditable.value[drawerSet.value.id] = { ...drawerSetAttrs.value }
  drawerSet.value = null
}

// Edit tag name
const editingTag = ref(null)
const editTagName = ref('')
const editTagCover = ref(null)
function startEditTag(tag) { editingTag.value = tag; editTagName.value = tag.name; editTagCover.value = tag.cover }
function handleEditCoverUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => { editTagCover.value = reader.result }
  reader.readAsDataURL(file)
}
function confirmEditTag() {
  if (editingTag.value && editTagName.value.trim()) {
    editingTag.value.name = editTagName.value.trim()
    editingTag.value.cover = editTagCover.value
  }
  editingTag.value = null; editTagName.value = ''; editTagCover.value = null
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="品类与标签" subtitle="管理商品品类树和自定义营销标签">
      <template #actions><button class="btn-gold"><RocketLaunchIcon class="h-4 w-4" />保存并发布</button></template>
    </PageHeader>

    <!-- Main Tabs -->
    <div class="mb-4 flex items-center gap-1 border-b border-line">
      <button v-for="[key, label] in [['taxonomy','标准品类'],['attributes','属性集与字典'],['tags','自定义标签']]" :key="key"
        @click="mainTab = key"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="mainTab === key ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">
        {{ label }}
      </button>
    </div>

    <!-- ==================== Tab 1: Standard Taxonomy ==================== -->
    <div v-show="mainTab === 'taxonomy'">
      <div class="mb-4 flex items-center justify-between gap-2">
        <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
          <SwatchIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
          <span>标准品类决定商品表单中出现哪些属性字段。每个商品只能属于一个品类。</span>
        </div>
        <button class="btn-outline shrink-0" @click="showNewRoot = true"><PlusIcon class="h-4 w-4" />添加根品类</button>
      </div>

      <!-- 品类卡片网格：超出部分自动换行，不横向滚动 -->
      <div class="flex flex-wrap gap-4">
        <div v-for="cat in taxonomy" :key="cat.id" class="panel flex w-72 shrink-0 flex-col p-4">
            <!-- 品类头 -->
            <div class="flex items-center gap-2">
              <Bars3Icon class="h-4 w-4 cursor-grab text-ink-faint" />
              <span class="font-display text-base font-medium text-ink">{{ cat.name }}</span>
              <button class="btn-danger-ghost ml-auto" :disabled="!canDelete(cat)" :title="canDelete(cat) ? '删除' : '有商品关联，无法删除'">
                <TrashIcon class="h-4 w-4" />
              </button>
            </div>
            <!-- 属性集配置（点击展开抽屉，与子品类同款交互） + 商品数 -->
            <div class="mt-2 flex flex-wrap items-center gap-1.5">
              <button v-if="cat.attributeSetId" @click="openCatSetDrawer(cat)"
                class="inline-flex items-center gap-1 rounded-full bg-gold/14 px-2 py-0.5 text-[11px] text-gold-deep transition-colors hover:bg-gold/25">
                <PencilSquareIcon class="h-3 w-3" />
                {{ attrSetInfo(cat.attributeSetId).label }}属性集 · {{ attrSetInfo(cat.attributeSetId).count }} 项
              </button>
              <span v-else class="flex items-center gap-1 rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">
                <ExclamationTriangleIcon class="h-3 w-3" />未配置属性集
              </span>
              <span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">{{ cat.count }} 件商品</span>
            </div>
            <!-- 子类目 chips（纵向堆叠，卡片内可滚动） -->
            <div class="mt-3 flex flex-1 flex-col gap-2 border-t border-line pt-3">
              <p class="text-[11px] font-medium uppercase tracking-wider text-ink-faint">子类目 ({{ cat.children.length }})</p>
              <div class="flex flex-wrap gap-2">
                <span v-for="(ch, ci) in cat.children" :key="ch.id" class="flex items-center gap-1 rounded-luxe border border-line px-2.5 py-1 text-[12px] text-ink-soft">
                  <template v-if="editingChild.catId === cat.id && editingChild.idx === ci">
                    <input v-model="editChildName" class="w-20 bg-transparent text-[12px] outline-none border-b border-gold" @keyup.enter="confirmEditChild(cat)" @keyup.escape="editingChild = { catId: null, idx: null }" autofocus />
                  </template>
                  <template v-else>
                    <ChevronRightIcon class="h-3 w-3 text-ink-faint" />
                    <span class="cursor-pointer" @dblclick="startEditChild(cat.id, ci, ch.name)">{{ ch.name }}</span>
                    <button @click="openDrawer(ch, cat)"
                      class="rounded px-1 py-0.5 text-[10px] leading-none transition-colors hover:bg-info/20"
                      :class="Object.keys(ch.attrOverrides || {}).length ? 'bg-info/12 text-info' : 'bg-canvas-warm text-ink-faint'">
                      {{ Object.keys(ch.attrOverrides || {}).length ? Object.keys(ch.attrOverrides).length + '覆盖' : '继承' }}
                    </button>
                  </template>
                </span>
                <span v-if="addingChildFor === cat.id" class="flex items-center gap-1 rounded-luxe border border-gold px-2.5 py-1">
                  <input v-model="newChildName" class="w-24 bg-transparent text-[12px] outline-none" placeholder="子类目名" @keyup.enter="confirmAddChild(cat)" @keyup.escape="addingChildFor = null" autofocus />
                </span>
              </div>
              <button class="btn-ghost mt-auto self-start text-[12px]" @click="startAddChild(cat.id)"><PlusIcon class="h-3 w-3" />添加子类目</button>
            </div>
        </div>
      </div>
    </div>

    <!-- ==================== Tab 2: 属性字典 ==================== -->
    <div v-show="mainTab === 'attributes'">
      <div class="mb-4 flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
        <SwatchIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
        <span>属性字典维护全局可选值。各品类启用哪些属性、必填或可选，在「标准品类」Tab 的品类卡片上点击属性集徽章配置。</span>
      </div>

      <!-- 属性字典 -->
      <div class="space-y-2">
        <div v-for="attr in dict" :key="attr.key" class="panel overflow-hidden">
          <div class="flex cursor-pointer items-center gap-3 p-4" @click="expandedAttr = expandedAttr === attr.key ? null : attr.key">
            <div class="flex-1">
              <span class="text-[13px] font-medium text-ink">{{ attr.label }}</span>
              <span class="ml-2 rounded px-1.5 py-0.5 text-[10px]"
                :class="attr.type === 'select' ? 'bg-gold/10 text-gold-deep' : attr.type === 'multiselect' ? 'bg-info/10 text-info' : 'bg-canvas-warm text-ink-faint'">
                {{ attr.type }}
              </span>
            </div>
            <span class="text-[12px] text-ink-faint">{{ attr.options.length ? attr.options.length + ' 个选项' : '无选项（' + attr.type + '）' }}</span>
            <svg class="h-4 w-4 text-ink-faint transition-transform" :class="expandedAttr === attr.key ? 'rotate-180' : ''" viewBox="0 0 20 20" fill="currentColor"><path d="M5 7l5 5 5-5"/></svg>
          </div>
          <div v-if="expandedAttr === attr.key && attr.options.length" class="border-t border-line px-4 pb-4 pt-3">
            <div class="flex flex-wrap gap-2">
              <span v-for="(opt, i) in attr.options" :key="i"
                class="group flex items-center gap-1 rounded-full border border-line px-2.5 py-1 text-[12px] text-ink-soft">
                <span v-if="editingOption.attrKey === attr.key && editingOption.idx === i">
                  <input v-model="editingOption.val" class="w-24 border-b border-gold bg-transparent text-[12px] outline-none" @keyup.enter="confirmEditOpt(attr)" @keyup.escape="editingOption = { attrKey: null }" />
                </span>
                <span v-else @dblclick="startEditOpt(attr.key, i, opt)">{{ opt }}</span>
                <button @click.stop="removeOption(attr, i)" class="invisible text-ink-faint hover:text-danger group-hover:visible"><XMarkIcon class="h-3 w-3" /></button>
              </span>
              <span v-if="newOption.attrKey === attr.key" class="flex items-center gap-1 rounded-full border border-gold px-2.5 py-1">
                <input v-model="newOption.val" class="w-24 bg-transparent text-[12px] outline-none" placeholder="新选项" @keyup.enter="confirmAddOption(attr)" @keyup.escape="newOption = { attrKey: null }" autofocus />
              </span>
              <button @click.stop="startAddOption(attr.key)" class="btn-ghost text-[12px]"><PlusIcon class="h-3 w-3" />添加</button>
            </div>
            <p class="mt-2 text-[11px] text-ink-faint">双击选项可编辑；回车确认，Esc 取消。</p>
          </div>
          <div v-else-if="expandedAttr === attr.key" class="border-t border-line px-4 py-3 text-[12px] italic text-ink-faint">
            该属性类型（{{ attr.type }}）无预设选项。
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== Tab 3: Custom Tags ==================== -->
    <div v-show="mainTab === 'tags'">
      <div class="mb-4 flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
        <TagIcon class="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" />
        <span>自定义标签仅用于前台导航和营销聚合，不影响商品属性表单。商品可多选标签。</span>
      </div>

      <!-- Dimension sub-tabs -->
      <div class="mb-4 flex items-center gap-1 border-b border-line">
        <button v-for="d in dimensions" :key="d.id" @click="activeTagDim = d.id"
          class="group flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-[13px] transition-colors"
          :class="activeTagDim === d.id ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">
          {{ d.name }}
          <button @click.stop="removeDim(d.id)" class="invisible ml-0.5 rounded p-0.5 text-ink-faint hover:text-danger group-hover:visible">
            <XMarkIcon class="h-3 w-3" />
          </button>
        </button>
        <button @click="showNewDim = true" class="ml-1 flex items-center gap-1 rounded-luxe px-3 py-2 text-[12px] text-ink-faint hover:bg-canvas-warm hover:text-ink">
          <PlusIcon class="h-3.5 w-3.5" />新建维度
        </button>
      </div>

      <!-- Unified card grid -->
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        <div v-for="t in tagsByActiveDim" :key="t.id" class="panel overflow-hidden">
          <div class="relative aspect-[3/4]">
            <img v-if="t.cover" :src="t.cover" class="h-full w-full object-cover" />
            <div v-else class="flex h-full w-full items-center justify-center bg-canvas-warm text-ink-faint"><TagIcon class="h-8 w-8" /></div>
            <div class="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent"></div>
            <p class="absolute bottom-2 left-3 font-display text-lg text-white">{{ t.name }}</p>
          </div>
          <div class="flex items-center justify-between p-3">
            <span class="text-[12px] text-ink-faint">{{ t.count }} 件</span>
            <div class="flex items-center gap-2">
              <Toggle v-model="t.enabled" />
              <button class="btn-ghost" @click="startEditTag(t)"><PencilSquareIcon class="h-3.5 w-3.5" /></button>
              <button class="btn-danger-ghost" @click="removeTag(t.id)"><TrashIcon class="h-3.5 w-3.5" /></button>
            </div>
          </div>
        </div>
        <button class="panel flex aspect-[3/4] flex-col items-center justify-center gap-2 border-2 border-dashed text-ink-faint hover:border-gold" @click="showNewTag = true">
          <PlusIcon class="h-6 w-6" /><span class="text-[12px]">新增标签</span>
        </button>
      </div>
    </div>

    <!-- ===== Modal: Add Root Category ===== -->
    <div v-if="showNewRoot" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showNewRoot = false">
      <div class="panel w-96 p-6">
        <div class="mb-5 flex items-center justify-between">
          <h3 class="text-[15px] font-medium text-ink">添加根品类</h3>
          <button @click="showNewRoot = false" class="btn-ghost"><XMarkIcon class="h-4 w-4" /></button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="field-label">品类名称 *</label>
            <input v-model="newRootName" class="field" placeholder="如：Flower Girl" @keyup.enter="confirmAddRoot" autofocus />
          </div>
          <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-3 py-2 text-[11px] text-ink-soft">
            <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0 text-gold-deep" />
            <span>新增根品类后需要在「属性集」页面配置对应属性集，否则商品编辑表单将缺少属性字段。</span>
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="btn-outline" @click="showNewRoot = false">取消</button>
          <button class="btn-gold" @click="confirmAddRoot" :disabled="!newRootName.trim()">创建</button>
        </div>
      </div>
    </div>

    <!-- ===== Modal: Add Tag ===== -->
    <div v-if="showNewTag" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="resetNewTag">
      <div class="panel w-96 p-6">
        <div class="mb-5 flex items-center justify-between">
          <h3 class="text-[15px] font-medium text-ink">新增标签</h3>
          <button @click="resetNewTag" class="btn-ghost"><XMarkIcon class="h-4 w-4" /></button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="field-label">标签名称 *</label>
            <input v-model="newTagName" class="field" placeholder="如：Boho、Spring 2026" @keyup.enter="confirmAddTag" autofocus />
          </div>
          <div>
            <label class="field-label">封面图片</label>
            <label class="group relative flex aspect-[3/4] w-32 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-luxe border-2 border-dashed border-line text-ink-faint transition-colors hover:border-gold">
              <img v-if="newTagCover" :src="newTagCover" class="absolute inset-0 h-full w-full object-cover" />
              <template v-else>
                <PhotoIcon class="h-7 w-7" />
                <span class="mt-1 text-[11px]">点击上传</span>
              </template>
              <span v-if="newTagCover" class="absolute inset-0 flex items-center justify-center bg-ink/40 text-[11px] text-white opacity-0 transition-opacity group-hover:opacity-100">更换图片</span>
              <input type="file" accept="image/*" class="hidden" @change="handleCoverUpload" />
            </label>
            <p class="mt-1.5 text-[11px] text-ink-faint">用于前台导航卡片展示，建议竖图 3:4。不上传则以纯文字标签展示。</p>
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="btn-outline" @click="resetNewTag">取消</button>
          <button class="btn-gold" @click="confirmAddTag" :disabled="!newTagName.trim()">添加</button>
        </div>
      </div>
    </div>

    <!-- ===== Modal: Add Dimension ===== -->
    <div v-if="showNewDim" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showNewDim = false">
      <div class="panel w-96 p-6">
        <div class="mb-5 flex items-center justify-between">
          <h3 class="text-[15px] font-medium text-ink">新建标签维度</h3>
          <button @click="showNewDim = false" class="btn-ghost"><XMarkIcon class="h-4 w-4" /></button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="field-label">维度名称 *</label>
            <input v-model="newDimLabel" class="field" placeholder="如：风格 Style、用途 Usage" @keyup.enter="confirmAddDim" autofocus />
          </div>
          <p class="text-[11px] text-ink-faint">标签维度仅用于前台营销聚合与导航，不影响商品属性表单。</p>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="btn-outline" @click="showNewDim = false">取消</button>
          <button class="btn-gold" @click="confirmAddDim" :disabled="!newDimLabel.trim()">创建维度</button>
        </div>
      </div>
    </div>

    <!-- ===== Modal: Edit Tag Name ===== -->
    <div v-if="editingTag" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="editingTag = null">
      <div class="panel w-96 p-6">
        <div class="mb-5 flex items-center justify-between">
          <h3 class="text-[15px] font-medium text-ink">编辑标签</h3>
          <button @click="editingTag = null" class="btn-ghost"><XMarkIcon class="h-4 w-4" /></button>
        </div>
        <div class="space-y-4">
          <div>
            <label class="field-label">名称</label>
            <input v-model="editTagName" class="field" @keyup.enter="confirmEditTag" autofocus />
          </div>
          <div>
            <label class="field-label">封面图片</label>
            <div class="flex items-end gap-3">
              <label class="group relative flex aspect-[3/4] w-32 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-luxe border-2 border-dashed border-line text-ink-faint transition-colors hover:border-gold">
                <img v-if="editTagCover" :src="editTagCover" class="absolute inset-0 h-full w-full object-cover" />
                <template v-else>
                  <PhotoIcon class="h-7 w-7" />
                  <span class="mt-1 text-[11px]">点击上传</span>
                </template>
                <span v-if="editTagCover" class="absolute inset-0 flex items-center justify-center bg-ink/40 text-[11px] text-white opacity-0 transition-opacity group-hover:opacity-100">更换图片</span>
                <input type="file" accept="image/*" class="hidden" @change="handleEditCoverUpload" />
              </label>
              <button v-if="editTagCover" class="btn-danger-ghost text-[12px]" @click="editTagCover = null"><TrashIcon class="h-3.5 w-3.5" />移除封面</button>
            </div>
            <p class="mt-1.5 text-[11px] text-ink-faint">移除封面后该标签将以纯文字形式展示。</p>
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="btn-outline" @click="editingTag = null">取消</button>
          <button class="btn-gold" @click="confirmEditTag" :disabled="!editTagName.trim()">保存</button>
        </div>
      </div>
    </div>
  </div>

  <!-- ===== 子品类属性覆盖抽屉 ===== -->
  <div v-if="drawerChild" class="fixed inset-0 z-50 flex justify-end bg-ink/40" @click.self="drawerChild = null">
    <div class="flex h-full w-[480px] flex-col bg-canvas shadow-2xl">
      <!-- 抽屉头 -->
      <div class="flex items-center justify-between border-b border-line px-6 py-4">
        <div>
          <p class="text-[11px] text-ink-faint">{{ drawerChild.parentCat.name }}</p>
          <h3 class="text-[15px] font-medium text-ink">{{ drawerChild.child.name }} · 属性覆盖配置</h3>
        </div>
        <button @click="drawerChild = null" class="btn-ghost"><XMarkIcon class="h-4 w-4" /></button>
      </div>

      <!-- 说明条 -->
      <div class="border-b border-line bg-canvas-warm/60 px-6 py-2.5 text-[12px] text-ink-soft">
        点击状态循环切换：
        <span class="rounded px-1.5 py-0.5 text-[10px] bg-gold/15 text-gold-deep">必填</span>
        <span class="mx-1">→</span>
        <span class="rounded px-1.5 py-0.5 text-[10px] bg-info/10 text-info">可选</span>
        <span class="mx-1">→</span>
        <span class="rounded px-1.5 py-0.5 text-[10px] bg-canvas-warm text-ink-faint">隐藏</span>
        · 灰色底 = 继承父级，有色底 = 已覆盖
      </div>

      <!-- 属性列表 -->
      <div class="flex-1 overflow-y-auto px-6 py-4 space-y-2">
        <div v-for="attr in editableAttrs" :key="attr.key"
          class="flex items-center justify-between rounded-luxe px-3 py-2.5 transition-colors"
          :class="isInherited(attr.key) ? 'bg-canvas-warm/30' : 'bg-info/5 ring-1 ring-info/20'">
          <div class="flex items-center gap-2">
            <span class="text-[13px] text-ink">{{ attr.label }}</span>
            <span v-if="!isInherited(attr.key)" class="rounded-full bg-info/12 px-1.5 py-0.5 text-[10px] text-info leading-none">覆盖</span>
            <span v-else class="text-[11px] text-ink-faint">继承父级</span>
          </div>
          <button @click="cycleDrawerState(attr.key)"
            class="min-w-[3.5rem] rounded px-2.5 py-1 text-[11px] font-medium transition-colors"
            :class="STATE_CLASSES[drawerAttrs[attr.key] || 'hidden']">
            {{ STATE_LABELS[drawerAttrs[attr.key] || 'hidden'] }}
          </button>
        </div>
      </div>

      <!-- 底部操作 -->
      <div class="flex items-center justify-between border-t border-line px-6 py-4">
        <span class="text-[12px] text-ink-faint">
          {{ Object.keys(drawerChild.child.attrOverrides || {}).length }} 项覆盖（保存后生效）
        </span>
        <div class="flex gap-2">
          <button class="btn-outline" @click="drawerChild = null">取消</button>
          <button class="btn-gold" @click="saveDrawer">保存</button>
        </div>
      </div>
    </div>
  </div>

  <!-- ===== 属性集配置抽屉 ===== -->
  <div v-if="drawerSet" class="fixed inset-0 z-50 flex justify-end bg-ink/40" @click.self="drawerSet = null">
    <div class="flex h-full w-[480px] flex-col bg-canvas shadow-2xl">
      <div class="flex items-center justify-between border-b border-line px-6 py-4">
        <div>
          <p class="text-[11px] text-ink-faint">属性集</p>
          <h3 class="text-[15px] font-medium text-ink">{{ drawerSet.label }} · 属性配置</h3>
          <div class="mt-1 flex flex-wrap gap-1">
            <span v-for="c in drawerSet.categories" :key="c" class="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">{{ c }}</span>
          </div>
        </div>
        <button @click="drawerSet = null" class="btn-ghost"><XMarkIcon class="h-4 w-4" /></button>
      </div>
      <div class="border-b border-line bg-canvas-warm/60 px-6 py-2.5 text-[12px] text-ink-soft">
        点击状态循环切换：
        <span class="rounded px-1.5 py-0.5 text-[10px] bg-gold/15 text-gold-deep">必填</span>
        <span class="mx-1">→</span>
        <span class="rounded px-1.5 py-0.5 text-[10px] bg-info/10 text-info">可选</span>
        <span class="mx-1">→</span>
        <span class="rounded px-1.5 py-0.5 text-[10px] bg-canvas-warm text-ink-faint">隐藏</span>
      </div>
      <div class="flex-1 overflow-y-auto px-6 py-4 space-y-2">
        <div v-for="attr in editableAttrs" :key="attr.key"
          class="flex items-center justify-between rounded-luxe px-3 py-2.5 bg-canvas-warm/30 transition-colors">
          <span class="text-[13px] text-ink">{{ attr.label }}</span>
          <button @click="cycleSetDrawerState(attr.key)"
            class="min-w-[3.5rem] rounded px-2.5 py-1 text-[11px] font-medium transition-colors"
            :class="STATE_CLASSES[drawerSetAttrs[attr.key] || 'hidden']">
            {{ STATE_LABELS[drawerSetAttrs[attr.key] || 'hidden'] }}
          </button>
        </div>
      </div>
      <div class="flex justify-end gap-2 border-t border-line px-6 py-4">
        <button class="btn-outline" @click="drawerSet = null">取消</button>
        <button class="btn-gold" @click="saveSetDrawer">保存</button>
      </div>
    </div>
  </div>
</template>
