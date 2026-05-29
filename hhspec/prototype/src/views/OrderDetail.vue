<script setup>
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SAMPLE_ORDERS, SAMPLE_ADDRESSES } from '../data/catalog'
import { useCurrencyStore } from '../stores/currency'
import { useUiStore } from '../stores/ui'
import AccountLayout from '../components/AccountLayout.vue'

const route = useRoute()
const { t } = useI18n()
const currency = useCurrencyStore()
const ui = useUiStore()

const order = computed(() => SAMPLE_ORDERS.find((o) => o.id === route.params.id) || SAMPLE_ORDERS[0])

const STEPS = [
  { id: 'processing', label: 'Ordered', icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z' },
  { id: 'inProduction', label: 'In Production', icon: 'M3 6h18M6 6v14h12V6M9 10h6M9 14h6' },
  { id: 'shipped', label: 'Shipped', icon: 'M3 7h13l4 5h-2m-9 5a2 2 0 11-4 0 2 2 0 014 0zm10 0a2 2 0 11-4 0 2 2 0 014 0z' },
  { id: 'delivered', label: 'Delivered', icon: 'M5 13l4 4L19 7' },
]
const STEP_INDEX = { processing: 0, inProduction: 1, shipped: 2, delivered: 3, cancelled: 1 }
const currentStep = computed(() => STEP_INDEX[order.value.status] ?? 0)

const itemsSubtotal = computed(() => order.value.items.reduce((n, i) => n + i.price * i.qty, 0))
const shippingFee = computed(() => (itemsSubtotal.value >= 500 ? 0 : 25))
const taxFee = computed(() => Math.round(itemsSubtotal.value * 0.0875))
const grandTotal = computed(() => itemsSubtotal.value + shippingFee.value + taxFee.value)

const shippingAddress = SAMPLE_ADDRESSES[0]

const statusKeys = {
  processing: 'account.status.processing',
  inProduction: 'account.status.inProduction',
  shipped: 'account.status.shipped',
  delivered: 'account.status.delivered',
  cancelled: 'account.status.cancelled',
}
function statusLabel(s) { return t(statusKeys[s] || 'account.status.processing') }
function statusTone(s) {
  if (s === 'shipped') return 'bg-ink-950 text-white'
  if (s === 'delivered') return 'bg-champagne-100 text-champagne-800 border border-champagne-300'
  if (s === 'cancelled') return 'bg-ink-100 text-ink-500'
  return 'bg-white text-ink-900 border border-ink-300'
}

function reorder() { ui.pushToast(t('toast.addedToBag')) }
function support() { ui.pushToast(t('toast.copied'), 'muted') }
</script>

<template>
  <AccountLayout>
    <!-- Breadcrumb -->
    <nav class="flex items-center gap-2 editorial-label text-ink-400 text-[10px] mb-6">
      <RouterLink to="/account" class="hover:text-ink-900">{{ t('account.dashboard') }}</RouterLink><span>/</span>
      <RouterLink to="/account/orders" class="hover:text-ink-900">{{ t('account.orders') }}</RouterLink><span>/</span>
      <span class="text-ink-700">{{ order.id }}</span>
    </nav>

    <!-- Heading -->
    <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.orderHistory') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">Order {{ order.id }}</h2>
        <p class="text-sm text-ink-500 mt-2">{{ t('checkout.orderNumber') }} placed on {{ order.date }}</p>
      </div>
      <span class="editorial-label text-[10px] px-4 py-2 inline-block self-start sm:self-auto" :class="statusTone(order.status)">{{ statusLabel(order.status) }}</span>
    </div>

    <!-- Timeline -->
    <section class="bg-white border border-ink-200 p-8 lg:p-10 mb-8">
      <div class="flex items-center justify-between mb-8">
        <h3 class="font-serif text-xl">Production timeline</h3>
        <p v-if="order.status !== 'delivered' && order.status !== 'cancelled'" class="editorial-label text-champagne-700 text-[10px]">
          ETA · {{ order.estimatedDelivery }}
        </p>
      </div>
      <ol class="grid grid-cols-4 gap-2 relative">
        <li v-for="(step, i) in STEPS" :key="step.id" class="relative">
          <!-- connector -->
          <div
            v-if="i > 0"
            class="absolute top-5 right-1/2 w-full h-px"
            :class="i <= currentStep ? 'bg-ink-950' : 'bg-ink-200'"
          ></div>
          <!-- node -->
          <div class="relative grid place-items-center">
            <div
              class="relative z-10 w-10 h-10 grid place-items-center border-2 transition-colors"
              :class="i <= currentStep ? 'bg-ink-950 border-ink-950 text-white' : 'bg-white border-ink-300 text-ink-300'"
            >
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" :d="step.icon" />
              </svg>
            </div>
          </div>
          <p class="text-center mt-3 text-xs sm:text-sm font-medium" :class="i <= currentStep ? 'text-ink-950' : 'text-ink-400'">{{ step.label }}</p>
          <p v-if="i === currentStep" class="text-center editorial-label text-champagne-700 text-[9px] mt-1">Current</p>
        </li>
      </ol>
    </section>

    <!-- Items + sidebar -->
    <div class="grid lg:grid-cols-[1fr_360px] gap-8">
      <!-- Items -->
      <section class="bg-white border border-ink-200">
        <div class="px-8 py-5 border-b border-ink-100 flex items-center justify-between">
          <h3 class="font-serif text-xl">Items</h3>
          <p class="editorial-label text-ink-400 text-[10px]">{{ order.items.length }} {{ order.items.length === 1 ? t('cart.item') : t('cart.items') }}</p>
        </div>
        <ul class="divide-y divide-ink-100">
          <li v-for="(it, i) in order.items" :key="i" class="px-8 py-6 grid grid-cols-[96px_1fr_auto] gap-5 items-center">
            <div class="relative aspect-[3/4] bg-ink-50 overflow-hidden">
              <img :src="it.img" :alt="it.name" class="absolute inset-0 w-full h-full object-cover" />
            </div>
            <div class="min-w-0">
              <p class="font-serif text-lg text-ink-950 truncate">{{ it.name }}</p>
              <p class="text-xs text-ink-500 mt-1.5">{{ it.color }} · {{ it.size }}</p>
              <p class="text-xs text-ink-400 mt-1">{{ t('cart.quantity') }}: {{ it.qty }}</p>
            </div>
            <p class="text-sm text-ink-950 tabular-nums">{{ currency.format(it.price * it.qty) }}</p>
          </li>
        </ul>
        <!-- CTAs -->
        <div class="flex flex-wrap gap-3 px-8 py-6 border-t border-ink-100 bg-ink-50/40">
          <button type="button" class="btn-ink btn-sm" @click="reorder">Reorder</button>
          <button type="button" class="btn-outline btn-sm" @click="support">Contact support</button>
          <RouterLink to="/account/orders" class="btn-outline btn-sm">{{ t('common.back') }} to orders</RouterLink>
        </div>
      </section>

      <!-- Sidebar -->
      <aside class="space-y-6">
        <!-- Shipping -->
        <section class="bg-white border border-ink-200 p-6">
          <div class="flex items-start justify-between mb-3">
            <p class="editorial-label text-ink-400">{{ t('checkout.shippingAddress') }}</p>
            <span class="editorial-label text-champagne-700 text-[9px]">{{ t('account.default') }}</span>
          </div>
          <p class="font-serif text-lg text-ink-950">{{ shippingAddress.firstName }} {{ shippingAddress.lastName }}</p>
          <p class="text-sm text-ink-600 mt-1 leading-relaxed">
            {{ shippingAddress.street1 }}<br />
            <span v-if="shippingAddress.street2">{{ shippingAddress.street2 }}<br /></span>
            {{ shippingAddress.city }}, {{ shippingAddress.state }} {{ shippingAddress.postalCode }}<br />
            {{ shippingAddress.country }}
          </p>
          <p class="text-xs text-ink-400 mt-3">{{ shippingAddress.phone }}</p>
        </section>

        <!-- Payment -->
        <section class="bg-white border border-ink-200 p-6">
          <p class="editorial-label text-ink-400 mb-3">{{ t('checkout.payment') }}</p>
          <div class="flex items-center gap-3">
            <div class="w-12 h-8 bg-ink-950 text-white text-[10px] font-medium tracking-wider grid place-items-center">VISA</div>
            <p class="text-sm text-ink-950">Visa ····4242</p>
          </div>
          <p class="text-xs text-ink-400 mt-3">Expires 09 / 28</p>
        </section>

        <!-- Totals -->
        <section class="bg-white border border-ink-200 p-6">
          <p class="editorial-label text-ink-400 mb-4">{{ t('checkout.orderSummary') }}</p>
          <dl class="space-y-2 text-sm">
            <div class="flex justify-between text-ink-600">
              <dt>{{ t('cart.subtotal') }}</dt>
              <dd class="tabular-nums">{{ currency.format(itemsSubtotal) }}</dd>
            </div>
            <div class="flex justify-between text-ink-600">
              <dt>{{ t('cart.shipping') }}</dt>
              <dd class="tabular-nums">{{ shippingFee ? currency.format(shippingFee) : t('cart.freeShipping') }}</dd>
            </div>
            <div class="flex justify-between text-ink-600">
              <dt>{{ t('cart.tax') }}</dt>
              <dd class="tabular-nums">{{ currency.format(taxFee) }}</dd>
            </div>
          </dl>
          <div class="hairline my-4"></div>
          <div class="flex justify-between text-ink-950">
            <span class="font-serif text-lg">{{ t('cart.total') }}</span>
            <span class="text-lg tabular-nums">{{ currency.format(grandTotal) }}</span>
          </div>
        </section>
      </aside>
    </div>
  </AccountLayout>
</template>
