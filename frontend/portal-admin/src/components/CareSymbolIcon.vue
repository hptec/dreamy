<script setup lang="ts">
// CareSymbolIcon.vue — ISO 3758 护理符号 SVG 线条图标（与消费端 care-symbol-icon.tsx 同构）
// 按 CareItem.symbol（emoji）映射；未命中回退显示原 emoji。
import type { ClassValue } from 'vue'

const props = withDefaults(defineProps<{ symbol: string; class?: ClassValue }>(), {
  class: 'h-7 w-7',
})

// emoji → SVG path 片段集合（24×24 viewBox）
type IconDef = { paths?: string[]; dots?: [number, number, number][] }
const ICONS: Record<string, IconDef> = {
  '🫧': { paths: ['M4 8h16l-2 11H6L4 8z', 'M8 13c1.3-1 2.7-1 4 0s2.7 1 4 0'] },
  '🌀': { paths: ['M4 8h16l-2 11H6L4 8z', 'M8 12c1.3-1 2.7-1 4 0s2.7 1 4 0', 'M8 14.5c1.3-1 2.7-1 4 0s2.7 1 4 0'] },
  '🚫': { paths: ['M4 8h16l-2 11H6L4 8z', 'M5 19L19 5'] },
  '🧊': { paths: ['M12 4L21 19H3L12 4z', 'M8.5 10l7 8', 'M15.5 10l-7 8'] },
  '△': { paths: ['M12 4L21 19H3L12 4z'] },
  '🌡': { paths: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0'], dots: [[12, 12, 2]] },
  '🪝': { paths: ['M3 6h18v15H3z', 'M12 6V3', 'M9 3q1.5-2 3 0'] },
  '❌': { paths: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M5 19L19 5'] },
  '♨': { paths: ['M4 16h16l2-6H10C6 10 4 12 4 16z'], dots: [[12, 14, 1.5]] },
  '💨': { paths: ['M4 16h16l2-6H10C6 10 4 12 4 16z', 'M9 9Q8.5 7 9 5', 'M13 9q-.5-2 0-4'] },
  '🚷': { paths: ['M4 16h16l2-6H10C6 10 4 12 4 16z', 'M5 19L19 5'] },
  '⭕': { paths: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0'] },
  '⊗': { paths: ['M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0', 'M5 19L19 5'] },
}

const icon = () => ICONS[props.symbol]
</script>

<template>
  <svg
    v-if="icon()"
    :class="props.class"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, i) in icon()!.paths || []" :key="'p' + i" :d="d" />
    <circle
      v-for="(c, i) in icon()!.dots || []"
      :key="'c' + i"
      :cx="c[0]"
      :cy="c[1]"
      :r="c[2]"
      fill="currentColor"
      stroke="none"
    />
  </svg>
  <span v-else :class="[props.class, 'text-2xl leading-none']" aria-hidden="true">{{ symbol }}</span>
</template>
