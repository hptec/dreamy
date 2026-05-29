<script setup>
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const popular = [
  { to: '/wedding-dresses', label: 'Wedding' },
  { to: '/evening-dresses', label: 'Evening' },
  { to: '/bridesmaid-dresses', label: 'Bridesmaids' },
  { to: '/accessories', label: 'Accessories' },
]
</script>

<template>
  <section class="relative -mt-20 min-h-screen flex items-center justify-center overflow-hidden bg-ink-950 text-white">
    <!-- subtle champagne wash for editorial depth -->
    <div class="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full bg-champagne-500/15 blur-3xl pointer-events-none"></div>
    <div class="absolute inset-0 pointer-events-none bg-gradient-to-b from-ink-950 via-transparent to-ink-950/95"></div>

    <div class="relative container-narrow text-center py-32 sm:py-40">
      <!-- editorial 404 mark -->
      <div class="flex items-center justify-center gap-4 mb-10">
        <span class="block h-px w-12 bg-champagne-500/60"></span>
        <p class="editorial-label-light text-champagne-300 tracking-wide2">404 &middot; Not Found</p>
        <span class="block h-px w-12 bg-champagne-500/60"></span>
      </div>

      <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">
        {{ t('status.notFoundTitle') }}
      </h1>
      <p class="text-base sm:text-lg text-ink-200/85 mt-8 max-w-md mx-auto font-light leading-relaxed">{{ t('status.notFoundDesc') }} Perhaps a thread came loose &mdash; let us help you find your way back.</p>

      <div class="flex flex-wrap justify-center gap-4 mt-12">
        <RouterLink to="/" class="btn-ink !bg-white !text-ink-950 !border-white hover:!bg-transparent hover:!text-white">{{ t('status.backHome') }}</RouterLink>
        <RouterLink to="/search" class="btn-ghost">Search Maison Eden</RouterLink>
      </div>

      <div class="mt-20">
        <p class="editorial-label-light text-champagne-400 mb-7">Or explore</p>
        <div class="flex flex-wrap justify-center items-center gap-x-8 gap-y-4">
          <RouterLink
            v-for="link in popular"
            :key="link.to"
            :to="link.to"
            class="text-white editorial-label link-underline text-[11px]"
          >
            {{ link.label }}
          </RouterLink>
        </div>
      </div>

      <!-- ornament -->
      <div class="mt-28 flex flex-col items-center gap-4">
        <div class="w-px h-14 bg-white/20"></div>
        <p class="editorial-label-light text-ink-400 text-[10px]">Maison Eden &middot; Atelier since 1962</p>
      </div>
    </div>
  </section>
</template>
