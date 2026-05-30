<script setup>
import { computed } from 'vue'
const props = defineProps({
  data: { type: Array, required: true },
  labels: { type: Array, default: () => [] },
  height: { type: Number, default: 220 },
  stroke: { type: String, default: '#C19A6B' },
  fill: { type: String, default: 'rgba(193,154,107,0.14)' }
})
const W = 760
const pts = computed(() => {
  const max = Math.max(...props.data) * 1.1
  const min = Math.min(...props.data) * 0.85
  const span = max - min || 1
  const step = W / (props.data.length - 1)
  return props.data.map((v, i) => ({ x: i * step, y: props.height - ((v - min) / span) * (props.height - 24) - 12 }))
})
const line = computed(() => pts.value.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '))
const area = computed(() => `${line.value} L${W},${props.height} L0,${props.height} Z`)
</script>

<template>
  <div class="w-full">
    <svg :viewBox="`0 0 ${W} ${height}`" class="w-full" preserveAspectRatio="none" :style="{ height: height + 'px' }">
      <path :d="area" :fill="fill" />
      <path :d="line" :fill="'none'" :stroke="stroke" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      <circle v-for="(p, i) in pts" :key="i" :cx="p.x" :cy="p.y" r="2.5" :fill="stroke" />
    </svg>
    <div v-if="labels.length" class="mt-2 flex justify-between text-[10px] text-ink-faint">
      <span v-for="(l, i) in labels" :key="i" v-show="i % 2 === 0">{{ l }}</span>
    </div>
  </div>
</template>
