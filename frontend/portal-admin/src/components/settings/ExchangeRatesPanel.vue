<script setup lang="ts">
// COMP-TRD-A07 汇率管理（USD 行只读恒 1；EUR/CAD/AUD/GBP 行内编辑）
// order-flow-complete E：列增来源/同步时间/加价后有效汇率/手工锁定开关；「立即刷新」（manual 模式 409905 提示）；行「历史」弹层（近 30 天）
import { ref } from 'vue'
import Toggle from '@/components/Toggle.vue'
import { useTradingSettingsStore } from '@/stores/tradingSettings'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { validateExchangeRate } from '@/utils/validators'
import { formatDate, formatDateTime } from '@/utils/format'
import { EXCHANGE_RATE_SOURCE_LABEL } from '@/utils/tradingLabels'
import { ExchangeRateSource } from '@/api/types'
import type { ExchangeRate, ExchangeRateHistoryItem } from '@/api/types'
import { PencilSquareIcon, CheckIcon, XMarkIcon, ArrowPathIcon, ClockIcon } from '@heroicons/vue/24/outline'

const store = useTradingSettingsStore()
const toast = useToastStore()

const editingCurrency = ref<string | null>(null)
const rateDraft = ref('')
const rateError = ref('')
const savingRate = ref(false)

function startEditRate(currency: string, rate: number) {
  editingCurrency.value = currency
  rateDraft.value = String(rate)
  rateError.value = ''
}

async function saveRate() {
  if (!editingCurrency.value || savingRate.value) return
  // FORM-TRD-A04：rate>0 数值预校验
  const err = validateExchangeRate(rateDraft.value)
  if (err) {
    rateError.value = err
    return
  }
  savingRate.value = true
  try {
    await store.saveRate(editingCurrency.value, rateDraft.value)
    toast.success('汇率已更新（仅影响新订单锁汇）')
    editingCurrency.value = null
  } catch (e) {
    // 422605/422601 错误回显
    if (e instanceof BizError && (e.code === 422605 || e.code === 422601)) {
      rateError.value = e.message
    } else {
      toast.error(describeError(e, '保存失败'))
    }
  } finally {
    savingRate.value = false
  }
}

async function onToggleOverride(r: ExchangeRate, on: boolean) {
  try {
    await store.toggleManualOverride(r, on)
    toast.success(on ? `${r.currency} 已锁定为手工汇率，供应商刷新将跳过` : `${r.currency} 已解除手工锁定`)
  } catch (e) {
    toast.error(describeError(e, '切换失败'))
  }
}

const refreshNote = ref('')
async function refresh() {
  if (store.refreshing) return
  refreshNote.value = ''
  try {
    const res = await store.refreshRates()
    const skipped = res.skippedCurrencies?.length ? `，跳过手工锁定 ${res.skippedCurrencies.join('/')}` : ''
    toast.success(`已刷新 ${res.updatedCount ?? 0} 个币种${skipped}`)
  } catch (e) {
    const msg = describeError(e, '刷新失败')
    if (e instanceof BizError && e.code === 409905) refreshNote.value = msg
    toast.error(msg)
  }
}

// ---- 历史弹层 ----
const historyCurrency = ref<string | null>(null)
const historyItems = ref<ExchangeRateHistoryItem[]>([])
const historyLoading = ref(false)

async function openHistory(currency: string) {
  historyCurrency.value = currency
  historyItems.value = []
  historyLoading.value = true
  try {
    historyItems.value = await store.fetchHistory(currency, 30)
  } catch (e) {
    toast.error(describeError(e, '加载历史失败'))
  } finally {
    historyLoading.value = false
  }
}
</script>

<template>
  <div class="panel overflow-hidden" data-testid="exchange-rates-panel">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3">
      <p class="text-[12px] text-ink-soft">供应商模式下每日自动刷新；手工模式仅可行内编辑。加价率（spread）在「结算配置」维护。</p>
      <button class="btn-outline" :disabled="store.refreshing" data-testid="rates-refresh" @click="refresh">
        <ArrowPathIcon class="h-4 w-4" :class="store.refreshing && 'animate-spin'" />{{ store.refreshing ? '刷新中…' : '立即刷新' }}
      </button>
    </div>
    <p v-if="refreshNote" class="border-b border-warn/30 bg-warn/8 px-4 py-2 text-[12px] text-warn" data-testid="rates-refresh-note">{{ refreshNote }}</p>
    <div class="overflow-x-auto">
      <table class="data-table" data-testid="exchange-rates-table">
        <thead>
          <tr><th>币种</th><th class="text-right">汇率（1 USD =）</th><th class="text-right">有效汇率（含加价）</th><th>来源</th><th>同步时间</th><th>手工锁定</th><th>更新</th><th class="text-right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="store.loadingRates"><td colspan="8" class="py-10 text-center text-ink-faint">加载中…</td></tr>
          <tr v-for="r in store.rates" v-else :key="r.currency" :data-testid="`rate-row-${r.currency}`">
            <td class="font-medium text-ink">{{ r.currency }}</td>
            <td class="text-right">
              <template v-if="editingCurrency === r.currency">
                <div class="flex items-center justify-end gap-2">
                  <input v-model="rateDraft" type="number" min="0" step="0.0001" class="field w-28 px-2 py-1 text-right text-[12px]" />
                  <button class="btn-ghost text-ok" :disabled="savingRate" @click="saveRate"><CheckIcon class="h-4 w-4" /></button>
                  <button class="btn-ghost" :disabled="savingRate" @click="editingCurrency = null"><XMarkIcon class="h-4 w-4" /></button>
                </div>
                <p v-if="rateError" class="mt-1 text-right text-[11px] text-danger">{{ rateError }}</p>
              </template>
              <span v-else class="font-mono text-[13px] text-ink">{{ Number(r.rate).toFixed(4) }}</span>
            </td>
            <td class="text-right font-mono text-[13px]" :class="r.effectiveRate != null && Number(r.effectiveRate) !== Number(r.rate) ? 'text-gold-deep' : 'text-ink-soft'">
              {{ r.effectiveRate != null ? Number(r.effectiveRate).toFixed(4) : '—' }}
              <span v-if="r.spreadScaled" class="ml-1 text-[10px] text-ink-faint">+{{ (r.spreadScaled / 100).toFixed(2) }}%</span>
            </td>
            <td>
              <span class="rounded px-1.5 py-0.5 text-[10px]" :class="r.source === ExchangeRateSource.PROVIDER ? 'bg-info/12 text-info' : 'bg-ink/6 text-ink-soft'">{{ r.source ? EXCHANGE_RATE_SOURCE_LABEL[r.source] : '—' }}</span>
            </td>
            <td class="text-[12px] text-ink-faint">{{ r.syncedAt ? formatDateTime(r.syncedAt) : '未同步' }}</td>
            <td>
              <span v-if="r.currency === 'USD'" class="text-[11px] text-ink-faint">基准</span>
              <Toggle v-else :model-value="!!r.manualOverride" @update:model-value="onToggleOverride(r, $event)" />
            </td>
            <td class="text-[12px] text-ink-faint">{{ formatDateTime(r.updatedAt) }}<span v-if="r.updatedBy" class="ml-1">#{{ r.updatedBy }}</span></td>
            <td class="text-right">
              <div class="flex items-center justify-end gap-1">
                <button class="btn-ghost" data-testid="rate-history" @click="openHistory(r.currency)"><ClockIcon class="h-4 w-4" />历史</button>
                <!-- USD 行只读（恒 1，编辑按钮禁用） -->
                <button
                  class="btn-ghost disabled:opacity-40"
                  :disabled="r.currency === 'USD'"
                  :title="r.currency === 'USD' ? 'USD 为基准币种，恒为 1' : '编辑汇率'"
                  @click="startEditRate(r.currency, r.rate)"
                ><PencilSquareIcon class="h-4 w-4" />编辑</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="border-t border-line px-4 py-3 text-[12px] text-ink-faint">汇率仅影响新订单锁汇；既有订单按下单时锁定汇率结算。手工锁定的币种在供应商刷新时保持现值。</p>

    <!-- 历史弹层 -->
    <Teleport to="body">
      <div v-if="historyCurrency" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (historyCurrency = null)">
        <div class="panel w-[32rem] max-w-[92vw] p-6" data-testid="rate-history-dialog">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="text-[15px] font-medium text-ink">{{ historyCurrency }} 汇率历史（近 30 天）</h3>
            <button class="btn-ghost" @click="historyCurrency = null"><XMarkIcon class="h-4 w-4" /></button>
          </div>
          <div class="max-h-[60vh] overflow-y-auto">
            <table class="data-table">
              <thead><tr><th>报价日</th><th class="text-right">汇率</th><th>来源</th><th>记录时间</th></tr></thead>
              <tbody>
                <tr v-if="historyLoading"><td colspan="4" class="py-8 text-center text-ink-faint">加载中…</td></tr>
                <tr v-else-if="!historyItems.length"><td colspan="4" class="py-8 text-center text-ink-faint">近 30 天无历史记录</td></tr>
                <tr v-for="(h, i) in historyItems" v-else :key="i">
                  <td>{{ formatDate(h.quoteDate) }}</td>
                  <td class="text-right font-mono">{{ Number(h.rate).toFixed(6) }}</td>
                  <td><span class="rounded bg-ink/6 px-1.5 py-0.5 text-[10px] text-ink-soft">{{ EXCHANGE_RATE_SOURCE_LABEL[h.source] || h.source }}</span></td>
                  <td class="text-[12px] text-ink-faint">{{ formatDateTime(h.recordedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
