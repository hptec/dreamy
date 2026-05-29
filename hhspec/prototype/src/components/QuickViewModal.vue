<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { useUiStore } from '../stores/ui'
import { useCartStore } from '../stores/cart'
import { useCurrencyStore } from '../stores/currency'
import { COLOR_MAP, STANDARD_SIZES } from '../data/catalog'
import ColorSwatches from './ColorSwatches.vue'
import StarRating from './StarRating.vue'

const ui = useUiStore()
const cart = useCartStore()
const currency = useCurrencyStore()
const router = useRouter()
const { t } = useI18n()

const color = ref('')
const size = ref('')
const product = computed(() => ui.quickViewProduct)

watch(product, (p) => {
  if (p) { color.value = p.colorIds?.[0] || ''; size.value = '' }
})

function addToBag() {
  const p = product.value
  if (!p) return
  cart.add({
    productId: p.id, slug: p.slug, name: p.name, image: p.thumb,
    colorId: color.value, colorName: COLOR_MAP[color.value]?.name,
    size: size.value || 'US 6', productionTimeId: 'standard', productionLabel: 'Standard · 3–4 weeks',
    unitPriceUSD: p.basePrice, qty: 1,
  })
  ui.pushToast(t('toast.addedToBag'))
  ui.closeQuickView()
  ui.openCart()
}
function viewFull() { const p = product.value; ui.closeQuickView(); router.push(`/products/${p.slug}`) }
</script>

<template>
  <TransitionRoot :show="!!product" as="template">
    <Dialog @close="ui.closeQuickView()" class="relative z-[60]">
      <TransitionChild as="template" enter="duration-300 ease-out" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
      </TransitionChild>
      <div class="fixed inset-0 flex items-center justify-center p-4">
        <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0 scale-95">
          <DialogPanel v-if="product" class="w-full max-w-4xl bg-white grid sm:grid-cols-2 overflow-hidden shadow-2xl max-h-[90vh]">
            <div class="relative bg-ink-50 aspect-[3/4] sm:aspect-auto">
              <img :src="product.thumb" :alt="product.fullName" class="absolute inset-0 w-full h-full object-cover" />
            </div>
            <div class="p-8 overflow-y-auto relative">
              <button @click="ui.closeQuickView()" class="absolute top-4 right-4 text-ink-400 hover:text-ink-950" aria-label="Close"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
              <p class="editorial-label text-ink-400 mb-2">{{ product.silhouette }} · {{ product.fabric }}</p>
              <h2 class="font-serif text-3xl mb-2">{{ product.name }}</h2>
              <StarRating :rating="product.rating" :count="product.reviewCount" class="mb-4" />
              <div class="flex items-baseline gap-2 mb-6">
                <span class="text-xl tabular-nums">{{ currency.format(product.basePrice) }}</span>
                <span v-if="product.originalPrice" class="text-sm text-ink-400 line-through tabular-nums">{{ currency.format(product.originalPrice) }}</span>
              </div>
              <div class="mb-5">
                <p class="field-label">{{ t('product.color') }}: <span class="text-ink-900 normal-case tracking-normal">{{ COLOR_MAP[color]?.name }}</span></p>
                <ColorSwatches v-model="color" :color-ids="product.colorIds" :max="10" size="md" />
              </div>
              <div class="mb-6">
                <p class="field-label">{{ t('product.size') }}</p>
                <div class="flex flex-wrap gap-2">
                  <button v-for="s in STANDARD_SIZES.slice(0,9)" :key="s" @click="size = s" class="px-3 py-2 text-xs border transition-colors" :class="size === s ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 hover:border-ink-950'">{{ s }}</button>
                </div>
              </div>
              <button class="btn-ink w-full mb-3" @click="addToBag">{{ t('product.addToBag') }}</button>
              <button class="link-underline editorial-label text-ink-500 mx-auto block" @click="viewFull">{{ t('common.viewDetails') }}</button>
            </div>
          </DialogPanel>
        </TransitionChild>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
