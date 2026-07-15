<script setup lang="ts">
// COMP-MKT-A05 BannerFormDrawer：name/image_url（MediaUploadCard scope=banner）/position/start·end js_guard/
// status/sort + EN 文案区（title/subtitle/cta_text，DEC-MKT-1 可选）+ ES/FR 三语 tab
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import { useBannersStore } from '@/stores/banners'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, toIsoDateTime, validateBannerForm, type FieldErrors } from '@/utils/validators'
import { toDatetimeLocal } from '@/utils/format'
import { BannerPosition, BannerStatus } from '@/api/types'
import type { Banner, BannerTranslation } from '@/api/types'

const props = withDefaults(defineProps<{ open: boolean; editing: Banner | null; homeHero?: boolean }>(), {
  homeHero: false,
})
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
  ctaLink: '',
  ctaTextSecondary: '',
  ctaLinkSecondary: '',
})
const trans = ref<Record<'es' | 'fr', { imageUrl: string; title: string; subtitle: string; ctaText: string; ctaTextSecondary: string }>>({
  es: { imageUrl: '', title: '', subtitle: '', ctaText: '', ctaTextSecondary: '' },
  fr: { imageUrl: '', title: '', subtitle: '', ctaText: '', ctaTextSecondary: '' },
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

const displayStatus = computed({
  get: () => form.value.status === BannerStatus.PUBLISHED ? BannerStatus.PUBLISHED : BannerStatus.ARCHIVED,
  set: (status: BannerStatus) => {
    if (status === BannerStatus.PUBLISHED) {
      form.value.status = status
      return
    }
    form.value.status = !props.editing || props.editing.status === BannerStatus.DRAFT
      ? BannerStatus.DRAFT
      : BannerStatus.ARCHIVED
  },
})

function updateDisplayStatus(status: unknown) {
  if (status === BannerStatus.PUBLISHED || status === BannerStatus.ARCHIVED) {
    displayStatus.value = status
  }
}

const filled = computed(() => ({
  en: !!(form.value.title || form.value.subtitle || form.value.ctaText || form.value.ctaTextSecondary),
  es: !!(trans.value.es.imageUrl || trans.value.es.title || trans.value.es.subtitle || trans.value.es.ctaText || trans.value.es.ctaTextSecondary),
  fr: !!(trans.value.fr.imageUrl || trans.value.fr.title || trans.value.fr.subtitle || trans.value.fr.ctaText || trans.value.fr.ctaTextSecondary),
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
          ctaLink: e.ctaLink || '',
          ctaTextSecondary: e.ctaTextSecondary || '',
          ctaLinkSecondary: e.ctaLinkSecondary || '',
        }
      : { name: '', imageUrl: '', position: BannerPosition.HERO, startTime: '', endTime: '', status: BannerStatus.DRAFT, sort: 0, title: '', subtitle: '', ctaText: '', ctaLink: '', ctaTextSecondary: '', ctaLinkSecondary: '' }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    trans.value = {
      es: { imageUrl: byLocale('es')?.imageUrl || '', title: byLocale('es')?.title || '', subtitle: byLocale('es')?.subtitle || '', ctaText: byLocale('es')?.ctaText || '', ctaTextSecondary: byLocale('es')?.ctaTextSecondary || '' },
      fr: { imageUrl: byLocale('fr')?.imageUrl || '', title: byLocale('fr')?.title || '', subtitle: byLocale('fr')?.subtitle || '', ctaText: byLocale('fr')?.ctaText || '', ctaTextSecondary: byLocale('fr')?.ctaTextSecondary || '' },
    }
  },
)

function buildTranslations(): BannerTranslation[] {
  const rows: BannerTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (t.imageUrl || t.title.trim() || t.subtitle.trim() || t.ctaText.trim() || t.ctaTextSecondary.trim()) {
      rows.push({ locale: l, imageUrl: t.imageUrl || null, title: t.title.trim() || null, subtitle: t.subtitle.trim() || null, ctaText: t.ctaText.trim() || null, ctaTextSecondary: t.ctaTextSecondary.trim() || null })
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
        ctaLink: form.value.ctaLink.trim() || null,
        ctaTextSecondary: form.value.ctaTextSecondary.trim() || null,
        ctaLinkSecondary: form.value.ctaLinkSecondary.trim() || null,
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
  <DrawerShell
    :open="open"
    eyebrow="Site Builder"
    :title="homeHero ? (editing ? '编辑首页主视觉' : '新增首页主视觉') : (editing ? '编辑 Banner' : '新增 Banner')"
    @close="emit('close')"
  >
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-show="locale === 'en'" class="space-y-4">
      <div>
        <label class="field-label">名称 *</label>
        <input v-model="form.name" class="field" placeholder="内部识别名，如春季首屏主视觉" />
        <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
      </div>
      <div>
        <label class="field-label">Banner 图 *（presign 直传，scope=banner）</label>
        <MediaUploadCard v-model="form.imageUrl" scope="banner" aspect="aspect-[16/6]" label="点击上传 Banner 图" />
        <p v-if="errors.imageUrl" class="mt-1 text-[11px] text-danger">{{ errors.imageUrl }}</p>
      </div>
      <div v-if="!homeHero" class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">广告位置 *</label>
          <SelectMenu
            :model-value="form.position"
            :options="[{ value: BannerPosition.HERO, label: '首屏主视觉' }, { value: BannerPosition.FEATURED, label: '推荐位' }, { value: BannerPosition.TOPBAR, label: '顶部通告条' }]"
            @update:model-value="form.position = $event as typeof form.position"
          />
        </div>
        <div>
          <label class="field-label">排序</label>
          <input v-model.number="form.sort" type="number" class="field" />
        </div>
      </div>
      <div v-if="!homeHero" class="grid grid-cols-2 gap-4">
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
      <div v-if="!homeHero">
        <label class="field-label">状态</label>
        <SelectMenu
          :model-value="displayStatus"
          :options="[{ value: BannerStatus.PUBLISHED, label: '上线' }, { value: BannerStatus.ARCHIVED, label: '下线' }]"
          @update:model-value="updateDisplayStatus"
        />
      </div>
      <div class="rounded-luxe border border-line p-4">
        <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">展示文案（EN，可选——DEC-MKT-1）</p>
        <div class="space-y-3">
          <div>
            <label class="field-label">标题</label>
            <input v-model="form.title" class="field" placeholder="请输入标题" />
          </div>
          <div>
            <label class="field-label">副标题</label>
            <input v-model="form.subtitle" class="field" placeholder="请输入副标题" />
          </div>
          <div>
            <label class="field-label">主要按钮文案</label>
            <input v-model="form.ctaText" class="field" placeholder="如 Shop Now" />
          </div>
          <div>
            <label class="field-label">主要按钮链接</label>
            <input v-model="form.ctaLink" class="field" placeholder="如 /wedding-dresses" />
          </div>
          <div>
            <label class="field-label">次要按钮文案</label>
            <input v-model="form.ctaTextSecondary" class="field" placeholder="选填" />
          </div>
          <div>
            <label class="field-label">次要按钮链接</label>
            <input v-model="form.ctaLinkSecondary" class="field" placeholder="如 /outdoor-weddings" />
          </div>
        </div>
      </div>
    </div>

    <template v-for="l in ['es', 'fr'] as const" :key="l">
      <div v-if="locale === l" class="space-y-3">
        <div>
          <label class="field-label">Banner 图（{{ l.toUpperCase() }}，选填）</label>
          <MediaUploadCard
            v-model="trans[l].imageUrl"
            :fallback-value="form.imageUrl"
            fallback-label="继承 EN 图片"
            scope="banner"
            aspect="aspect-[16/6]"
            label="点击上传 Banner 图"
          />
        </div>
        <div class="rounded-luxe border border-line p-4">
          <p class="mb-3 text-[12px] font-semibold uppercase tracking-widest text-ink-faint">展示文案（{{ l.toUpperCase() }}，可选）</p>
          <div class="space-y-3">
            <div>
              <label class="field-label">标题</label>
              <input v-model="trans[l].title" class="field" :placeholder="form.title ? `继承 EN：${form.title}` : '请输入标题'" />
            </div>
            <div>
              <label class="field-label">副标题</label>
              <input v-model="trans[l].subtitle" class="field" :placeholder="form.subtitle ? `继承 EN：${form.subtitle}` : '请输入副标题'" />
            </div>
            <div>
              <label class="field-label">主要按钮文案</label>
              <input v-model="trans[l].ctaText" class="field" :placeholder="form.ctaText ? `继承 EN：${form.ctaText}` : '请输入主要按钮文案'" />
            </div>
            <div>
              <label class="field-label">次要按钮文案</label>
              <input v-model="trans[l].ctaTextSecondary" class="field" :placeholder="form.ctaTextSecondary ? `继承 EN：${form.ctaTextSecondary}` : '请输入次要按钮文案'" />
            </div>
          </div>
          <p class="mt-3 text-[11px] text-ink-faint">空白字段继承 EN 内容；主要及次要按钮链接统一使用 EN 配置。</p>
        </div>
      </div>
    </template>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
