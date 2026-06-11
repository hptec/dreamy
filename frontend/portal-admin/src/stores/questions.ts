// STORE-REV-A02 useQuestionsStore：Q&A 列表 + 回答（首答自动可见）+ 可见性 Toggle 乐观更新
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { reviewsApi } from '@/api'
import type { AdminQuestion, QuestionVisible } from '@/api/types'
import { normalizeFilter } from '@/utils/validators'

export const useQuestionsStore = defineStore('questions', () => {
  const list = ref<AdminQuestion[]>([])
  const totalElements = ref(0)
  /** STORE-REV-A03：待回答角标（answered=unanswered 查询 totalElements，切 tab 首载取得） */
  const unansweredCount = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  const answered = ref('all') // all | answered | unanswered
  const search = ref('') // COMP-REV-A05：当前页内存过滤（契约无 search 参数）

  async function fetch() {
    loading.value = true
    try {
      const res = await reviewsApi.listQuestions({
        page: page.value,
        pageSize: pageSize.value,
        answered: normalizeFilter(answered.value),
      })
      list.value = res.data
      totalElements.value = res.totalElements
      if (answered.value === 'unanswered') unansweredCount.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  /** 角标初载（不影响主列表态） */
  async function fetchUnansweredCount() {
    try {
      const res = await reviewsApi.listQuestions({ page: 1, pageSize: 1, answered: 'unanswered' })
      unansweredCount.value = res.totalElements
    } catch {
      /* 角标失败静默 */
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetch()
  }

  function applyFilters() {
    page.value = 1
    return fetch()
  }

  function replaceRow(updated: AdminQuestion) {
    const idx = list.value.findIndex((q) => q.id === updated.id)
    if (idx >= 0) list.value[idx] = updated
  }

  /** FORM-REV-A06：保存回答；首答成功后行内 visible 同步（响应回写） */
  async function saveAnswer(id: number, answer: string) {
    const row = list.value.find((q) => q.id === id)
    const firstAnswer = row != null && !row.answer
    const updated = await reviewsApi.putQuestionAnswer(id, answer)
    replaceRow(updated)
    if (firstAnswer) unansweredCount.value = Math.max(0, unansweredCount.value - 1)
    return updated
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

  return {
    list,
    totalElements,
    unansweredCount,
    page,
    pageSize,
    loading,
    answered,
    search,
    fetch,
    fetchUnansweredCount,
    setPage,
    applyFilters,
    saveAnswer,
    toggleVisible,
  }
})
