// STORE-SHP-01 useShippingStore 单测：Toggle 乐观回滚 + enabledCount 预判数据源
// order-flow-complete D：rates → options（listShippingOptions）；运费选项启用开关乐观回滚；国家列表缓存
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const toggleCarrierStatus = vi.fn()
const listCarriers = vi.fn()
const listShippingOptions = vi.fn()
const setShippingOptionEnabled = vi.fn()
const listCountries = vi.fn()

vi.mock('@/api', () => ({
  shippingApi: {
    listCarriers: (...args: unknown[]) => listCarriers(...args),
    listShippingOptions: (...args: unknown[]) => listShippingOptions(...args),
    listCountries: (...args: unknown[]) => listCountries(...args),
    toggleCarrierStatus: (...args: unknown[]) => toggleCarrierStatus(...args),
    setShippingOptionEnabled: (...args: unknown[]) => setShippingOptionEnabled(...args),
    createCarrier: vi.fn(),
    updateCarrier: vi.fn(),
    deleteCarrier: vi.fn(),
    createShippingOption: vi.fn(),
    updateShippingOption: vi.fn(),
    deleteShippingOption: vi.fn(),
    previewShippingQuote: vi.fn(),
  },
}))

import { useShippingStore } from '@/stores/shipping'
import { CarrierStatus, ShippingServiceLevel } from '@/api/types'
import type { Carrier, ShippingOption } from '@/api/types'

const option = (id: number, enabled = true): ShippingOption => ({
  id,
  zone: 'NORTH_AMERICA',
  carrierCode: 'FEDEX',
  carrierName: 'FedEx',
  serviceLevel: ShippingServiceLevel.STANDARD,
  feeUnder: 25,
  feeOver: 0,
  threshold: 300,
  transitDaysMin: 5,
  transitDaysMax: 8,
  enabled,
})

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

  it('fetchAll 并行拉取承运方与运费选项', async () => {
    listCarriers.mockResolvedValue({ items: [carrier(1)] })
    listShippingOptions.mockResolvedValue({ items: [option(1)] })
    const store = useShippingStore()
    await store.fetchAll()
    expect(store.carriers).toHaveLength(1)
    expect(store.options).toHaveLength(1)
  })

  it('toggleOption 失败回滚 / 同态幂等', async () => {
    const store = useShippingStore()
    const o = option(1, true)
    store.options = [o]
    setShippingOptionEnabled.mockRejectedValue(new Error('404903'))
    await expect(store.toggleOption(o, false)).rejects.toThrow()
    expect(o.enabled).toBe(true)
    await store.toggleOption(o, true)
    expect(setShippingOptionEnabled).toHaveBeenCalledTimes(1)
  })

  it('ensureCountries 仅请求一次（多页复用缓存）', async () => {
    listCountries.mockResolvedValue({ items: [{ code: 'US', name: 'United States', zone: 'NORTH_AMERICA', supported: true, regions: [] }] })
    const store = useShippingStore()
    await store.ensureCountries()
    await store.ensureCountries()
    expect(listCountries).toHaveBeenCalledTimes(1)
    expect(store.countries[0]!.code).toBe('US')
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
