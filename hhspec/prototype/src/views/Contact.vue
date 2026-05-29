<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useUiStore } from '../stores/ui'
import { HERO_IMAGES, PRODUCT_IMAGES } from '../data/catalog'

const ui = useUiStore()

const form = ref({
  firstName: '',
  lastName: '',
  email: '',
  orderNumber: '',
  subject: 'general',
  message: '',
})
const submitted = ref(false)

const subjects = [
  { id: 'general', label: 'A general question' },
  { id: 'order', label: 'An existing order' },
  { id: 'custom', label: 'Custom or bespoke design' },
  { id: 'sizing', label: 'Sizing & fit' },
  { id: 'returns', label: 'Returns & exchanges' },
  { id: 'press', label: 'Press & partnerships' },
]

const isValid = computed(() => {
  return form.value.firstName.trim() && form.value.lastName.trim() && form.value.email.trim() && form.value.message.trim().length >= 10
})

function submit() {
  if (!isValid.value) return
  submitted.value = true
  ui.pushToast('Your note has been received. We will reply within a day.')
}

function resetForm() {
  form.value = { firstName: '', lastName: '', email: '', orderNumber: '', subject: 'general', message: '' }
  submitted.value = false
}

const showroomHours = [
  { day: 'Monday – Friday', hours: '10:00 – 19:00' },
  { day: 'Saturday', hours: '10:00 – 18:00' },
  { day: 'Sunday', hours: 'By appointment' },
]
</script>

<template>
  <div class="bg-canvas">
    <!-- HERO -->
    <section class="relative -mt-20 h-[55vh] min-h-[400px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[10]" alt="Contact Maison Eden" class="absolute inset-0 w-full h-full object-cover" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/85 via-ink-950/20 to-ink-950/40"></div>
      <div class="relative container-editorial pb-14 sm:pb-20">
        <div class="max-w-3xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">A Conversation</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">Write to us.</h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-xl font-light leading-relaxed">
            Most messages get a thoughtful reply by the next morning. Monday – Saturday, our atelier is on the other end of the line.
          </p>
        </div>
      </div>
    </section>

    <!-- TWO-COLUMN -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-12 lg:gap-20">
          <!-- LEFT: FORM -->
          <div class="lg:col-span-7">
            <p class="editorial-label text-champagne-600 mb-3">Send a Note</p>
            <h2 class="font-serif text-3xl sm:text-4xl leading-tight mb-3">However we can help.</h2>
            <p class="text-ink-500 font-light leading-relaxed mb-10 max-w-xl">
              The more you tell us, the better we can answer. Include your order number if your note is about an existing piece.
            </p>

            <div v-if="submitted" class="border border-champagne-300 bg-champagne-50 p-10 lg:p-12 text-center">
              <div class="w-14 h-14 grid place-items-center border border-champagne-500 text-champagne-600 mx-auto mb-6">
                <svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4"><path stroke-linecap="round" stroke-linejoin="round" d="M4 12.5l5 5 11-11" /></svg>
              </div>
              <p class="editorial-label text-champagne-700 mb-3">Sent · Thank You</p>
              <h3 class="font-serif text-3xl text-ink-950 mb-4 leading-tight">Your note has been received.</h3>
              <p class="text-ink-700 leading-relaxed mb-7 max-w-md mx-auto font-light">
                A member of our customer-care team will reply to <span class="text-ink-950 font-medium">{{ form.email }}</span> within one business day — often the same morning.
              </p>
              <button type="button" @click="resetForm" class="btn-outline">Send another note</button>
            </div>

            <form v-else class="space-y-6" @submit.prevent="submit">
              <div class="grid sm:grid-cols-2 gap-5">
                <div>
                  <label for="fname" class="field-label">First Name</label>
                  <input id="fname" v-model="form.firstName" type="text" required autocomplete="given-name" class="field" />
                </div>
                <div>
                  <label for="lname" class="field-label">Last Name</label>
                  <input id="lname" v-model="form.lastName" type="text" required autocomplete="family-name" class="field" />
                </div>
              </div>

              <div class="grid sm:grid-cols-2 gap-5">
                <div>
                  <label for="email" class="field-label">Email</label>
                  <input id="email" v-model="form.email" type="email" required autocomplete="email" class="field" />
                </div>
                <div>
                  <label for="order" class="field-label">Order Number <span class="text-ink-400 normal-case tracking-normal">(optional)</span></label>
                  <input id="order" v-model="form.orderNumber" type="text" placeholder="ME-2026-00000" class="field" />
                </div>
              </div>

              <div>
                <label for="subject" class="field-label">Subject</label>
                <select id="subject" v-model="form.subject" class="field appearance-none bg-no-repeat bg-right pr-10" style="background-image: url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2212%22 height=%228%22 viewBox=%220 0 12 8%22 fill=%22none%22><path d=%22M1 1l5 5 5-5%22 stroke=%22%23666%22 stroke-width=%221.2%22/></svg>'); background-position: right 1rem center;">
                  <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.label }}</option>
                </select>
              </div>

              <div>
                <label for="message" class="field-label">Your Message</label>
                <textarea id="message" v-model="form.message" rows="7" required class="field resize-y" placeholder="Please share as much detail as you can — gowns mentioned, measurements, timing." />
                <p class="editorial-label text-ink-400 mt-2 text-[10px]">{{ form.message.length }} characters — minimum 10</p>
              </div>

              <div class="pt-3 flex flex-col sm:flex-row sm:items-center gap-5">
                <button type="submit" class="btn-ink" :disabled="!isValid">Send the Note</button>
                <p class="editorial-label text-ink-400 text-[10px]">We never share your details. Read our <RouterLink to="/shipping-returns#privacy" class="link-underline text-ink-600">Privacy Policy</RouterLink>.</p>
              </div>
            </form>
          </div>

          <!-- RIGHT: DETAILS -->
          <aside class="lg:col-span-5">
            <div class="lg:sticky lg:top-28 space-y-12">
              <div>
                <p class="editorial-label text-champagne-600 mb-3">Customer Care</p>
                <p class="font-serif text-3xl text-ink-950 mb-2 leading-tight">A real person, every time.</p>
                <p class="text-ink-500 font-light leading-relaxed">Replies arrive within one business day — usually sooner.</p>
              </div>

              <div class="border-t border-ink-200 pt-8">
                <p class="editorial-label text-ink-400 mb-3">By Email</p>
                <a href="mailto:care@maisoneden.com" class="font-serif text-2xl text-ink-950 link-underline">care@maisoneden.com</a>
                <p class="text-sm text-ink-500 mt-2 font-light">For press: press@maisoneden.com</p>
              </div>

              <div class="border-t border-ink-200 pt-8">
                <p class="editorial-label text-ink-400 mb-3">By Telephone</p>
                <a href="tel:+12125550148" class="font-serif text-2xl text-ink-950 link-underline">+1 (212) 555 0148</a>
                <p class="text-sm text-ink-500 mt-2 font-light">Monday – Saturday, 9 a.m. – 7 p.m. Eastern</p>
              </div>

              <div class="border-t border-ink-200 pt-8">
                <p class="editorial-label text-ink-400 mb-3">The Showroom</p>
                <p class="font-serif text-xl text-ink-950 leading-snug">
                  124 Greenwich Street<br />
                  Tribeca, New York<br />
                  10006
                </p>
                <p class="text-sm text-ink-500 mt-3 font-light">By appointment, with private fitting room.</p>
                <ul class="mt-5 space-y-2">
                  <li v-for="h in showroomHours" :key="h.day" class="flex justify-between text-sm border-b border-ink-100 pb-2">
                    <span class="text-ink-500 font-light">{{ h.day }}</span>
                    <span class="text-ink-900 tabular-nums">{{ h.hours }}</span>
                  </li>
                </ul>
              </div>

              <div class="border-t border-ink-200 pt-8">
                <div class="bg-ink-950 text-white p-8">
                  <p class="editorial-label-light text-champagne-400 mb-4">Live Stylist Chat</p>
                  <h3 class="font-serif text-2xl text-white leading-tight mb-3">A stylist on the line.</h3>
                  <p class="text-ink-300 text-sm leading-relaxed mb-5 font-light">
                    Need a colour matched, a silhouette decided, a measurement reviewed? Tap the chat icon at the corner of any page — Mon–Sat, 9 a.m. – 7 p.m. Eastern.
                  </p>
                  <p class="editorial-label-light text-ink-400">Median response · 38 seconds</p>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </section>

    <!-- LARGE IMAGERY CLOSING -->
    <section class="bg-white">
      <div class="container-editorial grid lg:grid-cols-2">
        <div class="relative min-h-[360px] lg:min-h-[480px]">
          <img :src="PRODUCT_IMAGES[16]" alt="The atelier door" class="absolute inset-0 w-full h-full object-cover" />
        </div>
        <div class="flex items-center py-14 lg:pl-16">
          <div class="max-w-md">
            <p class="editorial-label text-champagne-600 mb-4">Or, Begin a Gown</p>
            <h2 class="font-serif text-3xl sm:text-4xl leading-tight mb-5 text-balance">
              The fastest way to a stylist is a saved wishlist.
            </h2>
            <p class="text-ink-600 leading-relaxed mb-7 font-light">
              Save two or three gowns you love and a stylist will reach out personally — with paired veils, swatches, and a fit recommendation tailored to your party and timeline.
            </p>
            <RouterLink to="/wedding-dresses" class="btn-outline">Browse Bridal Couture</RouterLink>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
