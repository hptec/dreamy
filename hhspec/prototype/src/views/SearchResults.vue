<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption, TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { searchProducts, filterProducts, sortProducts, ALL_PRODUCTS, COLOR_MAP } from '../data/catalog'
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

const SUGGESTED_CHIPS = ['A-Line', 'Lace', 'Cabernet', 'Bridesmaid', 'Veil', 'Mermaid', 'Sequins', 'Ivory']

const query = computed(() => (route.query.q || '').toString())
const sort = ref('recommended')
const visible = ref(9)
const mobileFilterOpen = ref(false)

function emptyFilters() {
  return { colors: [], silhouettes: [], fabrics: [], necklines: [], shipsNow: false, priceMin: null, priceMax: null }
}
const filters = ref(emptyFilters())

function resetAll() {
  filters.value = emptyFilters()
  sort.value = 'recommended'
  visible.value = 9
}

onMounted(resetAll)
watch(() => route.query.q, () => { resetAll() })

const baseResults = computed(() => (query.value ? searchProducts(query.value) : []))
const filtered = computed(() => sortProducts(filterProducts(baseResults.value, filters.value), sort.value))
const shown = computed(() => filtered.value.slice(0, visible.value))

// Curated picks shown when query is empty OR search returns nothing
const bestSellers = computed(() =>
  ALL_PRODUCTS.filter((p) => p.tags?.includes('best-seller')).slice(0, 8),
)
const curated = computed(() => ALL_PRODUCTS.slice(0, 8))

function goChip(term) {
  router.push({ path: '/search', query: { q: term } })
}

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
    <!-- search header -->
    <section class="border-b border-ink-100 bg-white">
      <div class="container-editorial py-14 sm:py-20">
        <p class="editorial-label text-champagne-600 mb-4">Search</p>
        <div v-if="query">
          <h1 class="font-serif text-4xl sm:text-6xl text-ink-950 leading-[1.05] text-balance">
            Results for <span class="italic text-ink-700">&lsquo;{{ query }}&rsquo;</span>
          </h1>
          <p class="text-sm text-ink-500 mt-5">
            <span class="text-ink-950 tabular-nums">{{ baseResults.length }}</span> {{ t('common.results') }}
            <span v-if="baseResults.length" class="text-ink-300 mx-2">·</span>
            <span v-if="baseResults.length" class="text-ink-400">Refine with filters below</span>
          </p>
        </div>
        <div v-else>
          <h1 class="font-serif text-4xl sm:text-6xl text-ink-950 leading-[1.05] text-balance">Find your gown</h1>
          <p class="text-base sm:text-lg text-ink-500 mt-5 font-light max-w-xl leading-relaxed">Search for silhouettes, colors, fabrics or styles &mdash; or explore a few we love below.</p>
          <div class="flex flex-wrap gap-2 mt-7">
            <button
              v-for="term in SUGGESTED_CHIPS"
              :key="term"
              @click="goChip(term)"
              class="inline-flex items-center gap-2 border border-ink-200 hover:border-ink-950 text-ink-700 hover:text-ink-950 text-xs px-4 py-2 transition-colors"
            >
              <svg class="w-3 h-3 text-champagne-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="10.5" cy="10.5" r="6.5" /><path stroke-linecap="round" d="M20 20l-4.35-4.35" /></svg>
              {{ term }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- HAS RESULTS -->
    <div v-if="query && baseResults.length" class="container-editorial py-10">
      <div class="grid lg:grid-cols-[260px_1fr] gap-10">
        <div class="hidden lg:block">
          <div class="sticky top-28">
            <FilterSidebar v-model="filters" @clear="clearAll" />
          </div>
        </div>

        <div>
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

          <div v-if="chips.length" class="flex flex-wrap gap-2 pt-5">
            <button v-for="(c, i) in chips" :key="`${c.group}-${c.value}-${i}`" @click="removeChip(c)" class="inline-flex items-center gap-2 bg-ink-50 hover:bg-ink-100 text-ink-700 text-xs px-3 py-1.5 transition-colors">
              {{ c.label }}
              <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg>
            </button>
            <button @click="clearAll" class="editorial-label text-ink-400 hover:text-ink-950 text-[10px] self-center ml-2">{{ t('common.clearAll') }}</button>
          </div>

          <div v-if="shown.length" class="grid grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-10 lg:gap-x-6 pt-8">
            <ProductCard v-for="p in shown" :key="p.id" :product="p" show-rating />
          </div>

          <div v-else class="py-24 text-center">
            <p class="font-serif text-2xl mb-2">No matches with these filters</p>
            <p class="text-sm text-ink-500 mb-6">Try removing a filter to see more.</p>
            <button class="btn-outline" @click="clearAll">{{ t('common.clearAll') }}</button>
          </div>

          <div v-if="visible < filtered.length" class="text-center pt-14">
            <p class="text-xs text-ink-400 mb-4">{{ t('common.showing') }} {{ shown.length }} {{ t('common.of') }} {{ filtered.length }}</p>
            <button class="btn-outline" @click="visible += 9">{{ t('common.loadMore') }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- EMPTY STATE: explicit query, no matches -->
    <div v-else-if="query && !baseResults.length" class="container-editorial py-20">
      <div class="max-w-2xl mx-auto text-center mb-16">
        <div class="w-16 h-16 mx-auto grid place-items-center border border-champagne-300 text-champagne-600 mb-7">
          <svg class="w-7 h-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2"><circle cx="10.5" cy="10.5" r="6.5" /><path stroke-linecap="round" d="M20 20l-4.35-4.35" /></svg>
        </div>
        <p class="editorial-label text-champagne-600 mb-4">Nothing yet</p>
        <h2 class="font-serif text-3xl sm:text-5xl text-ink-950 leading-tight text-balance">{{ t('status.noResults') }}</h2>
        <p class="text-base text-ink-500 mt-5 leading-relaxed">{{ t('status.noResultsDesc') }} Or browse one of these popular searches.</p>

        <div class="flex flex-wrap justify-center gap-2 mt-8">
          <button v-for="term in SUGGESTED_CHIPS" :key="term" @click="goChip(term)" class="inline-flex items-center gap-2 border border-ink-200 hover:border-ink-950 text-ink-700 hover:text-ink-950 text-xs px-4 py-2 transition-colors">
            <svg class="w-3 h-3 text-champagne-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="10.5" cy="10.5" r="6.5" /><path stroke-linecap="round" d="M20 20l-4.35-4.35" /></svg>
            {{ term }}
          </button>
        </div>
        <div class="flex flex-wrap justify-center gap-4 mt-10">
          <RouterLink to="/wedding-dresses" class="btn-ink">Shop Wedding Gowns</RouterLink>
          <RouterLink to="/evening-dresses" class="btn-outline">Browse Evening</RouterLink>
        </div>
      </div>

      <div class="hairline mb-12 mt-4 max-w-md mx-auto" />

      <div class="text-center mb-10">
        <p class="editorial-label text-champagne-600 mb-3">Loved by the Maison</p>
        <h3 class="font-serif text-3xl sm:text-4xl text-ink-950">Best Sellers</h3>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
        <ProductCard v-for="p in bestSellers" :key="p.id" :product="p" />
      </div>
    </div>

    <!-- NO QUERY: editorial curated grid -->
    <div v-else class="container-editorial py-16">
      <div class="text-center mb-10">
        <p class="editorial-label text-champagne-600 mb-3">Editor&rsquo;s picks</p>
        <h3 class="font-serif text-3xl sm:text-4xl text-ink-950">Popular this season</h3>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-10 lg:gap-x-6">
        <ProductCard v-for="p in curated" :key="p.id" :product="p" />
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
