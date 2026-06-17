// STORE-002 useGlossaryStore：术语表 CRUD（FUNC-022）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { glossaryApi } from '@/api'
import type { GlossaryTerm, GlossaryTermUpsert } from '@/api/types'

export const useGlossaryStore = defineStore('glossary', () => {
  const terms = ref<GlossaryTerm[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(50)
  const loading = ref(false)
  const saving = ref(false)

  /** FUNC-022：拉取术语列表（category/enabled/keyword 筛选） */
  async function fetchTerms(
    params: { category?: string; enabled?: boolean; keyword?: string; page?: number } = {},
  ) {
    loading.value = true
    if (params.page) page.value = params.page
    try {
      const res = await glossaryApi.listTerms({
        category: params.category,
        enabled: params.enabled,
        keyword: params.keyword,
        page: page.value,
        pageSize: pageSize.value,
      })
      terms.value = res.data
      total.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  async function saveTerm(body: GlossaryTermUpsert, id?: number): Promise<GlossaryTerm> {
    saving.value = true
    try {
      const saved = id == null ? await glossaryApi.createTerm(body) : await glossaryApi.updateTerm(id, body)
      const idx = terms.value.findIndex((t) => t.id === saved.id)
      if (idx >= 0) terms.value[idx] = saved
      else terms.value.unshift(saved)
      return saved
    } finally {
      saving.value = false
    }
  }

  async function deleteTerm(id: number) {
    await glossaryApi.deleteTerm(id)
    terms.value = terms.value.filter((t) => t.id !== id)
  }

  return { terms, total, page, pageSize, loading, saving, fetchTerms, saveTerm, deleteTerm }
})
