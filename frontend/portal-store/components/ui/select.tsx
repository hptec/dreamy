'use client'

import { useEffect, useId, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { Check, ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface SelectOption<T extends string = string> {
  value: T
  label: string
}

/**
 * 自绘下拉（替换原生 <select>——原生 option 弹层由 OS 渲染，与站点 luxe 风格脱节）。
 * 弹层经 portal 渲染到 body + fixed 定位，避免被 overflow / transform 祖先裁剪；
 * 键盘：↑/↓ 移动高亮、Enter/Space 确认、Esc/Tab/点击外部关闭；焦点留在触发器
 * （combobox 模式，aria-activedescendant 同步高亮项）。
 */
export function Select<T extends string>({
  value,
  options,
  onChange,
  id,
  ariaLabel,
  align = 'left',
  triggerClassName,
  optionClassName,
  disabled = false
}: {
  value: T
  options: readonly SelectOption<T>[]
  onChange: (value: T) => void
  id?: string
  ariaLabel?: string
  align?: 'left' | 'right'
  triggerClassName?: string
  optionClassName?: string
  disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [popupStyle, setPopupStyle] = useState<CSSProperties | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const listId = useId()

  const selectedIndex = options.findIndex((o) => o.value === value)
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined

  const updatePosition = () => {
    const rect = triggerRef.current?.getBoundingClientRect()
    if (!rect) return
    const flip = rect.bottom + 300 > window.innerHeight && rect.top > 320
    setPopupStyle({
      position: 'fixed',
      minWidth: rect.width,
      ...(flip ? { bottom: window.innerHeight - rect.top + 4 } : { top: rect.bottom + 4 }),
      ...(align === 'right' ? { left: rect.right, transform: 'translateX(-100%)' } : { left: rect.left })
    })
  }

  useEffect(() => {
    if (!open) return
    updatePosition()
    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      const target = e.target as Node
      if (!rootRef.current?.contains(target) && !listRef.current?.contains(target)) setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('touchstart', onPointerDown)
    window.addEventListener('scroll', updatePosition, true)
    window.addEventListener('resize', updatePosition)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('touchstart', onPointerDown)
      window.removeEventListener('scroll', updatePosition, true)
      window.removeEventListener('resize', updatePosition)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, align])

  useEffect(() => {
    if (open) setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0)
    else setPopupStyle(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  useEffect(() => {
    if (!open || activeIndex < 0) return
    const el = listRef.current?.children[activeIndex] as HTMLElement | undefined
    el?.scrollIntoView({ block: 'nearest' })
  }, [activeIndex, open])

  const commit = (i: number) => {
    const opt = options[i]
    if (!opt) return
    onChange(opt.value)
    setOpen(false)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return
    switch (e.key) {
      case 'Enter':
      case ' ':
        e.preventDefault()
        if (open) commit(activeIndex)
        else setOpen(true)
        break
      case 'ArrowDown':
        e.preventDefault()
        if (!open) setOpen(true)
        else setActiveIndex((i) => Math.min(options.length - 1, i + 1))
        break
      case 'ArrowUp':
        e.preventDefault()
        if (open) setActiveIndex((i) => Math.max(0, i - 1))
        break
      case 'Home':
        if (open) {
          e.preventDefault()
          setActiveIndex(0)
        }
        break
      case 'End':
        if (open) {
          e.preventDefault()
          setActiveIndex(options.length - 1)
        }
        break
      case 'Escape':
        if (open) {
          e.preventDefault()
          e.stopPropagation()
          setOpen(false)
        }
        break
      case 'Tab':
        if (open) setOpen(false)
        break
    }
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        id={id}
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-label={ariaLabel}
        aria-activedescendant={open && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        onKeyDown={onKeyDown}
        className={cn(
          'flex w-full cursor-pointer items-center justify-between gap-2 rounded-sm border border-line bg-surface text-left outline-none transition-colors focus:border-gold',
          disabled && 'cursor-not-allowed opacity-60',
          triggerClassName
        )}
      >
        <span className="truncate">{selected?.label ?? ''}</span>
        <ChevronDown className={cn('h-3.5 w-3.5 shrink-0 text-ink-soft transition-transform duration-300 ease-luxe', open && 'rotate-180')} />
      </button>
      {open &&
        popupStyle &&
        options.length > 0 &&
        createPortal(
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            aria-label={ariaLabel}
            style={popupStyle}
            className="z-[70] max-h-72 overflow-auto rounded-sm border border-line bg-surface py-1 shadow-lift"
          >
            {options.map((opt, i) => (
              <li
                key={opt.value}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={opt.value === value}
                onMouseEnter={() => setActiveIndex(i)}
                onClick={() => commit(i)}
                className={cn(
                  'flex cursor-pointer select-none items-center justify-between gap-3 whitespace-nowrap px-4 py-2 text-sm',
                  i === activeIndex ? 'bg-muted text-ink' : 'text-ink-soft',
                  optionClassName
                )}
              >
                <span className={cn('truncate', opt.value === value && 'font-medium text-gold-deep')}>{opt.label}</span>
                {opt.value === value && <Check className="h-3.5 w-3.5 shrink-0 text-gold" />}
              </li>
            ))}
          </ul>,
          document.body
        )}
    </div>
  )
}
