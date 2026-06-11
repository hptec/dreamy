<script setup lang="ts">
// PAGE-SHP-01 / COMP-SHP-01~03：物流配置（双 panel 布局零改动；mock → E-SHP-01~09）
// DEC-SHP-FE-1：移除「保存配置」按钮（逐资源即时持久化）；DEC-SHP-FE-2：行内编辑/删除补齐
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Toggle from '@/components/Toggle.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import CarrierFormDrawer from '@/components/drawers/CarrierFormDrawer.vue'
import RateFormDrawer from '@/components/drawers/RateFormDrawer.vue'
import { useShippingStore } from '@/stores/shipping'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { PlusIcon, TruckIcon, PencilSquareIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { Carrier, ShippingRate } from '@/api/types'

const store = useShippingStore()
const toast = useToastStore()

const carrierDrawer = ref(false)
const editingCarrier = ref<Carrier | null>(null)
const rateDrawer = ref(false)
const editingRate = ref<ShippingRate | null>(null)

const confirm = ref<{ kind: 'carrier' | 'rate'; id: number; message: string } | null>(null)
const confirmBusy = ref(false)

function load() {
  store.fetchAll().catch((e) => toast.error(e instanceof BizError ? e.message : '加载物流配置失败'))
}

onMounted(load)

/** 预判：仅剩 1 个启用承运方时禁用其 Toggle/删除（后端 409902 兜底） */
function lastEnabled(c: Carrier): boolean {
  return c.status === 'enabled' && store.enabledCount <= 1
}

async function onToggleCarrier(c: Carrier, on: boolean) {
  if (!on && lastEnabled(c)) {
    toast.error('至少保留一个启用的承运方')
    return
  }
  try {
    await store.toggleCarrier(c, on ? 'enabled' : 'disabled')
  } catch (e) {
    if (e instanceof BizError && e.code === 404901) {
      toast.error('数据已变更，列表已刷新')
      load()
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  }
}

function openCarrier(c?: Carrier) {
  editingCarrier.value = c ?? null
  carrierDrawer.value = true
}

function openRate(r?: ShippingRate) {
  editingRate.value = r ?? null
  rateDrawer.value = true
}

function askDeleteCarrier(c: Carrier) {
  confirm.value = {
    kind: 'carrier',
    id: c.id,
    message: '删除后该承运商不再出现在结算选项；历史订单不受影响。确认删除？',
  }
}

/** FORM-SHP-03 / CV-SHP-006：全局兜底行删除警示 */
function askDeleteRate(r: ShippingRate) {
  const isFallback = !r.zone.includes(' / ') && r.zone === 'Rest of World'
  confirm.value = {
    kind: 'rate',
    id: r.id,
    message: isFallback
      ? '这是全局兜底行，删除后未配置区域将无运费报价。确认删除？'
      : `确认删除邮费规则「${r.zone}」？`,
  }
}

async function doConfirm() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    if (confirm.value.kind === 'carrier') await store.removeCarrier(confirm.value.id)
    else await store.removeRate(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    if (e instanceof BizError && (e.code === 404901 || e.code === 404902)) {
      toast.error('数据已变更，列表已刷新')
      confirm.value = null
      load()
    } else {
      toast.error(e instanceof BizError ? e.message : '操作失败')
    }
  } finally {
    confirmBusy.value = false
  }
}

/** DEC-SHP-3 语义可视化：null → —；fee_over=0 → 免邮（text-ok） */
function money(v?: number | null): string {
  return v == null ? '—' : '$' + Number(v).toFixed(2)
}
function feeOverText(r: ShippingRate): string {
  return r.feeOver === 0 ? '免邮' : money(r.feeOver)
}
function thresholdText(r: ShippingRate): string {
  return r.threshold == null ? '—' : '$' + Number(r.threshold)
}
</script>

<template>
  <div class="animate-fadeup">
    <!-- DEC-SHP-FE-1：actions 槽不再有「保存配置」（行内操作即时落库） -->
    <PageHeader eyebrow="Settings" title="物流配置" subtitle="管理承运方、国际邮费表与运送规则" />

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <!-- COMP-SHP-02 承运方面板 -->
      <div class="panel p-6">
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
              <p class="text-[13px] font-medium text-ink">{{ c.name }}</p>
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
              <Toggle :model-value="c.status === 'enabled'" @update:model-value="onToggleCarrier(c, $event)" />
            </span>
          </div>
          <button class="btn-ghost" @click="openCarrier()"><PlusIcon class="h-4 w-4" />添加承运方</button>
        </div>
      </div>

      <!-- COMP-SHP-03 国际邮费表面板 -->
      <div class="panel p-6">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-display text-lg font-semibold text-ink">国际邮费表（分区）</h3>
          <button class="btn-ghost" @click="openRate()"><PlusIcon class="h-4 w-4" />添加规则</button>
        </div>
        <template v-if="store.loadingRates">
          <div class="space-y-2">
            <div v-for="i in 4" :key="i" class="h-10 animate-pulse rounded-luxe bg-canvas-warm/50"></div>
          </div>
        </template>
        <EmptyState v-else-if="!store.rates.length" title="暂无邮费规则" hint="添加分区规则后结算报价才能计算运费。" />
        <table v-else class="data-table">
          <thead>
            <tr><th>区域</th><th class="text-right">基础邮费</th><th class="text-right">满额包邮</th><th class="text-right">门槛</th><th class="text-right">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in store.rates" :key="r.id">
              <td class="font-medium text-ink">{{ r.zone }}</td>
              <td class="text-right text-ink-soft">{{ money(r.feeUnder) }}</td>
              <td class="text-right" :class="r.feeOver === 0 ? 'text-ok' : 'text-ink-soft'">{{ feeOverText(r) }}</td>
              <td class="text-right text-ink-soft">{{ thresholdText(r) }}</td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <button class="btn-ghost" @click="openRate(r)"><PencilSquareIcon class="h-4 w-4" /></button>
                  <button class="btn-danger-ghost" @click="askDeleteRate(r)"><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <CarrierFormDrawer :open="carrierDrawer" :editing="editingCarrier" @close="carrierDrawer = false" />
    <RateFormDrawer :open="rateDrawer" :editing="editingRate" @close="rateDrawer = false" />

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
