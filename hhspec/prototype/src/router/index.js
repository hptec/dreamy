import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue'), meta: { heroHeader: true } },

  // Shop / category listings
  { path: '/wedding-dresses', name: 'wedding', component: () => import('../views/WeddingDresses.vue'), meta: { category: 'wedding' } },
  { path: '/evening-dresses', name: 'evening', component: () => import('../views/EveningDresses.vue'), meta: { category: 'evening' } },
  { path: '/bridesmaid-dresses', name: 'bridesmaid', component: () => import('../views/BridesmaidDresses.vue'), meta: { category: 'bridesmaid' } },
  { path: '/special-occasions', name: 'special-occasions', component: () => import('../views/SpecialOccasions.vue') },
  { path: '/accessories', name: 'accessories', component: () => import('../views/Accessories.vue'), meta: { category: 'accessories' } },
  { path: '/search', name: 'search', component: () => import('../views/SearchResults.vue') },
  { path: '/products/:slug', name: 'product', component: () => import('../views/ProductDetail.vue') },

  // Cart & checkout
  { path: '/cart', name: 'cart', component: () => import('../views/Cart.vue') },
  { path: '/checkout/address', name: 'checkout-address', component: () => import('../views/CheckoutAddress.vue'), meta: { checkout: true } },
  { path: '/checkout/payment', name: 'checkout-payment', component: () => import('../views/CheckoutPayment.vue'), meta: { checkout: true } },
  { path: '/checkout/review', name: 'checkout-review', component: () => import('../views/CheckoutReview.vue'), meta: { checkout: true } },
  { path: '/checkout/success', name: 'order-confirmation', component: () => import('../views/OrderConfirmation.vue'), meta: { checkout: true } },

  // Account
  { path: '/account/auth', name: 'auth', component: () => import('../views/Auth.vue') },
  { path: '/account', name: 'account', component: () => import('../views/AccountDashboard.vue'), meta: { account: true } },
  { path: '/account/profile', name: 'account-profile', component: () => import('../views/AccountProfile.vue'), meta: { account: true } },
  { path: '/account/orders', name: 'account-orders', component: () => import('../views/AccountOrders.vue'), meta: { account: true } },
  { path: '/account/orders/:id', name: 'order-detail', component: () => import('../views/OrderDetail.vue'), meta: { account: true } },
  { path: '/account/wishlist', name: 'wishlist', component: () => import('../views/Wishlist.vue'), meta: { account: true } },
  { path: '/account/addresses', name: 'addresses', component: () => import('../views/Addresses.vue'), meta: { account: true } },
  { path: '/account/settings', name: 'account-settings', component: () => import('../views/AccountSettings.vue'), meta: { account: true } },

  // Content / brand
  { path: '/about', name: 'about', component: () => import('../views/About.vue') },
  { path: '/atelier', name: 'atelier', component: () => import('../views/Atelier.vue') },
  { path: '/size-guide', name: 'size-guide', component: () => import('../views/SizeGuide.vue') },
  { path: '/shipping-returns', name: 'shipping-returns', component: () => import('../views/ShippingReturns.vue') },
  { path: '/faq', name: 'faq', component: () => import('../views/Faq.vue') },
  { path: '/contact', name: 'contact', component: () => import('../views/Contact.vue') },
  { path: '/style-gallery', name: 'style-gallery', component: () => import('../views/StyleGallery.vue') },
  { path: '/lookbook/:slug?', name: 'lookbook', component: () => import('../views/Lookbook.vue') },

  // Status
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFound.vue') },
]

const router = createRouter({
  // hash history for the portable static build (works from any path / file://, no SPA-fallback config);
  // clean history URLs during local development
  history: import.meta.env.PROD ? createWebHashHistory() : createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
})

export default router
