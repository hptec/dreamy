<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { PRODUCT_IMAGES, ALL_PRODUCTS } from '../data/catalog'
import { useCurrencyStore } from '../stores/currency'

const currency = useCurrencyStore()

// Curated UGC selection — 28 images
const galleryProducts = computed(() => ALL_PRODUCTS.slice(0, 28))
const HANDLES = ['@laurenmoreau', '@isabella.k', '@thequietbride', '@sophialune', '@evrosebridal', '@charlottewren', '@aviva.veil', '@harper.atelier', '@oliviaivory', '@mia.couture', '@ameliarose', '@_clairewren', '@evelynjune', '@noor.bridal', '@romybride', '@delphinev', '@elise.couture', '@calla.aurelia', '@maevecanvas', '@cosima.archive', '@solene.gowns', '@eden.bride', '@yara_v', '@marisol.silver', '@vivienne.bridal', '@brigittecouture', '@thefadingvows', '@saskia.atelier']

const CATEGORIES = [
  { id: 'all', label: 'All' },
  { id: 'wedding', label: 'Brides' },
  { id: 'bridesmaid', label: 'Bridesmaids' },
  { id: 'evening', label: 'Evening' },
  { id: 'real', label: 'Real Weddings' },
]

const CAPTIONS = [
  'A garden ceremony in Provence',
  'Reception sparkle, downtown',
  'A quiet first look at sunrise',
  'Black tie at the Hôtel de Crillon',
  'Bridesmaids in the loggia',
  'A toast to forever',
  'After-party in candlelight',
  'The first dance',
  'A whisper of lace',
  'Bridal portrait, golden hour',
  'A celebration on the cliffs',
  'Evening drinks on the terrace',
  'A walk through the orchard',
  'The final stitch in the atelier',
  'Soft morning light, downtown loft',
  'A toast under the chandeliers',
  'Vows beneath the colonnade',
  'A weekend in the countryside',
  'A second look for the evening',
  'A flower girl in motion',
  'The Maison Eden archive',
  'A villa wedding in Lake Como',
  'Sunset on the Amalfi coast',
  'A church ceremony in Charleston',
  'Black-tie soirée in Manhattan',
  'A backyard reception in Connecticut',
  'A reception in the orangerie',
  'Couture in the Catskills',
]

const tiles = computed(() => {
  // varied aspect-ratio buckets for editorial wall rhythm (portrait / square / landscape)
  const aspects = ['aspect-[3/4]', 'aspect-square', 'aspect-[3/4]', 'aspect-[4/5]', 'aspect-square', 'aspect-[3/4]']
  return galleryProducts.value.map((p, i) => {
    const img = p.thumb || PRODUCT_IMAGES[i % PRODUCT_IMAGES.length]
    const handle = HANDLES[i % HANDLES.length]
    const caption = CAPTIONS[i % CAPTIONS.length]
    const realWedding = i % 4 === 0
    const tagBucket = realWedding ? 'real' : (p.category === 'bridesmaid' ? 'bridesmaid' : (p.category === 'evening' ? 'evening' : 'wedding'))
    return { id: `${p.id}-${i}`, img, handle, caption, product: p, tagBucket, aspect: aspects[i % aspects.length] }
  })
})

const filterId = ref('all')
const filtered = computed(() => {
  if (filterId.value === 'all') return tiles.value
  return tiles.value.filter((t) => t.tagBucket === filterId.value)
})

const lightboxOpen = ref(false)
const active = ref(null)

function openTile(t) {
  active.value = t
  lightboxOpen.value = true
}
function closeLightbox() {
  lightboxOpen.value = false
}
</script>

<template>
  <div>
    <!-- editorial header -->
    <section class="bg-canvas border-b border-ink-100">
      <div class="container-editorial section-pad !py-20 sm:!py-28 text-center">
        <p class="editorial-label text-champagne-600 mb-5">@maisoneden &middot; Real life</p>
        <h1 class="font-serif text-5xl sm:text-7xl text-ink-950 leading-[0.95] text-balance max-w-4xl mx-auto">Maison Eden, in real life</h1>
        <p class="text-base sm:text-lg text-ink-500 mt-7 max-w-xl mx-auto font-light leading-relaxed">A living archive of brides, bridesmaids, and the quiet moments in between &mdash; shared by the people who wore the gowns.</p>
        <p class="editorial-label text-ink-400 mt-8 text-[10px]">Tag <span class="text-ink-950">@maisoneden</span> to be featured</p>
      </div>
    </section>

    <!-- filter chip strip -->
    <div class="sticky top-20 z-30 bg-white/95 backdrop-blur-sm border-b border-ink-100">
      <div class="container-editorial py-5 flex items-center justify-center gap-2 sm:gap-3 overflow-x-auto no-scrollbar">
        <button
          v-for="c in CATEGORIES"
          :key="c.id"
          @click="filterId = c.id"
          class="shrink-0 inline-flex items-center gap-2 px-5 py-2.5 text-[11px] font-medium uppercase tracking-label border transition-all duration-200 ease-editorial"
          :class="filterId === c.id ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 text-ink-600 hover:border-ink-950 hover:text-ink-950'"
        >
          {{ c.label }}
        </button>
      </div>
    </div>

    <!-- masonry wall -->
    <section class="container-editorial py-12 sm:py-16">
      <div class="columns-2 sm:columns-3 lg:columns-4 gap-3 sm:gap-4 lg:gap-5">
        <button
          v-for="t in filtered"
          :key="t.id"
          @click="openTile(t)"
          class="block w-full mb-3 sm:mb-4 lg:mb-5 break-inside-avoid relative group overflow-hidden bg-ink-100 text-left"
        >
          <div class="w-full overflow-hidden" :class="t.aspect">
            <img :src="t.img" :alt="t.caption" loading="lazy" class="w-full h-full object-cover transition-transform duration-700 ease-editorial group-hover:scale-[1.05]" />
          </div>
          <!-- hover overlay -->
          <div class="absolute inset-0 bg-ink-950/0 group-hover:bg-ink-950/55 transition-colors duration-300"></div>
          <div class="absolute inset-0 flex flex-col items-center justify-center text-white opacity-0 group-hover:opacity-100 transition-opacity duration-300 p-4 text-center">
            <svg class="w-7 h-7 mb-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><rect x="3" y="3" width="18" height="18" rx="2.5" /><circle cx="12" cy="12" r="4" /><circle cx="17.5" cy="6.5" r="1" fill="currentColor" /></svg>
            <p class="editorial-label-light text-[10px] text-champagne-300">{{ t.handle }}</p>
            <p class="font-serif text-base sm:text-lg mt-1 leading-tight">{{ t.product.name }}</p>
          </div>
        </button>
      </div>

      <p v-if="!filtered.length" class="text-center py-24 font-serif text-2xl text-ink-500">No posts in this filter yet.</p>
    </section>

    <!-- footer CTA strip -->
    <section class="bg-ink-950 text-white">
      <div class="container-editorial section-pad !py-20 text-center">
        <p class="editorial-label-light text-champagne-400 mb-4">Be featured</p>
        <h2 class="font-serif text-3xl sm:text-5xl text-white text-balance">Share your Maison Eden moment</h2>
        <p class="text-ink-300 mt-5 max-w-md mx-auto leading-relaxed text-sm">Tag <span class="text-white">@maisoneden</span> on Instagram for a chance to be featured here.</p>
        <div class="flex flex-wrap justify-center gap-4 mt-9">
          <RouterLink to="/wedding-dresses" class="btn-ghost">Shop Wedding</RouterLink>
          <RouterLink to="/lookbook/eden-2026" class="text-white editorial-label link-underline self-center">View the Lookbook</RouterLink>
        </div>
      </div>
    </section>

    <!-- lightbox -->
    <TransitionRoot :show="lightboxOpen" as="template">
      <Dialog @close="closeLightbox" class="relative z-[70]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0">
          <div class="fixed inset-0 bg-ink-950/92 backdrop-blur-sm" />
        </TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-4 sm:p-8">
          <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0 scale-95">
            <DialogPanel class="relative w-full max-w-5xl bg-ink-950 grid lg:grid-cols-[1.4fr_1fr] gap-0 max-h-[90vh] overflow-hidden">
              <button @click="closeLightbox" :aria-label="'Close'" class="absolute top-4 right-4 z-10 w-10 h-10 grid place-items-center text-white/70 hover:text-white bg-ink-950/50 backdrop-blur-sm">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg>
              </button>
              <div v-if="active" class="relative bg-ink-900 min-h-[60vh] lg:min-h-0">
                <img :src="active.img" :alt="active.caption" class="absolute inset-0 w-full h-full object-cover" />
              </div>
              <div v-if="active" class="p-7 sm:p-10 bg-white text-ink-950 flex flex-col overflow-y-auto">
                <p class="editorial-label text-champagne-600 mb-3">{{ active.handle }}</p>
                <h3 class="font-serif text-2xl sm:text-3xl leading-tight">{{ active.caption }}</h3>
                <p class="text-sm text-ink-500 mt-3 leading-relaxed italic">"wearing the <span class="text-ink-950 not-italic">{{ active.product.name }}</span> gown"</p>

                <div class="hairline my-7" />

                <!-- product mini -->
                <RouterLink :to="`/products/${active.product.slug}`" class="group flex items-center gap-4">
                  <div class="w-20 h-24 bg-ink-50 overflow-hidden shrink-0">
                    <img :src="active.product.thumb" :alt="active.product.name" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="editorial-label text-ink-400 text-[10px] mb-1">{{ active.product.silhouette }} &middot; {{ active.product.fabric }}</p>
                    <p class="font-serif text-lg text-ink-950 group-hover:text-champagne-700 transition-colors">{{ active.product.fullName }}</p>
                    <p class="text-sm text-ink-500 mt-1 tabular-nums">From {{ currency.format(active.product.basePrice) }}</p>
                  </div>
                </RouterLink>

                <RouterLink :to="`/products/${active.product.slug}`" class="btn-ink w-full mt-7" @click="closeLightbox">
                  Shop this look
                  <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M9 5l7 7-7 7" /></svg>
                </RouterLink>
                <div class="mt-5 flex items-center gap-4 text-[11px] text-ink-400">
                  <span class="inline-flex items-center gap-1.5">
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" /></svg>
                    Save look
                  </span>
                  <span class="inline-flex items-center gap-1.5">
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M7.217 10.907a2.25 2.25 0 100 2.186m0-2.186c.18.324.283.696.283 1.093s-.103.77-.283 1.093m0-2.186l9.566-5.314m-9.566 7.5l9.566 5.314m0 0a2.25 2.25 0 103.935 2.186 2.25 2.25 0 00-3.935-2.186zm0-12.814a2.25 2.25 0 103.933-2.185 2.25 2.25 0 00-3.933 2.185z" /></svg>
                    Share
                  </span>
                </div>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>
  </div>
</template>
