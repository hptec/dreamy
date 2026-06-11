// COMP-CAT-M02-1 / STORE-CAT-M01（admin-prototype-alignment ALIGN-001）：
// 品类×属性矩阵可编辑副本——自 AttributeSets.vue 抽出为 composable，供 Categories.vue Tab 2 矩阵 sub-tab 消费；
// E-CAT-21 / ALIGN-006 豁免沿用：hasUnsavedChanges + 整单覆盖保存语义不变（不回对原型即点即改）。
import { computed, reactive, ref, watch } from 'vue'
import { useAttributeStore } from '@/stores/attributes'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import type { AdminCategoryNode, AttrVisibility } from '@/api/types'

const STATES: AttrVisibility[] = ['visible', 'optional', 'hidden']

export interface SubcategoryOverrideChild {
  id: number
  name: string
  overrides: Record<string, AttrVisibility>
}

export interface SubcategoryOverrideGroup {
  rootId: number
  rootName: string
  children: SubcategoryOverrideChild[]
}

/**
 * COMP-CAT-M03（ALIGN-001）：子品类覆盖只读汇总——按根品类分组列出含 attr_overrides delta 的子品类。
 * 派生口径对照原型 AttributeSets.vue L46-54：无覆盖子类过滤、空组过滤（空时整个卡片区不渲染）。
 */
export function deriveSubcategoryOverrides(tree: AdminCategoryNode[]): SubcategoryOverrideGroup[] {
  return tree
    .map((root) => ({
      rootId: root.id,
      rootName: root.name,
      children: (root.children || [])
        .map((c) => ({ id: c.id, name: c.name, overrides: c.attrOverrides || {} }))
        .filter((c) => Object.keys(c.overrides).length > 0),
    }))
    .filter((g) => g.children.length > 0)
}

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

export function useAttributeMatrix() {
  const store = useAttributeStore()
  const toast = useToastStore()

  // setId → (attributeId → visibility) 可编辑副本
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

  // fetchAll / removeSet 整组替换 sets 数组时重建基线（saveMatrix 成功后手动重建）
  watch(() => store.sets, initMatrix, { immediate: true })

  function cellState(setId: number, defId: number): AttrVisibility {
    return matrix.value[setId]?.[defId] || 'hidden'
  }

  function cycleState(setId: number, defId: number) {
    const cur = cellState(setId, defId)
    if (!matrix.value[setId]) matrix.value[setId] = {}
    matrix.value[setId][defId] = STATES[(STATES.indexOf(cur) + 1) % STATES.length]
  }

  /** FORM-CAT-A04 / E-CAT-21：未保存变更检测（保存按钮态 + Tab 切换防丢失 guard 数据源） */
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

  /** E-CAT-21：整单覆盖保存（仅 dirty 属性集逐个 PUT，items 含非 hidden 项；成功后重置基线） */
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

  return reactive({
    matrix,
    initMatrix,
    cellState,
    cycleState,
    hasUnsavedChanges,
    savingMatrix,
    saveMatrix,
  })
}

export type AttributeMatrixCtl = ReturnType<typeof useAttributeMatrix>
