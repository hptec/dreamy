// TC-ALIGN-032 [UT][P1] 邮费表语义可视化（DEC-SHP-3：feeOver=0 → 「免邮」text-ok；threshold=null → '—'）
// L0 TRACE: ui-checklist shipping（ALIGN-032）；L2 TRACE: COMP-SHP-03 / DEC-SHP-3（Shipping.vue feeOverText/thresholdText）
// 实现说明：feeOverText/thresholdText 定义于 <script setup>（不可独立导入）；工程禁止引入 @vue/test-utils，
// 故采用 vue 自带 server-renderer 做 SSR 字符串渲染断言（零新依赖，node 环境可运行；onMounted 在 SSR 中不执行，
// 因此通过预置 pinia store 状态绕开网络请求）。
import { describe, expect, it, vi } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api', () => ({
  shippingApi: {
    listCarriers: vi.fn(),
    listRates: vi.fn(),
    toggleCarrierStatus: vi.fn(),
    createCarrier: vi.fn(),
    updateCarrier: vi.fn(),
    deleteCarrier: vi.fn(),
    createRate: vi.fn(),
    updateRate: vi.fn(),
    deleteRate: vi.fn(),
  },
}))

// node 测试环境无 window（toast 内部 window.setTimeout）——mock 掉提示层（与既有 spec 同口径）
vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ success: vi.fn(), error: vi.fn(), info: vi.fn(), warn: vi.fn() }),
}))

import Shipping from '@/views/Shipping.vue'
import { useShippingStore } from '@/stores/shipping'
import type { ShippingRate } from '@/api/types'

async function renderShipping(rates: ShippingRate[]): Promise<string> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useShippingStore()
  store.carriers = [{ id: 1, name: 'DHL Express', zones: 'US', leadTime: '3-5 日', status: 'enabled' }]
  store.rates = rates
  const app = createSSRApp(Shipping)
  app.use(pinia)
  return renderToString(app)
}

describe('TC-ALIGN-032 邮费表语义可视化（SSR 渲染断言）', () => {
  it('feeOver=0 → 渲染「免邮」且满额包邮单元格 class 含 text-ok', async () => {
    const html = await renderShipping([{ id: 1, zone: '美国境内', feeUnder: 8, feeOver: 0, threshold: 200 }])
    expect(html).toContain('免邮')
    // :class 与静态 class 合并（SSR 实际序为 "text-ok text-right"）——断言不依赖类名顺序
    expect(html).toMatch(/<td class="[^"]*text-ok[^"]*">[^<]*免邮/)
    expect(html).toContain('$200') // threshold 有值 → $金额
  })

  it('threshold=null → 渲染「—」；feeOver 非 0 → 金额格式 $xx.xx + text-ink-soft（非 text-ok）', async () => {
    const html = await renderShipping([{ id: 2, zone: 'Rest of World', feeUnder: 28, feeOver: 12, threshold: null }])
    expect(html).toContain('$12.00')
    expect(html).not.toContain('免邮')
    expect(html).toMatch(/<td class="[^"]*text-ink-soft[^"]*">[^<]*\$12\.00/)
    expect(html).not.toMatch(/<td class="[^"]*text-ok[^"]*">/) // 非 0 不得命中 text-ok
    expect(html).toMatch(/<td class="[^"]*text-ink-soft[^"]*">[^<]*—/) // threshold null → '—'
  })

  it('feeOver=null → 满额包邮列同样回退「—」（money() null 分支）', async () => {
    const html = await renderShipping([{ id: 3, zone: '加拿大', feeUnder: 18, feeOver: null, threshold: null }])
    expect(html).not.toContain('免邮')
    expect(html).toMatch(/<td class="[^"]*text-ink-soft[^"]*">[^<]*—/)
  })
})
