<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SAMPLE_ORDERS, SAMPLE_ADDRESSES } from '../data/catalog'
import { useAuthStore } from '../stores/auth'
import { useWishlistStore } from '../stores/wishlist'
import { useCurrencyStore } from '../stores/currency'
import AccountLayout from '../components/AccountLayout.vue'

const { t } = useI18n()
const auth = useAuthStore()
const wishlist = useWishlistStore()
const currency = useCurrencyStore()

const orders = SAMPLE_ORDERS
const recentOrders = computed(() => orders.slice(0, 2))
const lastOrder = computed(() => orders[0])

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
  if (s === 'delivered') return 'bg-champagne-100 text-champagne-800'
  if (s === 'cancelled') return 'bg-ink-100 text-ink-500'
  return 'bg-white text-ink-900 border border-ink-300'
}

const cards = computed(() => [
  {
    label: t('account.recentOrders'),
    value: orders.length,
    detail: lastOrder.value ? `#${lastOrder.value.id}` : '—',
    to: '/account/orders',
    cta: t('common.viewAll'),
  },
  {
    label: t('account.savedItems'),
    value: wishlist.count,
    detail: wishlist.count ? 'Pieces saved to revisit' : 'Begin a wishlist of gowns',
    to: '/account/wishlist',
    cta: t('account.wishlist'),
  },
  {
    label: t('account.savedAddresses'),
    value: SAMPLE_ADDRESSES.length,
    detail: SAMPLE_ADDRESSES[0] ? `${SAMPLE_ADDRESSES[0].city}, ${SAMPLE_ADDRESSES[0].state}` : '—',
    to: '/account/addresses',
    cta: t('account.addresses'),
  },
  {
    label: 'Atelier Credit',
    value: currency.format(50),
    detail: 'Available after your first order',
    to: '/account/profile',
    cta: 'View terms',
    accent: true,
  },
])
</script>

<template>
  <AccountLayout>
    <!-- Section header -->
    <div class="flex items-end justify-between mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.dashboard') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">{{ t('account.recentOrders') }}, fittings & favourites.</h2>
      </div>
      <RouterLink to="/account/profile" class="editorial-label link-underline hidden sm:inline-block">{{ t('account.profile') }}</RouterLink>
    </div>

    <!-- Overview cards -->
    <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-5">
      <RouterLink
        v-for="c in cards"
        :key="c.label"
        :to="c.to"
        class="group bg-white border border-ink-200 p-6 lg:p-7 flex flex-col justify-between min-h-[180px] hover:border-ink-950 transition-colors"
        :class="c.accent ? 'border-champagne-300 bg-champagne-50/60' : ''"
      >
        <div>
          <p class="editorial-label" :class="c.accent ? 'text-champagne-700' : 'text-ink-400'">{{ c.label }}</p>
          <p class="font-serif text-4xl mt-3 text-ink-950 tabular-nums">{{ c.value }}</p>
          <p class="text-xs text-ink-500 mt-2 leading-relaxed">{{ c.detail }}</p>
        </div>
        <span class="editorial-label text-ink-500 group-hover:text-ink-950 text-[10px] mt-6 inline-flex items-center gap-1.5">
          {{ c.cta }}
          <svg class="w-3 h-3 transition-transform group-hover:translate-x-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M5 12h14m0 0l-6-6m6 6l-6 6"/></svg>
        </span>
      </RouterLink>
    </div>

    <!-- Recent orders -->
    <section class="mt-16">
      <div class="flex items-end justify-between mb-6">
        <h3 class="font-serif text-2xl">{{ t('account.recentOrders') }}</h3>
        <RouterLink to="/account/orders" class="editorial-label link-underline text-[11px]">{{ t('common.viewAll') }}</RouterLink>
      </div>

      <div class="space-y-3">
        <RouterLink
          v-for="o in recentOrders"
          :key="o.id"
          :to="`/account/orders/${o.id}`"
          class="group bg-white border border-ink-200 hover:border-ink-950 transition-colors grid grid-cols-[80px_1fr_auto] sm:grid-cols-[96px_1fr_auto_auto] items-center gap-5 p-4 sm:p-5"
        >
          <div class="relative aspect-[3/4] bg-ink-50 overflow-hidden">
            <img :src="o.items[0].img" :alt="o.items[0].name" class="absolute inset-0 w-full h-full object-cover" />
          </div>
          <div class="min-w-0">
            <p class="editorial-label text-ink-400 text-[10px]">{{ o.id }} · {{ o.date }}</p>
            <p class="font-serif text-lg text-ink-950 truncate mt-1">{{ o.items[0].name }}</p>
            <p class="text-xs text-ink-500 mt-1">{{ o.items.length }} {{ o.items.length === 1 ? t('cart.item') : t('cart.items') }} · {{ o.items[0].color }}</p>
          </div>
          <span class="editorial-label text-[10px] px-3 py-1.5 hidden sm:inline-block" :class="statusTone(o.status)">{{ statusLabel(o.status) }}</span>
          <div class="text-right">
            <p class="text-sm text-ink-950 tabular-nums">{{ currency.format(o.total) }}</p>
            <span class="editorial-label text-ink-400 group-hover:text-ink-950 text-[10px] inline-flex items-center gap-1.5 mt-1">
              View
              <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M9 5l7 7-7 7"/></svg>
            </span>
          </div>
        </RouterLink>
      </div>
    </section>

    <!-- Quick links -->
    <section class="mt-16 bg-ink-950 text-white p-10 lg:p-12">
      <div class="grid sm:grid-cols-[1fr_auto] gap-6 items-center">
        <div>
          <p class="editorial-label-light text-champagne-300 mb-3">Atelier services</p>
          <h3 class="font-serif text-3xl text-white leading-tight">Need a hand from the atelier?</h3>
          <p class="text-ink-300 text-sm mt-3 max-w-md font-light leading-relaxed">
            Book a virtual fitting, request fabric swatches or speak with a private stylist.
          </p>
        </div>
        <div class="flex flex-wrap gap-3">
          <RouterLink to="/atelier" class="btn-ghost btn-sm">Book a fitting</RouterLink>
          <RouterLink to="/contact" class="btn-ghost btn-sm">Contact stylist</RouterLink>
        </div>
      </div>
    </section>
  </AccountLayout>
</template>
