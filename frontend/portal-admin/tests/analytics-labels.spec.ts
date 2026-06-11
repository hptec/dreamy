// TC-ALIGN-017 [UT][P1] 来源/漏斗标签中文化（COMP-ANA-A01，admin-prototype-alignment）
// 组件级渲染断言（流量/漏斗 Tab 实际 DOM）标 deferred-to-L3-test，由 E2E/组件测试阶段覆盖
import { describe, expect, it } from 'vitest'
import { SOURCE_LABEL, STAGE_LABEL, sourceLabel, stageLabel } from '@/utils/analyticsLabels'

describe('TC-ALIGN-017 访客来源标签中文化（SOURCE_LABEL）', () => {
  it('organic → 自然搜索', () => {
    expect(sourceLabel('organic')).toBe('自然搜索')
  })

  it('设计 §B.1 列举的七个来源逐字映射', () => {
    expect(sourceLabel('instagram')).toBe('Instagram')
    expect(sourceLabel('pinterest')).toBe('Pinterest')
    expect(sourceLabel('direct')).toBe('直接访问')
    expect(sourceLabel('email')).toBe('邮件')
    expect(sourceLabel('referral')).toBe('外链引荐')
    expect(sourceLabel('paid')).toBe('付费广告')
  })

  it('后端 Ga4Normalizer social 桶保留中文映射（契约 key 为映射键）', () => {
    expect(sourceLabel('social')).toBe('社交')
  })

  it('未知来源 unknown_x 回退原始 key 不报错', () => {
    expect(sourceLabel('unknown_x')).toBe('unknown_x')
    expect(SOURCE_LABEL['unknown_x']).toBeUndefined()
  })
})

describe('TC-ALIGN-017 漏斗阶段标签中文化（STAGE_LABEL）', () => {
  it('原型四阶段文案逐字（mock.js L61-66）', () => {
    // 契约 stage key 为映射键、中文文案不变（设计 §B.1）
    expect(stageLabel('page_view')).toBe('商品浏览')
    expect(stageLabel('add_to_cart')).toBe('加入购物车')
    expect(stageLabel('begin_checkout')).toBe('进入结算')
    expect(stageLabel('purchase')).toBe('完成支付')
  })

  it('契约五阶段全覆盖（analytics-api enum）', () => {
    expect(Object.keys(STAGE_LABEL)).toEqual(['page_view', 'view_item', 'add_to_cart', 'begin_checkout', 'purchase'])
    expect(stageLabel('view_item')).toBe('商品详情')
  })

  it('未知 stage 回退原始 key 不报错', () => {
    expect(stageLabel('unknown_stage')).toBe('unknown_stage')
  })
})
