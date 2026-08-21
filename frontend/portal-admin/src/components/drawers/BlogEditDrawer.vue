<script setup lang="ts">
// COMP-MKT-A07 BlogEditDrawer：title/slug（pattern + published 必填联动 + published/archived 置灰）/category/author/
// cover（scope=content）/content（Vditor Markdown 编辑器,可通过 VITE_BLOG_EDITOR=textarea 回滚）/status + 三语 tab
// views/published_at/wordCount/readingMinutes 只读;version 乐观锁;409 VERSION_CONFLICT 弹三按钮
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import MediaUploadCard from '@/components/MediaUploadCard.vue'
import AiTranslateButton from '@/components/ai/AiTranslateButton.vue'
import VditorEditor from '@/components/blog/VditorEditor.vue'
import { useBlogStore } from '@/stores/blog'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateBlogForm, type FieldErrors } from '@/utils/validators'
import { formatDateTime } from '@/utils/format'
import { ContentStatus } from '@/api/types'
import type { BlogPost, BlogPostTranslation } from '@/api/types'
import { marketingApi } from '@/api'

/** ISO（2026-08-01T10:00:00 或带 Z）→ datetime-local input 格式（2026-08-01T10:00） */
function toDatetimeLocal(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** datetime-local（2026-08-01T10:00）→ 后端 LocalDateTime 反序列化兼容格式（2026-08-01T10:00:00） */
function fromDatetimeLocal(local: string): string | null {
  const t = local.trim()
  if (!t) return null
  return t.length === 16 ? `${t}:00` : t
}

const props = defineProps<{ open: boolean; editing: BlogPost | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useBlogStore()
const toast = useToastStore()

const STORE_BASE = import.meta.env.VITE_STORE_BASE_URL || 'http://localhost:5173'
const BLOG_EDITOR = (import.meta.env.VITE_BLOG_EDITOR || 'vditor') as 'vditor' | 'textarea'

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  title: '',
  slug: '',
  category: '',
  author: '',
  cover: '',
  content: '',
  excerpt: '',
  seoTitle: '',
  seoDescription: '',
  status: ContentStatus.DRAFT as ContentStatus,
  version: 0,
  publishedAt: '',
})
type TransRow = { title: string; excerpt: string; body: string; seoTitle: string; seoDescription: string }
const emptyTrans = (): TransRow => ({ title: '', excerpt: '', body: '', seoTitle: '', seoDescription: '' })
const trans = ref<Record<'es' | 'fr', TransRow>>({ es: emptyTrans(), fr: emptyTrans() })
const errors = ref<FieldErrors>({})
const saving = ref(false)
const previewing = ref(false)

// 409 冲突弹窗状态
const conflict = ref<{ show: boolean; latestVersion: number | null }>({ show: false, latestVersion: null })

const filled = computed(() => ({
  en: !!(form.value.title || form.value.content),
  es: Object.values(trans.value.es).some((v) => !!v),
  fr: Object.values(trans.value.fr).some((v) => !!v),
}))

// slug 锁定:published / archived 置灰(发布后锁定,P1 才做 301 历史表)
const slugLocked = computed(
  () => form.value.status === ContentStatus.PUBLISHED || form.value.status === ContentStatus.ARCHIVED,
)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    locale.value = 'en'
    errors.value = {}
    conflict.value = { show: false, latestVersion: null }
    const e = props.editing
    form.value = e
      ? {
          title: e.title,
          slug: e.slug || '',
          category: e.category || '',
          author: e.author || '',
          cover: e.cover || '',
          content: e.content || '',
          excerpt: e.excerpt || '',
          seoTitle: e.seoTitle || '',
          seoDescription: e.seoDescription || '',
          status: e.status,
          version: e.version ?? 0,
          publishedAt: toDatetimeLocal(e.publishedAt),
        }
      : {
          title: '',
          slug: '',
          category: '',
          author: '',
          cover: '',
          content: '',
          excerpt: '',
          seoTitle: '',
          seoDescription: '',
          status: ContentStatus.DRAFT,
          version: 0,
          publishedAt: '',
        }
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
    if (Object.values(t).some((v) => !!v)) {
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

function buildPayload() {
  return {
    title: form.value.title.trim(),
    slug: form.value.slug.trim() || null,
    category: form.value.category.trim() || null,
    author: form.value.author.trim() || null,
    cover: form.value.cover || null,
    content: form.value.content || null,
    excerpt: form.value.excerpt.trim() || null,
    seoTitle: form.value.seoTitle.trim() || null,
    seoDescription: form.value.seoDescription.trim() || null,
    status: form.value.status,
    version: props.editing?.id ? form.value.version : null,
    publishedAt: fromDatetimeLocal(form.value.publishedAt),
    translations: buildTranslations(),
  }
}

async function doSave() {
  await store.save(buildPayload(), props.editing?.id)
  toast.success('文章已保存，已触发缓存失效链')
  emit('close')
}

async function submit() {
  errors.value = validateBlogForm(form.value)
  if (Object.keys(errors.value).length) {
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await doSave()
  } catch (e) {
    if (e instanceof BizError && e.code === 409704) {
      // VERSION_CONFLICT:弹三按钮（取消/查看最新/强制覆盖）
      conflict.value = { show: true, latestVersion: null }
    } else if (e instanceof BizError && e.code === 409702) {
      errors.value = { slug: 'slug 已存在' }
      locale.value = 'en'
    } else if (e instanceof BizError && e.code === 422705) {
      errors.value = { slug: '该 slug 为系统保留字' }
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

/** 409 三按钮：查看最新 → 重新 GET,覆盖表单 + 更新 version */
async function conflictViewLatest() {
  if (!props.editing?.id) return
  try {
    const latest = await marketingApi.getBlog(props.editing.id)
    form.value = {
      title: latest.title,
      slug: latest.slug || '',
      category: latest.category || '',
      author: latest.author || '',
      cover: latest.cover || '',
      content: latest.content || '',
      excerpt: latest.excerpt || '',
      seoTitle: latest.seoTitle || '',
      seoDescription: latest.seoDescription || '',
      status: latest.status,
      version: latest.version ?? 0,
      publishedAt: toDatetimeLocal(latest.publishedAt),
    }
    const byLocale = (l: 'es' | 'fr') => latest.translations?.find((t) => t.locale === l)
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
    conflict.value = { show: false, latestVersion: null }
    toast.success('已加载最新版本，请合并您的修改后再次保存')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载最新版本失败')
  }
}

/** 409 三按钮：强制覆盖 → 先拉最新 version 再重提交（保留本地表单内容,仅更新 version） */
async function conflictForceOverwrite() {
  if (!props.editing?.id) return
  saving.value = true
  try {
    const latest = await marketingApi.getBlog(props.editing.id)
    form.value.version = latest.version ?? 0
    conflict.value = { show: false, latestVersion: null }
    await doSave()
  } catch (e) {
    if (e instanceof BizError && e.code === 409704) {
      // 又被改了,继续弹
      conflict.value = { show: true, latestVersion: null }
    } else {
      toast.error(e instanceof BizError ? e.message : '强制覆盖失败')
    }
  } finally {
    saving.value = false
  }
}

/** 预览 → 生成 token 新窗口打开 /blog/preview/{token} */
async function preview() {
  if (!props.editing?.id) {
    toast.error('请先保存文章后再预览')
    return
  }
  previewing.value = true
  try {
    const t = await marketingApi.createBlogPreviewToken(props.editing.id)
    window.open(`${STORE_BASE}${t.previewUrl}`, '_blank')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '生成预览链接失败')
  } finally {
    previewing.value = false
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
          <label class="field-label">
            URL Slug
            <span v-if="form.status === ContentStatus.PUBLISHED" class="text-danger"> *</span>
            <span v-if="slugLocked" class="ml-1 text-[11px] text-ink-faint">（发布后锁定）</span>
          </label>
          <input
            v-model="form.slug"
            class="field font-mono"
            :class="{ 'cursor-not-allowed bg-canvas-warm/50 opacity-70': slugLocked }"
            :disabled="slugLocked"
            placeholder="how-to-choose-veil"
          />
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
          <SelectMenu
            :model-value="form.status"
            :options="[{ value: ContentStatus.DRAFT, label: '草稿' }, { value: ContentStatus.PUBLISHED, label: '已发布' }, { value: ContentStatus.ARCHIVED, label: '已归档' }]"
            @update:model-value="form.status = $event as typeof form.status"
          />
        </div>
        <div>
          <label class="field-label">首次发布时间</label>
          <input
            v-model="form.publishedAt"
            type="datetime-local"
            class="field"
            :max="toDatetimeLocal(new Date().toISOString())"
          />
          <p class="mt-1 text-[12px] text-ink-faint">留空：发布时自动记录；填入：覆盖现有时间</p>
        </div>
      </div>
      <div>
        <label class="field-label">封面图（scope=content）</label>
        <MediaUploadCard v-model="form.cover" scope="content" aspect="aspect-[16/10]" label="点击上传封面" />
      </div>

      <!-- 2026-08-20: EN 主表 SEO 三列(折叠面板) -->
      <details class="rounded border border-line bg-canvas-warm/30 p-3">
        <summary class="cursor-pointer text-[13px] font-medium text-ink">SEO 设置（可选）</summary>
        <div class="mt-3 space-y-3">
          <div>
            <label class="field-label">
              摘要 excerpt
              <span class="ml-2 text-[11px] text-ink-faint">{{ form.excerpt.length }} / 160</span>
            </label>
            <textarea v-model="form.excerpt" rows="2" maxlength="500" class="field resize-none" placeholder="列表卡片/SEO 描述展示用，留空时自动从正文截取"></textarea>
          </div>
          <div>
            <label class="field-label">
              SEO Title
              <span class="ml-2 text-[11px] text-ink-faint">{{ form.seoTitle.length }} / 60</span>
            </label>
            <input v-model="form.seoTitle" maxlength="128" class="field" placeholder="留空时使用标题" />
          </div>
          <div>
            <label class="field-label">
              SEO Description
              <span class="ml-2 text-[11px] text-ink-faint">{{ form.seoDescription.length }} / 160</span>
            </label>
            <textarea v-model="form.seoDescription" rows="2" maxlength="255" class="field resize-none" placeholder="留空时使用摘要"></textarea>
          </div>
        </div>
      </details>

      <div>
        <label class="field-label">正文（EN,Markdown 格式）</label>
        <VditorEditor v-if="BLOG_EDITOR === 'vditor'" v-model="form.content" :height="480" upload-scope="content" />
        <textarea v-else v-model="form.content" rows="10" class="field resize-y leading-relaxed" placeholder="文章正文…"></textarea>
      </div>
      <p v-if="editing" class="text-[12px] text-ink-faint">
        阅读 {{ editing.views?.toLocaleString() ?? 0 }}
        · 字数 {{ editing.wordCount ?? 0 }} · 阅读时长 {{ editing.readingMinutes ?? 0 }} 分钟
        · 首次发布时间 {{ formatDateTime(editing.publishedAt) }}
      </p>
    </div>

    <div v-for="l in ['es', 'fr'] as const" v-show="locale === l" :key="l" class="space-y-4">
      <div>
        <div class="mb-1.5 flex items-center justify-between">
          <label class="field-label mb-0">标题（{{ l.toUpperCase() }}）</label>
          <AiTranslateButton
            v-model="trans[l].title"
            :source-text="form.title"
            :target-lang="l"
            field-label="标题"
            compact
          />
        </div>
        <input v-model="trans[l].title" class="field" />
      </div>
      <div>
        <label class="field-label">摘要 excerpt（{{ l.toUpperCase() }}）</label>
        <textarea v-model="trans[l].excerpt" rows="2" class="field resize-none"></textarea>
      </div>
      <div>
        <div class="mb-1.5 flex items-center justify-between">
          <label class="field-label mb-0">正文 body（{{ l.toUpperCase() }}）</label>
          <AiTranslateButton
            v-model="trans[l].body"
            :source-text="form.content"
            :target-lang="l"
            field-label="正文"
            compact
          />
        </div>
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
      <button
        v-if="editing?.id"
        class="btn-outline"
        :disabled="previewing"
        @click="preview"
      >{{ previewing ? '生成中…' : '预览' }}</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>

  <!-- 2026-08-20: 409 VERSION_CONFLICT 三按钮弹窗 -->
  <Teleport to="body">
    <div v-if="conflict.show" class="fixed inset-0 z-[60] flex items-center justify-center bg-ink/40 backdrop-blur-sm">
      <div class="panel w-[420px] p-6">
        <h3 class="font-display text-lg font-medium text-ink">内容已被他人修改</h3>
        <p class="mt-2 text-[13px] leading-relaxed text-ink-faint">
          在您编辑期间，另一位管理员已保存了此文章。您可以查看最新版本（您的修改将被替换），或强制覆盖（保留您的修改并覆盖对方）。
        </p>
        <div class="mt-5 flex justify-end gap-2">
          <button class="btn-outline" :disabled="saving" @click="conflict.show = false">取消</button>
          <button class="btn-outline" :disabled="saving" @click="conflictViewLatest">查看最新</button>
          <button class="btn-gold" :disabled="saving" @click="conflictForceOverwrite">{{ saving ? '提交中…' : '强制覆盖' }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
