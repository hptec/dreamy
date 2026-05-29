<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { useUiStore } from '../stores/ui'
import { PRODUCT_IMAGES } from '../data/catalog'

const ui = useUiStore()
const { t } = useI18n()
const email = ref('')
const done = ref(false)

function submit() {
  if (!email.value) return
  done.value = true
  ui.pushToast(t('newsletter.success'))
  setTimeout(() => ui.closeNewsletter(), 1400)
}
</script>

<template>
  <TransitionRoot :show="ui.newsletterOpen" as="template">
    <Dialog @close="ui.closeNewsletter()" class="relative z-[60]">
      <TransitionChild as="template" enter="duration-300 ease-out" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
      </TransitionChild>
      <div class="fixed inset-0 flex items-center justify-center p-4">
        <TransitionChild as="template" enter="duration-400 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0 scale-95">
          <DialogPanel class="w-full max-w-3xl bg-white grid sm:grid-cols-2 overflow-hidden shadow-2xl">
            <div class="hidden sm:block relative">
              <img :src="PRODUCT_IMAGES[12]" alt="Maison Eden" class="absolute inset-0 w-full h-full object-cover" />
            </div>
            <div class="p-8 sm:p-10 relative">
              <button @click="ui.closeNewsletter()" class="absolute top-4 right-4 text-ink-400 hover:text-ink-950" aria-label="Close"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
              <p class="editorial-label text-champagne-600 mb-3">Maison Eden</p>
              <h2 class="font-serif text-3xl leading-tight mb-3">{{ t('newsletter.title') }}</h2>
              <p class="text-sm text-ink-500 mb-6">{{ t('newsletter.desc') }}</p>
              <form @submit.prevent="submit" class="space-y-3">
                <input v-model="email" type="email" required placeholder="Email address" class="field" />
                <button type="submit" class="btn-ink w-full">{{ t('newsletter.cta') }}</button>
              </form>
              <button class="editorial-label text-ink-400 hover:text-ink-950 mt-4" @click="ui.closeNewsletter()">{{ t('newsletter.noThanks') }}</button>
            </div>
          </DialogPanel>
        </TransitionChild>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
