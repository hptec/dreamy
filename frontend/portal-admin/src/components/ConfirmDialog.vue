<script setup lang="ts">
// 二次确认弹窗（CP-071 危险操作通用载体；原型 modal 同型 token：panel/btn-outline/btn-gold/btn-danger）
import { XMarkIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'

withDefaults(
  defineProps<{
    open: boolean
    title?: string
    message?: string
    confirmText?: string
    danger?: boolean
    busy?: boolean
  }>(),
  { title: '确认操作', message: '', confirmText: '确认', danger: false, busy: false },
)

const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>()
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center bg-ink/40" @click.self="emit('cancel')">
      <div class="panel w-96 p-6">
        <div class="mb-4 flex items-center justify-between">
          <h3 class="flex items-center gap-2 text-[15px] font-medium text-ink">
            <ExclamationTriangleIcon v-if="danger" class="h-5 w-5 text-danger" />
            {{ title }}
          </h3>
          <button class="btn-ghost" @click="emit('cancel')"><XMarkIcon class="h-4 w-4" /></button>
        </div>
        <p class="text-[13px] leading-relaxed text-ink-soft">{{ message }}</p>
        <slot />
        <div class="mt-6 flex justify-end gap-2">
          <button class="btn-outline" :disabled="busy" @click="emit('cancel')">取消</button>
          <button
            :class="danger ? 'inline-flex items-center gap-1.5 rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/85 disabled:opacity-50' : 'btn-gold'"
            :disabled="busy"
            @click="emit('confirm')"
          >{{ busy ? '处理中…' : confirmText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
