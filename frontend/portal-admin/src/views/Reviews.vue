<script setup lang="ts">
// PAGE-REV-A01 / COMP-REV-A01~A06：评价与 Q&A（按原型 583 行版 copy-adapt 新建；mock → E-REV-06~15）
// 显式偏离 ×2（设计 §C）：①chips 计数仅「待审核」带角标（契约仅 pending_count）；
// ②Q&A 提问人/内容搜索为当前页内存过滤（契约无 search 参数，tooltip 标注）
import { computed, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import Toggle from '@/components/Toggle.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useReviewsStore } from '@/stores/reviews'
import { useQuestionsStore } from '@/stores/questions'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { formatDateTime } from '@/utils/format'
import { StarIcon as StarSolid } from '@heroicons/vue/24/solid'
import {
  StarIcon, MagnifyingGlassIcon, XMarkIcon, CheckIcon, NoSymbolIcon, SparklesIcon,
  PhotoIcon, ChatBubbleLeftRightIcon, PencilSquareIcon, TrashIcon, ArrowUturnLeftIcon,
} from '@heroicons/vue/24/outline'
import { QuestionVisible, ReviewModerationStatus } from '@/api/types'
import type { AdminQuestion, AdminReview } from '@/api/types'

const reviews = useReviewsStore()
const questions = useQuestionsStore()
const toast = useToastStore()

const activeTab = ref<'reviews' | 'qa'>('reviews')
const mainTabs = [
  ['reviews', '评价审核'],
  ['qa', 'Q&A 管理'],
] as const

function bizMsg(e: unknown, fallback: string): string {
  return e instanceof BizError ? e.message : fallback
}

/* ===================== Tab 1 · 评价审核 ===================== */

// chips（仅「待审核」带 pendingCount 角标——显式偏离①）
const statusChips = computed(() => [
  { key: 'all', label: '全部', count: null as number | null },
  { key: 'pending', label: '待审核', count: reviews.pendingCount },
  { key: 'approved', label: '已通过', count: null },
  { key: 'featured', label: '精选', count: null },
  { key: 'rejected', label: '已拒绝', count: null },
])

function selectChip(key: string) {
  reviews.chip = key as typeof reviews.chip
  reviews.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
}

let reviewSearchTimer: ReturnType<typeof setTimeout> | null = null
function onReviewSearch() {
  if (reviewSearchTimer) clearTimeout(reviewSearchTimer)
  reviewSearchTimer = setTimeout(() => {
    reviews.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
  }, 300)
}

function onRatingChange() {
  reviews.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
}

function reviewBadge(r: AdminReview) {
  if (r.status === ReviewModerationStatus.PENDING) return { tone: 'warn', label: '待审核' }
  if (r.status === ReviewModerationStatus.REJECTED) return { tone: 'danger', label: '已拒绝' }
  return r.featured ? { tone: 'info', label: '精选' } : { tone: 'ok', label: '已通过' }
}

// 勾选与批量（FORM-REV-A02）
const allChecked = computed(
  () => reviews.list.length > 0 && reviews.list.every((r) => reviews.selectedIds.includes(r.id)),
)
function toggleAll() {
  if (allChecked.value) reviews.selectedIds = []
  else reviews.selectedIds = reviews.list.map((r) => r.id)
}
function toggleSelect(id: number) {
  const idx = reviews.selectedIds.indexOf(id)
  if (idx >= 0) reviews.selectedIds.splice(idx, 1)
  else reviews.selectedIds.push(id)
}

async function batchSet(action: 'approve' | 'reject') {
  try {
    const result = await reviews.batch(action)
    const verb = action === 'approve' ? '通过' : '拒绝'
    let msg = `已批量${verb} ${result.updatedIds.length} 条评价`
    if (result.skippedIds.length) msg += `，${result.skippedIds.length} 条因状态限制跳过`
    toast.success(msg)
  } catch (e) {
    toast.error(bizMsg(e, '批量操作失败'))
  }
}

// 单条状态流转（FORM-REV-A01）
async function approveReview(r: AdminReview) {
  try {
    await reviews.moderate(r, ReviewModerationStatus.APPROVED)
    toast.success(`评价 ${r.id} 已通过，已记入操作日志`)
    syncDetail(r.id)
  } catch (e) {
    handleModerateError(e)
  }
}
async function rejectReview(r: AdminReview) {
  try {
    await reviews.moderate(r, ReviewModerationStatus.REJECTED)
    toast.success(`评价 ${r.id} 已拒绝，已记入操作日志`)
    syncDetail(r.id)
  } catch (e) {
    handleModerateError(e)
  }
}
function handleModerateError(e: unknown) {
  if (e instanceof BizError && e.code === 409802) {
    toast.error('仅待审核评价可审核')
    reviews.fetch().catch(() => undefined)
  } else if (e instanceof BizError && (e.code === 404801 || e.code === 404802 || e.code === 404803)) {
    toast.error('数据已变更')
    reviews.fetch().catch(() => undefined)
  } else {
    toast.error(bizMsg(e, '操作失败'))
  }
}

// 精选（FORM-REV-A03：乐观翻转，409803 回滚）
async function setFeatured(r: AdminReview, val: boolean) {
  try {
    await reviews.setFeatured(r, val)
    toast.success(val ? '已设为精选，将在前台评价区置顶' : '已取消精选')
    syncDetail(r.id)
  } catch (e) {
    if (e instanceof BizError && e.code === 409803) toast.error('仅已通过评价可精选')
    else toast.error(bizMsg(e, '操作失败'))
  }
}

// 详情抽屉（COMP-REV-A03）
const showReviewDrawer = ref(false)
const detailReview = ref<AdminReview | null>(null)
const replyDraft = ref('')
const replyEditing = ref(false)
const confirmDeleteReply = ref(false)
const confirmBusy = ref(false)

function openReview(r: AdminReview) {
  detailReview.value = r
  replyDraft.value = r.replyContent || ''
  replyEditing.value = false
  showReviewDrawer.value = true
}

/** 行内写操作后同步抽屉对象引用 */
function syncDetail(id: number) {
  if (detailReview.value?.id === id) {
    const fresh = reviews.list.find((r) => r.id === id)
    if (fresh) detailReview.value = fresh
  }
}

async function saveReply() {
  if (!detailReview.value || !replyDraft.value.trim()) return
  try {
    const updated = await reviews.saveReply(detailReview.value.id, replyDraft.value.trim())
    detailReview.value = updated
    replyEditing.value = false
    toast.success('官方回复已保存，将以 Dreamy Team 署名展示')
  } catch (e) {
    if (e instanceof BizError && e.code === 409804) toast.error('仅已通过评价可回复')
    else if (e instanceof BizError && e.code === 422801) toast.error(e.message)
    else toast.error(bizMsg(e, '操作失败'))
  }
}

/** 删除回复二次确认（CP-071 危险操作） */
async function deleteReply() {
  if (!detailReview.value) return
  confirmBusy.value = true
  try {
    const updated = await reviews.removeReply(detailReview.value.id)
    detailReview.value = updated
    replyDraft.value = ''
    replyEditing.value = false
    confirmDeleteReply.value = false
    toast.success('官方回复已删除')
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  } finally {
    confirmBusy.value = false
  }
}

// Lightbox（COMP-REV-A04 / FORM-REV-A05：可逆操作无确认弹窗）
const lightbox = ref<{ review: AdminReview; index: number } | null>(null)
function openLightbox(r: AdminReview, i: number) {
  lightbox.value = { review: r, index: i }
}
const lightboxImage = computed(() => (lightbox.value ? lightbox.value.review.images[lightbox.value.index] : null))

async function toggleLightboxImage(rejected: boolean) {
  if (!lightbox.value || !lightboxImage.value) return
  const { review, index } = lightbox.value
  try {
    const updated = await reviews.toggleImage(review.id, lightboxImage.value.id, rejected)
    lightbox.value = { review: updated, index }
    syncDetail(review.id)
    if (detailReview.value?.id === review.id) detailReview.value = updated
    toast.success(rejected ? '已驳回该图片，前台将不再展示' : '已恢复展示该图片')
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

/* ===================== Tab 2 · Q&A 管理 ===================== */

const qaChips = computed(() => [
  { key: 'all', label: '全部', count: null as number | null },
  { key: 'unanswered', label: '待回答', count: questions.unansweredCount },
  { key: 'answered', label: '已回答', count: null },
])

function selectQaChip(key: string) {
  questions.answered = key
  questions.applyFilters().catch((e) => toast.error(bizMsg(e, '加载失败')))
}

/** 显式偏离②：提问人/内容搜索为当前页内存过滤（契约无 search 参数） */
const filteredQa = computed(() => {
  const q = questions.search.trim().toLowerCase()
  if (!q) return questions.list
  return questions.list.filter(
    (item) =>
      (item.productName || '').toLowerCase().includes(q) ||
      (item.asker || '').toLowerCase().includes(q) ||
      item.question.toLowerCase().includes(q),
  )
})

async function toggleQaVisible(q: AdminQuestion, val: boolean) {
  try {
    await questions.toggleVisible(q, val ? QuestionVisible.VISIBLE : QuestionVisible.HIDDEN)
    toast.success(val ? '该问答已上线，前台可见' : '该问答已隐藏')
    if (detailQa.value?.id === q.id) detailQa.value = questions.list.find((x) => x.id === q.id) || detailQa.value
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

const showQaDrawer = ref(false)
const detailQa = ref<AdminQuestion | null>(null)
const answerDraft = ref('')
const answerEditing = ref(false)

function openQa(q: AdminQuestion) {
  detailQa.value = q
  answerDraft.value = q.answer || ''
  answerEditing.value = false
  showQaDrawer.value = true
}

async function saveAnswer() {
  if (!detailQa.value || !answerDraft.value.trim()) return
  try {
    const updated = await questions.saveAnswer(detailQa.value.id, answerDraft.value.trim())
    detailQa.value = updated // 首答自动可见（响应回写 visible）
    answerEditing.value = false
    toast.success('官方回答已保存，已记入操作日志')
  } catch (e) {
    toast.error(bizMsg(e, '操作失败'))
  }
}

// 两 tab 数据互不预载（切换时 fetch——COMP-REV-A01）
const qaLoaded = ref(false)
watch(activeTab, (t) => {
  if (t === 'qa' && !qaLoaded.value) {
    qaLoaded.value = true
    questions.fetch().catch((e) => toast.error(bizMsg(e, '加载提问失败')))
  }
})

onMounted(() => {
  reviews.fetch().catch((e) => toast.error(bizMsg(e, '加载评价失败')))
  questions.fetchUnansweredCount()
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Content · UGC" title="评价与 Q&A" subtitle="审核买家评价与提问，管理精选、官方回复与前台可见性">
      <template #actions>
        <span class="rounded-full bg-warn/14 px-3 py-1 text-[12px] text-warn">{{ reviews.pendingCount }} 条评价待审核</span>
        <span class="rounded-full bg-info/12 px-3 py-1 text-[12px] text-info">{{ questions.unansweredCount }} 个提问待回答</span>
      </template>
    </PageHeader>

    <!-- 主 Tab -->
    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in mainTabs"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="activeTab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="activeTab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- ===================== Tab 1 评价审核 ===================== -->
    <template v-if="activeTab === 'reviews'">
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="c in statusChips"
            :key="c.key"
            class="rounded-full border px-3 py-1 text-[12px] transition-colors"
            :class="reviews.chip === c.key ? 'border-gold bg-gold/10 font-medium text-gold-deep' : 'border-line text-ink-soft hover:bg-canvas-warm'"
            @click="selectChip(c.key)"
          >{{ c.label }}<template v-if="c.count != null"> · {{ c.count }}</template></button>
        </div>
        <select v-model="reviews.rating" class="field" style="min-width:120px" @change="onRatingChange">
          <option value="all">全部星级</option>
          <option v-for="n in [5, 4, 3, 2, 1]" :key="n" :value="String(n)">{{ n }} 星</option>
        </select>
        <div class="relative">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="reviews.search" class="field w-56 pl-9" placeholder="搜索商品 / 买家…" @input="onReviewSearch" />
        </div>
        <span class="ml-auto text-[12px] text-ink-faint">共 {{ reviews.totalElements }} 条</span>
      </div>

      <!-- 批量操作条 -->
      <div v-if="reviews.selectedIds.length" class="mb-3 flex items-center gap-3 rounded-luxe border border-gold/40 bg-gold/8 px-4 py-2.5">
        <span class="text-[13px] text-ink">已选 {{ reviews.selectedIds.length }} 条评价</span>
        <button class="btn-ghost text-ok" @click="batchSet('approve')"><CheckIcon class="h-4 w-4" />批量通过</button>
        <button class="btn-danger-ghost" @click="batchSet('reject')"><NoSymbolIcon class="h-4 w-4" />批量拒绝</button>
        <button class="ml-auto text-[12px] text-ink-faint hover:text-ink" @click="reviews.selectedIds = []">取消选择</button>
      </div>

      <div class="panel overflow-hidden">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:36px"><input type="checkbox" class="h-3.5 w-3.5 rounded accent-gold" :checked="allChecked" @change="toggleAll" /></th>
              <th>商品</th>
              <th>买家</th>
              <th style="width:110px">星级</th>
              <th>评价摘要</th>
              <th style="width:64px">图片</th>
              <th style="width:90px">状态</th>
              <th style="width:130px">提交时间</th>
              <th class="text-right" style="width:170px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="reviews.loading"><td colspan="9" class="py-12 text-center text-ink-faint">加载中…</td></tr>
            <tr v-for="r in reviews.list" v-else :key="r.id" class="cursor-pointer hover:bg-canvas-warm" @click="openReview(r)">
              <td @click.stop>
                <input type="checkbox" class="h-3.5 w-3.5 rounded accent-gold" :checked="reviews.selectedIds.includes(r.id)" @change="toggleSelect(r.id)" />
              </td>
              <td>
                <div class="flex items-center gap-2.5">
                  <span class="max-w-[180px] truncate text-ink">{{ r.productName || `#${r.productId}` }}</span>
                </div>
              </td>
              <td class="whitespace-nowrap text-ink-soft">{{ r.customerName || '—' }}</td>
              <td>
                <span class="flex items-center gap-0.5">
                  <component :is="i <= r.rating ? StarSolid : StarIcon" v-for="i in 5" :key="i" class="h-3.5 w-3.5" :class="i <= r.rating ? 'text-gold' : 'text-line'" />
                </span>
              </td>
              <td class="max-w-[220px] truncate text-ink-soft">{{ r.content }}</td>
              <td>
                <span v-if="r.images.length" class="inline-flex items-center gap-1 text-[12px] text-ink-soft">
                  <PhotoIcon class="h-4 w-4 text-ink-faint" />{{ r.images.length }}
                  <span v-if="r.images.some((i) => i.rejected)" class="text-[10px] text-danger">驳{{ r.images.filter((i) => i.rejected).length }}</span>
                </span>
                <span v-else class="text-[12px] text-ink-faint">—</span>
              </td>
              <td><StatusBadge :tone="reviewBadge(r).tone" :label="reviewBadge(r).label" /></td>
              <td class="whitespace-nowrap text-[12px] text-ink-faint">{{ formatDateTime(r.submittedAt) }}</td>
              <td @click.stop>
                <div class="flex items-center justify-end gap-1">
                  <template v-if="r.status === ReviewModerationStatus.PENDING">
                    <button class="btn-ghost text-ok" @click="approveReview(r)"><CheckIcon class="h-4 w-4" />通过</button>
                    <button class="btn-danger-ghost" @click="rejectReview(r)"><NoSymbolIcon class="h-4 w-4" />拒绝</button>
                  </template>
                  <template v-else-if="r.status === ReviewModerationStatus.APPROVED">
                    <button v-if="!r.featured" class="btn-ghost text-gold-deep" @click="setFeatured(r, true)"><SparklesIcon class="h-4 w-4" />设为精选</button>
                    <button v-else class="btn-ghost" @click="setFeatured(r, false)"><SparklesIcon class="h-4 w-4" />取消精选</button>
                  </template>
                  <span v-else class="text-[12px] text-ink-faint">已处理</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="!reviews.loading && reviews.list.length === 0" title="暂无匹配的评价" hint="尝试调整状态、星级或搜索条件" />
        <Pagination v-else :total="reviews.totalElements" :page="reviews.page" :per-page="reviews.pageSize" @change="(p) => reviews.setPage(p)" />
      </div>
    </template>

    <!-- ===================== Tab 2 Q&A 管理 ===================== -->
    <template v-else>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="c in qaChips"
            :key="c.key"
            class="rounded-full border px-3 py-1 text-[12px] transition-colors"
            :class="questions.answered === c.key ? 'border-gold bg-gold/10 font-medium text-gold-deep' : 'border-line text-ink-soft hover:bg-canvas-warm'"
            @click="selectQaChip(c.key)"
          >{{ c.label }}<template v-if="c.count != null"> · {{ c.count }}</template></button>
        </div>
        <div class="relative" title="当前页过滤（提问搜索为本页内存过滤）">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="questions.search" class="field w-56 pl-9" placeholder="搜索商品 / 提问人 / 内容…（当前页）" />
        </div>
        <span class="ml-auto text-[12px] text-ink-faint">共 {{ questions.totalElements }} 条</span>
      </div>

      <div class="panel overflow-hidden">
        <table class="data-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>提问内容</th>
              <th style="width:120px">提问人</th>
              <th style="width:130px">提问时间</th>
              <th style="width:90px">回答状态</th>
              <th style="width:90px" class="text-center">前台可见</th>
              <th class="text-right" style="width:100px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="questions.loading"><td colspan="7" class="py-12 text-center text-ink-faint">加载中…</td></tr>
            <tr v-for="q in filteredQa" v-else :key="q.id" class="cursor-pointer hover:bg-canvas-warm" @click="openQa(q)">
              <td><span class="max-w-[180px] truncate text-ink">{{ q.productName || `#${q.productId}` }}</span></td>
              <td class="max-w-[280px] truncate text-ink-soft">{{ q.question }}</td>
              <td class="whitespace-nowrap text-ink-soft">{{ q.asker || '—' }}</td>
              <td class="whitespace-nowrap text-[12px] text-ink-faint">{{ formatDateTime(q.askedAt) }}</td>
              <td><StatusBadge :tone="q.answer ? 'ok' : 'warn'" :label="q.answer ? '已回答' : '待回答'" /></td>
              <td class="text-center" @click.stop>
                <Toggle :model-value="q.visible === QuestionVisible.VISIBLE" @update:model-value="toggleQaVisible(q, $event)" />
              </td>
              <td class="text-right" @click.stop>
                <button class="btn-ghost" @click="openQa(q)">
                  <ChatBubbleLeftRightIcon class="h-4 w-4" />{{ q.answer ? '编辑回答' : '回答' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="!questions.loading && filteredQa.length === 0" title="暂无匹配的提问" hint="尝试调整回答状态或搜索条件" />
        <Pagination v-else :total="questions.totalElements" :page="questions.page" :per-page="questions.pageSize" @change="(p) => questions.setPage(p)" />
      </div>
    </template>

    <!-- ===================== 评价详情抽屉 ===================== -->
    <Teleport to="body">
      <div v-if="showReviewDrawer" class="fixed inset-0 z-50 flex justify-end bg-ink/20" v-dismiss="() => (showReviewDrawer = false)">
        <div class="h-full w-full max-w-lg overflow-y-auto border-l border-line bg-white shadow-2xl">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-white px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">评价详情</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="showReviewDrawer = false">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div v-if="detailReview" class="p-6">
            <!-- 商品 + 买家信息（customer_name 后台不脱敏） -->
            <div class="flex items-center gap-3 rounded-xl bg-canvas-warm p-4">
              <div class="min-w-0 flex-1">
                <p class="truncate text-[13px] font-medium text-ink">{{ detailReview.productName || `#${detailReview.productId}` }}</p>
                <p class="mt-0.5 text-[12px] text-ink-faint">{{ detailReview.customerName || '—' }} · {{ formatDateTime(detailReview.submittedAt) }}</p>
                <span class="mt-1 flex items-center gap-0.5">
                  <component :is="i <= detailReview.rating ? StarSolid : StarIcon" v-for="i in 5" :key="i" class="h-4 w-4" :class="i <= detailReview.rating ? 'text-gold' : 'text-line'" />
                </span>
              </div>
              <StatusBadge :tone="reviewBadge(detailReview).tone" :label="reviewBadge(detailReview).label" />
            </div>

            <!-- 审核操作 -->
            <div v-if="detailReview.status === ReviewModerationStatus.PENDING" class="mt-4 flex gap-2">
              <button class="btn-primary flex-1 justify-center" @click="approveReview(detailReview)"><CheckIcon class="h-4 w-4" />通过审核</button>
              <button class="btn-danger-ghost flex-1 justify-center" @click="rejectReview(detailReview)"><NoSymbolIcon class="h-4 w-4" />拒绝</button>
            </div>
            <div v-else-if="detailReview.status === ReviewModerationStatus.APPROVED" class="mt-4">
              <button v-if="!detailReview.featured" class="btn-outline w-full justify-center" @click="setFeatured(detailReview, true)"><SparklesIcon class="h-4 w-4" />设为精选 · 前台置顶展示</button>
              <button v-else class="btn-ghost w-full justify-center" @click="setFeatured(detailReview, false)"><SparklesIcon class="h-4 w-4" />取消精选</button>
            </div>

            <!-- 完整评价内容 -->
            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">评价内容</h4>
              <p class="rounded-lg border border-line p-4 text-[13px] leading-relaxed text-ink-soft">{{ detailReview.content }}</p>
            </div>

            <!-- 买家秀图片 -->
            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">买家秀图片（{{ detailReview.images.length }}）</h4>
              <div v-if="detailReview.images.length" class="grid grid-cols-3 gap-2">
                <button
                  v-for="(img, i) in detailReview.images"
                  :key="img.id"
                  class="group relative aspect-[3/4] overflow-hidden rounded-lg border border-line"
                  @click="openLightbox(detailReview, i)"
                >
                  <img :src="img.url" class="h-full w-full object-cover transition-transform group-hover:scale-105" :class="img.rejected ? 'grayscale opacity-50' : ''" />
                  <span v-if="img.rejected" class="absolute inset-x-0 bottom-0 bg-danger/85 py-1 text-center text-[10px] font-medium text-white">已驳回</span>
                </button>
              </div>
              <p v-else class="rounded-lg border border-dashed border-line py-6 text-center text-[12px] text-ink-faint">该评价未上传图片</p>
              <p v-if="detailReview.images.length" class="mt-2 text-[11px] text-ink-faint">点击图片可放大查看，并可单独驳回违规图片</p>
            </div>

            <!-- 官方回复（status≠approved → 占位 js_guard，409804 兜底） -->
            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">官方回复</h4>
              <template v-if="detailReview.status !== ReviewModerationStatus.APPROVED">
                <p class="rounded-lg border border-dashed border-line py-6 text-center text-[12px] text-ink-faint">评价通过审核后才可追加官方回复</p>
              </template>
              <template v-else-if="detailReview.replyContent && !replyEditing">
                <div class="rounded-xl border border-gold/30 bg-gold/6 p-4">
                  <div class="flex items-center gap-2">
                    <span class="flex h-7 w-7 items-center justify-center rounded-full bg-gold font-display text-[12px] font-semibold text-white">D</span>
                    <span class="text-[13px] font-medium text-gold-deep">{{ detailReview.replyAuthor || 'Dreamy Team' }}</span>
                    <span class="ml-auto text-[11px] text-ink-faint">{{ formatDateTime(detailReview.replyTime) }}</span>
                  </div>
                  <p class="mt-2.5 text-[13px] leading-relaxed text-ink-soft">{{ detailReview.replyContent }}</p>
                  <div class="mt-3 flex items-center gap-1 border-t border-gold/20 pt-3">
                    <button class="btn-ghost" @click="replyEditing = true; replyDraft = detailReview.replyContent || ''"><PencilSquareIcon class="h-4 w-4" />编辑</button>
                    <button class="btn-danger-ghost" @click="confirmDeleteReply = true"><TrashIcon class="h-4 w-4" />删除</button>
                  </div>
                </div>
              </template>
              <template v-else>
                <textarea
                  v-model="replyDraft"
                  rows="4"
                  class="field w-full resize-none leading-relaxed"
                  placeholder="以 Dreamy Team 名义回复买家，将在前台评价区展示…"
                ></textarea>
                <div class="mt-2 flex items-center justify-between">
                  <span class="text-[11px] text-ink-faint">回复将以 "Dreamy Team" 署名公开展示</span>
                  <div class="flex gap-2">
                    <button v-if="replyEditing" class="btn-ghost" @click="replyEditing = false; replyDraft = detailReview.replyContent || ''">取消</button>
                    <button class="btn-primary" :disabled="!replyDraft.trim()" @click="saveReply"><ChatBubbleLeftRightIcon class="h-4 w-4" />{{ replyEditing ? '保存修改' : '发布回复' }}</button>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===================== 图片 Lightbox ===================== -->
    <Teleport to="body">
      <div v-if="lightbox && lightboxImage" class="fixed inset-0 z-[60] flex flex-col items-center justify-center bg-ink/85 p-6" v-dismiss="() => (lightbox = null)">
        <button class="absolute right-5 top-5 rounded-full bg-white/10 p-2 text-white hover:bg-white/20" @click="lightbox = null">
          <XMarkIcon class="h-5 w-5" />
        </button>
        <div class="relative max-h-[78vh] overflow-hidden rounded-xl">
          <img :src="lightboxImage.url" class="max-h-[78vh] w-auto object-contain" :class="lightboxImage.rejected ? 'grayscale opacity-60' : ''" />
          <span v-if="lightboxImage.rejected" class="absolute left-3 top-3 rounded-full bg-danger px-3 py-1 text-[12px] font-medium text-white">已驳回 · 前台不展示</span>
        </div>
        <div class="mt-4 flex items-center gap-3">
          <span class="text-[12px] text-white/70">{{ lightbox.index + 1 }} / {{ lightbox.review.images.length }} · {{ lightbox.review.customerName || '买家' }} 的买家秀</span>
          <button
            v-if="!lightboxImage.rejected"
            class="inline-flex items-center gap-1.5 rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/85"
            @click="toggleLightboxImage(true)"
          ><NoSymbolIcon class="h-4 w-4" />驳回此图</button>
          <button
            v-else
            class="inline-flex items-center gap-1.5 rounded-luxe bg-white/15 px-4 py-2 text-[13px] font-medium text-white hover:bg-white/25"
            @click="toggleLightboxImage(false)"
          ><ArrowUturnLeftIcon class="h-4 w-4" />恢复展示</button>
        </div>
      </div>
    </Teleport>

    <!-- ===================== Q&A 详情抽屉 ===================== -->
    <Teleport to="body">
      <div v-if="showQaDrawer" class="fixed inset-0 z-50 flex justify-end bg-ink/20" v-dismiss="() => (showQaDrawer = false)">
        <div class="h-full w-full max-w-lg overflow-y-auto border-l border-line bg-white shadow-2xl">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-white px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">问答详情</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="showQaDrawer = false">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div v-if="detailQa" class="p-6">
            <div class="flex items-center gap-3 rounded-xl bg-canvas-warm p-4">
              <div class="min-w-0 flex-1">
                <p class="truncate text-[13px] font-medium text-ink">{{ detailQa.productName || `#${detailQa.productId}` }}</p>
                <p class="mt-0.5 text-[12px] text-ink-faint">{{ detailQa.asker || '—' }} · {{ formatDateTime(detailQa.askedAt) }}</p>
              </div>
              <div class="flex flex-col items-end gap-1.5">
                <StatusBadge :tone="detailQa.answer ? 'ok' : 'warn'" :label="detailQa.answer ? '已回答' : '待回答'" />
                <div class="flex items-center gap-1.5 text-[11px] text-ink-faint">
                  前台可见 <Toggle :model-value="detailQa.visible === QuestionVisible.VISIBLE" @update:model-value="toggleQaVisible(detailQa, $event)" />
                </div>
              </div>
            </div>

            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">买家提问</h4>
              <p class="rounded-lg border border-line p-4 text-[13px] leading-relaxed text-ink-soft">{{ detailQa.question }}</p>
            </div>

            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">官方回答</h4>
              <template v-if="detailQa.answer && !answerEditing">
                <div class="rounded-xl border border-gold/30 bg-gold/6 p-4">
                  <div class="flex items-center gap-2">
                    <span class="flex h-7 w-7 items-center justify-center rounded-full bg-gold font-display text-[12px] font-semibold text-white">D</span>
                    <span class="text-[13px] font-medium text-gold-deep">Dreamy Team</span>
                    <span class="ml-auto text-[11px] text-ink-faint">{{ formatDateTime(detailQa.answerTime) }}</span>
                  </div>
                  <p class="mt-2.5 text-[13px] leading-relaxed text-ink-soft">{{ detailQa.answer }}</p>
                  <div class="mt-3 border-t border-gold/20 pt-3">
                    <button class="btn-ghost" @click="answerEditing = true; answerDraft = detailQa.answer || ''"><PencilSquareIcon class="h-4 w-4" />编辑回答</button>
                  </div>
                </div>
              </template>
              <template v-else>
                <textarea
                  v-model="answerDraft"
                  rows="4"
                  class="field w-full resize-none leading-relaxed"
                  placeholder="以 Dreamy Team 名义回答买家提问，将在前台商品页 Q&A 区展示…"
                ></textarea>
                <div class="mt-2 flex items-center justify-between">
                  <span class="text-[11px] text-ink-faint">回答将以 "Dreamy Team" 署名公开展示；首次回答自动上线可见</span>
                  <div class="flex gap-2">
                    <button v-if="answerEditing" class="btn-ghost" @click="answerEditing = false; answerDraft = detailQa.answer || ''">取消</button>
                    <button class="btn-primary" :disabled="!answerDraft.trim()" @click="saveAnswer"><ChatBubbleLeftRightIcon class="h-4 w-4" />{{ answerEditing ? '保存修改' : '发布回答' }}</button>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="confirmDeleteReply"
      title="删除官方回复"
      message="确认删除该官方回复？前台将不再展示。"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="deleteReply"
      @cancel="confirmDeleteReply = false"
    />
  </div>
</template>
