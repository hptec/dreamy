<script setup>
import { ref, computed } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useCartStore } from '../stores/cart'
import { useWishlistStore } from '../stores/wishlist'
import { useCurrencyStore } from '../stores/currency'
import { useUiStore } from '../stores/ui'
import { COLOR_MAP, PRODUCTS } from '../data/catalog'
import ProductCard from '../components/ProductCard.vue'

const cart = useCartStore()
const wishlist = useWishlistStore()
const currency = useCurrencyStore()
const ui = useUiStore()
const router = useRouter()
const { t } = useI18n()

const promoCode = ref('')

const shippingFee = computed(() => (cart.subtotal >= 500 ? 0 : 0))
const tax = computed(() => Math.max(0, (cart.subtotal - cart.discountUSD) * 0.08))
const total = computed(() => Math.max(0, cart.subtotal - cart.discountUSD + shippingFee.value + tax.value))
const freeShip = computed(() => cart.subtotal >= 500)
const remaining = computed(() => Math.max(0, 500 - cart.subtotal))

const bestSellers = computed(() => {
  const best = PRODUCTS.filter((p) => p.tags.includes('best-seller'))
  return (best.length >= 4 ? best : PRODUCTS).slice(0, 4)
})

function applyPromo() {
  const code = promoCode.value.trim()
  if (!code) return
  const ok = cart.applyPromo(code)
  if (ok) {
    ui.pushToast(t('toast.promoApplied'))
    promoCode.value = ''
  } else {
    ui.pushToast(t('toast.promoInvalid'), 'muted')
  }
}
function clearPromo() {
  cart.clearPromo()
  ui.pushToast('Promo removed', 'muted')
}
function moveToWishlist(item) {
  if (!wishlist.has(item.productId)) wishlist.toggle(item.productId)
  cart.remove(item.key)
  ui.pushToast(t('toast.addedToWishlist'))
}
function go(slug) { router.push(`/products/${slug}`) }
function goCheckout() { router.push('/checkout/address') }
</script>

<template>
  <div class="bg-canvas min-h-screen">
    <!-- breadcrumb -->
    <div class="container-editorial pt-8 sm:pt-10">
      <nav class="editorial-label text-ink-400 text-[10px] inline-flex items-center gap-2">
        <RouterLink to="/" class="hover:text-ink-900">Home</RouterLink>
        <span>/</span>
        <span class="text-ink-700">{{ t('cart.title') }}</span>
      </nav>
    </div>

    <header class="container-editorial pt-6 pb-10 sm:pb-12">
      <p v-if="!cart.isEmpty" class="editorial-label text-champagne-600 mb-3">Atelier · Shopping Bag</p>
      <h1 class="font-serif text-5xl sm:text-6xl lg:text-7xl text-ink-950 leading-none">{{ t('cart.title') }}</h1>
      <p v-if="!cart.isEmpty" class="text-ink-500 mt-4 text-sm">
        {{ cart.count }} {{ cart.count === 1 ? t('cart.item') : t('cart.items') }} reserved in your bag.
      </p>
    </header>

    <!-- empty state -->
    <section v-if="cart.isEmpty" class="container-editorial pb-24">
      <div class="bg-white border border-ink-100 py-20 sm:py-28 text-center px-6">
        <svg class="w-12 h-12 mx-auto text-ink-200" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
          <path stroke-linecap="round" d="M15.75 10.5V6a3.75 3.75 0 10-7.5 0v4.5m11.356-1.993l1.263 12A1.125 1.125 0 0119.747 21H4.253a1.125 1.125 0 01-1.122-1.243l1.263-12A1.125 1.125 0 015.513 6.75h12.974c.576 0 1.059.435 1.119 1.007z" />
        </svg>
        <p class="editorial-label text-champagne-600 mt-7 mb-3">Your bag rests empty</p>
        <h2 class="font-serif text-3xl sm:text-4xl text-ink-950">{{ t('cart.empty') }}</h2>
        <p class="text-ink-500 mt-3 max-w-md mx-auto">{{ t('cart.emptyDesc') }}</p>
        <div class="mt-9 flex flex-wrap justify-center gap-3">
          <RouterLink to="/wedding-dresses" class="btn-ink">Shop Wedding</RouterLink>
          <RouterLink to="/evening-dresses" class="btn-outline">Shop Evening</RouterLink>
        </div>
      </div>

      <div class="mt-20 sm:mt-24">
        <div class="flex items-end justify-between mb-8 sm:mb-10">
          <div>
            <p class="editorial-label text-champagne-600 mb-2">Curated by the Atelier</p>
            <h3 class="font-serif text-3xl sm:text-4xl">You May Also Like</h3>
          </div>
          <RouterLink to="/wedding-dresses" class="hidden sm:inline-block editorial-label link-underline text-ink-700 text-[10px]">{{ t('common.viewAll') }}</RouterLink>
        </div>
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
          <ProductCard v-for="p in bestSellers" :key="p.id" :product="p" />
        </div>
      </div>
    </section>

    <!-- with items -->
    <section
      v-else
      class="container-editorial pb-24 grid lg:grid-cols-[1fr_400px] gap-10 lg:gap-14 items-start"
    >
      <!-- left: line items -->
      <div>
        <!-- free shipping progress -->
        <div class="bg-white border border-ink-100 px-6 sm:px-8 py-5 mb-6">
          <p class="text-xs text-ink-600 text-center">
            <template v-if="freeShip">
              You've unlocked <span class="text-champagne-700 editorial-label text-[10px]">complimentary worldwide shipping</span>
            </template>
            <template v-else>
              Add <span class="text-ink-950 tabular-nums">{{ currency.format(remaining) }}</span> for complimentary worldwide shipping
            </template>
          </p>
          <div class="mt-3 h-0.5 bg-ink-100">
            <div
              class="h-full bg-champagne-500 transition-all duration-700 ease-editorial"
              :style="{ width: Math.min(100, (cart.subtotal / 500) * 100) + '%' }"
            ></div>
          </div>
        </div>

        <ul class="border-t border-ink-200 divide-y divide-ink-100 bg-white">
          <li v-for="it in cart.items" :key="it.key" class="flex gap-5 sm:gap-7 p-5 sm:p-7">
            <button class="shrink-0" :aria-label="`View ${it.name}`" @click="go(it.slug)">
              <img
                :src="it.image"
                :alt="it.name"
                class="w-24 sm:w-32 aspect-[3/4] object-cover bg-ink-50 transition-transform duration-500 hover:scale-[1.02]"
              />
            </button>
            <div class="flex-1 min-w-0 flex flex-col">
              <div class="flex justify-between gap-4">
                <div class="min-w-0">
                  <h3
                    class="font-serif text-xl sm:text-2xl text-ink-950 leading-tight cursor-pointer hover:text-champagne-700 transition-colors"
                    @click="go(it.slug)"
                  >{{ it.name }}</h3>
                  <p class="editorial-label text-ink-400 mt-1.5 text-[10px]">{{ it.productionLabel }}</p>
                  <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-600">
                    <span class="inline-flex items-center gap-1.5">
                      <span
                        class="w-3 h-3 rounded-full ring-1 ring-ink-200"
                        :style="{ background: COLOR_MAP[it.colorId]?.hex }"
                      ></span>
                      {{ COLOR_MAP[it.colorId]?.name }}
                    </span>
                    <span class="text-ink-200" aria-hidden="true">|</span>
                    <span>Size {{ it.size }}</span>
                  </div>
                </div>
                <div class="text-right shrink-0">
                  <span class="text-base tabular-nums text-ink-950">{{ currency.format(it.unitPriceUSD * it.qty) }}</span>
                  <p v-if="it.qty > 1" class="text-[10px] text-ink-400 tabular-nums mt-1">
                    {{ currency.format(it.unitPriceUSD) }} each
                  </p>
                </div>
              </div>
              <div class="mt-auto pt-6 flex items-end justify-between flex-wrap gap-3">
                <div class="inline-flex items-center border border-ink-200">
                  <button
                    class="w-9 h-9 grid place-items-center text-ink-500 hover:text-ink-950 transition-colors"
                    :aria-label="`Decrease ${it.name}`"
                    @click="cart.setQty(it.key, it.qty - 1)"
                  >−</button>
                  <span class="w-10 text-center text-sm tabular-nums">{{ it.qty }}</span>
                  <button
                    class="w-9 h-9 grid place-items-center text-ink-500 hover:text-ink-950 transition-colors"
                    :aria-label="`Increase ${it.name}`"
                    @click="cart.setQty(it.key, it.qty + 1)"
                  >+</button>
                </div>
                <div class="flex items-center gap-5 text-[11px] text-ink-500">
                  <button class="link-underline hover:text-ink-950" @click="moveToWishlist(it)">
                    {{ t('cart.saveForLater') }}
                  </button>
                  <button class="link-underline hover:text-ink-950" @click="cart.remove(it.key)">
                    {{ t('common.remove') }}
                  </button>
                </div>
              </div>
            </div>
          </li>
        </ul>

        <div class="mt-8 flex justify-between items-center">
          <RouterLink
            to="/wedding-dresses"
            class="editorial-label text-ink-500 hover:text-ink-950 inline-flex items-center gap-2 text-[10px] transition-colors"
          >
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" d="M15 19l-7-7 7-7" />
            </svg>
            {{ t('cart.continueShopping') }}
          </RouterLink>
        </div>
      </div>

      <!-- right: summary -->
      <aside class="lg:sticky lg:top-28 space-y-5">
        <div class="bg-white border border-ink-100 p-6 lg:p-8">
          <h2 class="font-serif text-2xl mb-1">{{ t('checkout.orderSummary') }}</h2>
          <p class="editorial-label text-ink-400 text-[10px]">{{ cart.count }} {{ cart.count === 1 ? t('cart.item') : t('cart.items') }}</p>

          <!-- promo input -->
          <div class="mt-6">
            <label for="promo" class="field-label">{{ t('cart.promoCode') }}</label>
            <div v-if="!cart.promo" class="flex gap-2">
              <input
                id="promo"
                v-model="promoCode"
                type="text"
                :placeholder="t('cart.promoPlaceholder')"
                class="field flex-1 uppercase tracking-wider text-xs"
                @keyup.enter="applyPromo"
              />
              <button type="button" class="btn-outline btn-sm" @click="applyPromo">{{ t('common.apply') }}</button>
            </div>
            <div
              v-else
              class="flex items-center justify-between bg-champagne-50 border border-champagne-200 px-4 py-3 text-xs"
            >
              <span class="inline-flex items-center gap-2 text-champagne-800">
                <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path stroke-linecap="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <strong class="tracking-label uppercase">{{ cart.promo.code }}</strong>
                <span class="text-ink-600 normal-case">— {{ cart.promo.label }}</span>
              </span>
              <button
                type="button"
                class="text-ink-400 hover:text-ink-950"
                aria-label="Remove promo"
                @click="clearPromo"
              >
                <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" />
                </svg>
              </button>
            </div>
            <p class="text-[10px] text-ink-400 mt-2">Try <span class="tracking-wider tabular-nums">WELCOME10</span> for first-order savings.</p>
          </div>

          <div class="hairline my-6"></div>

          <dl class="space-y-3 text-sm">
            <div class="flex justify-between">
              <dt class="text-ink-500">{{ t('cart.subtotal') }}</dt>
              <dd class="tabular-nums">{{ currency.format(cart.subtotal) }}</dd>
            </div>
            <div v-if="cart.promo" class="flex justify-between text-champagne-700">
              <dt>{{ t('cart.discount') }}</dt>
              <dd class="tabular-nums">−{{ currency.format(cart.discountUSD) }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-ink-500">{{ t('cart.estimatedShipping') }}</dt>
              <dd
                class="tabular-nums"
                :class="freeShip ? 'text-champagne-700 editorial-label text-[10px]' : ''"
              >{{ freeShip ? t('cart.freeShipping') : currency.format(shippingFee) }}</dd>
            </div>
            <div class="flex justify-between">
              <dt class="text-ink-500">{{ t('cart.tax') }}</dt>
              <dd class="tabular-nums">{{ currency.format(tax) }}</dd>
            </div>
          </dl>

          <div class="hairline my-6"></div>

          <div class="flex justify-between items-baseline mb-7">
            <span class="editorial-label text-ink-950">{{ t('cart.total') }}</span>
            <span class="font-serif text-3xl tabular-nums text-ink-950">{{ currency.format(total) }}</span>
          </div>

          <button type="button" class="btn-ink w-full" @click="goCheckout">{{ t('cart.checkout') }}</button>

          <p class="text-[10px] text-ink-400 mt-3 text-center uppercase tracking-label">
            Secure checkout · {{ currency.code }}
          </p>

          <div class="mt-7 grid grid-cols-3 gap-3 text-center pt-6 border-t border-ink-100">
            <div>
              <svg class="w-4 h-4 mx-auto text-champagne-600 mb-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3">
                <rect x="3" y="11" width="18" height="10" rx="1" />
                <path stroke-linecap="round" d="M7 11V7a5 5 0 0110 0v4" />
              </svg>
              <p class="editorial-label text-ink-400 text-[8px]">Encrypted</p>
            </div>
            <div>
              <svg class="w-4 h-4 mx-auto text-champagne-600 mb-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3">
                <path stroke-linecap="round" d="M3 8l4-4 4 4M7 4v12m14 0l-4 4-4-4m4 4V8" />
              </svg>
              <p class="editorial-label text-ink-400 text-[8px]">Free Returns</p>
            </div>
            <div>
              <svg class="w-4 h-4 mx-auto text-champagne-600 mb-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3">
                <circle cx="12" cy="12" r="9" />
                <path stroke-linecap="round" d="M3 12h18" />
              </svg>
              <p class="editorial-label text-ink-400 text-[8px]">Worldwide</p>
            </div>
          </div>
        </div>

        <p class="text-[11px] text-ink-400 text-center leading-relaxed px-3">
          By proceeding to checkout you agree to our <RouterLink to="/shipping-returns" class="link-underline text-ink-600">Return Policy</RouterLink> and <RouterLink to="/faq" class="link-underline text-ink-600">Terms of Service</RouterLink>.
        </p>
      </aside>
    </section>
  </div>
</template>
