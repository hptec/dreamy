<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ALL_PRODUCTS, getProductById, COLOR_MAP, PRODUCTION_TIMES } from '../data/catalog'
import { useWishlistStore } from '../stores/wishlist'
import { useCartStore } from '../stores/cart'
import { useCurrencyStore } from '../stores/currency'
import { useUiStore } from '../stores/ui'
import AccountLayout from '../components/AccountLayout.vue'
import ProductCard from '../components/ProductCard.vue'

const { t } = useI18n()
const wishlist = useWishlistStore()
const cart = useCartStore()
const currency = useCurrencyStore()
const ui = useUiStore()

const items = computed(() =>
  wishlist.ids.map(getProductById).filter((p) => !!p),
)

const recommendations = computed(() =>
  ALL_PRODUCTS.filter((p) => p.tags.includes('best-seller') && !wishlist.ids.includes(p.id)).slice(0, 4),
)

function moveToBag(p) {
  cart.add({
    productId: p.id,
    slug: p.slug,
    name: p.name,
    image: p.gallery[0],
    colorId: p.colorIds?.[0],
    colorName: COLOR_MAP[p.colorIds?.[0]]?.name,
    size: 'US 6',
    productionTimeId: 'standard',
    productionLabel: `${t('product.standard')} · ${PRODUCTION_TIMES[0].detail}`,
    unitPriceUSD: p.basePrice,
    qty: 1,
  })
  wishlist.remove(p.id)
  ui.pushToast(t('toast.addedToBag'))
}

function removeFromWishlist(id) {
  wishlist.remove(id)
  ui.pushToast(t('toast.removedFromWishlist'), 'muted')
}
</script>

<template>
  <AccountLayout>
    <div class="flex items-end justify-between mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.wishlist') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">{{ t('account.savedItems') }}</h2>
        <p v-if="items.length" class="text-sm text-ink-500 mt-2">{{ items.length }} {{ items.length === 1 ? t('cart.item') : t('cart.items') }} saved for later</p>
      </div>
      <RouterLink v-if="items.length" to="/wedding-dresses" class="editorial-label link-underline hidden sm:inline-block">{{ t('common.viewAll') }}</RouterLink>
    </div>

    <!-- Populated grid -->
    <div v-if="items.length" class="grid grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-12 lg:gap-x-6">
      <div v-for="p in items" :key="p.id" class="relative">
        <ProductCard :product="p" show-rating />
        <div class="mt-3 flex gap-2">
          <button type="button" class="btn-ink btn-sm flex-1" @click="moveToBag(p)">{{ t('common.addToBag') }}</button>
          <button type="button" class="btn-outline btn-sm !px-3" :aria-label="t('common.remove')" @click="removeFromWishlist(p.id)">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6"/></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else class="bg-white border border-ink-200 px-10 py-16 lg:py-20 text-center">
      <div class="w-16 h-16 mx-auto mb-6 grid place-items-center border border-champagne-300 text-champagne-600">
        <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
        </svg>
      </div>
      <h3 class="font-serif text-3xl mb-3">{{ t('status.emptyWishlist') }}</h3>
      <p class="text-sm text-ink-500 max-w-md mx-auto mb-8 leading-relaxed">{{ t('status.emptyWishlistDesc') }}</p>
      <RouterLink to="/wedding-dresses" class="btn-ink">{{ t('common.shopNow') }}</RouterLink>
    </div>

    <!-- Recommendations -->
    <section v-if="recommendations.length" class="mt-20">
      <div class="flex items-end justify-between mb-8">
        <div>
          <p class="editorial-label text-champagne-600 mb-3">For you</p>
          <h3 class="font-serif text-2xl sm:text-3xl">You might love</h3>
        </div>
        <RouterLink to="/wedding-dresses" class="editorial-label link-underline hidden sm:inline-block text-[11px]">{{ t('common.viewAll') }}</RouterLink>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
        <ProductCard v-for="p in recommendations" :key="p.id" :product="p" />
      </div>
    </section>
  </AccountLayout>
</template>
