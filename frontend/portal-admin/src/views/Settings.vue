<script setup lang="ts">
// PAGE-TRD-A04 / COMP-TRD-A07~A08：汇率管理 + 结算配置 + 税费规则（权限 /settings；复用 tab+panel+data-table 风格）
// order-flow-complete E/F：三 tab（rates / checkout / tax），支持 ?tab= 深链；面板拆为独立组件按需挂载
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import ExchangeRatesPanel from '@/components/settings/ExchangeRatesPanel.vue'
import CheckoutConfigPanel from '@/components/settings/CheckoutConfigPanel.vue'
import TaxRulesPanel from '@/components/settings/TaxRulesPanel.vue'
import { useTradingSettingsStore } from '@/stores/tradingSettings'
import { useToastStore } from '@/stores/toast'
import { describeError } from '@/constants/tradingErrors'

type Tab = 'rates' | 'checkout' | 'tax'
const TABS: { k: Tab; label: string }[] = [
  { k: 'rates', label: '汇率管理' },
  { k: 'checkout', label: '结算配置' },
  { k: 'tax', label: '税费规则' },
]

const route = useRoute()
const router = useRouter()
const store = useTradingSettingsStore()
const toast = useToastStore()

const tab = ref<Tab>('rates')
const isTab = (v: unknown): v is Tab => v === 'rates' || v === 'checkout' || v === 'tax'

/** 已挂载过的 tab 保持存活（v-show），未访问过的按需挂载，避免首屏并发 5 个请求 */
const visited = ref<Set<Tab>>(new Set(['rates']))
const visitedList = computed(() => Array.from(visited.value))

function select(k: Tab) {
  tab.value = k
  visited.value.add(k)
  router.replace({ query: { ...route.query, tab: k === 'rates' ? undefined : k } })
}

watch(
  () => route.query.tab,
  (q) => {
    if (isTab(q) && q !== tab.value) {
      tab.value = q
      visited.value.add(q)
    }
  },
)

onMounted(() => {
  const q = route.query.tab
  if (isTab(q)) {
    tab.value = q
    visited.value.add(q)
  }
  store.fetchRates().catch((e) => toast.error(describeError(e, '加载汇率失败')))
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Settings" title="汇率、税费与结算配置" subtitle="多币种汇率维护、目的地税费规则与结算/履约参数（仅影响新订单）" />

    <div class="mb-4 flex gap-1 border-b border-line" data-testid="settings-tabs">
      <button
        v-for="t in TABS"
        :key="t.k"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t.k ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        :data-testid="`settings-tab-${t.k}`"
        @click="select(t.k)"
      >{{ t.label }}</button>
    </div>

    <div v-show="tab === 'rates'"><ExchangeRatesPanel /></div>
    <div v-if="visitedList.includes('checkout')" v-show="tab === 'checkout'"><CheckoutConfigPanel /></div>
    <div v-if="visitedList.includes('tax')" v-show="tab === 'tax'"><TaxRulesPanel /></div>
  </div>
</template>
