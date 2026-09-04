<script setup lang="ts">
// COMP-MKT-A10 GuideFormDrawer：phase/timeframe/title/tasks/body EN + 三语 tab（title/body）
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import LocaleTabs from '@/components/LocaleTabs.vue'
import VditorEditor from '@/components/blog/VditorEditor.vue'
import { useLookbookStore } from '@/stores/lookbook'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { PublishStatus } from '@/api/types'
import type { Guide, GuideTranslation } from '@/api/types'

const props = defineProps<{ open: boolean; editing: Guide | null }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useLookbookStore()
const toast = useToastStore()

const locale = ref<'en' | 'es' | 'fr'>('en')
const form = ref({
  phase: '',
  timeframe: '',
  title: '',
  tasks: [] as { taskId?: number; label: string }[],
  body: '',
  status: PublishStatus.DRAFT as PublishStatus,
})
const trans = ref<Record<'es' | 'fr', { title: string; body: string; tasks: { taskId?: number; label: string }[] }>>({
  es: { title: '', body: '', tasks: [] },
  fr: { title: '', body: '', tasks: [] },
})
const errors = ref<FieldErrors>({})
const saving = ref(false)

const filled = computed(() => ({
  en: !!(form.value.title || form.value.body),
  es: !!(trans.value.es.title || trans.value.es.body || trans.value.es.tasks.some((task) => task.label)),
  fr: !!(trans.value.fr.title || trans.value.fr.body || trans.value.fr.tasks.some((task) => task.label)),
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
          phase: e.phase,
          timeframe: e.timeframe || '',
          title: e.title,
          tasks: e.tasks ?? [],
          body: e.body || '',
          status: e.status,
        }
      : { phase: '', timeframe: '', title: '', tasks: [], body: '', status: PublishStatus.DRAFT }
    const byLocale = (l: 'es' | 'fr') => e?.translations?.find((t) => t.locale === l)
    const localizedTasks = (l: 'es' | 'fr') => form.value.tasks.map((task) => ({
      taskId: task.taskId,
      label: byLocale(l)?.tasks?.find((translated) => translated.taskId === task.taskId)?.label || '',
    }))
    trans.value = {
      es: { title: byLocale('es')?.title || '', body: byLocale('es')?.body || '', tasks: localizedTasks('es') },
      fr: { title: byLocale('fr')?.title || '', body: byLocale('fr')?.body || '', tasks: localizedTasks('fr') },
    }
  },
)

function buildTranslations(): GuideTranslation[] {
  const rows: GuideTranslation[] = []
  for (const l of ['es', 'fr'] as const) {
    const t = trans.value[l]
    if (t.title.trim() || t.body.trim() || t.tasks.some((task) => task.label.trim())) {
      rows.push({ locale: l, title: t.title.trim() || null, body: t.body.trim() || null, tasks: t.tasks.filter((task) => task.label.trim()).map((task) => ({ taskId: task.taskId, label: task.label.trim() })) })
    }
  }
  return rows
}

function addTask() {
  form.value.tasks.push({ label: '' })
  trans.value.es.tasks.push({ label: '' })
  trans.value.fr.tasks.push({ label: '' })
}

function removeTask(index: number) {
  form.value.tasks.splice(index, 1)
  trans.value.es.tasks.splice(index, 1)
  trans.value.fr.tasks.splice(index, 1)
}

async function submit() {
  errors.value = {}
  if (!form.value.phase.trim()) errors.value.phase = '阶段必填'
  if (!form.value.title.trim()) errors.value.title = '标题必填'
  if (Object.keys(errors.value).length) {
    locale.value = 'en'
    return
  }
  saving.value = true
  try {
    await store.saveGuide(
      {
        phase: form.value.phase.trim(),
        timeframe: form.value.timeframe.trim() || null,
        title: form.value.title.trim(),
        tasks: form.value.tasks.map((t) => ({ taskId: t.taskId, label: t.label.trim() })).filter((t) => t.label),
        body: form.value.body.trim() || null,
        status: form.value.status,
        translations: buildTranslations(),
      },
      props.editing?.id,
    )
    toast.success('指南已保存，已触发缓存失效链')
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
  <DrawerShell :open="open" eyebrow="Content · CMS" :title="editing ? '编辑指南' : '新增指南'" @close="emit('close')">
    <LocaleTabs v-model="locale" :filled="filled" />

    <div v-if="locale === 'en'" class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">阶段 *</label>
          <input v-model="form.phase" class="field" placeholder="如 12 Months Before" />
          <p v-if="errors.phase" class="mt-1 text-[11px] text-danger">{{ errors.phase }}</p>
        </div>
        <div>
          <label class="field-label">时间范围</label>
          <input v-model="form.timeframe" class="field" placeholder="如 12-10 个月" />
        </div>
      </div>
      <div>
        <label class="field-label">标题 *</label>
        <input v-model="form.title" class="field" />
        <p v-if="errors.title" class="mt-1 text-[11px] text-danger">{{ errors.title }}</p>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">状态</label>
          <SelectMenu
            :model-value="form.status"
            :options="[{ value: PublishStatus.DRAFT, label: '草稿' }, { value: PublishStatus.PUBLISHED, label: '已发布' }]"
            @update:model-value="form.status = $event as typeof form.status"
          />
        </div><div></div>
      </div>
      <div>
        <label class="field-label">正文（EN）</label>
        <p class="mb-2 text-[11px] text-ink-faint">任务清单请在下方单独维护，正文仅用于补充说明。</p>
        <VditorEditor v-model="form.body" :height="320" upload-scope="content" />
      </div>
      <div>
        <label class="field-label">待办任务</label>
        <div class="space-y-2">
          <div v-for="(task, i) in form.tasks" :key="task.taskId ?? `new-${i}`" class="flex gap-2"><input v-model="task.label" class="field" :placeholder="`任务 ${i + 1}`" /><button type="button" class="btn-danger-ghost" @click="removeTask(i)">删除</button></div>
          <button type="button" class="btn-outline" @click="addTask">+ 添加任务</button>
        </div>
      </div>
    </div>

    <template v-for="l in ['es', 'fr'] as const" :key="l">
    <div v-if="locale === l" class="space-y-4">
      <div>
        <label class="field-label">标题（{{ l.toUpperCase() }}）</label>
        <input v-model="trans[l].title" class="field" />
      </div>
      <div>
        <label class="field-label">正文（{{ l.toUpperCase() }}）</label>
        <VditorEditor v-model="trans[l].body" :height="320" upload-scope="content" />
      </div>
      <div>
        <label class="field-label">待办任务（{{ l.toUpperCase() }}）</label>
        <div class="space-y-2">
          <input v-for="(task, i) in trans[l].tasks" :key="task.taskId ?? i" v-model="task.label" class="field" :placeholder="form.tasks[i]?.label || `任务 ${i + 1}`" />
        </div>
        <p class="mt-2 text-[11px] text-ink-faint">按 EN 任务顺序填写；留空时回退 EN。</p>
      </div>
      <p class="text-[11px] text-ink-faint">留空时消费端回退 EN（决策 13）。</p>
    </div>
    </template>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
