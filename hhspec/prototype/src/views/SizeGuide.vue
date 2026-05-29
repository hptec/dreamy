<script setup>
import { RouterLink } from 'vue-router'
import { Disclosure, DisclosureButton, DisclosurePanel } from '@headlessui/vue'
import { HERO_IMAGES, PRODUCT_IMAGES } from '../data/catalog'

// Re-used from SizeGuideModal: [US, EU, UK, Bust, Waist, Hips]
const sizeRows = [
  ['US 0', 'EU 30', 'UK 4', '32', '24', '34'],
  ['US 2', 'EU 32', 'UK 6', '33', '25', '35'],
  ['US 4', 'EU 34', 'UK 8', '34', '26', '36'],
  ['US 6', 'EU 36', 'UK 10', '35', '27', '37'],
  ['US 8', 'EU 38', 'UK 12', '36', '28', '38'],
  ['US 10', 'EU 40', 'UK 14', '37.5', '29.5', '39.5'],
  ['US 12', 'EU 42', 'UK 16', '39', '31', '41'],
  ['US 14', 'EU 44', 'UK 18', '40.5', '32.5', '42.5'],
  ['US 16', 'EU 46', 'UK 20', '42', '34', '44'],
  ['US 18W', 'EU 48', 'UK 22', '44', '36', '46'],
  ['US 20W', 'EU 50', 'UK 24', '46', '38', '48'],
]

const measureSteps = [
  {
    n: '01',
    label: 'Bust',
    body: 'With a soft tape, measure around the fullest part of your bust, keeping the tape level and parallel to the floor. Wear an unpadded bra similar to what you will wear on the day.',
  },
  {
    n: '02',
    label: 'Waist',
    body: 'Find the narrowest part of your natural waistline — usually about an inch above the navel. Keep the tape snug, never pulled tight, and breathe normally.',
  },
  {
    n: '03',
    label: 'Hips',
    body: 'Stand with feet together. Measure around the fullest part of your hips, approximately 20 cm (8 inches) below the natural waistline.',
  },
  {
    n: '04',
    label: 'Hollow to Floor',
    body: 'From the hollow between your collarbones, run the tape straight down the centre of your body to the floor. Stand barefoot for the truest reading — this gives us your gown length.',
  },
]

const faqs = [
  {
    q: 'How do Maison Eden sizes compare to other US bridal labels?',
    a: 'Our standard sizing follows the published chart on this page, which is true to body measurements (not the +2 vanity scaling some labels use). If you typically wear a US 4 in ready-to-wear, you most likely wear a US 6 in bridal — please order by your measurements, not by your usual ready-to-wear size.',
  },
  {
    q: 'I am between two sizes — should I size up or down?',
    a: 'We always recommend sizing up. A gown can be brought in by your local seamstress with very little visible change, but a gown that is too small at the bust or waist often cannot be let out without disturbing the design lines. Or, simpler: choose Custom Size at checkout and skip the question entirely.',
  },
  {
    q: 'What does Custom Size include?',
    a: 'Custom Size is hand-built from fourteen of your measurements — bust, waist, hips, hollow-to-floor, height with shoes, shoulder, sleeve length, and seven more for fit. It adds no cost on most styles, and adds two to seven days of production time.',
  },
  {
    q: 'How accurate do my measurements need to be?',
    a: 'Within a quarter inch. If you can have a friend take them with a soft tape while you stand naturally (and breathe), they will be accurate enough. If you are unsure, our atelier will email you back within a day to confirm.',
  },
  {
    q: 'Will the gown still need alterations once it arrives?',
    a: 'Standard sizes will typically need a hem and may need small adjustments. Custom Size gowns rarely need anything beyond a final press. Either way, we recommend one fitting with a local seamstress two to three weeks before the wedding.',
  },
]
</script>

<template>
  <div class="bg-canvas">
    <!-- HERO -->
    <section class="relative -mt-20 h-[80vh] min-h-[520px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[5]" alt="Size Guide" class="absolute inset-0 w-full h-full object-cover animate-kenburns" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/85 via-ink-950/20 to-ink-950/40"></div>
      <div class="relative container-editorial pb-16 sm:pb-24">
        <div class="max-w-3xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">Fit & Measurement</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">The Size Guide</h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-xl font-light leading-relaxed">
            A gown that fits is a gown that is forgotten. Below, our chart, our measurement method, and a quiet word on Custom Size.
          </p>
        </div>
      </div>
    </section>

    <!-- INTRO -->
    <section class="section-pad bg-canvas">
      <div class="container-narrow text-center">
        <p class="editorial-label text-champagne-600 mb-5">A note before you measure</p>
        <h2 class="font-serif text-4xl sm:text-5xl mb-8 leading-tight text-balance">
          Order by your measurements, not your usual size.
        </h2>
        <p class="text-ink-700 text-lg font-light leading-[1.85]">
          All Maison Eden sizing follows true body measurements. Take your bust, waist, hips, and hollow-to-floor as described below, find them on the chart, and order the closest US size — or, if no row is a perfect match, choose <RouterLink to="/wedding-dresses" class="link-underline font-medium">Custom Size</RouterLink> at checkout and we will cut to your exact measurements.
        </p>
      </div>
    </section>

    <!-- SIZE CHART -->
    <section class="section-pad bg-white">
      <div class="container-editorial">
        <div class="flex flex-col lg:flex-row lg:items-end lg:justify-between mb-10 gap-6">
          <div>
            <p class="editorial-label text-champagne-600 mb-3">The Chart</p>
            <h2 class="font-serif text-3xl sm:text-4xl leading-tight">International Size Chart</h2>
          </div>
          <p class="editorial-label text-ink-400">All measurements in inches</p>
        </div>

        <div class="overflow-x-auto border border-ink-200">
          <table class="w-full text-sm min-w-[640px]">
            <thead>
              <tr class="editorial-label text-ink-500 text-[10px] bg-ink-50 border-b border-ink-200">
                <th class="text-left py-4 px-5">US</th>
                <th class="text-left py-4 px-5">EU</th>
                <th class="text-left py-4 px-5">UK</th>
                <th class="text-left py-4 px-5">Bust</th>
                <th class="text-left py-4 px-5">Waist</th>
                <th class="text-left py-4 px-5">Hips</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in sizeRows" :key="r[0]" class="border-b border-ink-100 last:border-b-0 hover:bg-canvas">
                <td v-for="(c, j) in r" :key="j" class="py-4 px-5 tabular-nums" :class="j === 0 ? 'font-serif text-lg text-ink-950' : 'text-ink-600'">{{ c }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <p class="editorial-label text-ink-400 mt-6 text-[10px]">
          For maternity, plus, or non-standard requests, please <RouterLink to="/contact" class="link-underline text-ink-700">contact our atelier</RouterLink> before ordering.
        </p>
      </div>
    </section>

    <!-- HOW TO MEASURE — split with image -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-12">
          <div class="lg:col-span-5">
            <p class="editorial-label text-champagne-600 mb-4">How to Measure</p>
            <h2 class="font-serif text-4xl sm:text-5xl leading-tight mb-8 text-balance">
              Four measurements. One soft tape. A friend, ideally.
            </h2>
            <div class="aspect-[3/4] overflow-hidden bg-ink-100 hidden lg:block">
              <img :src="PRODUCT_IMAGES[24]" alt="How to measure" class="w-full h-full object-cover" />
            </div>
          </div>
          <ol class="lg:col-span-7 lg:pt-2 space-y-px bg-ink-200">
            <li v-for="step in measureSteps" :key="step.n" class="bg-white p-8 lg:p-10">
              <div class="flex items-baseline gap-5 mb-4">
                <span class="font-serif text-5xl text-champagne-500 tabular-nums leading-none">{{ step.n }}</span>
                <span class="editorial-label text-ink-500">{{ step.label }}</span>
              </div>
              <p class="text-ink-700 text-base leading-relaxed font-light">{{ step.body }}</p>
            </li>
          </ol>
        </div>
        <p class="text-sm text-ink-500 mt-10 text-center max-w-2xl mx-auto font-light leading-relaxed">
          A note: keep the tape snug but never pulled tight. Wear the undergarments you intend to wear on the day, and stand naturally — never sucked in. Breathe.
        </p>
      </div>
    </section>

    <!-- CUSTOM SIZE CALLOUT -->
    <section class="bg-ink-950 text-white">
      <div class="container-editorial grid lg:grid-cols-2">
        <div class="relative min-h-[380px] lg:min-h-[520px]">
          <img :src="PRODUCT_IMAGES[2]" alt="Custom size" class="absolute inset-0 w-full h-full object-cover" />
        </div>
        <div class="flex items-center py-16 lg:pl-16">
          <div class="max-w-md">
            <p class="editorial-label-light text-champagne-400 mb-5">Custom Size</p>
            <h2 class="font-serif text-4xl sm:text-5xl text-white leading-tight mb-6 text-balance">
              The most accurate fit we make.
            </h2>
            <p class="text-ink-300 leading-relaxed mb-4 font-light">
              Custom Size is cut from fourteen of your measurements rather than a chart — bust, waist, hips, hollow-to-floor, plus shoulder, sleeve, and seven smaller dimensions for the architecture of the bodice.
            </p>
            <p class="text-ink-300 leading-relaxed mb-8 font-light">
              It is the closest you can come to standing in our atelier in Paris. There is no surcharge on most bridal styles.
            </p>
            <RouterLink to="/wedding-dresses" class="btn-champagne">Browse Bridal Gowns</RouterLink>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="section-pad bg-white">
      <div class="container-editorial">
        <div class="text-center mb-14">
          <p class="editorial-label text-champagne-600 mb-3">Frequently Asked</p>
          <h2 class="font-serif text-4xl sm:text-5xl">A few quiet questions, answered.</h2>
        </div>
        <div class="container-narrow !px-0">
          <Disclosure v-for="(f, i) in faqs" :key="f.q" v-slot="{ open }" as="div" class="border-b border-ink-200 first:border-t" :defaultOpen="i === 0">
            <DisclosureButton class="w-full text-left py-7 flex items-start justify-between gap-6 group">
              <span class="font-serif text-xl sm:text-2xl text-ink-950 leading-snug pr-4 group-hover:text-champagne-700 transition-colors">{{ f.q }}</span>
              <span class="shrink-0 text-ink-400 mt-1 transition-transform duration-300" :class="open ? 'rotate-45' : ''">
                <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4"><path stroke-linecap="round" d="M12 5v14M5 12h14" /></svg>
              </span>
            </DisclosureButton>
            <DisclosurePanel class="pb-8 pr-12 text-ink-600 text-base leading-relaxed font-light max-w-3xl">{{ f.a }}</DisclosurePanel>
          </Disclosure>
        </div>
        <div class="text-center mt-14">
          <p class="text-sm text-ink-500 mb-5 font-light">Still unsure? Our stylists are on hand.</p>
          <RouterLink to="/contact" class="btn-outline">Contact the Atelier</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>
