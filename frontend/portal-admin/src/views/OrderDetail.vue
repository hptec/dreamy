<script setup lang="ts">
// PAGE-TRD-A02 / COMP-TRD-A02~A05：订单详情（信息区 + 右侧操作栏）
// order-flow-complete J：6 步时间线（异常分支）/ 制作阶段步进器 / 含税金额拆分 / 包裹面板（部分发货・轨迹・签收・作废・同步）
// / 时间线备注面板 / 按状态操作（PAID 取消=自动全额退款、部分退款默认剩余可退；SHIPPED 标记送达；DELIVERED 标记完成）
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ShipmentCard from '@/components/orders/ShipmentCard.vue'
import ShipmentCreateDrawer from '@/components/orders/ShipmentCreateDrawer.vue'
import OrderTimelinePanel from '@/components/orders/OrderTimelinePanel.vue'
import ProductionStageStepper from '@/components/orders/ProductionStageStepper.vue'
import { useOrdersStore } from '@/stores/orders'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { OrderStatus, ShipmentStatus } from '@/api/types'
import { extractFieldErrors, validateAdminRefundForm, type FieldErrors } from '@/utils/validators'
import { currencySymbol, formatDate, formatDateTime, formatMoney } from '@/utils/format'
import {
  INCOTERM_LABEL, REFUND_STATUS_META, SERVICE_LEVEL_LABEL, orderStatusMeta, productionStageMeta, ratePercent,
} from '@/utils/tradingLabels'
import {
  ArrowLeftIcon, TruckIcon, ArrowUturnLeftIcon, CheckIcon, MapPinIcon, CreditCardIcon, XMarkIcon, ChevronDownIcon, ChevronUpIcon, HomeIcon, PlusIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const router = useRouter()
const store = useOrdersStore()
const shippingStore = useShippingStore()
const toast = useToastStore()

const orderId = computed(() => Number(route.params.id))
const o = computed(() => store.detail)
const sym = computed(() => currencySymbol(o.value?.currency))

// ===== 金额派生 =====
const refunded = computed(() => Number(o.value?.refundedAmount ?? 0))
const refundable = computed(() => Math.max(0, Number(o.value?.totalAmount ?? 0) - refunded.value))
const showTax = computed(() => (o.value?.amountVersion ?? 1) >= 2)
const taxOpen = ref(false)

// ===== 6 步时间线（Placed→Paid→In production→Shipped→Delivered→Completed；异常分支单独提示） =====
const timeline = computed(() => {
  const d = o.value
  const st = d?.status
  const inProduction = !!d?.paidAt && (st === OrderStatus.PAID ? (d?.productionStage ?? 1) >= 2 : !!d?.shippedAt)
  return [
    { label: '已下单', time: d?.createdAt, done: !!d },
    { label: '已付款', time: d?.paidAt, done: !!d?.paidAt },
    { label: '制作中', time: null, done: inProduction, hint: st === OrderStatus.PAID ? productionStageMeta(d?.productionStage).label : '' },
    { label: '已发货', time: d?.shippedAt, done: !!d?.shippedAt },
    { label: '已签收', time: d?.deliveredAt, done: !!d?.deliveredAt },
    { label: '已完成', time: d?.completedAt, done: !!d?.completedAt },
  ]
})
const abnormal = computed(() => {
  const st = o.value?.status
  if (st === OrderStatus.CANCELLED) return { tone: 'neutral', text: '订单已取消' + (o.value?.paidAt ? '（已支付订单由后台取消并全额退款）' : '') }
  if (st === OrderStatus.REFUNDING) return { tone: 'danger', text: '订单处于退款中，存在待处理退款工单；批准/驳回后将还原到申请前状态或转为已退款' }
  if (st === OrderStatus.REFUNDED) return { tone: 'neutral', text: '订单已全额退款' }
  return null
})

// ===== 包裹 =====
const shipments = computed(() => o.value?.shipments ?? [])
const activeShipments = computed(() => shipments.value.filter((s) => s.status !== ShipmentStatus.CANCELLED))
const unshippedQty = computed(() => {
  if (!o.value) return 0
  const shipped = new Map<number, number>()
  for (const s of activeShipments.value) for (const l of s.lines ?? []) shipped.set(l.orderLineId, (shipped.get(l.orderLineId) ?? 0) + l.qty)
  return o.value.lines.reduce((sum, l) => sum + Math.max(0, l.qty - (shipped.get(l.id) ?? 0)), 0)
})
const showShipmentDrawer = ref(false)
const canCreateShipment = computed(() => o.value?.status === OrderStatus.PAID && unshippedQty.value > 0)

// ===== 退款弹窗（部分/全额；默认剩余可退） =====
const showRefund = ref(false)
const refundForm = ref({ amount: '' as number | string, reason: '' })
const refundErrors = ref<FieldErrors>({})
const refundNote = ref('')
const refunding = ref(false)

function openRefund() {
  refundForm.value = { amount: refundable.value.toFixed(2), reason: '' }
  refundErrors.value = {}
  refundNote.value = ''
  showRefund.value = true
}

async function doRefund() {
  if (refunding.value) return
  refundErrors.value = validateAdminRefundForm(refundForm.value, refundable.value)
  if (Object.keys(refundErrors.value).length) return
  refunding.value = true
  try {
    await store.createRefund(orderId.value, refundForm.value.amount, refundForm.value.reason.trim())
    toast.success('退款工单已创建，订单进入退款中')
    showRefund.value = false
  } catch (e) {
    if (e instanceof BizError && e.code === 422602) {
      const deadline = (e.details as Record<string, string> | undefined)?.grace_deadline
      refundNote.value = `定制商品已投产，不可退款${deadline ? `（宽限截止 ${formatDateTime(String(deadline))}）` : ''}`
    } else if (e instanceof BizError && (e.code === 422603 || e.code === 422908)) {
      refundErrors.value = { amount: describeError(e) }
    } else if (e instanceof BizError && (e.code === 409605 || e.code === 409907)) {
      toast.error(describeError(e))
      showRefund.value = false
      store.refreshDetail(orderId.value).catch(() => undefined)
    } else if (e instanceof BizError && e.code === 409602) {
      toast.error(describeError(e))
      showRefund.value = false
      store.refreshDetail(orderId.value).catch(() => undefined)
    } else {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) refundErrors.value = fields
      else toast.error(describeError(e))
    }
  } finally {
    refunding.value = false
  }
}

// ===== 状态操作（取消 / 标记送达 / 标记完成） =====
const confirmAction = ref<{ status: OrderStatus; title: string; message: string; confirmText: string; danger?: boolean } | null>(null)
const confirmBusy = ref(false)

function askCancelPending() {
  confirmAction.value = { status: OrderStatus.CANCELLED, title: '取消订单', message: '确认取消该待付款订单？取消后买家将无法继续支付。', confirmText: '确认取消', danger: true }
}
function askCancelPaid() {
  confirmAction.value = {
    status: OrderStatus.CANCELLED,
    title: '取消已支付订单',
    message: `确认取消？系统将自动创建并批准全额退款（${formatMoney(refundable.value, o.value?.currency)}），库存回补，订单转为「已取消」。此操作不可撤销。`,
    confirmText: '取消并全额退款',
    danger: true,
  }
}
function askDelivered() {
  confirmAction.value = { status: OrderStatus.DELIVERED, title: '标记送达', message: '确认所有包裹已送达买家？订单将进入「已签收」，到期自动完成。', confirmText: '标记送达' }
}
function askComplete() {
  confirmAction.value = { status: OrderStatus.COMPLETED, title: '确认完成', message: '确认买家已收货并完成本订单？完成后不可再发起售后以外操作。', confirmText: '确认完成' }
}

async function doStatusPatch() {
  if (!confirmAction.value || confirmBusy.value) return
  confirmBusy.value = true
  const target = confirmAction.value.status
  try {
    await store.patchStatus(orderId.value, target)
    toast.success(target === OrderStatus.COMPLETED ? '订单已完成' : target === OrderStatus.DELIVERED ? '订单已标记送达' : '订单已取消')
    confirmAction.value = null
  } catch (e) {
    toast.error(describeError(e))
    if (e instanceof BizError && (e.code === 409602 || e.code === 409907)) {
      confirmAction.value = null
      store.refreshDetail(orderId.value).catch(() => undefined)
    }
  } finally {
    confirmBusy.value = false
  }
}

const addr = computed(() => (o.value?.addressSnapshot ?? {}) as Record<string, string>)

onMounted(() => {
  store.fetchDetail(orderId.value).catch((e) => toast.error(describeError(e, '加载订单详情失败')))
  shippingStore.fetchCarriers().catch(() => undefined)
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader :eyebrow="o?.orderNo || '...'" title="订单详情">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/orders')"><ArrowLeftIcon class="h-4 w-4" />返回</button>
      </template>
    </PageHeader>

    <div v-if="store.detailLoading && !o" class="panel p-12 text-center text-ink-faint">加载中…</div>

    <div v-else-if="o" class="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_340px]">
      <!-- 左：信息区 -->
      <div class="space-y-6">
        <!-- 状态时间线 + 制作阶段 -->
        <div class="panel p-6" data-testid="order-status-panel">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="font-display text-lg font-semibold text-ink">订单进度</h3>
            <StatusBadge :tone="orderStatusMeta(o.status).tone" :label="orderStatusMeta(o.status).label" />
          </div>
          <div class="flex items-start justify-between">
            <template v-for="(t, i) in timeline" :key="i">
              <div class="flex w-16 flex-col items-center text-center">
                <span class="flex h-9 w-9 items-center justify-center rounded-full border-2" :class="t.done ? 'border-gold bg-gold text-white' : 'border-line text-ink-faint'">
                  <CheckIcon v-if="t.done" class="h-4 w-4" /><span v-else>{{ i + 1 }}</span>
                </span>
                <p class="mt-2 text-[12px]" :class="t.done ? 'font-medium text-ink' : 'text-ink-faint'">{{ t.label }}</p>
                <p class="text-[10px] text-ink-faint">{{ t.time ? formatDateTime(t.time) : t.hint || '—' }}</p>
              </div>
              <div v-if="i < timeline.length - 1" class="mx-1 mt-4 h-px flex-1" :class="timeline[i + 1]!.done ? 'bg-gold' : 'bg-line'"></div>
            </template>
          </div>
          <p v-if="abnormal" class="mt-4 rounded-luxe px-3 py-2 text-[12px]" :class="abnormal.tone === 'danger' ? 'bg-danger/8 text-danger' : 'bg-ink/6 text-ink-soft'" data-testid="order-abnormal">{{ abnormal.text }}</p>
          <div v-if="o.status === OrderStatus.PAID" class="mt-5">
            <ProductionStageStepper :order-id="o.id" :stage="o.productionStage" />
          </div>
        </div>

        <!-- 商品明细 + 金额拆分 -->
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
          <div class="grid grid-cols-1 gap-4 border-t border-line px-6 py-4 md:grid-cols-[1fr_20rem]">
            <!-- 履约摘要 -->
            <div class="space-y-1 text-[12px] text-ink-soft">
              <p>预计送达：<span class="text-ink" data-testid="order-eta">{{ o.estimatedDeliveryFrom ? `${formatDate(o.estimatedDeliveryFrom)} ~ ${formatDate(o.estimatedDeliveryTo)}` : '—' }}</span></p>
              <p>服务等级：<span class="text-ink">{{ o.shippingServiceLevel ? SERVICE_LEVEL_LABEL[o.shippingServiceLevel] : '—' }}</span></p>
              <p>税费条款：<span class="text-ink">{{ o.incoterm ? INCOTERM_LABEL[o.incoterm] : '—' }}</span></p>
              <p>锁定汇率：<span class="font-mono text-ink">1 USD = {{ o.exchangeRate != null ? Number(o.exchangeRate).toFixed(4) : '—' }} {{ o.currency }}</span></p>
              <p v-if="o.weddingDate">婚期：<span class="text-ink">{{ o.weddingDate }}</span><span v-if="o.weddingDaysLeft != null" class="ml-1" :class="o.weddingDaysLeft <= 14 ? 'font-medium text-danger' : 'text-ink-faint'">（距今 {{ o.weddingDaysLeft }} 天）</span></p>
            </div>
            <!-- 金额拆分（决策 28 + 税费 + 已退款） -->
            <div class="space-y-1.5 text-[13px]" data-testid="order-amounts">
              <div class="flex justify-between text-ink-soft"><span>小计</span><span>{{ formatMoney(o.subtotal, o.currency) }}</span></div>
              <div class="flex justify-between text-ink-soft"><span>运费</span><span>{{ Number(o.shippingFee) === 0 ? '免邮' : formatMoney(o.shippingFee, o.currency) }}</span></div>
              <div v-if="o.giftWrap" class="flex justify-between text-ink-soft"><span>Gift Wrapping</span><span>{{ formatMoney(o.giftWrapFee, o.currency) }}</span></div>
              <template v-if="showTax">
                <div class="flex justify-between text-ink-soft">
                  <button class="inline-flex items-center gap-1 hover:text-ink" :disabled="!o.taxBreakdown?.length" @click="taxOpen = !taxOpen">
                    税费<component :is="taxOpen ? ChevronUpIcon : ChevronDownIcon" v-if="o.taxBreakdown?.length" class="h-3 w-3" />
                  </button>
                  <span>{{ formatMoney(o.taxAmount ?? 0, o.currency) }}</span>
                </div>
                <div v-if="taxOpen && o.taxBreakdown?.length" class="space-y-0.5 rounded-luxe bg-canvas-warm/50 px-2 py-1.5 text-[11px] text-ink-faint">
                  <div v-for="(t, i) in o.taxBreakdown" :key="i" class="flex justify-between">
                    <span>{{ t.label || t.type }} · {{ ratePercent(t.rateScaled) }} × {{ formatMoney(t.base, o.currency) }}</span>
                    <span>{{ formatMoney(t.amount, o.currency) }}</span>
                  </div>
                </div>
              </template>
              <div v-if="Number(o.discountAmount) > 0" class="flex justify-between text-ink-soft"><span>优惠</span><span>-{{ formatMoney(o.discountAmount, o.currency) }}</span></div>
              <div class="flex justify-between border-t border-line pt-2 font-display text-lg font-semibold text-ink"><span>合计</span><span>{{ formatMoney(o.totalAmount, o.currency) }}</span></div>
              <div v-if="refunded > 0" class="flex justify-between text-[12px] text-danger" data-testid="order-refunded">
                <span>已退款累计</span><span>-{{ formatMoney(refunded, o.currency) }}</span>
              </div>
              <div v-if="refunded > 0" class="flex justify-between text-[12px] text-ink-faint"><span>剩余可退</span><span>{{ formatMoney(refundable, o.currency) }}</span></div>
            </div>
          </div>
        </div>

        <!-- 包裹面板 -->
        <div class="panel" data-testid="order-shipments-panel">
          <div class="flex items-center justify-between border-b border-line px-6 py-4">
            <h3 class="flex items-center gap-2 font-display text-lg font-semibold text-ink">
              <TruckIcon class="h-5 w-5 text-gold-deep" />包裹
              <span class="text-[12px] font-normal text-ink-faint">{{ activeShipments.length }} 个 · 未发 {{ unshippedQty }} 件</span>
            </h3>
            <button v-if="canCreateShipment" class="btn-gold" data-testid="open-shipment-drawer" @click="showShipmentDrawer = true"><PlusIcon class="h-4 w-4" />创建包裹</button>
          </div>
          <div class="space-y-4 p-6">
            <p v-if="!shipments.length" class="text-[13px] text-ink-faint">
              尚未创建包裹。<template v-if="o.status === OrderStatus.PAID">制作完成后点击「创建包裹」发货，支持按行部分发货。</template>
            </p>
            <ShipmentCard v-for="s in shipments" :key="s.id" :shipment="s" :order-id="o.id" />
          </div>
        </div>

        <!-- 时间线 / 备注 -->
        <OrderTimelinePanel :order-id="o.id" :events="o.events ?? []" />

        <!-- 关联退款工单 -->
        <div v-if="o.refunds?.length" class="panel" data-testid="order-refunds-panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">关联退款工单</h3></div>
          <table class="data-table">
            <thead><tr><th>工单号</th><th class="text-right">金额</th><th>原因</th><th>状态</th><th>申请时间</th></tr></thead>
            <tbody>
              <tr v-for="r in o.refunds" :key="r.id">
                <td class="font-mono text-[12px] text-gold-deep">{{ r.refundNo }}</td>
                <td class="text-right font-medium text-ink">{{ formatMoney(r.amount, r.currency) }}</td>
                <td class="text-ink-soft">{{ r.reason || '—' }}<span v-if="r.rejectReason" class="block text-[11px] text-danger">驳回：{{ r.rejectReason }}</span></td>
                <td><StatusBadge :tone="REFUND_STATUS_META[r.status]?.tone || 'neutral'" :label="REFUND_STATUS_META[r.status]?.label || String(r.status)" /></td>
                <td class="text-[12px] text-ink-faint">{{ formatDateTime(r.appliedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 右：操作栏 + 客户 + 地址 + 支付 -->
      <div class="space-y-6">
        <div class="panel p-5" data-testid="order-actions">
          <div class="mb-3 flex items-center justify-between">
            <h3 class="font-display text-base font-semibold text-ink">操作</h3>
            <StatusBadge :tone="orderStatusMeta(o.status).tone" :label="orderStatusMeta(o.status).label" />
          </div>
          <div class="flex flex-col gap-2">
            <!-- PENDING -->
            <template v-if="o.status === OrderStatus.PENDING">
              <p class="text-[11px] text-ink-faint">待付款订单可直接取消；超时（{{ o.expiresAt ? formatDateTime(o.expiresAt) : '—' }}）将自动取消。</p>
              <button class="btn-outline" :disabled="confirmBusy" @click="askCancelPending"><XMarkIcon class="h-4 w-4" />取消订单</button>
            </template>
            <!-- PAID -->
            <template v-else-if="o.status === OrderStatus.PAID">
              <button v-if="canCreateShipment" class="btn-primary" data-testid="action-create-shipment" @click="showShipmentDrawer = true"><TruckIcon class="h-4 w-4" />创建包裹发货</button>
              <button class="btn-outline" :disabled="refunding" data-testid="action-refund" @click="openRefund"><ArrowUturnLeftIcon class="h-4 w-4" />部分 / 全额退款</button>
              <button class="btn-danger-ghost justify-center border border-danger/30" :disabled="confirmBusy" data-testid="action-cancel-paid" @click="askCancelPaid"><XMarkIcon class="h-4 w-4" />取消订单（自动全额退款）</button>
            </template>
            <!-- SHIPPED -->
            <template v-else-if="o.status === OrderStatus.SHIPPED">
              <button class="btn-primary" :disabled="confirmBusy" data-testid="action-delivered" @click="askDelivered"><CheckIcon class="h-4 w-4" />标记送达</button>
              <button class="btn-outline" :disabled="refunding" data-testid="action-refund" @click="openRefund"><ArrowUturnLeftIcon class="h-4 w-4" />部分 / 全额退款</button>
              <p class="text-[11px] text-ink-faint">全部包裹标记签收后订单会自动进入「已签收」，也可在此手动标记。</p>
            </template>
            <!-- DELIVERED -->
            <template v-else-if="o.status === OrderStatus.DELIVERED">
              <button class="btn-primary" :disabled="confirmBusy" data-testid="action-complete" @click="askComplete"><CheckIcon class="h-4 w-4" />标记完成</button>
              <button class="btn-outline" :disabled="refunding" data-testid="action-refund" @click="openRefund"><ArrowUturnLeftIcon class="h-4 w-4" />部分 / 全额退款</button>
              <p class="text-[11px] text-ink-faint">签收后到期将自动完成；买家也可在前台确认收货。</p>
            </template>
            <template v-else-if="o.status === OrderStatus.REFUNDING">
              <p class="text-[12px] text-ink-soft">存在待处理退款工单，请前往
                <RouterLink to="/refunds" class="text-gold-deep hover:underline">退款工单</RouterLink> 审批。</p>
            </template>
            <template v-else>
              <p class="text-[12px] text-ink-faint">订单已进入终态，无可用操作。</p>
            </template>
          </div>
          <div class="mt-4 space-y-1 border-t border-line pt-3 text-[12px] text-ink-soft">
            <p v-if="o.status === OrderStatus.PAID">制作阶段：<span class="text-ink">{{ productionStageMeta(o.productionStage).label }}</span></p>
            <p v-if="refunded > 0">已退款：<span class="text-danger">{{ formatMoney(refunded, o.currency) }}</span> / 剩余可退 {{ formatMoney(refundable, o.currency) }}</p>
            <p v-if="o.carrier">最新承运：{{ o.carrier }}<template v-if="o.trackingNo"> · {{ o.trackingNo }}</template></p>
          </div>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 font-display text-base font-semibold text-ink">客户信息</h3>
          <p class="text-[13px] font-medium text-ink">{{ o.customerName || '—' }}</p>
          <p class="text-[12px] text-ink-soft">{{ o.customerEmail || '—' }}</p>
          <p class="text-[12px] text-ink-soft">{{ o.customerPhone || '—' }}</p>
          <p v-if="o.localeSnapshot" class="mt-1 text-[11px] text-ink-faint">下单语言 {{ o.localeSnapshot }}</p>
        </div>
        <div class="panel p-5" data-testid="order-address">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><MapPinIcon class="h-4 w-4 text-gold-deep" />收货地址</h3>
          <p class="text-[13px] text-ink-soft">
            {{ addr.receiver }}<template v-if="addr.phone"> · {{ addr.phone }}</template><br />
            {{ addr.line }}<br />
            {{ addr.city }}<template v-if="addr.state">, {{ addr.state }}</template> {{ addr.zip }}<br />
            {{ addr.country }}
          </p>
          <p class="mt-2 flex items-center gap-1.5 text-[11px] text-ink-faint">
            <HomeIcon class="h-3.5 w-3.5" />
            ISO：<span class="font-mono text-ink">{{ addr.country_code || addr.countryCode || '—' }}</span>
            <template v-if="addr.region_code || addr.regionCode"> / <span class="font-mono text-ink">{{ addr.region_code || addr.regionCode }}</span></template>
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

    <ShipmentCreateDrawer :open="showShipmentDrawer" :order="o" @close="showShipmentDrawer = false" />

    <!-- 发起退款弹窗 -->
    <Teleport to="body">
      <div v-if="showRefund" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showRefund = false)">
        <div class="panel w-[28rem] p-6" data-testid="refund-dialog">
          <div class="mb-5 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">发起退款（代客）</h3>
            <button class="btn-ghost" @click="showRefund = false"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="space-y-4">
            <div>
              <label class="field-label">退款金额（{{ o?.currency }}，剩余可退 {{ formatMoney(refundable, o?.currency) }}）</label>
              <div class="flex items-center gap-2">
                <input v-model="refundForm.amount" type="number" min="0" :max="refundable" step="0.01" class="field" data-testid="refund-amount" />
                <button class="btn-ghost shrink-0 text-[12px]" type="button" @click="refundForm.amount = refundable.toFixed(2)">全额</button>
              </div>
              <p v-if="refundErrors.amount" class="mt-1 text-[11px] text-danger">{{ refundErrors.amount }}</p>
              <p class="mt-1 text-[11px] text-ink-faint">小于剩余可退为部分退款；批准后订单还原到申请前状态，累计达合计才转为「已退款」。</p>
            </div>
            <div>
              <label class="field-label">退款原因 *</label>
              <textarea v-model="refundForm.reason" rows="3" class="field resize-none" maxlength="255" placeholder="填写退款原因（≤255 字）" data-testid="refund-reason"></textarea>
              <p v-if="refundErrors.reason" class="mt-1 text-[11px] text-danger">{{ refundErrors.reason }}</p>
            </div>
            <p v-if="refundNote" class="rounded-luxe border border-warn/40 bg-warn/8 px-3 py-2 text-[12px] text-warn">{{ refundNote }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-2">
            <button class="btn-outline" :disabled="refunding" @click="showRefund = false">取消</button>
            <button class="btn-gold" :disabled="refunding" data-testid="refund-submit" @click="doRefund">{{ refunding ? '提交中…' : '创建退款工单' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <ConfirmDialog
      :open="!!confirmAction"
      :title="confirmAction?.title || ''"
      :message="confirmAction?.message || ''"
      :danger="!!confirmAction?.danger"
      :busy="confirmBusy"
      :confirm-text="confirmAction?.confirmText || '确认'"
      @confirm="doStatusPatch"
      @cancel="confirmAction = null"
    />
  </div>
</template>
