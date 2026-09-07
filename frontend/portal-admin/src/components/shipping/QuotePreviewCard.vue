<script setup lang="ts">
// order-flow-complete D：运费试算卡片（国家下拉 + 地区 + 小计 USD + 服务等级 → 各承运商报价 + 税费预估 + DDP/DDU 提示）
import { computed, onMounted, ref } from 'vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { shippingApi } from '@/api'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { formatDate, formatMoney } from '@/utils/format'
import { INCOTERM_LABEL, SERVICE_LEVEL_LABEL, ratePercent, zoneLabel } from '@/utils/tradingLabels'
import { Incoterm } from '@/api/types'
import type { ShippingQuotePreviewResponse } from '@/api/types'
import { CalculatorIcon } from '@heroicons/vue/24/outline'

const store = useShippingStore()
const toast = useToastStore()

const form = ref<{ countryCode: string; regionCode: string; subtotalUsd: string; serviceLevel: number | 'all' }>({
  countryCode: 'US', regionCode: '', subtotalUsd: '500', serviceLevel: 'all',
})
const errors = ref<FieldErrors>({})
const busy = ref(false)
const result = ref<ShippingQuotePreviewResponse | null>(null)

const countryOptions = computed(() =>
  store.countries.map((c) => ({ value: c.code, label: `${c.name}（${c.code}）`, disabled: !c.supported })),
)
const regionOptions = computed(() => {
  const c = store.countries.find((x) => x.code === form.value.countryCode)
  const regions = c?.regions ?? []
  return [{ value: '', label: regions.length ? '不指定州/省' : '该国无州/省字典' }, ...regions.map((r) => ({ value: r.code, label: `${r.name}（${r.code}）` }))]
})
const LEVEL_OPTIONS = [
  { value: 'all', label: '全部等级' },
  { value: 1, label: '标准（Standard）' },
  { value: 2, label: '加急（Express）' },
]

function onCountryChange() {
  form.value.regionCode = ''
  result.value = null
}

async function run() {
  if (busy.value) return
  errors.value = {}
  const subtotal = Number(form.value.subtotalUsd)
  if (!form.value.countryCode) errors.value.countryCode = '请选择国家'
  if (form.value.subtotalUsd === '' || Number.isNaN(subtotal) || subtotal < 0) errors.value.subtotalUsd = '小计需 ≥ 0'
  if (Object.keys(errors.value).length) return
  busy.value = true
  try {
    result.value = await shippingApi.previewShippingQuote({
      countryCode: form.value.countryCode,
      regionCode: form.value.regionCode || null,
      subtotalUsd: form.value.subtotalUsd,
      serviceLevel: form.value.serviceLevel === 'all' ? null : (form.value.serviceLevel as 1 | 2),
    })
  } catch (e) {
    result.value = null
    if (e instanceof BizError && (e.code === 422901 || e.code === 422601)) {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) errors.value = fields
      else toast.error(describeError(e))
    } else {
      toast.error(describeError(e, '试算失败'))
    }
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  store.ensureCountries().catch((e) => toast.error(describeError(e, '加载国家列表失败')))
})
</script>

<template>
  <div class="panel p-6" data-testid="quote-preview-card">
    <h3 class="mb-4 flex items-center gap-2 font-display text-lg font-semibold text-ink">
      <CalculatorIcon class="h-5 w-5 text-gold-deep" />运费试算
      <span class="text-[12px] font-normal text-ink-faint">USD 基准，按当前启用的运费选项与税率规则计算</span>
    </h3>
    <div class="grid grid-cols-1 gap-3 md:grid-cols-[1.4fr_1fr_1fr_1fr_auto]">
      <div>
        <label class="field-label">国家 *</label>
        <SelectMenu
          :model-value="form.countryCode"
          :options="countryOptions"
          :disabled="store.loadingCountries"
          :placeholder="store.loadingCountries ? '加载中…' : '选择国家'"
          data-testid="quote-country"
          @update:model-value="form.countryCode = String($event ?? ''); onCountryChange()"
        />
        <p v-if="errors.countryCode" class="mt-1 text-[11px] text-danger">{{ errors.countryCode }}</p>
      </div>
      <div>
        <label class="field-label">州/省</label>
        <SelectMenu :model-value="form.regionCode" :options="regionOptions" :disabled="regionOptions.length <= 1" data-testid="quote-region" @update:model-value="form.regionCode = String($event ?? '')" />
        <p v-if="errors.regionCode" class="mt-1 text-[11px] text-danger">{{ errors.regionCode }}</p>
      </div>
      <div>
        <label class="field-label">商品小计（USD）*</label>
        <input v-model="form.subtotalUsd" type="number" min="0" step="0.01" class="field" data-testid="quote-subtotal" @keyup.enter="run" />
        <p v-if="errors.subtotalUsd" class="mt-1 text-[11px] text-danger">{{ errors.subtotalUsd }}</p>
      </div>
      <div>
        <label class="field-label">服务等级</label>
        <SelectMenu :model-value="form.serviceLevel" :options="LEVEL_OPTIONS" @update:model-value="form.serviceLevel = $event === 'all' ? 'all' : Number($event)" />
      </div>
      <div class="flex items-end">
        <button class="btn-gold" :disabled="busy || store.loadingCountries" data-testid="quote-run" @click="run">{{ busy ? '计算中…' : '试算' }}</button>
      </div>
    </div>

    <div v-if="result" class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-[1fr_18rem]" data-testid="quote-result">
      <div>
        <p class="mb-2 text-[12px] text-ink-soft">分区：<span class="font-medium text-ink">{{ zoneLabel(result.zone) }}</span></p>
        <table class="data-table">
          <thead><tr><th>承运商</th><th>等级</th><th class="text-right">运费</th><th>运输天数</th><th>预计送达</th></tr></thead>
          <tbody>
            <tr v-if="!result.options.length"><td colspan="5" class="py-6 text-center text-ink-faint">该分区无启用的运费选项</td></tr>
            <tr v-for="(opt, i) in result.options" :key="i" :class="opt.selected ? 'bg-gold/5' : ''">
              <td class="font-medium text-ink">{{ opt.carrierName || opt.carrier || '—' }} <span class="font-mono text-[10px] text-ink-faint">{{ opt.carrierCode }}</span></td>
              <td>{{ opt.serviceLevel ? SERVICE_LEVEL_LABEL[opt.serviceLevel] : '—' }}</td>
              <td class="text-right" :class="Number(opt.fee) === 0 ? 'text-ok' : 'text-ink'">{{ Number(opt.fee) === 0 ? '免邮' : formatMoney(opt.fee, 'USD') }}</td>
              <td class="text-ink-soft">{{ opt.transitDaysMin ?? '—' }}~{{ opt.transitDaysMax ?? '—' }} 天</td>
              <td class="text-[12px] text-ink-soft">{{ opt.estimatedDeliveryFrom ? `${formatDate(opt.estimatedDeliveryFrom)} ~ ${formatDate(opt.estimatedDeliveryTo)}` : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="rounded-luxe border border-line bg-canvas-warm/40 p-4 text-[12px]" data-testid="quote-tax">
        <p class="mb-2 font-medium text-ink">税费预估</p>
        <p class="flex justify-between text-ink-soft"><span>条款</span><span class="font-medium" :class="result.incoterm === Incoterm.DDP ? 'text-ok' : 'text-warn'">{{ result.incoterm ? INCOTERM_LABEL[result.incoterm] : 'DDU（默认）' }}</span></p>
        <p class="flex justify-between text-ink-soft"><span>税费合计</span><span class="font-medium text-ink">{{ formatMoney(result.taxAmountUsd, 'USD') }}</span></p>
        <ul v-if="result.taxBreakdown?.length" class="mt-2 space-y-0.5 border-t border-line pt-2 text-ink-faint">
          <li v-for="(t, i) in result.taxBreakdown" :key="i" class="flex justify-between">
            <span>{{ t.label || t.type }} · {{ ratePercent(t.rateScaled) }}</span><span>{{ formatMoney(t.amount, 'USD') }}</span>
          </li>
        </ul>
        <p v-else class="mt-2 border-t border-line pt-2 text-ink-faint">该目的地无生效税率规则</p>
        <p v-if="result.dutiesNotice" class="mt-2 rounded bg-warn/10 px-2 py-1 text-warn">结算页将提示买家可能需自行承担进口关税（DDU）。</p>
        <p v-else-if="result.incoterm === Incoterm.DDP" class="mt-2 rounded bg-ok/10 px-2 py-1 text-ok">DDP：税费计入订单合计，买家无需另付。</p>
      </div>
    </div>
  </div>
</template>
