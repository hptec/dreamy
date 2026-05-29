<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { FOOTER_NAV } from '../data/nav'
import { LOCALES, setLocale } from '../i18n'
import { useCurrencyStore } from '../stores/currency'
import { useUiStore } from '../stores/ui'

const { t, locale } = useI18n()
const currency = useCurrencyStore()
const ui = useUiStore()
const email = ref('')
const subscribed = ref(false)

function subscribe() {
  if (!email.value) return
  subscribed.value = true
  ui.pushToast(t('newsletter.success'))
  email.value = ''
}
function changeLocale(code) { setLocale(code); locale.value = code }
</script>

<template>
  <footer class="bg-ink-950 text-white mt-auto">
    <!-- newsletter band -->
    <div class="border-b border-white/10">
      <div class="container-editorial section-pad !py-14 grid lg:grid-cols-2 gap-10 items-center">
        <div>
          <p class="editorial-label-light mb-3 text-champagne-400">{{ t('footer.newsletterTitle') }}</p>
          <h3 class="font-serif text-3xl sm:text-4xl text-white max-w-md leading-tight">{{ t('newsletter.title') }}</h3>
          <p class="text-ink-300 mt-3 text-sm max-w-md">{{ t('footer.newsletterDesc') }}</p>
        </div>
        <form class="flex flex-col sm:flex-row gap-3 lg:justify-end" @submit.prevent="subscribe">
          <input v-model="email" type="email" required :placeholder="t('footer.emailPlaceholder')"
            class="bg-transparent border border-white/30 text-white placeholder:text-ink-400 px-4 py-3.5 text-sm sm:w-72 focus:outline-none focus:border-champagne-400 transition-colors" />
          <button type="submit" class="btn-champagne">{{ t('footer.subscribe') }}</button>
        </form>
      </div>
    </div>

    <!-- columns -->
    <div class="container-editorial py-16 grid grid-cols-2 lg:grid-cols-5 gap-10">
      <div class="col-span-2 lg:col-span-2">
        <RouterLink to="/" class="font-serif text-2xl">Maison Eden</RouterLink>
        <p class="text-ink-400 text-sm mt-4 max-w-xs leading-relaxed">{{ t('brand.tagline') }}.</p>
        <div class="flex gap-3 mt-6">
          <a v-for="s in ['Instagram','Pinterest','TikTok','YouTube']" :key="s" href="#" :aria-label="s"
            class="w-9 h-9 grid place-items-center border border-white/20 hover:border-champagne-400 hover:text-champagne-400 transition-colors text-[10px] uppercase">{{ s[0] }}</a>
        </div>
      </div>

      <div>
        <h4 class="editorial-label-light text-champagne-400 mb-4">{{ t('footer.shop') }}</h4>
        <ul class="space-y-2.5">
          <li v-for="l in FOOTER_NAV.shop" :key="l.to + l.label"><RouterLink :to="l.to" class="text-sm text-ink-300 hover:text-white transition-colors">{{ l.label }}</RouterLink></li>
        </ul>
      </div>
      <div>
        <h4 class="editorial-label-light text-champagne-400 mb-4">{{ t('footer.customerCare') }}</h4>
        <ul class="space-y-2.5">
          <li v-for="l in FOOTER_NAV.care" :key="l.to + l.label"><RouterLink :to="l.to" class="text-sm text-ink-300 hover:text-white transition-colors">{{ l.label }}</RouterLink></li>
        </ul>
      </div>
      <div>
        <h4 class="editorial-label-light text-champagne-400 mb-4">{{ t('footer.aboutCol') }}</h4>
        <ul class="space-y-2.5">
          <li v-for="l in FOOTER_NAV.about" :key="l.to + l.label"><RouterLink :to="l.to" class="text-sm text-ink-300 hover:text-white transition-colors">{{ l.label }}</RouterLink></li>
        </ul>
      </div>
    </div>

    <!-- bottom bar -->
    <div class="border-t border-white/10">
      <div class="container-editorial py-6 flex flex-col md:flex-row items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <button @click="ui.openRegion()" class="editorial-label-light text-ink-300 hover:text-white inline-flex items-center gap-2">
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3a15 15 0 010 18M12 3a15 15 0 000 18" /></svg>
            {{ t('footer.region') }}
          </button>
          <select :value="currency.code" @change="currency.setCurrency($event.target.value)" class="bg-transparent border border-white/20 text-ink-300 text-[11px] px-2 py-1 focus:outline-none">
            <option v-for="c in currency.list" :key="c.code" :value="c.code" class="text-ink-900">{{ c.code }}</option>
          </select>
          <select :value="locale" @change="changeLocale($event.target.value)" class="bg-transparent border border-white/20 text-ink-300 text-[11px] px-2 py-1 focus:outline-none">
            <option v-for="l in LOCALES" :key="l.code" :value="l.code" class="text-ink-900">{{ l.label }}</option>
          </select>
        </div>
        <div class="flex items-center gap-5 editorial-label-light text-ink-400">
          <RouterLink to="/shipping-returns" class="hover:text-white">{{ t('footer.terms') }}</RouterLink>
          <RouterLink to="/shipping-returns" class="hover:text-white">{{ t('footer.privacy') }}</RouterLink>
          <button @click="ui.cookieVisible = true" class="hover:text-white uppercase">{{ t('footer.cookies') }}</button>
        </div>
        <p class="editorial-label-light text-ink-500 text-[10px]">© 2026 Maison Eden. {{ t('footer.rights') }}</p>
      </div>
    </div>
  </footer>
</template>
