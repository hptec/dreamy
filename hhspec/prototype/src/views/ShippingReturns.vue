<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { RouterLink } from 'vue-router'
import { HERO_IMAGES, PRODUCT_IMAGES } from '../data/catalog'

const sections = [
  { id: 'production', label: 'Production Times' },
  { id: 'shipping', label: 'Shipping & Delivery' },
  { id: 'duties', label: 'Duties & Taxes' },
  { id: 'returns', label: 'Returns Policy' },
  { id: 'process', label: 'Return Process' },
  { id: 'terms', label: 'Terms of Service' },
  { id: 'privacy', label: 'Privacy Policy' },
]

const productionRows = [
  { tier: 'Standard Atelier', detail: 'Made to order · 3–4 weeks', fee: 'Included', note: 'The default for every gown — gives the seamstress room to finish properly.' },
  { tier: 'Express', detail: 'Made to order · 2–3 weeks', fee: '+ $15', note: 'Moves your gown to the front of the cutting queue without altering the finishing.' },
  { tier: 'Rush', detail: 'Priority · 4–8 days', fee: '+ $30', note: 'Reserved for inside-thirty-day weddings. Subject to atelier availability.' },
]

const shippingRows = [
  { region: 'United States', method: 'Insured Express', days: '2–4 business days', cost: 'Free over $500 · $25 under' },
  { region: 'Canada', method: 'Insured Express', days: '3–5 business days', cost: 'Free over $500 · $35 under' },
  { region: 'United Kingdom', method: 'Insured Express', days: '3–5 business days', cost: 'Free over $500 · $35 under' },
  { region: 'Europe (EU)', method: 'Insured Express', days: '3–6 business days', cost: 'Free over $500 · $40 under' },
  { region: 'Australia & NZ', method: 'Insured Express', days: '5–7 business days', cost: 'Free over $500 · $55 under' },
  { region: 'Rest of World', method: 'Insured Standard', days: '7–14 business days', cost: 'Calculated at checkout' },
]

const returnSteps = [
  { n: '01', title: 'Begin a return', body: 'Open your order in your account and tap “Begin Return.” Choose the reason and select your preferred resolution — refund, exchange, or store credit.' },
  { n: '02', title: 'Print the label', body: 'A prepaid return label and a one-page packing slip are emailed within fifteen minutes. Use the original travel box if you still have it.' },
  { n: '03', title: 'Drop off', body: 'Hand the parcel to any major carrier point in your country. The label is fully tracked and insured.' },
  { n: '04', title: 'Inspection & refund', body: 'Once received at our New York warehouse, the gown is inspected within three business days. Refunds are issued to the original payment method.' },
]

// Sticky-side-nav active tracking
const active = ref('production')
let observer = null
onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting && e.intersectionRatio > 0.2) active.value = e.target.id
      })
    },
    { rootMargin: '-30% 0px -55% 0px', threshold: [0.2, 0.5] },
  )
  sections.forEach((s) => {
    const el = document.getElementById(s.id)
    if (el) observer.observe(el)
  })
})
onBeforeUnmount(() => observer && observer.disconnect())
</script>

<template>
  <div class="bg-canvas">
    <!-- HERO -->
    <section class="relative -mt-20 h-[70vh] min-h-[480px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[6]" alt="Shipping & returns" class="absolute inset-0 w-full h-full object-cover" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/85 via-ink-950/20 to-ink-950/40"></div>
      <div class="relative container-editorial pb-14 sm:pb-20">
        <div class="max-w-3xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">Customer Care</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">Shipping & Returns</h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-xl font-light leading-relaxed">
            How long your gown will take, how it will travel, and what to do — quietly, and without fuss — should it ever need to come back.
          </p>
        </div>
      </div>
    </section>

    <!-- BODY: sticky side-nav + content -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-12 lg:gap-16">
          <!-- side nav -->
          <aside class="lg:col-span-3">
            <div class="lg:sticky lg:top-28">
              <p class="editorial-label text-ink-400 mb-5">On This Page</p>
              <ul class="space-y-1 border-l border-ink-200">
                <li v-for="s in sections" :key="s.id">
                  <a
                    :href="`#${s.id}`"
                    class="block pl-5 py-2.5 text-sm transition-colors -ml-px border-l"
                    :class="active === s.id ? 'border-ink-950 text-ink-950 font-medium' : 'border-transparent text-ink-500 hover:text-ink-950'"
                  >{{ s.label }}</a>
                </li>
              </ul>
            </div>
          </aside>

          <!-- content -->
          <div class="lg:col-span-9 space-y-24">
            <!-- PRODUCTION TIMES -->
            <article id="production" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 01</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Production Times</h2>
              <p class="text-ink-600 leading-relaxed font-light mb-8 max-w-2xl">
                Every Maison Eden gown is cut after your order is placed. The timelines below begin the morning the order is confirmed by our atelier — usually within a few hours of purchase.
              </p>
              <div class="border border-ink-200">
                <div v-for="(row, i) in productionRows" :key="row.tier" class="grid grid-cols-12 gap-4 px-6 py-6 items-center" :class="i < productionRows.length - 1 ? 'border-b border-ink-200' : ''">
                  <div class="col-span-12 sm:col-span-3">
                    <p class="font-serif text-lg text-ink-950">{{ row.tier }}</p>
                    <p class="editorial-label text-ink-400 mt-1">{{ row.detail }}</p>
                  </div>
                  <p class="col-span-6 sm:col-span-2 editorial-label text-ink-700">{{ row.fee }}</p>
                  <p class="col-span-6 sm:col-span-7 text-sm text-ink-600 font-light leading-relaxed">{{ row.note }}</p>
                </div>
              </div>
            </article>

            <!-- SHIPPING -->
            <article id="shipping" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 02</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Shipping & Delivery</h2>
              <p class="text-ink-600 leading-relaxed font-light mb-8 max-w-2xl">
                Every gown ships in our signature hand-folded travel box, fully insured, with door-to-door tracking. Free shipping is included on all orders over <span class="tabular-nums">$500</span>.
              </p>
              <div class="overflow-x-auto border border-ink-200">
                <table class="w-full text-sm min-w-[640px]">
                  <thead>
                    <tr class="editorial-label text-ink-500 text-[10px] bg-ink-50 border-b border-ink-200">
                      <th class="text-left py-4 px-5">Region</th>
                      <th class="text-left py-4 px-5">Method</th>
                      <th class="text-left py-4 px-5">Estimated Time</th>
                      <th class="text-left py-4 px-5">Cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in shippingRows" :key="r.region" class="border-b border-ink-100 last:border-b-0">
                      <td class="py-4 px-5 font-serif text-base text-ink-950">{{ r.region }}</td>
                      <td class="py-4 px-5 text-ink-600">{{ r.method }}</td>
                      <td class="py-4 px-5 text-ink-600 tabular-nums">{{ r.days }}</td>
                      <td class="py-4 px-5 text-ink-600">{{ r.cost }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p class="editorial-label text-ink-400 mt-6 text-[10px]">
                Delivery times begin after production is complete and are calculated in business days.
              </p>
            </article>

            <!-- DUTIES -->
            <article id="duties" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 03</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Duties & Taxes</h2>
              <div class="space-y-5 text-ink-600 leading-relaxed font-light max-w-2xl">
                <p>
                  For orders shipping outside the United States, any applicable duties, taxes, and customs fees are calculated transparently at checkout and prepaid on your behalf. No surprise invoices on the doorstep — when your gown arrives, it is yours, in full.
                </p>
                <p>
                  Domestic US orders include sales tax where applicable, displayed clearly in the order summary before payment.
                </p>
                <div class="border border-ink-200 p-6 bg-white mt-6">
                  <p class="editorial-label text-champagne-600 mb-2">Delivered Duty Paid</p>
                  <p class="text-sm text-ink-700 leading-relaxed">
                    All international orders ship DDP — Delivered Duty Paid. The price you see at checkout is the price you pay, full stop.
                  </p>
                </div>
              </div>
            </article>

            <!-- RETURNS POLICY -->
            <article id="returns" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 04</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Returns Policy</h2>
              <p class="text-ink-600 leading-relaxed font-light mb-8 max-w-2xl">
                Standard-size gowns and accessories may be returned within thirty days of delivery, unworn, with original tags and travel box. Custom Size gowns are made specifically for you and are considered final sale.
              </p>

              <div class="grid sm:grid-cols-2 gap-px bg-ink-200">
                <div class="bg-white p-7">
                  <p class="editorial-label text-champagne-600 mb-4">Eligible for Return</p>
                  <ul class="space-y-3 text-sm text-ink-700 font-light leading-relaxed">
                    <li class="flex gap-3"><span class="text-champagne-500">—</span>Standard-size gowns, unworn, within 30 days</li>
                    <li class="flex gap-3"><span class="text-champagne-500">—</span>Accessories with tags attached</li>
                    <li class="flex gap-3"><span class="text-champagne-500">—</span>Fabric swatches (always free, never returned)</li>
                  </ul>
                </div>
                <div class="bg-white p-7">
                  <p class="editorial-label text-bordeaux-500 mb-4">Final Sale</p>
                  <ul class="space-y-3 text-sm text-ink-700 font-light leading-relaxed">
                    <li class="flex gap-3"><span class="text-bordeaux-500">—</span>Custom Size gowns</li>
                    <li class="flex gap-3"><span class="text-bordeaux-500">—</span>Gowns modified after purchase</li>
                    <li class="flex gap-3"><span class="text-bordeaux-500">—</span>Items marked Final Sale at checkout</li>
                  </ul>
                </div>
              </div>
            </article>

            <!-- RETURN PROCESS -->
            <article id="process" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 05</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Return Process</h2>
              <p class="text-ink-600 leading-relaxed font-light mb-10 max-w-2xl">
                Four steps, no telephone tag, no restocking fee on US returns.
              </p>
              <ol class="grid sm:grid-cols-2 gap-px bg-ink-200">
                <li v-for="step in returnSteps" :key="step.n" class="bg-white p-8">
                  <div class="flex items-baseline gap-4 mb-4">
                    <span class="font-serif text-4xl text-champagne-500 tabular-nums leading-none">{{ step.n }}</span>
                    <h4 class="font-serif text-xl text-ink-950">{{ step.title }}</h4>
                  </div>
                  <p class="text-sm text-ink-600 leading-relaxed font-light">{{ step.body }}</p>
                </li>
              </ol>
            </article>

            <!-- TERMS -->
            <article id="terms" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 06</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Terms of Service</h2>
              <div class="space-y-5 text-ink-600 leading-relaxed font-light max-w-2xl">
                <p>
                  By placing an order with Maison Eden, you agree to the terms of sale below. The full legal text is available on request — what follows is a plain-language summary of the points that matter most.
                </p>
                <ul class="space-y-3">
                  <li class="flex gap-3"><span class="editorial-label text-champagne-600 shrink-0 pt-1">01</span><span>All gowns are made-to-order. Production begins on order confirmation and cannot be cancelled after twenty-four hours.</span></li>
                  <li class="flex gap-3"><span class="editorial-label text-champagne-600 shrink-0 pt-1">02</span><span>Images on this site are accurate representations; minor variation in lace placement, beading density, and natural fabric character is part of the made-by-hand process.</span></li>
                  <li class="flex gap-3"><span class="editorial-label text-champagne-600 shrink-0 pt-1">03</span><span>Maison Eden reserves the right to refuse or refund any order where measurements appear to have been submitted in error, without first contacting the customer for confirmation.</span></li>
                  <li class="flex gap-3"><span class="editorial-label text-champagne-600 shrink-0 pt-1">04</span><span>All disputes are governed by the laws of the State of New York, with arbitration available as an alternative to court proceedings.</span></li>
                </ul>
              </div>
            </article>

            <!-- PRIVACY -->
            <article id="privacy" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">Section 07</p>
              <h2 class="font-serif text-3xl sm:text-4xl mb-4 leading-tight">Privacy Policy</h2>
              <div class="space-y-5 text-ink-600 leading-relaxed font-light max-w-2xl">
                <p>
                  We collect only the information required to make and ship your gown — your name, address, measurements, payment method, and the messages you send our atelier. We do not sell your personal data, ever.
                </p>
                <p>
                  You may request a full export, correction, or deletion of your data at any time by writing to <a href="mailto:privacy@maisoneden.com" class="link-underline text-ink-800">privacy@maisoneden.com</a>. We respond within five business days.
                </p>
                <p>
                  We use functional cookies to keep you signed in and your bag intact, and anonymized analytics to understand which collections are most viewed. You can change these settings at any time via the footer of any page.
                </p>
              </div>
            </article>
          </div>
        </div>
      </div>
    </section>

    <!-- CLOSING CONTACT BAND -->
    <section class="bg-ink-950 text-white">
      <div class="container-editorial grid lg:grid-cols-2">
        <div class="relative min-h-[320px] lg:min-h-[420px]">
          <img :src="PRODUCT_IMAGES[19]" alt="Atelier care" class="absolute inset-0 w-full h-full object-cover" />
        </div>
        <div class="flex items-center py-14 lg:pl-16">
          <div class="max-w-md">
            <p class="editorial-label-light text-champagne-400 mb-4">Still Have a Question?</p>
            <h2 class="font-serif text-3xl sm:text-4xl text-white leading-tight mb-5">Our customer care team is on hand.</h2>
            <p class="text-ink-300 leading-relaxed mb-7 font-light">Monday – Saturday, 9 a.m. to 7 p.m. Eastern. We answer every email within one business day.</p>
            <RouterLink to="/contact" class="btn-ghost">Contact the Atelier</RouterLink>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
