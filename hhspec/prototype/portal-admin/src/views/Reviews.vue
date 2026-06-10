<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import Toggle from '@/components/Toggle.vue'
import { reviews as reviewsSeed, productQuestions as questionsSeed, products } from '@/data/mock'
import { StarIcon as StarSolid } from '@heroicons/vue/24/solid'
import {
  StarIcon, MagnifyingGlassIcon, XMarkIcon, CheckIcon, NoSymbolIcon, SparklesIcon,
  PhotoIcon, ChatBubbleLeftRightIcon, PencilSquareIcon, TrashIcon, ArrowUturnLeftIcon
} from '@heroicons/vue/24/outline'

const productById = Object.fromEntries(products.map((p) => [p.id, p]))

// 本地副本：原型内操作不污染共享 mock
const reviewList = ref(reviewsSeed.map((r) => ({
  ...r,
  images: r.images.map((img) => ({ ...img })),
  reply: r.reply ? { ...r.reply } : null
})))
const qaList = ref(questionsSeed.map((q) => ({ ...q })))

const activeTab = ref('reviews')
const mainTabs = [
  ['reviews', '评价审核'],
  ['qa', 'Q&A 管理']
]

// toast
const toast = ref('')
let toastTimer = null
function showToast(msg) {
  toast.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2400)
}

function now() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/* ===================== Tab 1 · 评价审核 ===================== */

const reviewStatus = ref('all')
const reviewRating = ref('all')
const reviewQuery = ref('')
const selectedIds = ref([])

const statusChips = computed(() => [
  ['all', '全部', reviewList.value.length],
  ['pending', '待审核', reviewList.value.filter((r) => r.status === 'pending').length],
  ['approved', '已通过', reviewList.value.filter((r) => r.status === 'approved').length],
  ['featured', '精选', reviewList.value.filter((r) => r.featured).length],
  ['rejected', '已拒绝', reviewList.value.filter((r) => r.status === 'rejected').length]
])

const filteredReviews = computed(() => reviewList.value.filter((r) => {
  if (reviewStatus.value === 'featured') {
    if (!r.featured) return false
  } else if (reviewStatus.value !== 'all' && r.status !== reviewStatus.value) {
    return false
  }
  if (reviewRating.value !== 'all' && r.rating !== Number(reviewRating.value)) return false
  if (reviewQuery.value.trim()) {
    const q = reviewQuery.value.trim().toLowerCase()
    const p = productById[r.productId]
    if (!(p?.name.toLowerCase().includes(q) || r.customer.toLowerCase().includes(q))) return false
  }
  return true
}))

function reviewBadge(r) {
  if (r.status === 'pending') return { tone: 'warn', label: '待审核' }
  if (r.status === 'rejected') return { tone: 'danger', label: '已拒绝' }
  return r.featured ? { tone: 'info', label: '精选' } : { tone: 'ok', label: '已通过' }
}

// 勾选与批量
const allChecked = computed(() => filteredReviews.value.length > 0 && filteredReviews.value.every((r) => selectedIds.value.includes(r.id)))
function toggleAll() {
  if (allChecked.value) selectedIds.value = []
  else selectedIds.value = filteredReviews.value.map((r) => r.id)
}
function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}
function batchSet(status) {
  let count = 0
  for (const r of reviewList.value) {
    if (selectedIds.value.includes(r.id) && r.status !== status) {
      r.status = status
      if (status !== 'approved') r.featured = false
      count++
    }
  }
  selectedIds.value = []
  showToast(status === 'approved' ? `已批量通过 ${count} 条评价` : `已批量拒绝 ${count} 条评价`)
}

// 单条状态流转
function approveReview(r) {
  r.status = 'approved'
  showToast(`评价 ${r.id} 已通过，已记入操作日志`)
}
function rejectReview(r) {
  r.status = 'rejected'
  r.featured = false
  showToast(`评价 ${r.id} 已拒绝，已记入操作日志`)
}
function setFeatured(r, val) {
  r.featured = val
  showToast(val ? '已设为精选，将在前台评价区置顶' : '已取消精选')
}

// 详情抽屉
const showReviewDrawer = ref(false)
const detailReview = ref(null)
const replyDraft = ref('')
const replyEditing = ref(false)

function openReview(r) {
  detailReview.value = r
  replyDraft.value = r.reply?.content || ''
  replyEditing.value = false
  showReviewDrawer.value = true
}
function saveReply() {
  if (!replyDraft.value.trim()) return
  detailReview.value.reply = { author: 'Dreamy Team', content: replyDraft.value.trim(), time: now() }
  replyEditing.value = false
  showToast('官方回复已保存，将以 Dreamy Team 署名展示')
}
function deleteReply() {
  detailReview.value.reply = null
  replyDraft.value = ''
  replyEditing.value = false
  showToast('官方回复已删除')
}

// Lightbox（A-070 带图审核）
const lightbox = ref(null) // { review, index }
function openLightbox(r, i) {
  lightbox.value = { review: r, index: i }
}
const lightboxImage = computed(() => lightbox.value ? lightbox.value.review.images[lightbox.value.index] : null)
function rejectImage() {
  lightboxImage.value.rejected = true
  showToast('已驳回该图片，前台将不再展示')
}
function restoreImage() {
  lightboxImage.value.rejected = false
  showToast('已恢复展示该图片')
}

/* ===================== Tab 2 · Q&A 管理 ===================== */

const qaStatus = ref('all')
const qaQuery = ref('')
const qaChips = computed(() => [
  ['all', '全部', qaList.value.length],
  ['unanswered', '待回答', qaList.value.filter((q) => !q.answer).length],
  ['answered', '已回答', qaList.value.filter((q) => q.answer).length]
])

const filteredQa = computed(() => qaList.value.filter((q) => {
  if (qaStatus.value === 'unanswered' && q.answer) return false
  if (qaStatus.value === 'answered' && !q.answer) return false
  if (qaQuery.value.trim()) {
    const s = qaQuery.value.trim().toLowerCase()
    const p = productById[q.productId]
    if (!(p?.name.toLowerCase().includes(s) || q.asker.toLowerCase().includes(s) || q.question.toLowerCase().includes(s))) return false
  }
  return true
}))

function toggleQaVisible(q, val) {
  q.visible = val
  showToast(val ? '该问答已上线，前台可见' : '该问答已隐藏')
}

const showQaDrawer = ref(false)
const detailQa = ref(null)
const answerDraft = ref('')
const answerEditing = ref(false)

function openQa(q) {
  detailQa.value = q
  answerDraft.value = q.answer || ''
  answerEditing.value = false
  showQaDrawer.value = true
}
function saveAnswer() {
  if (!answerDraft.value.trim()) return
  detailQa.value.answer = answerDraft.value.trim()
  detailQa.value.answerTime = now()
  answerEditing.value = false
  showToast('官方回答已保存，已记入操作日志')
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Content · UGC" title="评价与 Q&A" subtitle="审核买家评价与提问，管理精选、官方回复与前台可见性">
      <template #actions>
        <span class="rounded-full bg-warn/14 px-3 py-1 text-[12px] text-warn">{{ statusChips[1][2] }} 条评价待审核</span>
        <span class="rounded-full bg-info/12 px-3 py-1 text-[12px] text-info">{{ qaChips[1][2] }} 个提问待回答</span>
      </template>
    </PageHeader>

    <!-- 主 Tab -->
    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in mainTabs" :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="activeTab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="activeTab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- ===================== Tab 1 评价审核 ===================== -->
    <template v-if="activeTab === 'reviews'">
      <!-- 筛选栏 -->
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="c in statusChips" :key="c[0]"
            class="rounded-full border px-3 py-1 text-[12px] transition-colors"
            :class="reviewStatus === c[0] ? 'border-gold bg-gold/10 font-medium text-gold-deep' : 'border-line text-ink-soft hover:bg-canvas-warm'"
            @click="reviewStatus = c[0]"
          >{{ c[1] }} · {{ c[2] }}</button>
        </div>
        <select v-model="reviewRating" class="field" style="min-width:120px">
          <option value="all">全部星级</option>
          <option v-for="n in [5,4,3,2,1]" :key="n" :value="String(n)">{{ n }} 星</option>
        </select>
        <div class="relative">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="reviewQuery" class="field w-56 pl-9" placeholder="搜索商品 / 买家…" />
        </div>
        <span class="ml-auto text-[12px] text-ink-faint">共 {{ filteredReviews.length }} 条</span>
      </div>

      <!-- 批量操作条 -->
      <div v-if="selectedIds.length" class="mb-3 flex items-center gap-3 rounded-luxe border border-gold/40 bg-gold/8 px-4 py-2.5">
        <span class="text-[13px] text-ink">已选 {{ selectedIds.length }} 条评价</span>
        <button class="btn-ghost text-ok" @click="batchSet('approved')"><CheckIcon class="h-4 w-4" />批量通过</button>
        <button class="btn-danger-ghost" @click="batchSet('rejected')"><NoSymbolIcon class="h-4 w-4" />批量拒绝</button>
        <button class="ml-auto text-[12px] text-ink-faint hover:text-ink" @click="selectedIds = []">取消选择</button>
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
            <tr v-for="r in filteredReviews" :key="r.id" class="cursor-pointer hover:bg-canvas-warm" @click="openReview(r)">
              <td @click.stop>
                <input type="checkbox" class="h-3.5 w-3.5 rounded accent-gold" :checked="selectedIds.includes(r.id)" @change="toggleSelect(r.id)" />
              </td>
              <td>
                <div class="flex items-center gap-2.5">
                  <img :src="productById[r.productId]?.img" class="h-10 w-8 shrink-0 rounded object-cover" />
                  <span class="max-w-[150px] truncate text-ink">{{ productById[r.productId]?.name }}</span>
                </div>
              </td>
              <td class="text-ink-soft whitespace-nowrap">{{ r.customer }}</td>
              <td>
                <span class="flex items-center gap-0.5">
                  <component v-for="i in 5" :key="i" :is="i <= r.rating ? StarSolid : StarIcon" class="h-3.5 w-3.5" :class="i <= r.rating ? 'text-gold' : 'text-line'" />
                </span>
              </td>
              <td class="max-w-[220px] truncate text-ink-soft">{{ r.content }}</td>
              <td>
                <span v-if="r.images.length" class="inline-flex items-center gap-1 text-[12px] text-ink-soft">
                  <PhotoIcon class="h-4 w-4 text-ink-faint" />{{ r.images.length }}
                  <span v-if="r.images.some(i => i.rejected)" class="text-[10px] text-danger">驳{{ r.images.filter(i => i.rejected).length }}</span>
                </span>
                <span v-else class="text-[12px] text-ink-faint">—</span>
              </td>
              <td><StatusBadge :tone="reviewBadge(r).tone" :label="reviewBadge(r).label" /></td>
              <td class="text-[12px] text-ink-faint whitespace-nowrap">{{ r.date }}</td>
              <td @click.stop>
                <div class="flex items-center justify-end gap-1">
                  <template v-if="r.status === 'pending'">
                    <button class="btn-ghost text-ok" @click="approveReview(r)"><CheckIcon class="h-4 w-4" />通过</button>
                    <button class="btn-danger-ghost" @click="rejectReview(r)"><NoSymbolIcon class="h-4 w-4" />拒绝</button>
                  </template>
                  <template v-else-if="r.status === 'approved'">
                    <button v-if="!r.featured" class="btn-ghost text-gold-deep" @click="setFeatured(r, true)"><SparklesIcon class="h-4 w-4" />设为精选</button>
                    <button v-else class="btn-ghost" @click="setFeatured(r, false)"><SparklesIcon class="h-4 w-4" />取消精选</button>
                  </template>
                  <span v-else class="text-[12px] text-ink-faint">已处理</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="filteredReviews.length === 0" title="暂无匹配的评价" hint="尝试调整状态、星级或搜索条件" />
        <Pagination v-else :total="filteredReviews.length" :per-page="10" />
      </div>
    </template>

    <!-- ===================== Tab 2 Q&A 管理 ===================== -->
    <template v-else>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="c in qaChips" :key="c[0]"
            class="rounded-full border px-3 py-1 text-[12px] transition-colors"
            :class="qaStatus === c[0] ? 'border-gold bg-gold/10 font-medium text-gold-deep' : 'border-line text-ink-soft hover:bg-canvas-warm'"
            @click="qaStatus = c[0]"
          >{{ c[1] }} · {{ c[2] }}</button>
        </div>
        <div class="relative">
          <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
          <input v-model="qaQuery" class="field w-56 pl-9" placeholder="搜索商品 / 提问人 / 内容…" />
        </div>
        <span class="ml-auto text-[12px] text-ink-faint">共 {{ filteredQa.length }} 条</span>
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
            <tr v-for="q in filteredQa" :key="q.id" class="cursor-pointer hover:bg-canvas-warm" @click="openQa(q)">
              <td>
                <div class="flex items-center gap-2.5">
                  <img :src="productById[q.productId]?.img" class="h-10 w-8 shrink-0 rounded object-cover" />
                  <span class="max-w-[150px] truncate text-ink">{{ productById[q.productId]?.name }}</span>
                </div>
              </td>
              <td class="max-w-[280px] truncate text-ink-soft">{{ q.question }}</td>
              <td class="text-ink-soft whitespace-nowrap">{{ q.asker }}</td>
              <td class="text-[12px] text-ink-faint whitespace-nowrap">{{ q.date }}</td>
              <td><StatusBadge :tone="q.answer ? 'ok' : 'warn'" :label="q.answer ? '已回答' : '待回答'" /></td>
              <td class="text-center" @click.stop>
                <Toggle :model-value="q.visible" @update:model-value="toggleQaVisible(q, $event)" />
              </td>
              <td class="text-right" @click.stop>
                <button class="btn-ghost" @click="openQa(q)">
                  <ChatBubbleLeftRightIcon class="h-4 w-4" />{{ q.answer ? '编辑回答' : '回答' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="filteredQa.length === 0" title="暂无匹配的提问" hint="尝试调整回答状态或搜索条件" />
        <Pagination v-else :total="filteredQa.length" :per-page="10" />
      </div>
    </template>

    <!-- ===================== 评价详情抽屉 ===================== -->
    <Teleport to="body">
      <div v-if="showReviewDrawer" class="fixed inset-0 z-50 flex justify-end bg-ink/20" @click.self="showReviewDrawer = false">
        <div class="h-full w-full max-w-lg overflow-y-auto border-l border-line bg-white shadow-2xl">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-white px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">评价详情</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="showReviewDrawer = false">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div v-if="detailReview" class="p-6">
            <!-- 商品 + 买家信息 -->
            <div class="flex items-center gap-3 rounded-xl bg-canvas-warm p-4">
              <img :src="productById[detailReview.productId]?.img" class="h-16 w-12 shrink-0 rounded-lg object-cover" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-[13px] font-medium text-ink">{{ productById[detailReview.productId]?.name }}</p>
                <p class="mt-0.5 text-[12px] text-ink-faint">{{ detailReview.customer }} · {{ detailReview.date }}</p>
                <span class="mt-1 flex items-center gap-0.5">
                  <component v-for="i in 5" :key="i" :is="i <= detailReview.rating ? StarSolid : StarIcon" class="h-4 w-4" :class="i <= detailReview.rating ? 'text-gold' : 'text-line'" />
                </span>
              </div>
              <StatusBadge :tone="reviewBadge(detailReview).tone" :label="reviewBadge(detailReview).label" />
            </div>

            <!-- 审核操作 -->
            <div v-if="detailReview.status === 'pending'" class="mt-4 flex gap-2">
              <button class="btn-primary flex-1 justify-center" @click="approveReview(detailReview)"><CheckIcon class="h-4 w-4" />通过审核</button>
              <button class="btn-danger-ghost flex-1 justify-center" @click="rejectReview(detailReview)"><NoSymbolIcon class="h-4 w-4" />拒绝</button>
            </div>
            <div v-else-if="detailReview.status === 'approved'" class="mt-4">
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
                  v-for="(img, i) in detailReview.images" :key="i"
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

            <!-- 官方回复 -->
            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">官方回复</h4>
              <template v-if="detailReview.status !== 'approved'">
                <p class="rounded-lg border border-dashed border-line py-6 text-center text-[12px] text-ink-faint">评价通过审核后才可追加官方回复</p>
              </template>
              <template v-else-if="detailReview.reply && !replyEditing">
                <div class="rounded-xl border border-gold/30 bg-gold/6 p-4">
                  <div class="flex items-center gap-2">
                    <span class="flex h-7 w-7 items-center justify-center rounded-full bg-gold font-display text-[12px] font-semibold text-white">D</span>
                    <span class="text-[13px] font-medium text-gold-deep">Dreamy Team</span>
                    <span class="ml-auto text-[11px] text-ink-faint">{{ detailReview.reply.time }}</span>
                  </div>
                  <p class="mt-2.5 text-[13px] leading-relaxed text-ink-soft">{{ detailReview.reply.content }}</p>
                  <div class="mt-3 flex items-center gap-1 border-t border-gold/20 pt-3">
                    <button class="btn-ghost" @click="replyEditing = true; replyDraft = detailReview.reply.content"><PencilSquareIcon class="h-4 w-4" />编辑</button>
                    <button class="btn-danger-ghost" @click="deleteReply"><TrashIcon class="h-4 w-4" />删除</button>
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
                    <button v-if="replyEditing" class="btn-ghost" @click="replyEditing = false; replyDraft = detailReview.reply?.content || ''">取消</button>
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
      <div v-if="lightbox" class="fixed inset-0 z-[60] flex flex-col items-center justify-center bg-ink/85 p-6" @click.self="lightbox = null">
        <button class="absolute right-5 top-5 rounded-full bg-white/10 p-2 text-white hover:bg-white/20" @click="lightbox = null">
          <XMarkIcon class="h-5 w-5" />
        </button>
        <div class="relative max-h-[78vh] overflow-hidden rounded-xl">
          <img :src="lightboxImage.url" class="max-h-[78vh] w-auto object-contain" :class="lightboxImage.rejected ? 'grayscale opacity-60' : ''" />
          <span v-if="lightboxImage.rejected" class="absolute left-3 top-3 rounded-full bg-danger px-3 py-1 text-[12px] font-medium text-white">已驳回 · 前台不展示</span>
        </div>
        <div class="mt-4 flex items-center gap-3">
          <span class="text-[12px] text-white/70">{{ lightbox.index + 1 }} / {{ lightbox.review.images.length }} · {{ lightbox.review.customer }} 的买家秀</span>
          <button
            v-if="!lightboxImage.rejected"
            class="inline-flex items-center gap-1.5 rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/85"
            @click="rejectImage"
          ><NoSymbolIcon class="h-4 w-4" />驳回此图</button>
          <button
            v-else
            class="inline-flex items-center gap-1.5 rounded-luxe bg-white/15 px-4 py-2 text-[13px] font-medium text-white hover:bg-white/25"
            @click="restoreImage"
          ><ArrowUturnLeftIcon class="h-4 w-4" />恢复展示</button>
        </div>
      </div>
    </Teleport>

    <!-- ===================== Q&A 详情抽屉 ===================== -->
    <Teleport to="body">
      <div v-if="showQaDrawer" class="fixed inset-0 z-50 flex justify-end bg-ink/20" @click.self="showQaDrawer = false">
        <div class="h-full w-full max-w-lg overflow-y-auto border-l border-line bg-white shadow-2xl">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-white px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">问答详情</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="showQaDrawer = false">
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div v-if="detailQa" class="p-6">
            <!-- 商品 -->
            <div class="flex items-center gap-3 rounded-xl bg-canvas-warm p-4">
              <img :src="productById[detailQa.productId]?.img" class="h-16 w-12 shrink-0 rounded-lg object-cover" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-[13px] font-medium text-ink">{{ productById[detailQa.productId]?.name }}</p>
                <p class="mt-0.5 text-[12px] text-ink-faint">{{ detailQa.asker }} · {{ detailQa.date }}</p>
              </div>
              <div class="flex flex-col items-end gap-1.5">
                <StatusBadge :tone="detailQa.answer ? 'ok' : 'warn'" :label="detailQa.answer ? '已回答' : '待回答'" />
                <div class="flex items-center gap-1.5 text-[11px] text-ink-faint">
                  前台可见 <Toggle :model-value="detailQa.visible" @update:model-value="toggleQaVisible(detailQa, $event)" />
                </div>
              </div>
            </div>

            <!-- 问题 -->
            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">买家提问</h4>
              <p class="rounded-lg border border-line p-4 text-[13px] leading-relaxed text-ink-soft">{{ detailQa.question }}</p>
            </div>

            <!-- 官方回答 -->
            <div class="mt-6">
              <h4 class="mb-2 text-[13px] font-medium text-ink">官方回答</h4>
              <template v-if="detailQa.answer && !answerEditing">
                <div class="rounded-xl border border-gold/30 bg-gold/6 p-4">
                  <div class="flex items-center gap-2">
                    <span class="flex h-7 w-7 items-center justify-center rounded-full bg-gold font-display text-[12px] font-semibold text-white">D</span>
                    <span class="text-[13px] font-medium text-gold-deep">Dreamy Team</span>
                    <span class="ml-auto text-[11px] text-ink-faint">{{ detailQa.answerTime }}</span>
                  </div>
                  <p class="mt-2.5 text-[13px] leading-relaxed text-ink-soft">{{ detailQa.answer }}</p>
                  <div class="mt-3 border-t border-gold/20 pt-3">
                    <button class="btn-ghost" @click="answerEditing = true; answerDraft = detailQa.answer"><PencilSquareIcon class="h-4 w-4" />编辑回答</button>
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
                  <span class="text-[11px] text-ink-faint">回答将以 "Dreamy Team" 署名公开展示</span>
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

    <!-- toast -->
    <Teleport to="body">
      <transition enter-active-class="transition duration-150" enter-from-class="opacity-0 translate-y-2" leave-active-class="transition duration-150" leave-to-class="opacity-0">
        <div v-if="toast" class="fixed bottom-6 left-1/2 z-[70] -translate-x-1/2 rounded-luxe bg-ink px-5 py-2.5 text-[13px] text-canvas shadow-2xl">
          {{ toast }}
        </div>
      </transition>
    </Teleport>
  </div>
</template>
