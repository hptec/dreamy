<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { useUiStore } from '../stores/ui'
import { useCurrencyStore } from '../stores/currency'
import { searchProducts } from '../data/catalog'

const ui = useUiStore()
const currency = useCurrencyStore()
const router = useRouter()
const { t } = useI18n()

const q = ref('')
const inputRef = ref(null)
const popular = ['A-Line', 'Mermaid', 'Lace', 'Cabernet', 'Bridesmaid', 'Veil']
const suggestions = computed(() => (q.value.length >= 2 ? searchProducts(q.value).slice(0, 5) : []))

watch(() => ui.searchOpen, async (open) => {
  if (open) { await nextTick(); inputRef.value?.focus() }
  else q.value = ''
})

function submit() {
  if (!q.value.trim()) return
  ui.closeSearch()
  router.push(`/search?q=${encodeURIComponent(q.value.trim())}`)
}
function go(slug) { ui.closeSearch(); router.push(`/products/${slug}`) }
function quick(term) { q.value = term; submit() }
</script>

<template>
  <TransitionRoot :show="ui.searchOpen" as="template">
    <Dialog @close="ui.closeSearch()" class="relative z-50">
      <TransitionChild as="template" enter="duration-300 ease-out" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
      </TransitionChild>
      <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 -translate-y-4" enter-to="opacity-100 translate-y-0" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0 -translate-y-4">
        <DialogPanel class="fixed inset-x-0 top-0 bg-white">
          <div class="container-editorial py-8">
            <div class="flex items-center justify-between mb-6">
              <span class="editorial-label text-ink-400">{{ t('header.search') }}</span>
              <button @click="ui.closeSearch()" class="text-ink-500 hover:text-ink-950" aria-label="Close"><svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
            </div>
            <form @submit.prevent="submit" class="border-b-2 border-ink-950 flex items-center gap-3 pb-3">
              <svg class="w-6 h-6 text-ink-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z" /></svg>
              <input ref="inputRef" v-model="q" type="text" :placeholder="t('header.searchPlaceholder')" class="flex-1 bg-transparent font-serif text-2xl sm:text-4xl placeholder:text-ink-300 focus:outline-none" />
            </form>

            <div class="grid md:grid-cols-2 gap-10 mt-8 pb-6">
              <div>
                <p class="editorial-label text-ink-400 mb-4">Popular Searches</p>
                <div class="flex flex-wrap gap-2">
                  <button v-for="p in popular" :key="p" @click="quick(p)" class="border border-ink-200 px-4 py-2 text-sm hover:border-ink-950 transition-colors">{{ p }}</button>
                </div>
              </div>
              <div v-if="suggestions.length">
                <p class="editorial-label text-ink-400 mb-4">Suggestions</p>
                <ul class="space-y-3">
                  <li v-for="s in suggestions" :key="s.id" @click="go(s.slug)" class="flex items-center gap-4 cursor-pointer group">
                    <img :src="s.thumb" :alt="s.name" class="w-12 h-16 object-cover bg-ink-50" />
                    <div class="flex-1">
                      <p class="font-serif text-base group-hover:text-champagne-700 transition-colors">{{ s.name }}</p>
                      <p class="editorial-label text-ink-400 text-[10px]">{{ s.silhouette }}</p>
                    </div>
                    <span class="text-sm text-ink-600 tabular-nums">{{ currency.format(s.basePrice) }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </DialogPanel>
      </TransitionChild>
    </Dialog>
  </TransitionRoot>
</template>
