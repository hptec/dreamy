<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption, TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { CATEGORIES, getByCategory, filterProducts, sortProducts, COLOR_MAP } from '../data/catalog'
import ProductCard from '../components/ProductCard.vue'
import FilterSidebar from '../components/FilterSidebar.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const SORTS = [
  { id: 'recommended', label: 'sort.recommended' },
  { id: 'newest', label: 'sort.newest' },
  { id: 'priceAsc', label: 'sort.priceAsc' },
  { id: 'priceDesc', label: 'sort.priceDesc' },
  { id: 'bestSelling', label: 'sort.bestSelling' },
]

// Sub-category tabs: filter content, keep one fixed editorial hero for stability
const SUBS = [
  { id: 'all', label: 'All Occasions', category: null, tagline: "From a mother's quiet poise to a flower girl's first twirl &mdash; dresses for every loved one in the room." },
  { id: 'mother', label: 'Mother of the Bride', category: 'mother', tagline: 'Refined elegance for the most important guests &mdash; tailored, considered, never overshadowed.' },
  { id: 'guest', label: 'Wedding Guest', category: 'guest', tagline: 'Effortless dresses for every celebration on the calendar &mdash; ceremony to reception.' },
  { id: 'flowergirl', label: 'Flower Girl', category: 'flowergirl', tagline: 'Little dresses for the smallest members of the party &mdash; hand-finished with the same care as the bride&rsquo;s.' },
]

const heroImg = CATEGORIES.mother.hero

const sub = ref('all')
const sort = ref('recommended')
const visible = ref(9)
const mobileFilterOpen = ref(false)

function emptyFilters() {
  return { colors: [], silhouettes: [], fabrics: [], necklines: [], shipsNow: false, priceMin: null, priceMax: null }
}
const filters = ref(emptyFilters())

function applyQuery() {
  const q = route.query
  const f = emptyFilters()
  if (q.color) f.colors = [q.color]
  if (q.silhouette) f.silhouettes = [q.silhouette]
  if (q.shipsNow) f.shipsNow = true
  filters.value = f
  if (q.sort) sort.value = q.sort
  if (q.sub && SUBS.some((s) => s.id === q.sub)) sub.value = q.sub
  else sub.value = 'all'
  visible.value = 9
}
onMounted(applyQuery)
watch(() => route.query, applyQuery)

const activeSub = computed(() => SUBS.find((s) => s.id === sub.value) || SUBS[0])

const base = computed(() => {
  const all = getByCategory('special-occasions')
  if (sub.value === 'all') return all
  return all.filter((p) => p.category === sub.value)
})
const filtered = computed(() => sortProducts(filterProducts(base.value, filters.value), sort.value))
const shown = computed(() => filtered.value.slice(0, visible.value))

function selectSub(id) {
  sub.value = id
  visible.value = 9
  router.replace({ query: { ...route.query, sub: id === 'all' ? undefined : id } })
}

const subCounts = computed(() => {
  const all = getByCategory('special-occasions')
  return {
    all: all.length,
    mother: all.filter((p) => p.category === 'mother').length,
    guest: all.filter((p) => p.category === 'guest').length,
    flowergirl: all.filter((p) => p.category === 'flowergirl').length,
  }
})

const chips = computed(() => {
  const c = []
  filters.value.colors.forEach((x) => c.push({ group: 'colors', value: x, label: COLOR_MAP[x]?.name || x }))
  filters.value.silhouettes.forEach((x) => c.push({ group: 'silhouettes', value: x, label: x }))
  filters.value.fabrics.forEach((x) => c.push({ group: 'fabrics', value: x, label: x }))
  filters.value.necklines.forEach((x) => c.push({ group: 'necklines', value: x, label: x }))
  if (filters.value.shipsNow) c.push({ group: 'shipsNow', value: true, label: 'Ships Now' })
  if (filters.value.priceMin != null) c.push({ group: 'price', value: true, label: `$${filters.value.priceMin}–${filters.value.priceMax >= 99999 ? '+' : '$' + filters.value.priceMax}` })
  return c
})
function removeChip(chip) {
  const f = { ...filters.value }
  if (chip.group === 'shipsNow') f.shipsNow = false
  else if (chip.group === 'price') { f.priceMin = null; f.priceMax = null }
  else f[chip.group] = f[chip.group].filter((x) => x !== chip.value)
  filters.value = f
}
function clearAll() { filters.value = emptyFilters() }
</script>

<template>
  <div>
    <!-- category hero -->
    <section class="relative h-[44vh] min-h-[320px] flex items-end overflow-hidden bg-ink-950">
      <img :src="heroImg" alt="Special Occasions" class="absolute inset-0 w-full h-full object-cover opacity-85" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/80 via-ink-950/30 to-ink-950/10"></div>
      <div class="relative container-editorial pb-12">
        <p class="editorial-label-light text-champagne-300 mb-3">Maison Eden</p>
        <h1 class="font-serif text-white text-5xl sm:text-6xl text-balance">Special Occasions</h1>
        <p class="text-ink-100/85 mt-3 max-w-xl font-light leading-relaxed" v-html="activeSub.tagline"></p>
      </div>
    </section>

    <!-- sub-category tab strip -->
    <section class="border-b border-ink-100 bg-white sticky top-20 z-30">
      <div class="container-editorial">
        <div class="flex items-center gap-1 sm:gap-3 overflow-x-auto no-scrollbar -mx-5 sm:mx-0 px-5 sm:px-0">
          <button
            v-for="s in SUBS"
            :key="s.id"
            @click="selectSub(s.id)"
            class="relative shrink-0 py-5 px-4 sm:px-5 text-[11px] sm:text-xs font-medium uppercase tracking-label transition-colors duration-200 focus:outline-none"
            :class="sub === s.id ? 'text-ink-950' : 'text-ink-400 hover:text-ink-700'"
          >
            <span>{{ s.label }}</span>
            <span class="ml-2 text-[10px] text-ink-300 tabular-nums">{{ subCounts[s.id] }}</span>
            <span class="absolute left-3 right-3 sm:left-4 sm:right-4 bottom-0 h-[2px] transition-all duration-300 ease-editorial" :class="sub === s.id ? 'bg-ink-950' : 'bg-transparent'"></span>
          </button>
        </div>
      </div>
    </section>

    <div class="container-editorial py-10">
      <div class="grid lg:grid-cols-[260px_1fr] gap-10">
        <!-- desktop filters -->
        <div class="hidden lg:block">
          <div class="sticky top-44">
            <FilterSidebar v-model="filters" @clear="clearAll" />
          </div>
        </div>

        <!-- results -->
        <div>
          <!-- toolbar -->
          <div class="flex items-center justify-between gap-4 pb-5 border-b border-ink-200">
            <p class="text-sm text-ink-500"><span class="text-ink-950 tabular-nums">{{ filtered.length }}</span> {{ t('common.results') }}</p>
            <div class="flex items-center gap-3">
              <button class="lg:hidden editorial-label text-[11px] inline-flex items-center gap-2 border border-ink-300 px-4 py-2" @click="mobileFilterOpen = true">
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M3 6h18M6 12h12M10 18h4" /></svg>
                {{ t('common.filters') }}
              </button>
              <Listbox v-model="sort">
                <div class="relative">
                  <ListboxButton class="editorial-label text-[11px] inline-flex items-center gap-2 border border-ink-300 px-4 py-2 min-w-[180px] justify-between">
                    <span>{{ t('common.sortBy') }}: {{ t(SORTS.find(s=>s.id===sort).label) }}</span>
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
                  </ListboxButton>
                  <ListboxOptions class="absolute right-0 mt-2 w-56 bg-white shadow-lg ring-1 ring-ink-900/5 py-1 z-20 focus:outline-none">
                    <ListboxOption v-for="s in SORTS" :key="s.id" :value="s.id" v-slot="{ active, selected }">
                      <div :class="[active ? 'bg-ink-50' : '', selected ? 'text-ink-950' : 'text-ink-600', 'px-4 py-2.5 text-sm cursor-pointer']">{{ t(s.label) }}</div>
                    </ListboxOption>
                  </ListboxOptions>
                </div>
              </Listbox>
            </div>
          </div>

          <!-- chips -->
          <div v-if="chips.length" class="flex flex-wrap gap-2 pt-5">
            <button v-for="(c, i) in chips" :key="`${c.group}-${c.value}-${i}`" @click="removeChip(c)" class="inline-flex items-center gap-2 bg-ink-50 hover:bg-ink-100 text-ink-700 text-xs px-3 py-1.5 transition-colors">
              {{ c.label }}
              <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg>
            </button>
            <button @click="clearAll" class="editorial-label text-ink-400 hover:text-ink-950 text-[10px] self-center ml-2">{{ t('common.clearAll') }}</button>
          </div>

          <!-- grid -->
          <div v-if="shown.length" class="grid grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-10 lg:gap-x-6 pt-8">
            <ProductCard v-for="p in shown" :key="p.id" :product="p" show-rating />
          </div>

          <!-- empty -->
          <div v-else class="py-24 text-center">
            <p class="editorial-label text-champagne-600 mb-4">No matches</p>
            <p class="font-serif text-3xl mb-3 text-ink-950">{{ t('status.noResults') }}</p>
            <p class="text-sm text-ink-500 mb-8 max-w-sm mx-auto">{{ t('status.noResultsDesc') }}</p>
            <button class="btn-outline" @click="clearAll">{{ t('common.clearAll') }}</button>
          </div>

          <!-- load more -->
          <div v-if="visible < filtered.length" class="text-center pt-14">
            <p class="text-xs text-ink-400 mb-4">{{ t('common.showing') }} {{ shown.length }} {{ t('common.of') }} {{ filtered.length }}</p>
            <button class="btn-outline" @click="visible += 9">{{ t('common.loadMore') }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- mobile filter drawer -->
    <TransitionRoot :show="mobileFilterOpen" as="template">
      <Dialog @close="mobileFilterOpen = false" class="relative z-50 lg:hidden">
        <TransitionChild as="template" enter="duration-300" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200" leave-from="opacity-100" leave-to="opacity-0">
          <div class="fixed inset-0 bg-ink-950/50" />
        </TransitionChild>
        <div class="fixed inset-y-0 left-0 max-w-full flex">
          <TransitionChild as="template" enter="transform transition duration-300 ease-editorial" enter-from="-translate-x-full" enter-to="translate-x-0" leave="transform transition duration-200" leave-from="translate-x-0" leave-to="-translate-x-full">
            <DialogPanel class="w-screen max-w-xs bg-white h-full overflow-y-auto p-6">
              <div class="flex items-center justify-between mb-4">
                <h2 class="font-serif text-xl">{{ t('common.filters') }}</h2>
                <button @click="mobileFilterOpen = false" :aria-label="t('common.close')"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
              </div>
              <FilterSidebar v-model="filters" @clear="clearAll" />
              <button class="btn-ink w-full mt-6" @click="mobileFilterOpen = false">{{ t('common.viewAll') }} ({{ filtered.length }})</button>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </TransitionRoot>
  </div>
</template>
