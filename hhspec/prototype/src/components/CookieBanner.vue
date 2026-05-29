<script setup>
import { useI18n } from 'vue-i18n'
import { useUiStore } from '../stores/ui'
const ui = useUiStore()
const { t } = useI18n()
</script>

<template>
  <transition enter-active-class="transition duration-500 ease-editorial" enter-from-class="translate-y-full opacity-0" enter-to-class="translate-y-0 opacity-100" leave-active-class="transition duration-300" leave-from-class="translate-y-0" leave-to-class="translate-y-full opacity-0">
    <div v-if="ui.cookieVisible" class="fixed bottom-0 inset-x-0 z-[55] bg-ink-950 text-white">
      <div class="container-editorial py-4 flex flex-col sm:flex-row items-center gap-4 justify-between">
        <p class="text-xs text-ink-300 max-w-2xl">{{ t('cookie.text') }}</p>
        <div class="flex items-center gap-3 shrink-0">
          <button @click="ui.rejectCookie()" class="editorial-label-light text-ink-300 hover:text-white">{{ t('cookie.reject') }}</button>
          <button @click="ui.acceptCookie()" class="bg-white text-ink-950 px-6 py-2.5 editorial-label text-[11px] hover:bg-champagne-100 transition-colors">{{ t('cookie.accept') }}</button>
        </div>
      </div>
    </div>
  </transition>
</template>
