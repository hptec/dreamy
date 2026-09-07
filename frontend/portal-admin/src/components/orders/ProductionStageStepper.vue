<script setup lang="ts">
// order-flow-complete J：制作阶段步进器（仅 PAID 态渲染；4 段；「推进到下一阶段」/「回退一档」；跳级不可——按钮只提供 ±1）
import { computed, ref } from 'vue'
import { useOrdersStore } from '@/stores/orders'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { PRODUCTION_STAGES, productionStageMeta } from '@/utils/tradingLabels'
import type { ProductionStage } from '@/api/types'
import { ArrowLeftIcon, ArrowRightIcon, CheckIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{ orderId: number; stage: ProductionStage | null | undefined }>()

const store = useOrdersStore()
const toast = useToastStore()

const current = computed(() => props.stage ?? null)
const idx = computed(() => (current.value == null ? -1 : PRODUCTION_STAGES.indexOf(current.value)))
const next = computed<ProductionStage | null>(() => (idx.value >= 0 && idx.value < PRODUCTION_STAGES.length - 1 ? PRODUCTION_STAGES[idx.value + 1]! : null))
const prev = computed<ProductionStage | null>(() => (idx.value > 0 ? PRODUCTION_STAGES[idx.value - 1]! : null))
const busy = ref(false)

async function go(target: ProductionStage | null) {
  if (target == null || busy.value) return
  busy.value = true
  try {
    await store.patchProductionStage(props.orderId, target)
    toast.success(`制作阶段已更新为「${productionStageMeta(target).label}」`)
  } catch (e) {
    toast.error(describeError(e, '更新制作阶段失败'))
    if (e instanceof BizError && e.code === 409602) store.refreshDetail(props.orderId).catch(() => undefined)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="rounded-luxe border border-gold/30 bg-gold/5 p-4" data-testid="production-stepper">
    <div class="mb-3 flex items-center justify-between">
      <p class="text-[12px] font-medium uppercase tracking-luxe text-gold-deep">制作阶段</p>
      <p class="text-[12px] text-ink-soft">当前：<span class="font-medium text-ink" data-testid="production-stage-current">{{ productionStageMeta(current).label }}</span></p>
    </div>
    <div class="flex items-center">
      <template v-for="(st, i) in PRODUCTION_STAGES" :key="st">
        <div class="flex flex-col items-center text-center">
          <span
            class="flex h-7 w-7 items-center justify-center rounded-full border-2 text-[11px]"
            :class="i < idx ? 'border-gold bg-gold text-white' : i === idx ? 'border-gold bg-white text-gold-deep font-semibold' : 'border-line text-ink-faint'"
          >
            <CheckIcon v-if="i < idx" class="h-3.5 w-3.5" /><span v-else>{{ i + 1 }}</span>
          </span>
          <p class="mt-1 text-[11px]" :class="i <= idx ? 'font-medium text-ink' : 'text-ink-faint'">{{ productionStageMeta(st).label }}</p>
        </div>
        <div v-if="i < PRODUCTION_STAGES.length - 1" class="mx-1.5 mb-4 h-px flex-1" :class="i < idx ? 'bg-gold' : 'bg-line'"></div>
      </template>
    </div>
    <div class="mt-3 flex items-center justify-end gap-2">
      <button class="btn-ghost" :disabled="busy || prev == null" :title="prev == null ? '已是第一阶段' : `回退到「${productionStageMeta(prev).label}」`" data-testid="production-prev" @click="go(prev)">
        <ArrowLeftIcon class="h-4 w-4" />回退一档
      </button>
      <button class="btn-gold" :disabled="busy || next == null" :title="next == null ? '已是最后阶段，可创建包裹发货' : `推进到「${productionStageMeta(next).label}」`" data-testid="production-next" @click="go(next)">
        {{ busy ? '处理中…' : next == null ? '已可发货' : `推进到「${productionStageMeta(next).label}」` }}<ArrowRightIcon v-if="next != null" class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>
