<script setup lang="ts">
// PAGE-CAT-A04 / COMP-CAT-A04：属性集（真实工程缺页，按原型 209 行版 copy-adapt 新建）
// 左/矩阵=属性集×属性字典可见性矩阵（cycleState 三态 + hasUnsavedChanges + 整单保存 E-CAT-21）；
// 属性字典管理（选项增删改 confirmAddOption/removeOption/confirmEdit + type 徽章 + 三语 label/options tab）
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DrawerShell from '@/components/DrawerShell.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import { useAttributeStore } from '@/stores/attributes'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import type { AttrVisibility, AttributeDef, AttributeDefTranslation, AttributeDefType, AttributeSet } from '@/api/types'

const store = useAttributeStore()
const toast = useToastStore()

const tab = ref<'dict' | 'matrix'>('dict')

const STATES: AttrVisibility[] = ['visible', 'optional', 'hidden']
const STATE_LABELS: Record<AttrVisibility, string> = { visible: '必', optional: '选', hidden: '—' }
const STATE_CLASSES: Record<AttrVisibility, string> = {
  visible: 'bg-gold/15 text-gold-deep',
  optional: 'bg-info/10 text-info',
  hidden: 'bg-canvas-warm text-ink-faint',
}

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

function load() {
  store.fetchAll().then(initMatrix).catch((e) => toast.error(bizMsg(e, '加载属性配置失败')))
}

/* ===================== 矩阵（可编辑副本 + hasUnsavedChanges） ===================== */

// setId → (attributeId → visibility)
const matrix = ref<Record<number, Record<number, AttrVisibility>>>({})

function initMatrix() {
  const m: Record<number, Record<number, AttrVisibility>> = {}
  for (const set of store.sets) {
    const row: Record<number, AttrVisibility> = {}
    for (const item of set.items) row[item.attributeId] = item.visibility
    m[set.id] = row
  }
  matrix.value = m
}

function cellState(setId: number, defId: number): AttrVisibility {
  return matrix.value[setId]?.[defId] || 'hidden'
}

function cycleState(setId: number, defId: number) {
  const cur = cellState(setId, defId)
  if (!matrix.value[setId]) matrix.value[setId] = {}
  matrix.value[setId][defId] = STATES[(STATES.indexOf(cur) + 1) % STATES.length]
}

/** FORM-CAT-A04：未保存变更检测（离开提示 + 保存按钮态） */
const hasUnsavedChanges = computed(() => {
  for (const set of store.sets) {
    const local = matrix.value[set.id] || {}
    for (const def of store.defs) {
      const saved = set.items.find((i) => i.attributeId === def.id)?.visibility || 'hidden'
      if ((local[def.id] || 'hidden') !== saved) return true
    }
  }
  return false
})

const savingMatrix = ref(false)
async function saveMatrix() {
  savingMatrix.value = true
  try {
    for (const set of store.sets) {
      const local = matrix.value[set.id] || {}
      const dirty = store.defs.some((def) => {
        const saved = set.items.find((i) => i.attributeId === def.id)?.visibility || 'hidden'
        return (local[def.id] || 'hidden') !== saved
      })
      if (!dirty) continue
      // 整单覆盖提交（items 含非 hidden 项）
      const items = store.defs
        .filter((def) => (local[def.id] || 'hidden') !== 'hidden')
        .map((def) => ({ attributeId: def.id, visibility: local[def.id] }))
      await store.saveSet({ label: set.label, items }, set.id)
    }
    initMatrix()
    toast.success('属性集配置已保存')
  } catch (e) {
    toast.error(bizMsg(e, '保存失败'))
  } finally {
    savingMatrix.value = false
  }
}

/* ===================== 属性集 CRUD（新增/改名/删除——409503） ===================== */

const setModal = ref<{ editing: AttributeSet | null } | null>(null)
const setLabelDraft = ref('')
const setModalError = ref('')
const savingSet = ref(false)

function openSetModal(set?: AttributeSet) {
  setModal.value = { editing: set ?? null }
  setLabelDraft.value = set?.label || ''
  setModalError.value = ''
}

async function saveSetLabel() {
  const label = setLabelDraft.value.trim()
  if (!label) {
    setModalError.value = '名称必填'
    return
  }
  savingSet.value = true
  try {
    const editing = setModal.value?.editing
    if (editing) {
      const items = (matrix.value[editing.id] && store.defs
        .filter((def) => (matrix.value[editing.id][def.id] || 'hidden') !== 'hidden')
        .map((def) => ({ attributeId: def.id, visibility: matrix.value[editing.id][def.id] }))) || editing.items
      await store.saveSet({ label, items }, editing.id)
    } else {
      await store.saveSet({ label, items: [] })
      initMatrix()
    }
    toast.success('已保存')
    setModal.value = null
  } catch (e) {
    setModalError.value = bizMsg(e, '保存失败')
  } finally {
    savingSet.value = false
  }
}

const confirmDeleteSet = ref<AttributeSet | null>(null)
const confirmBusy = ref(false)
async function doDeleteSet() {
  if (!confirmDeleteSet.value) return
  confirmBusy.value = true
  try {
    await store.removeSet(confirmDeleteSet.value.id)
    initMatrix()
    toast.success('已删除')
    confirmDeleteSet.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409503) toast.error('该属性集被分类引用，不可删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

/* ===================== 属性字典（展开选项编辑 + 定义抽屉 + 删除 409507） ===================== */

const expanded = ref<number | null>(null)
const editingOption = ref<{ defId: number | null; idx: number | null; val: string }>({ defId: null, idx: null, val: '' })
const newOption = ref<{ defId: number | null; val: string }>({ defId: null, val: '' })

function optionsOf(def: AttributeDef): string[] {
  return def.options || []
}

function startAddOption(defId: number) {
  newOption.value = { defId, val: '' }
}

async function pushDefOptions(def: AttributeDef, options: string[]) {
  try {
    await store.saveDef(
      { key: def.key, label: def.label, type: def.type, options, translations: def.translations || [] },
      def.id,
    )
    toast.success('选项已保存')
  } catch (e) {
    toast.error(bizMsg(e, '保存失败'))
  }
}

function confirmAddOption(def: AttributeDef) {
  const v = newOption.value.val.trim()
  if (v && newOption.value.defId === def.id) {
    pushDefOptions(def, [...optionsOf(def), v])
    newOption.value = { defId: null, val: '' }
  }
}
function removeOption(def: AttributeDef, i: number) {
  const next = [...optionsOf(def)]
  next.splice(i, 1)
  pushDefOptions(def, next)
}
function startEdit(defId: number, i: number, val: string) {
  editingOption.value = { defId, idx: i, val }
}
function confirmEdit(def: AttributeDef) {
  const v = editingOption.value.val.trim()
  if (v && editingOption.value.idx != null) {
    const next = [...optionsOf(def)]
    next[editingOption.value.idx] = v
    pushDefOptions(def, next)
  }
  editingOption.value = { defId: null, idx: null, val: '' }
}

// 定义编辑抽屉（key/label/type + 三语 label/options tab）
const defDrawer = ref(false)
const editingDef = ref<AttributeDef | null>(null)
const defLocale = ref<'en' | 'es' | 'fr'>('en')
const defForm = ref({ key: '', label: '', type: 'select' as AttributeDefType, optionsText: '' })
const defTrans = ref<Record<'es' | 'fr', { label: string; optionsText: string }>>({
  es: { label: '', optionsText: '' },
  fr: { label: '', optionsText: '' },
})
const defErrors = ref<FieldErrors>({})
const savingDef = ref(false)

const defFilled = computed(() => ({
  en: !!defForm.value.label,
  es: !!defTrans.value.es.label,
  fr: !!defTrans.value.fr.label,
}))

function openDefDrawer(def?: AttributeDef) {
  editingDef.value = def ?? null
  defLocale.value = 'en'
  defErrors.value = {}
  defForm.value = def
    ? { key: def.key, label: def.label, type: def.type, optionsText: (def.options || []).join('\n') }
    : { key: '', label: '', type: 'select', optionsText: '' }
  const byLocale = (l: 'es' | 'fr') => def?.translations?.find((t) => t.locale === l)
  defTrans.value = {
    es: { label: byLocale('es')?.label || '', optionsText: (byLocale('es')?.options || []).join('\n') },
    fr: { label: byLocale('fr')?.label || '', optionsText: (byLocale('fr')?.options || []).join('\n') },
  }
  defDrawer.value = true
}

function parseOptions(text: string): string[] {
  return text
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}

async function saveDef() {
  defErrors.value = {}
  if (!defForm.value.key.trim()) defErrors.value.key = 'key 必填'
  if (!defForm.value.label.trim()) defErrors.value.label = '名称必填'
  if (Object.keys(defErrors.value).length) {
    defLocale.value = 'en'
    return
  }
  const options = parseOptions(defForm.value.optionsText)
  const translations: AttributeDefTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = defTrans.value[l]
    if (t.label.trim() || t.optionsText.trim()) {
      // V-CAT-058：翻译 options 与主表等长（不足时 inline 提示）
      const localized = parseOptions(t.optionsText)
      if (localized.length && localized.length !== options.length) {
        defErrors.value[l === 'es' ? 'esOptions' : 'frOptions'] = `选项翻译需与主表等长（${options.length} 项）`
        defLocale.value = l
        return
      }
      translations.push({ locale: l, label: t.label.trim() || null, options: localized.length ? localized : null })
    }
  }
  savingDef.value = true
  try {
    await store.saveDef(
      {
        key: defForm.value.key.trim(),
        label: defForm.value.label.trim(),
        type: defForm.value.type,
        options,
        translations,
      },
      editingDef.value?.id,
    )
    toast.success('属性定义已保存')
    defDrawer.value = false
  } catch (e) {
    if (e instanceof BizError && e.code === 422501) {
      defErrors.value = extractFieldErrors(e)
      if (!Object.keys(defErrors.value).length) toast.error(e.message)
      defLocale.value = 'en'
    } else {
      toast.error(bizMsg(e, '保存失败'))
    }
  } finally {
    savingDef.value = false
  }
}

const confirmDeleteDef = ref<AttributeDef | null>(null)
async function doDeleteDef() {
  if (!confirmDeleteDef.value) return
  confirmBusy.value = true
  try {
    await store.removeDef(confirmDeleteDef.value.id)
    initMatrix()
    toast.success('已删除')
    confirmDeleteDef.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409507) toast.error('该属性被属性集引用，不可删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Catalog" title="属性集定义" subtitle="管理商品属性字典与品类×属性可见性矩阵">
      <template #actions>
        <button class="btn-gold" :disabled="!hasUnsavedChanges || savingMatrix" @click="saveMatrix">
          <RocketLaunchIcon class="h-4 w-4" />{{ savingMatrix ? '保存中…' : hasUnsavedChanges ? '保存配置 *' : '保存配置' }}
        </button>
      </template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in [['dict', '属性字典'], ['matrix', '属性集×属性矩阵']] as const"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="tab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- ① 属性字典 -->
    <div v-show="tab === 'dict'">
      <div class="mb-3 flex justify-end">
        <button class="btn-outline" @click="openDefDrawer()"><PlusIcon class="h-4 w-4" />新增属性定义</button>
      </div>
      <div v-if="store.loading" class="space-y-2">
        <div v-for="i in 5" :key="i" class="panel h-14 animate-pulse bg-canvas-warm/40"></div>
      </div>
      <EmptyState v-else-if="!store.defs.length" title="暂无属性定义" hint="点击「新增属性定义」创建。" />
      <div v-else class="space-y-2">
        <div v-for="attr in store.defs" :key="attr.id" class="panel overflow-hidden">
          <div class="flex cursor-pointer items-center gap-3 p-4" @click="expanded = expanded === attr.id ? null : attr.id">
            <div class="flex-1">
              <span class="text-[13px] font-medium text-ink">{{ attr.label }}</span>
              <span
                class="ml-2 rounded px-1.5 py-0.5 text-[10px]"
                :class="attr.type === 'select' ? 'bg-gold/10 text-gold-deep' : attr.type === 'multiselect' ? 'bg-info/10 text-info' : 'bg-canvas-warm text-ink-faint'"
              >{{ attr.type }}</span>
              <span class="ml-2 font-mono text-[10px] text-ink-faint">{{ attr.key }}</span>
            </div>
            <span v-if="optionsOf(attr).length" class="text-[12px] text-ink-faint">{{ optionsOf(attr).length }} 个选项</span>
            <span v-else class="text-[12px] italic text-ink-faint">无选项（{{ attr.type }}）</span>
            <button class="btn-ghost" @click.stop="openDefDrawer(attr)"><PencilSquareIcon class="h-4 w-4" /></button>
            <button class="btn-danger-ghost" @click.stop="confirmDeleteDef = attr"><TrashIcon class="h-4 w-4" /></button>
            <svg class="h-4 w-4 text-ink-faint transition-transform" :class="expanded === attr.id ? 'rotate-180' : ''" viewBox="0 0 20 20" fill="currentColor"><path d="M5 7l5 5 5-5" /></svg>
          </div>

          <!-- 选项展开区 -->
          <div v-if="expanded === attr.id && (optionsOf(attr).length || attr.type === 'select' || attr.type === 'multiselect')" class="border-t border-line px-4 pb-4 pt-3">
            <div class="flex flex-wrap gap-2">
              <span
                v-for="(opt, i) in optionsOf(attr)"
                :key="i"
                class="group flex items-center gap-1 rounded-full border border-line px-2.5 py-1 text-[12px] text-ink-soft"
              >
                <span v-if="editingOption.defId === attr.id && editingOption.idx === i">
                  <input v-model="editingOption.val" class="w-24 border-b border-gold bg-transparent text-[12px] outline-none" @keyup.enter="confirmEdit(attr)" @keyup.escape="editingOption = { defId: null, idx: null, val: '' }" />
                </span>
                <span v-else @dblclick="startEdit(attr.id, i, opt)">{{ opt }}</span>
                <button class="invisible text-ink-faint hover:text-danger group-hover:visible" @click.stop="removeOption(attr, i)"><XMarkIcon class="h-3 w-3" /></button>
              </span>
              <span v-if="newOption.defId === attr.id" class="flex items-center gap-1 rounded-full border border-gold px-2.5 py-1">
                <input v-model="newOption.val" class="w-24 bg-transparent text-[12px] outline-none" placeholder="新选项" @keyup.enter="confirmAddOption(attr)" @keyup.escape="newOption = { defId: null, val: '' }" />
              </span>
              <button class="btn-ghost text-[12px]" @click.stop="startAddOption(attr.id)"><PlusIcon class="h-3 w-3" />添加</button>
            </div>
            <p class="mt-2 text-[11px] text-ink-faint">双击选项可编辑；回车确认，Esc 取消。变更即时保存。</p>
          </div>
          <div v-else-if="expanded === attr.id" class="border-t border-line px-4 py-3 text-[12px] italic text-ink-faint">
            该属性类型（{{ attr.type }}）无预设选项。
          </div>
        </div>
      </div>
    </div>

    <!-- ② 属性集×属性矩阵 -->
    <div v-show="tab === 'matrix'">
      <div class="mb-3 flex items-center justify-between gap-2">
        <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
          <span>列按<strong class="text-ink">属性集</strong>聚合。点击格子循环切换：
            <span class="rounded bg-gold/15 px-1.5 py-0.5 text-[10px] text-gold-deep">必</span> →
            <span class="rounded bg-info/10 px-1.5 py-0.5 text-[10px] text-info">选</span> →
            <span class="rounded bg-canvas-warm px-1.5 py-0.5 text-[10px] text-ink-faint">—</span>（隐藏）；改完点右上「保存配置」整单提交</span>
        </div>
        <button class="btn-outline shrink-0" @click="openSetModal()"><PlusIcon class="h-4 w-4" />新增属性集</button>
      </div>

      <EmptyState v-if="!store.loading && !store.sets.length" title="暂无属性集" hint="新增属性集后可在矩阵中配置属性可见性。" />
      <div v-else class="overflow-x-auto rounded-luxe border border-line">
        <table class="w-full border-collapse text-[12px]">
          <thead>
            <tr class="border-b border-line bg-canvas-warm/40">
              <th class="sticky left-0 z-10 min-w-[140px] bg-canvas-warm/40 px-4 py-3 text-left font-medium text-ink-soft">属性 \ 属性集</th>
              <th v-for="set in store.sets" :key="set.id" class="min-w-[140px] px-3 py-3 text-center align-top font-medium text-ink-soft">
                <div class="font-display text-[13px] text-ink">{{ set.label }}</div>
                <div class="mt-1 flex items-center justify-center gap-1">
                  <span class="rounded-full bg-gold/10 px-1.5 py-0.5 text-[10px] text-gold-deep">{{ set.categoryCount ?? 0 }} 个分类引用</span>
                  <button class="btn-ghost p-0.5" @click="openSetModal(set)"><PencilSquareIcon class="h-3.5 w-3.5" /></button>
                  <button
                    class="btn-danger-ghost p-0.5 disabled:opacity-40"
                    :disabled="(set.categoryCount ?? 0) > 0"
                    :title="(set.categoryCount ?? 0) > 0 ? '被分类引用，不可删除' : '删除'"
                    @click="confirmDeleteSet = set"
                  ><TrashIcon class="h-3.5 w-3.5" /></button>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="attr in store.defs" :key="attr.id" class="border-b border-line/60 last:border-0 hover:bg-canvas-warm/40">
              <td class="sticky left-0 z-10 bg-canvas px-4 py-2.5 text-ink-soft">{{ attr.label }}</td>
              <td v-for="set in store.sets" :key="set.id" class="px-3 py-2 text-center">
                <button
                  class="min-w-[2rem] rounded px-2 py-0.5 text-[11px] font-medium transition-colors"
                  :class="STATE_CLASSES[cellState(set.id, attr.id)]"
                  @click="cycleState(set.id, attr.id)"
                >{{ STATE_LABELS[cellState(set.id, attr.id)] }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="hasUnsavedChanges" class="mt-2 text-[12px] text-warn">有未保存的矩阵变更，请点击右上角「保存配置」。</p>
    </div>

    <!-- 属性集名称弹窗 -->
    <Teleport to="body">
      <div v-if="setModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="setModal = null">
        <div class="panel w-96 p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">{{ setModal.editing ? '编辑属性集' : '新增属性集' }}</h3>
            <button class="btn-ghost" @click="setModal = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div>
            <label class="field-label">属性集名称 *</label>
            <input v-model="setLabelDraft" class="field" placeholder="如：婚纱属性集" @keyup.enter="saveSetLabel" />
            <p v-if="setModalError" class="mt-1 text-[11px] text-danger">{{ setModalError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="setModal = null">取消</button>
            <button class="btn-gold" :disabled="savingSet" @click="saveSetLabel">{{ savingSet ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 属性定义抽屉（三语 label/options tab） -->
    <DrawerShell :open="defDrawer" eyebrow="Catalog" :title="editingDef ? '编辑属性定义' : '新增属性定义'" @close="defDrawer = false">
      <LocaleTabs v-model="defLocale" :filled="defFilled" />
      <div v-show="defLocale === 'en'" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="field-label">key *（英文标识，不可重复）</label>
            <input v-model="defForm.key" class="field font-mono" :disabled="!!editingDef" placeholder="如 silhouette" />
            <p v-if="defErrors.key" class="mt-1 text-[11px] text-danger">{{ defErrors.key }}</p>
          </div>
          <div>
            <label class="field-label">类型</label>
            <select v-model="defForm.type" class="field" :disabled="!!editingDef">
              <option value="select">select（单选）</option>
              <option value="multiselect">multiselect（多选）</option>
              <option value="text">text（文本）</option>
              <option value="toggle">toggle（开关）</option>
            </select>
          </div>
        </div>
        <div>
          <label class="field-label">名称 label *</label>
          <input v-model="defForm.label" class="field" placeholder="如 廓形 / Silhouette" />
          <p v-if="defErrors.label" class="mt-1 text-[11px] text-danger">{{ defErrors.label }}</p>
        </div>
        <div v-if="defForm.type === 'select' || defForm.type === 'multiselect'">
          <label class="field-label">选项（每行一项）</label>
          <textarea v-model="defForm.optionsText" rows="6" class="field resize-y font-mono text-[12px]"></textarea>
        </div>
      </div>
      <div v-for="l in ['es', 'fr'] as const" v-show="defLocale === l" :key="l" class="space-y-4">
        <div>
          <label class="field-label">名称 label（{{ l.toUpperCase() }}）</label>
          <input v-model="defTrans[l].label" class="field" />
        </div>
        <div v-if="defForm.type === 'select' || defForm.type === 'multiselect'">
          <label class="field-label">选项翻译（每行一项，需与主表等长）</label>
          <textarea v-model="defTrans[l].optionsText" rows="6" class="field resize-y font-mono text-[12px]"></textarea>
          <p v-if="defErrors[l + 'Options']" class="mt-1 text-[11px] text-danger">{{ defErrors[l + 'Options'] }}</p>
        </div>
        <p class="text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13）。</p>
      </div>
      <template #footer>
        <button class="btn-outline" @click="defDrawer = false">取消</button>
        <button class="btn-gold" :disabled="savingDef" @click="saveDef">{{ savingDef ? '保存中…' : '保存' }}</button>
      </template>
    </DrawerShell>

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
      :open="!!confirmDeleteDef"
      title="删除属性定义"
      :message="`确认删除属性「${confirmDeleteDef?.label}」？被属性集引用时将被拒绝（409）。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteDef"
      @cancel="confirmDeleteDef = null"
    />
  </div>
</template>
