<script setup>
import { useUiStore } from '../stores/ui'
const ui = useUiStore()
</script>

<template>
  <div class="fixed bottom-6 left-1/2 -translate-x-1/2 z-[70] flex flex-col items-center gap-2 pointer-events-none">
    <transition-group enter-active-class="transition duration-300 ease-editorial" enter-from-class="translate-y-4 opacity-0" enter-to-class="translate-y-0 opacity-100" leave-active-class="transition duration-200" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-for="tt in ui.toasts" :key="tt.id" class="pointer-events-auto bg-ink-950 text-white px-5 py-3 shadow-xl flex items-center gap-3 min-w-[260px]">
        <svg v-if="tt.type === 'success'" class="w-4 h-4 text-champagne-400 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" /></svg>
        <svg v-else class="w-4 h-4 text-ink-300 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" d="M5 12h14" /></svg>
        <span class="text-sm flex-1">{{ tt.message }}</span>
        <button @click="ui.dismissToast(tt.id)" class="text-ink-500 hover:text-white" aria-label="Dismiss"><svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
      </div>
    </transition-group>
  </div>
</template>
