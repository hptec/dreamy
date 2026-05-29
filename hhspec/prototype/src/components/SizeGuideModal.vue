<script setup>
import { useI18n } from 'vue-i18n'
import { TransitionRoot, TransitionChild, Dialog, DialogPanel, TabGroup, TabList, Tab, TabPanels, TabPanel } from '@headlessui/vue'

defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
const { t } = useI18n()

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
  { t: 'Bust', d: 'Measure around the fullest part of your bust, keeping the tape level.' },
  { t: 'Waist', d: 'Measure around the narrowest part of your natural waistline.' },
  { t: 'Hips', d: 'Measure around the fullest part of your hips, about 20cm below the waist.' },
  { t: 'Hollow to Floor', d: 'From the hollow between your collarbones straight down to the floor, barefoot.' },
]
</script>

<template>
  <TransitionRoot :show="open" as="template">
    <Dialog @close="emit('close')" class="relative z-[65]">
      <TransitionChild as="template" enter="duration-300 ease-out" enter-from="opacity-0" enter-to="opacity-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-ink-950/60 backdrop-blur-sm" />
      </TransitionChild>
      <div class="fixed inset-0 flex items-center justify-center p-4">
        <TransitionChild as="template" enter="duration-300 ease-editorial" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100" leave="duration-200 ease-in" leave-from="opacity-100" leave-to="opacity-0 scale-95">
          <DialogPanel class="w-full max-w-2xl bg-white max-h-[88vh] overflow-y-auto">
            <div class="flex items-center justify-between p-6 border-b border-ink-100 sticky top-0 bg-white">
              <h2 class="font-serif text-2xl">{{ t('product.sizeGuide') }}</h2>
              <button @click="emit('close')" class="text-ink-400 hover:text-ink-950" aria-label="Close"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 6l12 12M6 18L18 6" /></svg></button>
            </div>
            <TabGroup>
              <TabList class="flex border-b border-ink-100 px-6">
                <Tab v-for="tab in ['Size Chart', 'How to Measure', 'Conversion']" :key="tab" v-slot="{ selected }" as="template">
                  <button class="py-4 px-4 editorial-label text-[11px] border-b-2 -mb-px transition-colors focus:outline-none" :class="selected ? 'border-ink-950 text-ink-950' : 'border-transparent text-ink-400 hover:text-ink-700'">{{ tab }}</button>
                </Tab>
              </TabList>
              <TabPanels class="p-6">
                <TabPanel>
                  <p class="text-sm text-ink-500 mb-4">All measurements in inches. For the truest fit, choose Custom Size at checkout.</p>
                  <table class="w-full text-sm">
                    <thead><tr class="editorial-label text-ink-400 text-[10px] border-b border-ink-200"><th class="text-left py-2">US</th><th class="text-left py-2">EU</th><th class="text-left py-2">UK</th><th class="text-left py-2">Bust</th><th class="text-left py-2">Waist</th><th class="text-left py-2">Hips</th></tr></thead>
                    <tbody>
                      <tr v-for="(r, i) in sizeRows" :key="i" class="border-b border-ink-50 hover:bg-ink-50/60"><td v-for="(c, j) in r" :key="j" class="py-2.5 tabular-nums" :class="j === 0 ? 'font-medium text-ink-900' : 'text-ink-600'">{{ c }}</td></tr>
                    </tbody>
                  </table>
                </TabPanel>
                <TabPanel>
                  <div class="grid sm:grid-cols-2 gap-5">
                    <div v-for="(m, i) in measureSteps" :key="i" class="border border-ink-100 p-5">
                      <span class="editorial-label text-champagne-600 text-[10px]">Step {{ i + 1 }}</span>
                      <h4 class="font-serif text-lg mt-1 mb-1">{{ m.t }}</h4>
                      <p class="text-sm text-ink-500 leading-relaxed">{{ m.d }}</p>
                    </div>
                  </div>
                  <p class="text-xs text-ink-400 mt-5">Tip: have a friend help and keep the tape snug but not tight. Wear the undergarments you plan to wear on the day.</p>
                </TabPanel>
                <TabPanel>
                  <p class="text-sm text-ink-500 mb-4">Quick reference for international conversions.</p>
                  <div class="grid grid-cols-3 gap-3">
                    <div v-for="r in sizeRows.slice(0,9)" :key="r[0]" class="border border-ink-100 p-3 text-center">
                      <p class="font-serif text-lg">{{ r[0] }}</p>
                      <p class="editorial-label text-ink-400 text-[9px] mt-1">{{ r[1] }} · {{ r[2] }}</p>
                    </div>
                  </div>
                </TabPanel>
              </TabPanels>
            </TabGroup>
          </DialogPanel>
        </TransitionChild>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
