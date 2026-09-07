// STORE-TRD-A01 useOrdersStore：后台订单列表分页/筛选 + 详情 + 发货/状态/代客退款（写成功后 fetchDetail 重载）
// order-flow-complete J：制作阶段 / 备注 / 包裹操作 / 列表新筛选（制作阶段、婚期早于、有无包裹）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ordersApi } from '@/api'
import type {
  AdminOrderDetail,
  AdminOrderListItem,
  OrderStatus,
  ProductionStage,
  ShipmentCreateRequest,
  ShipmentEventCreateRequest,
} from '@/api/types'
import { dateToEndOfDay, dateToStartOfDay, normalizeEnumFilter, normalizeFilter } from '@/utils/validators'

export const useOrdersStore = defineStore('orders', () => {
  const list = ref<AdminOrderListItem[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)

  const status = ref<OrderStatus | 'all'>('all')
  const search = ref('')
  const currency = ref('all')
  const from = ref('')
  const to = ref('')
  const productionStage = ref<ProductionStage | 'all'>('all')
  const weddingBefore = ref('')
  /** 'all' | 'yes' | 'no' */
  const hasShipment = ref<'all' | 'yes' | 'no'>('all')

  const detail = ref<AdminOrderDetail | null>(null)
  const detailLoading = ref(false)

  function hasShipmentParam(): boolean | undefined {
    if (hasShipment.value === 'yes') return true
    if (hasShipment.value === 'no') return false
    return undefined
  }

  async function fetchList() {
    loading.value = true
    try {
      const res = await ordersApi.listOrders({
        page: page.value,
        pageSize: pageSize.value,
        status: normalizeEnumFilter(status.value),
        search: search.value.trim() || undefined,
        currency: normalizeFilter(currency.value),
        from: dateToStartOfDay(from.value),
        to: dateToEndOfDay(to.value),
        productionStage: normalizeEnumFilter(productionStage.value),
        weddingBefore: weddingBefore.value || undefined,
        hasShipment: hasShipmentParam(),
      })
      list.value = res.data
      total.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  function setPage(p: number) {
    page.value = p
    return fetchList()
  }

  function applyFilters() {
    page.value = 1
    return fetchList()
  }

  const exporting = ref(false)

  /**
   * FORM-TRD-O01（ALIGN-012）：导出当前筛选订单 CSV。
   * 筛选参数与 fetchList 一致（导出端点未扩展新筛选，仅传基础项）；exporting 防重复提交；返回截断标记供视图 toast.warn。
   */
  async function exportList(): Promise<{ truncated: boolean }> {
    if (exporting.value) return { truncated: false }
    exporting.value = true
    try {
      return await ordersApi.exportOrders({
        status: normalizeEnumFilter(status.value),
        search: search.value.trim() || undefined,
        currency: normalizeFilter(currency.value),
        from: dateToStartOfDay(from.value),
        to: dateToEndOfDay(to.value),
      })
    } finally {
      exporting.value = false
    }
  }

  async function fetchDetail(id: number) {
    detailLoading.value = true
    try {
      detail.value = await ordersApi.getOrder(id)
      return detail.value
    } finally {
      detailLoading.value = false
    }
  }

  /** 静默刷新（不闪 loading，供包裹/备注等局部写操作后同步详情） */
  async function refreshDetail(id: number) {
    detail.value = await ordersApi.getOrder(id)
    return detail.value
  }

  /** FORM-TRD-A01：发货（兼容别名；成功后详情重载） */
  async function ship(id: number, carrier: string, trackingNo: string) {
    detail.value = await ordersApi.shipOrder(id, { carrier, trackingNo })
    return detail.value
  }

  /** COMP-TRD-A05：确认完成 / 取消订单 / 标记送达 */
  async function patchStatus(id: number, statusValue: OrderStatus) {
    detail.value = await ordersApi.patchOrderStatus(id, statusValue)
    return detail.value
  }

  async function patchProductionStage(id: number, stage: ProductionStage) {
    detail.value = await ordersApi.patchProductionStage(id, stage)
    return detail.value
  }

  async function addNote(id: number, content: string, customerVisible: boolean) {
    const event = await ordersApi.addOrderNote(id, { content, customerVisible })
    await refreshDetail(id)
    return event
  }

  /** FORM-TRD-A02：代客退款（成功后详情重载带出工单） */
  async function createRefund(id: number, amount: number | string, reason: string) {
    const refund = await ordersApi.createRefund(id, { amount, reason })
    await refreshDetail(id)
    return refund
  }

  // ---- 包裹 ----
  async function createShipment(id: number, body: ShipmentCreateRequest) {
    const shipment = await ordersApi.createShipment(id, body)
    await refreshDetail(id)
    return shipment
  }

  async function patchShipment(orderId: number, shipmentId: number, body: { carrierCode?: string; trackingNo?: string }) {
    const shipment = await ordersApi.patchShipment(shipmentId, body)
    await refreshDetail(orderId)
    return shipment
  }

  async function addShipmentEvent(orderId: number, shipmentId: number, body: ShipmentEventCreateRequest) {
    const shipment = await ordersApi.addShipmentEvent(shipmentId, body)
    await refreshDetail(orderId)
    return shipment
  }

  async function deliverShipment(orderId: number, shipmentId: number) {
    const shipment = await ordersApi.deliverShipment(shipmentId)
    await refreshDetail(orderId)
    return shipment
  }

  async function cancelShipment(orderId: number, shipmentId: number) {
    const shipment = await ordersApi.cancelShipment(shipmentId)
    await refreshDetail(orderId)
    return shipment
  }

  /** 返回 null = provider stub 无操作（204） */
  async function syncShipment(orderId: number, shipmentId: number) {
    const shipment = await ordersApi.syncShipment(shipmentId)
    if (shipment) await refreshDetail(orderId)
    return shipment
  }

  return {
    list,
    total,
    page,
    pageSize,
    loading,
    status,
    search,
    currency,
    from,
    to,
    productionStage,
    weddingBefore,
    hasShipment,
    detail,
    detailLoading,
    exporting,
    fetchList,
    setPage,
    applyFilters,
    exportList,
    fetchDetail,
    refreshDetail,
    ship,
    patchStatus,
    patchProductionStage,
    addNote,
    createRefund,
    createShipment,
    patchShipment,
    addShipmentEvent,
    deliverShipment,
    cancelShipment,
    syncShipment,
  }
})
