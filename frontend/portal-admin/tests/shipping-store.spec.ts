// STORE-SHP-01 useShippingStore 单测：Toggle 乐观回滚 + enabledCount 预判数据源
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const toggleCarrierStatus = vi.fn()
const listCarriers = vi.fn()
const listRates = vi.fn()

vi.mock('@/api', () => ({
  shippingApi: {
    listCarriers: (...args: unknown[]) => listCarriers(...args),
    listRates: (...args: unknown[]) => listRates(...args),
    toggleCarrierStatus: (...args: unknown[]) => toggleCarrierStatus(...args),
    createCarrier: vi.fn(),
    updateCarrier: vi.fn(),
    deleteCarrier: vi.fn(),
    createRate: vi.fn(),
    updateRate: vi.fn(),
    deleteRate: vi.fn(),
  },
}))

import { useShippingStore } from '@/stores/shipping'
import { CarrierStatus } from '@/api/types'
import type { Carrier } from '@/api/types'

const carrier = (id: number, status: CarrierStatus = CarrierStatus.ENABLED): Carrier => ({
  id,
  name: `C${id}`,
  zones: 'US',
  leadTime: '3-5 日',
  status,
})

describe('useShippingStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchAll 并行拉取承运方与规则', async () => {
    listCarriers.mockResolvedValue({ items: [carrier(1)] })
    listRates.mockResolvedValue({ items: [{ id: 1, zone: 'US', feeUnder: 25, feeOver: 0, threshold: 300 }] })
    const store = useShippingStore()
    await store.fetchAll()
    expect(store.carriers).toHaveLength(1)
    expect(store.rates).toHaveLength(1)
  })

  it('enabledCount 仅统计 enabled（409902 预判数据源）', () => {
    const store = useShippingStore()
    store.carriers = [carrier(1, CarrierStatus.ENABLED), carrier(2, CarrierStatus.DISABLED), carrier(3, CarrierStatus.ENABLED)]
    expect(store.enabledCount).toBe(2)
  })

  it('toggleCarrier 失败回滚（FORM-SHP-04）', async () => {
    const store = useShippingStore()
    const c = carrier(1, CarrierStatus.ENABLED)
    store.carriers = [c]
    toggleCarrierStatus.mockRejectedValue(new Error('409902'))
    await expect(store.toggleCarrier(c, CarrierStatus.DISABLED)).rejects.toThrow()
    expect(c.status).toBe(CarrierStatus.ENABLED)
  })

  it('toggleCarrier 同态幂等', async () => {
    const store = useShippingStore()
    const c = carrier(1, CarrierStatus.ENABLED)
    await store.toggleCarrier(c, CarrierStatus.ENABLED)
    expect(toggleCarrierStatus).not.toHaveBeenCalled()
  })
})
