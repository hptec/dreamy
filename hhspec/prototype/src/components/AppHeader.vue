<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Menu, MenuButton, MenuItems, MenuItem } from '@headlessui/vue'
import { MAIN_NAV, MEGA } from '../data/nav'
import { LOCALES, setLocale } from '../i18n'
import { useCurrencyStore } from '../stores/currency'
import { useWishlistStore } from '../stores/wishlist'
import { useCartStore } from '../stores/cart'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import MegaMenu from './MegaMenu.vue'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const currency = useCurrencyStore()
const wishlist = useWishlistStore()
const cart = useCartStore()
const auth = useAuthStore()
const ui = useUiStore()

const scrolled = ref(false)
const activeMega = ref(null)
const mobileOpen = ref(false)
const announceOpen = ref(true)

function onScroll() { scrolled.value = window.scrollY > 12 }
onMounted(() => { onScroll(); window.addEventListener('scroll', onScroll, { passive: true }) })
onUnmounted(() => window.removeEventListener('scroll', onScroll))
watch(() => route.fullPath, () => { activeMega.value = null; mobileOpen.value = false })

const isTransparent = computed(() => route.meta.heroHeader && !scrolled.value && !activeMega.value)
const barClass = computed(() =>
  isTransparent.value ? 'bg-transparent text-white' : 'bg-white/95 backdrop-blur-md text-ink-900 border-b border-ink-100',
)
const linkColor = computed(() => (isTransparent.value ? 'text-white/90 hover:text-white' : 'text-ink-700 hover:text-ink-950'))

function enterMega(item) { activeMega.value = item.mega ? item.id : null }
function accountTo() { return auth.isAuthenticated ? '/account' : '/account/auth' }
function changeLocale(code) { setLocale(code); locale.value = code }
</script>

<template>
  <div>
    <!-- Announcement bar -->
    <transition name="fade">
      <div v-if="announceOpen" class="bg-ink-950 text-white">
        <div class="container-editorial flex items-center justify-center gap-4 py-2.5 relative">
          <p class="editorial-label-light text-[10px] text-center text-white/80">{{ t('header.announcement') }}</p>
          <button class="absolute right-4 text-white/50 hover:text-white" @click="announceOpen = false" aria-label="Dismiss">
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg>
          </button>
        </div>
      </div>
    </transition>

    <!-- Sticky header -->
    <header class="sticky top-0 z-40" @mouseleave="activeMega = null">
      <div :class="barClass" class="transition-colors duration-300">
        <div class="container-editorial">
          <div class="h-20 grid grid-cols-[1fr_auto_1fr] items-center">
            <!-- left: desktop nav / mobile hamburger -->
            <div class="flex items-center">
              <button class="lg:hidden -ml-1 p-2" @click="mobileOpen = true" aria-label="Menu">
                <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M3 6h18M3 12h18M3 18h18" /></svg>
              </button>
              <nav class="hidden lg:flex items-center gap-7">
                <div v-for="item in MAIN_NAV" :key="item.id" @mouseenter="enterMega(item)">
                  <RouterLink :to="item.to" class="editorial-label text-[11px] transition-colors duration-200" :class="linkColor">
                    {{ t(item.labelKey) }}
                  </RouterLink>
                </div>
              </nav>
            </div>

            <!-- center: logo -->
            <RouterLink to="/" class="text-center select-none" @mouseenter="activeMega = null">
              <span class="font-serif tracking-wide leading-none block" :class="isTransparent ? 'text-white' : 'text-ink-950'" style="font-size:1.55rem">Maison&nbsp;Eden</span>
              <span class="editorial-label block mt-0.5 text-[8px]" :class="isTransparent ? 'text-white/60' : 'text-ink-400'">Couture Atelier</span>
            </RouterLink>

            <!-- right: utilities -->
            <div class="flex items-center justify-end gap-1 sm:gap-2.5" :class="linkColor">
              <!-- currency -->
              <Menu as="div" class="relative hidden sm:block">
                <MenuButton class="editorial-label text-[10px] px-1.5 py-1 inline-flex items-center gap-1">
                  {{ currency.code }}
                  <svg class="w-3 h-3 opacity-60" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
                </MenuButton>
                <MenuItems class="absolute right-0 mt-2 w-44 bg-white text-ink-900 shadow-lg ring-1 ring-ink-900/5 py-1 focus:outline-none z-50">
                  <MenuItem v-for="c in currency.list" :key="c.code" v-slot="{ active }">
                    <button @click="currency.setCurrency(c.code)" :class="[active ? 'bg-ink-50' : '', 'w-full text-left px-4 py-2 text-xs flex justify-between']">
                      <span>{{ c.label }}</span><span class="text-ink-400">{{ c.symbol }} {{ c.code }}</span>
                    </button>
                  </MenuItem>
                </MenuItems>
              </Menu>

              <!-- language -->
              <Menu as="div" class="relative hidden sm:block">
                <MenuButton class="editorial-label text-[10px] px-1.5 py-1 inline-flex items-center gap-1 uppercase">
                  {{ locale }}
                  <svg class="w-3 h-3 opacity-60" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
                </MenuButton>
                <MenuItems class="absolute right-0 mt-2 w-36 bg-white text-ink-900 shadow-lg ring-1 ring-ink-900/5 py-1 focus:outline-none z-50">
                  <MenuItem v-for="l in LOCALES" :key="l.code" v-slot="{ active }">
                    <button @click="changeLocale(l.code)" :class="[active ? 'bg-ink-50' : '', 'w-full text-left px-4 py-2 text-xs']">{{ l.label }}</button>
                  </MenuItem>
                </MenuItems>
              </Menu>

              <button class="p-2" @click="ui.openSearch()" aria-label="Search">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z" /></svg>
              </button>
              <RouterLink :to="accountTo()" class="p-2 hidden sm:block" aria-label="Account">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0" /></svg>
              </RouterLink>
              <RouterLink to="/account/wishlist" class="p-2 relative" aria-label="Wishlist">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" /></svg>
                <span v-if="wishlist.count" class="absolute top-0.5 right-0.5 bg-champagne-500 text-white text-[9px] w-4 h-4 grid place-items-center rounded-full tabular-nums">{{ wishlist.count }}</span>
              </RouterLink>
              <button class="p-2 relative" @click="ui.openCart()" aria-label="Bag">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 10.5V6a3.75 3.75 0 10-7.5 0v4.5m11.356-1.993l1.263 12A1.125 1.125 0 0119.747 21H4.253a1.125 1.125 0 01-1.122-1.243l1.263-12A1.125 1.125 0 015.513 6.75h12.974c.576 0 1.059.435 1.119 1.007z" /></svg>
                <span v-if="cart.count" class="absolute top-0.5 right-0.5 bg-ink-950 text-white text-[9px] w-4 h-4 grid place-items-center rounded-full tabular-nums">{{ cart.count }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- mega menu -->
        <transition name="mega">
          <div v-if="activeMega && MEGA[activeMega]" class="absolute inset-x-0 top-full">
            <MegaMenu :data="MEGA[activeMega]" @navigate="activeMega = null" />
          </div>
        </transition>
      </div>
    </header>

    <!-- mobile drawer -->
    <transition name="fade">
      <div v-if="mobileOpen" class="fixed inset-0 z-50 lg:hidden">
        <div class="absolute inset-0 bg-ink-950/40" @click="mobileOpen = false"></div>
        <div class="absolute left-0 top-0 bottom-0 w-[84%] max-w-sm bg-white flex flex-col animate-[fadeUp_0.3s]">
          <div class="flex items-center justify-between p-5 border-b border-ink-100">
            <span class="font-serif text-xl">Maison Eden</span>
            <button @click="mobileOpen = false" aria-label="Close"><svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
          </div>
          <nav class="flex-1 overflow-y-auto p-5 space-y-1">
            <RouterLink v-for="item in MAIN_NAV" :key="item.id" :to="item.to" class="block py-3 font-serif text-2xl text-ink-900 border-b border-ink-50" @click="mobileOpen = false">{{ t(item.labelKey) }}</RouterLink>
            <RouterLink to="/about" class="block py-3 text-sm text-ink-600" @click="mobileOpen = false">{{ t('nav.about') }}</RouterLink>
            <RouterLink to="/atelier" class="block py-3 text-sm text-ink-600" @click="mobileOpen = false">{{ t('nav.atelier') }}</RouterLink>
            <RouterLink :to="accountTo()" class="block py-3 text-sm text-ink-600" @click="mobileOpen = false">{{ t('header.account') }}</RouterLink>
          </nav>
          <div class="p-5 border-t border-ink-100 flex gap-2">
            <select class="field !py-2 text-xs flex-1" :value="currency.code" @change="currency.setCurrency($event.target.value)">
              <option v-for="c in currency.list" :key="c.code" :value="c.code">{{ c.code }} {{ c.symbol }}</option>
            </select>
            <select class="field !py-2 text-xs flex-1" :value="locale" @change="changeLocale($event.target.value)">
              <option v-for="l in LOCALES" :key="l.code" :value="l.code">{{ l.label }}</option>
            </select>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.mega-enter-active, .mega-leave-active { transition: opacity 0.25s ease, transform 0.25s cubic-bezier(0.22,1,0.36,1); }
.mega-enter-from, .mega-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
