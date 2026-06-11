<script setup lang="ts">
// 右侧抽屉壳（原型 Categories/Reviews 抽屉同型：fixed overlay + 右滑面板；不依赖 Headless-UI，规避 CP-072）
import { XMarkIcon } from '@heroicons/vue/24/outline'

withDefaults(defineProps<{ open: boolean; title?: string; eyebrow?: string; width?: string }>(), {
  title: '',
  eyebrow: '',
  width: 'max-w-lg',
})

const emit = defineEmits<{ (e: 'close'): void }>()
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex justify-end bg-ink/30" @click.self="emit('close')">
      <div class="flex h-full w-full flex-col border-l border-line bg-canvas shadow-2xl" :class="width">
        <div class="flex items-center justify-between border-b border-line bg-canvas px-6 py-4">
          <div>
            <p v-if="eyebrow" class="text-[11px] text-ink-faint">{{ eyebrow }}</p>
            <h3 class="font-display text-lg font-semibold text-ink">{{ title }}</h3>
          </div>
          <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="emit('close')">
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>
        <div class="flex-1 overflow-y-auto px-6 py-5">
          <slot />
        </div>
        <div v-if="$slots.footer" class="flex items-center justify-end gap-2 border-t border-line px-6 py-4">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
