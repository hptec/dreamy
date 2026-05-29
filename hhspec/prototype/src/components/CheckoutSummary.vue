<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCartStore } from '../stores/cart'
import { useCurrencyStore } from '../stores/currency'
import { COLOR_MAP } from '../data/catalog'

const props = defineProps({
  shippingMethod: { type: Object, default: null },
  title: { type: String, default: '' },
})

const { t } = useI18n()
const cart = useCartStore()
const currency = useCurrencyStore()

const shippingFee = computed(() => {
  if (cart.subtotal >= 500) return 0
  return props.shippingMethod?.feeUSD ?? 0
})
const tax = computed(() => Math.max(0, (cart.subtotal - cart.discountUSD) * 0.08))
const total = computed(() => Math.max(0, cart.subtotal - cart.discountUSD + shippingFee.value + tax.value))
const isFree = computed(() => shippingFee.value === 0)
</script>

<template>
  <aside class="bg-white border border-ink-100">
    <header class="flex items-baseline justify-between px-6 lg:px-7 pt-6 pb-5">
      <h2 class="font-serif text-xl text-ink-950">{{ title || t('checkout.orderSummary') }}</h2>
      <span class="editorial-label text-ink-400 text-[10px]">
        {{ cart.count }} {{ cart.count === 1 ? t('cart.item') : t('cart.items') }}
      </span>
    </header>

    <ul
      v-if="!cart.isEmpty"
      class="px-6 lg:px-7 space-y-5 max-h-[260px] overflow-y-auto border-t border-ink-100 pt-5"
    >
      <li v-for="it in cart.items" :key="it.key" class="flex gap-4">
        <div class="relative shrink-0">
          <img :src="it.image" :alt="it.name" class="w-14 h-[72px] object-cover bg-ink-50" />
          <span
            class="absolute -top-2 -right-2 min-w-[20px] h-5 grid place-items-center bg-ink-950 text-white text-[10px] tabular-nums px-1 rounded-full ring-2 ring-white"
          >{{ it.qty }}</span>
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-serif text-[15px] leading-tight text-ink-950 truncate">{{ it.name }}</p>
          <p class="text-[11px] text-ink-500 mt-1 inline-flex items-center gap-1.5">
            <span
              class="w-2 h-2 rounded-full ring-1 ring-ink-200"
              :style="{ background: COLOR_MAP[it.colorId]?.hex }"
            ></span>
            <span class="truncate">{{ COLOR_MAP[it.colorId]?.name }}</span>
            <span>·</span>
            <span>{{ it.size }}</span>
          </p>
        </div>
        <span class="text-sm tabular-nums text-ink-950 shrink-0">
          {{ currency.format(it.unitPriceUSD * it.qty) }}
        </span>
      </li>
    </ul>

    <div class="px-6 lg:px-7 mt-6">
      <div class="hairline mb-5"></div>
      <dl class="space-y-3 text-sm">
        <div class="flex justify-between">
          <dt class="text-ink-500">{{ t('cart.subtotal') }}</dt>
          <dd class="tabular-nums text-ink-900">{{ currency.format(cart.subtotal) }}</dd>
        </div>
        <div v-if="cart.promo" class="flex justify-between text-champagne-700">
          <dt class="inline-flex items-center gap-2">
            {{ t('cart.discount') }}
            <span class="editorial-label text-[9px] bg-champagne-50 text-champagne-800 px-1.5 py-0.5">{{ cart.promo.code }}</span>
          </dt>
          <dd class="tabular-nums">−{{ currency.format(cart.discountUSD) }}</dd>
        </div>
        <div class="flex justify-between">
          <dt class="text-ink-500">{{ t('cart.estimatedShipping') }}</dt>
          <dd
            class="tabular-nums"
            :class="isFree ? 'text-champagne-700 editorial-label text-[10px]' : 'text-ink-900'"
          >{{ isFree ? t('cart.freeShipping') : currency.format(shippingFee) }}</dd>
        </div>
        <div class="flex justify-between">
          <dt class="text-ink-500">{{ t('cart.tax') }}</dt>
          <dd class="tabular-nums text-ink-900">{{ currency.format(tax) }}</dd>
        </div>
      </dl>
    </div>

    <div class="px-6 lg:px-7 mt-6 pb-6 lg:pb-7">
      <div class="hairline mb-5"></div>
      <div class="flex justify-between items-baseline">
        <span class="editorial-label text-ink-950">{{ t('cart.total') }}</span>
        <span class="font-serif text-3xl tabular-nums text-ink-950">{{ currency.format(total) }}</span>
      </div>
      <p class="text-[10px] text-ink-400 mt-2 uppercase tracking-label">
        {{ currency.code }} · Tax estimated · Final at confirmation
      </p>
    </div>
  </aside>
</template>
