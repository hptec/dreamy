<script setup lang="ts">
// LocaleFlag：locale 国旗图标，三态可视化翻译状态
// - filled: 已有翻译内容（彩色国旗）
// - missing: 应翻译但未填（灰色国旗 + tooltip 提示）
// - disabled: 不在应翻译范围（低对比度，鼠标不提示）
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    locale: 'en' | 'es' | 'fr'
    state?: 'filled' | 'missing' | 'disabled'
    size?: 'sm' | 'md'
  }>(),
  { state: 'filled', size: 'sm' },
)

const flag = computed(() => {
  switch (props.locale) {
    case 'en':
      return '🇺🇸'
    case 'es':
      return '🇪🇸'
    case 'fr':
      return '🇫🇷'
  }
})

const tooltip = computed(() => {
  switch (props.state) {
    case 'filled':
      return `${props.locale.toUpperCase()} 已填写`
    case 'missing':
      return `${props.locale.toUpperCase()} 应翻译但未填`
    case 'disabled':
      return `${props.locale.toUpperCase()} 不在应翻译范围`
  }
})

const sizeClass = computed(() => (props.size === 'md' ? 'text-[16px]' : 'text-[13px]'))
const stateClass = computed(() => {
  switch (props.state) {
    case 'filled':
      return 'opacity-100'
    case 'missing':
      return 'opacity-30 grayscale'
    case 'disabled':
      return 'opacity-20 grayscale'
  }
})
</script>

<template>
  <span
    class="inline-flex leading-none"
    :class="[sizeClass, stateClass]"
    :title="tooltip"
    :aria-label="tooltip"
  >
    {{ flag }}
  </span>
</template>
