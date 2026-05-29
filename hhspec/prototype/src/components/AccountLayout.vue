<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const auth = useAuthStore()
const ui = useUiStore()

onMounted(() => {
  if (!auth.isAuthenticated) auth.signIn()
})

const navItems = computed(() => [
  {
    name: 'account', to: '/account', label: t('account.dashboard'),
    icon: 'M3 12l2-2m0 0l7-7 7 7m-9 2v8a2 2 0 002 2h2a2 2 0 002-2v-4a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 002 2h2a2 2 0 002-2v-8m-9 2h2',
  },
  {
    name: 'account-profile', to: '/account/profile', label: t('account.profile'),
    icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z',
  },
  {
    name: 'account-orders', to: '/account/orders', label: t('account.orders'),
    icon: 'M3.75 6h16.5l-1.5 13.5a1.5 1.5 0 01-1.49 1.35H6.74a1.5 1.5 0 01-1.49-1.35L3.75 6zM9 9.75V7.5a3 3 0 116 0v2.25',
  },
  {
    name: 'wishlist', to: '/account/wishlist', label: t('account.wishlist'),
    icon: 'M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z',
  },
  {
    name: 'addresses', to: '/account/addresses', label: t('account.addresses'),
    icon: 'M15 10.5a3 3 0 11-6 0 3 3 0 016 0z M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z',
  },
  {
    name: 'account-settings', to: '/account/settings', label: t('account.settings'),
    icon: 'M10.343 3.94c.09-.542.56-.94 1.11-.94h1.093c.55 0 1.02.398 1.11.94l.149.894c.07.424.384.764.78.93.398.164.855.142 1.205-.108l.737-.527a1.125 1.125 0 011.45.12l.773.774c.39.389.44 1.002.12 1.45l-.527.737c-.25.35-.272.806-.107 1.204.165.397.505.71.93.78l.893.15c.543.09.94.56.94 1.109v1.094c0 .55-.397 1.02-.94 1.11l-.893.149c-.425.07-.765.383-.93.78-.165.398-.143.854.107 1.204l.527.738c.32.447.269 1.06-.12 1.45l-.774.773a1.125 1.125 0 01-1.449.12l-.738-.527c-.35-.25-.806-.272-1.203-.107-.397.165-.71.505-.781.929l-.149.894c-.09.542-.56.94-1.11.94h-1.094c-.55 0-1.019-.398-1.11-.94l-.148-.894c-.071-.424-.384-.764-.781-.93-.398-.164-.854-.142-1.204.108l-.738.527c-.447.32-1.06.269-1.45-.12l-.773-.774a1.125 1.125 0 01-.12-1.45l.527-.737c.25-.35.272-.806.108-1.204-.165-.397-.506-.71-.93-.78l-.894-.15c-.542-.09-.94-.56-.94-1.109v-1.094c0-.55.398-1.02.94-1.11l.894-.149c.424-.07.765-.383.93-.78.165-.398.143-.854-.107-1.204l-.527-.738a1.125 1.125 0 01.12-1.45l.773-.773a1.125 1.125 0 011.45-.12l.737.527c.35.25.807.272 1.204.107.397-.165.71-.505.78-.929l.15-.894z M15 12a3 3 0 11-6 0 3 3 0 016 0z',
  },
])

const greetingFirst = computed(() => auth.user?.firstName || 'Isabella')

function handleSignOut() {
  auth.signOut()
  ui.pushToast(t('toast.signedOut'), 'muted')
  router.push('/')
}
</script>

<template>
  <div class="bg-canvas">
    <!-- Editorial header band -->
    <section class="bg-ink-950 text-white">
      <div class="container-editorial py-14 sm:py-16 grid sm:grid-cols-[1fr_auto] gap-8 items-end">
        <div>
          <p class="editorial-label-light text-champagne-300 mb-3">Maison Eden</p>
          <h1 class="font-serif text-white text-4xl sm:text-5xl lg:text-6xl leading-none">
            {{ t('account.welcome') }}, <span class="italic text-champagne-300">{{ greetingFirst }}</span>
          </h1>
          <p class="text-ink-300 mt-4 max-w-md text-sm font-light leading-relaxed">
            Your private atelier — orders, fittings and the gowns that have caught your eye.
          </p>
        </div>
        <div class="flex items-center gap-5">
          <div class="hidden sm:block text-right">
            <p class="editorial-label-light text-ink-400 text-[10px]">{{ auth.user?.firstName }} {{ auth.user?.lastName }}</p>
            <p class="text-ink-200 text-sm mt-0.5">{{ auth.user?.email }}</p>
          </div>
          <div class="w-16 h-16 grid place-items-center border border-champagne-400/40 text-champagne-300 font-serif text-2xl tracking-wider">
            {{ auth.initials || 'IM' }}
          </div>
        </div>
      </div>
    </section>

    <!-- Layout: sidebar + content -->
    <div class="container-editorial pt-10 pb-24">
      <div class="grid lg:grid-cols-[260px_1fr] gap-10 lg:gap-16">
        <!-- Sidebar nav -->
        <aside class="lg:sticky lg:top-28 lg:self-start">
          <p class="editorial-label text-ink-400 mb-5">{{ t('account.dashboard') }}</p>
          <nav class="border-t border-ink-200">
            <RouterLink
              v-for="item in navItems"
              :key="item.name"
              :to="item.to"
              class="group flex items-center gap-3 py-4 border-b border-ink-100 text-sm transition-colors"
              :class="$route.name === item.name ? 'text-ink-950' : 'text-ink-500 hover:text-ink-950'"
            >
              <span
                class="w-1 h-5 -ml-1 transition-colors"
                :class="$route.name === item.name ? 'bg-champagne-500' : 'bg-transparent'"
              ></span>
              <svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3">
                <path stroke-linecap="round" stroke-linejoin="round" :d="item.icon" />
              </svg>
              <span class="flex-1">{{ item.label }}</span>
              <svg
                v-if="$route.name === item.name"
                class="w-3.5 h-3.5 text-champagne-600"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
              >
                <path stroke-linecap="round" d="M9 5l7 7-7 7" />
              </svg>
            </RouterLink>
          </nav>
          <button
            type="button"
            class="mt-8 inline-flex items-center gap-2 editorial-label text-ink-500 hover:text-ink-950 transition-colors text-[11px]"
            @click="handleSignOut"
          >
            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15M12 9l-3 3m0 0l3 3m-3-3h12.75" />
            </svg>
            {{ t('account.signOut') }}
          </button>
        </aside>

        <!-- Main content slot -->
        <main class="min-w-0">
          <slot />
        </main>
      </div>
    </div>
  </div>
</template>
