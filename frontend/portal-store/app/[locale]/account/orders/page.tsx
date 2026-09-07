'use client'

/**
 * 我的订单列表（COMP-TRD-S05，layout-keep + data-swap）：
 * filter chips 改绑 API status 枚举全集（原型四 chip 语义映射扩全）；
 * 卡片结构不变（首图=firstLineImg+lineCount、金额、状态徽章、Details 链接）；服务端分页加载更多。
 */

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import type { StoreOrderListItem } from '@/lib/api/store-types'
import { OrderStatus } from '@/lib/api/store-types'
import { listStoreOrders } from '@/lib/api/trading-api'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ApiError } from '@/lib/api/client'
import { statusBadgeClass, orderStatusLabel } from '@/lib/order-ui'
import { formatAmount, formatDateTimeLong, cn } from '@/lib/utils'

export default function OrdersPage() {
  const { t, te } = useI18n()
  /** 状态筛选 chips（value=undefined 表示「全部」——请求不传 status 参数；其余为 IntEnum 数值） */
  const filters: { label: string; value: OrderStatus | undefined }[] = [
    { label: t.orders.all, value: undefined },
    { label: t.orders.status.pending, value: OrderStatus.PENDING },
    { label: t.orders.status.paid, value: OrderStatus.PAID },
    { label: t.orders.status.shipped, value: OrderStatus.SHIPPED },
    { label: t.orders.status.completed, value: OrderStatus.COMPLETED },
    { label: t.orders.status.cancelled, value: OrderStatus.CANCELLED },
    { label: t.orders.status.refunding, value: OrderStatus.REFUNDING },
    { label: t.orders.status.refunded, value: OrderStatus.REFUNDED }
  ]
  /** order-ui 标签本地化映射（key=IntEnum 数值） */
  const statusLabels = Object.fromEntries(
    (Object.keys(t.orders.status) as (keyof typeof t.orders.status)[]).map((k) => [OrderStatus[k.toUpperCase() as keyof typeof OrderStatus], t.orders.status[k]])
  ) as Record<OrderStatus, string>
  const [filter, setFilter] = useState<OrderStatus | undefined>(undefined)
  const [orders, setOrders] = useState<StoreOrderListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (status: OrderStatus | undefined, pageNum: number, append: boolean) => {
    setLoading(true)
    setError(null)
    try {
      const res = await listStoreOrders({ status, page: pageNum, pageSize: 10 })
      setOrders((prev) => (append ? [...prev, ...res.data] : res.data))
      setTotal(res.totalElements)
      setPage(res.pageNumber)
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setLoading(false)
    }
  }, [te])

  useEffect(() => {
    void load(filter, 1, false)
  }, [filter, load])

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">{t.orders.title}</h1>
      <div className="mt-6 flex flex-wrap gap-2">
        {filters.map((f) => (
          <button key={f.label} onClick={() => setFilter(f.value)} className={cn('cursor-pointer rounded-full px-4 py-1.5 text-xs uppercase tracking-luxe transition-colors', filter === f.value ? 'bg-ink text-canvas' : 'border border-line text-ink-soft hover:border-ink')}>{f.label}</button>
        ))}
      </div>

      <div className="mt-6 space-y-4">
        {error && (
          <div className="py-10 text-center">
            <p className="text-sm text-blush">{error}</p>
            <button onClick={() => void load(filter, 1, false)} className="btn-outline mt-4">{t.common.retry}</button>
          </div>
        )}
        {!error && loading && orders.length === 0 && (
          <div className="space-y-4" aria-hidden="true">
            {[0, 1, 2].map((i) => <div key={i} className="h-32 animate-pulse rounded-sm bg-muted" />)}
          </div>
        )}
        {!error && !loading && orders.length === 0 ? (
          <p className="py-16 text-center text-ink-soft">{filter === undefined ? t.orders.none : t.orders.noneFiltered.replace('{status}', orderStatusLabel(filter, statusLabels))}</p>
        ) : (
          orders.map((o) => (
            <div key={o.id} className="rounded-sm border border-line bg-surface p-5">
              <div className="flex items-center justify-between border-b border-line/60 pb-3">
                <div>
                  <p className="text-sm font-medium">{t.orders.orderNo.replace('{no}', o.orderNo)}</p>
                  <p className="text-xs text-ink-soft">{t.orders.placed.replace('{date}', formatDateTimeLong(o.createdAt))}</p>
                </div>
                <span className={cn('rounded-full px-3 py-1 text-xs capitalize', statusBadgeClass(o.status))}>{orderStatusLabel(o.status, statusLabels)}</span>
              </div>
              <div className="mt-3 flex items-center gap-4">
                <div className="flex -space-x-3">
                  {o.firstLineImg ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={o.firstLineImg} alt="" className="h-16 w-12 rounded-sm border-2 border-surface object-cover" />
                  ) : (
                    <div className="h-16 w-12 rounded-sm border-2 border-surface bg-muted" />
                  )}
                </div>
                <div className="flex-1 text-sm text-ink-soft">{t.orders.itemsCount.replace('{count}', String(o.lineCount ?? 1))}</div>
                <span className="font-medium">{formatAmount(o.totalAmount, o.currency)}</span>
                <Link href={`/account/orders/${o.id}`} className="flex items-center gap-1 text-sm text-gold-deep underline">{t.orders.details} <ChevronRight className="h-3.5 w-3.5" /></Link>
              </div>
            </div>
          ))
        )}
        {!error && orders.length < total && (
          <div className="pt-2 text-center">
            <button onClick={() => void load(filter, page + 1, true)} disabled={loading} className="btn-outline disabled:opacity-60">
              {loading ? t.common.loading : t.collection.loadMore}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
