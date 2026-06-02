// 全局 toast 反馈（成功/错误提示，统一展示 BizError 中文 message）
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ToastItem {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

let seq = 0

export const useToastStore = defineStore('toast', () => {
  const items = ref<ToastItem[]>([])

  function push(type: ToastItem['type'], message: string, duration = 2600) {
    const id = ++seq
    items.value.push({ id, type, message })
    window.setTimeout(() => dismiss(id), duration)
  }

  function dismiss(id: number) {
    items.value = items.value.filter((t) => t.id !== id)
  }

  const success = (m: string) => push('success', m)
  const error = (m: string) => push('error', m)
  const info = (m: string) => push('info', m)

  return { items, push, dismiss, success, error, info }
})
