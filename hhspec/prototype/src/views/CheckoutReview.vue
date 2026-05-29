<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useCartStore } from '../stores/cart'
import { useCurrencyStore } from '../stores/currency'
import { SHIPPING_METHODS, COLOR_MAP } from '../data/catalog'
import CheckoutStepper from '../components/CheckoutStepper.vue'
import CheckoutSummary from '../components/CheckoutSummary.vue'

const router = useRouter()
const { t } = useI18n()
const cart = useCartStore()
const currency = useCurrencyStore()

const STORAGE_KEY = 'me_checkout'

function loadState() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') } catch (e) { return {} }
}

const state = ref(loadState())
const agree = ref(false)
const placing = ref(false)

const shippingMethod = computed(
  () => SHIPPING_METHODS.find((m) => m.id === state.value.shippingMethodId) || SHIPPING_METHODS[0],
)
const paymentLabel = computed(() => {
  const m = state.value.paymentMethod
  if (m === 'paypal') return 'PayPal'
  if (m === 'apple') return 'Apple Pay'
  return 'Credit Card'
})
const cardMasked = computed(() => {
  const n = (state.value.card?.number || '').replace(/\s+/g, '')
  if (n.length < 4) return ''
  return '•••• •••• •••• ' + n.slice(-4)
})

function genOrderNumber() {
  const n = Math.floor(10000 + Math.random() * 90000)
  return `ME-2026-${n}`
}
function fmtDate(d) {
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function placeOrder() {
  if (!agree.value || placing.value) return
  placing.value = true
  setTimeout(() => {
    const subtotal = cart.subtotal
    const discount = cart.discountUSD
    const shippingFee = subtotal >= 500 ? 0 : shippingMethod.value.feeUSD
    const tax = Math.max(0, (subtotal - discount) * 0.08)
    const total = Math.max(0, subtotal - discount + shippingFee + tax)
    const today = new Date()
    const delivery = new Date(today.getTime() + 28 * 24 * 60 * 60 * 1000)

    const lastOrder = {
      number: genOrderNumber(),
      email: state.value.email,
      subtotal,
      discount,
      shippingFee,
      tax,
      total,
      currency: currency.code,
      itemCount: cart.count,
      items: cart.items.map((it) => ({
        name: it.name,
        image: it.image,
        slug: it.slug,
        qty: it.qty,
        size: it.size,
        colorId: it.colorId,
        colorName: COLOR_MAP[it.colorId]?.name,
        unitPriceUSD: it.unitPriceUSD,
        productionLabel: it.productionLabel,
      })),
      address: state.value.address,
      shippingLabel: shippingMethod.value.label,
      shippingDetail: shippingMethod.value.detail,
      paymentLabel: paymentLabel.value,
      paymentMethod: state.value.paymentMethod,
      cardMasked: cardMasked.value,
      placedAt: today.toISOString(),
      estimatedDelivery: delivery.toISOString(),
      estimatedDeliveryLabel: fmtDate(delivery),
    }

    try {
      localStorage.setItem('me_last_order', JSON.stringify(lastOrder))
    } catch (e) { /* ignore */ }

    cart.clear()
    placing.value = false
    router.push('/checkout/success')
  }, 700)
}

onMounted(() => {
  if (cart.isEmpty) { router.replace('/cart'); return }
  if (!state.value.email || !state.value.address?.street1) { router.replace('/checkout/address'); return }
  if (!state.value.shippingMethodId || !state.value.paymentMethod) { router.replace('/checkout/payment') }
})
</script>

<template>
  <div class="bg-canvas min-h-screen">
    <CheckoutStepper :current="3" />

    <div class="container-editorial pt-8 sm:pt-10">
      <p class="editorial-label text-champagne-600 mb-2">Step 03 · A last look</p>
      <h1 class="font-serif text-4xl sm:text-5xl text-ink-950">Review & Confirm</h1>
      <p class="text-ink-500 mt-3 max-w-xl text-sm">
        Please review the details below. Once placed, our atelier begins crafting your gown.
      </p>
    </div>

    <div class="container-editorial pt-10 pb-20 grid lg:grid-cols-[1fr_400px] gap-10 lg:gap-14 items-start">
      <div class="space-y-8">
        <!-- items -->
        <section class="bg-white border border-ink-100">
          <div class="flex items-center justify-between px-6 py-4 border-b border-ink-100">
            <h2 class="font-serif text-lg">
              Your Items <span class="text-ink-400 text-sm">({{ cart.count }})</span>
            </h2>
            <RouterLink
              to="/cart"
              class="editorial-label text-ink-500 hover:text-ink-950 text-[10px]"
            >{{ t('common.edit') }}</RouterLink>
          </div>
          <ul class="divide-y divide-ink-100">
            <li v-for="it in cart.items" :key="it.key" class="flex gap-5 p-6">
              <img :src="it.image" :alt="it.name" class="w-20 h-28 object-cover bg-ink-50 shrink-0" />
              <div class="flex-1 min-w-0">
                <h3 class="font-serif text-lg leading-tight text-ink-950">{{ it.name }}</h3>
                <p class="editorial-label text-ink-400 mt-1 text-[10px]">{{ it.productionLabel }}</p>
                <p class="text-xs text-ink-600 mt-2 inline-flex flex-wrap items-center gap-x-2 gap-y-1">
                  <span class="inline-flex items-center gap-1.5">
                    <span
                      class="w-2.5 h-2.5 rounded-full ring-1 ring-ink-200"
                      :style="{ background: COLOR_MAP[it.colorId]?.hex }"
                    ></span>
                    {{ COLOR_MAP[it.colorId]?.name }}
                  </span>
                  <span class="text-ink-200" aria-hidden="true">|</span>
                  <span>Size {{ it.size }}</span>
                  <span class="text-ink-200" aria-hidden="true">|</span>
                  <span>Qty {{ it.qty }}</span>
                </p>
              </div>
              <span class="text-sm tabular-nums text-ink-950 shrink-0">{{ currency.format(it.unitPriceUSD * it.qty) }}</span>
            </li>
          </ul>
        </section>

        <!-- details grid -->
        <section class="grid sm:grid-cols-2 gap-5">
          <article class="bg-white border border-ink-100 p-6">
            <div class="flex items-center justify-between mb-3">
              <p class="editorial-label text-champagne-700 text-[10px]">{{ t('checkout.contact') }}</p>
              <RouterLink
                to="/checkout/address"
                class="editorial-label text-ink-500 hover:text-ink-950 text-[9px]"
              >{{ t('common.edit') }}</RouterLink>
            </div>
            <p class="text-sm text-ink-900">{{ state.email }}</p>
          </article>

          <article class="bg-white border border-ink-100 p-6">
            <div class="flex items-center justify-between mb-3">
              <p class="editorial-label text-champagne-700 text-[10px]">{{ t('checkout.shippingAddress') }}</p>
              <RouterLink
                to="/checkout/address"
                class="editorial-label text-ink-500 hover:text-ink-950 text-[9px]"
              >{{ t('common.edit') }}</RouterLink>
            </div>
            <p class="text-sm text-ink-900">{{ state.address?.firstName }} {{ state.address?.lastName }}</p>
            <p class="text-xs text-ink-500 mt-1">
              {{ state.address?.street1 }}<template v-if="state.address?.street2">, {{ state.address.street2 }}</template>
            </p>
            <p class="text-xs text-ink-500">
              {{ state.address?.city }}, {{ state.address?.state }} {{ state.address?.postalCode }}
            </p>
            <p class="text-xs text-ink-500">{{ state.address?.country }} · {{ state.address?.phone }}</p>
          </article>

          <article class="bg-white border border-ink-100 p-6">
            <div class="flex items-center justify-between mb-3">
              <p class="editorial-label text-champagne-700 text-[10px]">{{ t('checkout.shippingMethod') }}</p>
              <RouterLink
                to="/checkout/payment"
                class="editorial-label text-ink-500 hover:text-ink-950 text-[9px]"
              >{{ t('common.edit') }}</RouterLink>
            </div>
            <p class="text-sm text-ink-900">{{ shippingMethod.label }}</p>
            <p class="text-xs text-ink-500 mt-1">{{ shippingMethod.detail }}</p>
          </article>

          <article class="bg-white border border-ink-100 p-6">
            <div class="flex items-center justify-between mb-3">
              <p class="editorial-label text-champagne-700 text-[10px]">{{ t('checkout.payment') }}</p>
              <RouterLink
                to="/checkout/payment"
                class="editorial-label text-ink-500 hover:text-ink-950 text-[9px]"
              >{{ t('common.edit') }}</RouterLink>
            </div>
            <p class="text-sm text-ink-900">{{ paymentLabel }}</p>
            <p v-if="state.paymentMethod === 'card'" class="text-xs text-ink-500 mt-1 tabular-nums">{{ cardMasked }}</p>
            <p v-else-if="state.paymentMethod === 'paypal'" class="text-xs text-ink-500 mt-1">Authorize on PayPal after submit</p>
            <p v-else class="text-xs text-ink-500 mt-1">Authorize with Face ID / Touch ID</p>
          </article>
        </section>

        <!-- agree + place -->
        <section class="bg-white border border-ink-100 p-6 sm:p-8 space-y-5">
          <label class="flex items-start gap-3 text-sm text-ink-700 cursor-pointer">
            <input v-model="agree" type="checkbox" class="mt-1 accent-ink-950" />
            <span>
              {{ t('checkout.agreeTerms') }} — custom-size gowns are final sale per our
              <RouterLink to="/shipping-returns" class="link-underline text-ink-950">Return Policy</RouterLink>.
            </span>
          </label>
          <button
            type="button"
            class="btn-champagne w-full !py-4"
            :disabled="!agree || placing"
            @click="placeOrder"
          >
            <svg v-if="placing" class="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2" stroke-dasharray="40" stroke-linecap="round" />
            </svg>
            <template v-else>{{ t('checkout.placeOrder') }}</template>
          </button>
          <div class="flex items-center justify-between pt-2">
            <RouterLink
              to="/checkout/payment"
              class="editorial-label text-ink-500 hover:text-ink-950 inline-flex items-center gap-2 text-[10px] transition-colors"
            >
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" d="M15 19l-7-7 7-7" />
              </svg>
              Return to Payment
            </RouterLink>
            <p class="text-[10px] text-ink-400 uppercase tracking-label inline-flex items-center gap-2">
              <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <rect x="3" y="11" width="18" height="10" rx="1" />
                <path stroke-linecap="round" d="M7 11V7a5 5 0 0110 0v4" />
              </svg>
              Secure checkout
            </p>
          </div>
        </section>
      </div>

      <aside class="lg:sticky lg:top-28">
        <CheckoutSummary :shipping-method="shippingMethod" />
      </aside>
    </div>
  </div>
</template>
