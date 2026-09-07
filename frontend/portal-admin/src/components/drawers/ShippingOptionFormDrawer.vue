<script setup lang="ts">
// order-flow-complete D：运费选项抽屉（替代 RateFormDrawer）：zone*/carrier_code*/service_level*/fee_under*/fee_over/threshold/transit_days_min~max/enabled
// 409904 → zone inline「同分区、承运商与服务等级的运费选项已存在」；422901 → 字段分发
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import Toggle from '@/components/Toggle.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, validateShippingOptionForm, type FieldErrors } from '@/utils/validators'
import { SERVICE_LEVEL_OPTIONS, ZONE_OPTIONS } from '@/utils/tradingLabels'
import { ShippingServiceLevel } from '@/api/types'
import type { ShippingOption } from '@/api/types'

const props = defineProps<{ open: boolean; editing: ShippingOption | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useShippingStore()
const toast = useToastStore()

interface FormState {
  zone: string
  carrierCode: string
  serviceLevel: number
  feeUnder: string
  feeOver: string
  threshold: string
  transitDaysMin: string
  transitDaysMax: string
  enabled: boolean
}

const blank = (): FormState => ({
  zone: '', carrierCode: '', serviceLevel: ShippingServiceLevel.STANDARD, feeUnder: '', feeOver: '', threshold: '', transitDaysMin: '', transitDaysMax: '', enabled: true,
})
const form = ref<FormState>(blank())
const errors = ref<FieldErrors>({})
const saving = ref(false)

const carrierOptions = computed(() => [
  { value: 'ANY', label: '任意承运商（ANY，兜底价）' },
  ...store.carriers.filter((c) => !!c.code).map((c) => ({ value: c.code as string, label: `${c.name}（${c.code}）` })),
])

const str = (v?: number | null) => (v == null ? '' : String(v))

watch(
  () => props.open,
  (open) => {
    if (!open) return
    errors.value = {}
    form.value = props.editing
      ? {
          zone: props.editing.zone,
          carrierCode: props.editing.carrierCode,
          serviceLevel: props.editing.serviceLevel,
          feeUnder: str(props.editing.feeUnder),
          feeOver: str(props.editing.feeOver),
          threshold: str(props.editing.threshold),
          transitDaysMin: str(props.editing.transitDaysMin),
          transitDaysMax: str(props.editing.transitDaysMax),
          enabled: props.editing.enabled,
        }
      : blank()
  },
)

async function submit() {
  if (saving.value) return
  errors.value = validateShippingOptionForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await store.saveOption(
      {
        zone: form.value.zone,
        carrierCode: form.value.carrierCode,
        serviceLevel: form.value.serviceLevel as ShippingOption['serviceLevel'],
        feeUnder: form.value.feeUnder,
        feeOver: form.value.feeOver === '' ? null : form.value.feeOver,
        threshold: form.value.threshold === '' ? null : form.value.threshold,
        transitDaysMin: form.value.transitDaysMin === '' ? null : Number(form.value.transitDaysMin),
        transitDaysMax: form.value.transitDaysMax === '' ? null : Number(form.value.transitDaysMax),
        enabled: form.value.enabled,
      },
      props.editing?.id,
    )
    toast.success('运费选项已保存')
    emit('saved')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 409904) {
      errors.value = { zone: describeError(e) }
    } else if (e instanceof BizError && (e.code === 422901 || e.code === 422601)) {
      errors.value = extractFieldErrors(e)
      if (!Object.keys(errors.value).length) toast.error(e.message)
    } else if (e instanceof BizError && e.code === 404903) {
      toast.error(describeError(e))
      emit('saved')
      emit('close')
    } else {
      toast.error(describeError(e))
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell :open="open" eyebrow="Shipping" :title="editing ? '编辑运费选项' : '添加运费选项'" width="max-w-md" @close="emit('close')">
    <div class="space-y-4">
      <div>
        <label class="field-label">分区 *</label>
        <SelectMenu :model-value="form.zone" :options="ZONE_OPTIONS" placeholder="选择分区" :disabled="!!editing" @update:model-value="form.zone = String($event ?? '')" />
        <p v-if="errors.zone" class="mt-1 text-[11px] text-danger">{{ errors.zone }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">ISO 国家按 8 区自动归属；分区 × 承运商 × 服务等级唯一。</p>
      </div>
      <div>
        <label class="field-label">承运商 *</label>
        <SelectMenu :model-value="form.carrierCode" :options="carrierOptions" placeholder="选择承运商" :disabled="!!editing" @update:model-value="form.carrierCode = String($event ?? '')" />
        <p v-if="errors.carrierCode" class="mt-1 text-[11px] text-danger">{{ errors.carrierCode }}</p>
      </div>
      <div>
        <label class="field-label">服务等级 *</label>
        <SelectMenu :model-value="form.serviceLevel" :options="SERVICE_LEVEL_OPTIONS" :disabled="!!editing" @update:model-value="form.serviceLevel = Number($event)" />
        <p v-if="errors.serviceLevel" class="mt-1 text-[11px] text-danger">{{ errors.serviceLevel }}</p>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">基础运费（USD）*</label>
          <input v-model="form.feeUnder" type="number" min="0" step="0.01" class="field" placeholder="未满门槛" data-testid="option-fee-under" />
          <p v-if="errors.feeUnder" class="mt-1 text-[11px] text-danger">{{ errors.feeUnder }}</p>
        </div>
        <div>
          <label class="field-label">满额运费（USD，0 = 免邮）</label>
          <input v-model="form.feeOver" type="number" min="0" step="0.01" class="field" placeholder="留空 = 与基础相同" />
          <p v-if="errors.feeOver" class="mt-1 text-[11px] text-danger">{{ errors.feeOver }}</p>
        </div>
      </div>
      <div>
        <label class="field-label">门槛金额（USD）</label>
        <input v-model="form.threshold" type="number" min="0" step="0.01" class="field" placeholder="留空 = 无门槛" />
        <p v-if="errors.threshold" class="mt-1 text-[11px] text-danger">{{ errors.threshold }}</p>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">运输天数（最短）</label>
          <input v-model="form.transitDaysMin" type="number" min="0" max="365" step="1" class="field" placeholder="如 5" data-testid="option-transit-min" />
          <p v-if="errors.transitDaysMin" class="mt-1 text-[11px] text-danger">{{ errors.transitDaysMin }}</p>
        </div>
        <div>
          <label class="field-label">运输天数（最长）</label>
          <input v-model="form.transitDaysMax" type="number" min="0" max="365" step="1" class="field" placeholder="如 8" data-testid="option-transit-max" />
          <p v-if="errors.transitDaysMax" class="mt-1 text-[11px] text-danger">{{ errors.transitDaysMax }}</p>
        </div>
      </div>
      <p class="text-[11px] text-ink-faint">预计送达 = 下单日 + 制作周期 + 运输天数区间。</p>
      <label class="flex items-center gap-2 text-[13px] text-ink-soft">
        <Toggle v-model="form.enabled" />启用（结算页可选）
      </label>
    </div>
    <template #footer>
      <button class="btn-outline" :disabled="saving" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" data-testid="option-submit" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
