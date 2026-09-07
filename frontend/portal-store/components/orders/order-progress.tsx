'use client'

/**
 * 订单进度共用组件（order-flow-complete I，订单详情 + 游客查单页共用；沿用既有 timeline/badge token）：
 * - <OrderTimeline>：6 步主时间线 Placed → Paid → In production → Shipped → Delivered → Completed。
 * - <ProductionStages>：PAID 态制作阶段 4 小步（待审核 → 制作中 → 质检中 → 待发货）。
 * - <ShipmentCard>：包裹卡（承运商/单号/tracking_url/状态徽章/行明细/轨迹倒序）。
 * - <OrderActivity>：customer_visible 事件列表（倒序）。
 */

import { Check, Truck, ExternalLink, Package, CreditCard, Scissors, RotateCcw, MessageSquare, Mail, CircleDot } from 'lucide-react'
import type { OrderEvent, OrderStatus, ProductionStage, Shipment } from '@/lib/api/store-types'
import { OrderEventType, ProductionStage as Stage, ShipmentStatus, ShippingServiceLevel } from '@/lib/api/store-types'
import { useI18n } from '@/lib/i18n/i18n-context'
import { enumLabels, ORDER_TIMELINE_STEPS, orderTimelineDates, PRODUCTION_STAGES, productionStageLabel, shipmentStatusBadgeClass, shipmentStatusLabel } from '@/lib/order-ui'
import { cn, formatDateTimeLong } from '@/lib/utils'

/** ISO datetime → 'Sep 19, 2026 14:05'（轨迹/活动需要到分钟） */
function formatDateTimeMinute(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function OrderTimeline({
  order,
  carrierLine
}: {
  order: {
    status: OrderStatus
    productionStage?: ProductionStage | null
    createdAt: string
    paidAt?: string | null
    shippedAt?: string | null
    deliveredAt?: string | null
    completedAt?: string | null
    shippingServiceLevel?: ShippingServiceLevel | null
  }
  /** 顶部承运商摘要行（可选，如 "UPS · 1Z..."） */
  carrierLine?: string | null
}) {
  const { t } = useI18n()
  const dates = orderTimelineDates(order)
  const labels: Record<(typeof ORDER_TIMELINE_STEPS)[number], string> = {
    placed: t.orders.detail.timelinePlaced,
    paid: t.orders.detail.timelinePaid,
    production: t.orders.detail.timelineProduction,
    shipped: t.orders.detail.timelineShipped,
    delivered: t.orders.detail.timelineDelivered,
    completed: t.orders.detail.timelineCompleted
  }
  // 到达步数 = 连续已到达的前缀长度（避免中间缺日期造成断裂）
  let doneCount = 0
  for (const step of ORDER_TIMELINE_STEPS) {
    if (dates[step]) doneCount += 1
    else break
  }
  return (
    <div className="rounded-sm border border-line bg-surface p-6" data-testid="order-timeline" data-done={doneCount}>
      {carrierLine && (
        <div className="mb-5 flex items-center gap-2">
          <Truck className="h-5 w-5 text-gold" />
          <p className="text-sm font-medium">{carrierLine}</p>
        </div>
      )}
      <div className="relative flex justify-between">
        <div className="absolute left-0 right-0 top-3 h-0.5 bg-line" />
        <div className="absolute left-0 top-3 h-0.5 bg-gold transition-all" style={{ width: `${(Math.max(0, doneCount - 1) / (ORDER_TIMELINE_STEPS.length - 1)) * 100}%` }} />
        {ORDER_TIMELINE_STEPS.map((step, i) => {
          const date = dates[step]
          const done = i < doneCount
          const current = i === doneCount - 1
          return (
            <div key={step} className="relative z-10 flex flex-1 flex-col items-center text-center" data-testid={`timeline-step-${step}`} data-done={done}>
              <div className={cn('flex h-6 w-6 items-center justify-center rounded-full border-2 bg-surface', done ? 'border-gold' : 'border-line', current && 'ring-4 ring-gold/15')}>
                {done && <Check className="h-3 w-3 text-gold" />}
              </div>
              <p className={cn('mt-2 text-[11px] leading-tight', done ? 'font-medium text-ink' : 'text-ink-faint')}>{labels[step]}</p>
              {done && date && <p className="text-[10px] text-ink-faint">{formatDateTimeLong(date)}</p>}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export function ProductionStages({ stage }: { stage: ProductionStage }) {
  const { t } = useI18n()
  const stageLabels = enumLabels(Stage, t.orders.productionStage)
  const idx = PRODUCTION_STAGES.indexOf(stage)
  return (
    <div className="rounded-sm border border-gold/30 bg-gold/5 p-5" data-testid="production-stages" data-stage={stage}>
      <p className="flex items-center gap-2 text-sm font-medium"><Scissors className="h-4 w-4 text-gold" /> {t.orders.detail.productionTitle}</p>
      <p className="mt-1 text-xs text-ink-soft">{t.orders.detail.productionBody}</p>
      <ol className="mt-4 grid grid-cols-4 gap-2">
        {PRODUCTION_STAGES.map((s, i) => {
          const done = i <= idx
          const current = i === idx
          return (
            <li key={s} className="flex flex-col items-center text-center">
              <span className={cn('flex h-5 w-5 items-center justify-center rounded-full border text-[10px]', done ? 'border-gold bg-gold text-white' : 'border-line text-ink-faint', current && 'ring-4 ring-gold/20')}>
                {done && !current ? <Check className="h-3 w-3" /> : i + 1}
              </span>
              <span className={cn('mt-1.5 text-[11px] leading-tight', done ? 'font-medium text-ink' : 'text-ink-faint')}>{productionStageLabel(s, stageLabels)}</span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

export function ShipmentCard({ shipment }: { shipment: Shipment }) {
  const { t } = useI18n()
  const statusLabels = enumLabels(ShipmentStatus, t.orders.shipmentStatus)
  const events = [...(shipment.events ?? [])].sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
  return (
    <div className="rounded-sm border border-line bg-surface p-5" data-testid="shipment-card" data-shipment-status={shipment.status}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-medium"><Package className="h-4 w-4 text-gold" /> {t.orders.detail.shipmentNo.replace('{no}', shipment.shipmentNo)}</p>
          <p className="mt-1 text-sm text-ink-soft">{shipment.carrierName}{shipment.trackingNo ? ` · ${shipment.trackingNo}` : ''}</p>
          {shipment.trackingUrl && (
            <a href={shipment.trackingUrl} target="_blank" rel="noopener noreferrer" className="mt-1 inline-flex items-center gap-1 text-xs text-gold-deep underline">
              {t.orders.detail.trackPackage} <ExternalLink className="h-3 w-3" />
            </a>
          )}
        </div>
        <span className={cn('rounded-full px-3 py-1 text-xs', shipmentStatusBadgeClass(shipment.status))}>{shipmentStatusLabel(shipment.status, statusLabels)}</span>
      </div>

      {(shipment.lines ?? []).length > 0 && (
        <div className="mt-4">
          <p className="eyebrow mb-1.5">{t.orders.detail.contents}</p>
          <ul className="space-y-1 text-sm text-ink-soft">
            {shipment.lines.map((l) => (
              <li key={l.orderLineId} className="flex justify-between gap-3">
                <span>{l.productName}{[l.color, l.size].filter(Boolean).length ? ` · ${[l.color, l.size].filter(Boolean).join(' / ')}` : ''}</span>
                <span className="shrink-0">× {l.qty}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-4">
        <p className="eyebrow mb-1.5">{t.orders.detail.trackingHistory}</p>
        {events.length === 0 ? (
          <p className="text-xs text-ink-faint">{t.orders.detail.noTrackingEvents}</p>
        ) : (
          <ol className="relative space-y-3 border-l border-line pl-4">
            {events.map((e, i) => (
              <li key={e.id} className="relative text-sm">
                <span className={cn('absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full border-2 bg-surface', i === 0 ? 'border-gold' : 'border-line')} />
                <p className={cn(i === 0 ? 'font-medium text-ink' : 'text-ink-soft')}>{shipmentStatusLabel(e.status, statusLabels)}{e.description ? ` — ${e.description}` : ''}</p>
                <p className="text-[11px] text-ink-faint">{formatDateTimeMinute(e.occurredAt)}{e.location ? ` · ${e.location}` : ''}</p>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  )
}

function eventIcon(type: OrderEvent['type']) {
  switch (type) {
    case OrderEventType.PAYMENT:
      return CreditCard
    case OrderEventType.SHIPMENT:
      return Truck
    case OrderEventType.PRODUCTION:
      return Scissors
    case OrderEventType.REFUND:
      return RotateCcw
    case OrderEventType.NOTE:
      return MessageSquare
    case OrderEventType.EMAIL:
      return Mail
    default:
      return CircleDot
  }
}

export function OrderActivity({ events }: { events: OrderEvent[] }) {
  const { t } = useI18n()
  const visible = events.filter((e) => e.customerVisible !== false).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  if (visible.length === 0) return null
  return (
    <div className="rounded-sm border border-line bg-surface p-5" data-testid="order-activity">
      <h2 className="font-display text-xl font-medium">{t.orders.detail.activity}</h2>
      <ol className="mt-4 space-y-3">
        {visible.map((e) => {
          const Icon = eventIcon(e.type)
          return (
            <li key={e.id} className="flex items-start gap-3 text-sm">
              <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted"><Icon className="h-3.5 w-3.5 text-gold-deep" /></span>
              <div className="min-w-0 flex-1">
                <p className="font-medium text-ink">{e.title}</p>
                {e.detail && <p className="text-xs text-ink-soft">{e.detail}</p>}
              </div>
              <span className="shrink-0 text-[11px] text-ink-faint">{formatDateTimeMinute(e.createdAt)}</span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
