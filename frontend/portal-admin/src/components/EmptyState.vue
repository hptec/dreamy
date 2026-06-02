<script setup>
import { InboxIcon } from '@heroicons/vue/24/outline'
defineProps({ title: String, hint: String })
</script>

<template>
  <div class="flex flex-col items-center justify-center px-6 py-16 text-center">
    <div class="flex h-14 w-14 items-center justify-center rounded-full bg-canvas-warm">
      <InboxIcon class="h-7 w-7 text-ink-faint" />
    </div>
    <p class="mt-4 font-display text-lg text-ink">{{ title }}</p>
    <p v-if="hint" class="mt-1 max-w-sm text-[13px] text-ink-faint">{{ hint }}</p>
    <div class="mt-4"><slot name="action" /></div>
  </div>
</template>
