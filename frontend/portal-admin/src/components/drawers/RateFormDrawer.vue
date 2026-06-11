<script setup lang="ts">
// COMP-SHP-05 RateFormDrawer（FORM-SHP-02 载体）：zone*/fee_under/fee_over/threshold
// 409901 → zone inline「同名规则行已存在」；422901 → 字段分发
import { ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateRateForm, type FieldErrors } from '@/utils/validators'
import type { ShippingRate } from '@/api/types'

const props = defineProps<{ open: boolean; editing: ShippingRate | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useShippingStore()
const toast = useToastStore()

const form = ref<{ zone: string; feeUnder: string; feeOver: string; threshold: string }>({
  zone: '',
  feeUnder: '',
  feeOver: '',
  threshold: '',
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    errors.value = {}
    form.value = props.editing
      ? {
          zone: props.editing.zone,
          feeUnder: props.editing.feeUnder == null ? '' : String(props.editing.feeUnder),
          feeOver: props.editing.feeOver == null ? '' : String(props.editing.feeOver),
          threshold: props.editing.threshold == null ? '' : String(props.editing.threshold),
        }
      : { zone: '', feeUnder: '', feeOver: '', threshold: '' }
  },
)

async function submit() {
  errors.value = validateRateForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await store.saveRate(
      {
        zone: form.value.zone.trim(),
        feeUnder: form.value.feeUnder === '' ? null : form.value.feeUnder,
        feeOver: form.value.feeOver === '' ? null : form.value.feeOver,
        threshold: form.value.threshold === '' ? null : form.value.threshold,
      },
      props.editing?.id,
    )
    toast.success('已保存')
    emit('saved')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 409901) {
      errors.value = { zone: '同名规则行已存在' }
    } else if (e instanceof BizError && e.code === 422901) {
      errors.value = extractFieldErrors(e)
      if (!Object.keys(errors.value).length) toast.error(e.message)
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell :open="open" eyebrow="Shipping" :title="editing ? '编辑邮费规则' : '添加邮费规则'" width="max-w-md" @close="emit('close')">
    <div class="space-y-4">
      <div>
        <label class="field-label">区域 *</label>
        <input v-model="form.zone" class="field" placeholder="如 US / Rest of World / US / FedEx" />
        <p v-if="errors.zone" class="mt-1 text-[11px] text-danger">{{ errors.zone }}</p>
        <!-- F-036 约定运营可见化 -->
        <p class="mt-1.5 text-[11px] text-ink-faint">「区域 / 承运商名」= 该承运商专属价；仅填区域 = 该区域全部启用承运商兜底价。</p>
      </div>
      <div>
        <label class="field-label">基础邮费（未满门槛，USD）</label>
        <input v-model="form.feeUnder" type="number" min="0" step="0.01" class="field" placeholder="留空显示 —" />
        <p v-if="errors.feeUnder" class="mt-1 text-[11px] text-danger">{{ errors.feeUnder }}</p>
      </div>
      <div>
        <label class="field-label">满额邮费（达门槛，USD，0 = 免邮）</label>
        <input v-model="form.feeOver" type="number" min="0" step="0.01" class="field" placeholder="0 表示免邮" />
        <p v-if="errors.feeOver" class="mt-1 text-[11px] text-danger">{{ errors.feeOver }}</p>
      </div>
      <div>
        <label class="field-label">门槛金额（USD）</label>
        <input v-model="form.threshold" type="number" min="0" step="0.01" class="field" placeholder="留空 = 无门槛" />
        <p v-if="errors.threshold" class="mt-1 text-[11px] text-danger">{{ errors.threshold }}</p>
      </div>
    </div>
    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
