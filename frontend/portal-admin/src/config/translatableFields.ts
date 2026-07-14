// 应翻译字段元数据：声明每个实体的哪些字段属于"应翻译"（数据级 i18n）
// 用于 LocaleTabs/LocaleFlag 计算 missing/disabled 状态
//
// 约定：
// - 'translate'：业务数据翻译，前端提供 ES/FR 输入框，未填显示 missing 灰国旗
// - 'system'：系统级 i18n 范围（枚举名/固定文案），由后端 MessageResolver 处理，前端不输入
// - 'none'：不涉及翻译（如数字/URL/ID）
//
// 当前 LocaleTabs 仅消费 locale 级 state（filled/missing/disabled），字段级策略供未来扩展使用。

export type FieldI18nPolicy = 'translate' | 'system' | 'none'

export const fieldI18nPolicy: Record<string, Record<string, FieldI18nPolicy>> = {
  product: {
    name: 'translate',
    description: 'translate',
    sellingPoints: 'translate',
    designerNote: 'translate',
    seoTitle: 'translate',
    seoDescription: 'translate',
    fabricCareNote: 'translate',
    status: 'system',
    slug: 'none',
    styleNo: 'none',
  },
  category: {
    name: 'translate',
    description: 'translate',
  },
  attribute_def: {
    name: 'translate',
    description: 'translate',
  },
  collection_group: {
    name: 'translate',
    description: 'translate',
  },
  collection: {
    name: 'translate',
  },
  banner: {
    title: 'translate',
    subtitle: 'translate',
    ctaText: 'translate',
  },
  blog_post: {
    title: 'translate',
    excerpt: 'translate',
    content: 'translate',
  },
  coupon: {
    name: 'translate',
    description: 'translate',
  },
  flash_sale: {
    name: 'translate',
    description: 'translate',
  },
  guide: {
    title: 'translate',
    content: 'translate',
  },
  lookbook: {
    title: 'translate',
    description: 'translate',
  },
  real_wedding: {
    title: 'translate',
    story: 'translate',
  },
}

/** 默认策略：未声明的字段视为 'translate'（保持向后兼容） */
export function resolveFieldPolicy(entity: string, field: string): FieldI18nPolicy {
  return fieldI18nPolicy[entity]?.[field] ?? 'translate'
}
