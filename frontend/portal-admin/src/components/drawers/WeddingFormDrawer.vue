<script setup lang="ts">
// COMP-MKT-A09 WeddingFormDrawer：couple/location/theme/wedding_date/cover/status
// + EN 文案（title/story）+ ES/FR 三语 tab + Shop the Look 商品选择器
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import ProductPickerPanel from '@/components/ProductPickerPanel.vue'
import { useWeddingsStore } from '@/stores/weddings'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { PublishStatus } from '@/api/types'
import type { RealWedding, RealWeddingTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: RealWedding | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useWeddingsStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  couple: '',
  location: '',
  theme: '',
  weddingDate: '',
  cover: '',
  status: PublishStatus.DRAFT as PublishStatus,
  title: '',
  story: '',
  productIds: [] as number[],
})
const trans = ref<Record<'es' | 'fr', { title: string; story: string }>>({
  es: { title: '', story: '' },
  fr: { title: '', story: '' },
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

const filled = computed(() => ({
  en: !!(form.value.title || form.value.story),
  es: !!(trans.value.es.title || trans.value.es.story),
  fr: !!(trans.value.fr.title || trans.value.fr.story),
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
          couple: e.couple,
          location: e.location || '',
          theme: e.theme || '',
          weddingDate: e.weddingDate || '',
          cover: e.cover || '',
          status: e.status,
          title: e.title || '',
          story: e.story || '',
          productIds: [...(e.productIds || [])],
        }
      : { couple: '', location: '', theme: '', weddingDate: '', cover: '', status: PublishStatus.DRAFT, title: '', story: '', productIds: [] }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    trans.value = {
      es: { title: byLocale('es')?.title || '', story: byLocale('es')?.story || '' },
      fr: { title: byLocale('fr')?.title || '', story: byLocale('fr')?.story || '' },
    }
  },
)

function buildTranslations(): RealWeddingTranslation[] {
  const rows: RealWeddingTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (t.title.trim() || t.story.trim()) {
      rows.push({ locale: l, title: t.title.trim() || null, story: t.story.trim() || null })
    }
  }
  return rows
}

async function submit() {
  errors.value = {}
  if (!form.value.couple.trim()) {
    errors.value.couple = '新人姓名必填'
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.save(
      {
        couple: form.value.couple.trim(),
        location: form.value.location.trim() || null,
        theme: form.value.theme.trim() || null,
        weddingDate: form.value.weddingDate || null,
        cover: form.value.cover || null,
        status: form.value.status,
        title: form.value.title.trim() || null,
        story: form.value.story.trim() || null,
        productIds: form.value.productIds,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('婚礼故事已保存，已触发缓存失效链')
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
  <DrawerShell :open="open" eyebrow="Content · CMS" :title="editing ? '编辑婚礼故事' : '新增婚礼故事'" width="max-w-2xl" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-show="locale === 'en'" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">新人 *</label>
          <input v-model="form.couple" class="field" placeholder="如 Emma & Liam" />
          <p v-if="errors.couple" class="mt-1 text-[11px] text-danger">{{ errors.couple }}</p>
        </div>
        <div>
          <label class="field-label">地点</label>
          <input v-model="form.location" class="field" placeholder="如 Tuscany, Italy" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">主题</label>
          <input v-model="form.theme" class="field" placeholder="如 Garden Romance" />
        </div>
        <div>
          <label class="field-label">婚期</label>
          <input v-model="form.weddingDate" type="date" class="field" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">封面图（scope=content）</label>
          <MediaUploadCard v-model="form.cover" scope="content" aspect="aspect-[3/4]" label="点击上传封面" />
        </div>
        <div>
          <label class="field-label">状态</label>
          <AppSelect
            :model-value="form.status"
            :options="[{ value: PublishStatus.DRAFT, label: '草稿' }, { value: PublishStatus.PUBLISHED, label: '已发布' }]"
            @update:model-value="form.status = $event as typeof form.status"
          />
        </div>
      </div>
      <div>
        <label class="field-label">标题（EN，可选）</label>
        <input v-model="form.title" class="field" />
      </div>
      <div>
        <label class="field-label">故事正文（EN）</label>
        <textarea v-model="form.story" rows="6" class="field resize-y leading-relaxed"></textarea>
      </div>
      <div>
        <label class="field-label">Shop the Look 关联商品</label>
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
        <label class="field-label">故事正文（{{ l.toUpperCase() }}）</label>
        <textarea v-model="trans[l].story" rows="6" class="field resize-y leading-relaxed"></textarea>
      </div>
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13）。</p>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
