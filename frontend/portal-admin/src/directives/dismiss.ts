import type { Directive } from 'vue'

type DismissHandler = (e: PointerEvent) => void

interface DismissEl extends HTMLElement {
  _dismiss?: {
    handler: DismissHandler
    down: boolean
    onPointerDown: (e: PointerEvent) => void
    onPointerUp: (e: PointerEvent) => void
  }
}

// 遮罩关闭不能用 @click.self：mousedown 在面板内输入框、mouseup 拖到遮罩上时，
// 浏览器会把 click 派发到共同祖先（即遮罩自身），导致弹层误关。
// 因此要求 pointerdown 与 pointerup 都落在遮罩本身才触发关闭。
export const vDismiss: Directive<DismissEl, DismissHandler> = {
  mounted(el, binding) {
    const state = {
      handler: binding.value,
      down: false,
      onPointerDown(e: PointerEvent) {
        state.down = e.target === el
      },
      onPointerUp(e: PointerEvent) {
        if (state.down && e.target === el) state.handler(e)
        state.down = false
      },
    }
    el._dismiss = state
    el.addEventListener('pointerdown', state.onPointerDown)
    el.addEventListener('pointerup', state.onPointerUp)
  },
  updated(el, binding) {
    if (el._dismiss) el._dismiss.handler = binding.value
  },
  unmounted(el) {
    if (!el._dismiss) return
    el.removeEventListener('pointerdown', el._dismiss.onPointerDown)
    el.removeEventListener('pointerup', el._dismiss.onPointerUp)
    delete el._dismiss
  },
}
