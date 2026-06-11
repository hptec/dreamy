// COMP-CAT-M02-1 / COMP-CAT-M03 / STORE-CAT-M01 单测（admin-prototype-alignment ALIGN-001/006）：
// 子品类覆盖只读派生 deriveSubcategoryOverrides + 矩阵副本 hasUnsavedChanges/整单保存（E-CAT-21 豁免语义）
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listAttributeSets = vi.fn()
const listAttributeDefs = vi.fn()
const updateAttributeSet = vi.fn()

vi.mock('@/api', () => ({
  catalogApi: {
    listAttributeSets: (...args: unknown[]) => listAttributeSets(...args),
    listAttributeDefs: (...args: unknown[]) => listAttributeDefs(...args),
    updateAttributeSet: (...args: unknown[]) => updateAttributeSet(...args),
    createAttributeSet: vi.fn(),
    deleteAttributeSet: vi.fn(),
    createAttributeDef: vi.fn(),
    updateAttributeDef: vi.fn(),
    deleteAttributeDef: vi.fn(),
  },
}))

// node 测试环境无 window（toast 内部 window.setTimeout）——mock 掉提示层
vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ success: vi.fn(), error: vi.fn() }),
}))

import { deriveSubcategoryOverrides, useAttributeMatrix } from '@/composables/useAttributeMatrix'
import { useAttributeStore } from '@/stores/attributes'
import type { AdminCategoryNode, AttributeDef, AttributeSet } from '@/api/types'

const def = (id: number, key: string): AttributeDef => ({
  id,
  key,
  label: `L-${key}`,
  type: 'select',
  options: [],
  translations: [],
})

const set = (id: number, label: string, items: AttributeSet['items']): AttributeSet => ({
  id,
  label,
  items,
  categoryCount: 0,
})

const node = (id: number, name: string, extra: Partial<AdminCategoryNode> = {}): AdminCategoryNode => ({
  id,
  name,
  parentId: null,
  attributeSetId: null,
  ...extra,
})

describe('deriveSubcategoryOverrides（COMP-CAT-M03：子品类覆盖只读汇总）', () => {
  it('仅保留有 attrOverrides delta 的子品类，且空组整体过滤（原型 L46-54 同口径）', () => {
    const tree: AdminCategoryNode[] = [
      node(1, 'Wedding', {
        children: [
          node(11, 'A-Line', { parentId: 1, attrOverrides: { train: 'visible' } }),
          node(12, 'Mermaid', { parentId: 1, attrOverrides: {} }),
          node(13, 'Ball Gown', { parentId: 1, attrOverrides: null }),
        ],
      }),
      node(2, 'Bridesmaid', { children: [node(21, 'Short', { parentId: 2 })] }),
      node(3, 'Accessories', { children: [] }),
    ]
    const groups = deriveSubcategoryOverrides(tree)
    expect(groups).toHaveLength(1)
    expect(groups[0].rootName).toBe('Wedding')
    expect(groups[0].children).toEqual([{ id: 11, name: 'A-Line', overrides: { train: 'visible' } }])
  })

  it('无任何覆盖时返回空数组（卡片区整体不渲染）', () => {
    expect(deriveSubcategoryOverrides([node(1, 'Wedding', { children: [node(11, 'A-Line', { parentId: 1 })] })])).toEqual([])
  })

  // TC-ALIGN-001b [UT][P1] 子品类覆盖卡片派生逻辑（L2 test-skeleton 原 fixture：root A(a1 含 2 项 overrides, a2 无)、root B 无）
  // L0 TRACE: ALIGN-001（source_id=page_categories_view.taxonomy_tab→matrix_tab）；L2 TRACE: COMP-CAT-M03
  it('TC-ALIGN-001b：a1 的 2 项 overrides 全量保留（渲染 2 行 delta 数据源）；a2/root B 无 delta 不入组', () => {
    const tree: AdminCategoryNode[] = [
      node(1, 'Root A', {
        children: [
          node(11, 'a1', { parentId: 1, attrOverrides: { train: 'visible', veil: 'hidden' } }),
          node(12, 'a2', { parentId: 1 }),
        ],
      }),
      node(2, 'Root B', { children: [node(21, 'b1', { parentId: 2 })] }),
    ]
    const groups = deriveSubcategoryOverrides(tree)
    expect(groups).toHaveLength(1) // 仅 root A 入组
    expect(groups[0].rootName).toBe('Root A')
    expect(groups[0].children).toHaveLength(1) // a2 无 overrides 不渲染
    expect(groups[0].children[0].name).toBe('a1')
    expect(groups[0].children[0].overrides).toEqual({ train: 'visible', veil: 'hidden' }) // 2 行 delta
  })
})

describe('useAttributeMatrix（COMP-CAT-M02-1 / E-CAT-21 整单保存豁免沿用）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  function seedStore() {
    const store = useAttributeStore()
    store.defs = [def(1, 'silhouette'), def(2, 'neckline')]
    store.sets = [
      set(10, '婚纱属性集', [{ attributeId: 1, visibility: 'visible' }]),
      set(20, '配饰属性集', [{ attributeId: 2, visibility: 'optional' }]),
    ]
    return store
  }

  it('初始化后无未保存变更；cycleState 后 hasUnsavedChanges=true（Tab 切换 guard 数据源）', () => {
    seedStore()
    const ctl = useAttributeMatrix()
    expect(ctl.hasUnsavedChanges).toBe(false)
    expect(ctl.cellState(10, 1)).toBe('visible')
    ctl.cycleState(10, 1) // visible → optional
    expect(ctl.cellState(10, 1)).toBe('optional')
    expect(ctl.hasUnsavedChanges).toBe(true)
  })

  it('saveMatrix 仅提交 dirty 属性集（整单覆盖，items 含非 hidden 项），成功后重置基线', async () => {
    seedStore()
    const ctl = useAttributeMatrix()
    ctl.cycleState(10, 2) // hidden → visible（set 10 变 dirty；set 20 不动）
    updateAttributeSet.mockResolvedValue(
      set(10, '婚纱属性集', [
        { attributeId: 1, visibility: 'visible' },
        { attributeId: 2, visibility: 'visible' },
      ]),
    )
    await ctl.saveMatrix()
    expect(updateAttributeSet).toHaveBeenCalledTimes(1)
    expect(updateAttributeSet).toHaveBeenCalledWith(10, {
      label: '婚纱属性集',
      items: [
        { attributeId: 1, visibility: 'visible' },
        { attributeId: 2, visibility: 'visible' },
      ],
    })
    expect(ctl.hasUnsavedChanges).toBe(false)
    expect(ctl.savingMatrix).toBe(false)
  })
})
