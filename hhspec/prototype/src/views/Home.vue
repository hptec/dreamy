<script setup>
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { HERO_IMAGES, PRODUCT_IMAGES, TILE_IMAGES, COLLECTIONS, HOME_CATEGORY_TILES, COLORS, ALL_PRODUCTS } from '../data/catalog'
import ProductCard from '../components/ProductCard.vue'

const { t } = useI18n()
const colorDots = COLORS.slice(0, 18)
const irl = PRODUCT_IMAGES.slice(20, 26)
const featured = ALL_PRODUCTS.filter((p) => p.tags.includes('best-seller')).slice(0, 4)

const values = [
  { t: 'home.value1Title', d: 'home.value1Desc', icon: 'scissors' },
  { t: 'home.value2Title', d: 'home.value2Desc', icon: 'ruler' },
  { t: 'home.value3Title', d: 'home.value3Desc', icon: 'swatch' },
  { t: 'home.value4Title', d: 'home.value4Desc', icon: 'globe' },
]
</script>

<template>
  <div>
    <!-- HERO -->
    <section class="relative -mt-20 h-screen min-h-[640px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[0]" alt="Maison Eden Couture" class="absolute inset-0 w-full h-full object-cover animate-kenburns" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/70 via-ink-950/10 to-ink-950/30"></div>
      <div class="relative container-editorial pb-20 sm:pb-28">
        <div class="max-w-2xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">{{ t('hero.eyebrow') }}</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">{{ t('hero.title') }}</h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-lg font-light leading-relaxed">{{ t('hero.subtitle') }}</p>
          <div class="flex flex-wrap gap-4 mt-9">
            <RouterLink to="/wedding-dresses" class="btn-ghost">{{ t('hero.cta') }}</RouterLink>
            <RouterLink to="/atelier" class="text-white editorial-label link-underline self-center">{{ t('hero.ctaSecondary') }}</RouterLink>
          </div>
        </div>
      </div>
      <div class="absolute bottom-8 left-1/2 -translate-x-1/2 text-white/60 animate-bounce">
        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M12 4v16m0 0l6-6m-6 6l-6-6" /></svg>
      </div>
    </section>

    <!-- COLLECTIONS -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="text-center mb-14">
          <p class="editorial-label text-champagne-600 mb-3">{{ t('home.collectionsSub') }}</p>
          <h2 class="font-serif text-4xl sm:text-5xl">{{ t('home.collectionsTitle') }}</h2>
        </div>
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6">
          <RouterLink v-for="c in COLLECTIONS" :key="c.id" :to="c.route" class="group relative overflow-hidden aspect-[3/4] bg-ink-100">
            <img :src="c.img" :alt="c.title" class="absolute inset-0 w-full h-full object-cover transition-transform duration-[1.2s] ease-editorial group-hover:scale-105" />
            <div class="absolute inset-0 bg-gradient-to-t from-ink-950/65 via-transparent to-transparent"></div>
            <div class="absolute bottom-0 inset-x-0 p-5 sm:p-6">
              <h3 class="font-serif text-white text-2xl">{{ c.title }}</h3>
              <p class="editorial-label-light text-white/70 mt-1 text-[10px]">{{ c.sub }}</p>
              <span class="inline-block mt-3 text-white editorial-label text-[10px] border-b border-white/0 group-hover:border-white/80 transition-all pb-0.5">{{ t('common.discover') }}</span>
            </div>
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- CATEGORY TILES -->
    <section class="pb-4">
      <div class="container-editorial">
        <div class="grid grid-cols-3 lg:grid-cols-6 gap-3 lg:gap-4">
          <RouterLink v-for="cat in HOME_CATEGORY_TILES" :key="cat.label" :to="cat.route" class="group text-center">
            <div class="relative overflow-hidden aspect-square bg-ink-100 mb-3">
              <img :src="cat.img" :alt="cat.label" class="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" />
            </div>
            <span class="editorial-label text-ink-700 group-hover:text-champagne-700 transition-colors">{{ cat.label }}</span>
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- VALUE PROPS -->
    <section class="section-pad bg-ink-950 text-white">
      <div class="container-editorial">
        <div class="text-center mb-14">
          <p class="editorial-label-light text-champagne-400 mb-3">Maison Eden</p>
          <h2 class="font-serif text-4xl sm:text-5xl text-white">{{ t('home.valuesTitle') }}</h2>
        </div>
        <div class="grid sm:grid-cols-2 lg:grid-cols-4 gap-10">
          <div v-for="(v, i) in values" :key="i" class="text-center">
            <div class="w-14 h-14 mx-auto mb-5 grid place-items-center border border-champagne-400/40 text-champagne-400">
              <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
                <path v-if="v.icon==='scissors'" stroke-linecap="round" stroke-linejoin="round" d="M7.848 8.25l1.536.887M7.848 8.25a3 3 0 11-5.196-3 3 3 0 015.196 3zm1.536.887a2.165 2.165 0 011.083 1.839c.005.351.054.695.14 1.024M9.384 9.137l10.62 6.13m-8 .867a3 3 0 11-5.196 3 3 3 0 015.196-3z" />
                <path v-else-if="v.icon==='ruler'" stroke-linecap="round" stroke-linejoin="round" d="M3 6.75A.75.75 0 013.75 6h16.5a.75.75 0 01.75.75v10.5a.75.75 0 01-.75.75H3.75a.75.75 0 01-.75-.75V6.75zM7 6v3M11 6v4M15 6v3M19 6v4" />
                <path v-else-if="v.icon==='swatch'" stroke-linecap="round" stroke-linejoin="round" d="M4.098 19.902a3.75 3.75 0 005.304 0l6.401-6.402M6.75 21A3.75 3.75 0 013 17.25V4.125C3 3.504 3.504 3 4.125 3h5.25c.621 0 1.125.504 1.125 1.125v4.072M6.75 21a3.75 3.75 0 003.75-3.75V8.197" />
                <path v-else stroke-linecap="round" stroke-linejoin="round" d="M12 21a9 9 0 100-18 9 9 0 000 18zm0 0c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m-9 9h18" />
              </svg>
            </div>
            <h3 class="font-serif text-xl mb-2 text-white">{{ t(v.t) }}</h3>
            <p class="text-sm text-ink-300 leading-relaxed">{{ t(v.d) }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- FEATURED PRODUCTS -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="flex items-end justify-between mb-12">
          <div>
            <p class="editorial-label text-champagne-600 mb-3">Best Sellers</p>
            <h2 class="font-serif text-4xl sm:text-5xl">Most Loved</h2>
          </div>
          <RouterLink to="/wedding-dresses" class="editorial-label link-underline hidden sm:block">{{ t('common.viewAll') }}</RouterLink>
        </div>
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
          <ProductCard v-for="p in featured" :key="p.id" :product="p" />
        </div>
      </div>
    </section>

    <!-- LOOKBOOK EDITORIAL -->
    <section class="relative h-[70vh] min-h-[480px] flex items-center overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[1]" alt="Lookbook" class="absolute inset-0 w-full h-full object-cover opacity-90" />
      <div class="absolute inset-0 bg-ink-950/30"></div>
      <div class="relative container-editorial">
        <div class="max-w-xl">
          <p class="editorial-label-light text-champagne-300 mb-4">{{ t('home.lookbookEyebrow') }}</p>
          <h2 class="font-serif text-white text-5xl sm:text-6xl leading-none mb-6">{{ t('home.lookbookTitle') }}</h2>
          <RouterLink to="/lookbook/eden-2026" class="btn-ghost">{{ t('home.lookbookCta') }}</RouterLink>
        </div>
      </div>
    </section>

    <!-- COLOR SYSTEM -->
    <section class="section-pad bg-white">
      <div class="container-editorial text-center">
        <p class="editorial-label text-champagne-600 mb-3">{{ t('home.colorSub') }}</p>
        <h2 class="font-serif text-4xl sm:text-5xl mb-12">{{ t('home.colorTitle') }}</h2>
        <div class="flex flex-wrap justify-center gap-3 max-w-3xl mx-auto">
          <RouterLink v-for="c in colorDots" :key="c.id" :to="`/bridesmaid-dresses?color=${c.id}`" :title="c.name"
            class="w-12 h-12 sm:w-14 sm:h-14 rounded-full ring-1 ring-ink-200 hover:ring-2 hover:ring-ink-950 hover:ring-offset-2 transition-all duration-200" :style="{ background: c.hex }" />
        </div>
        <RouterLink to="/bridesmaid-dresses" class="btn-outline mt-12">{{ t('nav.bridesmaid') }}</RouterLink>
      </div>
    </section>

    <!-- IRL / REAL BRIDES -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="text-center mb-12">
          <p class="editorial-label text-champagne-600 mb-3">{{ t('home.irlSub') }}</p>
          <h2 class="font-serif text-4xl sm:text-5xl">{{ t('home.irlTitle') }}</h2>
        </div>
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          <RouterLink v-for="(img, i) in irl" :key="i" to="/style-gallery" class="group relative overflow-hidden aspect-square bg-ink-100">
            <img :src="img" alt="Real bride" class="absolute inset-0 w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" />
            <div class="absolute inset-0 bg-ink-950/0 group-hover:bg-ink-950/20 transition-colors grid place-items-center">
              <svg class="w-6 h-6 text-white opacity-0 group-hover:opacity-100 transition-opacity" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="12" cy="12" r="3.5" /><circle cx="17.5" cy="6.5" r="1" fill="currentColor" /></svg>
            </div>
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- ATELIER STORY -->
    <section class="bg-ink-950 text-white">
      <div class="container-editorial grid lg:grid-cols-2">
        <div class="relative min-h-[400px] lg:min-h-[560px]">
          <img :src="PRODUCT_IMAGES[8]" alt="The Atelier" class="absolute inset-0 w-full h-full object-cover" />
        </div>
        <div class="flex items-center py-16 lg:pl-16">
          <div class="max-w-md">
            <p class="editorial-label-light text-champagne-400 mb-4">{{ t('home.atelierTitle') }}</p>
            <h2 class="font-serif text-4xl sm:text-5xl text-white leading-tight mb-6">A gown is a promise, stitched by hand.</h2>
            <p class="text-ink-300 leading-relaxed mb-8">{{ t('home.atelierDesc') }}</p>
            <RouterLink to="/atelier" class="btn-ghost">{{ t('nav.atelier') }}</RouterLink>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
