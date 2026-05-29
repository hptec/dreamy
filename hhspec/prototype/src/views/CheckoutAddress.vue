<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { SAMPLE_ADDRESSES } from '../data/catalog'
import CheckoutStepper from '../components/CheckoutStepper.vue'
import CheckoutSummary from '../components/CheckoutSummary.vue'

const router = useRouter()
const { t } = useI18n()
const auth = useAuthStore()
const cart = useCartStore()

const STORAGE_KEY = 'me_checkout'

function loadState() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') } catch (e) { return {} }
}

const saved = loadState()

const email = ref(saved.email || (auth.isAuthenticated ? auth.user.email : ''))
const subscribe = ref(saved.subscribe !== false)
const createAccount = ref(!!saved.createAccount)

const form = reactive({
  firstName: saved.address?.firstName || (auth.isAuthenticated ? auth.user.firstName : ''),
  lastName: saved.address?.lastName || (auth.isAuthenticated ? auth.user.lastName : ''),
  street1: saved.address?.street1 || '',
  street2: saved.address?.street2 || '',
  city: saved.address?.city || '',
  state: saved.address?.state || '',
  postalCode: saved.address?.postalCode || '',
  country: saved.address?.country || 'United States',
  phone: saved.address?.phone || '',
})

const COUNTRIES = ['United States', 'Canada', 'United Kingdom', 'France', 'Germany', 'Spain', 'Italy', 'Australia', 'Japan']
const US_STATES = ['Alabama','Alaska','Arizona','Arkansas','California','Colorado','Connecticut','Delaware','Florida','Georgia','Hawaii','Idaho','Illinois','Indiana','Iowa','Kansas','Kentucky','Louisiana','Maine','Maryland','Massachusetts','Michigan','Minnesota','Mississippi','Missouri','Montana','Nebraska','Nevada','New Hampshire','New Jersey','New Mexico','New York','North Carolina','North Dakota','Ohio','Oklahoma','Oregon','Pennsylvania','Rhode Island','South Carolina','South Dakota','Tennessee','Texas','Utah','Vermont','Virginia','Washington','West Virginia','Wisconsin','Wyoming']

const activeSavedId = ref(null)

function fillFromSaved(addr) {
  activeSavedId.value = addr.id
  form.firstName = addr.firstName
  form.lastName = addr.lastName
  form.street1 = addr.street1
  form.street2 = addr.street2 || ''
  form.city = addr.city
  form.state = addr.state
  form.postalCode = addr.postalCode
  form.country = addr.country
  form.phone = addr.phone
}

function persist() {
  const state = loadState()
  state.email = email.value
  state.subscribe = subscribe.value
  state.createAccount = createAccount.value
  state.address = { ...form }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

function submit() {
  persist()
  router.push('/checkout/payment')
}

onMounted(() => {
  if (cart.isEmpty) router.replace('/cart')
})
</script>

<template>
  <div class="bg-canvas min-h-screen">
    <CheckoutStepper :current="1" />

    <div class="container-editorial pt-8 sm:pt-10">
      <p class="editorial-label text-champagne-600 mb-2">Step 01 · Where it's going</p>
      <h1 class="font-serif text-4xl sm:text-5xl text-ink-950">Shipping Details</h1>
      <p class="text-ink-500 mt-3 max-w-xl text-sm">
        Each gown is hand-finished after your order and shipped insured to your door. Tell us where to deliver.
      </p>
    </div>

    <div class="container-editorial pt-10 pb-20 grid lg:grid-cols-[1fr_400px] gap-10 lg:gap-14 items-start">
      <form class="space-y-12 bg-white border border-ink-100 p-6 sm:p-10" @submit.prevent="submit">
        <!-- saved addresses for authed users -->
        <section v-if="auth.isAuthenticated" class="space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="font-serif text-xl">Saved Addresses</h2>
            <RouterLink
              to="/account/addresses"
              class="editorial-label text-ink-500 hover:text-ink-950 text-[10px]"
            >{{ t('common.edit') }}</RouterLink>
          </div>
          <div class="grid sm:grid-cols-2 gap-3">
            <button
              v-for="a in SAMPLE_ADDRESSES"
              :key="a.id"
              type="button"
              class="text-left border p-4 transition-colors"
              :class="activeSavedId === a.id ? 'border-ink-950 bg-ink-50' : 'border-ink-200 hover:border-ink-700'"
              @click="fillFromSaved(a)"
            >
              <p class="editorial-label text-champagne-700 text-[9px] mb-1 capitalize">
                {{ a.label }}<span v-if="a.isDefault" class="text-ink-400"> · Default</span>
              </p>
              <p class="text-sm text-ink-900">{{ a.firstName }} {{ a.lastName }}</p>
              <p class="text-xs text-ink-500 mt-0.5">
                {{ a.street1 }}<template v-if="a.street2">, {{ a.street2 }}</template>
              </p>
              <p class="text-xs text-ink-500">{{ a.city }}, {{ a.state }} {{ a.postalCode }}</p>
            </button>
          </div>
          <div class="hairline pt-2"></div>
        </section>

        <!-- contact -->
        <section>
          <div class="flex items-center justify-between mb-5">
            <h2 class="font-serif text-2xl">{{ t('checkout.contact') }}</h2>
            <RouterLink
              v-if="!auth.isAuthenticated"
              to="/account/auth"
              class="editorial-label text-ink-500 hover:text-ink-950 text-[10px]"
            >{{ t('auth.signIn') }}</RouterLink>
          </div>
          <div>
            <label for="email" class="field-label">{{ t('checkout.email') }}</label>
            <input
              id="email"
              v-model="email"
              type="email"
              required
              class="field"
              placeholder="you@example.com"
              autocomplete="email"
            />
            <label
              v-if="!auth.isAuthenticated"
              class="mt-3 inline-flex items-start gap-2 text-xs text-ink-600 cursor-pointer"
            >
              <input v-model="subscribe" type="checkbox" class="mt-0.5 accent-ink-950" />
              <span>Email me Maison Eden private previews and atelier stories.</span>
            </label>
          </div>
        </section>

        <!-- shipping address -->
        <section>
          <h2 class="font-serif text-2xl mb-5">{{ t('checkout.shippingAddress') }}</h2>
          <div class="grid sm:grid-cols-2 gap-5">
            <div>
              <label class="field-label" for="firstName">{{ t('checkout.firstName') }}</label>
              <input id="firstName" v-model="form.firstName" type="text" required class="field" autocomplete="given-name" />
            </div>
            <div>
              <label class="field-label" for="lastName">{{ t('checkout.lastName') }}</label>
              <input id="lastName" v-model="form.lastName" type="text" required class="field" autocomplete="family-name" />
            </div>
            <div class="sm:col-span-2">
              <label class="field-label" for="street1">{{ t('checkout.address1') }}</label>
              <input id="street1" v-model="form.street1" type="text" required class="field" autocomplete="address-line1" />
            </div>
            <div class="sm:col-span-2">
              <label class="field-label" for="street2">{{ t('checkout.address2') }}</label>
              <input id="street2" v-model="form.street2" type="text" class="field" autocomplete="address-line2" />
            </div>
            <div>
              <label class="field-label" for="city">{{ t('checkout.city') }}</label>
              <input id="city" v-model="form.city" type="text" required class="field" autocomplete="address-level2" />
            </div>
            <div>
              <label class="field-label" for="state">{{ t('checkout.state') }}</label>
              <select id="state" v-model="form.state" required class="field" autocomplete="address-level1">
                <option value="" disabled>Select state</option>
                <option v-for="s in US_STATES" :key="s" :value="s">{{ s }}</option>
              </select>
            </div>
            <div>
              <label class="field-label" for="postalCode">{{ t('checkout.postalCode') }}</label>
              <input id="postalCode" v-model="form.postalCode" type="text" required class="field tabular-nums" autocomplete="postal-code" />
            </div>
            <div>
              <label class="field-label" for="country">{{ t('checkout.country') }}</label>
              <select id="country" v-model="form.country" required class="field" autocomplete="country-name">
                <option v-for="c in COUNTRIES" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
            <div class="sm:col-span-2">
              <label class="field-label" for="phone">{{ t('checkout.phone') }}</label>
              <input
                id="phone"
                v-model="form.phone"
                type="tel"
                required
                class="field"
                placeholder="+1 (555) 000-0000"
                autocomplete="tel"
              />
              <p class="text-[10px] text-ink-400 mt-2">For carrier dispatch notifications only.</p>
            </div>
          </div>
        </section>

        <!-- guest / create account -->
        <section v-if="!auth.isAuthenticated" class="border border-ink-100 bg-ink-50/40 p-5 sm:p-6">
          <p class="editorial-label text-champagne-700 text-[9px] mb-2">{{ t('checkout.guestCheckout') }}</p>
          <p class="text-sm text-ink-600 mb-4 leading-relaxed">
            Continue as guest, or save your details for faster checkout, order tracking, and access to your wishlist anywhere.
          </p>
          <label class="inline-flex items-start gap-2 text-sm text-ink-700 cursor-pointer">
            <input v-model="createAccount" type="checkbox" class="mt-0.5 accent-ink-950" />
            <span>{{ t('checkout.createAccount') }}</span>
          </label>
        </section>

        <!-- actions -->
        <div class="flex items-center justify-between border-t border-ink-100 pt-8">
          <RouterLink
            to="/cart"
            class="editorial-label text-ink-500 hover:text-ink-950 inline-flex items-center gap-2 text-[10px] transition-colors"
          >
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" d="M15 19l-7-7 7-7" />
            </svg>
            Return to Bag
          </RouterLink>
          <button type="submit" class="btn-ink">{{ t('checkout.continueToShipping') }}</button>
        </div>
      </form>

      <aside class="lg:sticky lg:top-28">
        <CheckoutSummary />
      </aside>
    </div>
  </div>
</template>
