import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * /unsubscribe 落地页交互测试（2026-07-18 退订变更）：
 * 五态流转（confirm→confirming→success / invalid[422704 终态] / error[瞬时故障可重试]）
 * + 缺 token 直接 invalid + 挂载即抹 query + 防重复提交。
 */

const replaceStateSpy = vi.fn()
const unsubscribeMock = vi.fn<(token: string) => Promise<boolean>>()

vi.mock('next/navigation', () => ({
  useSearchParams: () => new URLSearchParams(globalThis.__TEST_TOKEN__ == null ? '' : `token=${globalThis.__TEST_TOKEN__}`),
  usePathname: () => '/en/unsubscribe'
}))

vi.mock('@/lib/api/marketing-api', () => ({
  unsubscribeNewsletter: (token: string) => unsubscribeMock(token)
}))

vi.mock('@/lib/api/client', () => {
  // 类必须定义在 factory 内（vi.mock 提升到文件顶部，引用外部类会 TDZ 报错）
  class ApiError extends Error {
    readonly code: number
    constructor(code: number, message: string) {
      super(message)
      this.code = code
    }
  }
  return { ApiError }
})

vi.mock('@/lib/i18n/i18n-context', () => ({
  useI18n: () => ({
    t: {
      unsubscribe: {
        title: 'Unsubscribe from our newsletter',
        body: 'body',
        confirm: 'Confirm unsubscribe',
        confirming: 'Unsubscribing…',
        successTitle: 'You have been unsubscribed',
        successBody: 'success body',
        invalidTitle: 'This link is no longer valid',
        invalidBody: 'invalid body',
        errorTitle: 'Something went wrong',
        errorBody: 'error body',
        retry: 'Try again'
      }
    }
  })
}))

import { UnsubscribeConfirm } from '@/app/[locale]/unsubscribe/unsubscribe-confirm'
import { ApiError } from '@/lib/api/client'

declare global {
  // eslint-disable-next-line no-var
  var __TEST_TOKEN__: string | null | undefined
}

let root: Root | null = null
let container: HTMLDivElement | null = null
const actEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }

async function render() {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  await act(async () => root?.render(<UnsubscribeConfirm />))
  return container
}

describe('UnsubscribeConfirm', () => {
  beforeEach(() => {
    actEnvironment.IS_REACT_ACT_ENVIRONMENT = true
    unsubscribeMock.mockReset()
    replaceStateSpy.mockReset()
    window.history.replaceState = replaceStateSpy
  })

  afterEach(async () => {
    if (root) await act(async () => root?.unmount())
    root = null
    container?.remove()
    container = null
    globalThis.__TEST_TOKEN__ = undefined
  })

  it('带 token：渲染确认态，挂载即 replaceState 抹掉 query', async () => {
    globalThis.__TEST_TOKEN__ = 'signed-token'
    const el = await render()
    expect(el.textContent).toContain('Unsubscribe from our newsletter')
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', window.location.pathname)
  })

  it('缺 token：直接 invalid 态，不调 API，不抹 query', async () => {
    globalThis.__TEST_TOKEN__ = null
    const el = await render()
    expect(el.textContent).toContain('This link is no longer valid')
    expect(unsubscribeMock).not.toHaveBeenCalled()
    expect(replaceStateSpy).not.toHaveBeenCalled()
  })

  it('确认后成功：调用 API 并进入 success 态', async () => {
    globalThis.__TEST_TOKEN__ = 'signed-token'
    unsubscribeMock.mockResolvedValue(true)
    const el = await render()
    const button = el.querySelector('button')!
    await act(async () => button.click())
    expect(unsubscribeMock).toHaveBeenCalledWith('signed-token')
    expect(el.textContent).toContain('You have been unsubscribed')
  })

  it('422704（无效/过期/代际落后）：进入 invalid 终态，无重试按钮', async () => {
    globalThis.__TEST_TOKEN__ = 'stale-token'
    unsubscribeMock.mockRejectedValue(new ApiError(422704, 'invalid'))
    const el = await render()
    const button = el.querySelector('button')!
    await act(async () => button.click())
    expect(el.textContent).toContain('This link is no longer valid')
    expect(el.textContent).not.toContain('Try again')
  })

  it('网络/5xx 瞬时故障：进入 error 可重试态，重试后成功', async () => {
    globalThis.__TEST_TOKEN__ = 'signed-token'
    unsubscribeMock.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(true)
    const el = await render()
    await act(async () => el.querySelector('button')!.click())
    expect(el.textContent).toContain('Something went wrong')
    const retry = el.querySelector('button')!
    expect(retry.textContent).toBe('Try again')
    await act(async () => retry.click())
    expect(el.textContent).toContain('You have been unsubscribed')
    expect(unsubscribeMock).toHaveBeenCalledTimes(2)
  })

  it('防重复提交：confirming 态按钮 disabled，API 仅调用一次', async () => {
    globalThis.__TEST_TOKEN__ = 'signed-token'
    let resolveApi: (v: boolean) => void = () => {}
    unsubscribeMock.mockImplementation(() => new Promise<boolean>((r) => { resolveApi = r }))
    const el = await render()
    const button = el.querySelector('button')!
    await act(async () => button.click())
    expect(button.disabled).toBe(true)
    await act(async () => button.click())
    expect(unsubscribeMock).toHaveBeenCalledTimes(1)
    await act(async () => resolveApi(true))
    expect(el.textContent).toContain('You have been unsubscribed')
  })
})
