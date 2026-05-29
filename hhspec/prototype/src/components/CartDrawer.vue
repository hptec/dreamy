<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { useUiStore } from '../stores/ui'
import { useCartStore } from '../stores/cart'
import { useCurrencyStore } from '../stores/currency'
import { COLOR_MAP } from '../data/catalog'

const ui = useUiStore()
const cart = useCartStore()
const currency = useCurrencyStore()
const router = useRouter()
const { t } = useI18n()

const freeShip = computed(() => cart.subtotal >= 500)
const remaining = computed(() => Math.max(0, 500 - cart.subtotal))

function goCart() { ui.closeCart(); router.push('/cart') }
function goCheckout() { ui.closeCart(); router.push('/checkout/address') }
function go(slug) { ui.closeCart(); router.push(`/products/${slug}`) }
</script>

<template>
  <TransitionRoot :show="ui.cartDrawerOpen" as="template">
    <Dialog @close="ui.closeCart()" class="relative z-50">
      <TransitionChild as="template" enter="duration-300 ease-out" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-ink-950/50 backdrop-blur-sm" />
      </TransitionChild>
      <div class="fixed inset-0 overflow-hidden">
        <div class="absolute inset-y-0 right-0 max-w-full flex">
          <TransitionChild as="template" enter="transform transition duration-400 ease-editorial" enter-from="translate-x-full" enter-to="translate-x-0" leave="transform transition duration-300 ease-in" leave-from="translate-x-0" leave-to="translate-x-full">
            <DialogPanel class="w-screen max-w-md bg-white flex flex-col h-full shadow-2xl">
              <!-- header -->
              <div class="flex items-center justify-between px-6 py-5 border-b border-ink-100">
                <h2 class="font-serif text-xl">{{ t('cart.title') }} <span class="text-ink-400 text-base">({{ cart.count }})</span></h2>
                <button @click="ui.closeCart()" aria-label="Close" class="p-1 text-ink-500 hover:text-ink-950">
                  <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg>
                </button>
              </div>

              <!-- free shipping progress -->
              <div v-if="!cart.isEmpty" class="px-6 py-3 bg-ink-50 border-b border-ink-100">
                <p class="text-xs text-ink-600 text-center">
                  <template v-if="freeShip">You've unlocked <span class="text-champagne-700">complimentary shipping</span> ✦</template>
                  <template v-else>Add <span class="text-ink-950 tabular-nums">{{ currency.format(remaining) }}</span> for free worldwide shipping</template>
                </p>
                <div class="mt-2 h-0.5 bg-ink-200"><div class="h-full bg-champagne-500 transition-all duration-500" :style="{ width: Math.min(100, (cart.subtotal / 500) * 100) + '%' }"></div></div>
              </div>

              <!-- items -->
              <div class="flex-1 overflow-y-auto">
                <div v-if="cart.isEmpty" class="h-full flex flex-col items-center justify-center text-center px-8 gap-4">
                  <svg class="w-12 h-12 text-ink-200" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1"><path stroke-linecap="round" d="M15.75 10.5V6a3.75 3.75 0 10-7.5 0v4.5m11.356-1.993l1.263 12A1.125 1.125 0 0119.747 21H4.253a1.125 1.125 0 01-1.122-1.243l1.263-12A1.125 1.125 0 015.513 6.75h12.974c.576 0 1.059.435 1.119 1.007z" /></svg>
                  <p class="font-serif text-xl">{{ t('cart.empty') }}</p>
                  <p class="text-sm text-ink-500">{{ t('cart.emptyDesc') }}</p>
                  <button class="btn-ink mt-2" @click="goCart">{{ t('common.shopNow') }}</button>
                </div>
                <ul v-else class="divide-y divide-ink-100">
                  <li v-for="it in cart.items" :key="it.key" class="flex gap-4 p-6">
                    <img :src="it.image" :alt="it.name" class="w-20 h-28 object-cover bg-ink-50 cursor-pointer" @click="go(it.slug)" />
                    <div class="flex-1 min-w-0">
                      <div class="flex justify-between gap-2">
                        <h3 class="font-serif text-base leading-tight cursor-pointer hover:text-champagne-700" @click="go(it.slug)">{{ it.name }}</h3>
                        <button @click="cart.remove(it.key)" class="text-ink-300 hover:text-ink-950 shrink-0" aria-label="Remove"><svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
                      </div>
                      <div class="mt-1.5 flex items-center gap-2 text-[11px] text-ink-500">
                        <span class="inline-flex items-center gap-1"><span class="w-2.5 h-2.5 rounded-full ring-1 ring-ink-200" :style="{ background: COLOR_MAP[it.colorId]?.hex }"></span>{{ COLOR_MAP[it.colorId]?.name }}</span>
                        <span>·</span><span>{{ it.size }}</span>
                      </div>
                      <p class="editorial-label text-ink-400 mt-1 text-[9px]">{{ it.productionLabel }}</p>
                      <div class="mt-2 flex items-center justify-between">
                        <div class="inline-flex items-center border border-ink-200">
                          <button class="w-7 h-7 grid place-items-center text-ink-500 hover:text-ink-950" @click="cart.setQty(it.key, it.qty - 1)">−</button>
                          <span class="w-7 text-center text-xs tabular-nums">{{ it.qty }}</span>
                          <button class="w-7 h-7 grid place-items-center text-ink-500 hover:text-ink-950" @click="cart.setQty(it.key, it.qty + 1)">+</button>
                        </div>
                        <span class="text-sm tabular-nums">{{ currency.format(it.unitPriceUSD * it.qty) }}</span>
                      </div>
                    </div>
                  </li>
                </ul>
              </div>

              <!-- footer -->
              <div v-if="!cart.isEmpty" class="border-t border-ink-100 p-6 space-y-4">
                <div class="flex justify-between text-sm">
                  <span class="editorial-label text-ink-500">{{ t('cart.subtotal') }}</span>
                  <span class="tabular-nums text-ink-950">{{ currency.format(cart.subtotal) }}</span>
                </div>
                <p class="text-[11px] text-ink-400 text-center">{{ t('cart.calculatedAtCheckout') }}</p>
                <button class="btn-ink w-full" @click="goCheckout">{{ t('cart.checkout') }}</button>
                <button class="btn-outline w-full" @click="goCart">{{ t('cart.title') }}</button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
