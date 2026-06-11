// STORE-CAT-A02 useCategoriesStore：分类树单次全量拉取，写后整树 refetch；
// 派生 cascadeOptions（ProductEdit 两级级联）与 leafOf
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { catalogApi } from '@/api'
import type { AdminCategoryNode, AdminCategoryUpsert } from '@/api/types'

export interface CascadeOption {
  id: number
  name: string
  children: { id: number; name: string }[]
}

export const useCategoriesStore = defineStore('categories', () => {
  const tree = ref<AdminCategoryNode[]>([])
  const loading = ref(false)

  async function fetch() {
    loading.value = true
    try {
      const res = await catalogApi.listCategories()
      tree.value = res.items
    } finally {
      loading.value = false
    }
  }

  async function create(body: AdminCategoryUpsert) {
    const node = await catalogApi.createCategory(body)
    await fetch()
    return node
  }

  async function update(id: number, body: AdminCategoryUpsert) {
    const node = await catalogApi.updateCategory(id, body)
    await fetch()
    return node
  }

  async function remove(id: number) {
    await catalogApi.deleteCategory(id)
    await fetch()
  }

  /** 两级级联数据源（根 → 子） */
  const cascadeOptions = computed<CascadeOption[]>(() =>
    tree.value.map((root) => ({
      id: root.id,
      name: root.name,
      children: (root.children || []).map((c) => ({ id: c.id, name: c.name })),
    })),
  )

  /** categoryId → 节点（根或子级） */
  function leafOf(categoryId: number | null | undefined): AdminCategoryNode | null {
    if (categoryId == null) return null
    for (const root of tree.value) {
      if (root.id === categoryId) return root
      for (const c of root.children || []) {
        if (c.id === categoryId) return c
      }
    }
    return null
  }

  /** categoryId → 根节点（子级回溯父级） */
  function rootOf(categoryId: number | null | undefined): AdminCategoryNode | null {
    if (categoryId == null) return null
    for (const root of tree.value) {
      if (root.id === categoryId) return root
      if ((root.children || []).some((c) => c.id === categoryId)) return root
    }
    return null
  }

  return { tree, loading, fetch, create, update, remove, cascadeOptions, leafOf, rootOf }
})
