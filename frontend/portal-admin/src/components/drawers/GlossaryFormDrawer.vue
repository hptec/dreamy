<script setup lang="ts">
// COMP-005 GlossaryForm（FORM-002 载体）：术语表条目创建/编辑抽屉（FUNC-022）
import { ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import Toggle from '@/components/Toggle.vue'
import { useGlossaryStore } from '@/stores/glossary'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateGlossaryForm, type FieldErrors } from '@/utils/validators'
import { GlossaryErrorCode } from '@/api/types'
import type { GlossaryTerm } from '@/api/types'

const props = defineProps<{ open: boolean; editing: GlossaryTerm | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useGlossaryStore()
const toast = useToastStore()

// 决策 14：婚纱领域常见分类（自由填写，预设便捷选项）
const CATEGORY_PRESETS = ['廓形', '领型', '面料', '工艺', '裙摆', '袖型']

const form = ref({ termEn: '', termEs: '', termFr: '', category: '', enabled: true })
const errors = ref<FieldErrors>({})
const saving = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    errors.value = {}
    form.value = props.editing
      ? {
          termEn: props.editing.termEn,
          termEs: props.editing.termEs || '',
          termFr: props.editing.termFr || '',
          category: props.editing.category || '',
          enabled: props.editing.enabled,
        }
      : { termEn: '', termEs: '', termFr: '', category: '', enabled: true }
  },
)

async function submit() {
  errors.value = validateGlossaryForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await store.saveTerm(
      {
        termEn: form.value.termEn.trim(),
        termEs: form.value.termEs.trim() || null,
        termFr: form.value.termFr.trim() || null,
        category: form.value.category.trim() || null,
        enabled: form.value.enabled,
      },
      props.editing?.id,
    )
    toast.success('已保存')
    emit('saved')
    emit('close')
  } catch (e) {
    if (e instanceof BizError) {
      if (e.code === GlossaryErrorCode.TERM_EN_EXISTS) {
        errors.value = { termEn: '该英文术语已存在' }
      } else if (e.code === GlossaryErrorCode.FIELD_VALIDATION_FAILED) {
        errors.value = extractFieldErrors(e)
        if (!Object.keys(errors.value).length) toast.error(e.message)
      } else {
        toast.error(e.message)
      }
    } else {
      toast.error('保存失败')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <DrawerShell
    :open="open"
    eyebrow="Glossary"
    :title="editing ? '编辑术语' : '新增术语'"
    width="max-w-md"
    @close="emit('close')"
  >
    <div class="space-y-4">
      <div>
        <label class="field-label">英文术语 (EN) *</label>
        <input v-model="form.termEn" class="field" placeholder="如 A-line / sweetheart neckline" maxlength="128" />
        <p v-if="errors.termEn" class="mt-1 text-[11px] text-danger">{{ errors.termEn }}</p>
      </div>
      <div>
        <label class="field-label">西语译法 (ES)</label>
        <input v-model="form.termEs" class="field" placeholder="如 línea A" maxlength="128" />
        <p v-if="errors.termEs" class="mt-1 text-[11px] text-danger">{{ errors.termEs }}</p>
      </div>
      <div>
        <label class="field-label">法语译法 (FR)</label>
        <input v-model="form.termFr" class="field" placeholder="如 ligne A" maxlength="128" />
        <p v-if="errors.termFr" class="mt-1 text-[11px] text-danger">{{ errors.termFr }}</p>
      </div>
      <div>
        <label class="field-label">分类</label>
        <input v-model="form.category" class="field" list="glossary-categories" placeholder="如 廓形 / 领型 / 面料" maxlength="32" />
        <datalist id="glossary-categories">
          <option v-for="c in CATEGORY_PRESETS" :key="c" :value="c" />
        </datalist>
        <p v-if="errors.category" class="mt-1 text-[11px] text-danger">{{ errors.category }}</p>
      </div>
      <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
        <div>
          <p class="text-[13px] font-medium text-ink">启用（注入翻译 prompt）</p>
          <p class="text-[12px] text-ink-faint">启用的术语会在 AI 翻译时注入系统提示词</p>
        </div>
        <Toggle v-model="form.enabled" />
      </div>
    </div>
    <template #footer>
      <button class="btn-outline" @click="emit('close')">取消</button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
