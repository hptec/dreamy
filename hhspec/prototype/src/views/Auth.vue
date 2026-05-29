<script setup>
import { ref, reactive } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { HERO_IMAGES } from '../data/catalog'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const mode = ref('signIn') // 'signIn' | 'signUp'
const forgotOpen = ref(false)
const forgotEmail = ref('')

const signInForm = reactive({ email: '', password: '', remember: true })
const signUpForm = reactive({
  firstName: '', lastName: '', email: '', password: '', confirmPassword: '', newsletter: true, agree: true,
})

function submitSignIn() {
  if (!signInForm.email) return
  auth.signIn(signInForm.email)
  ui.pushToast(t('toast.signedIn'))
  router.push('/account')
}

function submitSignUp() {
  if (!signUpForm.email || !signUpForm.firstName) return
  auth.signUp({
    email: signUpForm.email,
    firstName: signUpForm.firstName,
    lastName: signUpForm.lastName,
    newsletter: signUpForm.newsletter,
  })
  ui.pushToast(t('toast.signedIn'))
  router.push('/account')
}

function sendReset() {
  forgotOpen.value = false
  ui.pushToast(t('toast.saved'))
  forgotEmail.value = ''
}

function socialSignIn(provider) {
  auth.signIn(`${provider.toLowerCase()}@maison-eden.com`)
  ui.pushToast(t('toast.signedIn'))
  router.push('/account')
}
</script>

<template>
  <div class="min-h-[calc(100vh-80px)] bg-canvas">
    <div class="grid lg:grid-cols-[1.05fr_1fr] min-h-[calc(100vh-80px)]">
      <!-- Editorial image rail -->
      <aside class="relative hidden lg:block overflow-hidden bg-ink-950">
        <img :src="HERO_IMAGES[3]" alt="Maison Eden Atelier" class="absolute inset-0 w-full h-full object-cover opacity-95" />
        <div class="absolute inset-0 bg-gradient-to-t from-ink-950/80 via-ink-950/20 to-ink-950/40"></div>
        <div class="relative h-full flex flex-col justify-between p-12 lg:p-16">
          <RouterLink to="/" class="inline-flex items-center gap-2 text-white">
            <span class="font-serif text-2xl">Maison Eden</span>
          </RouterLink>
          <div class="max-w-md">
            <p class="editorial-label-light text-champagne-300 mb-4">Membre privé</p>
            <h2 class="font-serif text-white text-4xl lg:text-5xl leading-[1.05]">
              A private salon for the brides we dress.
            </h2>
            <p class="text-ink-200/85 mt-5 text-sm leading-relaxed font-light max-w-sm">
              Track your made-to-order gown, save fittings, and revisit the silhouettes you have loved.
            </p>
            <div class="hairline border-white/15 mt-10 mb-6"></div>
            <p class="editorial-label-light text-ink-300 text-[10px]">In residence since 1962 · Paris · New York</p>
          </div>
        </div>
      </aside>

      <!-- Form rail -->
      <section class="flex items-center justify-center p-6 sm:p-10 lg:p-16">
        <div class="w-full max-w-md">
          <!-- mobile brand -->
          <RouterLink to="/" class="lg:hidden font-serif text-2xl block mb-10">Maison Eden</RouterLink>

          <!-- Tabs -->
          <div class="flex border-b border-ink-200 mb-10">
            <button
              type="button"
              class="flex-1 pb-4 editorial-label text-[11px] border-b-2 -mb-px transition-colors focus:outline-none"
              :class="mode === 'signIn' ? 'border-ink-950 text-ink-950' : 'border-transparent text-ink-400 hover:text-ink-700'"
              @click="mode = 'signIn'"
            >{{ t('auth.signIn') }}</button>
            <button
              type="button"
              class="flex-1 pb-4 editorial-label text-[11px] border-b-2 -mb-px transition-colors focus:outline-none"
              :class="mode === 'signUp' ? 'border-ink-950 text-ink-950' : 'border-transparent text-ink-400 hover:text-ink-700'"
              @click="mode = 'signUp'"
            >{{ t('auth.signUp') }}</button>
          </div>

          <!-- Sign In -->
          <form v-if="mode === 'signIn'" class="space-y-5" @submit.prevent="submitSignIn">
            <div>
              <p class="editorial-label text-champagne-600 mb-3">{{ t('auth.signIn') }}</p>
              <h1 class="font-serif text-4xl leading-tight">Welcome back to the Maison.</h1>
            </div>
            <div>
              <label class="field-label">{{ t('auth.email') }}</label>
              <input v-model="signInForm.email" type="email" required class="field" placeholder="you@maison-eden.com" />
            </div>
            <div>
              <label class="field-label">{{ t('auth.password') }}</label>
              <input v-model="signInForm.password" type="password" class="field" placeholder="••••••••" />
            </div>
            <div class="flex items-center justify-between pt-1">
              <label class="inline-flex items-center gap-2 cursor-pointer text-xs text-ink-600 select-none">
                <input v-model="signInForm.remember" type="checkbox" class="w-4 h-4 border border-ink-300 accent-ink-950" />
                {{ t('auth.rememberMe') }}
              </label>
              <button type="button" class="editorial-label text-ink-500 hover:text-ink-950 text-[10px]" @click="forgotOpen = true">
                {{ t('auth.forgotPassword') }}
              </button>
            </div>
            <button type="submit" class="btn-ink w-full !py-4">{{ t('auth.signIn') }}</button>

            <div class="relative my-8">
              <div class="hairline"></div>
              <span class="absolute -top-2.5 left-1/2 -translate-x-1/2 bg-canvas px-3 editorial-label text-ink-400 text-[10px]">{{ t('auth.or') }}</span>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <button type="button" class="btn-outline btn-sm" @click="socialSignIn('Google')">
                <svg class="w-4 h-4 fill-current text-ink-950" viewBox="0 0 24 24"><path d="M21.35 11.1h-9.17v2.92h5.5c-.28 1.49-1.69 4.38-5.5 4.38-3.31 0-6-2.74-6-6.12s2.69-6.12 6-6.12c1.88 0 3.14.8 3.86 1.49l2.63-2.53C16.9 3.6 14.76 2.8 12.18 2.8c-5.06 0-9.18 4.12-9.18 9.18s4.12 9.18 9.18 9.18c5.3 0 8.81-3.72 8.81-8.97 0-.6-.07-1.07-.16-1.5z"/></svg>
                <span class="ml-1.5">Google</span>
              </button>
              <button type="button" class="btn-outline btn-sm" @click="socialSignIn('Apple')">
                <svg class="w-4 h-4 fill-current text-ink-950" viewBox="0 0 24 24"><path d="M17.05 12.04c-.03-2.66 2.17-3.94 2.27-4-1.24-1.81-3.17-2.06-3.85-2.09-1.64-.16-3.2.96-4.03.96-.84 0-2.12-.94-3.49-.91-1.79.03-3.45 1.04-4.37 2.64-1.88 3.26-.48 8.09 1.34 10.74.91 1.3 1.99 2.75 3.41 2.7 1.37-.06 1.89-.88 3.54-.88 1.66 0 2.13.88 3.58.85 1.48-.03 2.41-1.32 3.31-2.62 1.04-1.5 1.47-2.95 1.5-3.03-.03-.01-2.88-1.1-2.91-4.36zm-2.62-8.02c.74-.9 1.24-2.16 1.1-3.4-1.07.04-2.36.71-3.13 1.6-.69.79-1.3 2.06-1.13 3.27 1.19.09 2.42-.6 3.16-1.47z"/></svg>
                <span class="ml-1.5">Apple</span>
              </button>
            </div>

            <p class="text-center text-sm text-ink-500 pt-4">
              {{ t('auth.noAccount') }}
              <button type="button" class="link-underline text-ink-950 ml-1" @click="mode = 'signUp'">{{ t('auth.signUp') }}</button>
            </p>
          </form>

          <!-- Sign Up -->
          <form v-else class="space-y-5" @submit.prevent="submitSignUp">
            <div>
              <p class="editorial-label text-champagne-600 mb-3">{{ t('auth.signUp') }}</p>
              <h1 class="font-serif text-4xl leading-tight">Begin your Maison story.</h1>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="field-label">{{ t('checkout.firstName') }}</label>
                <input v-model="signUpForm.firstName" type="text" required class="field" placeholder="Isabella" />
              </div>
              <div>
                <label class="field-label">{{ t('checkout.lastName') }}</label>
                <input v-model="signUpForm.lastName" type="text" required class="field" placeholder="Moreau" />
              </div>
            </div>
            <div>
              <label class="field-label">{{ t('auth.email') }}</label>
              <input v-model="signUpForm.email" type="email" required class="field" placeholder="you@maison-eden.com" />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="field-label">{{ t('auth.password') }}</label>
                <input v-model="signUpForm.password" type="password" class="field" placeholder="••••••••" />
              </div>
              <div>
                <label class="field-label">{{ t('auth.confirmPassword') }}</label>
                <input v-model="signUpForm.confirmPassword" type="password" class="field" placeholder="••••••••" />
              </div>
            </div>
            <label class="inline-flex items-start gap-2 cursor-pointer text-xs text-ink-600 select-none pt-1">
              <input v-model="signUpForm.newsletter" type="checkbox" class="w-4 h-4 mt-0.5 border border-ink-300 accent-ink-950" />
              <span>Send me the Eden newsletter — private previews and 10% off my first gown.</span>
            </label>
            <label class="inline-flex items-start gap-2 cursor-pointer text-xs text-ink-600 select-none">
              <input v-model="signUpForm.agree" type="checkbox" class="w-4 h-4 mt-0.5 border border-ink-300 accent-ink-950" />
              <span>I agree to the <span class="link-underline text-ink-950">Terms</span> and <span class="link-underline text-ink-950">Privacy Policy</span>.</span>
            </label>

            <button type="submit" class="btn-ink w-full !py-4" :disabled="!signUpForm.agree">{{ t('auth.signUp') }}</button>

            <div class="relative my-8">
              <div class="hairline"></div>
              <span class="absolute -top-2.5 left-1/2 -translate-x-1/2 bg-canvas px-3 editorial-label text-ink-400 text-[10px]">{{ t('auth.or') }}</span>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <button type="button" class="btn-outline btn-sm" @click="socialSignIn('Google')">
                <svg class="w-4 h-4 fill-current text-ink-950" viewBox="0 0 24 24"><path d="M21.35 11.1h-9.17v2.92h5.5c-.28 1.49-1.69 4.38-5.5 4.38-3.31 0-6-2.74-6-6.12s2.69-6.12 6-6.12c1.88 0 3.14.8 3.86 1.49l2.63-2.53C16.9 3.6 14.76 2.8 12.18 2.8c-5.06 0-9.18 4.12-9.18 9.18s4.12 9.18 9.18 9.18c5.3 0 8.81-3.72 8.81-8.97 0-.6-.07-1.07-.16-1.5z"/></svg>
                <span class="ml-1.5">Google</span>
              </button>
              <button type="button" class="btn-outline btn-sm" @click="socialSignIn('Apple')">
                <svg class="w-4 h-4 fill-current text-ink-950" viewBox="0 0 24 24"><path d="M17.05 12.04c-.03-2.66 2.17-3.94 2.27-4-1.24-1.81-3.17-2.06-3.85-2.09-1.64-.16-3.2.96-4.03.96-.84 0-2.12-.94-3.49-.91-1.79.03-3.45 1.04-4.37 2.64-1.88 3.26-.48 8.09 1.34 10.74.91 1.3 1.99 2.75 3.41 2.7 1.37-.06 1.89-.88 3.54-.88 1.66 0 2.13.88 3.58.85 1.48-.03 2.41-1.32 3.31-2.62 1.04-1.5 1.47-2.95 1.5-3.03-.03-.01-2.88-1.1-2.91-4.36zm-2.62-8.02c.74-.9 1.24-2.16 1.1-3.4-1.07.04-2.36.71-3.13 1.6-.69.79-1.3 2.06-1.13 3.27 1.19.09 2.42-.6 3.16-1.47z"/></svg>
                <span class="ml-1.5">Apple</span>
              </button>
            </div>
            <p class="text-center text-sm text-ink-500 pt-4">
              {{ t('auth.haveAccount') }}
              <button type="button" class="link-underline text-ink-950 ml-1" @click="mode = 'signIn'">{{ t('auth.signIn') }}</button>
            </p>
          </form>
        </div>
      </section>
    </div>

    <!-- Forgot Password Dialog -->
    <TransitionRoot :show="forgotOpen" as="template">
      <Dialog @close="forgotOpen = false" class="relative z-[65]">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0">
          <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
        </TransitionChild>
        <div class="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild
            as="template"
            enter="duration-300 ease-editorial"
            enter-from="opacity-0 scale-95"
            enter-to="opacity-100 scale-100"
            leave="duration-200"
            leave-from="opacity-100"
            leave-to="opacity-0 scale-95"
          >
            <DialogPanel class="w-full max-w-md bg-white p-10">
              <p class="editorial-label text-champagne-600 mb-3">{{ t('auth.forgotPassword') }}</p>
              <h2 class="font-serif text-3xl mb-2">{{ t('auth.forgotTitle') }}</h2>
              <p class="text-sm text-ink-500 mb-6 leading-relaxed">{{ t('auth.forgotDesc') }}</p>
              <form @submit.prevent="sendReset">
                <label class="field-label">{{ t('auth.email') }}</label>
                <input v-model="forgotEmail" type="email" required class="field" placeholder="you@maison-eden.com" />
                <button type="submit" class="btn-ink w-full mt-5">{{ t('auth.sendReset') }}</button>
                <button type="button" class="editorial-label text-ink-400 hover:text-ink-950 mt-4 mx-auto block text-[10px]" @click="forgotOpen = false">{{ t('common.cancel') }}</button>
              </form>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>
  </div>
</template>
