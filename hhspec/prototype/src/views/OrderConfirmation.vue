<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useCurrencyStore } from '../stores/currency'

const { t } = useI18n()
const currency = useCurrencyStore()

const order = ref(null)
const ready = ref(false)

const fallback = {
  number: 'ME-2026-04999',
  email: 'you@maisoneden.com',
  subtotal: 920,
  discount: 0,
  shippingFee: 0,
  tax: 73.6,
  total: 993.6,
  shippingLabel: 'Standard Atelier',
  shippingDetail: 'Made to order · 3–4 weeks',
  paymentLabel: 'Credit Card',
  paymentMethod: 'card',
  cardMasked: '•••• •••• •••• 4242',
  estimatedDeliveryLabel: 'Jun 25, 2026',
  itemCount: 1,
  items: [],
  address: null,
}

onMounted(() => {
  try {
    const raw = localStorage.getItem('me_last_order')
    order.value = raw ? JSON.parse(raw) : fallback
  } catch (e) {
    order.value = fallback
  }
  ready.value = true
})

const steps = [
  { title: 'Confirmation Email', desc: 'A receipt is on its way to your inbox within minutes.' },
  { title: 'Atelier Production', desc: 'Our seamstresses begin cutting and hand-finishing your gown.' },
  { title: 'Quality Inspection', desc: 'Each gown is steamed, pressed, and inspected before dispatch.' },
  { title: 'Worldwide Shipping', desc: 'Insured delivery, tracked door-to-door.' },
]
</script>

<template>
  <div v-if="ready && order" class="bg-canvas min-h-screen">
    <!-- hero -->
    <section class="bg-ink-950 text-white relative overflow-hidden">
      <div class="container-editorial py-20 sm:py-28 text-center">
        <div class="inline-flex items-center justify-center w-14 h-14 border border-champagne-500 mb-7">
          <svg class="w-6 h-6 text-champagne-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 12l5 5 9-11" />
          </svg>
        </div>
        <p class="editorial-label-light text-champagne-300 mb-4">{{ t('checkout.successTitle') }}</p>
        <h1 class="font-serif text-white text-5xl sm:text-7xl leading-[0.95] text-balance max-w-3xl mx-auto">
          Your gown is on its way to being made.
        </h1>
        <p class="text-ink-100/80 mt-6 max-w-xl mx-auto text-base leading-relaxed">
          {{ t('checkout.successDesc') }} A receipt has been sent to
          <span class="text-white">{{ order.email }}</span>.
        </p>

        <div class="mt-12 mx-auto inline-grid sm:grid-cols-3 gap-px bg-white/15 max-w-3xl w-full">
          <div class="bg-ink-950 px-6 py-6 text-left">
            <p class="editorial-label-light text-white/50 mb-1.5 text-[9px]">{{ t('checkout.orderNumber') }}</p>
            <p class="font-serif text-xl tabular-nums">{{ order.number }}</p>
          </div>
          <div class="bg-ink-950 px-6 py-6 text-left">
            <p class="editorial-label-light text-white/50 mb-1.5 text-[9px]">{{ t('cart.total') }}</p>
            <p class="font-serif text-xl tabular-nums">{{ currency.format(order.total) }}</p>
          </div>
          <div class="bg-ink-950 px-6 py-6 text-left">
            <p class="editorial-label-light text-white/50 mb-1.5 text-[9px]">{{ t('checkout.estimatedDelivery') }}</p>
            <p class="font-serif text-xl">{{ order.estimatedDeliveryLabel }}</p>
          </div>
        </div>

        <div class="mt-12 flex flex-wrap justify-center gap-4">
          <RouterLink to="/account/orders" class="btn-champagne">{{ t('checkout.trackOrder') }}</RouterLink>
          <RouterLink to="/" class="btn-ghost">{{ t('cart.continueShopping') }}</RouterLink>
        </div>
      </div>
    </section>

    <!-- what's next -->
    <section class="container-editorial section-pad">
      <div class="text-center mb-14 sm:mb-16">
        <p class="editorial-label text-champagne-600 mb-3">What Happens Next</p>
        <h2 class="font-serif text-3xl sm:text-5xl">From Atelier to your Door</h2>
      </div>
      <ol class="grid sm:grid-cols-2 lg:grid-cols-4 gap-10 lg:gap-8">
        <li v-for="(n, i) in steps" :key="i" class="relative">
          <p class="font-serif text-6xl text-champagne-500 tabular-nums leading-none">{{ String(i + 1).padStart(2, '0') }}</p>
          <h3 class="font-serif text-xl mt-4 text-ink-950">{{ n.title }}</h3>
          <p class="text-ink-500 text-sm mt-2 leading-relaxed">{{ n.desc }}</p>
        </li>
      </ol>
    </section>

    <!-- items + details -->
    <section v-if="order.items && order.items.length" class="container-editorial pb-24">
      <div class="grid lg:grid-cols-[1fr_360px] gap-10 lg:gap-14 items-start">
        <article class="bg-white border border-ink-100">
          <div class="px-6 py-4 border-b border-ink-100">
            <h3 class="font-serif text-xl">Your Order</h3>
          </div>
          <ul class="divide-y divide-ink-100">
            <li v-for="(it, i) in order.items" :key="i" class="flex gap-5 p-6">
              <img :src="it.image" :alt="it.name" class="w-20 h-28 object-cover bg-ink-50 shrink-0" />
              <div class="flex-1 min-w-0">
                <h4 class="font-serif text-lg leading-tight text-ink-950">{{ it.name }}</h4>
                <p v-if="it.productionLabel" class="editorial-label text-ink-400 mt-1 text-[10px]">{{ it.productionLabel }}</p>
                <p class="text-xs text-ink-500 mt-2">
                  {{ it.colorName }} · Size {{ it.size }} · Qty {{ it.qty }}
                </p>
              </div>
              <span class="text-sm tabular-nums text-ink-950 shrink-0">{{ currency.format(it.unitPriceUSD * it.qty) }}</span>
            </li>
          </ul>
          <!-- subtotal block -->
          <dl class="px-6 py-5 border-t border-ink-100 space-y-2.5 text-sm">
            <div class="flex justify-between text-ink-500">
              <dt>{{ t('cart.subtotal') }}</dt>
              <dd class="tabular-nums text-ink-900">{{ currency.format(order.subtotal) }}</dd>
            </div>
            <div v-if="order.discount" class="flex justify-between text-champagne-700">
              <dt>{{ t('cart.discount') }}</dt>
              <dd class="tabular-nums">−{{ currency.format(order.discount) }}</dd>
            </div>
            <div class="flex justify-between text-ink-500">
              <dt>{{ t('cart.estimatedShipping') }}</dt>
              <dd class="tabular-nums text-ink-900">
                {{ order.shippingFee === 0 ? t('cart.freeShipping') : currency.format(order.shippingFee) }}
              </dd>
            </div>
            <div class="flex justify-between text-ink-500">
              <dt>{{ t('cart.tax') }}</dt>
              <dd class="tabular-nums text-ink-900">{{ currency.format(order.tax) }}</dd>
            </div>
            <div class="flex justify-between pt-3 mt-2 border-t border-ink-100">
              <dt class="editorial-label text-ink-950">{{ t('cart.total') }}</dt>
              <dd class="font-serif text-2xl tabular-nums text-ink-950">{{ currency.format(order.total) }}</dd>
            </div>
          </dl>
        </article>

        <aside class="space-y-4">
          <article v-if="order.address" class="bg-white border border-ink-100 p-6">
            <p class="editorial-label text-champagne-700 text-[10px] mb-3">Shipping to</p>
            <p class="text-sm text-ink-900">{{ order.address.firstName }} {{ order.address.lastName }}</p>
            <p class="text-xs text-ink-500 mt-1">
              {{ order.address.street1 }}<template v-if="order.address.street2">, {{ order.address.street2 }}</template>
            </p>
            <p class="text-xs text-ink-500">
              {{ order.address.city }}, {{ order.address.state }} {{ order.address.postalCode }}
            </p>
            <p class="text-xs text-ink-500">{{ order.address.country }}</p>
          </article>
          <article class="bg-white border border-ink-100 p-6">
            <p class="editorial-label text-champagne-700 text-[10px] mb-3">Delivery</p>
            <p class="text-sm text-ink-900">{{ order.shippingLabel }}</p>
            <p v-if="order.shippingDetail" class="text-xs text-ink-500 mt-1">{{ order.shippingDetail }}</p>
            <p class="text-xs text-ink-500 mt-2 tabular-nums">Arrives by {{ order.estimatedDeliveryLabel }}</p>
          </article>
          <article class="bg-white border border-ink-100 p-6">
            <p class="editorial-label text-champagne-700 text-[10px] mb-3">Payment</p>
            <p class="text-sm text-ink-900">{{ order.paymentLabel }}</p>
            <p v-if="order.cardMasked" class="text-xs text-ink-500 mt-1 tabular-nums">{{ order.cardMasked }}</p>
          </article>
          <article class="bg-ink-950 text-white p-6">
            <p class="editorial-label-light text-champagne-300 text-[10px] mb-3">Need help?</p>
            <p class="text-sm text-white/85 leading-relaxed">
              Reach our concierge at
              <a href="mailto:concierge@maisoneden.com" class="link-underline text-white">concierge@maisoneden.com</a>
              or via the chat icon, any hour.
            </p>
          </article>
        </aside>
      </div>
    </section>
  </div>

  <div v-else class="container-editorial section-pad min-h-[60vh] grid place-items-center">
    <p class="editorial-label text-ink-400">{{ t('common.loading') }}</p>
  </div>
</template>
