<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCurrencyStore } from '../stores/currency'
import { useWishlistStore } from '../stores/wishlist'
import { useUiStore } from '../stores/ui'
import ColorSwatches from './ColorSwatches.vue'

const props = defineProps({
  product: { type: Object, required: true },
  showRating: { type: Boolean, default: false },
})

const router = useRouter()
const currency = useCurrencyStore()
const wishlist = useWishlistStore()
const ui = useUiStore()

const hovered = ref(false)
const activeColor = ref(props.product.colorIds?.[0] || '')

const displayImg = computed(() => (hovered.value ? props.product.hoverImg : props.product.thumb))
const saved = computed(() => wishlist.has(props.product.id))

const tag = computed(() => {
  const t = props.product.tags || []
  if (t.includes('new')) return { key: 'new', label: 'New' }
  if (t.includes('best-seller')) return { key: 'best', label: 'Best Seller' }
  return null
})

function go() {
  router.push(`/products/${props.product.slug}`)
}
function toggleWish() {
  const added = wishlist.toggle(props.product.id)
  ui.pushToast(added ? 'Saved to wishlist' : 'Removed from wishlist', added ? 'success' : 'muted')
}
function quickView() {
  ui.openQuickView(props.product)
}
</script>

<template>
  <div class="group relative">
    <!-- image -->
    <div
      class="relative overflow-hidden bg-ink-50 aspect-[3/4] cursor-pointer"
      @mouseenter="hovered = true"
      @mouseleave="hovered = false"
      @click="go"
    >
      <img
        :src="displayImg"
        :alt="product.fullName"
        loading="lazy"
        class="absolute inset-0 w-full h-full object-cover transition-all duration-700 ease-editorial group-hover:scale-[1.04]"
      />

      <!-- tags -->
      <div class="absolute top-3 left-3 flex flex-col gap-1.5">
        <span v-if="tag" class="bg-ink-950 text-white editorial-label-light px-2.5 py-1 text-[10px]">{{ tag.label }}</span>
        <span v-if="product.shipsNow" class="bg-white text-ink-900 editorial-label px-2.5 py-1 text-[10px] text-ink-700">Ships Now</span>
      </div>

      <!-- wishlist -->
      <button
        type="button"
        @click.stop="toggleWish"
        :aria-label="saved ? 'Remove from wishlist' : 'Add to wishlist'"
        class="absolute top-3 right-3 w-9 h-9 grid place-items-center bg-white/85 backdrop-blur-sm transition-all duration-200 hover:bg-white"
      >
        <svg class="w-4 h-4" :class="saved ? 'fill-champagne-500 stroke-champagne-500' : 'fill-none stroke-ink-700'" viewBox="0 0 24 24" stroke-width="1.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
        </svg>
      </button>

      <!-- quick view -->
      <button
        type="button"
        @click.stop="quickView"
        class="absolute bottom-0 inset-x-0 bg-ink-950/92 text-white py-3 editorial-label-light text-[11px] translate-y-full group-hover:translate-y-0 transition-transform duration-300 ease-editorial"
      >
        Quick View
      </button>
    </div>

    <!-- meta -->
    <div class="pt-4 space-y-2">
      <div class="flex items-start justify-between gap-3">
        <h3 class="font-serif text-lg leading-tight text-ink-950 cursor-pointer hover:text-champagne-700 transition-colors" @click="go">
          {{ product.name }}
        </h3>
      </div>
      <p class="editorial-label text-ink-400 text-[10px]">{{ product.silhouette }} · {{ product.fabric }}</p>
      <StarRating v-if="showRating" :rating="product.rating" :count="product.reviewCount" />
      <div class="flex items-baseline gap-2">
        <span class="text-sm text-ink-900 tabular-nums">{{ currency.format(product.basePrice) }}</span>
        <span v-if="product.originalPrice" class="text-xs text-ink-400 line-through tabular-nums">{{ currency.format(product.originalPrice) }}</span>
      </div>
      <ColorSwatches v-if="product.colorIds?.length" v-model="activeColor" :color-ids="product.colorIds" :max="6" />
    </div>
  </div>
</template>
