// STORE-CAT-A03 useAttributeStore：属性集 + 属性字典（fetchAll 并行）；
// resolveAttributeConfig(categoryId)：沿分类祖先链取最近 attribute_set_id 矩阵 + 子分类 attr_overrides delta 合并
// （prototype resolveAttributeConfig 同义复刻——ProductEdit 属性区显隐/必填数据源）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { catalogApi } from '@/api'
import type {
  AttrVisibility,
  AttributeDef,
  AttributeDefUpsert,
  AttributeSet,
  AttributeSetUpsert,
} from '@/api/types'
import { useCategoriesStore } from './categories'

export interface ResolvedAttributeConfig {
  setId: number | null
  label: string
  /** attribute key → 三态 */
  attrs: Record<string, AttrVisibility>
  /** 子分类 delta（key → 三态） */
  overrides: Record<string, AttrVisibility>
  isChild: boolean
  childName: string
}

export const useAttributeStore = defineStore('attributes', () => {
  const sets = ref<AttributeSet[]>([])
  const defs = ref<AttributeDef[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      const [setsRes, defsRes] = await Promise.all([
        catalogApi.listAttributeSets(),
        catalogApi.listAttributeDefs(),
      ])
      sets.value = setsRes.items
      defs.value = defsRes.items
    } finally {
      loading.value = false
    }
  }

  async function saveSet(body: AttributeSetUpsert, id?: number) {
    const saved = id == null ? await catalogApi.createAttributeSet(body) : await catalogApi.updateAttributeSet(id, body)
    const idx = sets.value.findIndex((s) => s.id === saved.id)
    if (idx >= 0) sets.value[idx] = saved
    else sets.value.push(saved)
    return saved
  }

  async function removeSet(id: number) {
    await catalogApi.deleteAttributeSet(id)
    sets.value = sets.value.filter((s) => s.id !== id)
  }

  async function saveDef(body: AttributeDefUpsert, id?: number) {
    const saved = id == null ? await catalogApi.createAttributeDef(body) : await catalogApi.updateAttributeDef(id, body)
    const idx = defs.value.findIndex((d) => d.id === saved.id)
    if (idx >= 0) defs.value[idx] = saved
    else defs.value.push(saved)
    return saved
  }

  async function removeDef(id: number) {
    await catalogApi.deleteAttributeDef(id)
    defs.value = defs.value.filter((d) => d.id !== id)
  }

  function defByKey(key: string): AttributeDef | undefined {
    return defs.value.find((d) => d.key === key)
  }

  function defById(id: number): AttributeDef | undefined {
    return defs.value.find((d) => d.id === id)
  }

  /** 属性集矩阵 → key 索引三态 map */
  function setAttrs(setId: number | null | undefined): Record<string, AttrVisibility> {
    const out: Record<string, AttrVisibility> = {}
    if (setId == null) return out
    const set = sets.value.find((s) => s.id === setId)
    if (!set) return out
    for (const item of set.items) {
      const def = defById(item.attributeId)
      if (def) out[def.key] = item.visibility
    }
    return out
  }

  /** STORE-CAT-A03 派生：分类 → 属性显隐/必填配置（父级基础属性集 ⊕ 子分类覆盖 delta） */
  function resolveAttributeConfig(categoryId: number | null | undefined): ResolvedAttributeConfig {
    const categories = useCategoriesStore()
    const leaf = categories.leafOf(categoryId)
    const root = categories.rootOf(categoryId)
    const setId = root?.attributeSetId ?? null
    const base = setAttrs(setId)
    const isChild = !!leaf && !!root && leaf.id !== root.id
    const overrides = (isChild ? leaf?.attrOverrides : null) || {}
    const attrs = { ...base, ...overrides }
    const setLabel = sets.value.find((s) => s.id === setId)?.label ?? '未配置'
    return {
      setId,
      label: setLabel,
      attrs,
      overrides: { ...overrides },
      isChild,
      childName: isChild ? leaf!.name : '',
    }
  }

  return {
    sets,
    defs,
    loading,
    fetchAll,
    saveSet,
    removeSet,
    saveDef,
    removeDef,
    defByKey,
    defById,
    setAttrs,
    resolveAttributeConfig,
  }
})
