<script setup>
import { RouterLink } from 'vue-router'
import { HERO_IMAGES, PRODUCT_IMAGES, TILE_IMAGES } from '../data/catalog'

const timeline = [
  {
    year: '1987',
    title: 'A maison is born',
    body:
      'Founded in a sun-filled atelier on the Rue de Sévigné, Maison Eden begins with a single seamstress, a roll of duchess satin, and an obsession with the silhouette of light on fabric.',
    img: HERO_IMAGES[3],
  },
  {
    year: '1996',
    title: 'The first New York showroom',
    body:
      'A discreet appointment-only townhouse in the West Village introduces American brides to European couture craft — and a quieter, more architectural language of bridal beauty.',
    img: HERO_IMAGES[6],
  },
  {
    year: '2008',
    title: 'Made-to-order, made universal',
    body:
      'We re-engineer our atelier process so that every gown — at every price — is cut after order, hand-finished by a single seamstress, and shipped insured to the bride.',
    img: HERO_IMAGES[9],
  },
  {
    year: '2018',
    title: 'Ninety shades for the party',
    body:
      'Our bridesmaid program launches a single curated palette of ninety shades, so an entire wedding party can be dressed in one cohesive editorial vocabulary.',
    img: HERO_IMAGES[7],
  },
  {
    year: '2026',
    title: 'A maison without borders',
    body:
      'Today our work travels insured to more than sixty countries, every gown still cut to order, every stitch still answered by the woman who placed it.',
    img: HERO_IMAGES[10],
  },
]

const values = [
  {
    label: 'Patience',
    title: 'A gown is finished when it is finished.',
    body:
      'We refuse the cadence of seasons. A piece leaves the atelier only when its seams, its hand-beading and its hem have each been signed off by the woman who made them.',
  },
  {
    label: 'Honesty',
    title: 'Fabric, never decoration.',
    body:
      'We choose materials that age beautifully — silk that breathes, lace that softens, satin that holds its weight. We do not disguise the modest with the ornate.',
  },
  {
    label: 'Intimacy',
    title: 'One seamstress, one gown.',
    body:
      'Every Maison Eden piece is made by a single pair of hands from first cut to final press. Her initials are stitched into the inner placket — a quiet signature, only for the bride.',
  },
]
</script>

<template>
  <div class="bg-canvas">
    <!-- HERO -->
    <section class="relative -mt-20 h-screen min-h-[640px] flex items-end overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[2]" alt="The Maison Eden story" class="absolute inset-0 w-full h-full object-cover animate-kenburns" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/80 via-ink-950/20 to-ink-950/40"></div>
      <div class="relative container-editorial pb-20 sm:pb-28">
        <div class="max-w-3xl animate-fadeUp">
          <p class="editorial-label-light text-champagne-300 mb-5">Our Story · Est. 1987</p>
          <h1 class="font-serif text-white text-5xl sm:text-7xl lg:text-8xl leading-[0.95] text-balance">
            Forty years of one stitch at a time.
          </h1>
          <p class="text-ink-100/90 text-base sm:text-lg mt-6 max-w-xl font-light leading-relaxed">
            A maison built on patience, on cloth, and on the belief that the most important dress a woman will ever wear deserves to be made for her — and only her.
          </p>
        </div>
      </div>
    </section>

    <!-- OPENING ESSAY -->
    <section class="section-pad bg-canvas">
      <div class="container-narrow">
        <p class="editorial-label text-champagne-600 mb-5 text-center">A letter from the founder</p>
        <h2 class="font-serif text-4xl sm:text-5xl text-center mb-12 leading-tight text-balance">
          We did not set out to build a brand. <br class="hidden sm:block" />We set out to make a dress.
        </h2>
        <div class="prose-essay space-y-6 text-ink-700 text-[17px] sm:text-lg leading-[1.8] font-light">
          <p>
            In the spring of 1987, in a one-room atelier above a flower market in the Marais, my mother cut her first bridal gown. She was twenty-eight years old. She owned three pairs of scissors, a single dress form she had rescued from a closing house, and a small, almost religious conviction: that what a bride wore on the morning of her wedding deserved the same quiet seriousness as the vows she spoke that afternoon.
          </p>
          <p>
            She did not think of it as a business. She thought of it as a debt she owed to the women who would trust her — a debt repaid only by hours, by hand-stitching, by the refusal to cut a corner where a corner was tempting.
          </p>
          <p>
            Almost forty years later, the maison is larger and the atelier louder, but the work has not changed. A gown is cut after it is ordered, by a single woman, who finishes it herself. Her initials are stitched inside the placket — a private gesture between her and the bride who will wear it.
          </p>
          <p>
            We have grown only as far as that single thread will carry us, and not a step further.
          </p>
        </div>
        <p class="font-serif italic text-2xl text-ink-800 mt-12 text-center">— Adèle Moreau, Creative Director</p>
      </div>
    </section>

    <!-- VALUES -->
    <section class="section-pad bg-ink-950 text-white">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-10 lg:gap-20 mb-16 items-end">
          <div class="lg:col-span-5">
            <p class="editorial-label-light text-champagne-400 mb-4">The Three Disciplines</p>
            <h2 class="font-serif text-4xl sm:text-5xl text-white leading-tight">A philosophy held by hand.</h2>
          </div>
          <p class="lg:col-span-7 text-ink-300 text-lg font-light leading-relaxed">
            We are not a brand of slogans. The three principles below are the only ones written down — they hang in framed type over the cutting table, and they are the only thing the seamstresses are asked to memorize.
          </p>
        </div>

        <div class="grid md:grid-cols-3 gap-px bg-white/10">
          <div v-for="v in values" :key="v.label" class="bg-ink-950 p-10 lg:p-12">
            <p class="editorial-label-light text-champagne-400 mb-6">{{ v.label }}</p>
            <h3 class="font-serif text-2xl sm:text-3xl text-white leading-tight mb-5">{{ v.title }}</h3>
            <p class="text-ink-300 text-sm leading-relaxed font-light">{{ v.body }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- TIMELINE -->
    <section class="section-pad bg-white">
      <div class="container-editorial">
        <div class="text-center mb-20">
          <p class="editorial-label text-champagne-600 mb-3">A Brief Chronology</p>
          <h2 class="font-serif text-4xl sm:text-5xl text-balance">Forty years, in five moments.</h2>
        </div>

        <div class="relative">
          <!-- vertical rail -->
          <div class="hidden lg:block absolute left-1/2 top-0 bottom-0 w-px bg-ink-200" aria-hidden="true"></div>
          <ol class="space-y-20 lg:space-y-32">
            <li
              v-for="(item, i) in timeline"
              :key="item.year"
              class="grid lg:grid-cols-2 gap-10 lg:gap-20 items-center"
              :class="i % 2 === 1 ? 'lg:[&>*:first-child]:order-2' : ''"
            >
              <div class="aspect-[4/5] overflow-hidden bg-ink-100">
                <img :src="item.img" :alt="item.title" class="w-full h-full object-cover" />
              </div>
              <div :class="i % 2 === 1 ? 'lg:pr-4' : 'lg:pl-4'">
                <p class="font-serif text-7xl lg:text-8xl text-champagne-500 leading-none mb-6 tabular-nums">{{ item.year }}</p>
                <h3 class="font-serif text-3xl sm:text-4xl leading-tight mb-5 text-balance">{{ item.title }}</h3>
                <p class="text-ink-600 text-base leading-relaxed font-light max-w-md">{{ item.body }}</p>
              </div>
            </li>
          </ol>
        </div>
      </div>
    </section>

    <!-- BIG IMAGERY BREAK -->
    <section class="relative h-[80vh] min-h-[560px] overflow-hidden bg-ink-950">
      <img :src="HERO_IMAGES[4]" alt="The maison today" class="absolute inset-0 w-full h-full object-cover opacity-80" />
      <div class="absolute inset-0 bg-gradient-to-r from-ink-950/60 via-transparent to-ink-950/30"></div>
      <div class="relative container-editorial h-full flex items-center">
        <blockquote class="max-w-xl">
          <p class="editorial-label-light text-champagne-300 mb-6">A note on time</p>
          <p class="font-serif text-white text-3xl sm:text-4xl lg:text-5xl leading-tight italic text-balance">
            “We are not in the business of fashion. We are in the business of giving you something to keep — and one day, to pass on.”
          </p>
        </blockquote>
      </div>
    </section>

    <!-- ATELIER & PEOPLE GRID -->
    <section class="section-pad bg-canvas">
      <div class="container-editorial">
        <div class="grid lg:grid-cols-12 gap-12 mb-16">
          <div class="lg:col-span-5">
            <p class="editorial-label text-champagne-600 mb-4">The Hands Behind the Work</p>
            <h2 class="font-serif text-4xl sm:text-5xl leading-tight">The maison, in plain numbers.</h2>
          </div>
          <p class="lg:col-span-7 lg:pt-2 text-ink-600 text-lg font-light leading-relaxed self-end">
            We are small on purpose. We have grown only as far as the cutting table allows us to keep our promise — that no two gowns are made by the same pair of hands on the same afternoon.
          </p>
        </div>

        <dl class="grid sm:grid-cols-2 lg:grid-cols-4 border-t border-ink-200">
          <div class="border-b border-ink-200 sm:border-r py-10">
            <dt class="editorial-label text-ink-400 mb-3">Master Seamstresses</dt>
            <dd class="font-serif text-6xl text-ink-950 tabular-nums">38</dd>
          </div>
          <div class="border-b border-ink-200 lg:border-r py-10 sm:pl-8 lg:pl-10">
            <dt class="editorial-label text-ink-400 mb-3">Hours, per gown, on average</dt>
            <dd class="font-serif text-6xl text-ink-950 tabular-nums">62</dd>
          </div>
          <div class="border-b border-ink-200 sm:border-r py-10 lg:pl-10">
            <dt class="editorial-label text-ink-400 mb-3">Brides served, since 1987</dt>
            <dd class="font-serif text-6xl text-ink-950 tabular-nums">41k</dd>
          </div>
          <div class="border-b border-ink-200 py-10 sm:pl-8 lg:pl-10">
            <dt class="editorial-label text-ink-400 mb-3">Countries we ship to</dt>
            <dd class="font-serif text-6xl text-ink-950 tabular-nums">63</dd>
          </div>
        </dl>

        <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 lg:gap-4 mt-16">
          <div v-for="img in [PRODUCT_IMAGES[12], TILE_IMAGES[10], PRODUCT_IMAGES[34], TILE_IMAGES[18]]" :key="img" class="aspect-[3/4] overflow-hidden bg-ink-100">
            <img :src="img" alt="Atelier" class="w-full h-full object-cover transition-transform duration-700 hover:scale-105" />
          </div>
        </div>
      </div>
    </section>

    <!-- CLOSING CTA -->
    <section class="section-pad bg-ink-950 text-white">
      <div class="container-editorial grid lg:grid-cols-2 gap-12 lg:gap-20 items-center">
        <div>
          <p class="editorial-label-light text-champagne-400 mb-5">Begin Your Gown</p>
          <h2 class="font-serif text-4xl sm:text-5xl text-white leading-tight text-balance">
            Forty years of stitches. One, finally, for you.
          </h2>
          <p class="text-ink-300 mt-6 text-lg font-light leading-relaxed max-w-md">
            Step inside the atelier — or begin with a gown from the new collection. We are honoured, either way.
          </p>
          <div class="flex flex-wrap gap-4 mt-9">
            <RouterLink to="/wedding-dresses" class="btn-champagne">Shop Bridal Couture</RouterLink>
            <RouterLink to="/atelier" class="btn-ghost">Inside the Atelier</RouterLink>
          </div>
        </div>
        <div class="aspect-[4/5] overflow-hidden bg-ink-800">
          <img :src="HERO_IMAGES[11]" alt="Maison Eden gown" class="w-full h-full object-cover" />
        </div>
      </div>
    </section>
  </div>
</template>
