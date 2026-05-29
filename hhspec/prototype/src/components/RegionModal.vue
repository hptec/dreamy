<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { useUiStore } from '../stores/ui'
import { useCurrencyStore } from '../stores/currency'
import { REGIONS } from '../config/currency'
import { setLocale } from '../i18n'

const ui = useUiStore()
const currency = useCurrencyStore()
const { t, locale } = useI18n()
const selected = ref('US')

function confirm() {
  const r = REGIONS.find((x) => x.code === selected.value)
  if (r) {
    currency.setCurrency(r.currency)
    setLocale(r.locale)
    locale.value = r.locale
  }
  ui.closeRegion()
}
</script>

<template>
  <TransitionRoot :show="ui.regionModalOpen" as="template">
    <Dialog @close="ui.closeRegion()" class="relative z-[60]">
      <TransitionChild as="template" enter="duration-300 ease-out" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
      </TransitionChild>
      <div class="fixed inset-0 flex items-center justify-center p-4">
        <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200 ease-in" leave-from="opacity-100 scale-100" leave-to="opacity-0 scale-95">
          <DialogPanel class="w-full max-w-md bg-white p-8 sm:p-10 text-center">
            <p class="editorial-label text-champagne-600 mb-3">Maison Eden</p>
            <h2 class="font-serif text-2xl mb-2">{{ t('region.title') }}</h2>
            <p class="text-sm text-ink-500 mb-6">{{ t('region.desc') }}</p>
            <label class="field-label text-left">{{ t('region.country') }}</label>
            <select v-model="selected" class="field mb-6">
              <option v-for="r in REGIONS" :key="r.code" :value="r.code">{{ r.flag }} {{ r.label }} — {{ r.currency }}</option>
            </select>
            <button class="btn-ink w-full mb-3" @click="confirm">{{ t('region.confirm') }}</button>
            <button class="editorial-label text-ink-400 hover:text-ink-950" @click="ui.closeRegion()">{{ t('region.skip') }}</button>
          </DialogPanel>
        </TransitionChild>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
