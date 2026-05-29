<script setup>
import { computed } from 'vue'

const props = defineProps({
  rating: { type: Number, default: 5 },
  count: { type: Number, default: null },
  size: { type: String, default: 'sm' }, // sm | md
})

const stars = computed(() => {
  const arr = []
  for (let i = 1; i <= 5; i++) {
    if (props.rating >= i) arr.push('full')
    else if (props.rating >= i - 0.5) arr.push('half')
    else arr.push('empty')
  }
  return arr
})
const dim = computed(() => (props.size === 'md' ? 'w-4 h-4' : 'w-3.5 h-3.5'))
</script>

<template>
  <div class="inline-flex items-center gap-1.5" :aria-label="`${rating} out of 5 stars`">
    <div class="flex items-center gap-0.5">
      <svg v-for="(s, i) in stars" :key="i" :class="dim" viewBox="0 0 20 20" aria-hidden="true">
        <defs v-if="s === 'half'">
          <linearGradient :id="`half-${i}`">
            <stop offset="50%" stop-color="#B5946A" />
            <stop offset="50%" stop-color="#E7E5E1" />
          </linearGradient>
        </defs>
        <path
          d="M10 1.5l2.47 5.18 5.7.6-4.27 3.83 1.2 5.59L10 13.9l-5.1 2.8 1.2-5.59L1.83 7.28l5.7-.6z"
          :fill="s === 'full' ? '#B5946A' : s === 'half' ? `url(#half-${i})` : '#E7E5E1'"
        />
      </svg>
    </div>
    <span v-if="count !== null" class="text-xs text-ink-500 tabular-nums">{{ rating.toFixed(1) }} ({{ count }})</span>
  </div>
</template>
