<script setup>
import { computed } from 'vue'
import { COLOR_MAP } from '../data/catalog'

const props = defineProps({
  colorIds: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' },
  max: { type: Number, default: 6 },
  size: { type: String, default: 'sm' }, // sm | md
  interactive: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue'])

const shown = computed(() => props.colorIds.slice(0, props.max))
const overflow = computed(() => Math.max(0, props.colorIds.length - props.max))
const dim = computed(() => (props.size === 'md' ? 'w-6 h-6' : 'w-4 h-4'))

function pick(id) {
  if (props.interactive) emit('update:modelValue', id)
}
</script>

<template>
  <div class="flex items-center gap-1.5">
    <button
      v-for="id in shown"
      :key="id"
      type="button"
      :title="COLOR_MAP[id]?.name"
      :aria-label="COLOR_MAP[id]?.name"
      @click.stop.prevent="pick(id)"
      class="rounded-full ring-1 ring-ink-200 transition-all duration-200"
      :class="[dim, modelValue === id ? 'ring-2 ring-ink-900 ring-offset-2 ring-offset-white' : 'hover:ring-ink-400', interactive ? 'cursor-pointer' : 'cursor-default']"
      :style="{ backgroundColor: COLOR_MAP[id]?.hex }"
    />
    <span v-if="overflow > 0" class="text-[11px] text-ink-400 tabular-nums">+{{ overflow }}</span>
  </div>
</template>
