<script setup>
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { HERO_IMAGES, PRODUCT_IMAGES, TILE_IMAGES, PRODUCTS, ALL_PRODUCTS } from '../data/catalog'
import { useCurrencyStore } from '../stores/currency'

const route = useRoute()
const currency = useCurrencyStore()

// Three editorial collections; each panel maps to real PRODUCTS
const collections = {
  'eden-2026': {
    slug: 'eden-2026',
    title: 'Eden, 2026',
    issue: 'Issue No. 14',
    subtitle: 'Bridal & Evening',
    location: 'Florence, in the early light',
    cover: HERO_IMAGES[1],
    intro:
      'A spring collection cut from the long afternoons of a Florentine garden — duchess satin, hand-laid silk lace, and a quiet, architectural restraint. Photographed in the early March light, two hours before the city wakes.',
    panels: [
      {
        side: 'left',
        eyebrow: 'Look I',
        title: 'A Long Quiet',
        story: 'A floor-sweeping gown in mulberry silk, cut on the bias, finished by hand with French seams. The bride wears nothing else — no veil, no jewellery, no second thought.',
        img: PRODUCT_IMAGES[2],
        productIds: [PRODUCTS[1]?.id, PRODUCTS[5]?.id],
      },
      {
        side: 'right',
        eyebrow: 'Look II',
        title: 'After the Vows',
        story: 'Two-piece bridal in re-embroidered Chantilly lace, designed to move from ceremony to reception without a second outfit. The skirt unhooks; the bodice stays.',
        img: PRODUCT_IMAGES[14],
        productIds: [PRODUCTS[8]?.id, PRODUCTS[6]?.id, PRODUCTS[11]?.id],
      },
      {
        side: 'left',
        eyebrow: 'Look III',
        title: 'The Long Train',
        story: 'A ball-gown silhouette in pressed duchess, finished with a five-metre chapel train laid by hand. Photographed at the foot of a cloister, before the bells.',
        img: PRODUCT_IMAGES[22],
        productIds: [PRODUCTS[1]?.id, PRODUCTS[11]?.id],
      },
      {
        side: 'right',
        eyebrow: 'Look IV',
        title: 'For the Evening',
        story: 'A halter-neck mermaid in cabernet velvet — the kind of gown that belongs to one woman, one room, one long champagne hour after the speeches end.',
        img: PRODUCT_IMAGES[66],
        productIds: [PRODUCTS[13]?.id, PRODUCTS[19]?.id],
      },
      {
        side: 'left',
        eyebrow: 'Look V',
        title: 'The Garden',
        story: 'A whisper of a gown in floral jacquard — for the late-spring garden ceremony when the air is warm and the silhouette wants air, too.',
        img: PRODUCT_IMAGES[28],
        productIds: [PRODUCTS[1]?.id, PRODUCTS[7]?.id],
      },
    ],
  },
  'evening-noir': {
    slug: 'evening-noir',
    title: 'Evening Noir',
    issue: 'Issue No. 13',
    subtitle: 'A Capsule, in Black',
    location: 'A nocturne in Paris',
    cover: HERO_IMAGES[5],
    intro:
      'Six gowns, one colour. A capsule study in the architecture of black — from sequinned mermaid to crepe sheath, from velvet halter to satin column.',
    panels: [
      {
        side: 'left',
        eyebrow: 'Look I',
        title: 'The Sequin',
        story: 'A mermaid silhouette in black sequins on silk, hand-applied one motif at a time over fourteen days of finishing.',
        img: PRODUCT_IMAGES[62],
        productIds: [PRODUCTS[12]?.id, PRODUCTS[17]?.id],
      },
      {
        side: 'right',
        eyebrow: 'Look II',
        title: 'The Crepe',
        story: 'Off-the-shoulder sheath in dense Italian crepe — for the woman who would rather move than be looked at.',
        img: PRODUCT_IMAGES[68],
        productIds: [PRODUCTS[15]?.id, PRODUCTS[12]?.id],
      },
      {
        side: 'left',
        eyebrow: 'Look III',
        title: 'The Velvet',
        story: 'Halter-neck velvet — heavy on the shoulder, lighter than expected at the hem.',
        img: PRODUCT_IMAGES[70],
        productIds: [PRODUCTS[13]?.id, PRODUCTS[19]?.id],
      },
      {
        side: 'right',
        eyebrow: 'Look IV',
        title: 'The Satin',
        story: 'A column of pressed duchess, single-back-slit, with a French seam down the spine.',
        img: PRODUCT_IMAGES[74],
        productIds: [PRODUCTS[18]?.id, PRODUCTS[16]?.id],
      },
    ],
  },
  'in-bloom': {
    slug: 'in-bloom',
    title: 'In Bloom',
    issue: 'Issue No. 12',
    subtitle: 'Bridesmaids in Ninety Shades',
    location: 'A garden, in May',
    cover: HERO_IMAGES[7],
    intro:
      'A single curated palette for the wedding party — ninety shades, four silhouettes, photographed across a sun-warmed garden over two slow afternoons.',
    panels: [
      {
        side: 'left',
        eyebrow: 'Look I',
        title: 'Dusty Rose, A-Line',
        story: 'The bridesmaid silhouette that has dressed more parties than any other in our archive — soft chiffon, halter neckline, deep side pockets.',
        img: PRODUCT_IMAGES[38],
        productIds: [PRODUCTS[18]?.id, PRODUCTS[20]?.id],
      },
      {
        side: 'right',
        eyebrow: 'Look II',
        title: 'Dusty Sage, Two-Piece',
        story: 'A two-piece in dusty sage — a separate bodice and skirt so each guest can fit independently. Five sizes apart, one cohesive party.',
        img: PRODUCT_IMAGES[44],
        productIds: [PRODUCTS[22]?.id, PRODUCTS[20]?.id],
      },
      {
        side: 'left',
        eyebrow: 'Look III',
        title: 'Cabernet, Mermaid',
        story: 'Mermaid silhouette in stretch satin, photographed at golden hour as the garden turned. A deep, walking colour.',
        img: PRODUCT_IMAGES[50],
        productIds: [PRODUCTS[21]?.id, PRODUCTS[23]?.id],
      },
    ],
  },
}

const currentSlug = computed(() => (route.params.slug && collections[route.params.slug] ? route.params.slug : 'eden-2026'))
const lb = computed(() => collections[currentSlug.value])

// All collections (for the index footer)
const otherCollections = computed(() => Object.values(collections).filter((c) => c.slug !== currentSlug.value))

function productFor(id) {
  return ALL_PRODUCTS.find((p) => p.id === id) || PRODUCTS[0]
}
</script>

<template>
  <div class="bg-canvas">
    <!-- COVER -->
    <section class="relative -mt-20 h-screen min-h-[680px] flex items-center justify-center overflow-hidden bg-ink-950">
      <img :src="lb.cover" :alt="lb.title" class="absolute inset-0 w-full h-full object-cover animate-kenburns" />
      <div class="absolute inset-0 bg-gradient-to-b from-ink-950/40 via-ink-950/10 to-ink-950/80"></div>

      <!-- masthead -->
      <div class="absolute inset-x-0 top-24 sm:top-28 container-editorial flex items-center justify-between text-white/80">
        <p class="editorial-label-light text-[10px]">Maison Eden · The Lookbook</p>
        <p class="editorial-label-light text-[10px] tabular-nums">{{ lb.issue }}</p>
      </div>

      <!-- title block -->
      <div class="relative container-editorial text-center animate-fadeUp">
        <p class="editorial-label-light text-champagne-300 mb-7">{{ lb.subtitle }}</p>
        <h1 class="font-serif text-white text-[14vw] sm:text-[10vw] lg:text-[8.5vw] leading-[0.9] tracking-tight text-balance">
          {{ lb.title }}
        </h1>
        <p class="text-white/70 mt-8 max-w-md mx-auto font-light leading-relaxed text-sm sm:text-base">
          {{ lb.location }}
        </p>
        <div class="mt-12 flex justify-center">
          <span class="block w-px h-16 bg-white/40"></span>
        </div>
      </div>

      <p class="absolute bottom-10 left-1/2 -translate-x-1/2 editorial-label-light text-white/60 text-[10px]">Scroll · The Issue</p>
    </section>

    <!-- INTRO ESSAY -->
    <section class="section-pad bg-canvas">
      <div class="container-narrow text-center">
        <p class="editorial-label text-champagne-600 mb-7">Editor’s Letter</p>
        <p class="font-serif text-3xl sm:text-4xl text-ink-950 leading-snug text-balance italic">
          “{{ lb.intro }}”
        </p>
        <div class="mt-10 flex items-center justify-center gap-6 text-ink-400">
          <span class="hairline border-t w-12"></span>
          <span class="editorial-label">Adèle Moreau, Creative Director</span>
          <span class="hairline border-t w-12"></span>
        </div>
      </div>
    </section>

    <!-- ALTERNATING PANELS -->
    <section>
      <article
        v-for="(p, idx) in lb.panels"
        :key="idx"
        class="relative overflow-hidden"
        :class="idx % 2 === 0 ? 'bg-canvas' : 'bg-ink-950 text-white'"
      >
        <div class="container-editorial">
          <div class="grid lg:grid-cols-12 items-center gap-10 lg:gap-16 py-16 sm:py-24 lg:py-32">
            <!-- IMAGE -->
            <div
              class="lg:col-span-7 relative aspect-[4/5] sm:aspect-[16/10] lg:aspect-[5/6] overflow-hidden bg-ink-100"
              :class="p.side === 'right' ? 'lg:order-2 lg:col-start-6' : ''"
            >
              <img :src="p.img" :alt="p.title" class="absolute inset-0 w-full h-full object-cover hover:scale-[1.03] transition-transform duration-[1.4s] ease-editorial" />
              <span class="absolute top-5 left-5 editorial-label-light text-white/85 bg-ink-950/30 backdrop-blur-sm px-3 py-1.5">{{ p.eyebrow }}</span>
            </div>

            <!-- STORY + SHOPPABLE -->
            <div
              class="lg:col-span-5"
              :class="p.side === 'right' ? 'lg:order-1 lg:col-start-1 lg:pr-8' : 'lg:pl-8'"
            >
              <p class="editorial-label mb-4" :class="idx % 2 === 0 ? 'text-champagne-600' : 'text-champagne-400'">
                {{ p.eyebrow }} · The Shoppable Look
              </p>
              <h2 class="font-serif text-4xl sm:text-5xl lg:text-6xl leading-[0.95] mb-7 text-balance" :class="idx % 2 === 0 ? 'text-ink-950' : 'text-white'">
                {{ p.title }}
              </h2>
              <p class="text-base sm:text-lg leading-relaxed font-light mb-10 max-w-md" :class="idx % 2 === 0 ? 'text-ink-700' : 'text-ink-300'">
                {{ p.story }}
              </p>

              <div class="space-y-px">
                <RouterLink
                  v-for="pid in p.productIds.filter(Boolean)"
                  :key="pid"
                  :to="`/products/${productFor(pid).slug}`"
                  class="group flex items-center gap-5 py-5 border-t last:border-b transition-colors"
                  :class="idx % 2 === 0 ? 'border-ink-200 hover:bg-white' : 'border-white/20 hover:bg-white/5'"
                >
                  <div class="w-16 h-20 shrink-0 overflow-hidden bg-ink-100">
                    <img :src="productFor(pid).thumb" :alt="productFor(pid).name" class="w-full h-full object-cover" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="font-serif text-lg leading-tight truncate" :class="idx % 2 === 0 ? 'text-ink-950' : 'text-white'">{{ productFor(pid).fullName }}</p>
                    <p class="editorial-label text-[10px] mt-1" :class="idx % 2 === 0 ? 'text-ink-400' : 'text-ink-400'">
                      {{ productFor(pid).silhouette }} · {{ productFor(pid).fabric }}
                    </p>
                  </div>
                  <div class="text-right shrink-0">
                    <p class="tabular-nums text-sm font-medium" :class="idx % 2 === 0 ? 'text-ink-950' : 'text-white'">
                      {{ currency.format(productFor(pid).basePrice) }}
                    </p>
                    <p class="editorial-label text-[10px] mt-1" :class="idx % 2 === 0 ? 'text-champagne-600' : 'text-champagne-400'">
                      Shop <span class="inline-block transition-transform group-hover:translate-x-1">→</span>
                    </p>
                  </div>
                </RouterLink>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>

    <!-- FULL-BLEED EDITORIAL BREAK -->
    <section class="relative h-[90vh] min-h-[600px] overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[9]" :alt="lb.title" class="absolute inset-0 w-full h-full object-cover scale-105" />
      <div class="absolute inset-0 bg-gradient-to-r from-ink-950/60 via-transparent to-ink-950/30"></div>
      <div class="relative container-editorial h-full flex items-center">
        <blockquote class="max-w-xl">
          <p class="editorial-label-light text-champagne-300 mb-7">An Afterthought</p>
          <p class="font-serif text-white text-4xl sm:text-5xl lg:text-6xl leading-[1.05] italic text-balance">
            “Light, then cloth, then the woman. The order matters.”
          </p>
          <p class="editorial-label-light text-white/70 mt-10">Adèle Moreau · for {{ lb.title }}</p>
        </blockquote>
      </div>
    </section>

    <!-- CREDITS -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-10">
          <div class="lg:col-span-4">
            <p class="editorial-label text-champagne-600 mb-3">Credits</p>
            <h2 class="font-serif text-3xl leading-tight">The hands behind the issue.</h2>
          </div>
          <dl class="lg:col-span-8 grid sm:grid-cols-2 gap-y-6 gap-x-10 text-sm">
            <div class="flex justify-between border-b border-ink-200 pb-3"><dt class="editorial-label text-ink-400">Creative Direction</dt><dd class="text-ink-900">Adèle Moreau</dd></div>
            <div class="flex justify-between border-b border-ink-200 pb-3"><dt class="editorial-label text-ink-400">Photography</dt><dd class="text-ink-900">Margaux Vidal</dd></div>
            <div class="flex justify-between border-b border-ink-200 pb-3"><dt class="editorial-label text-ink-400">Styling</dt><dd class="text-ink-900">Yamina Cherif</dd></div>
            <div class="flex justify-between border-b border-ink-200 pb-3"><dt class="editorial-label text-ink-400">Hair</dt><dd class="text-ink-900">Inès Laurent</dd></div>
            <div class="flex justify-between border-b border-ink-200 pb-3"><dt class="editorial-label text-ink-400">Make-up</dt><dd class="text-ink-900">Romy Hartmann</dd></div>
            <div class="flex justify-between border-b border-ink-200 pb-3"><dt class="editorial-label text-ink-400">Location</dt><dd class="text-ink-900">{{ lb.location }}</dd></div>
          </dl>
        </div>
      </div>
    </section>

    <!-- OTHER ISSUES -->
    <section class="section-pad bg-white">
      <div class="container-editorial">
        <div class="flex items-end justify-between mb-12 flex-wrap gap-6">
          <div>
            <p class="editorial-label text-champagne-600 mb-3">Other Issues</p>
            <h2 class="font-serif text-3xl sm:text-4xl leading-tight">From the archive.</h2>
          </div>
          <RouterLink to="/lookbook/eden-2026" class="editorial-label link-underline">View Eden, 2026 →</RouterLink>
        </div>

        <div class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3 lg:gap-5">
          <RouterLink
            v-for="oc in otherCollections"
            :key="oc.slug"
            :to="`/lookbook/${oc.slug}`"
            class="group relative aspect-[4/5] overflow-hidden bg-ink-100"
          >
            <img :src="oc.cover" :alt="oc.title" class="absolute inset-0 w-full h-full object-cover transition-transform duration-[1.2s] ease-editorial group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-ink-950/80 via-ink-950/10 to-transparent"></div>
            <div class="absolute bottom-0 inset-x-0 p-7">
              <p class="editorial-label-light text-champagne-300 mb-2 text-[10px]">{{ oc.issue }}</p>
              <h3 class="font-serif text-white text-3xl leading-tight">{{ oc.title }}</h3>
              <p class="text-white/70 text-sm mt-2 font-light">{{ oc.subtitle }}</p>
            </div>
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- CLOSING CTA -->
    <section class="section-pad bg-ink-950 text-white">
      <div class="container-editorial grid lg:grid-cols-2 gap-12 lg:gap-20 items-center">
        <div>
          <p class="editorial-label-light text-champagne-400 mb-5">Shop The Issue</p>
          <h2 class="font-serif text-4xl sm:text-5xl lg:text-6xl text-white leading-[0.95] text-balance">
            Every look, every gown, made-to-order in our atelier.
          </h2>
          <p class="text-ink-300 mt-7 text-lg font-light leading-relaxed max-w-md">
            Begin with the silhouette that caught your eye, or browse the full bridal collection. Each piece is cut to your measurements, hand-finished, and shipped insured.
          </p>
          <div class="flex flex-wrap gap-4 mt-9">
            <RouterLink to="/wedding-dresses" class="btn-champagne">Shop the Collection</RouterLink>
            <RouterLink to="/atelier" class="btn-ghost">Inside the Atelier</RouterLink>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div class="aspect-[3/4] overflow-hidden"><img :src="TILE_IMAGES[8]" alt="Detail" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden"><img :src="TILE_IMAGES[14]" alt="Detail" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden"><img :src="TILE_IMAGES[20]" alt="Detail" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden"><img :src="TILE_IMAGES[26]" alt="Detail" class="w-full h-full object-cover" /></div>
        </div>
      </div>
    </section>
  </div>
</template>
