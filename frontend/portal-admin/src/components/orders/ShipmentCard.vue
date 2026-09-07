<script setup lang="ts">
// order-flow-complete J：包裹卡片（承运商/单号外链/状态徽章/行明细/轨迹 + 操作：编辑单号、添加轨迹、标记签收、作废、同步）
// 所有按钮带 loading 防双击；失败一律 toast；409909 包裹状态不允许 → toast + 刷新
import { computed, ref } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useOrdersStore } from '@/stores/orders'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { formatDateTime } from '@/utils/format'
import { SHIPMENT_EVENT_SOURCE_LABEL, SHIPMENT_EVENT_STATUS_OPTIONS, shipmentStatusMeta } from '@/utils/tradingLabels'
import { CarrierStatus, ShipmentStatus } from '@/api/types'
import type { Shipment } from '@/api/types'
import {
  ArrowPathIcon, ArrowTopRightOnSquareIcon, CheckIcon, ChevronDownIcon, ChevronUpIcon, MapPinIcon, NoSymbolIcon, PencilSquareIcon, PlusIcon, XMarkIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{ shipment: Shipment; orderId: number }>()

const store = useOrdersStore()
const shippingStore = useShippingStore()
const toast = useToastStore()

const s = computed(() => props.shipment)
const isTerminal = computed(() => s.value.status === ShipmentStatus.DELIVERED || s.value.status === ShipmentStatus.CANCELLED)
const canDeliver = computed(() => !isTerminal.value)
const canCancel = computed(() => !isTerminal.value)
const canEdit = computed(() => !isTerminal.value)
const canSync = computed(() => s.value.status !== ShipmentStatus.CANCELLED)
const events = computed(() => [...(s.value.events ?? [])].sort((a, b) => (a.occurredAt < b.occurredAt ? 1 : -1)))
const showEvents = ref(true)

const carrierOptions = computed(() =>
  shippingStore.carriers
    .filter((c) => c.status === CarrierStatus.ENABLED && !!c.code)
    .map((c) => ({ value: c.code as string, label: `${c.name}（${c.code}）` })),
)

// ---- 编辑单号 ----
const editing = ref(false)
const editForm = ref({ carrierCode: '', trackingNo: '' })
const editErrors = ref<FieldErrors>({})
const editSaving = ref(false)

function openEdit() {
  editForm.value = { carrierCode: s.value.carrierCode, trackingNo: s.value.trackingNo }
  editErrors.value = {}
  editing.value = true
  addingEvent.value = false
}

async function saveEdit() {
  if (editSaving.value) return
  const trackingNo = editForm.value.trackingNo.trim()
  editErrors.value = {}
  if (!trackingNo) editErrors.value.trackingNo = '运单号必填'
  else if (trackingNo.length > 64) editErrors.value.trackingNo = '运单号不超过 64 字符'
  if (!editForm.value.carrierCode) editErrors.value.carrierCode = '请选择承运商'
  if (Object.keys(editErrors.value).length) return
  editSaving.value = true
  try {
    await store.patchShipment(props.orderId, s.value.id, { carrierCode: editForm.value.carrierCode, trackingNo })
    toast.success('包裹信息已更新')
    editing.value = false
  } catch (e) {
    if (e instanceof BizError && e.code === 409908) editErrors.value = { trackingNo: describeError(e) }
    else if (e instanceof BizError && e.code === 422601) {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) editErrors.value = fields
      else toast.error(describeError(e))
    } else {
      toast.error(describeError(e, '更新失败'))
      if (e instanceof BizError && (e.code === 409909 || e.code === 404907)) store.refreshDetail(props.orderId).catch(() => undefined)
    }
  } finally {
    editSaving.value = false
  }
}

// ---- 添加轨迹 ----
const addingEvent = ref(false)
const eventForm = ref<{ status: number; occurredAt: string; location: string; description: string }>({
  status: ShipmentStatus.IN_TRANSIT, occurredAt: '', location: '', description: '',
})
const eventErrors = ref<FieldErrors>({})
const eventSaving = ref(false)

function openAddEvent() {
  const next = s.value.status === ShipmentStatus.PENDING ? ShipmentStatus.IN_TRANSIT
    : s.value.status === ShipmentStatus.IN_TRANSIT ? ShipmentStatus.OUT_FOR_DELIVERY
      : s.value.status
  eventForm.value = { status: next, occurredAt: '', location: '', description: '' }
  eventErrors.value = {}
  addingEvent.value = true
  editing.value = false
}

async function saveEvent() {
  if (eventSaving.value) return
  eventErrors.value = {}
  const description = eventForm.value.description.trim()
  if (!description) eventErrors.value.description = '描述必填'
  else if (description.length > 255) eventErrors.value.description = '不超过 255 字符'
  if (eventForm.value.location.length > 128) eventErrors.value.location = '不超过 128 字符'
  if (Object.keys(eventErrors.value).length) return
  eventSaving.value = true
  try {
    await store.addShipmentEvent(props.orderId, s.value.id, {
      status: eventForm.value.status as Shipment['status'],
      // 订单域时间戳为服务器本地 LocalDateTime（与 created_at 同口径），datetime-local 直接补秒不做时区换算
      occurredAt: eventForm.value.occurredAt ? `${eventForm.value.occurredAt}:00` : null,
      location: eventForm.value.location.trim() || null,
      description,
    })
    toast.success(eventForm.value.status === ShipmentStatus.DELIVERED ? '轨迹已添加，包裹已签收' : '轨迹已添加')
    addingEvent.value = false
  } catch (e) {
    if (e instanceof BizError && e.code === 422601) {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) eventErrors.value = fields
      else toast.error(describeError(e))
    } else {
      toast.error(describeError(e, '添加轨迹失败'))
      if (e instanceof BizError && (e.code === 409909 || e.code === 404907)) store.refreshDetail(props.orderId).catch(() => undefined)
    }
  } finally {
    eventSaving.value = false
  }
}

// ---- 签收 / 作废（二次确认） ----
const confirm = ref<'deliver' | 'cancel' | null>(null)
const confirmBusy = ref(false)

async function doConfirm() {
  if (!confirm.value || confirmBusy.value) return
  confirmBusy.value = true
  const kind = confirm.value
  try {
    if (kind === 'deliver') {
      await store.deliverShipment(props.orderId, s.value.id)
      toast.success('包裹已标记签收')
    } else {
      await store.cancelShipment(props.orderId, s.value.id)
      toast.success('包裹已作废，分配已释放')
    }
    confirm.value = null
  } catch (e) {
    toast.error(describeError(e, kind === 'deliver' ? '签收失败' : '作废失败'))
    if (e instanceof BizError && (e.code === 409909 || e.code === 404907 || e.code === 409602)) {
      confirm.value = null
      store.refreshDetail(props.orderId).catch(() => undefined)
    }
  } finally {
    confirmBusy.value = false
  }
}

// ---- 同步 ----
const syncing = ref(false)
async function doSync() {
  if (syncing.value) return
  syncing.value = true
  try {
    const res = await store.syncShipment(props.orderId, s.value.id)
    if (res == null) toast.info('当前轨迹供应商为 stub 模式，无可同步数据')
    else toast.success('轨迹已同步')
  } catch (e) {
    toast.error(describeError(e, '同步失败'))
  } finally {
    syncing.value = false
  }
}
</script>

<template>
  <div class="rounded-luxe border border-line" :data-testid="`shipment-card-${s.id}`">
    <!-- 头部 -->
    <div class="flex flex-wrap items-center gap-3 border-b border-line bg-canvas-warm/40 px-4 py-3">
      <div class="min-w-0 flex-1">
        <p class="flex flex-wrap items-center gap-2 text-[13px] font-medium text-ink">
          <span>{{ s.carrierName || s.carrierCode }}</span>
          <span class="rounded bg-ink/8 px-1.5 py-0.5 font-mono text-[10px] text-ink-soft">{{ s.carrierCode }}</span>
          <StatusBadge :tone="shipmentStatusMeta(s.status).tone" :label="shipmentStatusMeta(s.status).label" />
        </p>
        <p class="mt-0.5 flex items-center gap-1.5 font-mono text-[12px] text-ink-soft">
          <span>{{ s.shipmentNo }}</span>
          <span class="text-ink-faint">·</span>
          <a v-if="s.trackingUrl" :href="s.trackingUrl" target="_blank" rel="noopener" class="inline-flex items-center gap-1 text-gold-deep hover:underline">
            {{ s.trackingNo }}<ArrowTopRightOnSquareIcon class="h-3 w-3" />
          </a>
          <span v-else>{{ s.trackingNo }}</span>
        </p>
        <p class="mt-0.5 text-[11px] text-ink-faint">
          发出 {{ formatDateTime(s.shippedAt) }}
          <template v-if="s.deliveredAt"> · 签收 {{ formatDateTime(s.deliveredAt) }}</template>
          <template v-else-if="s.lastEventDesc"> · 最新：{{ s.lastEventDesc }}（{{ formatDateTime(s.lastEventAt) }}）</template>
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <button v-if="canEdit" class="btn-ghost" :disabled="editSaving" title="编辑承运商/单号" @click="openEdit"><PencilSquareIcon class="h-4 w-4" />编辑</button>
        <button v-if="canEdit" class="btn-ghost" :disabled="eventSaving" title="添加轨迹事件" @click="openAddEvent"><PlusIcon class="h-4 w-4" />轨迹</button>
        <button v-if="canSync" class="btn-ghost" :disabled="syncing" title="拉取供应商轨迹" @click="doSync"><ArrowPathIcon class="h-4 w-4" :class="syncing && 'animate-spin'" />{{ syncing ? '同步中' : '同步' }}</button>
        <button v-if="canDeliver" class="btn-outline" :disabled="confirmBusy" @click="confirm = 'deliver'"><CheckIcon class="h-4 w-4" />标记签收</button>
        <button v-if="canCancel" class="btn-danger-ghost" :disabled="confirmBusy" title="作废包裹并释放分配" @click="confirm = 'cancel'"><NoSymbolIcon class="h-4 w-4" />作废</button>
      </div>
    </div>

    <!-- 编辑单号 -->
    <div v-if="editing" class="border-b border-line bg-gold/5 px-4 py-3">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_1fr_auto]">
        <div>
          <label class="field-label">承运商</label>
          <SelectMenu :model-value="editForm.carrierCode" :options="carrierOptions" @update:model-value="editForm.carrierCode = String($event ?? '')" />
          <p v-if="editErrors.carrierCode" class="mt-1 text-[11px] text-danger">{{ editErrors.carrierCode }}</p>
        </div>
        <div>
          <label class="field-label">运单号</label>
          <input v-model="editForm.trackingNo" class="field" />
          <p v-if="editErrors.trackingNo" class="mt-1 text-[11px] text-danger">{{ editErrors.trackingNo }}</p>
        </div>
        <div class="flex items-end gap-1">
          <button class="btn-gold" :disabled="editSaving" @click="saveEdit"><CheckIcon class="h-4 w-4" />{{ editSaving ? '保存中…' : '保存' }}</button>
          <button class="btn-ghost" :disabled="editSaving" @click="editing = false"><XMarkIcon class="h-4 w-4" /></button>
        </div>
      </div>
      <p class="mt-1.5 text-[11px] text-ink-faint">仅允许修改承运商与单号；分配行不可变，如需纠错请作废后重建。</p>
    </div>

    <!-- 添加轨迹 -->
    <div v-if="addingEvent" class="border-b border-line bg-gold/5 px-4 py-3" data-testid="shipment-event-form">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label class="field-label">状态 *</label>
          <SelectMenu :model-value="eventForm.status" :options="SHIPMENT_EVENT_STATUS_OPTIONS" @update:model-value="eventForm.status = Number($event)" />
        </div>
        <div>
          <label class="field-label">发生时间（留空 = 现在）</label>
          <input v-model="eventForm.occurredAt" type="datetime-local" class="field" />
        </div>
        <div>
          <label class="field-label">地点</label>
          <input v-model="eventForm.location" class="field" maxlength="128" placeholder="如 Shanghai Hub" data-testid="shipment-event-location" />
          <p v-if="eventErrors.location" class="mt-1 text-[11px] text-danger">{{ eventErrors.location }}</p>
        </div>
        <div>
          <label class="field-label">描述 *</label>
          <input v-model="eventForm.description" class="field" maxlength="255" placeholder="如 Departed facility" data-testid="shipment-event-desc" />
          <p v-if="eventErrors.description" class="mt-1 text-[11px] text-danger">{{ eventErrors.description }}</p>
        </div>
      </div>
      <div class="mt-3 flex justify-end gap-2">
        <button class="btn-ghost" :disabled="eventSaving" @click="addingEvent = false">取消</button>
        <button class="btn-gold" :disabled="eventSaving" data-testid="shipment-event-submit" @click="saveEvent"><CheckIcon class="h-4 w-4" />{{ eventSaving ? '提交中…' : '添加轨迹' }}</button>
      </div>
    </div>

    <!-- 行明细 -->
    <div class="px-4 py-3">
      <p class="mb-1.5 text-[11px] font-medium uppercase tracking-luxe text-ink-faint">包裹内容</p>
      <ul class="space-y-1 text-[12px] text-ink-soft">
        <li v-for="l in s.lines" :key="l.orderLineId" class="flex items-center justify-between gap-3">
          <span class="truncate">{{ l.productName || `行 #${l.orderLineId}` }} <span class="text-ink-faint">{{ l.skuCode || '' }} {{ l.color || '' }} {{ l.size || '' }}</span></span>
          <span class="shrink-0 font-medium text-ink">× {{ l.qty }}</span>
        </li>
      </ul>
    </div>

    <!-- 轨迹 -->
    <div class="border-t border-line px-4 py-3">
      <button class="flex w-full items-center justify-between text-left" @click="showEvents = !showEvents">
        <span class="text-[11px] font-medium uppercase tracking-luxe text-ink-faint">轨迹（{{ events.length }}）</span>
        <component :is="showEvents ? ChevronUpIcon : ChevronDownIcon" class="h-4 w-4 text-ink-faint" />
      </button>
      <ol v-if="showEvents" class="mt-2 space-y-2" data-testid="shipment-events">
        <li v-if="!events.length" class="text-[12px] text-ink-faint">暂无轨迹</li>
        <li v-for="ev in events" :key="ev.id" class="flex gap-2 text-[12px]">
          <MapPinIcon class="mt-0.5 h-3.5 w-3.5 shrink-0 text-gold-deep" />
          <div class="min-w-0 flex-1">
            <p class="text-ink">
              <StatusBadge :tone="shipmentStatusMeta(ev.status).tone" :label="shipmentStatusMeta(ev.status).label" :dot="false" />
              <span class="ml-1.5">{{ ev.description || '—' }}</span>
            </p>
            <p class="text-[11px] text-ink-faint">
              {{ formatDateTime(ev.occurredAt) }}<template v-if="ev.location"> · {{ ev.location }}</template>
              · {{ SHIPMENT_EVENT_SOURCE_LABEL[ev.source] || '—' }}
            </p>
          </div>
        </li>
      </ol>
    </div>

    <ConfirmDialog
      :open="!!confirm"
      :title="confirm === 'deliver' ? '标记签收' : '作废包裹'"
      :message="confirm === 'deliver'
        ? `确认包裹 ${s.trackingNo} 已签收？若订单全部包裹均签收，订单将自动进入「已签收」。`
        : `确认作废包裹 ${s.trackingNo}？分配数量将释放，若订单已发货且存在未发行将回退为「已付款」。`"
      :confirm-text="confirm === 'deliver' ? '确认签收' : '确认作废'"
      :danger="confirm === 'cancel'"
      :busy="confirmBusy"
      @confirm="doConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>
