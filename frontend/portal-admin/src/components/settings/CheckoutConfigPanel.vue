<script setup lang="ts">
// COMP-TRD-A08 结算配置（FORM-TRD-A05：fee≥0、grace 1..168 → 422 inline 兜底）
// order-flow-complete §2.3：新增 auto_complete_days / auto_deliver_days / pending_timeout_minutes / exchange_rate_spread_scaled / production_days_default
// 范围与 CheckoutConfigService checkRange 一致；越界 422601 fields 回显到对应输入
import { onMounted, ref } from 'vue'
import { useTradingSettingsStore } from '@/stores/tradingSettings'
import { useToastStore } from '@/stores/toast'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, validateCheckoutConfig, type FieldErrors } from '@/utils/validators'

const store = useTradingSettingsStore()
const toast = useToastStore()

const configForm = ref({
  giftWrapFeeUsd: '',
  customRefundGraceHours: 24,
  autoCompleteDays: 7,
  autoDeliverDays: 30,
  pendingTimeoutMinutes: 30,
  exchangeRateSpreadScaled: 0,
  productionDaysDefault: 21,
})
const configErrors = ref<FieldErrors>({})
const savingConfig = ref(false)

function fill() {
  const c = store.checkoutConfig
  if (!c) return
  configForm.value = {
    giftWrapFeeUsd: String(c.giftWrapFeeUsd),
    customRefundGraceHours: c.customRefundGraceHours,
    autoCompleteDays: c.autoCompleteDays ?? 7,
    autoDeliverDays: c.autoDeliverDays ?? 30,
    pendingTimeoutMinutes: c.pendingTimeoutMinutes ?? 30,
    exchangeRateSpreadScaled: c.exchangeRateSpreadScaled ?? 0,
    productionDaysDefault: c.productionDaysDefault ?? 21,
  }
}

async function load() {
  try {
    await store.fetchCheckoutConfig()
    fill()
  } catch (e) {
    toast.error(describeError(e, '加载配置失败'))
  }
}
onMounted(load)

async function saveConfig() {
  if (savingConfig.value) return
  configErrors.value = validateCheckoutConfig(configForm.value)
  if (Object.keys(configErrors.value).length) {
    toast.error('请修正标红字段后再保存')
    return
  }
  savingConfig.value = true
  try {
    await store.saveCheckoutConfig({
      giftWrapFeeUsd: configForm.value.giftWrapFeeUsd,
      customRefundGraceHours: Number(configForm.value.customRefundGraceHours),
      autoCompleteDays: Number(configForm.value.autoCompleteDays),
      autoDeliverDays: Number(configForm.value.autoDeliverDays),
      pendingTimeoutMinutes: Number(configForm.value.pendingTimeoutMinutes),
      exchangeRateSpreadScaled: Number(configForm.value.exchangeRateSpreadScaled),
      productionDaysDefault: Number(configForm.value.productionDaysDefault),
    })
    fill()
    toast.success('结算配置已保存')
  } catch (e) {
    const fields = extractFieldErrors(e)
    if (Object.keys(fields).length) {
      configErrors.value = fields
      toast.error('部分字段超出允许范围，请检查标红项')
    } else {
      toast.error(describeError(e, '保存失败'))
    }
  } finally {
    savingConfig.value = false
  }
}

const spreadPercent = () => (Number(configForm.value.exchangeRateSpreadScaled || 0) / 100).toFixed(2)
</script>

<template>
  <div class="panel max-w-3xl p-6" data-testid="checkout-config-panel">
    <div v-if="store.loadingConfig && !store.checkoutConfig" class="py-8 text-center text-ink-faint">加载中…</div>
    <div v-else class="grid grid-cols-1 gap-x-8 gap-y-5 md:grid-cols-2">
      <div>
        <label class="field-label">礼品包装费（USD）</label>
        <input v-model="configForm.giftWrapFeeUsd" type="number" min="0" step="0.01" class="field" data-testid="cfg-gift-wrap" />
        <p v-if="configErrors.giftWrapFeeUsd" class="mt-1 text-[11px] text-danger">{{ configErrors.giftWrapFeeUsd }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">结算页 Gift Wrapping 勾选项费用（决策 28 金额拆分行）。</p>
      </div>
      <div>
        <label class="field-label">定制商品退款宽限期（小时，1~168）</label>
        <input v-model.number="configForm.customRefundGraceHours" type="number" min="1" max="168" class="field" data-testid="cfg-grace" />
        <p v-if="configErrors.customRefundGraceHours" class="mt-1 text-[11px] text-danger">{{ configErrors.customRefundGraceHours }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">定制单付款后超过宽限期视为已投产，不再支持退款（决策 24）。</p>
      </div>

      <div class="md:col-span-2 border-t border-line pt-4">
        <p class="text-[12px] font-medium uppercase tracking-luxe text-gold-deep">履约与自动流转</p>
      </div>
      <div>
        <label class="field-label">待付款超时（分钟，5~1440）</label>
        <input v-model.number="configForm.pendingTimeoutMinutes" type="number" min="5" max="1440" class="field" data-testid="cfg-pending-timeout" />
        <p v-if="configErrors.pendingTimeoutMinutes" class="mt-1 text-[11px] text-danger">{{ configErrors.pendingTimeoutMinutes }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">下单后未支付超过该时长自动取消并释放库存；影响后续新订单的过期时间。</p>
      </div>
      <div>
        <label class="field-label">默认制作周期（天，1~180）</label>
        <input v-model.number="configForm.productionDaysDefault" type="number" min="1" max="180" class="field" data-testid="cfg-production-days" />
        <p v-if="configErrors.productionDaysDefault" class="mt-1 text-[11px] text-danger">{{ configErrors.productionDaysDefault }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">预计送达 = 下单日 + max(商品制作天数, 本值) + 运输天数区间。</p>
      </div>
      <div>
        <label class="field-label">自动签收（发货后天数，1~120）</label>
        <input v-model.number="configForm.autoDeliverDays" type="number" min="1" max="120" class="field" data-testid="cfg-auto-deliver" />
        <p v-if="configErrors.autoDeliverDays" class="mt-1 text-[11px] text-danger">{{ configErrors.autoDeliverDays }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">发货后超过该天数仍无签收记录，系统自动将订单置为「已签收」。</p>
      </div>
      <div>
        <label class="field-label">自动完成（签收后天数，1~60）</label>
        <input v-model.number="configForm.autoCompleteDays" type="number" min="1" max="60" class="field" data-testid="cfg-auto-complete" />
        <p v-if="configErrors.autoCompleteDays" class="mt-1 text-[11px] text-danger">{{ configErrors.autoCompleteDays }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">签收后超过该天数买家未确认，系统自动完成订单。</p>
      </div>

      <div class="md:col-span-2 border-t border-line pt-4">
        <p class="text-[12px] font-medium uppercase tracking-luxe text-gold-deep">多币种</p>
      </div>
      <div>
        <label class="field-label">汇率加价率（万分比，0~2000）</label>
        <input v-model.number="configForm.exchangeRateSpreadScaled" type="number" min="0" max="2000" class="field" data-testid="cfg-spread" />
        <p v-if="configErrors.exchangeRateSpreadScaled" class="mt-1 text-[11px] text-danger">{{ configErrors.exchangeRateSpreadScaled }}</p>
        <p class="mt-1 text-[11px] text-ink-faint">当前 = {{ spreadPercent() }}%；锁汇 = 现值 × (1 + 加价率)，用于覆盖汇兑与结算成本。</p>
      </div>
    </div>
    <div class="mt-6 flex justify-end">
      <button class="btn-gold" :disabled="savingConfig" data-testid="cfg-save" @click="saveConfig">{{ savingConfig ? '保存中…' : '保存配置' }}</button>
    </div>
  </div>
</template>
