// STORE-CAT-A04 useTagsStore：标签维度 + 标签（按维度缓存）；
// tagsByDimension(dimId) 为 ProductEdit 标签选择器数据源
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { catalogApi } from '@/api'
import type { Tag, TagDimension, TagDimensionUpsert, TagUpsert } from '@/api/types'

export const useTagsStore = defineStore('tags', () => {
  const dimensions = ref<TagDimension[]>([])
  const tags = ref<Tag[]>([])
  const loading = ref(false)

  async function fetchDimensions() {
    const res = await catalogApi.listTagDimensions()
    dimensions.value = res.items
  }

  async function fetchTags(dimensionId?: number) {
    loading.value = true
    try {
      const res = await catalogApi.listTags(dimensionId)
      if (dimensionId == null) {
        tags.value = res.items
      } else {
        tags.value = [...tags.value.filter((t) => t.dimensionId !== dimensionId), ...res.items]
      }
    } finally {
      loading.value = false
    }
  }

  function tagsByDimension(dimensionId: number): Tag[] {
    return tags.value.filter((t) => t.dimensionId === dimensionId)
  }

  async function saveDimension(body: TagDimensionUpsert, id?: number) {
    const saved = id == null ? await catalogApi.createTagDimension(body) : await catalogApi.updateTagDimension(id, body)
    await fetchDimensions()
    return saved
  }

  /** FORM-CAT-A05：删除维度 409506 由视图 toast 引导先清空标签 */
  async function removeDimension(id: number) {
    await catalogApi.deleteTagDimension(id)
    dimensions.value = dimensions.value.filter((d) => d.id !== id)
    tags.value = tags.value.filter((t) => t.dimensionId !== id)
  }

  async function saveTag(body: TagUpsert, id?: number) {
    const saved = id == null ? await catalogApi.createTag(body) : await catalogApi.updateTag(id, body)
    const idx = tags.value.findIndex((t) => t.id === saved.id)
    if (idx >= 0) tags.value[idx] = saved
    else tags.value.push(saved)
    return saved
  }

  async function removeTag(id: number) {
    await catalogApi.deleteTag(id)
    tags.value = tags.value.filter((t) => t.id !== id)
  }

  return {
    dimensions,
    tags,
    loading,
    fetchDimensions,
    fetchTags,
    tagsByDimension,
    saveDimension,
    removeDimension,
    saveTag,
    removeTag,
  }
})
