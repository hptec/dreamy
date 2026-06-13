<script setup lang="ts">
// COMP-MKT-A10 LookbookFormDrawer：title/theme/description EN + 三语 tab + 商品选择器 + 状态
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import ProductPickerPanel from '@/components/ProductPickerPanel.vue'
import { useLookbookStore } from '@/stores/lookbook'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { PublishStatus } from '@/api/types'
import type { Lookbook, LookbookTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: Lookbook | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useLookbookStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  title: '',
  theme: '',
  description: '',
  status: PublishStatus.DRAFT as PublishStatus,
  productIds: [] as number[],
})
const trans = ref<Record<'es' | 'fr', { title: string; description: string }>>({
  es: { title: '', description: '' },
  fr: { title: '', description: '' },
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

const filled = computed(() => ({
  en: !!(form.value.title || form.value.description),
  es: !!(trans.value.es.title || trans.value.es.description),
  fr: !!(trans.value.fr.title || trans.value.fr.description),
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
          title: e.title,
          theme: e.theme || '',
          description: e.description || '',
          status: e.status,
          productIds: [...(e.productIds || [])],
        }
      : { title: '', theme: '', description: '', status: PublishStatus.DRAFT, productIds: [] }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    trans.value = {
      es: { title: byLocale('es')?.title || '', description: byLocale('es')?.description || '' },
      fr: { title: byLocale('fr')?.title || '', description: byLocale('fr')?.description || '' },
    }
  },
)

function buildTranslations(): LookbookTranslation[] {
  const rows: LookbookTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (t.title.trim() || t.description.trim()) {
      rows.push({ locale: l, title: t.title.trim() || null, description: t.description.trim() || null })
    }
  }
  return rows
}

async function submit() {
  errors.value = {}
  if (!form.value.title.trim()) {
    errors.value.title = '标题必填'
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.saveLookbook(
      {
        title: form.value.title.trim(),
        theme: form.value.theme.trim() || null,
        description: form.value.description.trim() || null,
        status: form.value.status,
        productIds: form.value.productIds,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('Lookbook 已保存，已触发缓存失效链')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 422704) {
      errors.value = extractFieldErrors(e)
      if (errors.value.productIds) errors.value.productIds = '包含已删除商品，请移除'
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
  <DrawerShell :open="open" eyebrow="Content · CMS" :title="editing ? '编辑 Lookbook' : '新增 Lookbook'" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-show="locale === 'en'" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">标题 *</label>
          <input v-model="form.title" class="field" />
          <p v-if="errors.title" class="mt-1 text-[11px] text-danger">{{ errors.title }}</p>
        </div>
        <div>
          <label class="field-label">主题</label>
          <input v-model="form.theme" class="field" placeholder="如 Coastal Elegance" />
        </div>
      </div>
      <div>
        <label class="field-label">描述（EN）</label>
        <textarea v-model="form.description" rows="3" class="field resize-none"></textarea>
      </div>
      <div>
        <label class="field-label">状态</label>
        <AppSelect
          :model-value="form.status"
          :options="[{ value: PublishStatus.DRAFT, label: '草稿' }, { value: PublishStatus.PUBLISHED, label: '已发布' }]"
          @update:model-value="form.status = $event as typeof form.status"
        />
      </div>
      <div>
        <label class="field-label">商品锚点</label>
        <ProductPickerPanel v-model="form.productIds" />
        <p v-if="errors.productIds" class="mt-1 text-[11px] text-danger">{{ errors.productIds }}</p>
      </div>
    </div>

    <div v-for="l in ['es', 'fr'] as const" v-show="locale === l" :key="l" class="space-y-4">
      <div>
        <label class="field-label">标题（{{ l.toUpperCase() }}）</label>
        <input v-model="trans[l].title" class="field" />
      </div>
      <div>
        <label class="field-label">描述（{{ l.toUpperCase() }}）</label>
        <textarea v-model="trans[l].description" rows="3" class="field resize-none"></textarea>
      </div>
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13）。</p>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
