<script setup lang="ts">
// order-flow-complete F：税率规则抽屉（国家/地区/税种/税率%/含运费/起征额/生效窗口/启用/标签）
// 422907 生效窗口重叠 → effectiveFrom inline + toast；422601 → 字段分发；404906 → toast + 关闭刷新
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import Toggle from '@/components/Toggle.vue'
import { useTradingSettingsStore } from '@/stores/tradingSettings'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, validateTaxRuleForm, type FieldErrors } from '@/utils/validators'
import { TAX_TYPE_OPTIONS } from '@/utils/tradingLabels'
import { TaxType } from '@/api/types'
import type { TaxRule } from '@/api/types'

const props = defineProps<{ open: boolean; editing: TaxRule | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useTradingSettingsStore()
const shippingStore = useShippingStore()
const toast = useToastStore()

interface FormState {
  countryCode: string
  region: string
  taxType: number
  ratePercent: string
  appliesToShipping: boolean
  thresholdUsd: string
  effectiveFrom: string
  effectiveTo: string
  enabled: boolean
  label: string
}
const blank = (): FormState => ({
  countryCode: '', region: '', taxType: TaxType.VAT, ratePercent: '', appliesToShipping: true, thresholdUsd: '', effectiveFrom: '', effectiveTo: '', enabled: true, label: '',
})
const form = ref<FormState>(blank())
const errors = ref<FieldErrors>({})
const saving = ref(false)

const countryOptions = computed(() => shippingStore.countries.map((c) => ({ value: c.code, label: `${c.name}（${c.code}）` })))
const regionOptions = computed(() => {
  const c = shippingStore.countries.find((x) => x.code === form.value.countryCode)
  const regions = c?.regions ?? []
  return [{ value: '', label: '国家级（不限州/省）' }, ...regions.map((r) => ({ value: r.code, label: `${r.name}（${r.code}）` }))]
})

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    errors.value = {}
    if (!shippingStore.countries.length) shippingStore.ensureCountries().catch((e) => toast.error(describeError(e, '加载国家列表失败')))
    form.value = props.editing
      ? {
          countryCode: props.editing.countryCode,
          region: props.editing.region || '',
          taxType: props.editing.taxType,
          ratePercent: String(props.editing.rateScaled / 100),
          appliesToShipping: props.editing.appliesToShipping,
          thresholdUsd: props.editing.thresholdUsd == null ? '' : String(props.editing.thresholdUsd),
          effectiveFrom: props.editing.effectiveFrom || '',
          effectiveTo: props.editing.effectiveTo || '',
          enabled: props.editing.enabled,
          label: props.editing.label || '',
        }
      : blank()
  },
)

async function submit() {
  if (saving.value) return
  errors.value = validateTaxRuleForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await store.saveTaxRule(
      {
        countryCode: form.value.countryCode,
        region: form.value.region.trim(),
        taxType: form.value.taxType as TaxRule['taxType'],
        rateScaled: Math.round(Number(form.value.ratePercent) * 100),
        appliesToShipping: form.value.appliesToShipping,
        thresholdUsd: form.value.thresholdUsd === '' ? null : form.value.thresholdUsd,
        effectiveFrom: form.value.effectiveFrom || null,
        effectiveTo: form.value.effectiveTo || null,
        enabled: form.value.enabled,
        label: form.value.label.trim() || null,
      },
      props.editing?.id,
    )
    toast.success('税率规则已保存')
    emit('saved')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 422907) {
      errors.value = { effectiveFrom: describeError(e) }
      toast.error(describeError(e))
    } else if (e instanceof BizError && e.code === 422601) {
      const fields = extractFieldErrors(e)
      if (fields.rateScaled) fields.ratePercent = fields.rateScaled
      errors.value = fields
      if (!Object.keys(fields).length) toast.error(describeError(e))
    } else if (e instanceof BizError && e.code === 404906) {
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
  <DrawerShell :open="open" eyebrow="Tax" :title="editing ? '编辑税率规则' : '新建税率规则'" width="max-w-md" @close="emit('close')">
    <div class="space-y-4">
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">国家 *</label>
          <SelectMenu :model-value="form.countryCode" :options="countryOptions" :disabled="!!editing || shippingStore.loadingCountries" placeholder="选择国家" data-testid="tax-country" @update:model-value="form.countryCode = String($event ?? ''); form.region = ''" />
          <p v-if="errors.countryCode" class="mt-1 text-[11px] text-danger">{{ errors.countryCode }}</p>
        </div>
        <div>
          <label class="field-label">州/省</label>
          <SelectMenu :model-value="form.region" :options="regionOptions" :disabled="!!editing || regionOptions.length <= 1" data-testid="tax-region" @update:model-value="form.region = String($event ?? '')" />
          <p v-if="errors.region" class="mt-1 text-[11px] text-danger">{{ errors.region }}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">税种 *</label>
          <SelectMenu :model-value="form.taxType" :options="TAX_TYPE_OPTIONS" :disabled="!!editing" data-testid="tax-type" @update:model-value="form.taxType = Number($event)" />
          <p v-if="errors.taxType" class="mt-1 text-[11px] text-danger">{{ errors.taxType }}</p>
        </div>
        <div>
          <label class="field-label">税率（%）*</label>
          <input v-model="form.ratePercent" type="number" min="0" max="100" step="0.01" class="field" placeholder="如 20" data-testid="tax-rate" />
          <p v-if="errors.ratePercent" class="mt-1 text-[11px] text-danger">{{ errors.ratePercent }}</p>
        </div>
      </div>
      <p class="text-[11px] text-ink-faint">同一国家 + 州/省 + 税种唯一；州/省级规则优先于国家级。</p>
      <div>
        <label class="field-label">起征额（USD）</label>
        <input v-model="form.thresholdUsd" type="number" min="0" step="0.01" class="field" placeholder="留空 = 无起征额" data-testid="tax-threshold" />
        <p v-if="errors.thresholdUsd" class="mt-1 text-[11px] text-danger">{{ errors.thresholdUsd }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">计税基数（USD 折算）低于起征额时不计税。</p>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">生效开始</label>
          <input v-model="form.effectiveFrom" type="date" class="field" data-testid="tax-from" />
          <p v-if="errors.effectiveFrom" class="mt-1 text-[11px] text-danger">{{ errors.effectiveFrom }}</p>
        </div>
        <div>
          <label class="field-label">生效结束</label>
          <input v-model="form.effectiveTo" type="date" class="field" data-testid="tax-to" />
          <p v-if="errors.effectiveTo" class="mt-1 text-[11px] text-danger">{{ errors.effectiveTo }}</p>
        </div>
      </div>
      <p class="text-[11px] text-ink-faint">留空 = 不限；同键规则的生效窗口不可重叠（422907）。</p>
      <div>
        <label class="field-label">标签</label>
        <input v-model="form.label" class="field" maxlength="64" placeholder="如 VAT 20%（前台税费行展示）" data-testid="tax-label" />
        <p v-if="errors.label" class="mt-1 text-[11px] text-danger">{{ errors.label }}</p>
      </div>
      <div class="flex flex-wrap gap-6 text-[13px] text-ink-soft">
        <label class="flex items-center gap-2"><Toggle v-model="form.appliesToShipping" />运费计入计税基数</label>
        <label class="flex items-center gap-2"><Toggle v-model="form.enabled" />启用</label>
      </div>
    </div>
    <template #footer>
      <button class="btn-outline" :disabled="saving" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" data-testid="tax-submit" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
