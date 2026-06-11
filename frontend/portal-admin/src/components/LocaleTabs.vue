<script setup lang="ts">
// FORM-CAT-A06 / FORM-MKT-A05：三语 tab（EN 主字段，ES/FR 写 translations[]）
// tab 标注翻译完整度圆点（任一字段非空即绿）
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    /** 各 locale 是否已有内容（完整度圆点） */
    filled?: Partial<Record<'en' | 'es' | 'fr', boolean>>
  }>(),
  { filled: () => ({}) },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const tabs = computed(() => [
  { key: 'en', label: 'EN（主）', filled: props.filled.en ?? false },
  { key: 'es', label: 'ES', filled: props.filled.es ?? false },
  { key: 'fr', label: 'FR', filled: props.filled.fr ?? false },
])
</script>

<template>
  <div class="mb-3 flex gap-1 border-b border-line">
    <button
      v-for="t in tabs"
      :key="t.key"
      type="button"
      class="flex items-center gap-1.5 border-b-2 px-3 py-2 text-[12.5px] transition-colors"
      :class="modelValue === t.key ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
      @click="emit('update:modelValue', t.key)"
    >
      {{ t.label }}
      <span class="h-1.5 w-1.5 rounded-full" :class="t.filled ? 'bg-ok' : 'bg-line'"></span>
    </button>
  </div>
</template>
