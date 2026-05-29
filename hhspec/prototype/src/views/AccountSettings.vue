<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Switch, Listbox, ListboxButton, ListboxOptions, ListboxOption, TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { STANDARD_SIZES } from '../data/catalog'
import { useAuthStore } from '../stores/auth'
import { useCurrencyStore } from '../stores/currency'
import { useUiStore } from '../stores/ui'
import AccountLayout from '../components/AccountLayout.vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const currency = useCurrencyStore()
const ui = useUiStore()

const LANGUAGES = [
  { id: 'en', label: 'English (US)' },
  { id: 'fr', label: 'Français' },
  { id: 'es', label: 'Español' },
  { id: 'ja', label: '日本語' },
]
const SIZES = STANDARD_SIZES.slice(0, 12)

const prefs = reactive({
  newsletter: auth.user?.newsletterOptIn ?? true,
  textOptIn: false,
  privatePreviews: true,
  currency: currency.code,
  language: 'en',
  preferredSize: auth.user?.preferredSize || 'US 4',
})

const security = reactive({
  current: '',
  next: '',
  confirm: '',
})

const deleteConfirmOpen = ref(false)
const deleteText = ref('')

function savePrefs() {
  currency.setCurrency(prefs.currency)
  auth.updateProfile({
    newsletterOptIn: prefs.newsletter,
    preferredSize: prefs.preferredSize,
  })
  ui.pushToast(t('toast.saved'))
}

function changePassword() {
  if (!security.next || security.next !== security.confirm) {
    ui.pushToast('Passwords do not match', 'muted')
    return
  }
  security.current = ''
  security.next = ''
  security.confirm = ''
  ui.pushToast(t('toast.saved'))
}

function signOutEverywhere() {
  auth.signOut()
  ui.pushToast(t('toast.signedOut'), 'muted')
  router.push('/')
}

function deleteAccount() {
  if (deleteText.value !== 'DELETE') return
  auth.signOut()
  ui.pushToast('Account scheduled for deletion', 'muted')
  deleteConfirmOpen.value = false
  router.push('/')
}

function selectedLang() { return LANGUAGES.find((l) => l.id === prefs.language) || LANGUAGES[0] }
function selectedCurrency() { return currency.list.find((c) => c.code === prefs.currency) || currency.list[0] }
function selectedSize() { return prefs.preferredSize }
</script>

<template>
  <AccountLayout>
    <div class="flex items-end justify-between mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.settings') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">{{ t('account.preferences') }} & {{ t('account.security').toLowerCase() }}</h2>
      </div>
    </div>

    <!-- PREFERENCES -->
    <section class="bg-white border border-ink-200 p-8 lg:p-10 mb-8">
      <div class="flex items-center justify-between mb-8">
        <div>
          <p class="editorial-label text-ink-400 mb-2">01</p>
          <h3 class="font-serif text-xl">{{ t('account.preferences') }}</h3>
        </div>
      </div>

      <!-- Notification toggles -->
      <ul class="divide-y divide-ink-100">
        <li class="flex items-start justify-between gap-6 py-5">
          <div>
            <p class="text-sm text-ink-900">Eden newsletter</p>
            <p class="text-xs text-ink-500 mt-1 max-w-md leading-relaxed">Private previews, atelier stories and members-only events.</p>
          </div>
          <Switch
            v-model="prefs.newsletter"
            :class="prefs.newsletter ? 'bg-ink-950' : 'bg-ink-200'"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center transition-colors focus:outline-none"
          >
            <span class="inline-block h-4 w-4 bg-white transition-transform" :class="prefs.newsletter ? 'translate-x-6' : 'translate-x-1'" />
          </Switch>
        </li>
        <li class="flex items-start justify-between gap-6 py-5">
          <div>
            <p class="text-sm text-ink-900">SMS updates</p>
            <p class="text-xs text-ink-500 mt-1 max-w-md leading-relaxed">Production milestones and shipping updates to your phone.</p>
          </div>
          <Switch
            v-model="prefs.textOptIn"
            :class="prefs.textOptIn ? 'bg-ink-950' : 'bg-ink-200'"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center transition-colors focus:outline-none"
          >
            <span class="inline-block h-4 w-4 bg-white transition-transform" :class="prefs.textOptIn ? 'translate-x-6' : 'translate-x-1'" />
          </Switch>
        </li>
        <li class="flex items-start justify-between gap-6 py-5">
          <div>
            <p class="text-sm text-ink-900">Private previews</p>
            <p class="text-xs text-ink-500 mt-1 max-w-md leading-relaxed">Be first to view trunk show pieces before they reach the floor.</p>
          </div>
          <Switch
            v-model="prefs.privatePreviews"
            :class="prefs.privatePreviews ? 'bg-ink-950' : 'bg-ink-200'"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center transition-colors focus:outline-none"
          >
            <span class="inline-block h-4 w-4 bg-white transition-transform" :class="prefs.privatePreviews ? 'translate-x-6' : 'translate-x-1'" />
          </Switch>
        </li>
      </ul>

      <!-- Region & sizing -->
      <div class="hairline my-8"></div>
      <div class="grid sm:grid-cols-3 gap-5">
        <div>
          <label class="field-label">{{ t('footer.currency') }}</label>
          <Listbox v-model="prefs.currency">
            <div class="relative">
              <ListboxButton class="field text-left inline-flex items-center justify-between">
                <span>{{ selectedCurrency().code }} · {{ selectedCurrency().symbol }}</span>
                <svg class="w-3.5 h-3.5 text-ink-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
              </ListboxButton>
              <ListboxOptions class="absolute z-20 mt-1 w-full bg-white shadow-lg ring-1 ring-ink-900/5 py-1 focus:outline-none">
                <ListboxOption v-for="c in currency.list" :key="c.code" :value="c.code" v-slot="{ active, selected }">
                  <div :class="[active ? 'bg-ink-50' : '', selected ? 'text-ink-950' : 'text-ink-600', 'px-4 py-2.5 text-sm cursor-pointer']">{{ c.code }} · {{ c.symbol }}</div>
                </ListboxOption>
              </ListboxOptions>
            </div>
          </Listbox>
        </div>
        <div>
          <label class="field-label">{{ t('footer.language') }}</label>
          <Listbox v-model="prefs.language">
            <div class="relative">
              <ListboxButton class="field text-left inline-flex items-center justify-between">
                <span>{{ selectedLang().label }}</span>
                <svg class="w-3.5 h-3.5 text-ink-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
              </ListboxButton>
              <ListboxOptions class="absolute z-20 mt-1 w-full bg-white shadow-lg ring-1 ring-ink-900/5 py-1 focus:outline-none">
                <ListboxOption v-for="l in LANGUAGES" :key="l.id" :value="l.id" v-slot="{ active, selected }">
                  <div :class="[active ? 'bg-ink-50' : '', selected ? 'text-ink-950' : 'text-ink-600', 'px-4 py-2.5 text-sm cursor-pointer']">{{ l.label }}</div>
                </ListboxOption>
              </ListboxOptions>
            </div>
          </Listbox>
        </div>
        <div>
          <label class="field-label">Default size</label>
          <Listbox v-model="prefs.preferredSize">
            <div class="relative">
              <ListboxButton class="field text-left inline-flex items-center justify-between">
                <span>{{ selectedSize() }}</span>
                <svg class="w-3.5 h-3.5 text-ink-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
              </ListboxButton>
              <ListboxOptions class="absolute z-20 mt-1 w-full bg-white shadow-lg ring-1 ring-ink-900/5 py-1 focus:outline-none max-h-64 overflow-auto">
                <ListboxOption v-for="s in SIZES" :key="s" :value="s" v-slot="{ active, selected }">
                  <div :class="[active ? 'bg-ink-50' : '', selected ? 'text-ink-950' : 'text-ink-600', 'px-4 py-2.5 text-sm cursor-pointer']">{{ s }}</div>
                </ListboxOption>
              </ListboxOptions>
            </div>
          </Listbox>
        </div>
      </div>

      <div class="flex justify-end mt-8">
        <button type="button" class="btn-ink btn-sm" @click="savePrefs">{{ t('common.save') }} {{ t('account.preferences').toLowerCase() }}</button>
      </div>
    </section>

    <!-- SECURITY -->
    <section class="bg-white border border-ink-200 p-8 lg:p-10 mb-8">
      <div class="flex items-center justify-between mb-8">
        <div>
          <p class="editorial-label text-ink-400 mb-2">02</p>
          <h3 class="font-serif text-xl">{{ t('account.security') }}</h3>
        </div>
        <span class="editorial-label text-ink-400 text-[10px] hidden sm:inline">We never share your data</span>
      </div>

      <form class="grid sm:grid-cols-3 gap-5" @submit.prevent="changePassword">
        <div>
          <label class="field-label">Current password</label>
          <input v-model="security.current" type="password" class="field" placeholder="••••••••" />
        </div>
        <div>
          <label class="field-label">New password</label>
          <input v-model="security.next" type="password" class="field" placeholder="••••••••" />
        </div>
        <div>
          <label class="field-label">{{ t('auth.confirmPassword') }}</label>
          <input v-model="security.confirm" type="password" class="field" placeholder="••••••••" />
        </div>
        <div class="sm:col-span-3 flex justify-end">
          <button type="submit" class="btn-outline btn-sm">Update password</button>
        </div>
      </form>

      <div class="hairline my-8"></div>

      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <p class="text-sm text-ink-900">Sign out of every device</p>
          <p class="text-xs text-ink-500 mt-1">Useful if you used a borrowed phone or tablet.</p>
        </div>
        <button type="button" class="btn-outline btn-sm" @click="signOutEverywhere">Sign out everywhere</button>
      </div>
    </section>

    <!-- DELETE -->
    <section class="bg-white border border-ink-200 p-8 lg:p-10">
      <div class="flex items-center justify-between mb-6">
        <div>
          <p class="editorial-label text-ink-400 mb-2">03</p>
          <h3 class="font-serif text-xl">Close account</h3>
        </div>
      </div>
      <p class="text-sm text-ink-500 max-w-lg mb-6 leading-relaxed">
        We will retain order records for legal and warranty purposes, but your personal preferences, wishlist and stored addresses will be removed.
      </p>
      <button type="button" class="editorial-label text-bordeaux-500 hover:text-bordeaux-600 link-underline text-[11px]" @click="deleteConfirmOpen = true">Permanently close my account</button>
    </section>

    <!-- Bottom sign out -->
    <div class="mt-10 flex justify-end">
      <button type="button" class="btn-ink btn-sm" @click="signOutEverywhere">
        <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.3"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15M12 9l-3 3m0 0l3 3m-3-3h12.75" /></svg>
        {{ t('account.signOut') }}
      </button>
    </div>

    <!-- Delete confirm -->
    <TransitionRoot :show="deleteConfirmOpen" as="template">
      <Dialog @close="deleteConfirmOpen = false" class="relative z-[65]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0">
          <div class="fixed inset-0 bg-ink-950/70 backdrop-blur-sm" />
        </TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0 scale-95">
            <DialogPanel class="w-full max-w-md bg-white p-10">
              <p class="editorial-label text-bordeaux-500 mb-3">Final confirmation</p>
              <h3 class="font-serif text-2xl mb-3">Close your Maison account</h3>
              <p class="text-sm text-ink-500 mb-6 leading-relaxed">Type <span class="text-ink-950 font-medium">DELETE</span> below to permanently close your account. This is irreversible.</p>
              <input v-model="deleteText" type="text" class="field mb-5" placeholder="DELETE" />
              <div class="flex gap-3">
                <button type="button" class="btn-outline flex-1" @click="deleteConfirmOpen = false">{{ t('common.cancel') }}</button>
                <button type="button" class="btn-ink flex-1" :disabled="deleteText !== 'DELETE'" @click="deleteAccount">Close account</button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>
  </AccountLayout>
</template>
