<script setup lang="ts">
// COMP-MKT-A03 FlashSaleFormDrawer（FORM-MKT-A02）：name/discount/start·end js_guard/status（ended 禁选）
// + 商品选择器（useProductPicker 载体 ProductPickerPanel）+ 三语 name tab
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import ProductPickerPanel from '@/components/ProductPickerPanel.vue'
import { usePromotionsStore } from '@/stores/promotions'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, toIsoDateTime, validateFlashSaleForm, type FieldErrors } from '@/utils/validators'
import { toDatetimeLocal } from '@/utils/format'
import { FlashSaleStatus } from '@/api/types'
import type { FlashSale, FlashSaleTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: FlashSale | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = usePromotionsStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  name: '',
  discount: '',
  startAt: '',
  endAt: '',
  status: FlashSaleStatus.DRAFT as FlashSaleStatus,
  productIds: [] as number[],
})
const trans = ref<Record<'es' | 'fr', { name: string }>>({ es: { name: '' }, fr: { name: '' } })
const errors = ref<FieldErrors>({})
const saving = ref(false)

const filled = computed(() => ({
  en: !!form.value.name,
  es: !!trans.value.es.name,
  fr: !!trans.value.fr.name,
}))

watch(
  () => props.open,
  (open) => {
    if (!open) return
    locale.value = 'en'
    errors.value = {}
    const e = props.editing
    form.value = e
      ? {
          name: e.name,
          discount: e.discount,
          startAt: toDatetimeLocal(e.startAt),
          endAt: toDatetimeLocal(e.endAt),
          status: e.status,
          productIds: [...(e.productIds || [])],
        }
      : { name: '', discount: '', startAt: '', endAt: '', status: FlashSaleStatus.DRAFT, productIds: [] }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    trans.value = { es: { name: byLocale('es')?.name || '' }, fr: { name: byLocale('fr')?.name || '' } }
  },
)

function buildTranslations(): FlashSaleTranslation[] {
  const rows: FlashSaleTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    if (trans.value[l].name.trim()) rows.push({ locale: l, name: trans.value[l].name.trim() })
  }
  return rows
}

async function submit() {
  errors.value = validateFlashSaleForm(form.value)
  if (Object.keys(errors.value).length) {
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.saveFlashSale(
      {
        name: form.value.name.trim(),
        discount: form.value.discount.trim(),
        startAt: toIsoDateTime(form.value.startAt)!,
        endAt: toIsoDateTime(form.value.endAt)!,
        status: form.value.status,
        productIds: form.value.productIds,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('闪购活动已保存')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 422704) {
      const fields = extractFieldErrors(e)
      // FORM-MKT-A02：fields.product_ids → 选择器区 inline
      errors.value = fields
      if (fields.productIds) errors.value.productIds = '包含已删除商品，请移除'
      if (!Object.keys(fields).length) toast.error(e.message)
      locale.value = 'en'
    } else if (e instanceof BizError && e.code === 409703) {
      toast.error('当前发布状态不允许该操作')
    } else {
      toast.error(e instanceof BizError ? e.message : '保存失败')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell :open="open" eyebrow="Marketing" :title="editing ? '编辑闪购活动' : '新建闪购活动'" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-show="locale === 'en'" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">活动名称 *</label>
          <input v-model="form.name" class="field" placeholder="如 Memorial Day Flash" />
          <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
        </div>
        <div>
          <label class="field-label">折扣描述 *</label>
          <input v-model="form.discount" class="field" placeholder="如 -30%" />
          <p v-if="errors.discount" class="mt-1 text-[11px] text-danger">{{ errors.discount }}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">开始时间 *</label>
          <input v-model="form.startAt" type="datetime-local" class="field" />
          <p v-if="errors.startAt" class="mt-1 text-[11px] text-danger">{{ errors.startAt }}</p>
        </div>
        <div>
          <label class="field-label">结束时间 *</label>
          <input v-model="form.endAt" type="datetime-local" class="field" />
          <p v-if="errors.endAt" class="mt-1 text-[11px] text-danger">{{ errors.endAt }}</p>
        </div>
      </div>
      <div>
        <label class="field-label">状态</label>
        <select v-model="form.status" class="field">
          <option :value="FlashSaleStatus.DRAFT">草稿</option>
          <option :value="FlashSaleStatus.SCHEDULED">已排期</option>
          <option :value="FlashSaleStatus.ACTIVE">进行中</option>
          <option :value="FlashSaleStatus.ENDED" :disabled="!editing || editing.status !== FlashSaleStatus.ENDED">已结束</option>
        </select>
      </div>
      <div>
        <label class="field-label">活动商品（可留空——仅展示活动不挂品）</label>
        <ProductPickerPanel v-model="form.productIds" />
        <p v-if="errors.productIds" class="mt-1 text-[11px] text-danger">{{ errors.productIds }}</p>
      </div>
    </div>

    <div v-for="l in ['es', 'fr'] as const" v-show="locale === l" :key="l" class="space-y-4">
      <div>
        <label class="field-label">活动名称（{{ l.toUpperCase() }}）</label>
        <input v-model="trans[l].name" class="field" />
      </div>
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN 文案（决策 13）。</p>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
