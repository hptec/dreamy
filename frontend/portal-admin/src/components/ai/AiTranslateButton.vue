<script setup lang="ts">
// AiTranslateButton：翻译按钮 + AiTranslateModal 组合封装（FUNC-011 11 处嵌入的最小单元）
// 用法：放在 ES/FR 字段旁，v-model 绑定该字段，sourceText 传 EN 主字段值
import { ref } from 'vue'
import { SparklesIcon } from '@heroicons/vue/24/outline'
import AiTranslateModal from './AiTranslateModal.vue'
import { useToastStore } from '@/stores/toast'

const props = withDefaults(
  defineProps<{
    /** 当前译文（v-model，确认回填写回此值） */
    modelValue: string
    /** EN 主字段源文本 */
    sourceText: string
    targetLang: 'es' | 'fr'
    fieldLabel?: string
    /** 紧凑模式（仅图标，用于行内/卡片密集场景） */
    compact?: boolean
  }>(),
  { fieldLabel: '', compact: false },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const toast = useToastStore()
const open = ref(false)

function onClick() {
  // EDGE-002：源文本为空不打开弹窗，提示先填 EN
  if (!props.sourceText.trim()) {
    toast.error('请先填写 EN 主字段内容')
    return
  }
  open.value = true
}

function onConfirm(text: string) {
  emit('update:modelValue', text)
}
</script>

<template>
  <span class="inline-flex">
    <button
      type="button"
      class="inline-flex items-center gap-1 rounded-luxe border border-line px-2 py-1 text-[11.5px] text-gold-deep transition-colors hover:border-gold hover:bg-gold/8 disabled:opacity-40"
      :disabled="!sourceText.trim()"
      :title="sourceText.trim() ? `AI 翻译 EN → ${targetLang.toUpperCase()}` : '请先填写 EN 主字段内容'"
      @click="onClick"
    >
      <SparklesIcon class="h-3.5 w-3.5" />
      <span v-if="!compact">AI 翻译</span>
    </button>

    <AiTranslateModal
      :open="open"
      :source-text="sourceText"
      :target-lang="targetLang"
      :field-label="fieldLabel"
      @confirm="onConfirm"
      @close="open = false"
    />
  </span>
</template>
