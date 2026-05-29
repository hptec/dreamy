<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { RadioGroup, RadioGroupOption, TabGroup, TabList, Tab, TabPanels, TabPanel } from '@headlessui/vue'
import { useCartStore } from '../stores/cart'
import { useCurrencyStore } from '../stores/currency'
import { SHIPPING_METHODS } from '../data/catalog'
import CheckoutStepper from '../components/CheckoutStepper.vue'
import CheckoutSummary from '../components/CheckoutSummary.vue'

const router = useRouter()
const { t } = useI18n()
const cart = useCartStore()
const currency = useCurrencyStore()

const STORAGE_KEY = 'me_checkout'

function loadState() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') } catch (e) { return {} }
}

const saved = loadState()

const shippingMethodId = ref(saved.shippingMethodId || 'standard')
const paymentTab = ref(saved.paymentTab ?? 0) // 0 = card, 1 = paypal, 2 = apple

const card = reactive({
  number: saved.card?.number || '4242 4242 4242 4242',
  expiry: saved.card?.expiry || '12 / 28',
  cvc: saved.card?.cvc || '123',
  name: saved.card?.name || (saved.address ? `${saved.address.firstName || ''} ${saved.address.lastName || ''}`.trim() : ''),
})

const billingSameAsShipping = ref(saved.billingSameAsShipping !== false)

const selectedMethod = computed(
  () => SHIPPING_METHODS.find((m) => m.id === shippingMethodId.value) || SHIPPING_METHODS[0],
)

function feeLabel(m) {
  if (cart.subtotal >= 500 && m.id === 'standard') return t('cart.freeShipping')
  if (m.feeUSD === 0) return t('cart.freeShipping')
  return '+ ' + currency.format(m.feeUSD)
}

function persist() {
  const state = loadState()
  state.shippingMethodId = shippingMethodId.value
  state.paymentTab = paymentTab.value
  state.paymentMethod = paymentTab.value === 0 ? 'card' : paymentTab.value === 1 ? 'paypal' : 'apple'
  state.card = { number: card.number, expiry: card.expiry, cvc: card.cvc, name: card.name }
  state.billingSameAsShipping = billingSameAsShipping.value
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

function submit() {
  persist()
  router.push('/checkout/review')
}

onMounted(() => {
  if (cart.isEmpty) { router.replace('/cart'); return }
  if (!saved.email || !saved.address?.street1) { router.replace('/checkout/address') }
})
</script>

<template>
  <div class="bg-canvas min-h-screen">
    <CheckoutStepper :current="2" />

    <div class="container-editorial pt-8 sm:pt-10">
      <p class="editorial-label text-champagne-600 mb-2">Step 02 · How it travels</p>
      <h1 class="font-serif text-4xl sm:text-5xl text-ink-950">Shipping & Payment</h1>
      <p class="text-ink-500 mt-3 max-w-xl text-sm">
        Choose how quickly your gown should travel, and how you'd like to pay.
      </p>
    </div>

    <div class="container-editorial pt-10 pb-20 grid lg:grid-cols-[1fr_400px] gap-10 lg:gap-14 items-start">
      <form class="space-y-12 bg-white border border-ink-100 p-6 sm:p-10" @submit.prevent="submit">
        <!-- shipping method -->
        <section>
          <h2 class="font-serif text-2xl mb-5">{{ t('checkout.shippingMethod') }}</h2>
          <RadioGroup v-model="shippingMethodId" class="space-y-3">
            <RadioGroupOption
              v-for="m in SHIPPING_METHODS"
              :key="m.id"
              :value="m.id"
              v-slot="{ checked }"
              as="template"
            >
              <div
                class="border cursor-pointer px-5 py-4 flex items-center justify-between transition-colors"
                :class="checked ? 'border-ink-950 bg-ink-50/60' : 'border-ink-200 hover:border-ink-400'"
              >
                <span class="flex items-center gap-4">
                  <span
                    class="w-4 h-4 rounded-full border flex items-center justify-center transition-colors"
                    :class="checked ? 'border-ink-950' : 'border-ink-300'"
                  >
                    <span v-if="checked" class="w-2 h-2 rounded-full bg-ink-950"></span>
                  </span>
                  <span class="flex flex-col">
                    <span class="text-sm text-ink-950">{{ m.label }}</span>
                    <span class="text-[11px] text-ink-400 mt-0.5">{{ m.detail }}</span>
                  </span>
                </span>
                <span
                  class="text-xs tabular-nums"
                  :class="(m.feeUSD === 0 || (cart.subtotal >= 500 && m.id === 'standard'))
                    ? 'text-champagne-700 uppercase tracking-label text-[10px]'
                    : 'text-ink-700'"
                >{{ feeLabel(m) }}</span>
              </div>
            </RadioGroupOption>
          </RadioGroup>
        </section>

        <!-- payment -->
        <section>
          <h2 class="font-serif text-2xl mb-5">{{ t('checkout.payment') }}</h2>
          <TabGroup :selected-index="paymentTab" @change="paymentTab = $event">
            <TabList class="flex flex-wrap gap-6 border-b border-ink-200">
              <Tab
                v-for="(tab, i) in ['Credit Card', 'PayPal', 'Apple Pay']"
                :key="i"
                v-slot="{ selected }"
                as="template"
              >
                <button
                  class="pb-3 editorial-label text-[11px] border-b-2 -mb-px transition-colors focus:outline-none"
                  :class="selected ? 'border-ink-950 text-ink-950' : 'border-transparent text-ink-400 hover:text-ink-700'"
                >{{ tab }}</button>
              </Tab>
            </TabList>
            <TabPanels class="pt-7">
              <TabPanel>
                <div class="grid sm:grid-cols-2 gap-5">
                  <div class="sm:col-span-2">
                    <label for="cardNumber" class="field-label">{{ t('checkout.cardNumber') }}</label>
                    <div class="relative">
                      <input
                        id="cardNumber"
                        v-model="card.number"
                        type="text"
                        required
                        inputmode="numeric"
                        class="field tabular-nums tracking-wider pr-28"
                        placeholder="0000 0000 0000 0000"
                        autocomplete="cc-number"
                      />
                      <div class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1">
                        <span class="bg-ink-950 text-white text-[8px] px-1.5 py-0.5 tracking-label">VISA</span>
                        <span class="bg-ink-950 text-white text-[8px] px-1.5 py-0.5 tracking-label">MC</span>
                        <span class="bg-ink-950 text-white text-[8px] px-1.5 py-0.5 tracking-label">AMEX</span>
                      </div>
                    </div>
                  </div>
                  <div>
                    <label for="expiry" class="field-label">{{ t('checkout.expiry') }}</label>
                    <input
                      id="expiry"
                      v-model="card.expiry"
                      type="text"
                      required
                      class="field tabular-nums"
                      placeholder="MM / YY"
                      autocomplete="cc-exp"
                    />
                  </div>
                  <div>
                    <label for="cvc" class="field-label">{{ t('checkout.cvc') }}</label>
                    <input
                      id="cvc"
                      v-model="card.cvc"
                      type="text"
                      required
                      inputmode="numeric"
                      class="field tabular-nums"
                      placeholder="000"
                      autocomplete="cc-csc"
                    />
                  </div>
                  <div class="sm:col-span-2">
                    <label for="nameOnCard" class="field-label">{{ t('checkout.nameOnCard') }}</label>
                    <input
                      id="nameOnCard"
                      v-model="card.name"
                      type="text"
                      required
                      class="field"
                      autocomplete="cc-name"
                    />
                  </div>
                  <div class="sm:col-span-2">
                    <label class="inline-flex items-start gap-2 text-xs text-ink-600 cursor-pointer">
                      <input v-model="billingSameAsShipping" type="checkbox" class="mt-0.5 accent-ink-950" />
                      <span>Billing address same as shipping</span>
                    </label>
                  </div>
                </div>
                <p class="editorial-label text-ink-400 text-[10px] mt-5 inline-flex items-center gap-2">
                  <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="3" y="11" width="18" height="10" rx="1" />
                    <path stroke-linecap="round" d="M7 11V7a5 5 0 0110 0v4" />
                  </svg>
                  Payments are encrypted with 256-bit SSL
                </p>
              </TabPanel>
              <TabPanel>
                <div class="bg-ink-50 border border-ink-100 p-10 text-center">
                  <p class="font-serif text-2xl text-ink-900 mb-2">PayPal</p>
                  <p class="text-sm text-ink-500 max-w-md mx-auto">
                    You will be redirected to PayPal to authorize payment after placing your order.
                  </p>
                </div>
              </TabPanel>
              <TabPanel>
                <div class="bg-ink-950 text-white p-10 text-center">
                  <p class="font-serif text-2xl mb-2">Apple Pay</p>
                  <p class="text-sm text-white/70 max-w-md mx-auto">
                    Authorize with Touch ID or Face ID on a supported device.
                  </p>
                </div>
              </TabPanel>
            </TabPanels>
          </TabGroup>
        </section>

        <!-- actions -->
        <div class="flex items-center justify-between border-t border-ink-100 pt-8">
          <RouterLink
            to="/checkout/address"
            class="editorial-label text-ink-500 hover:text-ink-950 inline-flex items-center gap-2 text-[10px] transition-colors"
          >
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" d="M15 19l-7-7 7-7" />
            </svg>
            Return to Address
          </RouterLink>
          <button type="submit" class="btn-ink">{{ t('checkout.continueToReview') }}</button>
        </div>
      </form>

      <aside class="lg:sticky lg:top-28">
        <CheckoutSummary :shipping-method="selectedMethod" />
      </aside>
    </div>
  </div>
</template>
