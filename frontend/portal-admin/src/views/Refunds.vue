<script setup lang="ts">
// PAGE-TRD-A03 / COMP-TRD-A06：退款工单（mock → listAdminRefunds；审批弹窗/拒绝原因/登记退货单号——决策 24/31）
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
import type { AdminRefund } from '@/api/types'

const store = useRefundsStore()
const toast = useToastStore()

const tabs = [
  ['all', '全部'],
  ['pending', '待审批'],
  ['approved', '已同意'],
  ['rejected', '已拒绝'],
] as const
const tone: Record<string, string> = { pending: 'warn', approved: 'ok', rejected: 'danger' }
const label: Record<string, string> = { pending: '待审批', approved: '已同意', rejected: '已拒绝' }

function load() {
  store.fetchList().catch((e) => toast.error(e instanceof BizError ? e.message : '加载退款工单失败'))
}

function selectTab(k: string) {
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

// ===== 审批弹窗（同意：选填退货物流单号；拒绝：原因必填；登记：补录单号）=====
type ModalKind = 'approve' | 'reject' | 'track'
const modal = ref<{ kind: ModalKind; refund: AdminRefund } | null>(null)
const trackingDraft = ref('')
const reasonDraft = ref('')
const modalError = ref('')
const stripeError = ref('')
const busy = ref(false)

function openModal(kind: ModalKind, refund: AdminRefund) {
  modal.value = { kind, refund }
  trackingDraft.value = refund.returnTrackingNo || ''
  reasonDraft.value = ''
  modalError.value = ''
  stripeError.value = ''
}

async function submitModal() {
  if (!modal.value) return
  const { kind, refund } = modal.value
  if (kind === 'reject' && !reasonDraft.value.trim()) {
    modalError.value = '拒绝原因必填'
    return
  }
  if (kind === 'track' && !trackingDraft.value.trim()) {
    modalError.value = '退货物流单号必填'
    return
  }
  busy.value = true
  try {
    if (kind === 'approve') await store.approve(refund.id, trackingDraft.value.trim() || undefined)
    else if (kind === 'reject') await store.reject(refund.id, reasonDraft.value.trim())
    else await store.patchTracking(refund.id, trackingDraft.value.trim())
    toast.success(kind === 'approve' ? '已同意退款，Stripe 退款已发起' : kind === 'reject' ? '已拒绝该工单' : '退货单号已登记')
    modal.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409604) {
      // FORM-TRD-A03：已审工单 → toast + 行刷新
      toast.error('工单已审核，不可重复操作')
      modal.value = null
      load()
    } else if (e instanceof BizError && (e.code === 502601 || e.code === 504601)) {
      // 弹窗保留可重试（error-strategy admin 呈现）
      stripeError.value = 'Stripe 暂不可用，工单保持待审批，可稍后重试'
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  } finally {
    busy.value = false
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
              <div v-if="r.status === 'pending'" class="flex items-center justify-end gap-1">
                <button class="btn-ghost text-ok" @click="openModal('approve', r)"><CheckIcon class="h-4 w-4" />同意</button>
                <button class="btn-danger-ghost" @click="openModal('reject', r)"><XMarkIcon class="h-4 w-4" />拒绝</button>
              </div>
              <div v-else class="flex flex-col items-end gap-0.5 text-[11px] text-ink-faint">
                <span>已处理</span>
                <span v-if="r.stripeRefundId" class="font-mono">{{ r.stripeRefundId }}</span>
                <span v-if="r.returnTrackingNo" class="font-mono">退货 {{ r.returnTrackingNo }}</span>
                <!-- 决策 31：登记退货单号入口（已同意未登记） -->
                <button v-if="r.status === 'approved' && !r.returnTrackingNo" class="btn-ghost" @click="openModal('track', r)">
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

    <!-- 审批 / 拒绝 / 登记弹窗（FORM-TRD-A03 二次确认语义由弹窗承载） -->
    <Teleport to="body">
      <div v-if="modal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="modal = null">
        <div class="panel w-[26rem] p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">
              {{ modal.kind === 'approve' ? '同意退款' : modal.kind === 'reject' ? '拒绝退款' : '登记退货物流单号' }}
              <span class="ml-1 font-mono text-[12px] text-ink-faint">{{ modal.refund.refundNo }}</span>
            </h3>
            <button class="btn-ghost" @click="modal = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <p v-if="modal.kind === 'approve'" class="text-[13px] text-ink-soft">
              确认同意退款 {{ currencySymbol(modal.refund.currency) }}{{ Number(modal.refund.amount).toLocaleString() }}？
              将立即发起 Stripe 原路退款。
            </p>
            <div v-if="modal.kind !== 'reject'">
              <label class="field-label">退货物流单号{{ modal.kind === 'approve' ? '（选填）' : ' *' }}</label>
              <input v-model="trackingDraft" class="field" placeholder="买家退货物流单号" />
            </div>
            <div v-if="modal.kind === 'reject'">
              <label class="field-label">拒绝原因 *</label>
              <textarea v-model="reasonDraft" rows="3" class="field resize-none" maxlength="255" placeholder="将向买家展示该原因"></textarea>
            </div>
            <p v-if="modalError" class="text-[11px] text-danger">{{ modalError }}</p>
            <p v-if="stripeError" class="rounded-luxe border border-warn/40 bg-warn/8 px-3 py-2 text-[12px] text-warn">{{ stripeError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="modal = null">取消</button>
            <button class="btn-gold" :disabled="busy" @click="submitModal">
              {{ busy ? '处理中…' : modal.kind === 'approve' ? '确认同意' : modal.kind === 'reject' ? '确认拒绝' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
