<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
const router = useRouter()
const { t } = useI18n()
const hovered = ref(false)
</script>

<template>
  <button
    @click="router.push('/contact')"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    class="fixed bottom-6 right-6 z-[55] flex items-center gap-3 group"
    aria-label="Live chat"
  >
    <transition enter-active-class="transition duration-300" enter-from-class="opacity-0 translate-x-2" enter-to-class="opacity-100 translate-x-0" leave-active-class="transition duration-200" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <span v-if="hovered" class="hidden sm:block bg-white text-ink-900 text-xs px-4 py-2 shadow-lg whitespace-nowrap">{{ t('liveChat') }}</span>
    </transition>
    <span class="w-14 h-14 bg-ink-950 text-white grid place-items-center shadow-xl group-hover:bg-champagne-600 transition-colors duration-300">
      <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4"><path stroke-linecap="round" stroke-linejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.8 9.8 0 01-4-.84L3 20l1.05-3.5A7.6 7.6 0 013 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" /></svg>
    </span>
  </button>
</template>
