<script setup lang="ts">
// PAGE-TRD-A04 / COMP-TRD-A07~A08：汇率管理 + 结算配置（权限 /settings；复用 tab+panel+data-table 风格）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { useTradingSettingsStore } from '@/stores/tradingSettings'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateCheckoutConfig, validateExchangeRate, type FieldErrors } from '@/utils/validators'
import { formatDateTime } from '@/utils/format'
import { PencilSquareIcon, CheckIcon, XMarkIcon } from '@heroicons/vue/24/outline'

const store = useTradingSettingsStore()
const toast = useToastStore()

const tab = ref<'rates' | 'checkout'>('rates')

// ===== COMP-TRD-A07 汇率管理（USD 行只读恒 1；EUR/CAD/AUD/GBP 行内编辑） =====
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
  if (!editingCurrency.value) return
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
      toast.error(e instanceof BizError ? e.message : '保存失败')
    }
  } finally {
    savingRate.value = false
  }
}

// ===== COMP-TRD-A08 结算配置 =====
const configForm = ref({ giftWrapFeeUsd: '', customRefundGraceHours: 24 })
const configErrors = ref<FieldErrors>({})
const savingConfig = ref(false)

async function loadAll() {
  try {
    await Promise.all([store.fetchRates(), store.fetchCheckoutConfig()])
    if (store.checkoutConfig) {
      configForm.value = {
        giftWrapFeeUsd: String(store.checkoutConfig.giftWrapFeeUsd),
        customRefundGraceHours: store.checkoutConfig.customRefundGraceHours,
      }
    }
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载配置失败')
  }
}

async function saveConfig() {
  // FORM-TRD-A05：fee≥0、grace 1..168 区间预校验 → 422 inline 兜底
  configErrors.value = validateCheckoutConfig(configForm.value)
  if (Object.keys(configErrors.value).length) return
  savingConfig.value = true
  try {
    await store.saveCheckoutConfig({
      giftWrapFeeUsd: configForm.value.giftWrapFeeUsd,
      customRefundGraceHours: Number(configForm.value.customRefundGraceHours),
    })
    toast.success('结算配置已保存')
  } catch (e) {
    const fields = extractFieldErrors(e)
    if (Object.keys(fields).length) configErrors.value = fields
    else toast.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    savingConfig.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Settings" title="汇率与结算配置" subtitle="多币种汇率维护与结算费用配置（仅影响新订单）" />

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in [['rates', '汇率管理'], ['checkout', '结算配置']] as const"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="tab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- 汇率管理 tab -->
    <div v-show="tab === 'rates'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>币种</th><th class="text-right">汇率（1 USD =）</th><th>更新人</th><th>更新时间</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-if="store.loadingRates"><td colspan="5" class="py-10 text-center text-ink-faint">加载中…</td></tr>
          <tr v-for="r in store.rates" v-else :key="r.currency">
            <td class="font-medium text-ink">{{ r.currency }}</td>
            <td class="text-right">
              <template v-if="editingCurrency === r.currency">
                <div class="flex items-center justify-end gap-2">
                  <input v-model="rateDraft" type="number" min="0" step="0.0001" class="field w-28 px-2 py-1 text-right text-[12px]" />
                  <button class="btn-ghost text-ok" :disabled="savingRate" @click="saveRate"><CheckIcon class="h-4 w-4" /></button>
                  <button class="btn-ghost" @click="editingCurrency = null"><XMarkIcon class="h-4 w-4" /></button>
                </div>
                <p v-if="rateError" class="mt-1 text-right text-[11px] text-danger">{{ rateError }}</p>
              </template>
              <span v-else class="font-mono text-[13px] text-ink">{{ Number(r.rate).toFixed(4) }}</span>
            </td>
            <td class="text-[12px] text-ink-faint">{{ r.updatedBy ? `#${r.updatedBy}` : '—' }}</td>
            <td class="text-[12px] text-ink-faint">{{ formatDateTime(r.updatedAt) }}</td>
            <td class="text-right">
              <!-- USD 行只读（恒 1，编辑按钮禁用） -->
              <button
                class="btn-ghost disabled:opacity-40"
                :disabled="r.currency === 'USD'"
                :title="r.currency === 'USD' ? 'USD 为基准币种，恒为 1' : '编辑汇率'"
                @click="startEditRate(r.currency, r.rate)"
              ><PencilSquareIcon class="h-4 w-4" />编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="border-t border-line px-4 py-3 text-[12px] text-ink-faint">汇率仅影响新订单锁汇；既有订单按下单时锁定汇率结算。</p>
    </div>

    <!-- 结算配置 tab -->
    <div v-show="tab === 'checkout'" class="panel max-w-xl p-6">
      <div class="space-y-5">
        <div>
          <label class="field-label">礼品包装费（USD）</label>
          <input v-model="configForm.giftWrapFeeUsd" type="number" min="0" step="0.01" class="field" />
          <p v-if="configErrors.giftWrapFeeUsd" class="mt-1 text-[11px] text-danger">{{ configErrors.giftWrapFeeUsd }}</p>
          <p class="mt-1 text-[11px] text-ink-faint">结算页 Gift Wrapping 勾选项费用（决策 28 金额拆分行）。</p>
        </div>
        <div>
          <label class="field-label">定制商品退款宽限期（小时，1~168）</label>
          <input v-model.number="configForm.customRefundGraceHours" type="number" min="1" max="168" class="field" />
          <p v-if="configErrors.customRefundGraceHours" class="mt-1 text-[11px] text-danger">{{ configErrors.customRefundGraceHours }}</p>
          <p class="mt-1 text-[11px] text-ink-faint">定制单付款后超过宽限期视为已投产，不再支持退款（决策 24）。</p>
        </div>
        <div class="flex justify-end">
          <button class="btn-gold" :disabled="savingConfig" @click="saveConfig">{{ savingConfig ? '保存中…' : '保存配置' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
