// STORE-CAT-A04 useCollectionsStore：集合分组 + 集合（按分组缓存）；
// collectionsByGroup(groupId) 为 ProductEdit 加入集合选择器数据源
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { catalogApi } from '@/api'
import type {
  Collection,
  CollectionGroup,
  CollectionGroupUpsert,
  CollectionProduct,
  CollectionUpsert,
} from '@/api/types'

export const useCollectionsStore = defineStore('collections', () => {
  const groups = ref<CollectionGroup[]>([])
  const collections = ref<Collection[]>([])
  const loading = ref(false)

  async function fetchGroups() {
    const res = await catalogApi.listCollectionGroups()
    groups.value = res.items
  }

  async function fetchCollections(groupId?: number) {
    loading.value = true
    try {
      const res = await catalogApi.listCollections(groupId)
      if (groupId == null) {
        collections.value = res.items
      } else {
        collections.value = [...collections.value.filter((c) => c.collectionGroupId !== groupId), ...res.items]
      }
    } finally {
      loading.value = false
    }
  }

  function collectionsByGroup(groupId: number): Collection[] {
    return collections.value.filter((c) => c.collectionGroupId === groupId)
  }

  async function saveGroup(body: CollectionGroupUpsert, id?: number) {
    const saved = id == null ? await catalogApi.createCollectionGroup(body) : await catalogApi.updateCollectionGroup(id, body)
    await fetchGroups()
    return saved
  }

  /** FORM-CAT-A05：删除分组 409506 由视图 toast 引导先清空集合 */
  async function removeGroup(id: number) {
    await catalogApi.deleteCollectionGroup(id)
    groups.value = groups.value.filter((g) => g.id !== id)
    collections.value = collections.value.filter((c) => c.collectionGroupId !== id)
  }

  async function saveCollection(body: CollectionUpsert, id?: number) {
    const saved = id == null ? await catalogApi.createCollection(body) : await catalogApi.updateCollection(id, body)
    const idx = collections.value.findIndex((c) => c.id === saved.id)
    if (idx >= 0) collections.value[idx] = saved
    else collections.value.push(saved)
    return saved
  }

  async function removeCollection(id: number) {
    await catalogApi.deleteCollection(id)
    collections.value = collections.value.filter((c) => c.id !== id)
  }

  // ===== 集合内商品管理 E-CAT-35~37 =====

  async function fetchCollectionProducts(id: number): Promise<CollectionProduct[]> {
    const res = await catalogApi.listCollectionProducts(id)
    return res.items
  }

  async function replaceCollectionProducts(id: number, productIds: number[]): Promise<void> {
    await catalogApi.replaceCollectionProducts(id, productIds)
    // 本地刷新该集合 productCount（避免整页重拉）
    const c = collections.value.find((x) => x.id === id)
    if (c) c.productCount = productIds.length
  }

  async function removeCollectionProduct(id: number, productId: number): Promise<void> {
    await catalogApi.removeCollectionProduct(id, productId)
    const c = collections.value.find((x) => x.id === id)
    if (c && c.productCount != null) c.productCount = Math.max(0, c.productCount - 1)
  }

  return {
    groups,
    collections,
    loading,
    fetchGroups,
    fetchCollections,
    collectionsByGroup,
    saveGroup,
    removeGroup,
    saveCollection,
    removeCollection,
    fetchCollectionProducts,
    replaceCollectionProducts,
    removeCollectionProduct,
  }
})
