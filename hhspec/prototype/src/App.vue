<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
import CartDrawer from './components/CartDrawer.vue'
import SearchOverlay from './components/SearchOverlay.vue'
import QuickViewModal from './components/QuickViewModal.vue'
import RegionModal from './components/RegionModal.vue'
import NewsletterModal from './components/NewsletterModal.vue'
import CookieBanner from './components/CookieBanner.vue'
import LiveChatBubble from './components/LiveChatBubble.vue'
import ToastHost from './components/ToastHost.vue'
import { useUiStore } from './stores/ui'

const ui = useUiStore()
onMounted(() => ui.initOnLoad())
</script>

<template>
  <div class="min-h-screen flex flex-col bg-canvas">
    <AppHeader />
    <main class="flex-1">
      <RouterView v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>
    <AppFooter />

    <!-- Global overlays -->
    <CartDrawer />
    <SearchOverlay />
    <QuickViewModal />
    <RegionModal />
    <NewsletterModal />
    <CookieBanner />
    <LiveChatBubble />
    <ToastHost />
  </div>
</template>

<style>
* { box-sizing: border-box; }
.page-enter-active,
.page-leave-active { transition: opacity 0.35s cubic-bezier(0.22, 1, 0.36, 1); }
.page-enter-from,
.page-leave-to { opacity: 0; }
</style>
