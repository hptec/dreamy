<script setup lang="ts">
// COMP-SHP-04 CarrierFormDrawer（FORM-SHP-01 载体）：name*/zones/lead_time/status
// 422901 → details.field 分发 inline；409902（编辑中改 disabled）→ status 字段 inline
import { ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateCarrierForm, type FieldErrors } from '@/utils/validators'
import type { Carrier, CarrierStatus } from '@/api/types'

const props = defineProps<{ open: boolean; editing: Carrier | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useShippingStore()
const toast = useToastStore()

const form = ref({ name: '', zones: '', leadTime: '', status: 'enabled' as CarrierStatus })
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
          zones: props.editing.zones || '',
          leadTime: props.editing.leadTime || '',
          status: props.editing.status,
        }
      : { name: '', zones: '', leadTime: '', status: 'enabled' }
  },
)

async function submit() {
  errors.value = validateCarrierForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    const saved = await store.saveCarrier(
      {
        name: form.value.name.trim(),
        zones: form.value.zones.trim() || null,
        leadTime: form.value.leadTime.trim() || null,
        status: form.value.status,
      },
      props.editing?.id,
    )
    toast.success('已保存')
    // DEC-SHP-2 运营提示：名称变更需同步检查邮费规则行后缀
    if (props.editing && props.editing.name !== saved.name) {
      toast.info('承运商名称已变更，请同步检查邮费规则行后缀')
    }
    emit('saved')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 409902) {
      errors.value = { status: '至少保留一个启用的承运方' }
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
  <DrawerShell :open="open" eyebrow="Shipping" :title="editing ? '编辑承运方' : '添加承运方'" width="max-w-md" @close="emit('close')">
    <div class="space-y-4">
      <div>
        <label class="field-label">承运方名称 *</label>
        <input v-model="form.name" class="field" placeholder="如 FedEx International Priority" />
        <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
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
            <input v-model="form.status" type="radio" value="enabled" class="accent-gold" />启用
          </label>
          <label class="flex items-center gap-1.5">
            <input v-model="form.status" type="radio" value="disabled" class="accent-gold" />停用
          </label>
        </div>
        <p v-if="errors.status" class="mt-1 text-[11px] text-danger">{{ errors.status }}</p>
      </div>
    </div>
    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
