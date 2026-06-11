// STORE-TRD-R01 useRefundsStore 单测：approve 不再随同提交退货单号（决策 7）/ reject 原因透传 / patchTracking 事后登记（决策 31）/ replaceRow 行内刷新
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const listRefunds = vi.fn()
const approveRefund = vi.fn()
const rejectRefund = vi.fn()
const patchRefund = vi.fn()

vi.mock('@/api', () => ({
  refundsApi: {
    listRefunds: (...args: unknown[]) => listRefunds(...args),
    approveRefund: (...args: unknown[]) => approveRefund(...args),
    rejectRefund: (...args: unknown[]) => rejectRefund(...args),
    patchRefund: (...args: unknown[]) => patchRefund(...args),
  },
}))

import { useRefundsStore } from '@/stores/refunds'
import type { AdminRefund } from '@/api/types'

const refund = (
  id: number,
  status: AdminRefund['status'] = 'pending',
  extra: Partial<AdminRefund> = {},
): AdminRefund => ({
  id,
  refundNo: `RF-${id}`,
  orderId: id,
  amount: 100,
  currency: 'USD',
  status,
  ...extra,
})

describe('useRefundsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('approve(id) 仅传工单 id，不再随同提交退货单号（STORE-TRD-R01 / ALIGN-024）', async () => {
    approveRefund.mockResolvedValue(refund(1, 'approved', { stripeRefundId: 're_abc123' }))
    const store = useRefundsStore()
    store.list = [refund(1)]
    const updated = await store.approve(1)
    expect(approveRefund).toHaveBeenCalledWith(1)
    expect(approveRefund.mock.calls[0]).toHaveLength(1) // 签名收窄：无第二个 returnTrackingNo 参数
    expect(updated.status).toBe('approved')
    // replaceRow 行内刷新：已同意行带回 stripe_refund_id（COMP-TRD-R01 / ALIGN-025 数据来源）
    expect(store.list[0]).toMatchObject({ id: 1, status: 'approved', stripeRefundId: 're_abc123' })
  })

  it('reject(id, reason) 原因透传 + 仅替换目标行（FORM-TRD-R01）', async () => {
    rejectRefund.mockResolvedValue(refund(2, 'rejected', { rejectReason: '不符合退款政策' }))
    const store = useRefundsStore()
    store.list = [refund(1), refund(2)]
    await store.reject(2, '不符合退款政策')
    expect(rejectRefund).toHaveBeenCalledWith(2, '不符合退款政策')
    expect(store.list[0]).toMatchObject({ id: 1, status: 'pending' })
    expect(store.list[1]).toMatchObject({ id: 2, status: 'rejected', rejectReason: '不符合退款政策' })
  })

  it('patchTracking 事后登记退货单号并行内刷新（决策 31 / ALIGN-025）', async () => {
    patchRefund.mockResolvedValue(refund(3, 'approved', { returnTrackingNo: 'SF1234567890' }))
    const store = useRefundsStore()
    store.list = [refund(3, 'approved')]
    await store.patchTracking(3, 'SF1234567890')
    expect(patchRefund).toHaveBeenCalledWith(3, 'SF1234567890')
    expect(store.list[0]).toMatchObject({ id: 3, returnTrackingNo: 'SF1234567890' })
  })
})
