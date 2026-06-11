<script setup lang="ts">
// COMP-CAT-M02-2/3 + COMP-CAT-M03（admin-prototype-alignment ALIGN-001/005/006）：
// 「品类×属性矩阵」sub-tab 面板——矩阵表（列=属性集聚合）自 AttributeSets.vue 迁入；
// 「保存配置」按钮自 PageHeader actions 迁至本区块右上（COMP-CAT-M02-3，E-CAT-21 整单保存豁免沿用 ALIGN-006）；
// 矩阵表下方新增子品类覆盖只读卡片区（COMP-CAT-M03，1:1 复制原型 AttributeSets.vue L184-206）
import { computed, ref } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useAttributeStore } from '@/stores/attributes'
import { useCategoriesStore } from '@/stores/categories'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { deriveSubcategoryOverrides, type AttributeMatrixCtl } from '@/composables/useAttributeMatrix'
import { PlusIcon, PencilSquareIcon, TrashIcon, RocketLaunchIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import type { AttrVisibility, AttributeSet } from '@/api/types'

const props = defineProps<{
  /** 矩阵可编辑副本控制器（由 Categories.vue 创建，供 Tab 切换防丢失 guard 共享 hasUnsavedChanges） */
  ctl: AttributeMatrixCtl
}>()

const store = useAttributeStore()
const categories = useCategoriesStore()
const toast = useToastStore()

const STATE_LABELS: Record<AttrVisibility, string> = { visible: '必', optional: '选', hidden: '—' }
const STATE_CLASSES: Record<AttrVisibility, string> = {
  visible: 'bg-gold/15 text-gold-deep',
  optional: 'bg-info/10 text-info',
  hidden: 'bg-canvas-warm text-ink-faint',
}

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

/* ===== COMP-CAT-M03：子品类覆盖只读汇总（只读概览；编辑入口在 Tab 1 品类卡片「N覆盖/继承」按钮） ===== */

const subcategoryOverrides = computed(() => deriveSubcategoryOverrides(categories.tree))

function attrLabel(key: string): string {
  return store.defByKey(key)?.label || key
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
      const items = (props.ctl.matrix[editing.id] && store.defs
        .filter((def) => (props.ctl.matrix[editing.id][def.id] || 'hidden') !== 'hidden')
        .map((def) => ({ attributeId: def.id, visibility: props.ctl.matrix[editing.id][def.id] }))) || editing.items
      await store.saveSet({ label, items }, editing.id)
    } else {
      await store.saveSet({ label, items: [] })
      props.ctl.initMatrix()
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
    toast.success('已删除')
    confirmDeleteSet.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409503) toast.error('该属性集被分类引用，不可删除')
    else toast.error(bizMsg(e, '删除失败'))
  } finally {
    confirmBusy.value = false
  }
}
</script>

<template>
  <div>
    <div class="mb-3 flex items-center justify-between gap-2">
      <div class="flex items-start gap-2 rounded-luxe border border-line bg-canvas-warm/60 px-4 py-2.5 text-[12px] text-ink-soft">
        <span>列按<strong class="text-ink">属性集</strong>聚合。点击格子循环切换：
          <span class="rounded bg-gold/15 px-1.5 py-0.5 text-[10px] text-gold-deep">必</span> →
          <span class="rounded bg-info/10 px-1.5 py-0.5 text-[10px] text-info">选</span> →
          <span class="rounded bg-canvas-warm px-1.5 py-0.5 text-[10px] text-ink-faint">—</span>（隐藏）；改完点右上「保存配置」整单提交</span>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <button class="btn-outline" @click="openSetModal()"><PlusIcon class="h-4 w-4" />新增属性集</button>
        <!-- COMP-CAT-M02-3：保存配置（自 PageHeader 迁入；未保存星标 + disabled 态，E-CAT-21） -->
        <button class="btn-gold" :disabled="!ctl.hasUnsavedChanges || ctl.savingMatrix" @click="ctl.saveMatrix">
          <RocketLaunchIcon class="h-4 w-4" />{{ ctl.savingMatrix ? '保存中…' : ctl.hasUnsavedChanges ? '保存配置 *' : '保存配置' }}
        </button>
      </div>
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
                :class="STATE_CLASSES[ctl.cellState(set.id, attr.id)]"
                @click="ctl.cycleState(set.id, attr.id)"
              >{{ STATE_LABELS[ctl.cellState(set.id, attr.id)] }}</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="ctl.hasUnsavedChanges" class="mt-2 text-[12px] text-warn">有未保存的矩阵变更，请点击右上角「保存配置」。</p>

    <!-- COMP-CAT-M03（ALIGN-001）：子品类覆盖卡片区（仅矩阵 sub-tab 内显示，只读概览，编辑入口在 Tab 1 品类卡片） -->
    <div v-if="subcategoryOverrides.length" class="mt-6">
      <h3 class="mb-3 text-[13px] font-medium text-ink">子品类属性覆盖（相对父级基础属性集的 delta）</h3>
      <div class="space-y-4">
        <div v-for="group in subcategoryOverrides" :key="group.rootId">
          <p class="mb-2 text-[11px] font-semibold uppercase tracking-widest text-ink-faint">{{ group.rootName }}</p>
          <div class="flex flex-wrap gap-3">
            <div
              v-for="child in group.children"
              :key="child.id"
              class="min-w-[180px] rounded-luxe border border-line bg-canvas-warm/40 px-4 py-3"
            >
              <p class="mb-2 text-[13px] font-medium text-ink">{{ child.name }}</p>
              <div class="space-y-1">
                <div v-for="(val, key) in child.overrides" :key="key" class="flex items-center justify-between gap-4 text-[12px]">
                  <span class="text-ink-soft">{{ attrLabel(String(key)) }}</span>
                  <span class="rounded px-1.5 py-0.5 text-[10px] font-medium" :class="STATE_CLASSES[val]">{{ STATE_LABELS[val] }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 属性集名称弹窗 -->
    <Teleport to="body">
      <div v-if="setModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (setModal = null)">
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
  </div>
</template>
