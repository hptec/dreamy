<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { RouterLink } from 'vue-router'
import { Disclosure, DisclosureButton, DisclosurePanel } from '@headlessui/vue'
import { HERO_IMAGES } from '../data/catalog'

const search = ref('')
const active = ref('ordering')

const categories = [
  {
    id: 'ordering',
    label: 'Ordering',
    desc: 'How orders are placed, confirmed, and tracked.',
    faqs: [
      { q: 'How do I place an order?', a: 'Add the gown of your choice to your bag, select your colour, size or Custom Size, and your preferred production time, then proceed to checkout. You will receive a confirmation email within minutes, followed by an atelier confirmation within one business day.' },
      { q: 'Can I change my order after placing it?', a: 'You may modify size, colour, or production time within the first twenty-four hours after purchase by replying to your order confirmation email. After twenty-four hours your gown enters the cutting queue and changes are no longer possible.' },
      { q: 'Will I receive a confirmation when my gown enters production?', a: 'Yes — you will receive a confirmation email the morning your gown enters the cutting room, with the name of the seamstress assigned to your piece and her estimated finish date.' },
      { q: 'Can I order fabric swatches before I commit?', a: 'Yes. You may request up to 30 true-to-color fabric swatches at any time, free of charge. They ship within two business days and arrive in a small linen folio.' },
      { q: 'Do you offer in-person appointments?', a: 'Yes, in our New York and Paris showrooms by appointment only. Virtual fittings with a stylist are also available worldwide — both can be booked from our Contact page.' },
    ],
  },
  {
    id: 'sizing',
    label: 'Sizing & Fit',
    desc: 'Choosing the right size, between sizes, and our chart.',
    faqs: [
      { q: 'Do your gowns run true to size?', a: 'Our gowns are sized to true body measurements, not ready-to-wear vanity sizing. Please order by your bust, waist, and hip measurements as shown on our Size Guide rather than the size you wear at other labels.' },
      { q: 'I am between two sizes — which do I choose?', a: 'We recommend sizing up. A gown can almost always be brought in by a local seamstress; a gown that is too tight at the bust or waist cannot. Or, simpler still, choose Custom Size at checkout.' },
      { q: 'What is Custom Size?', a: 'Custom Size is cut to fourteen of your measurements rather than to a chart. It is the closest you can come to standing in our atelier. There is no upcharge on most bridal styles.' },
      { q: 'How do I measure myself?', a: 'See the four-step method on our Size Guide. You will need a soft tape, a friend if possible, and about ten minutes. Wear the undergarments you intend to wear on the day for the truest measurements.' },
      { q: 'Will my gown still need alterations?', a: 'A standard-size gown will typically need a hem and small fitting adjustments. A Custom Size gown rarely needs anything beyond a final press. Either way, we recommend one fitting two to three weeks before the wedding.' },
    ],
  },
  {
    id: 'customization',
    label: 'Customization',
    desc: 'Length, sleeves, colour, and bespoke alterations.',
    faqs: [
      { q: 'Can I change the length, sleeves, or train?', a: 'On most bridal gowns, yes. Length, sleeve style, and train length can be adjusted at checkout for a small fee, and bespoke design changes can be arranged with our atelier — please reach out via Contact before ordering.' },
      { q: 'Can I order a colour not shown on the page?', a: 'For bridesmaid styles, all 90 shades in our palette are available on every gown, even if not pictured. For bridal and evening, please write to our atelier to discuss custom-dye options.' },
      { q: 'Can I keep design changes for my bridal party identical?', a: 'Yes. Place one note in the order comments and our atelier will ensure all gowns in your party — even those ordered separately — match. We keep your party reference on file.' },
      { q: 'Are there design changes you cannot accommodate?', a: 'We cannot alter the structural silhouette of a gown — a mermaid cannot become an A-line, for instance. Within a given silhouette, however, almost any change is possible. The atelier will write back within one day with a clear yes, no, or quote.' },
    ],
  },
  {
    id: 'shipping',
    label: 'Shipping',
    desc: 'Delivery times, costs, and tracking your gown.',
    faqs: [
      { q: 'How long does production take?', a: 'Standard production is 3–4 weeks. Express (2–3 weeks) and Rush (4–8 days) are available at checkout for an additional fee. Shipping time is in addition to production time.' },
      { q: 'How will my gown be shipped?', a: 'In our signature hand-folded travel box, fully insured, with door-to-door tracking. International orders ship Delivered Duty Paid — no surprise customs invoices.' },
      { q: 'Do you offer free shipping?', a: 'Yes — complimentary insured express shipping on all orders over $500, in every region we ship to. Orders under $500 carry a flat shipping fee shown at checkout.' },
      { q: 'Can I track my order?', a: 'You will receive a tracking number by email the moment your gown leaves our warehouse, with door-to-door updates from carrier to recipient.' },
      { q: 'Do you ship internationally?', a: 'Yes, to 60+ countries, fully insured. See the regional table on our Shipping & Returns page for estimated times.' },
    ],
  },
  {
    id: 'returns',
    label: 'Returns & Exchanges',
    desc: 'Our 30-day return window and exchange options.',
    faqs: [
      { q: 'What is your return policy?', a: 'Standard-size gowns and accessories may be returned within 30 days of delivery, unworn, with tags attached and the original travel box. Custom Size gowns are made specifically for you and are considered final sale.' },
      { q: 'How do I begin a return?', a: 'Open your order in your account and tap “Begin Return.” A prepaid label and packing slip are emailed within fifteen minutes. Use the original travel box if you still have it.' },
      { q: 'How long do refunds take?', a: 'Once your return is received at our warehouse it is inspected within three business days, and refunds are issued to the original payment method. Most banks display the credit within 5–7 days.' },
      { q: 'Are exchanges possible?', a: 'Yes — for a different size or colour of the same style, provided the new piece is still available. Open your order and choose “Exchange” instead of “Refund” when beginning your return.' },
    ],
  },
  {
    id: 'account',
    label: 'Account & Payment',
    desc: 'Signing in, payment methods, and saved details.',
    faqs: [
      { q: 'Do I need an account to place an order?', a: 'No — guest checkout is available. Creating an account, however, allows you to track production status, save your measurements for future orders, and begin a return in two taps.' },
      { q: 'What payment methods do you accept?', a: 'Visa, Mastercard, American Express, Discover, Apple Pay, Google Pay, and Shop Pay. We also accept Klarna and Afterpay for orders between $50 and $2,000.' },
      { q: 'Is my payment information secure?', a: 'Yes. We never store full card numbers on our servers — payment is handled by Stripe, who are PCI-DSS Level 1 certified. Our checkout is end-to-end encrypted.' },
      { q: 'How do I reset my password?', a: 'On the sign-in page, tap “Forgot password.” A reset link will arrive in your inbox within a minute. The link expires after one hour for security.' },
      { q: 'How do I delete my account?', a: 'Write to privacy@maisoneden.com and we will close the account and remove your personal data within five business days, in keeping with our Privacy Policy.' },
    ],
  },
]

// Filter
const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return categories
  return categories
    .map((cat) => ({
      ...cat,
      faqs: cat.faqs.filter((f) => f.q.toLowerCase().includes(q) || f.a.toLowerCase().includes(q)),
    }))
    .filter((cat) => cat.faqs.length > 0)
})

const totalMatches = computed(() => filtered.value.reduce((acc, c) => acc + c.faqs.length, 0))

// Active section tracking via IntersectionObserver
let observer = null
onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting && e.intersectionRatio > 0.15) active.value = e.target.id
      })
    },
    { rootMargin: '-25% 0px -55% 0px', threshold: [0.15, 0.5] },
  )
  categories.forEach((c) => {
    const el = document.getElementById(`cat-${c.id}`)
    if (el) observer.observe(el)
  })
})
onBeforeUnmount(() => observer && observer.disconnect())
</script>

<template>
  <div class="bg-canvas">
    <!-- HERO -->
    <section class="relative -mt-20 h-[60vh] min-h-[440px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[7]" alt="Frequently asked" class="absolute inset-0 w-full h-full object-cover" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/85 via-ink-950/20 to-ink-950/40"></div>
      <div class="relative container-editorial pb-14 sm:pb-20">
        <div class="max-w-3xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">Frequently Asked</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">A quiet word.</h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-xl font-light leading-relaxed">
            Almost every question we receive lives below. If yours does not, write to our atelier and we will answer within a day.
          </p>
        </div>
      </div>
    </section>

    <!-- SEARCH -->
    <section class="bg-white border-b border-ink-100">
      <div class="container-editorial py-10 lg:py-14">
        <div class="container-narrow !px-0">
          <label for="faq-search" class="field-label text-center block">Search the FAQ</label>
          <div class="relative">
            <svg class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-ink-400 pointer-events-none" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="11" cy="11" r="7" /><path stroke-linecap="round" d="M21 21l-4.3-4.3" />
            </svg>
            <input
              id="faq-search"
              v-model="search"
              type="search"
              placeholder="Try “custom size,” “rush,” “returns”…"
              class="field !py-4 !pl-12 !text-base text-center"
            />
            <button v-if="search" @click="search = ''" class="absolute right-4 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-950" aria-label="Clear">
              <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg>
            </button>
          </div>
          <p v-if="search" class="editorial-label text-ink-400 text-center mt-5">
            {{ totalMatches }} {{ totalMatches === 1 ? 'answer' : 'answers' }} match “{{ search }}”
          </p>
        </div>
      </div>
    </section>

    <!-- BODY: sticky tabs + content -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-12 lg:gap-16">
          <!-- side nav -->
          <aside class="lg:col-span-3">
            <div class="lg:sticky lg:top-28">
              <p class="editorial-label text-ink-400 mb-5">Categories</p>
              <ul class="space-y-1 border-l border-ink-200">
                <li v-for="c in categories" :key="c.id">
                  <a
                    :href="`#cat-${c.id}`"
                    class="block pl-5 py-2.5 text-sm transition-colors -ml-px border-l"
                    :class="active === c.id ? 'border-ink-950 text-ink-950 font-medium' : 'border-transparent text-ink-500 hover:text-ink-950'"
                  >{{ c.label }}</a>
                </li>
              </ul>
              <div class="mt-12 hidden lg:block">
                <p class="editorial-label text-champagne-600 mb-3">Cannot find it?</p>
                <p class="text-sm text-ink-600 leading-relaxed font-light mb-4">Write to our atelier — most replies arrive the same day.</p>
                <RouterLink to="/contact" class="link-underline text-sm text-ink-950 font-medium">Contact the Maison</RouterLink>
              </div>
            </div>
          </aside>

          <!-- categories -->
          <div class="lg:col-span-9 space-y-20">
            <div v-if="totalMatches === 0" class="text-center py-20">
              <p class="font-serif text-3xl mb-3">No matches.</p>
              <p class="text-ink-500 mb-6 font-light">Try a different search term — or write to our atelier directly.</p>
              <RouterLink to="/contact" class="btn-outline">Contact the Atelier</RouterLink>
            </div>

            <article v-for="cat in filtered" :key="cat.id" :id="`cat-${cat.id}`" class="scroll-mt-32">
              <p class="editorial-label text-champagne-600 mb-3">{{ cat.label }}</p>
              <h2 class="font-serif text-3xl sm:text-4xl leading-tight mb-3">{{ cat.label }}</h2>
              <p class="text-ink-500 font-light leading-relaxed mb-8 max-w-2xl">{{ cat.desc }}</p>

              <Disclosure v-for="(f, i) in cat.faqs" :key="f.q" v-slot="{ open }" as="div" class="border-b border-ink-200 first:border-t" :defaultOpen="!!search && i === 0">
                <DisclosureButton class="w-full text-left py-6 flex items-start justify-between gap-6 group">
                  <span class="font-serif text-lg sm:text-xl text-ink-950 leading-snug pr-4 group-hover:text-champagne-700 transition-colors">{{ f.q }}</span>
                  <span class="shrink-0 text-ink-400 mt-1 transition-transform duration-300" :class="open ? 'rotate-45' : ''">
                    <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4"><path stroke-linecap="round" d="M12 5v14M5 12h14" /></svg>
                  </span>
                </DisclosureButton>
                <DisclosurePanel class="pb-7 pr-12 text-ink-600 text-base leading-relaxed font-light max-w-3xl">{{ f.a }}</DisclosurePanel>
              </Disclosure>
            </article>
          </div>
        </div>
      </div>
    </section>

    <!-- CLOSING CTA -->
    <section class="section-pad bg-ink-950 text-white text-center">
      <div class="container-narrow">
        <p class="editorial-label-light text-champagne-400 mb-5">Still nothing?</p>
        <h2 class="font-serif text-4xl sm:text-5xl text-white leading-tight mb-6 text-balance">
          A stylist is one note away.
        </h2>
        <p class="text-ink-300 leading-relaxed mb-9 font-light max-w-xl mx-auto">
          Our customer-care team answers every email within one business day, Monday through Saturday. Many questions get an answer the same hour.
        </p>
        <div class="flex flex-wrap justify-center gap-4">
          <RouterLink to="/contact" class="btn-champagne">Contact the Atelier</RouterLink>
          <RouterLink to="/size-guide" class="btn-ghost">Read the Size Guide</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>
