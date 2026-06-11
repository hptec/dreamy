// downloadCsv 单测（FND-REUSE-001）：锁定去重 helper 的行为与原 exportProducts/exportOrders 内联实现一致
// node 环境下 stub fetch / document / URL.createObjectURL，断言 URL 拼接、鉴权头、文件名解析与截断标记
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const getToken = vi.fn()

vi.mock('@/api/client', () => ({
  getToken: (...args: unknown[]) => getToken(...args),
}))

import { downloadCsv } from '@/utils/download'

interface MockResponseInit {
  ok?: boolean
  headers?: Record<string, string>
  json?: unknown
}

function mockResponse(init: MockResponseInit = {}) {
  const headers = init.headers ?? {}
  return {
    ok: init.ok ?? true,
    headers: { get: (key: string) => headers[key] ?? null },
    blob: async () => new Blob(['col_a,col_b\n1,2']),
    json: async () => {
      if (init.json === undefined) throw new Error('no json body')
      return init.json
    },
  }
}

const fetchMock = vi.fn()
const anchor = { href: '', download: '', click: vi.fn(), remove: vi.fn() }
const appendChild = vi.fn()

beforeEach(() => {
  getToken.mockReturnValue('tok-123')
  anchor.href = ''
  anchor.download = ''
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('document', {
    createElement: () => anchor,
    body: { appendChild },
  })
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  fetchMock.mockReset()
  anchor.click.mockReset()
  anchor.remove.mockReset()
  appendChild.mockReset()
  getToken.mockReset()
})

describe('downloadCsv', () => {
  it('带 query 与 Authorization 请求，优先取 Content-Disposition 文件名并解析截断标记', async () => {
    fetchMock.mockResolvedValue(
      mockResponse({
        headers: {
          'Content-Disposition': 'attachment; filename="products-20260611.csv"',
          'X-Export-Truncated': 'true',
        },
      }),
    )
    const query = new URLSearchParams()
    query.set('status', 'published')
    const result = await downloadCsv('/api/admin/products/export', query, 'products')

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/products/export?status=published', {
      headers: { Authorization: 'Bearer tok-123', 'Accept-Language': 'zh' },
    })
    expect(anchor.download).toBe('products-20260611.csv')
    expect(anchor.click).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
    expect(result).toEqual({ truncated: true })
  })

  it('空 query 不拼 "?"；缺 Content-Disposition 时兜底 {prefix}-{yyyyMMdd}.csv 且 truncated=false', async () => {
    fetchMock.mockResolvedValue(mockResponse())
    const result = await downloadCsv('/api/admin/orders/export', new URLSearchParams(), 'orders')

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/orders/export', {
      headers: { Authorization: 'Bearer tok-123', 'Accept-Language': 'zh' },
    })
    const yyyyMMdd = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    expect(anchor.download).toBe(`orders-${yyyyMMdd}.csv`)
    expect(result).toEqual({ truncated: false })
  })

  it('非 2xx 抛出后端 message；body 不可解析时回退通用文案', async () => {
    fetchMock.mockResolvedValueOnce(mockResponse({ ok: false, json: { message: '权限不足' } }))
    await expect(downloadCsv('/api/admin/orders/export', new URLSearchParams(), 'orders')).rejects.toThrow(
      '权限不足',
    )

    fetchMock.mockResolvedValueOnce(mockResponse({ ok: false }))
    await expect(downloadCsv('/api/admin/orders/export', new URLSearchParams(), 'orders')).rejects.toThrow(
      '导出失败，请稍后重试',
    )
    expect(anchor.click).not.toHaveBeenCalled()
  })

  it('无 token 时 Authorization 传空串（与原内联实现一致）', async () => {
    getToken.mockReturnValue(null)
    fetchMock.mockResolvedValue(mockResponse())
    await downloadCsv('/api/admin/orders/export', new URLSearchParams(), 'orders')
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/orders/export', {
      headers: { Authorization: '', 'Accept-Language': 'zh' },
    })
  })
})
