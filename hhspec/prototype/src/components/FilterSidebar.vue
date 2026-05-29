<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Disclosure, DisclosureButton, DisclosurePanel } from '@headlessui/vue'
import { COLOR_FAMILIES, COLORS, SILHOUETTES, FABRICS, NECKLINES } from '../data/catalog'

const props = defineProps({
  modelValue: { type: Object, required: true },
})
const emit = defineEmits(['update:modelValue', 'clear'])
const { t } = useI18n()

const PRICE_RANGES = [
  { label: 'Under $200', min: 0, max: 200 },
  { label: '$200 – $500', min: 200, max: 500 },
  { label: '$500 – $1000', min: 500, max: 1000 },
  { label: '$1000+', min: 1000, max: 99999 },
]

const familyColor = (id) => COLORS.find((c) => c.family === id)?.hex || '#ccc'

function toggle(group, value) {
  const next = { ...props.modelValue }
  const arr = new Set(next[group] || [])
  arr.has(value) ? arr.delete(value) : arr.add(value)
  next[group] = [...arr]
  emit('update:modelValue', next)
}
function isOn(group, value) { return (props.modelValue[group] || []).includes(value) }
function setPrice(r) {
  const next = { ...props.modelValue }
  if (next.priceMin === r.min && next.priceMax === r.max) { next.priceMin = null; next.priceMax = null }
  else { next.priceMin = r.min; next.priceMax = r.max }
  emit('update:modelValue', next)
}
function toggleShips() { emit('update:modelValue', { ...props.modelValue, shipsNow: !props.modelValue.shipsNow }) }

const activeCount = computed(() => {
  const m = props.modelValue
  return (m.colors?.length || 0) + (m.silhouettes?.length || 0) + (m.fabrics?.length || 0) + (m.necklines?.length || 0) + (m.shipsNow ? 1 : 0) + (m.priceMin != null ? 1 : 0)
})

const sections = [
  { key: 'colors', titleKey: 'filter.color', type: 'color', items: COLOR_FAMILIES },
  { key: 'silhouettes', titleKey: 'filter.silhouette', type: 'check', items: SILHOUETTES },
  { key: 'fabrics', titleKey: 'filter.fabric', type: 'check', items: FABRICS },
  { key: 'necklines', titleKey: 'filter.neckline', type: 'check', items: NECKLINES },
]
</script>

<template>
  <aside class="space-y-1">
    <div class="flex items-center justify-between pb-4 border-b border-ink-200">
      <h3 class="editorial-label text-ink-900">{{ t('common.filters') }}<span v-if="activeCount" class="text-champagne-600"> ({{ activeCount }})</span></h3>
      <button v-if="activeCount" @click="emit('clear')" class="editorial-label text-ink-400 hover:text-ink-950 text-[10px]">{{ t('common.clearAll') }}</button>
    </div>

    <!-- Ships now -->
    <div class="py-4 border-b border-ink-100 flex items-center justify-between">
      <span class="text-sm text-ink-700">{{ t('filter.inStock') }}</span>
      <button @click="toggleShips" class="w-10 h-5 rounded-full transition-colors relative" :class="modelValue.shipsNow ? 'bg-ink-950' : 'bg-ink-200'">
        <span class="absolute top-0.5 w-4 h-4 bg-white rounded-full transition-all" :class="modelValue.shipsNow ? 'left-[22px]' : 'left-0.5'"></span>
      </button>
    </div>

    <Disclosure v-for="sec in sections" :key="sec.key" :default-open="true" v-slot="{ open }">
      <div class="border-b border-ink-100">
        <DisclosureButton class="w-full flex items-center justify-between py-4">
          <span class="editorial-label text-ink-700">{{ t(sec.titleKey) }}</span>
          <svg class="w-4 h-4 text-ink-400 transition-transform" :class="open ? 'rotate-180' : ''" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
        </DisclosureButton>
        <DisclosurePanel class="pb-5">
          <div v-if="sec.type === 'color'" class="flex flex-wrap gap-2.5">
            <button v-for="f in sec.items" :key="f.id" @click="toggle('colors', f.id)" :title="f.name"
              class="w-7 h-7 rounded-full ring-1 transition-all" :class="isOn('colors', f.id) ? 'ring-2 ring-ink-950 ring-offset-2' : 'ring-ink-200 hover:ring-ink-400'"
              :style="{ background: familyColor(f.id) }" />
          </div>
          <div v-else class="space-y-2.5">
            <label v-for="it in sec.items" :key="it" class="flex items-center gap-3 cursor-pointer group">
              <span class="w-4 h-4 border flex items-center justify-center transition-colors" :class="isOn(sec.key, it) ? 'bg-ink-950 border-ink-950' : 'border-ink-300 group-hover:border-ink-500'">
                <svg v-if="isOn(sec.key, it)" class="w-3 h-3 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" /></svg>
              </span>
              <input type="checkbox" class="sr-only" :checked="isOn(sec.key, it)" @change="toggle(sec.key, it)" />
              <span class="text-sm text-ink-600 group-hover:text-ink-950">{{ it }}</span>
            </label>
          </div>
        </DisclosurePanel>
      </div>
    </Disclosure>

    <!-- Price -->
    <Disclosure :default-open="true" v-slot="{ open }">
      <div class="border-b border-ink-100">
        <DisclosureButton class="w-full flex items-center justify-between py-4">
          <span class="editorial-label text-ink-700">{{ t('filter.price') }}</span>
          <svg class="w-4 h-4 text-ink-400 transition-transform" :class="open ? 'rotate-180' : ''" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
        </DisclosureButton>
        <DisclosurePanel class="pb-5 space-y-2.5">
          <label v-for="r in PRICE_RANGES" :key="r.label" class="flex items-center gap-3 cursor-pointer group">
            <span class="w-4 h-4 rounded-full border flex items-center justify-center" :class="modelValue.priceMin === r.min && modelValue.priceMax === r.max ? 'border-ink-950' : 'border-ink-300 group-hover:border-ink-500'">
              <span v-if="modelValue.priceMin === r.min && modelValue.priceMax === r.max" class="w-2 h-2 rounded-full bg-ink-950"></span>
            </span>
            <input type="radio" class="sr-only" name="price" @change="setPrice(r)" />
            <span class="text-sm text-ink-600 group-hover:text-ink-950">{{ r.label }}</span>
          </label>
        </DisclosurePanel>
      </div>
    </Disclosure>
  </aside>
</template>
