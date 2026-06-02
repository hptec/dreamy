'use client'

/**
 * COMP-S03 <OtpInput>：6 格（或 N 格）OTP 输入。
 * 原型交互：输入数字自动跳下一格 / 退格回上一格 / 粘贴铺满 / 满位触发 onComplete（FORM-S02）。
 * 无障碍：每格 aria-label（UIS-S01）。
 */

import { useEffect, useRef } from 'react'

interface OtpInputProps {
  length: number
  value: string[]
  onChange: (next: string[]) => void
  onComplete: () => void
  autoFocus?: boolean
  ariaLabelPrefix?: string
}

export function OtpInput({
  length,
  value,
  onChange,
  onComplete,
  autoFocus = true,
  ariaLabelPrefix = 'Digit'
}: OtpInputProps) {
  const inputs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (autoFocus) {
      const t = setTimeout(() => inputs.current[0]?.focus(), 50)
      return () => clearTimeout(t)
    }
  }, [autoFocus])

  function setDigit(i: number, val: string) {
    const v = val.replace(/\D/g, '').slice(-1)
    const next = [...value]
    next[i] = v
    onChange(next)
    if (v && i < length - 1) inputs.current[i + 1]?.focus()
    if (next.every((d) => d) && next.length === length) {
      // 满位触发提交
      onComplete()
    }
  }

  function onKeyDown(i: number, e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace' && !value[i] && i > 0) inputs.current[i - 1]?.focus()
  }

  function onPaste(e: React.ClipboardEvent) {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!text) return
    e.preventDefault()
    const next = text.split('')
    while (next.length < length) next.push('')
    onChange(next)
    inputs.current[Math.min(text.length, length - 1)]?.focus()
    if (text.length === length) onComplete()
  }

  return (
    <div className="flex gap-2" onPaste={onPaste}>
      {Array.from({ length }).map((_, i) => (
        <input
          key={i}
          ref={(el) => {
            inputs.current[i] = el
          }}
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          maxLength={1}
          aria-label={`${ariaLabelPrefix} ${i + 1}`}
          value={value[i] ?? ''}
          onChange={(e) => setDigit(i, e.target.value)}
          onKeyDown={(e) => onKeyDown(i, e)}
          className="h-14 w-full rounded-sm border border-line bg-surface text-center font-display text-2xl outline-none focus:border-gold"
        />
      ))}
    </div>
  )
}
