<script setup lang="ts">
// order-flow-complete F：税费面板 —— 税率规则表格（筛选国家/税种/启用 + 新建/编辑抽屉 + 启用开关 + 删除）
// + 目的国政策表格（国家 / DDP-DDU / 关税提示开关 / 提示文案，行内编辑）
import { computed, onMounted, ref } from 'vue'
import Toggle from '@/components/Toggle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import TaxRuleFormDrawer from '@/components/drawers/TaxRuleFormDrawer.vue'
import { useTradingSettingsStore } from '@/stores/tradingSettings'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { formatDate } from '@/utils/format'
import { INCOTERM_LABEL, INCOTERM_OPTIONS, TAX_TYPE_LABEL, TAX_TYPE_OPTIONS, ratePercent } from '@/utils/tradingLabels'
import { Incoterm } from '@/api/types'
import type { TaxDestinationPolicy, TaxRule } from '@/api/types'
import { PlusIcon, PencilSquareIcon, TrashIcon, CheckIcon, XMarkIcon } from '@heroicons/vue/24/outline'

const store = useTradingSettingsStore()
const shippingStore = useShippingStore()
const toast = useToastStore()

const countryFilter = ref('all')
const typeFilter = ref<number | 'all'>('all')
const enabledFilter = ref<'all' | 'on' | 'off'>('all')

const drawer = ref(false)
const editing = ref<TaxRule | null>(null)
const confirm = ref<TaxRule | null>(null)
const confirmBusy = ref(false)

function countryName(code: string): string {
  return shippingStore.countries.find((c) => c.code === code)?.name || code
}

const countryFilterOptions = computed(() => {
  const codes = Array.from(new Set(store.taxRules.map((r) => r.countryCode))).sort()
  return [{ value: 'all', label: '全部国家' }, ...codes.map((c) => ({ value: c, label: `${countryName(c)}（${c}）` }))]
})

const filteredRules = computed(() =>
  store.taxRules
    .filter((r) => countryFilter.value === 'all' || r.countryCode === countryFilter.value)
    .filter((r) => typeFilter.value === 'all' || r.taxType === typeFilter.value)
    .filter((r) => enabledFilter.value === 'all' || (enabledFilter.value === 'on' ? r.enabled : !r.enabled))
    .sort((a, b) => a.countryCode.localeCompare(b.countryCode) || a.region.localeCompare(b.region) || a.taxType - b.taxType),
)

async function load() {
  try {
    await Promise.all([store.fetchTaxRules(), store.fetchPolicies(), shippingStore.ensureCountries()])
  } catch (e) {
    toast.error(describeError(e, '加载税费配置失败'))
  }
}
onMounted(load)

function openRule(r?: TaxRule) {
  editing.value = r ?? null
  drawer.value = true
}

async function onToggleRule(r: TaxRule, on: boolean) {
  try {
    await store.toggleTaxRule(r, on)
    toast.success(on ? '规则已启用' : '规则已停用')
  } catch (e) {
    toast.error(describeError(e))
    if (e instanceof BizError && e.code === 404906) store.fetchTaxRules().catch(() => undefined)
  }
}

async function doDelete() {
  if (!confirm.value || confirmBusy.value) return
  confirmBusy.value = true
  try {
    await store.removeTaxRule(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    toast.error(describeError(e))
    if (e instanceof BizError && e.code === 404906) {
      confirm.value = null
      store.fetchTaxRules().catch(() => undefined)
    }
  } finally {
    confirmBusy.value = false
  }
}

// ---- 目的国政策行内编辑 ----
const editingPolicy = ref<string | null>(null)
const policyDraft = ref<{ incoterm: number; dutiesNotice: boolean; noticeText: string }>({ incoterm: Incoterm.DDU, dutiesNotice: true, noticeText: '' })
const policySaving = ref(false)
const policyError = ref('')
const newPolicyCountry = ref('')

const policyCountryOptions = computed(() => {
  const existing = new Set(store.policies.map((p) => p.countryCode))
  return shippingStore.countries.filter((c) => !existing.has(c.code)).map((c) => ({ value: c.code, label: `${c.name}（${c.code}）` }))
})

const sortedPolicies = computed(() => [...store.policies].sort((a, b) => a.countryCode.localeCompare(b.countryCode)))

function startEditPolicy(p: TaxDestinationPolicy) {
  editingPolicy.value = p.countryCode
  policyDraft.value = { incoterm: p.incoterm, dutiesNotice: p.dutiesNotice, noticeText: p.noticeText || '' }
  policyError.value = ''
}

function startNewPolicy() {
  if (!newPolicyCountry.value) {
    toast.error('请先选择要添加政策的国家')
    return
  }
  editingPolicy.value = newPolicyCountry.value
  policyDraft.value = { incoterm: Incoterm.DDU, dutiesNotice: true, noticeText: '' }
  policyError.value = ''
}

const isNewPolicy = computed(() => !!editingPolicy.value && !store.policies.some((p) => p.countryCode === editingPolicy.value))

async function savePolicy() {
  if (!editingPolicy.value || policySaving.value) return
  policyError.value = ''
  if (policyDraft.value.noticeText.length > 255) {
    policyError.value = '提示文案不超过 255 字符'
    return
  }
  policySaving.value = true
  const cc = editingPolicy.value
  try {
    await store.savePolicy(cc, {
      incoterm: policyDraft.value.incoterm as TaxDestinationPolicy['incoterm'],
      dutiesNotice: policyDraft.value.dutiesNotice,
      noticeText: policyDraft.value.noticeText.trim() || null,
    })
    toast.success(`${countryName(cc)} 政策已保存`)
    editingPolicy.value = null
    newPolicyCountry.value = ''
  } catch (e) {
    policyError.value = describeError(e, '保存失败')
    toast.error(policyError.value)
  } finally {
    policySaving.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- 税率规则 -->
    <div class="panel" data-testid="tax-rules-panel">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-4">
        <h3 class="font-display text-lg font-semibold text-ink">税率规则</h3>
        <div class="flex flex-wrap items-center gap-2">
          <SelectMenu v-model="countryFilter" :options="countryFilterOptions" class="w-44" data-testid="tax-filter-country" />
          <SelectMenu v-model="typeFilter" :options="[{ value: 'all', label: '全部税种' }, ...TAX_TYPE_OPTIONS]" class="w-40" data-testid="tax-filter-type" />
          <SelectMenu v-model="enabledFilter" :options="[{ value: 'all', label: '全部状态' }, { value: 'on', label: '已启用' }, { value: 'off', label: '已停用' }]" class="w-32" />
          <button class="btn-gold" data-testid="tax-add" @click="openRule()"><PlusIcon class="h-4 w-4" />新建规则</button>
        </div>
      </div>
      <div v-if="store.loadingTaxRules" class="py-10 text-center text-ink-faint">加载中…</div>
      <EmptyState v-else-if="!filteredRules.length" title="暂无税率规则" hint="新建规则后，结算报价将按目的地自动计税。" />
      <div v-else class="overflow-x-auto">
        <table class="data-table" data-testid="tax-rules-table">
          <thead>
            <tr><th>国家</th><th>州/省</th><th>税种</th><th class="text-right">税率</th><th>含运费</th><th class="text-right">起征额</th><th>生效窗口</th><th>标签</th><th>启用</th><th class="text-right">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in filteredRules" :key="r.id" :class="!r.enabled && 'opacity-60'" :data-testid="`tax-rule-row-${r.id}`">
              <td class="font-medium text-ink">{{ countryName(r.countryCode) }} <span class="font-mono text-[10px] text-ink-faint">{{ r.countryCode }}</span></td>
              <td class="text-ink-soft">{{ r.region || '国家级' }}</td>
              <td><span class="rounded bg-ink/6 px-1.5 py-0.5 text-[10px] text-ink-soft">{{ TAX_TYPE_LABEL[r.taxType] || r.taxType }}</span></td>
              <td class="text-right font-mono text-ink">{{ ratePercent(r.rateScaled) }}</td>
              <td class="text-ink-soft">{{ r.appliesToShipping ? '是' : '否' }}</td>
              <td class="text-right text-ink-soft">{{ r.thresholdUsd == null ? '—' : '$' + Number(r.thresholdUsd).toFixed(2) }}</td>
              <td class="text-[12px] text-ink-soft">{{ r.effectiveFrom || r.effectiveTo ? `${r.effectiveFrom ? formatDate(r.effectiveFrom) : '…'} ~ ${r.effectiveTo ? formatDate(r.effectiveTo) : '…'}` : '不限' }}</td>
              <td class="text-ink-soft">{{ r.label || '—' }}</td>
              <td><Toggle :model-value="r.enabled" @update:model-value="onToggleRule(r, $event)" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <button class="btn-ghost" @click="openRule(r)"><PencilSquareIcon class="h-4 w-4" /></button>
                  <button class="btn-danger-ghost" @click="confirm = r"><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="border-t border-line px-6 py-3 text-[12px] text-ink-faint">税率以万分比存储（10000 = 100%）；同国家同税种下，州/省级规则优先于国家级；仅启用且在生效窗口内的规则参与计税。</p>
    </div>

    <!-- 目的国政策 -->
    <div class="panel" data-testid="tax-policies-panel">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-4">
        <h3 class="font-display text-lg font-semibold text-ink">目的国政策（DDP / DDU）</h3>
        <div class="flex items-center gap-2">
          <SelectMenu v-model="newPolicyCountry" :options="policyCountryOptions" placeholder="选择国家添加政策" class="w-56" data-testid="policy-new-country" />
          <button class="btn-ghost" :disabled="!!editingPolicy" data-testid="policy-add" @click="startNewPolicy"><PlusIcon class="h-4 w-4" />添加</button>
        </div>
      </div>
      <div v-if="store.loadingPolicies" class="py-10 text-center text-ink-faint">加载中…</div>
      <div v-else class="overflow-x-auto">
        <table class="data-table" data-testid="tax-policies-table">
          <thead><tr><th>国家</th><th>条款</th><th>关税提示</th><th>提示文案</th><th class="text-right">操作</th></tr></thead>
          <tbody>
            <!-- 新增行 -->
            <tr v-if="isNewPolicy" class="bg-gold/5">
              <td class="font-medium text-ink">{{ countryName(editingPolicy!) }} <span class="font-mono text-[10px] text-ink-faint">{{ editingPolicy }}</span></td>
              <td><SelectMenu :model-value="policyDraft.incoterm" :options="INCOTERM_OPTIONS" class="w-44" @update:model-value="policyDraft.incoterm = Number($event)" /></td>
              <td><Toggle v-model="policyDraft.dutiesNotice" /></td>
              <td><input v-model="policyDraft.noticeText" class="field" maxlength="255" placeholder="结算页提示文案（可空，使用默认）" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <button class="btn-ghost text-ok" :disabled="policySaving" @click="savePolicy"><CheckIcon class="h-4 w-4" /></button>
                  <button class="btn-ghost" :disabled="policySaving" @click="editingPolicy = null"><XMarkIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
            <tr v-if="!sortedPolicies.length && !isNewPolicy"><td colspan="5" class="py-8 text-center text-ink-faint">暂无目的国政策（未配置国家默认 DDU 并提示关税）</td></tr>
            <tr v-for="p in sortedPolicies" :key="p.countryCode" :data-testid="`policy-row-${p.countryCode}`">
              <td class="font-medium text-ink">{{ countryName(p.countryCode) }} <span class="font-mono text-[10px] text-ink-faint">{{ p.countryCode }}</span></td>
              <template v-if="editingPolicy === p.countryCode">
                <td><SelectMenu :model-value="policyDraft.incoterm" :options="INCOTERM_OPTIONS" class="w-44" data-testid="policy-incoterm-select" @update:model-value="policyDraft.incoterm = Number($event)" /></td>
                <td><Toggle v-model="policyDraft.dutiesNotice" /></td>
                <td>
                  <input v-model="policyDraft.noticeText" class="field" maxlength="255" placeholder="结算页提示文案（可空，使用默认）" data-testid="policy-notice-input" />
                  <p v-if="policyError" class="mt-1 text-[11px] text-danger">{{ policyError }}</p>
                </td>
                <td>
                  <div class="flex items-center justify-end gap-1">
                    <button class="btn-ghost text-ok" :disabled="policySaving" data-testid="policy-save" @click="savePolicy"><CheckIcon class="h-4 w-4" /></button>
                    <button class="btn-ghost" :disabled="policySaving" @click="editingPolicy = null"><XMarkIcon class="h-4 w-4" /></button>
                  </div>
                </td>
              </template>
              <template v-else>
                <td>
                  <span class="rounded px-1.5 py-0.5 text-[11px] font-medium" :class="p.incoterm === Incoterm.DDP ? 'bg-ok/12 text-ok' : 'bg-warn/14 text-warn'" data-testid="policy-incoterm">{{ INCOTERM_LABEL[p.incoterm] || p.incoterm }}</span>
                </td>
                <td class="text-ink-soft">{{ p.dutiesNotice ? '显示' : '不显示' }}</td>
                <td class="max-w-md truncate text-[12px] text-ink-soft" :title="p.noticeText || ''">{{ p.noticeText || '—' }}</td>
                <td class="text-right">
                  <button class="btn-ghost" :disabled="!!editingPolicy" data-testid="policy-edit" @click="startEditPolicy(p)"><PencilSquareIcon class="h-4 w-4" />编辑</button>
                </td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="border-t border-line px-6 py-3 text-[12px] text-ink-faint">DDP：税费计入订单合计；DDU：仅在结算页提示买家可能需自行承担关税，不计入合计。未配置的国家默认 DDU + 提示。</p>
    </div>

    <TaxRuleFormDrawer :open="drawer" :editing="editing" @close="drawer = false" />
    <ConfirmDialog
      :open="!!confirm"
      title="删除税率规则"
      :message="confirm ? `确认删除「${countryName(confirm.countryCode)}${confirm.region ? ' / ' + confirm.region : ''} · ${TAX_TYPE_LABEL[confirm.taxType]} ${ratePercent(confirm.rateScaled)}」？删除后该目的地将不再计此税。` : ''"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirm = null"
    />
  </div>
</template>
