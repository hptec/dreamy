<script setup lang="ts">
// PAGE-MKT-A01 / COMP-MKT-A01：优惠券与促销（双 tab 布局保持；mock → E-MKT-13~20）
// 删除预判：券仅 draft/expired 可删，闪购仅 draft 可删（409703 兜底 toast）
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import CouponFormDrawer from '@/components/drawers/CouponFormDrawer.vue'
import FlashSaleFormDrawer from '@/components/drawers/FlashSaleFormDrawer.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { usePromotionsStore } from '@/stores/promotions'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { formatDateTime } from '@/utils/format'
import { PlusIcon, PencilSquareIcon, TrashIcon, ClockIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'
import { CouponStatus, CouponType, FlashSaleStatus } from '@/api/types'
import type { Coupon, FlashSale } from '@/api/types'

const store = usePromotionsStore()
const toast = useToastStore()

const tab = ref<'coupon' | 'flash'>('coupon')
// tone/label 映射（整数契约后券/闪购各自独立——CouponStatus 与 FlashSaleStatus 数值空间不同）
const couponTone: Record<number, string> = { [CouponStatus.ACTIVE]: 'ok', [CouponStatus.EXPIRING]: 'warn', [CouponStatus.DRAFT]: 'neutral', [CouponStatus.SCHEDULED]: 'info', [CouponStatus.EXPIRED]: 'neutral' }
const couponLabel: Record<number, string> = { [CouponStatus.ACTIVE]: '进行中', [CouponStatus.EXPIRING]: '即将到期', [CouponStatus.DRAFT]: '草稿', [CouponStatus.SCHEDULED]: '已排期', [CouponStatus.EXPIRED]: '已过期' }
const flashTone: Record<number, string> = { [FlashSaleStatus.ACTIVE]: 'ok', [FlashSaleStatus.DRAFT]: 'neutral', [FlashSaleStatus.SCHEDULED]: 'info', [FlashSaleStatus.ENDED]: 'neutral' }
const flashLabel: Record<number, string> = { [FlashSaleStatus.ACTIVE]: '进行中', [FlashSaleStatus.DRAFT]: '草稿', [FlashSaleStatus.SCHEDULED]: '已排期', [FlashSaleStatus.ENDED]: '已结束' }
const couponTypeLabel: Record<number, string> = { [CouponType.DISCOUNT]: '折扣', [CouponType.FIXED_AMOUNT]: '满减', [CouponType.FREE_SHIPPING]: '包邮' }

const couponDrawer = ref(false)
const editingCoupon = ref<Coupon | null>(null)
const flashDrawer = ref(false)
const editingFlash = ref<FlashSale | null>(null)
const confirm = ref<{ kind: 'coupon' | 'flash'; id: number; name: string } | null>(null)
const confirmBusy = ref(false)

function load() {
  store.fetchCoupons().catch((e) => toast.error(e instanceof BizError ? e.message : '加载优惠券失败'))
  store.fetchFlashSales().catch((e) => toast.error(e instanceof BizError ? e.message : '加载闪购失败'))
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    store.applyCouponFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
  }, 300)
}

function applyCouponFilters() {
  store.applyCouponFilters().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

function applyFlashFilters() {
  store.fetchFlashSales().catch((e) => toast.error(e instanceof BizError ? e.message : '加载失败'))
}

function openNew() {
  if (tab.value === 'coupon') {
    editingCoupon.value = null
    couponDrawer.value = true
  } else {
    editingFlash.value = null
    flashDrawer.value = true
  }
}

/** js_guard 前端预判（draft/expired 以外置灰 + tooltip） */
function couponDeletable(c: Coupon): boolean {
  return c.status === CouponStatus.DRAFT || c.status === CouponStatus.EXPIRED
}

async function doDelete() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    if (confirm.value.kind === 'coupon') await store.removeCoupon(confirm.value.id)
    else await store.removeFlashSale(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    if (e instanceof BizError && e.code === 409703) toast.error('当前发布状态不允许该操作')
    else toast.error(e instanceof BizError ? e.message : '删除失败')
  } finally {
    confirmBusy.value = false
  }
}

function fmtLimit(c: Coupon): string {
  // DEC-MKT-5：total>9999 显示「不限」
  if (c.totalLimit == null || c.totalLimit > 9999) return '不限'
  return c.totalLimit.toLocaleString()
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Marketing" title="优惠券与促销" subtitle="管理优惠券、促销规则与限时秒杀">
      <template #actions><button class="btn-primary" @click="openNew"><PlusIcon class="h-4 w-4" />新建</button></template>
    </PageHeader>

    <div class="mb-4 flex gap-1 border-b border-line">
      <button
        v-for="t in [['coupon', '优惠券'], ['flash', 'Flash Sale 秒杀']] as const"
        :key="t[0]"
        class="border-b-2 px-4 py-2.5 text-[13px] transition-colors"
        :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'"
        @click="tab = t[0]"
      >{{ t[1] }}</button>
    </div>

    <!-- 券 tab -->
    <div v-show="tab === 'coupon'">
      <div class="panel mb-4 p-4">
        <div class="flex flex-wrap items-center gap-3">
          <div class="relative min-w-[220px] flex-1">
            <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
            <input v-model="store.couponSearch" class="field pl-9" placeholder="搜索券码 / 名称…" @input="onSearchInput" />
          </div>
          <SelectMenu
            v-model="store.couponStatus"
            class="w-36 shrink-0"
            :options="[
              { value: 'all', label: '全部状态' },
              { value: CouponStatus.DRAFT, label: '草稿' },
              { value: CouponStatus.SCHEDULED, label: '已排期' },
              { value: CouponStatus.ACTIVE, label: '进行中' },
              { value: CouponStatus.EXPIRING, label: '即将到期' },
              { value: CouponStatus.EXPIRED, label: '已过期' },
            ]"
            @change="applyCouponFilters"
          />
        </div>
      </div>

      <div v-if="store.loadingCoupons" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div v-for="i in 3" :key="i" class="panel h-48 animate-pulse bg-canvas-warm/40"></div>
      </div>
      <EmptyState v-else-if="!store.coupons.length" title="暂无优惠券" hint="点击右上角「新建」创建第一张券。" />
      <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div v-for="c in store.coupons" :key="c.id" class="panel overflow-hidden">
          <div class="relative bg-gradient-to-br from-gold/15 to-blush/15 p-5">
            <div class="flex items-start justify-between">
              <div>
                <p class="font-display text-2xl font-semibold text-gold-deep">{{ c.value }}</p>
                <p class="mt-1 text-[13px] text-ink">{{ c.name }}</p>
              </div>
              <StatusBadge :tone="couponTone[c.status]" :label="couponLabel[c.status]" />
            </div>
            <div class="mt-3 inline-block rounded-luxe border border-dashed border-gold bg-white/60 px-3 py-1 font-mono text-[13px] font-medium text-ink">{{ c.code }}</div>
            <span class="absolute -left-2 top-1/2 h-4 w-4 rounded-full bg-canvas"></span>
            <span class="absolute -right-2 top-1/2 h-4 w-4 rounded-full bg-canvas"></span>
          </div>
          <div class="p-4 text-[12px] text-ink-soft">
            <p>门槛：{{ Number(c.minAmount) > 0 ? '满 $' + Number(c.minAmount) : '无门槛' }} · 类型：{{ couponTypeLabel[c.type] || c.type }}</p>
            <p v-if="c.startAt">有效期：{{ formatDateTime(c.startAt) }} → {{ formatDateTime(c.endAt) }}</p>
            <div class="mt-2 flex items-center justify-between">
              <span>已用 {{ (c.usedCount ?? 0).toLocaleString() }} / {{ fmtLimit(c) }}</span>
              <div class="flex gap-1">
                <button class="btn-ghost" @click="editingCoupon = c; couponDrawer = true"><PencilSquareIcon class="h-4 w-4" /></button>
                <button
                  class="btn-danger-ghost disabled:opacity-40"
                  :disabled="!couponDeletable(c)"
                  :title="couponDeletable(c) ? '删除' : '仅草稿/已过期券可删除'"
                  @click="confirm = { kind: 'coupon', id: c.id, name: c.code }"
                ><TrashIcon class="h-4 w-4" /></button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="store.couponsTotal > store.couponPageSize" class="panel mt-4">
        <Pagination :total="store.couponsTotal" :page="store.couponPage" :per-page="store.couponPageSize" @change="(p) => store.setCouponPage(p)" />
      </div>
    </div>

    <!-- 闪购 tab -->
    <div v-show="tab === 'flash'">
      <div class="panel mb-4 p-4">
        <SelectMenu
          v-model="store.flashStatus"
          class="w-36"
          :options="[
            { value: 'all', label: '全部状态' },
            { value: FlashSaleStatus.DRAFT, label: '草稿' },
            { value: FlashSaleStatus.SCHEDULED, label: '已排期' },
            { value: FlashSaleStatus.ACTIVE, label: '进行中' },
            { value: FlashSaleStatus.ENDED, label: '已结束' },
          ]"
          @change="applyFlashFilters"
        />
      </div>
      <div class="panel overflow-hidden">
        <table class="data-table">
          <thead><tr><th>活动名称</th><th class="text-right">商品数</th><th>折扣</th><th>开始</th><th>结束</th><th>状态</th><th class="text-right">操作</th></tr></thead>
          <tbody>
            <tr v-if="store.loadingFlash"><td colspan="7" class="py-10 text-center text-ink-faint">加载中…</td></tr>
            <tr v-for="f in store.flashSales" v-else :key="f.id">
              <td class="flex items-center gap-2 font-medium text-ink"><ClockIcon class="h-4 w-4 text-gold-deep" />{{ f.name }}</td>
              <td class="text-right">{{ f.productIds?.length ?? 0 }}</td>
              <td class="text-gold-deep">{{ f.discount }}</td>
              <td class="text-[12px] text-ink-soft">{{ formatDateTime(f.startAt) }}</td>
              <td class="text-[12px] text-ink-soft">{{ formatDateTime(f.endAt) }}</td>
              <td><StatusBadge :tone="flashTone[f.status]" :label="flashLabel[f.status]" /></td>
              <td>
                <div class="flex items-center justify-end gap-1">
                  <button
                    class="btn-ghost disabled:opacity-40"
                    :disabled="f.status === FlashSaleStatus.ENDED"
                    :title="f.status === FlashSaleStatus.ENDED ? '已结束活动不可编辑' : '编辑'"
                    @click="editingFlash = f; flashDrawer = true"
                  ><PencilSquareIcon class="h-4 w-4" />编辑</button>
                  <button
                    v-if="f.status === FlashSaleStatus.DRAFT"
                    class="btn-danger-ghost"
                    @click="confirm = { kind: 'flash', id: f.id, name: f.name }"
                  ><TrashIcon class="h-4 w-4" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="!store.loadingFlash && !store.flashSales.length" title="暂无闪购活动" />
      </div>
    </div>

    <CouponFormDrawer :open="couponDrawer" :editing="editingCoupon" @close="couponDrawer = false" />
    <FlashSaleFormDrawer :open="flashDrawer" :editing="editingFlash" @close="flashDrawer = false" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="`确认删除「${confirm?.name}」？删除后不可恢复。`"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doDelete"
      @cancel="confirm = null"
    />
  </div>
</template>
