// STORE-REV-A02 useQuestionsStore：Q&A 列表（含 unansweredCount 角标）+ 回答（首答自动可见）
// + 可见性 Toggle 乐观更新 + 批量可见性 + 撤回回答
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { reviewsApi } from '@/api'
import type { AdminQuestion, QuestionVisible } from '@/api/types'
import { normalizeFilter } from '@/utils/validators'

export const useQuestionsStore = defineStore('questions', () => {
  const list = ref<AdminQuestion[]>([])
  const totalElements = ref(0)
  /** 待回答角标（列表响应 unanswered_count 平铺——对齐评价 pending_count 模式） */
  const unansweredCount = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  const answered = ref('all') // all | answered | unanswered
  const search = ref('')
  const selectedIds = ref<number[]>([])

  async function fetch() {
    loading.value = true
    try {
      const res = await reviewsApi.listQuestions({
        page: page.value,
        pageSize: pageSize.value,
        answered: normalizeFilter(answered.value),
        search: search.value.trim() || undefined,
      })
      list.value = res.data
      totalElements.value = res.totalElements
      unansweredCount.value = res.unansweredCount
    } finally {
      loading.value = false
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetch()
  }

  function applyFilters() {
    page.value = 1
    selectedIds.value = []
    return fetch()
  }

  function replaceRow(updated: AdminQuestion) {
    const idx = list.value.findIndex((q) => q.id === updated.id)
    if (idx >= 0) list.value[idx] = updated
  }

  /** FORM-REV-A06：保存回答（409805 并发冲突由视图层 toast + refetch）；首答成功后角标 -1 */
  async function saveAnswer(id: number, answer: string) {
    const row = list.value.find((q) => q.id === id)
    const firstAnswer = row != null && !row.answer
    const updated = await reviewsApi.putQuestionAnswer(id, answer)
    replaceRow(updated)
    if (firstAnswer) unansweredCount.value = Math.max(0, unansweredCount.value - 1)
    return updated
  }

  /** 撤回回答（幂等 204；answered→unanswered 角标 +1；行内 answer 同步） */
  async function removeAnswer(id: number) {
    await reviewsApi.deleteQuestionAnswer(id)
    const row = list.value.find((q) => q.id === id)
    if (row) {
      row.answer = null
      row.answerTime = null
    }
    unansweredCount.value += 1
  }

  /** 可见性 Toggle 乐观更新，失败回滚 */
  async function toggleVisible(row: AdminQuestion, visible: QuestionVisible) {
    const prev = row.visible
    if (prev === visible) return row
    row.visible = visible
    try {
      const updated = await reviewsApi.patchQuestionVisibility(row.id, visible)
      replaceRow(updated)
      return updated
    } catch (e) {
      row.visible = prev
      throw e
    }
  }

  /** 批量可见性后清选 + refetch（对齐评价 batch） */
  async function batch(action: 'hide' | 'show') {
    const result = await reviewsApi.batchQuestions([...selectedIds.value], action)
    selectedIds.value = []
    await fetch()
    return result
  }

  return {
    list,
    totalElements,
    unansweredCount,
    page,
    pageSize,
    loading,
    answered,
    search,
    selectedIds,
    fetch,
    setPage,
    applyFilters,
    saveAnswer,
    removeAnswer,
    toggleVisible,
    batch,
  }
})
