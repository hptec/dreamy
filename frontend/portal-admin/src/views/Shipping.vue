<script setup lang="ts">
// PAGE-SHP-01 / COMP-SHP-01~03：物流配置（承运方 + 运费选项 + 运费试算）
// DEC-SHP-FE-1：无「保存配置」按钮（逐资源即时持久化）；DEC-SHP-FE-2：行内编辑/删除
// order-flow-complete D：承运商 code / 跟踪链接模板；「国际邮费表」→「运费选项」（zone × 承运商 × 服务等级，两档费用/门槛/运输天数/启用）；试算卡片
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import CarrierFormDrawer from '@/components/drawers/CarrierFormDrawer.vue'
import ShippingOptionFormDrawer from '@/components/drawers/ShippingOptionFormDrawer.vue'
import QuotePreviewCard from '@/components/shipping/QuotePreviewCard.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { SERVICE_LEVEL_LABEL, ZONE_LABEL, ZONE_OPTIONS, zoneLabel } from '@/utils/tradingLabels'
import { PlusIcon, TruckIcon, PencilSquareIcon, TrashIcon, LinkIcon } from '@heroicons/vue/24/outline'
import { CarrierStatus, ShippingServiceLevel } from '@/api/types'
import type { Carrier, ShippingOption } from '@/api/types'

const store = useShippingStore()
const toast = useToastStore()

const carrierDrawer = ref(false)
const editingCarrier = ref<Carrier | null>(null)
const optionDrawer = ref(false)
const editingOption = ref<ShippingOption | null>(null)
const zoneFilter = ref<string>('all')

const confirm = ref<{ kind: 'carrier' | 'option'; id: number; message: string } | null>(null)
const confirmBusy = ref(false)

function load() {
  store.fetchAll().catch((e) => toast.error(describeError(e, '加载物流配置失败')))
}

onMounted(load)

/** 预判：仅剩 1 个启用承运方时禁用其 Toggle/删除（后端 409902 兜底） */
function lastEnabled(c: Carrier): boolean {
  return c.status === CarrierStatus.ENABLED && store.enabledCount <= 1
}

async function onToggleCarrier(c: Carrier, on: boolean) {
  if (!on && lastEnabled(c)) {
    toast.error('至少保留一个启用的承运方')
    return
  }
  try {
    await store.toggleCarrier(c, on ? CarrierStatus.ENABLED : CarrierStatus.DISABLED)
  } catch (e) {
    if (e instanceof BizError && e.code === 404901) {
      toast.error('数据已变更，列表已刷新')
      load()
    } else {
      toast.error(describeError(e))
    }
  }
}

async function onToggleOption(r: ShippingOption, on: boolean) {
  try {
    await store.toggleOption(r, on)
    toast.success(on ? '运费选项已启用' : '运费选项已停用')
  } catch (e) {
    if (e instanceof BizError && e.code === 404903) {
      toast.error('数据已变更，列表已刷新')
      load()
    } else {
      toast.error(describeError(e))
    }
  }
}

function openCarrier(c?: Carrier) {
  editingCarrier.value = c ?? null
  carrierDrawer.value = true
}

function openOption(r?: ShippingOption) {
  editingOption.value = r ?? null
  optionDrawer.value = true
}

function askDeleteCarrier(c: Carrier) {
  confirm.value = {
    kind: 'carrier',
    id: c.id,
    message: '删除后该承运商不再出现在结算选项；历史订单不受影响。确认删除？',
  }
}

function askDeleteOption(r: ShippingOption) {
  const isFallback = r.zone === 'REST' && r.carrierCode === 'ANY'
  confirm.value = {
    kind: 'option',
    id: r.id,
    message: isFallback
      ? '这是全局兜底选项（REST / ANY），删除后未配置区域将无运费报价。确认删除？'
      : `确认删除运费选项「${zoneLabel(r.zone)} · ${r.carrierName || r.carrierCode} · ${SERVICE_LEVEL_LABEL[r.serviceLevel]}」？`,
  }
}

async function doConfirm() {
  if (!confirm.value || confirmBusy.value) return
  confirmBusy.value = true
  try {
    if (confirm.value.kind === 'carrier') await store.removeCarrier(confirm.value.id)
    else await store.removeOption(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    if (e instanceof BizError && (e.code === 404901 || e.code === 404903)) {
      toast.error('数据已变更，列表已刷新')
      confirm.value = null
      load()
    } else {
      toast.error(describeError(e))
    }
  } finally {
    confirmBusy.value = false
  }
}

/** DEC-SHP-3 语义可视化：null → —；fee_over=0 → 免邮（text-ok） */
function money(v?: number | null): string {
  return v == null ? '—' : '$' + Number(v).toFixed(2)
}
function feeOverText(r: ShippingOption): string {
  return r.feeOver === 0 ? '免邮' : money(r.feeOver)
}
function thresholdText(r: ShippingOption): string {
  return r.threshold == null ? '—' : '$' + Number(r.threshold)
}

const ZONE_ORDER = Object.keys(ZONE_LABEL)
const filteredOptions = computed(() => {
  const list = zoneFilter.value === 'all' ? store.options : store.options.filter((o) => o.zone === zoneFilter.value)
  return [...list].sort((a, b) => {
    const z = ZONE_ORDER.indexOf(a.zone) - ZONE_ORDER.indexOf(b.zone)
    if (z !== 0) return z
    const c = a.carrierCode.localeCompare(b.carrierCode)
    return c !== 0 ? c : a.serviceLevel - b.serviceLevel
  })
})
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Settings" title="物流配置" subtitle="管理承运方、分区运费选项与运送时效，并可试算目的地运费与税费" />

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <!-- COMP-SHP-02 承运方面板 -->
      <div class="panel p-6" data-testid="carriers-panel">
        <h3 class="mb-4 flex items-center gap-2 font-display text-lg font-semibold text-ink">
          <TruckIcon class="h-5 w-5 text-gold-deep" />承运方
        </h3>
        <div class="space-y-2">
          <template v-if="store.loadingCarriers">
            <div v-for="i in 3" :key="i" class="h-14 animate-pulse rounded-luxe border border-line bg-canvas-warm/50"></div>
          </template>
          <EmptyState v-else-if="!store.carriers.length" title="尚未配置承运方" hint="添加承运方后才能在结算流程提供物流选项。" />
          <div
            v-for="c in store.carriers"
            v-else
            :key="c.id"
            class="group flex items-center gap-3 rounded-luxe border border-line p-3"
          >
            <div class="min-w-0 flex-1">
              <p class="flex items-center gap-2 text-[13px] font-medium text-ink">
                {{ c.name }}
                <span v-if="c.code" class="rounded bg-ink/8 px-1.5 py-0.5 font-mono text-[10px] text-ink-soft">{{ c.code }}</span>
                <span v-else class="rounded bg-warn/14 px-1.5 py-0.5 text-[10px] text-warn" title="未设置编码，无法用于包裹创建与运费选项">无编码</span>
                <LinkIcon v-if="c.trackingUrlTemplate" class="h-3.5 w-3.5 text-ink-faint" title="已配置跟踪链接模板" />
              </p>
              <p class="text-[12px] text-ink-faint">{{ c.zones || '—' }} · 时效 {{ c.leadTime || '—' }}</p>
            </div>
            <!-- DEC-SHP-FE-2：行 hover 编辑/删除 -->
            <div class="invisible flex items-center gap-1 group-hover:visible">
              <button class="btn-ghost" @click="openCarrier(c)"><PencilSquareIcon class="h-4 w-4" /></button>
              <button
                class="btn-danger-ghost disabled:opacity-40"
                :disabled="lastEnabled(c)"
                :title="lastEnabled(c) ? '至少保留一个启用的承运方' : '删除'"
                @click="askDeleteCarrier(c)"
              ><TrashIcon class="h-4 w-4" /></button>
            </div>
            <span :title="lastEnabled(c) ? '至少保留一个启用的承运方' : ''" :class="lastEnabled(c) ? 'opacity-60' : ''">
              <Toggle :model-value="c.status === CarrierStatus.ENABLED" @update:model-value="onToggleCarrier(c, $event)" />
            </span>
          </div>
          <button class="btn-ghost" data-testid="add-carrier" @click="openCarrier()"><PlusIcon class="h-4 w-4" />添加承运方</button>
        </div>
      </div>

      <!-- COMP-SHP-03 运费选项面板 -->
      <div class="panel p-6" data-testid="options-panel">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h3 class="font-display text-lg font-semibold text-ink">运费选项（分区 × 承运商 × 等级）</h3>
          <div class="flex items-center gap-2">
            <SelectMenu v-model="zoneFilter" :options="[{ value: 'all', label: '全部分区' }, ...ZONE_OPTIONS]" class="w-44" />
            <button class="btn-ghost" data-testid="add-option" @click="openOption()"><PlusIcon class="h-4 w-4" />添加选项</button>
          </div>
        </div>
        <template v-if="store.loadingOptions">
          <div class="space-y-2">
            <div v-for="i in 4" :key="i" class="h-10 animate-pulse rounded-luxe bg-canvas-warm/50"></div>
          </div>
        </template>
        <EmptyState v-else-if="!filteredOptions.length" title="暂无运费选项" hint="添加分区运费选项后结算报价才能计算运费与预计送达。" />
        <div v-else class="overflow-x-auto">
          <table class="data-table" data-testid="options-table">
            <thead>
              <tr><th>分区</th><th>承运商</th><th>等级</th><th class="text-right">基础</th><th class="text-right">满额</th><th class="text-right">门槛</th><th>运输天数</th><th>启用</th><th class="text-right">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in filteredOptions" :key="r.id" :class="!r.enabled && 'opacity-60'">
                <td class="whitespace-nowrap font-medium text-ink">{{ ZONE_LABEL[r.zone] || r.zone }}<span class="block font-mono text-[10px] font-normal text-ink-faint">{{ r.zone }}</span></td>
                <td class="whitespace-nowrap text-ink-soft">{{ r.carrierName || (r.carrierCode === 'ANY' ? '任意承运商' : r.carrierCode) }}<span class="block font-mono text-[10px] text-ink-faint">{{ r.carrierCode }}</span></td>
                <td>
                  <span class="rounded px-1.5 py-0.5 text-[10px]" :class="r.serviceLevel === ShippingServiceLevel.EXPRESS ? 'bg-gold/15 text-gold-deep' : 'bg-ink/6 text-ink-soft'">{{ SERVICE_LEVEL_LABEL[r.serviceLevel] }}</span>
                </td>
                <td class="whitespace-nowrap text-right text-ink-soft">{{ money(r.feeUnder) }}</td>
                <td class="whitespace-nowrap text-right" :class="r.feeOver === 0 ? 'text-ok' : 'text-ink-soft'">{{ feeOverText(r) }}</td>
                <td class="whitespace-nowrap text-right text-ink-soft">{{ thresholdText(r) }}</td>
                <td class="whitespace-nowrap text-ink-soft">{{ r.transitDaysMin ?? '—' }}~{{ r.transitDaysMax ?? '—' }} 天</td>
                <td><Toggle :model-value="r.enabled" @update:model-value="onToggleOption(r, $event)" /></td>
                <td>
                  <div class="flex items-center justify-end gap-1">
                    <button class="btn-ghost" @click="openOption(r)"><PencilSquareIcon class="h-4 w-4" /></button>
                    <button class="btn-danger-ghost" @click="askDeleteOption(r)"><TrashIcon class="h-4 w-4" /></button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 运费试算 -->
    <div class="mt-6">
      <QuotePreviewCard />
    </div>

    <CarrierFormDrawer :open="carrierDrawer" :editing="editingCarrier" @close="carrierDrawer = false" />
    <ShippingOptionFormDrawer :open="optionDrawer" :editing="editingOption" @close="optionDrawer = false" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="confirm?.message || ''"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>
