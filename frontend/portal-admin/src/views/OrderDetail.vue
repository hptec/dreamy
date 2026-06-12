<script setup lang="ts">
// PAGE-TRD-A02 / COMP-TRD-A02~A05：订单详情（mock → getAdminOrder；面板结构不变）
// 发货面板 carrier 由 API Carrier 枚举渲染（F-036：carrier 文案以 API 为准，勿照抄原型 mock 标签）
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useOrdersStore } from '@/stores/orders'
import { useToastStore } from '@/stores/toast'
import { shippingApi } from '@/api'
import { BizError } from '@/api/client'
import { CarrierStatus, OrderStatus, RefundStatus } from '@/api/types'
import { extractFieldErrors, validateAdminRefundForm, validateShipForm, type FieldErrors } from '@/utils/validators'
import { currencySymbol, formatDateTime, formatMoney } from '@/utils/format'
import {
  ArrowLeftIcon, TruckIcon, ArrowUturnLeftIcon, CheckIcon, MapPinIcon, CreditCardIcon, XMarkIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const store = useOrdersStore()
const toast = useToastStore()

const orderId = computed(() => Number(route.params.id))
const o = computed(() => store.detail)
const sym = computed(() => currencySymbol(o.value?.currency))

const ORDER_STATUS: Record<number, { tone: string; label: string }> = {
  [OrderStatus.PENDING]: { tone: 'warn', label: '待付款' },
  [OrderStatus.PAID]: { tone: 'info', label: '待发货' },
  [OrderStatus.SHIPPED]: { tone: 'info', label: '已发货' },
  [OrderStatus.COMPLETED]: { tone: 'ok', label: '已完成' },
  [OrderStatus.CANCELLED]: { tone: 'neutral', label: '已取消' },
  [OrderStatus.REFUNDING]: { tone: 'danger', label: '退款中' },
  [OrderStatus.REFUNDED]: { tone: 'neutral', label: '已退款' },
}
const REFUND_STATUS: Record<number, { tone: string; label: string }> = {
  [RefundStatus.PENDING]: { tone: 'warn', label: '待审批' },
  [RefundStatus.APPROVED]: { tone: 'ok', label: '已同意' },
  [RefundStatus.REJECTED]: { tone: 'danger', label: '已拒绝' },
}

// ===== COMP-TRD-A03 发货面板（carrier 选项 = API 启用承运方枚举） =====
const showShip = ref(false)
const carriers = ref<string[]>([])
const shipForm = ref({ carrier: '', trackingNo: '' })
const shipErrors = ref<FieldErrors>({})
const shipping = ref(false)

async function loadCarriers() {
  try {
    const res = await shippingApi.listCarriers()
    carriers.value = res.items.filter((c) => c.status === CarrierStatus.ENABLED).map((c) => c.name)
  } catch {
    carriers.value = []
  }
}

function openShip() {
  showShip.value = !showShip.value
  if (showShip.value) {
    // 默认值 = order.carrier（结算所选承运商沿用）
    shipForm.value = { carrier: o.value?.carrier || carriers.value[0] || '', trackingNo: '' }
    shipErrors.value = {}
  }
}

async function doShip() {
  // COMP-TRD-D02：carriers 空列表 → 提交校验「请先在物流配置启用承运方」（ALIGN-022 兜底）
  shipErrors.value = validateShipForm(shipForm.value, carriers.value.length > 0)
  if (Object.keys(shipErrors.value).length) return
  shipping.value = true
  try {
    await store.ship(orderId.value, shipForm.value.carrier, shipForm.value.trackingNo.trim())
    toast.success('已标记发货')
    showShip.value = false
  } catch (e) {
    if (e instanceof BizError && e.code === 409602) {
      // FORM-TRD-A01：状态机冲突 → toast + 刷新详情
      toast.error('当前订单状态不允许该操作')
      store.fetchDetail(orderId.value).catch(() => undefined)
    } else {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) shipErrors.value = fields
      else toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  } finally {
    shipping.value = false
  }
}

// ===== COMP-TRD-A04 发起退款弹窗 =====
const showRefund = ref(false)
const refundForm = ref({ amount: '' as number | string, reason: '' })
const refundErrors = ref<FieldErrors>({})
const refundNote = ref('')
const refunding = ref(false)

function openRefund() {
  refundForm.value = { amount: o.value?.totalAmount ?? '', reason: '' }
  refundErrors.value = {}
  refundNote.value = ''
  showRefund.value = true
}

async function doRefund() {
  refundErrors.value = validateAdminRefundForm(refundForm.value, Number(o.value?.totalAmount ?? 0))
  if (Object.keys(refundErrors.value).length) return
  refunding.value = true
  try {
    await store.createRefund(orderId.value, refundForm.value.amount, refundForm.value.reason.trim())
    toast.success('退款工单已创建')
    showRefund.value = false
  } catch (e) {
    if (e instanceof BizError && e.code === 422602) {
      const deadline = (e.details as Record<string, string> | undefined)?.grace_deadline
      refundNote.value = `定制商品已投产，不可退款${deadline ? `（宽限截止 ${formatDateTime(String(deadline))}）` : ''}`
    } else if (e instanceof BizError && e.code === 422603) {
      refundErrors.value = { amount: '超出可退上限' }
    } else if (e instanceof BizError && e.code === 409605) {
      toast.error('该订单已有进行中的退款')
      showRefund.value = false
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  } finally {
    refunding.value = false
  }
}

// ===== COMP-TRD-A05 完成 / 取消 =====
const confirmAction = ref<{ status: OrderStatus; title: string; message: string } | null>(null)
const confirmBusy = ref(false)

function askComplete() {
  confirmAction.value = { status: OrderStatus.COMPLETED, title: '确认完成', message: '确认买家已收货并完成本订单？' }
}
function askCancel() {
  confirmAction.value = { status: OrderStatus.CANCELLED, title: '取消订单', message: '确认取消该待付款订单？取消后买家将无法继续支付。' }
}

async function doStatusPatch() {
  if (!confirmAction.value) return
  confirmBusy.value = true
  try {
    await store.patchStatus(orderId.value, confirmAction.value.status)
    toast.success(confirmAction.value.status === OrderStatus.COMPLETED ? '订单已完成' : '订单已取消')
    confirmAction.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409602) {
      toast.error('当前订单状态不允许该操作')
      confirmAction.value = null
      store.fetchDetail(orderId.value).catch(() => undefined)
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  } finally {
    confirmBusy.value = false
  }
}

// 状态时间线（createdAt→paidAt→shippedAt→completedAt）
const timeline = computed(() => {
  const d = o.value
  return [
    { label: '已创建', time: d?.createdAt, done: !!d },
    { label: '已支付', time: d?.paidAt, done: !!d?.paidAt },
    { label: '已发货', time: d?.shippedAt, done: !!d?.shippedAt },
    { label: '已完成', time: d?.completedAt, done: !!d?.completedAt },
  ]
})

const addr = computed(() => (o.value?.addressSnapshot ?? {}) as Record<string, string>)

onMounted(() => {
  store.fetchDetail(orderId.value).catch((e) => toast.error(e instanceof BizError ? e.message : '加载订单详情失败'))
  loadCarriers()
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader :eyebrow="o?.orderNo || '...'" title="订单详情">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/orders')"><ArrowLeftIcon class="h-4 w-4" />返回</button>
        <!-- COMP-TRD-A05：pending → 取消；paid 不出取消（须走退款） -->
        <button v-if="o?.status === OrderStatus.PENDING" class="btn-outline" @click="askCancel"><XMarkIcon class="h-4 w-4" />取消订单</button>
        <button v-if="o && ([OrderStatus.PAID, OrderStatus.SHIPPED] as number[]).includes(o.status)" class="btn-outline" @click="openRefund"><ArrowUturnLeftIcon class="h-4 w-4" />发起退款</button>
        <button v-if="o?.status === OrderStatus.SHIPPED" class="btn-primary" @click="askComplete"><CheckIcon class="h-4 w-4" />确认完成</button>
        <!-- 仅 status=paid 显示「标记发货」（前端预判 + 后端 409602 兜底） -->
        <button v-if="o?.status === OrderStatus.PAID" class="btn-primary" @click="openShip"><TruckIcon class="h-4 w-4" />标记发货</button>
      </template>
    </PageHeader>

    <div v-if="store.detailLoading" class="panel p-12 text-center text-ink-faint">加载中…</div>

    <div v-else-if="o" class="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_340px]">
      <!-- 左：明细 + 时间线 -->
      <div class="space-y-6">
        <!-- 发货面板 -->
        <div v-if="showShip" class="panel border-gold/40 p-5">
          <h3 class="mb-3 font-display text-lg font-semibold text-ink">填写物流信息</h3>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="field-label">承运方</label>
              <select v-model="shipForm.carrier" class="field">
                <option v-for="c in carriers" :key="c" :value="c">{{ c }}</option>
              </select>
              <p v-if="shipErrors.carrier" class="mt-1 text-[11px] text-danger">{{ shipErrors.carrier }}</p>
            </div>
            <div>
              <label class="field-label">运单号</label>
              <input v-model="shipForm.trackingNo" class="field" placeholder="输入 tracking number" />
              <p v-if="shipErrors.trackingNo" class="mt-1 text-[11px] text-danger">{{ shipErrors.trackingNo }}</p>
            </div>
          </div>
          <div class="mt-4 flex justify-end gap-2">
            <button class="btn-ghost" @click="showShip = false">取消</button>
            <button class="btn-gold" :disabled="shipping" @click="doShip"><CheckIcon class="h-4 w-4" />{{ shipping ? '提交中…' : '确认发货' }}</button>
          </div>
        </div>

        <!-- 商品明细（定制行展开 customSizeData + 「定制」徽章） -->
        <div class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">商品明细</h3></div>
          <table class="data-table">
            <thead><tr><th>商品</th><th>SKU</th><th class="text-right">单价</th><th class="text-right">数量</th><th class="text-right">小计</th></tr></thead>
            <tbody>
              <tr v-for="l in o.lines" :key="l.id">
                <td>
                  <div class="flex items-center gap-3">
                    <img v-if="l.img" :src="l.img" class="h-12 w-10 rounded-luxe object-cover" />
                    <span v-else class="h-12 w-10 rounded-luxe bg-canvas-warm"></span>
                    <div>
                      <p class="font-medium text-ink">
                        {{ l.productName }}
                        <span v-if="l.customSizeData" class="ml-1 rounded bg-gold/12 px-1.5 py-0.5 text-[10px] text-gold-deep">定制</span>
                      </p>
                      <p class="text-[11px] text-ink-faint">{{ l.color || '—' }} · {{ l.size || (l.customSizeData ? 'Custom' : '—') }}</p>
                      <p v-if="l.customSizeData" class="text-[11px] text-ink-faint">
                        胸围 {{ l.customSizeData.bust ?? '—' }} · 腰围 {{ l.customSizeData.waist ?? '—' }} ·
                        臀围 {{ l.customSizeData.hips ?? '—' }} · 中空到地 {{ l.customSizeData.hollowToFloor ?? '—' }}
                      </p>
                    </div>
                  </div>
                </td>
                <td class="font-mono text-[12px] text-ink-soft">{{ l.skuCode || '—' }}</td>
                <td class="text-right">{{ sym }}{{ Number(l.unitPrice).toLocaleString() }}</td>
                <td class="text-right">{{ l.qty }}</td>
                <td class="text-right font-medium text-ink">{{ sym }}{{ (Number(l.unitPrice) * l.qty).toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
          <!-- 金额拆分（决策 28：小计/运费/Gift Wrapping/优惠/合计） -->
          <div class="border-t border-line px-6 py-4">
            <div class="ml-auto max-w-xs space-y-1.5 text-[13px]">
              <div class="flex justify-between text-ink-soft"><span>小计</span><span>{{ formatMoney(o.subtotal, o.currency) }}</span></div>
              <div class="flex justify-between text-ink-soft"><span>运费</span><span>{{ Number(o.shippingFee) === 0 ? '免邮' : formatMoney(o.shippingFee, o.currency) }}</span></div>
              <div v-if="o.giftWrap" class="flex justify-between text-ink-soft"><span>Gift Wrapping</span><span>{{ formatMoney(o.giftWrapFee, o.currency) }}</span></div>
              <div v-if="Number(o.discountAmount) > 0" class="flex justify-between text-ink-soft"><span>优惠</span><span>-{{ formatMoney(o.discountAmount, o.currency) }}</span></div>
              <div class="flex justify-between border-t border-line pt-2 font-display text-lg font-semibold text-ink"><span>合计</span><span>{{ formatMoney(o.totalAmount, o.currency) }}</span></div>
            </div>
          </div>
        </div>

        <!-- 状态时间线 -->
        <div class="panel p-6">
          <h3 class="mb-5 font-display text-lg font-semibold text-ink">订单状态</h3>
          <div class="flex items-center justify-between">
            <template v-for="(t, i) in timeline" :key="i">
              <div class="flex flex-col items-center text-center">
                <span class="flex h-9 w-9 items-center justify-center rounded-full border-2" :class="t.done ? 'border-gold bg-gold text-white' : 'border-line text-ink-faint'">
                  <CheckIcon v-if="t.done" class="h-4 w-4" /><span v-else>{{ i + 1 }}</span>
                </span>
                <p class="mt-2 text-[12px]" :class="t.done ? 'font-medium text-ink' : 'text-ink-faint'">{{ t.label }}</p>
                <p class="text-[10px] text-ink-faint">{{ t.time ? formatDateTime(t.time) : '—' }}</p>
              </div>
              <div v-if="i < timeline.length - 1" class="mx-2 h-px flex-1" :class="timeline[i + 1].done ? 'bg-gold' : 'bg-line'"></div>
            </template>
          </div>
        </div>

        <!-- 关联退款工单 -->
        <div v-if="o.refunds?.length" class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">关联退款工单</h3></div>
          <table class="data-table">
            <thead><tr><th>工单号</th><th class="text-right">金额</th><th>原因</th><th>状态</th><th>申请时间</th></tr></thead>
            <tbody>
              <tr v-for="r in o.refunds" :key="r.id">
                <td class="font-mono text-[12px] text-gold-deep">{{ r.refundNo }}</td>
                <td class="text-right font-medium text-ink">{{ formatMoney(r.amount, r.currency) }}</td>
                <td class="text-ink-soft">{{ r.reason || '—' }}</td>
                <td><StatusBadge :tone="REFUND_STATUS[r.status]?.tone || 'neutral'" :label="REFUND_STATUS[r.status]?.label || String(r.status)" /></td>
                <td class="text-[12px] text-ink-faint">{{ formatDateTime(r.appliedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 右：状态 + 客户 + 地址 + 支付 -->
      <div class="space-y-6">
        <div class="panel p-5">
          <div class="mb-1 flex items-center justify-between">
            <h3 class="font-display text-base font-semibold text-ink">订单状态</h3>
            <StatusBadge :tone="(ORDER_STATUS[o.status]?.tone as any) || 'neutral'" :label="ORDER_STATUS[o.status]?.label || String(o.status)" />
          </div>
          <p v-if="o.status === OrderStatus.PAID" class="text-[11px] text-ink-faint">已支付订单不可直接取消，如需取消请走退款流程。</p>
          <p v-if="o.weddingDate" class="mt-1 text-[12px] text-ink-soft">婚期：{{ o.weddingDate }}</p>
          <p v-if="o.carrier" class="mt-1 text-[12px] text-ink-soft">承运：{{ o.carrier }}<template v-if="o.trackingNo"> · {{ o.trackingNo }}</template></p>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 font-display text-base font-semibold text-ink">客户信息</h3>
          <p class="text-[13px] font-medium text-ink">{{ o.customerName || '—' }}</p>
          <p class="text-[12px] text-ink-soft">{{ o.customerEmail || '—' }}</p>
          <p class="text-[12px] text-ink-soft">{{ o.customerPhone || '—' }}</p>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><MapPinIcon class="h-4 w-4 text-gold-deep" />收货地址</h3>
          <p class="text-[13px] text-ink-soft">
            {{ addr.receiver }}<template v-if="addr.phone"> · {{ addr.phone }}</template><br />
            {{ addr.line }}<br />
            {{ addr.city }}<template v-if="addr.state">, {{ addr.state }}</template> {{ addr.zip }}<br />
            {{ addr.country }}
          </p>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><CreditCardIcon class="h-4 w-4 text-gold-deep" />支付信息</h3>
          <p class="text-[13px] text-ink-soft">{{ o.payment?.cardSummary || o.paymentMethod || '—' }}</p>
          <p v-if="o.payment?.paymentIntentId" class="break-all font-mono text-[11px] text-ink-faint">{{ o.payment.paymentIntentId }}</p>
          <p class="text-[12px] text-ink-faint">支付时间 {{ formatDateTime(o.payment?.paidAt || o.paidAt) }}</p>
        </div>
      </div>
    </div>

    <!-- 发起退款弹窗 -->
    <Teleport to="body">
      <div v-if="showRefund" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showRefund = false)">
        <div class="panel w-[28rem] p-6">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">发起退款（代客）</h3>
            <button class="btn-ghost" @click="showRefund = false"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">退款金额（{{ o?.currency }}，≤ {{ formatMoney(o?.totalAmount, o?.currency) }}）</label>
              <input v-model="refundForm.amount" type="number" min="0" step="0.01" class="field" />
              <p v-if="refundErrors.amount" class="mt-1 text-[11px] text-danger">{{ refundErrors.amount }}</p>
            </div>
            <div>
              <label class="field-label">退款原因 *</label>
              <textarea v-model="refundForm.reason" rows="3" class="field resize-none" maxlength="255" placeholder="填写退款原因（≤255 字）"></textarea>
              <p v-if="refundErrors.reason" class="mt-1 text-[11px] text-danger">{{ refundErrors.reason }}</p>
            </div>
            <p v-if="refundNote" class="rounded-luxe border border-warn/40 bg-warn/8 px-3 py-2 text-[12px] text-warn">{{ refundNote }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" @click="showRefund = false">取消</button>
            <button class="btn-gold" :disabled="refunding" @click="doRefund">{{ refunding ? '提交中…' : '创建退款工单' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="!!confirmAction"
      :title="confirmAction?.title || ''"
      :message="confirmAction?.message || ''"
      :danger="confirmAction?.status === OrderStatus.CANCELLED"
      :busy="confirmBusy"
      :confirm-text="confirmAction?.status === OrderStatus.COMPLETED ? '确认完成' : '确认取消'"
      @confirm="doStatusPatch"
      @cancel="confirmAction = null"
    />
  </div>
</template>
