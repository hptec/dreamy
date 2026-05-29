<script setup>
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption } from '@headlessui/vue'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import AccountLayout from '../components/AccountLayout.vue'

const { t } = useI18n()
const auth = useAuthStore()
const ui = useUiStore()

const GENDERS = [
  { id: 'unspecified', label: 'Prefer not to say' },
  { id: 'female', label: 'Female' },
  { id: 'male', label: 'Male' },
  { id: 'nonbinary', label: 'Non-binary' },
]

const form = reactive({
  firstName: auth.user?.firstName || 'Isabella',
  lastName: auth.user?.lastName || 'Moreau',
  email: auth.user?.email || 'isabella@example.com',
  phone: auth.user?.phone || '+1 212 555 0148',
  birthday: auth.user?.birthday || '1996-04-12',
  gender: auth.user?.gender || GENDERS[1].id,
  weddingDate: auth.user?.weddingDate || '2026-09-21',
  preferredSize: auth.user?.preferredSize || 'US 4',
})

const saving = ref(false)

function selectedGender() { return GENDERS.find((g) => g.id === form.gender) || GENDERS[0] }

function save() {
  saving.value = true
  setTimeout(() => {
    auth.updateProfile({
      firstName: form.firstName,
      lastName: form.lastName,
      email: form.email,
      phone: form.phone,
      birthday: form.birthday,
      gender: form.gender,
      weddingDate: form.weddingDate,
      preferredSize: form.preferredSize,
    })
    saving.value = false
    ui.pushToast(t('toast.saved'))
  }, 350)
}
</script>

<template>
  <AccountLayout>
    <div class="flex items-end justify-between mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.profile') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">Your details</h2>
      </div>
    </div>

    <form class="space-y-12" @submit.prevent="save">
      <!-- Personal section -->
      <section class="bg-white border border-ink-200 p-8 lg:p-10">
        <div class="flex items-center justify-between mb-8">
          <div>
            <p class="editorial-label text-ink-400 mb-2">01</p>
            <h3 class="font-serif text-xl">Personal information</h3>
          </div>
          <span class="editorial-label text-ink-400 text-[10px] hidden sm:inline">Required for fittings & shipping</span>
        </div>
        <div class="grid sm:grid-cols-2 gap-x-6 gap-y-5">
          <div>
            <label class="field-label">{{ t('checkout.firstName') }}</label>
            <input v-model="form.firstName" type="text" required class="field" />
          </div>
          <div>
            <label class="field-label">{{ t('checkout.lastName') }}</label>
            <input v-model="form.lastName" type="text" required class="field" />
          </div>
          <div>
            <label class="field-label">{{ t('auth.email') }}</label>
            <input v-model="form.email" type="email" required class="field" />
          </div>
          <div>
            <label class="field-label">{{ t('checkout.phone') }}</label>
            <input v-model="form.phone" type="tel" class="field" placeholder="+1 212 555 0148" />
          </div>
          <div>
            <label class="field-label">Date of birth</label>
            <input v-model="form.birthday" type="date" class="field" />
          </div>
          <div>
            <label class="field-label">Gender</label>
            <Listbox v-model="form.gender">
              <div class="relative">
                <ListboxButton class="field text-left inline-flex items-center justify-between">
                  <span>{{ selectedGender().label }}</span>
                  <svg class="w-3.5 h-3.5 text-ink-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
                </ListboxButton>
                <ListboxOptions class="absolute z-20 mt-1 w-full bg-white shadow-lg ring-1 ring-ink-900/5 py-1 focus:outline-none">
                  <ListboxOption v-for="g in GENDERS" :key="g.id" :value="g.id" v-slot="{ active, selected }">
                    <div :class="[active ? 'bg-ink-50' : '', selected ? 'text-ink-950' : 'text-ink-600', 'px-4 py-2.5 text-sm cursor-pointer']">{{ g.label }}</div>
                  </ListboxOption>
                </ListboxOptions>
              </div>
            </Listbox>
          </div>
        </div>
      </section>

      <!-- Bridal section -->
      <section class="bg-white border border-ink-200 p-8 lg:p-10">
        <div class="flex items-center justify-between mb-8">
          <div>
            <p class="editorial-label text-ink-400 mb-2">02</p>
            <h3 class="font-serif text-xl">Bridal & fit preferences</h3>
          </div>
          <span class="editorial-label text-champagne-600 text-[10px] hidden sm:inline">Used by your private stylist</span>
        </div>
        <div class="grid sm:grid-cols-2 gap-x-6 gap-y-5">
          <div>
            <label class="field-label">Wedding date</label>
            <input v-model="form.weddingDate" type="date" class="field" />
          </div>
          <div>
            <label class="field-label">Preferred size</label>
            <input v-model="form.preferredSize" type="text" class="field" placeholder="US 4" />
          </div>
        </div>
        <p class="text-xs text-ink-400 mt-6 leading-relaxed max-w-lg">
          We use your wedding date to schedule production windows and to send you a complimentary swatch box six weeks in advance.
        </p>
      </section>

      <!-- Save bar -->
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pt-2">
        <p class="editorial-label text-ink-400 text-[10px]">Last updated 2 days ago · Auto-saved as draft</p>
        <div class="flex gap-3">
          <button type="button" class="btn-outline btn-sm" @click="ui.pushToast(t('common.cancel'), 'muted')">{{ t('common.cancel') }}</button>
          <button type="submit" class="btn-ink btn-sm" :disabled="saving">
            <svg v-if="saving" class="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2" stroke-dasharray="40" stroke-linecap="round" /></svg>
            {{ t('common.save') }}
          </button>
        </div>
      </div>
    </form>
  </AccountLayout>
</template>
