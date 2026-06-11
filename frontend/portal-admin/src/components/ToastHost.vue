<script setup lang="ts">
// 全局 toast 容器（成功/错误/信息/警告），挂在 App 根，统一展示 BizError 中文 message
import { useToastStore } from '@/stores/toast'
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
} from '@heroicons/vue/24/outline'

const toast = useToastStore()

const toneClass: Record<string, string> = {
  success: 'bg-ink text-canvas',
  error: 'bg-danger text-white',
  info: 'bg-info text-white',
  warn: 'bg-warn text-white',
}
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed bottom-6 left-1/2 z-[100] flex -translate-x-1/2 flex-col items-center gap-2">
      <transition-group
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="translate-y-2 opacity-0"
        enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-for="t in toast.items"
          :key="t.id"
          class="pointer-events-auto flex items-center gap-2 rounded-luxe px-5 py-2.5 text-[13px] shadow-2xl"
          :class="toneClass[t.type]"
          role="status"
          aria-live="polite"
        >
          <CheckCircleIcon v-if="t.type === 'success'" class="h-4 w-4 shrink-0" />
          <ExclamationCircleIcon v-else-if="t.type === 'error'" class="h-4 w-4 shrink-0" />
          <ExclamationTriangleIcon v-else-if="t.type === 'warn'" class="h-4 w-4 shrink-0" />
          <InformationCircleIcon v-else class="h-4 w-4 shrink-0" />
          <span>{{ t.message }}</span>
        </div>
      </transition-group>
    </div>
  </Teleport>
</template>
