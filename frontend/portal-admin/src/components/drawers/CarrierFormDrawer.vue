<script setup lang="ts">
// COMP-SHP-04 CarrierFormDrawer（FORM-SHP-01 载体）：name*/zones/lead_time/status
// order-flow-complete C：追加 code（大写字母数字，包裹创建/运费选项以此关联）与 tracking_url_template（{tracking_no} 占位）
// 422901 → details.field 分发 inline；409902（编辑中改 disabled）→ status 字段 inline；409903 → code inline
import { ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, validateCarrierForm, type FieldErrors } from '@/utils/validators'
import { CarrierStatus } from '@/api/types'
import type { Carrier } from '@/api/types'

const props = defineProps<{ open: boolean; editing: Carrier | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useShippingStore()
const toast = useToastStore()

const form = ref({
  name: '',
  code: '',
  trackingUrlTemplate: '',
  zones: '',
  leadTime: '',
  status: CarrierStatus.ENABLED as CarrierStatus,
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
          name: props.editing.name,
          code: props.editing.code || '',
          trackingUrlTemplate: props.editing.trackingUrlTemplate || '',
          zones: props.editing.zones || '',
          leadTime: props.editing.leadTime || '',
          status: props.editing.status,
        }
      : { name: '', code: '', trackingUrlTemplate: '', zones: '', leadTime: '', status: CarrierStatus.ENABLED }
  },
)

function onCodeInput() {
  form.value.code = form.value.code.toUpperCase().replace(/[^A-Z0-9]/g, '')
}

async function submit() {
  if (saving.value) return
  errors.value = validateCarrierForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    const saved = await store.saveCarrier(
      {
        name: form.value.name.trim(),
        code: form.value.code.trim() || null,
        trackingUrlTemplate: form.value.trackingUrlTemplate.trim() || null,
        zones: form.value.zones.trim() || null,
        leadTime: form.value.leadTime.trim() || null,
        status: form.value.status,
      },
      props.editing?.id,
    )
    toast.success('已保存')
    if (props.editing && props.editing.code && props.editing.code !== saved.code) {
      toast.info('承运商编码已变更，请同步检查运费选项与历史包裹关联')
    }
    emit('saved')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 409902) {
      errors.value = { status: '至少保留一个启用的承运方' }
    } else if (e instanceof BizError && e.code === 409903) {
      errors.value = { code: describeError(e) }
    } else if (e instanceof BizError && e.code === 422901) {
      errors.value = extractFieldErrors(e)
      if (!Object.keys(errors.value).length) toast.error(e.message)
    } else {
      toast.error(describeError(e))
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell :open="open" eyebrow="Shipping" :title="editing ? '编辑承运方' : '添加承运方'" width="max-w-md" @close="emit('close')">
    <div class="space-y-4">
      <div>
        <label class="field-label">承运方名称 *</label>
        <input v-model="form.name" class="field" placeholder="如 FedEx International Priority" />
        <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
      </div>
      <div>
        <label class="field-label">承运商编码</label>
        <input v-model="form.code" class="field font-mono uppercase" placeholder="如 FEDEX / UPS / DHL" maxlength="32" data-testid="carrier-code" @input="onCodeInput" />
        <p v-if="errors.code" class="mt-1 text-[11px] text-danger">{{ errors.code }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">大写字母与数字，全局唯一；运费选项与包裹创建以编码关联，未设置编码的承运商不可用于发货。</p>
      </div>
      <div>
        <label class="field-label">跟踪链接模板</label>
        <input v-model="form.trackingUrlTemplate" class="field" placeholder="https://…?tracking={tracking_no}" maxlength="255" data-testid="carrier-tracking-template" />
        <p v-if="errors.trackingUrlTemplate" class="mt-1 text-[11px] text-danger">{{ errors.trackingUrlTemplate }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">以 <code class="rounded bg-canvas-warm px-1">{tracking_no}</code> 占位运单号，用于后台/前台包裹外链。</p>
      </div>
      <div>
        <label class="field-label">覆盖区域</label>
        <input v-model="form.zones" class="field" placeholder="如 US / CA / AU / UK" />
        <p v-if="errors.zones" class="mt-1 text-[11px] text-danger">{{ errors.zones }}</p>
      </div>
      <div>
        <label class="field-label">时效描述</label>
        <input v-model="form.leadTime" class="field" placeholder="如 3-5 工作日" />
        <p v-if="errors.leadTime" class="mt-1 text-[11px] text-danger">{{ errors.leadTime }}</p>
      </div>
      <div>
        <label class="field-label">状态 *</label>
        <div class="flex gap-4 text-[13px] text-ink-soft">
          <label class="flex items-center gap-1.5">
            <input v-model="form.status" type="radio" :value="CarrierStatus.ENABLED" class="accent-gold" />启用
          </label>
          <label class="flex items-center gap-1.5">
            <input v-model="form.status" type="radio" :value="CarrierStatus.DISABLED" class="accent-gold" />停用
          </label>
        </div>
        <p v-if="errors.status" class="mt-1 text-[11px] text-danger">{{ errors.status }}</p>
      </div>
    </div>
    <template #footer>
      <button class="btn-outline" :disabled="saving" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" data-testid="carrier-submit" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
