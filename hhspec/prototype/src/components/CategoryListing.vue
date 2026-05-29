<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption, TransitionRoot, TransitionChild, Dialog, DialogPanel } from '@headlessui/vue'
import { CATEGORIES, getByCategory, filterProducts, sortProducts, COLOR_MAP } from '../data/catalog'
import ProductCard from './ProductCard.vue'
import FilterSidebar from './FilterSidebar.vue'

const props = defineProps({ category: { type: String, required: true } })
const route = useRoute()
const { t } = useI18n()

const meta = computed(() => CATEGORIES[props.category] || CATEGORIES.wedding)
const SORTS = [
  { id: 'recommended', label: 'sort.recommended' },
  { id: 'newest', label: 'sort.newest' },
  { id: 'priceAsc', label: 'sort.priceAsc' },
  { id: 'priceDesc', label: 'sort.priceDesc' },
  { id: 'bestSelling', label: 'sort.bestSelling' },
]
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
  if (q.silhouette) f.silhouettes = [q.silhouette]
  if (q.color) f.colors = [q.color]
  if (q.shipsNow) f.shipsNow = true
  filters.value = f
  if (q.sort) sort.value = q.sort
}
onMounted(applyQuery)
watch(() => route.query, applyQuery)

const base = computed(() => getByCategory(props.category))
const filtered = computed(() => sortProducts(filterProducts(base.value, filters.value), sort.value))
const shown = computed(() => filtered.value.slice(0, visible.value))

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
      <img :src="meta.hero" :alt="meta.headline" class="absolute inset-0 w-full h-full object-cover opacity-85" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/70 to-ink-950/10"></div>
      <div class="relative container-editorial pb-12">
        <p class="editorial-label-light text-champagne-300 mb-3">Maison Eden</p>
        <h1 class="font-serif text-white text-5xl sm:text-6xl">{{ meta.headline }}</h1>
        <p class="text-ink-100/85 mt-3 max-w-lg font-light">{{ meta.tagline }}</p>
      </div>
    </section>

    <div class="container-editorial py-10">
      <div class="grid lg:grid-cols-[260px_1fr] gap-10">
        <!-- desktop filters -->
        <div class="hidden lg:block">
          <div class="sticky top-28">
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
            <button v-for="(c, i) in chips" :key="i" @click="removeChip(c)" class="inline-flex items-center gap-2 bg-ink-50 hover:bg-ink-100 text-ink-700 text-xs px-3 py-1.5 transition-colors">
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
            <p class="font-serif text-2xl mb-2">{{ t('status.noResults') }}</p>
            <p class="text-sm text-ink-500 mb-6">{{ t('status.noResultsDesc') }}</p>
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
                <button @click="mobileFilterOpen = false" aria-label="Close"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
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
