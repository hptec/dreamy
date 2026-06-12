<script setup lang="ts">
// PAGE-TRD-A03 / FORM-TRD-R01 / COMP-TRD-R01：退款工单（行内同意/拒绝审批——决策 7；已处理行展示退款/退货单号——ALIGN-025；登记退货单号弹窗保留——决策 31）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import { useRefundsStore } from '@/stores/refunds'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { currencySymbol, formatDateTime } from '@/utils/format'
import { CheckIcon, XMarkIcon, MagnifyingGlassIcon, TruckIcon } from '@heroicons/vue/24/outline'
import { RefundStatus } from '@/api/types'
import type { AdminRefund } from '@/api/types'

const store = useRefundsStore()
const toast = useToastStore()

const tabs = [
  ['all', '全部'],
  [RefundStatus.PENDING, '待审批'],
  [RefundStatus.APPROVED, '已同意'],
  [RefundStatus.REJECTED, '已拒绝'],
] as const
const tone: Record<number, string> = { [RefundStatus.PENDING]: 'warn', [RefundStatus.APPROVED]: 'ok', [RefundStatus.REJECTED]: 'danger' }
const label: Record<number, string> = { [RefundStatus.PENDING]: '待审批', [RefundStatus.APPROVED]: '已同意', [RefundStatus.REJECTED]: '已拒绝' }

function load() {
  store.fetchList().catch((e) => toast.error(e instanceof BizError ? e.message : '加载退款工单失败'))
}

function selectTab(k: RefundStatus | 'all') {
  store.status = k
  store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.applyFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
  }, 300)
}

function gotoPage(p: number) {
  store.setPage(p).catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

// ===== 行内审批状态机（FORM-TRD-R01 / ALIGN-024：决策 7 行内同意/拒绝，拒绝按钮原地展开原因输入）=====
const rejectingId = ref<number | null>(null) // 当前展开拒绝输入的工单 id（同一时刻仅一行展开）
const reasonDraft = ref('')
const inlineError = ref('')
const busyId = ref<number | null>(null)

/** 审批共用错误兜底：409604 已审工单 → toast + 行刷新；502601/504601 Stripe 不可用 → 工单保持待审批可重试 */
function handleApprovalError(e: unknown) {
  if (e instanceof BizError && e.code === 409604) {
    toast.error('工单已审核，不可重复操作')
    load()
  } else if (e instanceof BizError && (e.code === 502601 || e.code === 504601)) {
    toast.error('Stripe 暂不可用，工单保持待审批，可稍后重试')
  } else {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  }
}

/** 同意不弹窗（决策 7）；退货物流单号改为事后登记（见已同意行「登记退货单号」入口） */
async function approve(r: AdminRefund) {
  if (busyId.value !== null) return
  busyId.value = r.id
  try {
    await store.approve(r.id)
    toast.success('已同意退款，Stripe 退款已发起')
  } catch (e) {
    handleApprovalError(e)
  } finally {
    busyId.value = null
  }
}

function startReject(r: AdminRefund) {
  rejectingId.value = r.id
  reasonDraft.value = ''
  inlineError.value = ''
}

function cancelReject() {
  rejectingId.value = null
  reasonDraft.value = ''
  inlineError.value = ''
}

async function confirmReject(r: AdminRefund) {
  if (!reasonDraft.value.trim()) {
    inlineError.value = '拒绝原因必填' // 前端预判（后端必填约束不变，422 兜底）
    return
  }
  if (busyId.value !== null) return
  busyId.value = r.id
  inlineError.value = ''
  try {
    await store.reject(r.id, reasonDraft.value.trim())
    toast.success('已拒绝该工单')
    cancelReject()
  } catch (e) {
    if (e instanceof BizError && e.code === 409604) {
      toast.error('工单已审核，不可重复操作')
      cancelReject()
      load()
    } else {
      inlineError.value = e instanceof BizError ? e.message : '操作失败'
    }
  } finally {
    busyId.value = null
  }
}

// ===== 登记退货单号弹窗（决策 31 兼容：已同意未登记行事后补录，单字段表单弹窗，不属于审批二次确认范畴）=====
const trackModal = ref<AdminRefund | null>(null)
const trackingDraft = ref('')
const trackError = ref('')
const trackBusy = ref(false)

function openTrackModal(r: AdminRefund) {
  trackModal.value = r
  trackingDraft.value = r.returnTrackingNo || ''
  trackError.value = ''
}

async function submitTrack() {
  if (!trackModal.value) return
  if (!trackingDraft.value.trim()) {
    trackError.value = '退货物流单号必填'
    return
  }
  trackBusy.value = true
  try {
    await store.patchTracking(trackModal.value.id, trackingDraft.value.trim())
    toast.success('退货单号已登记')
    trackModal.value = null
  } catch (e) {
    trackError.value = e instanceof BizError ? e.message : '操作失败'
  } finally {
    trackBusy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="After-Sales" title="退款工单" subtitle="审批退款申请并记录处理日志" />

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in tabs"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="store.status === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="selectTab(t[0])"
      >{{ t[1] }}</button>
    </div>

    <!-- 新增搜索框（工单号/订单号/邮箱，沿用 Orders 搜索样式） -->
    <div class="panel mb-4 p-4">
      <div class="relative max-w-md">
        <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
        <input v-model="store.search" class="field pl-9" placeholder="搜索工单号 / 订单号 / 客户邮箱…" @input="onSearchInput" />
      </div>
    </div>

    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>工单号</th><th>关联订单</th><th>客户</th><th class="text-right">退款金额</th><th>退款原因</th><th>申请时间</th><th>状态</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="8" class="py-12 text-center text-ink-faint">加载中…</td></tr>
          <tr v-for="r in store.list" v-else :key="r.id">
            <td class="font-mono text-[12px] font-medium text-gold-deep">{{ r.refundNo }}</td>
            <td class="font-mono text-[12px] text-ink-soft">{{ r.orderNo }}</td>
            <td class="text-ink">{{ r.customerName || '—' }}<br /><span class="text-[11px] text-ink-faint">{{ r.customerEmail || '' }}</span></td>
            <td class="text-right font-medium text-ink">{{ currencySymbol(r.currency) }}{{ Number(r.amount).toLocaleString() }}</td>
            <td class="max-w-[200px] truncate text-ink-soft" :title="r.reason || ''">{{ r.reason || '—' }}</td>
            <td class="text-[12px] text-ink-faint">{{ formatDateTime(r.appliedAt) }}</td>
            <td><StatusBadge :tone="tone[r.status]" :label="label[r.status]" /></td>
            <td>
              <!-- FORM-TRD-R01 / ALIGN-024：行内审批（1:1 原型 L37-40） -->
              <template v-if="r.status === RefundStatus.PENDING">
                <div v-if="rejectingId !== r.id" class="flex items-center justify-end gap-1">
                  <button class="btn-ghost text-ok" :disabled="busyId === r.id" @click="approve(r)"><CheckIcon class="h-4 w-4" />同意</button>
                  <button class="btn-danger-ghost" :disabled="busyId === r.id" @click="startReject(r)"><XMarkIcon class="h-4 w-4" />拒绝</button>
                </div>
                <!-- 行内展开态：按钮位置原地替换为原因输入区（不弹层，行高自然增高） -->
                <template v-else>
                  <div class="flex items-center justify-end gap-1">
                    <input
                      v-model="reasonDraft"
                      class="field w-44"
                      maxlength="255"
                      placeholder="拒绝原因（必填，将向买家展示）"
                      autofocus
                      @keyup.enter="confirmReject(r)"
                      @keyup.esc="cancelReject"
                    />
                    <button class="btn-danger-ghost" :disabled="!reasonDraft.trim() || busyId === r.id" @click="confirmReject(r)">确认拒绝</button>
                    <button class="btn-ghost" @click="cancelReject">取消</button>
                  </div>
                  <p v-if="inlineError" class="mt-1 text-right text-[11px] text-danger">{{ inlineError }}</p>
                </template>
              </template>
              <!-- COMP-TRD-R01 / ALIGN-025：已处理 + 退款单号/退货单号 -->
              <div v-else class="text-right">
                <span class="text-[12px] text-ink-faint">已处理</span>
                <p v-if="r.status === RefundStatus.APPROVED && r.stripeRefundId" class="font-mono text-[11px] text-ink-faint">退款单号 {{ r.stripeRefundId }}</p>
                <p v-if="r.returnTrackingNo" class="font-mono text-[11px] text-ink-faint">退货单号 {{ r.returnTrackingNo }}</p>
                <!-- 决策 31：登记退货单号入口（已同意未登记，事后补录） -->
                <button v-if="r.status === RefundStatus.APPROVED && !r.returnTrackingNo" class="btn-ghost text-[12px]" @click="openTrackModal(r)">
                  <TruckIcon class="h-3.5 w-3.5" />登记退货单号
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!store.loading && store.list.length === 0" title="暂无退款工单" />
      <Pagination :total="store.total" :page="store.page" :per-page="store.pageSize" @change="gotoPage" />
    </div>

    <!-- 登记退货物流单号弹窗（决策 31：单字段补录表单，保留） -->
    <Teleport to="body">
      <div v-if="trackModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (trackModal = null)">
        <div class="panel w-[26rem] p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">
              登记退货物流单号
              <span class="ml-1 font-mono text-[12px] text-ink-faint">{{ trackModal.refundNo }}</span>
            </h3>
            <button class="btn-ghost" @click="trackModal = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">退货物流单号 *</label>
              <input v-model="trackingDraft" class="field" placeholder="买家退货物流单号" @keyup.enter="submitTrack" />
            </div>
            <p v-if="trackError" class="text-[11px] text-danger">{{ trackError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="trackModal = null">取消</button>
            <button class="btn-gold" :disabled="trackBusy" @click="submitTrack">{{ trackBusy ? '处理中…' : '保存' }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
