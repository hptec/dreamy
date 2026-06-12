<script setup lang="ts">
// COMP-MKT-A07 BlogEditDrawer：title/slug（pattern + published 必填联动）/category/author/
// cover（scope=content）/content（textarea 富文本基线）/status + 三语 tab（title/excerpt/body/seo_*）
// views/published_at 只读
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import { useBlogStore } from '@/stores/blog'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateBlogForm, type FieldErrors } from '@/utils/validators'
import { formatDateTime } from '@/utils/format'
import { ContentStatus } from '@/api/types'
import type { BlogPost, BlogPostTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: BlogPost | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useBlogStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  title: '',
  slug: '',
  category: '',
  author: '',
  cover: '',
  content: '',
  status: ContentStatus.DRAFT as ContentStatus,
})
type TransRow = { title: string; excerpt: string; body: string; seoTitle: string; seoDescription: string }
const emptyTrans = (): TransRow => ({ title: '', excerpt: '', body: '', seoTitle: '', seoDescription: '' })
const trans = ref<Record<'es' | 'fr', TransRow>>({ es: emptyTrans(), fr: emptyTrans() })
const errors = ref<FieldErrors>({})
const saving = ref(false)

const filled = computed(() => ({
  en: !!(form.value.title || form.value.content),
  es: Object.values(trans.value.es).some((v) => !!v),
  fr: Object.values(trans.value.fr).some((v) => !!v),
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
          slug: e.slug || '',
          category: e.category || '',
          author: e.author || '',
          cover: e.cover || '',
          content: e.content || '',
          status: e.status,
        }
      : { title: '', slug: '', category: '', author: '', cover: '', content: '', status: ContentStatus.DRAFT }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    const toRow = (l: 'es' | 'fr'): TransRow => {
      const t = byLocale(l)
      return {
        title: t?.title || '',
        excerpt: t?.excerpt || '',
        body: t?.body || '',
        seoTitle: t?.seoTitle || '',
        seoDescription: t?.seoDescription || '',
      }
    }
    trans.value = { es: toRow('es'), fr: toRow('fr') }
  },
)

function buildTranslations(): BlogPostTranslation[] {
  const rows: BlogPostTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (Object.values(t).some((v) => v.trim())) {
      rows.push({
        locale: l,
        title: t.title.trim() || null,
        excerpt: t.excerpt.trim() || null,
        body: t.body.trim() || null,
        seoTitle: t.seoTitle.trim() || null,
        seoDescription: t.seoDescription.trim() || null,
      })
    }
  }
  return rows
}

async function submit() {
  errors.value = validateBlogForm(form.value)
  if (Object.keys(errors.value).length) {
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.save(
      {
        title: form.value.title.trim(),
        slug: form.value.slug.trim() || null,
        category: form.value.category.trim() || null,
        author: form.value.author.trim() || null,
        cover: form.value.cover || null,
        content: form.value.content || null,
        status: form.value.status,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('文章已保存，已触发缓存失效链')
    emit('close')
  } catch (e) {
    if (e instanceof BizError && e.code === 409702) {
      errors.value = { slug: 'slug 已存在' }
      locale.value = 'en'
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
  <DrawerShell :open="open" eyebrow="Content · CMS" :title="editing ? '编辑文章' : '写文章'" width="max-w-2xl" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-show="locale === 'en'" class="space-y-4">
      <div>
        <label class="field-label">标题 *</label>
        <input v-model="form.title" class="field" />
        <p v-if="errors.title" class="mt-1 text-[11px] text-danger">{{ errors.title }}</p>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">URL Slug<span v-if="form.status === ContentStatus.PUBLISHED" class="text-danger"> *</span></label>
          <input v-model="form.slug" class="field font-mono" placeholder="how-to-choose-veil" />
          <p v-if="errors.slug" class="mt-1 text-[11px] text-danger">{{ errors.slug }}</p>
        </div>
        <div>
          <label class="field-label">分类</label>
          <input v-model="form.category" class="field" placeholder="Planning / Style / Tips" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">作者</label>
          <input v-model="form.author" class="field" />
        </div>
        <div>
          <label class="field-label">状态</label>
          <select v-model="form.status" class="field">
            <option :value="ContentStatus.DRAFT">草稿</option>
            <option :value="ContentStatus.PUBLISHED">已发布</option>
            <option :value="ContentStatus.ARCHIVED">已归档</option>
          </select>
        </div>
      </div>
      <div>
        <label class="field-label">封面图（scope=content）</label>
        <MediaUploadCard v-model="form.cover" scope="content" aspect="aspect-[16/10]" label="点击上传封面" />
      </div>
      <div>
        <label class="field-label">正文（EN）</label>
        <textarea v-model="form.content" rows="10" class="field resize-y leading-relaxed" placeholder="文章正文…"></textarea>
      </div>
      <p v-if="editing" class="text-[12px] text-ink-faint">
        阅读 {{ editing.views?.toLocaleString() ?? 0 }} · 发布时间 {{ formatDateTime(editing.publishedAt) }}（只读）
      </p>
    </div>

    <div v-for="l in ['es', 'fr'] as const" v-show="locale === l" :key="l" class="space-y-4">
      <div>
        <label class="field-label">标题（{{ l.toUpperCase() }}）</label>
        <input v-model="trans[l].title" class="field" />
      </div>
      <div>
        <label class="field-label">摘要 excerpt（{{ l.toUpperCase() }}）</label>
        <textarea v-model="trans[l].excerpt" rows="2" class="field resize-none"></textarea>
      </div>
      <div>
        <label class="field-label">正文 body（{{ l.toUpperCase() }}）</label>
        <textarea v-model="trans[l].body" rows="8" class="field resize-y leading-relaxed"></textarea>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">SEO Title（{{ l.toUpperCase() }}）</label>
          <input v-model="trans[l].seoTitle" class="field" />
        </div>
        <div>
          <label class="field-label">SEO Description（{{ l.toUpperCase() }}）</label>
          <input v-model="trans[l].seoDescription" class="field" />
        </div>
      </div>
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13，可部分提交）。</p>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
