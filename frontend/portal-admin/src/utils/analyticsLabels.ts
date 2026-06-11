// COMP-ANA-A01（ALIGN-017）：Analytics 流量来源 / 漏斗阶段中文标签映射
// 设计来源：hhspec/changes/admin-prototype-alignment/specs/design/admin-prototype-alignment/dashboard-analytics-frontend-detail.md §B.1
// 映射键以 analytics-api 契约为准（Ga4Normalizer 来源桶 / funnel stage 枚举），中文文案对照原型 mock.js 逐字

/** 访客来源中文映射（键 = 后端来源桶 + 设计列举的 GA4 原始来源；未知来源回退原始值） */
export const SOURCE_LABEL: Record<string, string> = {
  organic: '自然搜索',
  instagram: 'Instagram',
  pinterest: 'Pinterest',
  direct: '直接访问',
  email: '邮件',
  referral: '外链引荐',
  paid: '付费广告',
  social: '社交', // 后端 Ga4Normalizer BUCKET_SOCIAL（契约 key 为映射键）
}

/** 漏斗阶段中文映射（键 = analytics-api 契约 stage 枚举五阶段；原型四阶段文案逐字） */
export const STAGE_LABEL: Record<string, string> = {
  page_view: '商品浏览',
  view_item: '商品详情',
  add_to_cart: '加入购物车',
  begin_checkout: '进入结算',
  purchase: '完成支付',
}

/** 来源标签：未知 key 回退原始值，不报错（ALIGN-017） */
export function sourceLabel(source: string): string {
  return SOURCE_LABEL[source] ?? source
}

/** 漏斗阶段标签：未知 key 回退原始值，不报错（ALIGN-017） */
export function stageLabel(stage: string): string {
  return STAGE_LABEL[stage] ?? stage
}
