<script setup>
import { RouterLink } from 'vue-router'
import { HERO_IMAGES, PRODUCT_IMAGES, TILE_IMAGES } from '../data/catalog'

const steps = [
  {
    n: '01',
    label: 'Design',
    title: 'The sketch on tracing paper.',
    body:
      'Every gown begins on the long oak table at the back of the atelier — pencil, vellum, the architectural lines of the silhouette drawn at full scale before any cloth is touched. The pattern is cut by hand from the sketch, and signed.',
    img: PRODUCT_IMAGES[5],
  },
  {
    n: '02',
    label: 'Measure',
    title: 'The body, in fourteen numbers.',
    body:
      'When you order a custom size, fourteen measurements travel with the order — bust, waist, hips, hollow-to-floor and more — and the pattern is re-cut on paper to the millimetre, before any silk is laid out.',
    img: PRODUCT_IMAGES[14],
  },
  {
    n: '03',
    label: 'Cut',
    title: 'The cloth, only once.',
    body:
      'Each panel is hand-cut, never stack-cut. The grain of the silk is read before the shears go down. A misread grain is the difference between a gown that falls and one that simply hangs.',
    img: PRODUCT_IMAGES[22],
  },
  {
    n: '04',
    label: 'Hand-finish',
    title: 'Stitched, not assembled.',
    body:
      'Seams are pressed open. Hems are turned by hand and held with invisible catch-stitch. Beading and lace appliqué are placed one motif at a time. The seamstress signs her initials inside the placket when she is done.',
    img: PRODUCT_IMAGES[28],
  },
  {
    n: '05',
    label: 'Final inspection',
    title: 'Three pairs of eyes before it leaves.',
    body:
      'Every finished gown is reviewed at the inspection table by the seamstress, the head of atelier, and the maison’s creative director. Only then is the gown steamed, hand-folded, and placed in its travel box.',
    img: PRODUCT_IMAGES[37],
  },
]

const fabrics = [
  { name: 'Mulberry Silk', origin: 'Como, Italy', note: 'Twelve-mommé weight, woven for breath and weight at once.' },
  { name: 'Chantilly Lace', origin: 'Calais, France', note: 'Re-embroidered by hand onto silk tulle in our finishing room.' },
  { name: 'Duchess Satin', origin: 'Lyon, France', note: 'Pressed weight, holds an architectural line without lining.' },
  { name: 'Crystal Beading', origin: 'Vienna, Austria', note: 'Lead-free, sewn by single-needle pass onto a basted under-layer.' },
]
</script>

<template>
  <div class="bg-canvas">
    <!-- HERO -->
    <section class="relative -mt-20 h-screen min-h-[640px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[8]" alt="Inside the atelier" class="absolute inset-0 w-full h-full object-cover animate-kenburns" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/85 via-ink-950/15 to-ink-950/35"></div>
      <div class="relative container-editorial pb-20 sm:pb-28">
        <div class="max-w-3xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">Inside The Atelier</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">
            Where a gown is not made. It is finished.
          </h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-xl font-light leading-relaxed">
            Every Maison Eden piece begins as a length of cloth and the quiet of a single morning. What happens between is the rest of this page.
          </p>
        </div>
      </div>
    </section>

    <!-- INTRO -->
    <section class="section-pad bg-canvas">
      <div class="container-narrow text-center">
        <p class="editorial-label text-champagne-600 mb-5">Made-to-Order Couture</p>
        <h2 class="font-serif text-4xl sm:text-5xl mb-8 leading-tight text-balance">
          A gown only exists because you asked for it.
        </h2>
        <p class="text-ink-700 text-lg font-light leading-[1.85]">
          Maison Eden does not keep stock. We do not cut a gown in advance of a name. From the morning your order arrives, a single seamstress is assigned, the pattern is drawn from your measurements, and the silk is laid on the cutting table. What follows are the five rooms your gown passes through, in order, before it is folded into its box and sent to you.
        </p>
      </div>
    </section>

    <!-- PROCESS — alternating panels -->
    <section class="bg-white">
      <ol>
        <li
          v-for="(step, i) in steps"
          :key="step.n"
          class="border-t border-ink-100"
          :class="i === steps.length - 1 ? 'border-b border-ink-100' : ''"
        >
          <div
            class="container-editorial grid lg:grid-cols-2"
            :class="i % 2 === 1 ? 'lg:[&>*:first-child]:order-2' : ''"
          >
            <div class="relative aspect-[4/5] lg:aspect-auto lg:min-h-[640px] overflow-hidden bg-ink-100">
              <img :src="step.img" :alt="step.title" class="absolute inset-0 w-full h-full object-cover" />
            </div>
            <div class="section-pad flex items-center" :class="i % 2 === 1 ? 'lg:pr-0 lg:pl-16' : 'lg:pl-0 lg:pr-16'">
              <div class="max-w-md">
                <div class="flex items-baseline gap-5 mb-8">
                  <span class="font-serif text-6xl text-champagne-500 tabular-nums leading-none">{{ step.n }}</span>
                  <span class="editorial-label text-ink-400">{{ step.label }}</span>
                </div>
                <h3 class="font-serif text-3xl sm:text-4xl leading-tight mb-6 text-balance">{{ step.title }}</h3>
                <p class="text-ink-600 text-base leading-relaxed font-light">{{ step.body }}</p>
              </div>
            </div>
          </div>
        </li>
      </ol>
    </section>

    <!-- FABRIC SOURCING -->
    <section class="section-pad bg-ink-950 text-white">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-10 lg:gap-20 mb-16 items-end">
          <div class="lg:col-span-5">
            <p class="editorial-label-light text-champagne-400 mb-4">The Fabric Trail</p>
            <h2 class="font-serif text-4xl sm:text-5xl text-white leading-tight">Cloth chosen for the way it ages.</h2>
          </div>
          <p class="lg:col-span-7 text-ink-300 text-lg font-light leading-relaxed">
            We source from four mills, all of whom we have worked with for at least fifteen years. We pay them by the metre, never by the kilo, because a noble cloth is not a commodity.
          </p>
        </div>

        <ul class="grid md:grid-cols-2 lg:grid-cols-4 gap-px bg-white/10">
          <li v-for="f in fabrics" :key="f.name" class="bg-ink-950 p-8 lg:p-10">
            <p class="editorial-label-light text-champagne-400 mb-5">{{ f.origin }}</p>
            <h3 class="font-serif text-2xl text-white leading-tight mb-4">{{ f.name }}</h3>
            <p class="text-ink-300 text-sm leading-relaxed font-light">{{ f.note }}</p>
          </li>
        </ul>
      </div>
    </section>

    <!-- HAND-FINISHING DETAIL — image gallery + text -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-12 mb-16">
          <div class="lg:col-span-5">
            <p class="editorial-label text-champagne-600 mb-4">Hand-Finishing</p>
            <h2 class="font-serif text-4xl sm:text-5xl leading-tight text-balance">
              The small kindnesses you only feel from the inside.
            </h2>
          </div>
          <div class="lg:col-span-6 lg:col-start-7 text-ink-600 text-lg font-light leading-relaxed self-end">
            <p>
              French seams. Catch-stitched hems. A grosgrain ribbon at the inner waist to hold the silhouette without a corset boning that bites. None of these are visible from across the room — they are the difference, instead, the bride feels at the third hour.
            </p>
          </div>
        </div>

        <div class="grid grid-cols-2 lg:grid-cols-3 gap-3 lg:gap-4">
          <div class="aspect-[3/4] overflow-hidden bg-ink-100 lg:row-span-2"><img :src="PRODUCT_IMAGES[45]" alt="Hand-finishing" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden bg-ink-100"><img :src="PRODUCT_IMAGES[51]" alt="Hand-beading" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden bg-ink-100"><img :src="PRODUCT_IMAGES[58]" alt="Lace appliqué" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden bg-ink-100"><img :src="PRODUCT_IMAGES[63]" alt="Seam pressing" class="w-full h-full object-cover" /></div>
          <div class="aspect-[3/4] overflow-hidden bg-ink-100"><img :src="PRODUCT_IMAGES[71]" alt="Inspection" class="w-full h-full object-cover" /></div>
        </div>
      </div>
    </section>

    <!-- QUOTE PULL-OUT -->
    <section class="relative h-[70vh] min-h-[480px] overflow-hidden bg-ink-950 flex items-center">
      <img :src="HERO_IMAGES[12]" alt="The atelier" class="absolute inset-0 w-full h-full object-cover opacity-50" />
      <div class="absolute inset-0 bg-ink-950/40"></div>
      <div class="relative container-editorial text-center">
        <p class="editorial-label-light text-champagne-300 mb-8">Yamina, Head of Atelier · with us since 1994</p>
        <blockquote class="font-serif text-white text-3xl sm:text-4xl lg:text-6xl leading-tight italic max-w-4xl mx-auto text-balance">
          “We do not finish a gown to be seen. We finish it so that the woman who wears it forgets she is wearing one.”
        </blockquote>
      </div>
    </section>

    <!-- TIME & PROCESS LADDER -->
    <section class="section-pad bg-white">
      <div class="container-editorial">
        <div class="text-center mb-16">
          <p class="editorial-label text-champagne-600 mb-3">A Brief Calendar</p>
          <h2 class="font-serif text-4xl sm:text-5xl text-balance">From order to door.</h2>
        </div>
        <ol class="grid sm:grid-cols-2 lg:grid-cols-4 border-t border-ink-200">
          <li class="border-b border-ink-200 sm:border-r py-10 lg:py-12">
            <p class="editorial-label text-ink-400 mb-3">Days 1 – 2</p>
            <h4 class="font-serif text-2xl mb-3">Confirmation</h4>
            <p class="text-sm text-ink-600 leading-relaxed font-light">Your measurements are reviewed by atelier, and a single seamstress is assigned to your gown.</p>
          </li>
          <li class="border-b border-ink-200 lg:border-r py-10 lg:py-12 sm:pl-8 lg:pl-10">
            <p class="editorial-label text-ink-400 mb-3">Days 3 – 7</p>
            <h4 class="font-serif text-2xl mb-3">Pattern & Cut</h4>
            <p class="text-sm text-ink-600 leading-relaxed font-light">Pattern is drawn at full scale, silk is checked for flaws, panels are cut and labeled by hand.</p>
          </li>
          <li class="border-b border-ink-200 sm:border-r py-10 lg:py-12 lg:pl-10">
            <p class="editorial-label text-ink-400 mb-3">Weeks 2 – 3</p>
            <h4 class="font-serif text-2xl mb-3">Construction</h4>
            <p class="text-sm text-ink-600 leading-relaxed font-light">Seams are sewn, hand-finished, beaded if your design calls for it, and pressed between every step.</p>
          </li>
          <li class="border-b border-ink-200 py-10 lg:py-12 sm:pl-8 lg:pl-10">
            <p class="editorial-label text-ink-400 mb-3">Week 4</p>
            <h4 class="font-serif text-2xl mb-3">Inspection & Travel</h4>
            <p class="text-sm text-ink-600 leading-relaxed font-light">Final review, hand-fold into the travel box, and insured shipping to your door anywhere in the world.</p>
          </li>
        </ol>
      </div>
    </section>

    <!-- CLOSING CTA -->
    <section class="section-pad bg-ink-950 text-white">
      <div class="container-editorial text-center">
        <p class="editorial-label-light text-champagne-400 mb-5">Begin</p>
        <h2 class="font-serif text-4xl sm:text-5xl lg:text-6xl text-white leading-tight max-w-3xl mx-auto text-balance">
          We are ready when you are.
        </h2>
        <p class="text-ink-300 mt-6 max-w-xl mx-auto font-light leading-relaxed">
          Begin with a gown from the new bridal collection, or book a virtual fitting with a stylist who will walk you through the entire atelier process.
        </p>
        <div class="flex flex-wrap justify-center gap-4 mt-10">
          <RouterLink to="/wedding-dresses" class="btn-champagne">Shop the Collection</RouterLink>
          <RouterLink to="/contact" class="btn-ghost">Book a Virtual Fitting</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>
