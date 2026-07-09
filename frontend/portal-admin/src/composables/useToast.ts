import { useToastStore } from '@/stores/toast'

/**
 * Toast 通知 composable
 * 封装 toast store 提供便捷的通知方法
 */
export function useToast() {
  const store = useToastStore()

  return {
    success: store.success,
    error: store.error,
    info: store.info,
    warn: store.warn,
  }
}
