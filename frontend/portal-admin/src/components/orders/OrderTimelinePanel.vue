<script setup lang="ts">
// order-flow-complete J：订单时间线 / 备注面板（events 全量按时间倒序：类型图标 + actor + 可见性标识；底部备注输入 content + 顾客可见开关）
import { computed, ref } from 'vue'
import Toggle from '@/components/Toggle.vue'
import { useOrdersStore } from '@/stores/orders'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { describeError } from '@/constants/tradingErrors'
import { extractFieldErrors, type FieldErrors } from '@/utils/validators'
import { formatDateTime } from '@/utils/format'
import { ORDER_ACTOR_LABEL, ORDER_EVENT_TYPE_LABEL } from '@/utils/tradingLabels'
import { OrderEventType } from '@/api/types'
import type { OrderEvent } from '@/api/types'
import {
  ArrowPathIcon, ChatBubbleLeftEllipsisIcon, CreditCardIcon, EnvelopeIcon, EyeIcon, EyeSlashIcon, ArrowUturnLeftIcon, TruckIcon, WrenchScrewdriverIcon,
} from '@heroicons/vue/24/outline'
import type { FunctionalComponent } from 'vue'

const props = defineProps<{ orderId: number; events: OrderEvent[] }>()

const store = useOrdersStore()
const toast = useToastStore()

const ICON: Record<number, FunctionalComponent> = {
  [OrderEventType.STATUS_CHANGED]: ArrowPathIcon,
  [OrderEventType.NOTE]: ChatBubbleLeftEllipsisIcon,
  [OrderEventType.SHIPMENT]: TruckIcon,
  [OrderEventType.PAYMENT]: CreditCardIcon,
  [OrderEventType.REFUND]: ArrowUturnLeftIcon,
  [OrderEventType.EMAIL]: EnvelopeIcon,
  [OrderEventType.PRODUCTION]: WrenchScrewdriverIcon,
}
const ICON_TONE: Record<number, string> = {
  [OrderEventType.STATUS_CHANGED]: 'bg-info/12 text-info',
  [OrderEventType.NOTE]: 'bg-gold/15 text-gold-deep',
  [OrderEventType.SHIPMENT]: 'bg-info/12 text-info',
  [OrderEventType.PAYMENT]: 'bg-ok/12 text-ok',
  [OrderEventType.REFUND]: 'bg-danger/12 text-danger',
  [OrderEventType.EMAIL]: 'bg-ink/8 text-ink-soft',
  [OrderEventType.PRODUCTION]: 'bg-warn/14 text-warn',
}

const sorted = computed(() => [...props.events].sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : b.id - a.id)))

const noteForm = ref({ content: '', customerVisible: false })
const noteErrors = ref<FieldErrors>({})
const noteSaving = ref(false)

async function submitNote() {
  if (noteSaving.value) return
  const content = noteForm.value.content.trim()
  noteErrors.value = {}
  if (!content) noteErrors.value.content = '备注内容必填'
  else if (content.length > 512) noteErrors.value.content = '不超过 512 字符'
  if (Object.keys(noteErrors.value).length) return
  noteSaving.value = true
  try {
    await store.addNote(props.orderId, content, noteForm.value.customerVisible)
    toast.success(noteForm.value.customerVisible ? '备注已添加（顾客可见）' : '内部备注已添加')
    noteForm.value = { content: '', customerVisible: false }
  } catch (e) {
    if (e instanceof BizError && e.code === 422601) {
      const fields = extractFieldErrors(e)
      if (Object.keys(fields).length) noteErrors.value = fields
      else toast.error(describeError(e))
    } else {
      toast.error(describeError(e, '添加备注失败'))
    }
  } finally {
    noteSaving.value = false
  }
}
</script>

<template>
  <div class="panel" data-testid="order-timeline-panel">
    <div class="border-b border-line px-6 py-4">
      <h3 class="font-display text-lg font-semibold text-ink">时间线与备注</h3>
    </div>
    <ol class="max-h-[32rem] space-y-4 overflow-y-auto px-6 py-4" data-testid="order-events">
      <li v-if="!sorted.length" class="text-[13px] text-ink-faint">暂无事件</li>
      <li v-for="ev in sorted" :key="ev.id" class="flex gap-3">
        <span class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full" :class="ICON_TONE[ev.type] || 'bg-ink/8 text-ink-soft'">
          <component :is="ICON[ev.type] || ArrowPathIcon" class="h-4 w-4" />
        </span>
        <div class="min-w-0 flex-1">
          <p class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[13px] text-ink">
            <span class="font-medium">{{ ev.title }}</span>
            <span class="rounded bg-ink/6 px-1.5 py-0.5 text-[10px] text-ink-soft">{{ ORDER_EVENT_TYPE_LABEL[ev.type] || ev.type }}</span>
            <span
              class="inline-flex items-center gap-0.5 text-[10px]"
              :class="ev.customerVisible ? 'text-ok' : 'text-ink-faint'"
              :title="ev.customerVisible ? '顾客可见' : '仅内部可见'"
            >
              <component :is="ev.customerVisible ? EyeIcon : EyeSlashIcon" class="h-3 w-3" />{{ ev.customerVisible ? '顾客可见' : '内部' }}
            </span>
          </p>
          <p v-if="ev.detail" class="mt-0.5 whitespace-pre-wrap break-words text-[12px] text-ink-soft">{{ ev.detail }}</p>
          <p class="mt-0.5 text-[11px] text-ink-faint">
            {{ formatDateTime(ev.createdAt) }} · {{ ORDER_ACTOR_LABEL[ev.actorType] || '—' }}<template v-if="ev.actorName">（{{ ev.actorName }}）</template><template v-else-if="ev.actorId">（#{{ ev.actorId }}）</template>
          </p>
        </div>
      </li>
    </ol>
    <div class="border-t border-line px-6 py-4">
      <textarea
        v-model="noteForm.content"
        rows="2"
        class="field resize-none"
        maxlength="512"
        placeholder="添加备注（≤512 字）…"
        data-testid="order-note-input"
      ></textarea>
      <p v-if="noteErrors.content" class="mt-1 text-[11px] text-danger">{{ noteErrors.content }}</p>
      <div class="mt-2 flex items-center justify-between">
        <label class="flex items-center gap-2 text-[12px] text-ink-soft">
          <Toggle v-model="noteForm.customerVisible" />
          顾客可见
          <span class="text-ink-faint">{{ noteForm.customerVisible ? '（前台订单详情将展示）' : '（仅内部可见）' }}</span>
        </label>
        <button class="btn-gold" :disabled="noteSaving" data-testid="order-note-submit" @click="submitNote">{{ noteSaving ? '提交中…' : '添加备注' }}</button>
      </div>
    </div>
  </div>
</template>
