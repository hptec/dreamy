<script setup lang="ts">
// COMP-MKT-A02 CouponFormDrawer（FORM-MKT-A01）：code 大写自动转换 + pattern 即时提示 +
// value 按 type 占位/校验（DEC-MKT-4）+ end>start js_guard + 三语 tab（EN 主字段，ES/FR translations）
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import { usePromotionsStore } from '@/stores/promotions'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateCouponForm, type FieldErrors } from '@/utils/validators'
import { toDatetimeLocal } from '@/utils/format'
import { toIsoDateTime } from '@/utils/validators'
import { CouponStatus, CouponType } from '@/api/types'
import type { Coupon, CouponTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: Coupon | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = usePromotionsStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  code: '',
  name: '',
  type: CouponType.DISCOUNT as CouponType,
  value: '',
  minAmount: '' as number | string,
  totalLimit: '' as number | string,
  startAt: '',
  endAt: '',
  status: CouponStatus.DRAFT as CouponStatus,
  description: '',
})
const trans = ref<Record<'es' | 'fr', { name: string; description: string }>>({
  es: { name: '', description: '' },
  fr: { name: '', description: '' },
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

const valuePlaceholder = computed(() =>
  form.value.type === CouponType.DISCOUNT ? "如 '15% OFF'" : form.value.type === CouponType.FIXED_AMOUNT ? "如 '$50 OFF'" : '如 Free Shipping',
)
const statusOptions: { value: CouponStatus; label: string; createDisabled: boolean }[] = [
  { value: CouponStatus.DRAFT, label: '草稿', createDisabled: false },
  { value: CouponStatus.SCHEDULED, label: '已排期', createDisabled: false },
  { value: CouponStatus.ACTIVE, label: '进行中', createDisabled: false },
  { value: CouponStatus.EXPIRING, label: '即将到期', createDisabled: true },
  { value: CouponStatus.EXPIRED, label: '已过期', createDisabled: true },
]

const filled = computed(() => ({
  en: !!(form.value.name || form.value.description),
  es: !!(trans.value.es.name || trans.value.es.description),
  fr: !!(trans.value.fr.name || trans.value.fr.description),
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
          code: e.code,
          name: e.name,
          type: e.type,
          value: e.value,
          minAmount: e.minAmount ?? '',
          totalLimit: e.totalLimit ?? '',
          startAt: toDatetimeLocal(e.startAt),
          endAt: toDatetimeLocal(e.endAt),
          status: e.status,
          description: e.description || '',
        }
      : { code: '', name: '', type: CouponType.DISCOUNT, value: '', minAmount: '', totalLimit: '', startAt: '', endAt: '', status: CouponStatus.DRAFT, description: '' }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    trans.value = {
      es: { name: byLocale('es')?.name || '', description: byLocale('es')?.description || '' },
      fr: { name: byLocale('fr')?.name || '', description: byLocale('fr')?.description || '' },
    }
  },
)

function buildTranslations(): CouponTranslation[] {
  const rows: CouponTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (t.name.trim() || t.description.trim()) {
      rows.push({ locale: l, name: t.name.trim() || null, description: t.description.trim() || null })
    }
  }
  return rows
}

async function submit() {
  form.value.code = form.value.code.trim().toUpperCase()
  errors.value = validateCouponForm(form.value)
  if (Object.keys(errors.value).length) {
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.saveCoupon(
      {
        code: form.value.code,
        name: form.value.name.trim(),
        type: form.value.type,
        value: form.value.value.trim(),
        minAmount: form.value.minAmount === '' ? null : form.value.minAmount,
        totalLimit: form.value.totalLimit === '' ? null : Number(form.value.totalLimit),
        startAt: toIsoDateTime(form.value.startAt) ?? null,
        endAt: toIsoDateTime(form.value.endAt) ?? null,
        status: form.value.status,
        description: form.value.description.trim() || null,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('优惠券已保存')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 409701) {
      errors.value = { code: '券码已存在' }
      locale.value = 'en'
    } else if (e instanceof BizError && e.code === 409703) {
      toast.error('已上线券不可修改券码')
    } else if (e instanceof BizError && e.code === 422704) {
      errors.value = extractFieldErrors(e)
      if (!Object.keys(errors.value).length) toast.error(e.message)
      locale.value = 'en'
    } else {
      toast.error(e instanceof BizError ? e.message : '保存失败')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell :open="open" eyebrow="Marketing" :title="editing ? '编辑优惠券' : '新建优惠券'" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <!-- EN（主字段）面板 -->
    <div v-show="locale === 'en'" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">券码 *</label>
          <input
            v-model="form.code"
            class="field font-mono uppercase"
            placeholder="WELCOME15"
            :disabled="!!editing && editing.status !== CouponStatus.DRAFT"
            @input="form.code = form.code.toUpperCase()"
          />
          <p v-if="errors.code" class="mt-1 text-[11px] text-danger">{{ errors.code }}</p>
        </div>
        <div>
          <label class="field-label">名称 *</label>
          <input v-model="form.name" class="field" placeholder="新客 85 折" />
          <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">类型 *</label>
          <SelectMenu
            :model-value="form.type"
            :options="[{ value: CouponType.DISCOUNT, label: '折扣（discount）' }, { value: CouponType.FIXED_AMOUNT, label: '满减（fixed_amount）' }, { value: CouponType.FREE_SHIPPING, label: '免邮（free_shipping）' }]"
            @update:model-value="form.type = $event as typeof form.type"
          />
        </div>
        <div>
          <label class="field-label">面额 *</label>
          <input v-model="form.value" class="field" :placeholder="valuePlaceholder" />
          <p v-if="errors.value" class="mt-1 text-[11px] text-danger">{{ errors.value }}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">使用门槛（USD，0 = 无门槛）</label>
          <input v-model="form.minAmount" type="number" min="0" class="field" />
        </div>
        <div>
          <label class="field-label">发放上限</label>
          <input v-model="form.totalLimit" type="number" min="0" class="field" placeholder="留空 = 不限" />
          <p v-if="Number(form.totalLimit) > 9999" class="mt-1 text-[11px] text-ink-faint">超过 9999 视为「不限」展示</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">开始时间</label>
          <input v-model="form.startAt" type="datetime-local" class="field" />
        </div>
        <div>
          <label class="field-label">结束时间</label>
          <input v-model="form.endAt" type="datetime-local" class="field" />
          <p v-if="errors.endAt" class="mt-1 text-[11px] text-danger">{{ errors.endAt }}</p>
        </div>
      </div>
      <div>
        <label class="field-label">状态</label>
          <SelectMenu
            :model-value="form.status"
            :options="statusOptions.map(s => ({ value: s.value, label: s.label, disabled: !editing && s.createDisabled }))"
            @update:model-value="form.status = $event as typeof form.status"
          />
      </div>
      <div>
        <label class="field-label">描述（EN）</label>
        <textarea v-model="form.description" rows="2" class="field resize-none" placeholder="前台展示的券说明文案"></textarea>
      </div>
      <p v-if="editing" class="text-[12px] text-ink-faint">已核销：{{ editing.usedCount ?? 0 }} 次（只读）</p>
    </div>

    <!-- ES / FR 翻译面板（空字段允许提交，缺翻译消费端回退 EN） -->
    <div v-for="l in ['es', 'fr'] as const" v-show="locale === l" :key="l" class="space-y-4">
      <div>
        <label class="field-label">名称（{{ l.toUpperCase() }}）</label>
        <input v-model="trans[l].name" class="field" />
      </div>
      <div>
        <label class="field-label">描述（{{ l.toUpperCase() }}）</label>
        <textarea v-model="trans[l].description" rows="2" class="field resize-none"></textarea>
      </div>
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN 文案（决策 13）。</p>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
