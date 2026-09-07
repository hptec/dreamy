<script setup lang="ts">
// order-flow-complete J：创建包裹抽屉（承运商 code 下拉 + 单号 + 按行分配数量；默认全部未发数量，显示已发/剩余）
// 错误：422906 超量 → 行内；409908 重复单号 → trackingNo 行内；409906 锁冲突 → toast 重试；409602 状态不允许 → toast + 刷新
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useOrdersStore } from '@/stores/orders'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, validateShipmentForm, type FieldErrors } from '@/utils/validators'
import { CarrierStatus, ShipmentStatus } from '@/api/types'
import type { AdminOrderDetail } from '@/api/types'

const props = defineProps<{ open: boolean; order: AdminOrderDetail | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'created'): void }>()

const ordersStore = useOrdersStore()
const shippingStore = useShippingStore()
const toast = useToastStore()

interface LineDraft {
  orderLineId: number
  productName: string
  skuCode: string
  color: string
  size: string
  qty: number
  shipped: number
  remaining: number
  alloc: number | string
}

const form = ref<{ carrierCode: string; trackingNo: string }>({ carrierCode: '', trackingNo: '' })
const lines = ref<LineDraft[]>([])
const errors = ref<FieldErrors>({})
const saving = ref(false)

/** 已配置 code 且启用的承运商（创建包裹只能选有编码的承运商） */
const carrierOptions = computed(() =>
  shippingStore.carriers
    .filter((c) => c.status === CarrierStatus.ENABLED && !!c.code)
    .map((c) => ({ value: c.code as string, label: `${c.name}（${c.code}）` })),
)

function buildLines(order: AdminOrderDetail): LineDraft[] {
  const shippedByLine = new Map<number, number>()
  for (const s of order.shipments ?? []) {
    if (s.status === ShipmentStatus.CANCELLED) continue
    for (const l of s.lines ?? []) {
      shippedByLine.set(l.orderLineId, (shippedByLine.get(l.orderLineId) ?? 0) + l.qty)
    }
  }
  return order.lines.map((l) => {
    const shipped = shippedByLine.get(l.id) ?? 0
    const remaining = Math.max(0, l.qty - shipped)
    return {
      orderLineId: l.id,
      productName: l.productName,
      skuCode: l.skuCode || '—',
      color: l.color || '—',
      size: l.size || (l.customSizeData ? 'Custom' : '—'),
      qty: l.qty,
      shipped,
      remaining,
      alloc: remaining,
    }
  })
}

watch(
  () => props.open,
  async (open) => {
    if (!open || !props.order) return
    errors.value = {}
    lines.value = buildLines(props.order)
    if (!shippingStore.carriers.length) {
      try {
        await shippingStore.fetchCarriers()
      } catch (e) {
        toast.error(describeError(e, '加载承运商失败'))
      }
    }
    const preferred = shippingStore.carriers.find(
      (c) => c.status === CarrierStatus.ENABLED && !!c.code && (c.name === props.order?.carrier || c.code === props.order?.carrier),
    )
    form.value = { carrierCode: preferred?.code || (carrierOptions.value[0]?.value ?? ''), trackingNo: '' }
  },
)

const totalAlloc = computed(() => lines.value.reduce((s, l) => s + (Number(l.alloc) || 0), 0))
const totalRemaining = computed(() => lines.value.reduce((s, l) => s + l.remaining, 0))
const isFull = computed(() => totalRemaining.value > 0 && totalAlloc.value === totalRemaining.value)

function fillAll() {
  for (const l of lines.value) l.alloc = l.remaining
}

async function submit() {
  if (!props.order || saving.value) return
  errors.value = validateShipmentForm(
    { carrierCode: form.value.carrierCode, trackingNo: form.value.trackingNo, lines: lines.value.map((l) => ({ orderLineId: l.orderLineId, qty: l.alloc, remaining: l.remaining })) },
    carrierOptions.value.length > 0,
  )
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    const body = {
      carrierCode: form.value.carrierCode,
      trackingNo: form.value.trackingNo.trim(),
      lines: lines.value.filter((l) => Number(l.alloc) > 0).map((l) => ({ orderLineId: l.orderLineId, qty: Number(l.alloc) })),
    }
    await ordersStore.createShipment(props.order.id, body)
    toast.success(isFull.value ? '包裹已创建，订单已全部发出' : '包裹已创建（部分发货）')
    emit('created')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 422906) {
      errors.value = { lines: describeError(e) }
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) errors.value = { ...errors.value, ...fields }
    } else if (e instanceof BizError && e.code === 409908) {
      errors.value = { trackingNo: describeError(e) }
    } else if (e instanceof BizError && e.code === 409602) {
      toast.error(describeError(e))
      ordersStore.refreshDetail(props.order.id).catch(() => undefined)
      emit('close')
    } else if (e instanceof BizError && e.code === 422601) {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) errors.value = fields
      else toast.error(describeError(e))
    } else {
      toast.error(describeError(e, '创建包裹失败'))
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell :open="open" eyebrow="Shipment" title="创建包裹" width="max-w-2xl" @close="emit('close')">
    <div class="space-y-5">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">承运商 *</label>
          <SelectMenu
            :model-value="form.carrierCode"
            :options="carrierOptions"
            placeholder="选择承运商"
            @update:model-value="form.carrierCode = String($event ?? '')"
          />
          <p v-if="errors.carrierCode" class="mt-1 text-[11px] text-danger">{{ errors.carrierCode }}</p>
        </div>
        <div>
          <label class="field-label">运单号 *</label>
          <input v-model="form.trackingNo" class="field" placeholder="输入 tracking number" data-testid="shipment-tracking-no" />
          <p v-if="errors.trackingNo" class="mt-1 text-[11px] text-danger">{{ errors.trackingNo }}</p>
        </div>
      </div>

      <div>
        <div class="mb-2 flex items-center justify-between">
          <label class="field-label mb-0">按行分配数量</label>
          <button class="btn-ghost text-[12px]" type="button" @click="fillAll">全部未发数量</button>
        </div>
        <table class="data-table">
          <thead>
            <tr><th>商品</th><th class="text-right">订购</th><th class="text-right">已发</th><th class="text-right">剩余</th><th class="text-right">本次发出</th></tr>
          </thead>
          <tbody>
            <tr v-for="l in lines" :key="l.orderLineId" :class="l.remaining === 0 ? 'opacity-50' : ''">
              <td>
                <p class="font-medium text-ink">{{ l.productName }}</p>
                <p class="text-[11px] text-ink-faint">{{ l.skuCode }} · {{ l.color }} · {{ l.size }}</p>
              </td>
              <td class="text-right">{{ l.qty }}</td>
              <td class="text-right text-ink-soft">{{ l.shipped }}</td>
              <td class="text-right" :class="l.remaining > 0 ? 'text-ink' : 'text-ink-faint'">{{ l.remaining }}</td>
              <td class="text-right">
                <input
                  v-model="l.alloc"
                  type="number"
                  min="0"
                  :max="l.remaining"
                  step="1"
                  class="field w-20 px-2 py-1 text-right text-[12px]"
                  :disabled="l.remaining === 0"
                  :data-testid="`shipment-line-qty-${l.orderLineId}`"
                />
                <p v-if="errors[`line_${l.orderLineId}`]" class="mt-1 text-[11px] text-danger">{{ errors[`line_${l.orderLineId}`] }}</p>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="errors.lines" class="mt-2 text-[11px] text-danger">{{ errors.lines }}</p>
        <p class="mt-2 text-[11px] text-ink-faint">
          本次发出 {{ totalAlloc }} / 剩余 {{ totalRemaining }} 件。
          <template v-if="isFull">全部未发行将随本包裹发出，订单状态将变为「已发货」。</template>
          <template v-else-if="totalAlloc > 0">仍有未发行，订单保持「已付款」，可继续创建包裹。</template>
        </p>
      </div>
    </div>
    <template #footer>
      <button class="btn-outline" :disabled="saving" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" data-testid="shipment-submit" @click="submit">{{ saving ? '创建中…' : '创建包裹' }}</button>
    </template>
  </DrawerShell>
</template>
