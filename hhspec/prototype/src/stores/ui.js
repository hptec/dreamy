import { defineStore } from 'pinia'

let _toastId = 0

export const useUiStore = defineStore('ui', {
  state: () => ({
    cartDrawerOpen: false,
    searchOpen: false,
    regionModalOpen: false,
    newsletterOpen: false,
    cookieVisible: false,
    megaMenu: null, // active mega-menu category id or null
    quickViewProduct: null, // product object or null
    toasts: [],
    recentlyViewed: [], // product ids
  }),
  actions: {
    initOnLoad() {
      try {
        this.recentlyViewed = JSON.parse(localStorage.getItem('me_recent') || '[]')
      } catch (e) {
        this.recentlyViewed = []
      }
      // cookie banner
      if (typeof localStorage !== 'undefined' && !localStorage.getItem('me_cookie')) {
        setTimeout(() => { this.cookieVisible = true }, 1200)
      }
      // region selector on first visit
      if (typeof localStorage !== 'undefined' && !localStorage.getItem('me_region')) {
        setTimeout(() => { this.regionModalOpen = true }, 600)
      } else if (typeof localStorage !== 'undefined' && !localStorage.getItem('me_newsletter')) {
        // newsletter popup later, only if region already chosen
        setTimeout(() => { this.newsletterOpen = true }, 9000)
      }
    },
    openCart() { this.cartDrawerOpen = true },
    closeCart() { this.cartDrawerOpen = false },
    openSearch() { this.searchOpen = true },
    closeSearch() { this.searchOpen = false },
    openRegion() { this.regionModalOpen = true },
    closeRegion() {
      this.regionModalOpen = false
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_region', '1')
    },
    closeNewsletter() {
      this.newsletterOpen = false
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_newsletter', '1')
    },
    acceptCookie() {
      this.cookieVisible = false
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_cookie', 'accepted')
    },
    rejectCookie() {
      this.cookieVisible = false
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_cookie', 'rejected')
    },
    setMegaMenu(id) { this.megaMenu = id },
    openQuickView(product) { this.quickViewProduct = product },
    closeQuickView() { this.quickViewProduct = null },
    pushToast(message, type = 'success') {
      const id = ++_toastId
      this.toasts.push({ id, message, type })
      setTimeout(() => this.dismissToast(id), 3200)
    },
    dismissToast(id) {
      this.toasts = this.toasts.filter((t) => t.id !== id)
    },
    addRecentlyViewed(productId) {
      this.recentlyViewed = [productId, ...this.recentlyViewed.filter((x) => x !== productId)].slice(0, 8)
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_recent', JSON.stringify(this.recentlyViewed))
    },
  },
})
