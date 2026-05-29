<script setup>
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { SAMPLE_ADDRESSES } from '../data/catalog'
import { useUiStore } from '../stores/ui'
import AccountLayout from '../components/AccountLayout.vue'

const { t } = useI18n()
const ui = useUiStore()

const addresses = ref(SAMPLE_ADDRESSES.map((a) => ({ ...a })))

const dialogOpen = ref(false)
const editingId = ref(null)
const confirmDeleteId = ref(null)

const blank = () => ({
  id: 'new-' + Date.now(),
  label: 'home',
  firstName: '',
  lastName: '',
  street1: '',
  street2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'United States',
  phone: '',
  isDefault: false,
})
const form = reactive(blank())

const LABELS = [
  { id: 'home', label: 'Home' },
  { id: 'office', label: 'Office' },
  { id: 'other', label: 'Other' },
]

function openAdd() {
  Object.assign(form, blank())
  editingId.value = null
  dialogOpen.value = true
}

function openEdit(addr) {
  Object.assign(form, { ...addr })
  editingId.value = addr.id
  dialogOpen.value = true
}

function save() {
  if (editingId.value) {
    const idx = addresses.value.findIndex((a) => a.id === editingId.value)
    if (idx >= 0) addresses.value[idx] = { ...form }
    ui.pushToast(t('toast.saved'))
  } else {
    addresses.value.push({ ...form })
    ui.pushToast('Address added')
  }
  dialogOpen.value = false
}

function setDefault(id) {
  addresses.value = addresses.value.map((a) => ({ ...a, isDefault: a.id === id }))
  ui.pushToast(t('toast.saved'))
}

function remove(id) {
  addresses.value = addresses.value.filter((a) => a.id !== id)
  confirmDeleteId.value = null
  ui.pushToast('Address removed', 'muted')
}
</script>

<template>
  <AccountLayout>
    <div class="flex items-end justify-between mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.addresses') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">{{ t('account.savedAddresses') }}</h2>
        <p class="text-sm text-ink-500 mt-2">For fittings, shipping and atelier deliveries.</p>
      </div>
      <button type="button" class="btn-ink btn-sm" @click="openAdd">
        <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" d="M12 5v14M5 12h14"/></svg>
        {{ t('account.addAddress') }}
      </button>
    </div>

    <!-- cards -->
    <div v-if="addresses.length" class="grid sm:grid-cols-2 gap-5">
      <article
        v-for="addr in addresses"
        :key="addr.id"
        class="bg-white border p-7 flex flex-col"
        :class="addr.isDefault ? 'border-ink-950' : 'border-ink-200'"
      >
        <div class="flex items-center justify-between mb-4">
          <p class="editorial-label text-ink-400 text-[10px] capitalize">{{ LABELS.find(l => l.id === addr.label)?.label || addr.label }}</p>
          <span v-if="addr.isDefault" class="editorial-label text-[9px] px-2.5 py-1 bg-champagne-100 text-champagne-800 border border-champagne-300">{{ t('account.default') }}</span>
        </div>
        <p class="font-serif text-xl text-ink-950">{{ addr.firstName }} {{ addr.lastName }}</p>
        <p class="text-sm text-ink-600 mt-2 leading-relaxed flex-1">
          {{ addr.street1 }}<br />
          <span v-if="addr.street2">{{ addr.street2 }}<br /></span>
          {{ addr.city }}, {{ addr.state }} {{ addr.postalCode }}<br />
          {{ addr.country }}
        </p>
        <p class="text-xs text-ink-400 mt-2">{{ addr.phone }}</p>
        <div class="flex flex-wrap gap-x-5 gap-y-2 pt-5 mt-5 border-t border-ink-100">
          <button type="button" class="editorial-label text-[10px] text-ink-700 hover:text-ink-950 link-underline" @click="openEdit(addr)">{{ t('common.edit') }}</button>
          <button v-if="!addr.isDefault" type="button" class="editorial-label text-[10px] text-ink-700 hover:text-ink-950 link-underline" @click="setDefault(addr.id)">{{ t('account.setDefault') }}</button>
          <button type="button" class="editorial-label text-[10px] text-ink-500 hover:text-bordeaux-500 link-underline ml-auto" @click="confirmDeleteId = addr.id">{{ t('common.delete') }}</button>
        </div>
      </article>
    </div>

    <!-- Empty state -->
    <div v-else class="bg-white border border-dashed border-ink-300 px-10 py-20 text-center">
      <p class="font-serif text-2xl mb-2">No saved addresses</p>
      <p class="text-sm text-ink-500 mb-6">Add your first address for faster checkout.</p>
      <button type="button" class="btn-ink" @click="openAdd">{{ t('account.addAddress') }}</button>
    </div>

    <!-- Address Dialog -->
    <TransitionRoot :show="dialogOpen" as="template">
      <Dialog @close="dialogOpen = false" class="relative z-[65]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0">
          <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
        </TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-4 overflow-y-auto">
          <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0 scale-95">
            <DialogPanel class="w-full max-w-xl bg-white p-8 lg:p-10 my-12">
              <div class="flex items-center justify-between mb-6">
                <div>
                  <p class="editorial-label text-champagne-600 mb-2">{{ editingId ? t('common.edit') : t('account.addAddress') }}</p>
                  <h2 class="font-serif text-2xl">{{ editingId ? 'Edit address' : 'New address' }}</h2>
                </div>
                <button type="button" :aria-label="t('common.close')" class="text-ink-400 hover:text-ink-950" @click="dialogOpen = false">
                  <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6"/></svg>
                </button>
              </div>

              <form class="grid grid-cols-2 gap-x-5 gap-y-4" @submit.prevent="save">
                <div class="col-span-2">
                  <label class="field-label">Label</label>
                  <div class="flex gap-2">
                    <button v-for="l in LABELS" :key="l.id" type="button"
                      class="px-4 py-2 text-xs border transition-colors"
                      :class="form.label === l.id ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 hover:border-ink-950'"
                      @click="form.label = l.id">{{ l.label }}</button>
                  </div>
                </div>
                <div>
                  <label class="field-label">{{ t('checkout.firstName') }}</label>
                  <input v-model="form.firstName" required class="field" />
                </div>
                <div>
                  <label class="field-label">{{ t('checkout.lastName') }}</label>
                  <input v-model="form.lastName" required class="field" />
                </div>
                <div class="col-span-2">
                  <label class="field-label">{{ t('checkout.address1') }}</label>
                  <input v-model="form.street1" required class="field" />
                </div>
                <div class="col-span-2">
                  <label class="field-label">{{ t('checkout.address2') }}</label>
                  <input v-model="form.street2" class="field" />
                </div>
                <div>
                  <label class="field-label">{{ t('checkout.city') }}</label>
                  <input v-model="form.city" required class="field" />
                </div>
                <div>
                  <label class="field-label">{{ t('checkout.state') }}</label>
                  <input v-model="form.state" required class="field" />
                </div>
                <div>
                  <label class="field-label">{{ t('checkout.postalCode') }}</label>
                  <input v-model="form.postalCode" required class="field" />
                </div>
                <div>
                  <label class="field-label">{{ t('checkout.country') }}</label>
                  <input v-model="form.country" required class="field" />
                </div>
                <div class="col-span-2">
                  <label class="field-label">{{ t('checkout.phone') }}</label>
                  <input v-model="form.phone" type="tel" class="field" />
                </div>
                <label class="col-span-2 inline-flex items-center gap-2 cursor-pointer text-sm text-ink-600 select-none pt-2">
                  <input v-model="form.isDefault" type="checkbox" class="w-4 h-4 border border-ink-300 accent-ink-950" />
                  {{ t('account.setDefault') }}
                </label>

                <div class="col-span-2 flex gap-3 pt-4 mt-2 border-t border-ink-100">
                  <button type="submit" class="btn-ink flex-1">{{ t('common.save') }}</button>
                  <button type="button" class="btn-outline" @click="dialogOpen = false">{{ t('common.cancel') }}</button>
                </div>
              </form>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>

    <!-- Delete confirm -->
    <TransitionRoot :show="!!confirmDeleteId" as="template">
      <Dialog @close="confirmDeleteId = null" class="relative z-[70]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0">
          <div class="fixed inset-0 bg-ink-950/70 backdrop-blur-sm" />
        </TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0 scale-95">
            <DialogPanel class="w-full max-w-sm bg-white p-8 text-center">
              <h3 class="font-serif text-2xl mb-2">Remove address?</h3>
              <p class="text-sm text-ink-500 mb-6">This cannot be undone.</p>
              <div class="flex gap-3">
                <button type="button" class="btn-outline flex-1" @click="confirmDeleteId = null">{{ t('common.cancel') }}</button>
                <button type="button" class="btn-ink flex-1" @click="remove(confirmDeleteId)">{{ t('common.delete') }}</button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>
  </AccountLayout>
</template>
