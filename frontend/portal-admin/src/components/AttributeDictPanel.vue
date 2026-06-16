<script setup lang="ts">
// COMP-CAT-M02-1/2（admin-prototype-alignment ALIGN-001）：属性字典 sub-tab 面板——
// 自 AttributeSets.vue（PAGE-CAT-A04）原样迁入 Categories.vue Tab 2「属性集与字典」；
// 选项增删改 confirmAddOption/removeOption/confirmEdit + type 徽章 + 三语 label/options tab（V-CAT-058）+ 删除 409507 引导
import { computed, ref } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DrawerShell from '@/components/DrawerShell.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import { useAttributeStore } from '@/stores/attributes'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { ATTR_KEY_PATTERN, extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { PlusIcon, PencilSquareIcon, TrashIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { AttributeDefType } from '@/api/types'
import type { AttributeDef, AttributeDefTranslation } from '@/api/types'

const store = useAttributeStore()
const toast = useToastStore()

/** 类型徽章/提示文案（整数契约后由前端映射回可读名） */
const TYPE_LABELS: Record<number, string> = {
  [AttributeDefType.SELECT]: 'select',
  [AttributeDefType.MULTISELECT]: 'multiselect',
  [AttributeDefType.TEXT]: 'text',
  [AttributeDefType.TOGGLE]: 'toggle',
}

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
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
const defForm = ref({ key: '', label: '', type: AttributeDefType.SELECT as AttributeDefType, optionsText: '' })
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
    : { key: '', label: '', type: AttributeDefType.SELECT, optionsText: '' }
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
  const keyTrimmed = defForm.value.key.trim()
  if (!keyTrimmed) defErrors.value.key = 'key 必填'
  else if (keyTrimmed.length > 64 || !ATTR_KEY_PATTERN.test(keyTrimmed))
    defErrors.value.key = '仅允许小写字母开头，由小写字母/数字/下划线组成（≤64）'
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
const confirmBusy = ref(false)
const deleteConfirmText = ref('')
const deleteRefInfo = ref<{ setCount?: number; valueCount?: number } | null>(null)

async function doDeleteDef() {
  if (!confirmDeleteDef.value) return

  const needForce = deleteRefInfo.value && (deleteRefInfo.value.setCount || deleteRefInfo.value.valueCount)

  // 如果需要强制删除，检查确认文本
  if (needForce && deleteConfirmText.value !== 'DELETE') {
    return
  }

  confirmBusy.value = true
  try {
    await store.removeDef(confirmDeleteDef.value.id, needForce)
    toast.success('已删除')
    confirmDeleteDef.value = null
    deleteRefInfo.value = null
    deleteConfirmText.value = ''
  } catch (e) {
    if (e instanceof BizError && e.code === 409507) {
      // 首次删除遇到引用：提取引用信息并显示强制删除确认
      const details = e.details || {}
      deleteRefInfo.value = {
        setCount: details.attribute_set_count as number | undefined,
        valueCount: details.product_value_count as number | undefined,
      }
      deleteConfirmText.value = ''
      toast.error('该属性被引用，请输入 DELETE 确认强制删除')
    } else {
      toast.error(bizMsg(e, '删除失败'))
    }
  } finally {
    confirmBusy.value = false
  }
}

function cancelDeleteDef() {
  confirmDeleteDef.value = null
  deleteRefInfo.value = null
  deleteConfirmText.value = ''
}
</script>

<template>
  <div>
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
              :class="attr.type === AttributeDefType.SELECT ? 'bg-gold/10 text-gold-deep' : attr.type === AttributeDefType.MULTISELECT ? 'bg-info/10 text-info' : 'bg-canvas-warm text-ink-faint'"
            >{{ TYPE_LABELS[attr.type] }}</span>
            <span class="ml-2 font-mono text-[10px] text-ink-faint">{{ attr.key }}</span>
          </div>
          <span v-if="optionsOf(attr).length" class="text-[12px] text-ink-faint">{{ optionsOf(attr).length }} 个选项</span>
          <span v-else class="text-[12px] italic text-ink-faint">无选项（{{ TYPE_LABELS[attr.type] }}）</span>
          <button class="btn-ghost" @click.stop="openDefDrawer(attr)"><PencilSquareIcon class="h-4 w-4" /></button>
          <button class="btn-danger-ghost" @click.stop="confirmDeleteDef = attr"><TrashIcon class="h-4 w-4" /></button>
          <svg class="h-4 w-4 text-ink-faint transition-transform" :class="expanded === attr.id ? 'rotate-180' : ''" viewBox="0 0 20 20" fill="currentColor"><path d="M5 7l5 5 5-5" /></svg>
        </div>

        <!-- 选项展开区 -->
        <div v-if="expanded === attr.id && (optionsOf(attr).length || attr.type === AttributeDefType.SELECT || attr.type === AttributeDefType.MULTISELECT)" class="border-t border-line px-4 pb-4 pt-3">
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
          该属性类型（{{ TYPE_LABELS[attr.type] }}）无预设选项。
        </div>
      </div>
    </div>

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
            <AppSelect
              :model-value="defForm.type"
              :options="[{ value: AttributeDefType.SELECT, label: 'select（单选）' }, { value: AttributeDefType.MULTISELECT, label: 'multiselect（多选）' }, { value: AttributeDefType.TEXT, label: 'text（文本）' }, { value: AttributeDefType.TOGGLE, label: 'toggle（开关）' }]"
              :disabled="!!editingDef"
              @update:model-value="defForm.type = $event as typeof defForm.type"
            />
          </div>
        </div>
        <div>
          <label class="field-label">名称 label *</label>
          <input v-model="defForm.label" class="field" placeholder="如 廓形 / Silhouette" />
          <p v-if="defErrors.label" class="mt-1 text-[11px] text-danger">{{ defErrors.label }}</p>
        </div>
        <div v-if="defForm.type === AttributeDefType.SELECT || defForm.type === AttributeDefType.MULTISELECT">
          <label class="field-label">选项（每行一项）</label>
          <textarea v-model="defForm.optionsText" rows="6" class="field resize-y font-mono text-[12px]"></textarea>
        </div>
      </div>
      <div v-for="l in ['es', 'fr'] as const" v-show="defLocale === l" :key="l" class="space-y-4">
        <div>
          <label class="field-label">名称 label（{{ l.toUpperCase() }}）</label>
          <input v-model="defTrans[l].label" class="field" />
        </div>
        <div v-if="defForm.type === AttributeDefType.SELECT || defForm.type === AttributeDefType.MULTISELECT">
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
      :open="!!confirmDeleteDef"
      title="删除属性定义"
      :message="deleteRefInfo ? `属性「${confirmDeleteDef?.label}」被以下内容引用，强制删除将清理所有引用数据：` : `确认删除属性「${confirmDeleteDef?.label}」？`"
      :confirm-text="deleteRefInfo ? '强制删除' : '删除'"
      danger
      :busy="confirmBusy"
      @confirm="doDeleteDef"
      @cancel="cancelDeleteDef"
    >
      <div v-if="deleteRefInfo" class="mt-4 space-y-3">
        <div v-if="deleteRefInfo.setCount" class="rounded bg-canvas-warm px-3 py-2 text-[12px] text-ink-soft">
          <span class="font-medium text-danger">{{ deleteRefInfo.setCount }}</span> 个属性集引用
        </div>
        <div v-if="deleteRefInfo.valueCount" class="rounded bg-canvas-warm px-3 py-2 text-[12px] text-ink-soft">
          <span class="font-medium text-danger">{{ deleteRefInfo.valueCount }}</span> 个商品属性值
        </div>
        <div class="mt-3">
          <label class="field-label text-danger">请输入 <strong>DELETE</strong> 确认强制删除</label>
          <input
            v-model="deleteConfirmText"
            class="field border-danger focus:border-danger focus:ring-danger"
            placeholder="输入 DELETE"
            @keyup.enter="doDeleteDef"
          />
          <p class="mt-1 text-[11px] text-ink-faint">此操作不可撤销，将永久删除该属性及其所有引用数据。</p>
        </div>
      </div>
    </ConfirmDialog>
  </div>
</template>
