<script setup lang="ts">
// 三语 tab（EN 主字段，ES/FR 写 translations[]）
// tab 标注翻译状态国旗：filled=已填，missing=应填未填，disabled=不在应翻译范围
// 兼容：传入 filled (布尔) 时按 filled/missing 二态推导
import { computed } from 'vue'
import LocaleFlag from '@/components/ui/LocaleFlag.vue'

type LocaleKey = 'en' | 'es' | 'fr'
type LocaleState = 'filled' | 'missing' | 'disabled'

const props = withDefaults(
  defineProps<{
    modelValue: LocaleKey
    /** 各 locale 翻译状态（推荐） */
    state?: Partial<Record<LocaleKey, LocaleState>>
    /** 兼容旧 filled 布尔（true→filled，false→missing） */
    filled?: Partial<Record<LocaleKey, boolean>>
  }>(),
  { state: () => ({}), filled: () => ({}) },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: LocaleKey): void }>()

function resolveState(key: LocaleKey): LocaleState {
  if (props.state && props.state[key]) return props.state[key] as LocaleState
  // 兼容 filled 布尔
  const f = props.filled?.[key]
  return f ? 'filled' : 'missing'
}

const tabs = computed(() => [
  { key: 'en' as LocaleKey, label: 'EN（主）', state: resolveState('en') },
  { key: 'es' as LocaleKey, label: 'ES', state: resolveState('es') },
  { key: 'fr' as LocaleKey, label: 'FR', state: resolveState('fr') },
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
      <LocaleFlag :locale="t.key" :state="t.state" />
    </button>
  </div>
</template>
