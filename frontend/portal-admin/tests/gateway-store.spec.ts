import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const createConfig = vi.fn()
const updateConfig = vi.fn()

vi.mock('@/api', () => ({
  gatewayApi: {
    createConfig: (...args: unknown[]) => createConfig(...args),
    updateConfig: (...args: unknown[]) => updateConfig(...args),
  },
}))

import { useGatewayStore } from '@/stores/gateway'
import { GatewayProtocol, GatewayType } from '@/api/types'

describe('useGatewayStore Gateway version contract', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('更新请求保留调用方回传的 version，并合并服务端新 version', async () => {
    const body = {
      gatewayType: GatewayType.AI,
      name: 'OpenRouter',
      protocol: GatewayProtocol.OPENAI,
      baseUrl: 'https://openrouter.ai/api/v1',
      apiKey: 'sk-a****1234',
      enabled: true,
      version: 7,
    }
    updateConfig.mockResolvedValue({ ...body, id: 1, apiKeyMasked: body.apiKey, modelList: [], consecutiveFailures: 0, version: 8 })

    const saved = await useGatewayStore().saveConfig(body, 1)

    expect(updateConfig).toHaveBeenCalledWith(1, expect.objectContaining({ version: 7 }))
    expect(saved.version).toBe(8)
  })

  it('创建请求允许省略 version', async () => {
    const body = {
      gatewayType: GatewayType.LOGISTICS,
      name: 'Logistics',
      protocol: GatewayProtocol.OPENAI,
      baseUrl: 'https://gateway.example.com',
      apiKey: 'plain-key',
      enabled: true,
    }
    createConfig.mockResolvedValue({ ...body, id: 2, apiKeyMasked: 'plai****-key', modelList: [], consecutiveFailures: 0, version: 0 })

    await useGatewayStore().saveConfig(body)

    expect(createConfig).toHaveBeenCalledWith(expect.not.objectContaining({ version: expect.anything() }))
  })
})
