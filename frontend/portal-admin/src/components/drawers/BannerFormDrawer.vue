<script setup lang="ts">
// COMP-MKT-A05 BannerFormDrawer：name/image_url（MediaUploadCard scope=banner）/position/start·end js_guard/
// status/sort + EN 文案区（title/subtitle/cta_text，DEC-MKT-1 可选）+ ES/FR 三语 tab
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import { useBannersStore } from '@/stores/banners'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, toIsoDateTime, validateBannerForm, type FieldErrors } from '@/utils/validators'
import { toDatetimeLocal } from '@/utils/format'
import { BannerPosition, BannerStatus } from '@/api/types'
import type { Banner, BannerTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: Banner | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useBannersStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  name: '',
  imageUrl: '',
  position: BannerPosition.HERO as BannerPosition,
  startTime: '',
  endTime: '',
  status: BannerStatus.DRAFT as BannerStatus,
  sort: 0,
  title: '',
  subtitle: '',
  ctaText: '',
})
const trans = ref<Record<'es' | 'fr', { title: string; subtitle: string; ctaText: string }>>({
  es: { title: '', subtitle: '', ctaText: '' },
  fr: { title: '', subtitle: '', ctaText: '' },
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

const filled = computed(() => ({
  en: !!(form.value.title || form.value.subtitle || form.value.ctaText),
  es: !!(trans.value.es.title || trans.value.es.subtitle || trans.value.es.ctaText),
  fr: !!(trans.value.fr.title || trans.value.fr.subtitle || trans.value.fr.ctaText),
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
          imageUrl: e.imageUrl,
          position: e.position,
          startTime: toDatetimeLocal(e.startTime),
          endTime: toDatetimeLocal(e.endTime),
          status: e.status,
          sort: e.sort ?? 0,
          title: e.title || '',
          subtitle: e.subtitle || '',
          ctaText: e.ctaText || '',
        }
      : { name: '', imageUrl: '', position: BannerPosition.HERO, startTime: '', endTime: '', status: BannerStatus.DRAFT, sort: 0, title: '', subtitle: '', ctaText: '' }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    trans.value = {
      es: { title: byLocale('es')?.title || '', subtitle: byLocale('es')?.subtitle || '', ctaText: byLocale('es')?.ctaText || '' },
      fr: { title: byLocale('fr')?.title || '', subtitle: byLocale('fr')?.subtitle || '', ctaText: byLocale('fr')?.ctaText || '' },
    }
  },
)

function buildTranslations(): BannerTranslation[] {
  const rows: BannerTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (t.title.trim() || t.subtitle.trim() || t.ctaText.trim()) {
      rows.push({ locale: l, title: t.title.trim() || null, subtitle: t.subtitle.trim() || null, ctaText: t.ctaText.trim() || null })
    }
  }
  return rows
}

async function submit() {
  errors.value = validateBannerForm(form.value)
  if (Object.keys(errors.value).length) {
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.save(
      {
        name: form.value.name.trim(),
        imageUrl: form.value.imageUrl,
        position: form.value.position,
        startTime: toIsoDateTime(form.value.startTime) ?? null,
        endTime: toIsoDateTime(form.value.endTime) ?? null,
        status: form.value.status,
        sort: Number(form.value.sort) || 0,
        title: form.value.title.trim() || null,
        subtitle: form.value.subtitle.trim() || null,
        ctaText: form.value.ctaText.trim() || null,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('Banner 已保存')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 422704) {
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
  <DrawerShell :open="open" eyebrow="Site Builder" :title="editing ? '编辑 Banner' : '新增 Banner'" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-show="locale === 'en'" class="space-y-4">
      <div>
        <label class="field-label">名称 *</label>
        <input v-model="form.name" class="field" placeholder="内部识别名，如 Spring Hero" />
        <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
      </div>
      <div>
        <label class="field-label">Banner 图 *（presign 直传，scope=banner）</label>
        <MediaUploadCard v-model="form.imageUrl" scope="banner" aspect="aspect-[16/6]" label="点击上传 Banner 图" />
        <p v-if="errors.imageUrl" class="mt-1 text-[11px] text-danger">{{ errors.imageUrl }}</p>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">广告位置 *</label>
          <AppSelect
            :model-value="form.position"
            :options="[{ value: BannerPosition.HERO, label: '首页 Hero' }, { value: BannerPosition.FEATURED, label: '推荐位' }, { value: BannerPosition.TOPBAR, label: '顶部通告条' }]"
            @update:model-value="form.position = $event as typeof form.position"
          />
        </div>
        <div>
          <label class="field-label">排序</label>
          <input v-model.number="form.sort" type="number" class="field" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">上线时间</label>
          <input v-model="form.startTime" type="datetime-local" class="field" />
        </div>
        <div>
          <label class="field-label">下线时间</label>
          <input v-model="form.endTime" type="datetime-local" class="field" />
          <p v-if="errors.endTime" class="mt-1 text-[11px] text-danger">{{ errors.endTime }}</p>
        </div>
      </div>
      <div>
        <label class="field-label">状态</label>
        <AppSelect
          :model-value="form.status"
          :options="[{ value: BannerStatus.DRAFT, label: '草稿' }, { value: BannerStatus.PUBLISHED, label: '已发布' }, { value: BannerStatus.ARCHIVED, label: '已下线' }]"
          @update:model-value="form.status = $event as typeof form.status"
        />
      </div>
      <div class="rounded-luxe border border-line p-4">
        <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">展示文案（EN，可选——DEC-MKT-1）</p>
        <div class="space-y-3">
          <input v-model="form.title" class="field" placeholder="标题 title" />
          <input v-model="form.subtitle" class="field" placeholder="副标题 subtitle" />
          <input v-model="form.ctaText" class="field" placeholder="按钮文案 cta_text" />
        </div>
      </div>
    </div>

    <div v-for="l in ['es', 'fr'] as const" v-show="locale === l" :key="l" class="space-y-3">
      <input v-model="trans[l].title" class="field" :placeholder="`标题 title（${l.toUpperCase()}）`" />
      <input v-model="trans[l].subtitle" class="field" :placeholder="`副标题 subtitle（${l.toUpperCase()}）`" />
      <input v-model="trans[l].ctaText" class="field" :placeholder="`按钮文案 cta_text（${l.toUpperCase()}）`" />
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN 文案（决策 13）。</p>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
