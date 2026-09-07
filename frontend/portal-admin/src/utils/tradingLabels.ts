// trading / shipping / tax 域枚举 → 中文标签 + 徽章色（order-flow-complete J；后台无 i18n，文案集中于此供列表/详情/配置页复用）
import {
  ExchangeRateSource,
  Incoterm,
  OrderActorType,
  OrderEventType,
  OrderStatus,
  ProductionStage,
  RefundStatus,
  ShipmentEventSource,
  ShipmentStatus,
  ShippingServiceLevel,
  TaxType,
} from '@/api/types'

export type BadgeTone = 'ok' | 'warn' | 'danger' | 'info' | 'neutral'

export interface EnumMeta {
  tone: BadgeTone
  label: string
}

export const ORDER_STATUS_META: Record<number, EnumMeta> = {
  [OrderStatus.PENDING]: { tone: 'warn', label: '待付款' },
  [OrderStatus.PAID]: { tone: 'info', label: '已付款/制作中' },
  [OrderStatus.SHIPPED]: { tone: 'info', label: '已发货' },
  [OrderStatus.DELIVERED]: { tone: 'ok', label: '已签收' },
  [OrderStatus.COMPLETED]: { tone: 'ok', label: '已完成' },
  [OrderStatus.CANCELLED]: { tone: 'neutral', label: '已取消' },
  [OrderStatus.REFUNDING]: { tone: 'danger', label: '退款中' },
  [OrderStatus.REFUNDED]: { tone: 'neutral', label: '已退款' },
}

export function orderStatusMeta(s?: number | null): EnumMeta {
  return (s != null && ORDER_STATUS_META[s]) || { tone: 'neutral', label: s == null ? '—' : String(s) }
}

export const PRODUCTION_STAGE_META: Record<number, EnumMeta> = {
  [ProductionStage.PENDING_REVIEW]: { tone: 'warn', label: '待审核' },
  [ProductionStage.IN_PRODUCTION]: { tone: 'info', label: '制作中' },
  [ProductionStage.QUALITY_CHECK]: { tone: 'info', label: '质检中' },
  [ProductionStage.READY_TO_SHIP]: { tone: 'ok', label: '待发货' },
}
export const PRODUCTION_STAGES: ProductionStage[] = [
  ProductionStage.PENDING_REVIEW,
  ProductionStage.IN_PRODUCTION,
  ProductionStage.QUALITY_CHECK,
  ProductionStage.READY_TO_SHIP,
]

export function productionStageMeta(s?: number | null): EnumMeta {
  return (s != null && PRODUCTION_STAGE_META[s]) || { tone: 'neutral', label: '—' }
}

export const SHIPMENT_STATUS_META: Record<number, EnumMeta> = {
  [ShipmentStatus.PENDING]: { tone: 'warn', label: '待揽收' },
  [ShipmentStatus.IN_TRANSIT]: { tone: 'info', label: '运输中' },
  [ShipmentStatus.OUT_FOR_DELIVERY]: { tone: 'info', label: '派送中' },
  [ShipmentStatus.DELIVERED]: { tone: 'ok', label: '已签收' },
  [ShipmentStatus.EXCEPTION]: { tone: 'danger', label: '异常' },
  [ShipmentStatus.CANCELLED]: { tone: 'neutral', label: '已作废' },
}

export function shipmentStatusMeta(s?: number | null): EnumMeta {
  return (s != null && SHIPMENT_STATUS_META[s]) || { tone: 'neutral', label: '—' }
}

/** 手工轨迹事件可选状态（作废走独立操作，不在此列） */
export const SHIPMENT_EVENT_STATUS_OPTIONS = [
  { value: ShipmentStatus.PENDING, label: '待揽收' },
  { value: ShipmentStatus.IN_TRANSIT, label: '运输中' },
  { value: ShipmentStatus.OUT_FOR_DELIVERY, label: '派送中' },
  { value: ShipmentStatus.DELIVERED, label: '已签收' },
  { value: ShipmentStatus.EXCEPTION, label: '异常' },
]

export const SHIPMENT_EVENT_SOURCE_LABEL: Record<number, string> = {
  [ShipmentEventSource.MANUAL]: '手工',
  [ShipmentEventSource.PROVIDER]: '供应商',
  [ShipmentEventSource.SYSTEM]: '系统',
}

export const ORDER_EVENT_TYPE_LABEL: Record<number, string> = {
  [OrderEventType.STATUS_CHANGED]: '状态变更',
  [OrderEventType.NOTE]: '备注',
  [OrderEventType.SHIPMENT]: '物流',
  [OrderEventType.PAYMENT]: '支付',
  [OrderEventType.REFUND]: '退款',
  [OrderEventType.EMAIL]: '邮件',
  [OrderEventType.PRODUCTION]: '制作',
}

export const ORDER_ACTOR_LABEL: Record<number, string> = {
  [OrderActorType.SYSTEM]: '系统',
  [OrderActorType.CUSTOMER]: '顾客',
  [OrderActorType.ADMIN]: '管理员',
}

export const REFUND_STATUS_META: Record<number, EnumMeta> = {
  [RefundStatus.PENDING]: { tone: 'warn', label: '待审批' },
  [RefundStatus.APPROVED]: { tone: 'ok', label: '已同意' },
  [RefundStatus.REJECTED]: { tone: 'danger', label: '已拒绝' },
}

export const TAX_TYPE_LABEL: Record<number, string> = {
  [TaxType.VAT]: 'VAT',
  [TaxType.GST]: 'GST',
  [TaxType.SALES_TAX]: '销售税',
  [TaxType.DUTY]: '关税',
}
export const TAX_TYPE_OPTIONS = [
  { value: TaxType.VAT, label: 'VAT（增值税）' },
  { value: TaxType.GST, label: 'GST（商品服务税）' },
  { value: TaxType.SALES_TAX, label: 'Sales Tax（销售税）' },
  { value: TaxType.DUTY, label: 'Duty（关税）' },
]

export const INCOTERM_LABEL: Record<number, string> = {
  [Incoterm.DDP]: 'DDP（税费已含）',
  [Incoterm.DDU]: 'DDU（到付关税）',
}
export const INCOTERM_OPTIONS = [
  { value: Incoterm.DDP, label: 'DDP（税费已含）' },
  { value: Incoterm.DDU, label: 'DDU（到付关税）' },
]

export const SERVICE_LEVEL_LABEL: Record<number, string> = {
  [ShippingServiceLevel.STANDARD]: '标准',
  [ShippingServiceLevel.EXPRESS]: '加急',
}
export const SERVICE_LEVEL_OPTIONS = [
  { value: ShippingServiceLevel.STANDARD, label: '标准（Standard）' },
  { value: ShippingServiceLevel.EXPRESS, label: '加急（Express）' },
]

export const EXCHANGE_RATE_SOURCE_LABEL: Record<number, string> = {
  [ExchangeRateSource.MANUAL]: '手工',
  [ExchangeRateSource.PROVIDER]: '供应商',
}

export const ZONE_LABEL: Record<string, string> = {
  NORTH_AMERICA: '北美',
  EUROPE: '欧洲',
  UK: '英国',
  OCEANIA: '大洋洲',
  ASIA: '亚洲',
  LATAM: '拉美',
  MEA: '中东非洲',
  REST: '其他地区',
}
export const ZONE_OPTIONS = Object.entries(ZONE_LABEL).map(([value, zh]) => ({ value, label: `${zh}（${value}）` }))

export function zoneLabel(zone?: string | null): string {
  if (!zone) return '—'
  return ZONE_LABEL[zone] ? `${ZONE_LABEL[zone]}（${zone}）` : zone
}

/** rate_scaled（10000 = 100%）→ 百分比文本 */
export function ratePercent(rateScaled?: number | null): string {
  if (rateScaled == null) return '—'
  return `${(rateScaled / 100).toFixed(2).replace(/\.?0+$/, '')}%`
}
