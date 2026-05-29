<script setup>
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption } from '@headlessui/vue'
import { SAMPLE_ORDERS } from '../data/catalog'
import { useCurrencyStore } from '../stores/currency'
import AccountLayout from '../components/AccountLayout.vue'

const { t } = useI18n()
const currency = useCurrencyStore()

const STATUS_OPTIONS = [
  { id: 'all', label: 'All orders' },
  { id: 'processing', label: 'Processing' },
  { id: 'inProduction', label: 'In Production' },
  { id: 'shipped', label: 'Shipped' },
  { id: 'delivered', label: 'Delivered' },
  { id: 'cancelled', label: 'Cancelled' },
]

const status = ref('all')
const search = ref('')

const filteredOrders = computed(() => {
  return SAMPLE_ORDERS.filter((o) => {
    if (status.value !== 'all' && o.status !== status.value) return false
    if (search.value && !o.id.toLowerCase().includes(search.value.toLowerCase())) return false
    return true
  })
})

const selectedStatus = computed(() => STATUS_OPTIONS.find((s) => s.id === status.value) || STATUS_OPTIONS[0])

const statusKeys = {
  processing: 'account.status.processing',
  inProduction: 'account.status.inProduction',
  shipped: 'account.status.shipped',
  delivered: 'account.status.delivered',
  cancelled: 'account.status.cancelled',
}
function statusLabel(s) { return t(statusKeys[s] || 'account.status.processing') }
function statusTone(s) {
  if (s === 'shipped') return 'bg-ink-950 text-white'
  if (s === 'delivered') return 'bg-champagne-100 text-champagne-800 border border-champagne-300'
  if (s === 'cancelled') return 'bg-ink-100 text-ink-500'
  if (s === 'inProduction') return 'bg-white text-ink-900 border border-ink-300'
  return 'bg-white text-ink-900 border border-ink-300'
}

function totalQty(items) { return items.reduce((n, i) => n + i.qty, 0) }
</script>

<template>
  <AccountLayout>
    <div class="flex items-end justify-between mb-10">
      <div>
        <p class="editorial-label text-champagne-600 mb-3">{{ t('account.orders') }}</p>
        <h2 class="font-serif text-3xl sm:text-4xl">{{ t('account.orderHistory') }}</h2>
      </div>
      <p class="hidden sm:block editorial-label text-ink-400 text-[10px]">{{ SAMPLE_ORDERS.length }} orders since 2024</p>
    </div>

    <!-- Toolbar -->
    <div class="flex flex-col lg:flex-row gap-4 lg:items-center lg:justify-between pb-5 border-b border-ink-200">
      <div class="flex flex-wrap gap-2">
        <button
          v-for="opt in STATUS_OPTIONS"
          :key="opt.id"
          type="button"
          class="px-4 py-2 text-xs border transition-colors"
          :class="status === opt.id ? 'border-ink-950 bg-ink-950 text-white' : 'border-ink-200 text-ink-600 hover:border-ink-950 hover:text-ink-950'"
          @click="status = opt.id"
        >{{ opt.label }}</button>
      </div>
      <div class="flex items-center gap-3">
        <div class="relative">
          <svg class="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="7" /><path stroke-linecap="round" d="M21 21l-4.3-4.3" /></svg>
          <input
            v-model="search"
            type="search"
            placeholder="Search order #ME-2026-…"
            class="field !py-2.5 !pl-9 text-sm w-full lg:w-72"
          />
        </div>
        <Listbox v-model="status" class="lg:hidden">
          <div class="relative">
            <ListboxButton class="editorial-label text-[11px] inline-flex items-center gap-2 border border-ink-300 px-4 py-2.5 min-w-[160px] justify-between">
              <span>{{ selectedStatus.label }}</span>
              <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" d="M6 9l6 6 6-6" /></svg>
            </ListboxButton>
            <ListboxOptions class="absolute right-0 mt-2 w-56 bg-white shadow-lg ring-1 ring-ink-900/5 py-1 z-20 focus:outline-none">
              <ListboxOption v-for="s in STATUS_OPTIONS" :key="s.id" :value="s.id" v-slot="{ active, selected }">
                <div :class="[active ? 'bg-ink-50' : '', selected ? 'text-ink-950' : 'text-ink-600', 'px-4 py-2.5 text-sm cursor-pointer']">{{ s.label }}</div>
              </ListboxOption>
            </ListboxOptions>
          </div>
        </Listbox>
      </div>
    </div>

    <!-- Orders list -->
    <div v-if="filteredOrders.length" class="space-y-5 mt-8">
      <article
        v-for="o in filteredOrders"
        :key="o.id"
        class="bg-white border border-ink-200 hover:border-ink-950 transition-colors"
      >
        <!-- Header bar -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 px-6 py-5 border-b border-ink-100 items-center">
          <div>
            <p class="editorial-label text-ink-400 text-[10px]">Order #</p>
            <p class="text-sm text-ink-950 tabular-nums mt-1">{{ o.id }}</p>
          </div>
          <div>
            <p class="editorial-label text-ink-400 text-[10px]">Placed on</p>
            <p class="text-sm text-ink-950 mt-1">{{ o.date }}</p>
          </div>
          <div>
            <p class="editorial-label text-ink-400 text-[10px]">Total</p>
            <p class="text-sm text-ink-950 tabular-nums mt-1">{{ currency.format(o.total) }}</p>
          </div>
          <div class="flex sm:justify-end">
            <span class="editorial-label text-[10px] px-3 py-1.5 inline-block" :class="statusTone(o.status)">{{ statusLabel(o.status) }}</span>
          </div>
        </div>

        <!-- Items strip -->
        <div class="grid sm:grid-cols-[1fr_auto] gap-6 px-6 py-6 items-center">
          <div class="flex gap-4 min-w-0">
            <div
              v-for="(it, i) in o.items.slice(0, 3)"
              :key="i"
              class="relative aspect-[3/4] w-20 sm:w-24 bg-ink-50 overflow-hidden shrink-0"
            >
              <img :src="it.img" :alt="it.name" class="absolute inset-0 w-full h-full object-cover" />
            </div>
            <div class="min-w-0 flex-1 self-center">
              <p class="font-serif text-lg text-ink-950 truncate">{{ o.items[0].name }}</p>
              <p class="text-xs text-ink-500 mt-1.5">
                {{ totalQty(o.items) }} {{ totalQty(o.items) === 1 ? t('cart.item') : t('cart.items') }} · {{ o.items[0].color }} · {{ o.items[0].size }}
              </p>
              <p v-if="o.status !== 'delivered' && o.status !== 'cancelled'" class="text-xs text-champagne-700 mt-1">
                {{ t('checkout.estimatedDelivery') }}: {{ o.estimatedDelivery }}
              </p>
            </div>
          </div>
          <div class="flex gap-2 sm:flex-col">
            <RouterLink :to="`/account/orders/${o.id}`" class="btn-ink btn-sm">View details</RouterLink>
            <RouterLink v-if="o.status === 'shipped'" :to="`/account/orders/${o.id}`" class="btn-outline btn-sm">{{ t('checkout.trackOrder') }}</RouterLink>
            <button v-else type="button" class="btn-outline btn-sm">Reorder</button>
          </div>
        </div>
      </article>
    </div>

    <!-- Empty -->
    <div v-else class="text-center py-24 border border-dashed border-ink-200 mt-8">
      <p class="font-serif text-2xl mb-2">No orders match</p>
      <p class="text-sm text-ink-500 mb-6">Try clearing the filter or adjust your search.</p>
      <button type="button" class="btn-outline btn-sm" @click="status = 'all'; search = ''">{{ t('common.clearAll') }}</button>
    </div>
  </AccountLayout>
</template>
