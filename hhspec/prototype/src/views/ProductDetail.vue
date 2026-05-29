<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { TabGroup, TabList, Tab, TabPanels, TabPanel, Disclosure, DisclosureButton, DisclosurePanel, TransitionRoot, TransitionChild, Dialog, DialogPanel, Menu, MenuButton, MenuItems, MenuItem } from '@headlessui/vue'
import { getProductBySlug, getRelated, getOftenBoughtWith, getProductById, COLOR_MAP, STANDARD_SIZES, PRODUCTION_TIMES } from '../data/catalog'
import { useCartStore } from '../stores/cart'
import { useWishlistStore } from '../stores/wishlist'
import { useCurrencyStore } from '../stores/currency'
import { useUiStore } from '../stores/ui'
import ColorSwatches from '../components/ColorSwatches.vue'
import StarRating from '../components/StarRating.vue'
import SizeGuideModal from '../components/SizeGuideModal.vue'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const cart = useCartStore()
const wishlist = useWishlistStore()
const currency = useCurrencyStore()
const ui = useUiStore()

const product = computed(() => getProductBySlug(route.params.slug))
const activeImg = ref(0)
const color = ref('')
const size = ref('')
const length = ref('')
const production = ref('standard')
const sizeGuideOpen = ref(false)
const customSizeOpen = ref(false)
const lightboxOpen = ref(false)
const reviewSizeFilter = ref('all')
const adding = ref(false)
const custom = ref({ bust: '', waist: '', hips: '', height: '', hollow: '' })

function initProduct() {
  const p = product.value
  if (!p) return
  activeImg.value = 0
  color.value = p.colorIds?.[0] || ''
  size.value = ''
  length.value = p.lengths?.[0] || ''
  production.value = 'standard'
  reviewSizeFilter.value = 'all'
  ui.addRecentlyViewed(p.id)
  window.scrollTo({ top: 0 })
}
onMounted(initProduct)
watch(() => route.params.slug, initProduct)

const productionFee = computed(() => PRODUCTION_TIMES.find((x) => x.id === production.value)?.feeUSD || 0)
const totalUSD = computed(() => (product.value?.basePrice || 0) + productionFee.value)

const related = computed(() => (product.value ? getRelated(product.value, 6) : []))
const oftenBought = computed(() => (product.value ? getOftenBoughtWith(product.value, 4) : []))
const recent = computed(() =>
  ui.recentlyViewed.map(getProductById).filter((p) => p && p.id !== product.value?.id).slice(0, 6),
)
const saved = computed(() => (product.value ? wishlist.has(product.value.id) : false))

const ratingDist = computed(() => {
  const p = product.value
  if (!p) return []
  const total = p.reviewCount
  return [5, 4, 3, 2, 1].map((star) => {
    const pct = star === 5 ? 0.78 : star === 4 ? 0.16 : star === 3 ? 0.04 : star === 2 ? 0.01 : 0.01
    return { star, count: Math.round(total * pct), pct: Math.round(pct * 100) }
  })
})
const filteredReviews = computed(() => {
  const p = product.value
  if (!p) return []
  if (reviewSizeFilter.value === 'all') return p.reviews
  return p.reviews.filter((r) => r.sizeWorn === reviewSizeFilter.value)
})
const reviewSizes = computed(() => {
  const p = product.value
  if (!p) return []
  return [...new Set(p.reviews.map((r) => r.sizeWorn))]
})

function addToBag(buyNow = false) {
  const p = product.value
  if (!p) return
  adding.value = true
  setTimeout(() => {
    cart.add({
      productId: p.id, slug: p.slug, name: p.name, image: p.gallery[0],
      colorId: color.value, colorName: COLOR_MAP[color.value]?.name,
      size: size.value || 'US 6',
      productionTimeId: production.value,
      productionLabel: `${t('product.' + production.value)} · ${PRODUCTION_TIMES.find((x) => x.id === production.value)?.detail}`,
      unitPriceUSD: totalUSD.value, qty: 1,
    })
    adding.value = false
    if (buyNow) router.push('/checkout/address')
    else { ui.pushToast(t('toast.addedToBag')); ui.openCart() }
  }, 400)
}
function toggleWish() {
  const added = wishlist.toggle(product.value.id)
  ui.pushToast(added ? t('toast.addedToWishlist') : t('toast.removedFromWishlist'), added ? 'success' : 'muted')
}
function share() {
  ui.pushToast(t('toast.copied'))
}
function applyCustom() {
  size.value = 'Custom'
  customSizeOpen.value = false
  ui.pushToast(t('toast.saved'))
}
</script>

<template>
  <div v-if="product">
    <!-- breadcrumb -->
    <div class="container-editorial pt-6">
      <nav class="flex items-center gap-2 editorial-label text-ink-400 text-[10px]">
        <RouterLink to="/" class="hover:text-ink-900">Home</RouterLink><span>/</span>
        <RouterLink :to="`/${product.category}-dresses`" class="hover:text-ink-900 capitalize">{{ product.category }}</RouterLink><span>/</span>
        <span class="text-ink-700">{{ product.name }}</span>
      </nav>
    </div>

    <!-- main: gallery + buybox -->
    <section class="container-editorial py-8 grid lg:grid-cols-2 gap-10 lg:gap-16">
      <!-- gallery -->
      <div class="flex gap-4">
        <div class="hidden sm:flex flex-col gap-3 w-20 shrink-0">
          <button v-for="(img, i) in product.gallery" :key="i" @click="activeImg = i"
            class="relative aspect-[3/4] bg-ink-50 overflow-hidden transition-all" :class="activeImg === i ? 'ring-1 ring-ink-950' : 'opacity-60 hover:opacity-100'">
            <img :src="img" :alt="`${product.name} ${i + 1}`" class="absolute inset-0 w-full h-full object-cover" />
          </button>
        </div>
        <div class="flex-1 relative bg-ink-50 aspect-[3/4] overflow-hidden cursor-zoom-in group" @click="lightboxOpen = true">
          <img :src="product.gallery[activeImg]" :alt="product.fullName" class="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
          <div class="absolute top-4 left-4 flex flex-col gap-1.5">
            <span v-if="product.tags.includes('new')" class="bg-ink-950 text-white editorial-label-light px-2.5 py-1 text-[10px]">New</span>
            <span v-if="product.shipsNow" class="bg-white text-ink-700 editorial-label px-2.5 py-1 text-[10px]">Ships Now</span>
          </div>
          <span class="absolute bottom-4 right-4 bg-white/85 px-2.5 py-1.5 editorial-label text-[9px] text-ink-600">Click to zoom</span>
        </div>
      </div>

      <!-- buy box -->
      <div class="lg:py-2">
        <p class="editorial-label text-ink-400 mb-3">{{ product.silhouette }} · {{ product.fabric }}</p>
        <h1 class="font-serif text-4xl sm:text-5xl text-ink-950 leading-none">{{ product.name }}</h1>
        <div class="flex items-center gap-4 mt-4">
          <StarRating :rating="product.rating" size="md" />
          <a href="#reviews" class="editorial-label text-ink-500 hover:text-ink-950 text-[10px]">{{ product.reviewCount }} {{ t('product.reviews') }}</a>
        </div>

        <div class="flex items-baseline gap-3 mt-6">
          <span class="text-2xl text-ink-950 tabular-nums">{{ currency.format(totalUSD) }}</span>
          <span v-if="product.originalPrice" class="text-base text-ink-400 line-through tabular-nums">{{ currency.format(product.originalPrice) }}</span>
          <span v-if="product.originalPrice" class="editorial-label text-champagne-700 text-[10px]">Save {{ Math.round((1 - product.basePrice / product.originalPrice) * 100) }}%</span>
        </div>
        <p class="text-[11px] text-ink-400 mt-1">{{ product.shipsNow ? t('product.inStockShipsNow') : t('product.madeToOrderNote') }}</p>

        <!-- color -->
        <div class="mt-8">
          <div class="flex items-center justify-between mb-3">
            <p class="field-label !mb-0">{{ t('product.color') }}: <span class="text-ink-900 normal-case tracking-normal">{{ COLOR_MAP[color]?.name }}</span></p>
            <span class="text-[11px] text-ink-400">{{ product.colorIds.length }} {{ t('product.colorsAvailable') }}</span>
          </div>
          <ColorSwatches v-model="color" :color-ids="product.colorIds" :max="14" size="md" />
        </div>

        <!-- length -->
        <div v-if="product.lengths.length" class="mt-7">
          <p class="field-label">{{ t('product.length') }}</p>
          <div class="flex flex-wrap gap-2">
            <button v-for="l in product.lengths" :key="l" @click="length = l" class="px-4 py-2.5 text-xs border transition-colors" :class="length === l ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 hover:border-ink-950'">{{ l }}</button>
          </div>
        </div>

        <!-- size -->
        <div class="mt-7">
          <div class="flex items-center justify-between mb-3">
            <p class="field-label !mb-0">{{ t('product.size') }}</p>
            <button @click="sizeGuideOpen = true" class="editorial-label text-ink-500 hover:text-ink-950 text-[10px] inline-flex items-center gap-1">
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M3 7h18M3 12h18M3 17h18" /></svg>
              {{ t('product.sizeGuide') }}
            </button>
          </div>
          <div class="grid grid-cols-5 sm:grid-cols-7 gap-2">
            <button v-for="s in STANDARD_SIZES.slice(0, 12)" :key="s" @click="size = s" class="py-2.5 text-xs border transition-colors" :class="size === s ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 hover:border-ink-950'">{{ s.replace('US ', '') }}</button>
          </div>
          <button v-if="product.isCustomizable" @click="customSizeOpen = true" class="mt-3 w-full border border-dashed border-ink-300 hover:border-champagne-500 px-4 py-3 text-left transition-colors group">
            <span class="flex items-center justify-between">
              <span>
                <span class="text-sm text-ink-900 inline-flex items-center gap-2">
                  <svg class="w-4 h-4 text-champagne-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M3 6h18v4H3zM7 10v8M17 10v8" /></svg>
                  {{ t('product.customSize') }}
                  <span v-if="size === 'Custom'" class="editorial-label text-champagne-700 text-[9px]">✓ Selected</span>
                </span>
                <span class="block text-[11px] text-ink-400 mt-0.5 ml-6">{{ t('product.customSizeDesc') }}</span>
              </span>
              <svg class="w-4 h-4 text-ink-300 group-hover:text-champagne-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M9 5l7 7-7 7" /></svg>
            </span>
          </button>
        </div>

        <!-- production time -->
        <div class="mt-7">
          <p class="field-label">{{ t('product.productionTime') }}</p>
          <div class="space-y-2">
            <button v-for="pt in PRODUCTION_TIMES" :key="pt.id" @click="production = pt.id" class="w-full flex items-center justify-between px-4 py-3 border transition-colors text-left" :class="production === pt.id ? 'border-ink-950' : 'border-ink-200 hover:border-ink-400'">
              <span class="flex items-center gap-3">
                <span class="w-4 h-4 rounded-full border flex items-center justify-center" :class="production === pt.id ? 'border-ink-950' : 'border-ink-300'"><span v-if="production === pt.id" class="w-2 h-2 rounded-full bg-ink-950"></span></span>
                <span><span class="text-sm text-ink-900">{{ t('product.' + pt.id) }}</span> <span class="text-xs text-ink-400">· {{ pt.detail }}</span></span>
              </span>
              <span class="text-xs tabular-nums" :class="pt.feeUSD ? 'text-ink-700' : 'text-champagne-700'">{{ pt.feeUSD ? '+' + currency.format(pt.feeUSD) : 'Included' }}</span>
            </button>
          </div>
        </div>

        <!-- CTAs -->
        <div class="mt-8 space-y-3">
          <button class="btn-ink w-full !py-4" :disabled="adding" @click="addToBag(false)">
            <svg v-if="adding" class="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2" stroke-dasharray="40" stroke-linecap="round" /></svg>
            {{ t('product.addToBag') }} — {{ currency.format(totalUSD) }}
          </button>
          <div class="flex gap-3">
            <button class="btn-outline flex-1" @click="addToBag(true)">{{ t('product.buyNow') }}</button>
            <button class="btn-outline !px-4" :aria-label="t('common.addToWishlist')" @click="toggleWish">
              <svg class="w-5 h-5" :class="saved ? 'fill-champagne-500 stroke-champagne-500' : 'fill-none stroke-ink-900'" viewBox="0 0 24 24" stroke-width="1.5"><path stroke-linecap="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" /></svg>
            </button>
            <Menu as="div" class="relative">
              <MenuButton class="btn-outline !px-4 h-full" :aria-label="t('product.share')">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M7.217 10.907a2.25 2.25 0 100 2.186m0-2.186c.18.324.283.696.283 1.093s-.103.77-.283 1.093m0-2.186l9.566-5.314m-9.566 7.5l9.566 5.314m0 0a2.25 2.25 0 103.935 2.186 2.25 2.25 0 00-3.935-2.186zm0-12.814a2.25 2.25 0 103.933-2.185 2.25 2.25 0 00-3.933 2.185z" /></svg>
              </MenuButton>
              <MenuItems class="absolute right-0 mt-2 w-44 bg-white shadow-lg ring-1 ring-ink-900/5 py-1 z-20 focus:outline-none">
                <MenuItem v-for="opt in ['Copy Link', 'Email', 'Pinterest', 'Facebook']" :key="opt" v-slot="{ active }">
                  <button @click="share" :class="[active ? 'bg-ink-50' : '', 'w-full text-left px-4 py-2 text-sm text-ink-700']">{{ opt }}</button>
                </MenuItem>
              </MenuItems>
            </Menu>
          </div>
        </div>

        <!-- trust strip -->
        <div class="mt-8 grid grid-cols-3 gap-3 border-t border-ink-100 pt-6">
          <div class="text-center"><svg class="w-5 h-5 mx-auto text-champagne-600 mb-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><path stroke-linecap="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg><p class="editorial-label text-ink-500 text-[9px]">Made to Order</p></div>
          <div class="text-center"><svg class="w-5 h-5 mx-auto text-champagne-600 mb-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><path stroke-linecap="round" d="M3 8l4-4 4 4M7 4v12m14 0l-4 4-4-4m4 4V8" /></svg><p class="editorial-label text-ink-500 text-[9px]">Free Returns</p></div>
          <div class="text-center"><svg class="w-5 h-5 mx-auto text-champagne-600 mb-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><circle cx="12" cy="12" r="9" /><path stroke-linecap="round" d="M3 12h18" /></svg><p class="editorial-label text-ink-500 text-[9px]">Ships Worldwide</p></div>
        </div>
      </div>
    </section>

    <!-- detail tabs -->
    <section class="container-editorial py-12 border-t border-ink-100">
      <TabGroup>
        <TabList class="flex flex-wrap gap-8 border-b border-ink-200">
          <Tab v-for="tab in [t('product.description'), t('product.fabricCare'), t('product.shippingReturns')]" :key="tab" v-slot="{ selected }" as="template">
            <button class="pb-4 editorial-label text-[11px] border-b-2 -mb-px transition-colors focus:outline-none" :class="selected ? 'border-ink-950 text-ink-950' : 'border-transparent text-ink-400 hover:text-ink-700'">{{ tab }}</button>
          </Tab>
        </TabList>
        <TabPanels class="pt-8 max-w-3xl">
          <TabPanel class="space-y-4 text-ink-600 leading-relaxed text-[15px]">
            <p>{{ product.description }}</p>
            <p class="text-ink-400 text-sm">{{ product.modelNote }}</p>
            <ul class="grid sm:grid-cols-2 gap-2 pt-2">
              <li v-for="(d, i) in [['Silhouette', product.silhouette], ['Neckline', product.neckline], ['Fabric', product.fabric], ['Sleeve', product.sleeve]]" :key="i" class="flex justify-between border-b border-ink-50 py-2 text-sm"><span class="text-ink-400">{{ d[0] }}</span><span class="text-ink-800">{{ d[1] }}</span></li>
            </ul>
          </TabPanel>
          <TabPanel class="text-ink-600 leading-relaxed text-[15px]"><p>{{ product.fabricCare }}</p></TabPanel>
          <TabPanel class="text-ink-600 leading-relaxed text-[15px] space-y-3">
            <p><strong class="text-ink-900">Made-to-order:</strong> crafted in 3–4 weeks (Standard), with Express and Rush options at checkout.</p>
            <p><strong class="text-ink-900">Shipping:</strong> insured worldwide delivery to 60+ countries with full duty transparency. Complimentary over $500.</p>
            <p><strong class="text-ink-900">Returns:</strong> 30-day returns on standard sizes. Custom-size gowns are final sale, but we offer one complimentary alteration credit.</p>
          </TabPanel>
        </TabPanels>
      </TabGroup>
    </section>

    <!-- reviews -->
    <section id="reviews" class="bg-white border-t border-ink-100">
      <div class="container-editorial py-16">
        <div class="grid lg:grid-cols-[320px_1fr] gap-12">
          <div>
            <p class="editorial-label text-champagne-600 mb-3">{{ t('product.reviews') }}</p>
            <div class="flex items-end gap-3 mb-5">
              <span class="font-serif text-6xl text-ink-950">{{ product.rating.toFixed(1) }}</span>
              <div class="pb-2"><StarRating :rating="product.rating" size="md" /><p class="text-xs text-ink-400 mt-1">{{ product.reviewCount }} {{ t('product.reviews') }}</p></div>
            </div>
            <div class="space-y-1.5">
              <div v-for="r in ratingDist" :key="r.star" class="flex items-center gap-3 text-xs">
                <span class="text-ink-500 w-3 tabular-nums">{{ r.star }}</span>
                <svg class="w-3 h-3 text-champagne-500" viewBox="0 0 20 20" fill="currentColor"><path d="M10 1.5l2.47 5.18 5.7.6-4.27 3.83 1.2 5.59L10 13.9l-5.1 2.8 1.2-5.59L1.83 7.28l5.7-.6z" /></svg>
                <div class="flex-1 h-1.5 bg-ink-100"><div class="h-full bg-ink-900" :style="{ width: r.pct + '%' }"></div></div>
                <span class="text-ink-400 w-8 text-right tabular-nums">{{ r.count }}</span>
              </div>
            </div>
            <button class="btn-outline w-full mt-6 btn-sm">{{ t('product.writeReview') }}</button>
          </div>

          <div>
            <div class="flex items-center gap-2 mb-6 flex-wrap">
              <span class="editorial-label text-ink-400 text-[10px] mr-1">{{ t('product.filterBySize') }}:</span>
              <button @click="reviewSizeFilter = 'all'" class="px-3 py-1.5 text-xs border transition-colors" :class="reviewSizeFilter === 'all' ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 hover:border-ink-950'">All</button>
              <button v-for="s in reviewSizes" :key="s" @click="reviewSizeFilter = s" class="px-3 py-1.5 text-xs border transition-colors" :class="reviewSizeFilter === s ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 hover:border-ink-950'">{{ s }}</button>
            </div>
            <div class="space-y-8">
              <article v-for="r in filteredReviews" :key="r.id" class="border-b border-ink-100 pb-8">
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center gap-3">
                    <StarRating :rating="r.rating" />
                    <h4 class="font-serif text-lg">{{ r.title }}</h4>
                  </div>
                  <span class="text-xs text-ink-400">{{ r.date }}</span>
                </div>
                <p class="text-ink-600 text-[15px] leading-relaxed mb-3">{{ r.body }}</p>
                <div v-if="r.images.length" class="flex gap-2 mb-3">
                  <img v-for="(im, i) in r.images" :key="i" :src="im" alt="Customer photo" class="w-16 h-20 object-cover bg-ink-50" />
                </div>
                <div class="flex items-center gap-4 text-xs text-ink-400">
                  <span class="inline-flex items-center gap-1.5"><svg class="w-3.5 h-3.5 text-champagne-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>{{ r.author }}</span>
                  <span class="editorial-label text-[9px]">{{ t('product.verifiedBuyer') }}</span>
                  <span>· {{ t('product.sizeWorn') }} {{ r.sizeWorn }}</span>
                  <button class="ml-auto inline-flex items-center gap-1 hover:text-ink-900"><svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6.633 10.5h.75a2.25 2.25 0 002.25-2.25V6a2.25 2.25 0 014.5 0v2.25c0 .414.336.75.75.75h3a2.25 2.25 0 012.244 2.477l-.75 7.5A2.25 2.25 0 0117.385 21H6.633" /></svg>{{ t('product.helpful') }} ({{ r.helpful }})</button>
                </div>
                <div v-if="r.reply" class="mt-4 ml-6 pl-4 border-l-2 border-champagne-200 bg-ink-50/50 p-4">
                  <p class="editorial-label text-champagne-700 text-[9px] mb-1">{{ t('product.merchantReply') }}</p>
                  <p class="text-sm text-ink-600">{{ r.reply }}</p>
                </div>
              </article>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Q&A -->
    <section class="container-editorial py-16 border-t border-ink-100">
      <div class="flex items-center justify-between mb-8">
        <h2 class="font-serif text-3xl">{{ t('product.questions') }} <span class="text-ink-400 text-xl">({{ product.questions.length }})</span></h2>
        <button class="btn-outline btn-sm">{{ t('product.askQuestion') }}</button>
      </div>
      <div class="max-w-3xl divide-y divide-ink-100">
        <Disclosure v-for="q in product.questions" :key="q.id" v-slot="{ open }">
          <DisclosureButton class="w-full flex items-center justify-between py-5 text-left">
            <span class="text-ink-900 inline-flex items-center gap-3"><span class="editorial-label text-champagne-600 text-[10px]">Q</span>{{ q.body }}</span>
            <svg class="w-4 h-4 text-ink-400 transition-transform shrink-0" :class="open ? 'rotate-180' : ''" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
          </DisclosureButton>
          <DisclosurePanel class="pb-5 pl-7 text-ink-600 text-[15px] leading-relaxed">{{ q.answer }}</DisclosurePanel>
        </Disclosure>
      </div>
    </section>

    <!-- often bought with -->
    <section class="bg-ink-50/60 border-t border-ink-100">
      <div class="container-editorial py-16">
        <h2 class="font-serif text-3xl mb-8">{{ t('product.oftenBoughtWith') }}</h2>
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
          <ProductCard v-for="p in oftenBought" :key="p.id" :product="p" />
        </div>
      </div>
    </section>

    <!-- you may also like -->
    <section class="container-editorial py-16">
      <h2 class="font-serif text-3xl mb-8">{{ t('product.youMayAlsoLike') }}</h2>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
        <ProductCard v-for="p in related.slice(0, 4)" :key="p.id" :product="p" />
      </div>
    </section>

    <!-- recently viewed -->
    <section v-if="recent.length" class="container-editorial pb-16">
      <h2 class="font-serif text-2xl mb-6">{{ t('product.recentlyViewed') }}</h2>
      <div class="flex gap-4 overflow-x-auto no-scrollbar pb-2">
        <RouterLink v-for="p in recent" :key="p.id" :to="`/products/${p.slug}`" class="shrink-0 w-40 group">
          <div class="aspect-[3/4] bg-ink-50 overflow-hidden mb-2"><img :src="p.thumb" :alt="p.name" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" /></div>
          <p class="font-serif text-base group-hover:text-champagne-700">{{ p.name }}</p>
          <p class="text-xs text-ink-500 tabular-nums">{{ currency.format(p.basePrice) }}</p>
        </RouterLink>
      </div>
    </section>

    <!-- modals -->
    <SizeGuideModal :open="sizeGuideOpen" @close="sizeGuideOpen = false" />

    <!-- custom size modal -->
    <TransitionRoot :show="customSizeOpen" as="template">
      <Dialog @close="customSizeOpen = false" class="relative z-[65]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0"><div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" /></TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0 scale-95">
            <DialogPanel class="w-full max-w-md bg-white p-8">
              <h2 class="font-serif text-2xl mb-1">{{ t('product.customSize') }}</h2>
              <p class="text-sm text-ink-500 mb-6">{{ t('product.customSizeDesc') }} — all measurements in inches.</p>
              <div class="grid grid-cols-2 gap-4">
                <div v-for="f in [['bust','Bust'],['waist','Waist'],['hips','Hips'],['height','Height'],['hollow','Hollow to Floor']]" :key="f[0]" :class="f[0]==='hollow' ? 'col-span-2' : ''">
                  <label class="field-label">{{ f[1] }}</label>
                  <input v-model="custom[f[0]]" type="number" class="field" placeholder="0.0" />
                </div>
              </div>
              <button class="btn-ink w-full mt-6" @click="applyCustom">{{ t('common.save') }}</button>
              <button class="editorial-label text-ink-400 hover:text-ink-950 mt-3 mx-auto block" @click="customSizeOpen = false">{{ t('common.cancel') }}</button>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>

    <!-- lightbox -->
    <TransitionRoot :show="lightboxOpen" as="template">
      <Dialog @close="lightboxOpen = false" class="relative z-[65]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0"><div class="fixed inset-0 bg-ink-950/95" /></TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-6" @click="lightboxOpen = false">
          <button class="absolute top-6 right-6 text-white/70 hover:text-white" aria-label="Close"><svg class="w-7 h-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
          <img :src="product.gallery[activeImg]" :alt="product.fullName" class="max-h-[88vh] max-w-full object-contain" />
        </div>
      </Dialog>
    </TransitionRoot>
  </div>

  <!-- not found -->
  <div v-else class="container-editorial section-pad text-center min-h-[50vh] grid place-items-center">
    <div>
      <h1 class="font-serif text-3xl mb-3">Gown not found</h1>
      <RouterLink to="/wedding-dresses" class="btn-ink">{{ t('common.shopNow') }}</RouterLink>
    </div>
  </div>
</template>
