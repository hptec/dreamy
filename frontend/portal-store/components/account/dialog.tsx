'use client'

/**
 * 通用模态对话框（COMP-S08/S09 基座）。
 * 无障碍：role=dialog + aria-modal + 焦点陷阱 + Esc 关闭 + 点击遮罩关闭（UIS-S04 dialog role / focus-trap）。
 */

import { useCallback, useEffect, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

interface DialogProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  danger?: boolean
}

export function Dialog({ open, title, onClose, children, danger = false }: DialogProps) {
  const panelRef = useRef<HTMLDivElement>(null)

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
        return
      }
      if (e.key !== 'Tab') return
      const panel = panelRef.current
      if (!panel) return
      const focusable = panel.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    },
    [onClose]
  )

  useEffect(() => {
    if (!open) return
    document.addEventListener('keydown', handleKeyDown)
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // 自动聚焦面板首个可聚焦元素
    const t = setTimeout(() => {
      const el = panelRef.current?.querySelector<HTMLElement>(
        'input, button:not([data-close])'
      )
      el?.focus()
    }, 30)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = prevOverflow
      clearTimeout(t)
    }
  }, [open, handleKeyDown])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-ink/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="relative z-10 w-full max-w-md rounded-sm border border-line bg-surface p-6 shadow-lift"
      >
        <div className="flex items-start justify-between gap-4">
          <h2 className={danger ? 'font-display text-2xl font-medium text-blush' : 'font-display text-2xl font-medium'}>
            {title}
          </h2>
          <button
            type="button"
            data-close="true"
            onClick={onClose}
            aria-label="Close"
            className="rounded-sm p-1 text-ink-faint transition-colors hover:text-ink"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  )
}
