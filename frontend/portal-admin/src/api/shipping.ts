// shipping 域 API（PAGE-SHP-01；E-SHP-01~09）
// order-flow-complete D：承运商 code/tracking_url_template；运费选项 /shipping/options 替代 /shipping/rates（旧接口只读 410901）；
// 运费试算 quote-preview；ISO 国家列表（公开接口）
import { get, post, put, patch, del } from './client'
import type {
  Carrier,
  CarrierStatus,
  CarrierUpsert,
  Country,
  ShippingOption,
  ShippingOptionUpsert,
  ShippingQuotePreviewRequest,
  ShippingQuotePreviewResponse,
  ShippingRate,
} from './types'

export function listCarriers(): Promise<{ items: Carrier[] }> {
  return get<{ items: Carrier[] }>('/api/admin/shipping/carriers')
}

export function createCarrier(body: CarrierUpsert): Promise<Carrier> {
  return post<Carrier>('/api/admin/shipping/carriers', body)
}

export function updateCarrier(id: number, body: CarrierUpsert): Promise<Carrier> {
  return put<Carrier>(`/api/admin/shipping/carriers/${id}`, body)
}

export function deleteCarrier(id: number): Promise<void> {
  return del<void>(`/api/admin/shipping/carriers/${id}`)
}

export function toggleCarrierStatus(id: number, status: CarrierStatus): Promise<Carrier> {
  return patch<Carrier>(`/api/admin/shipping/carriers/${id}/status`, { status })
}

/** 旧运费规则（只读；写操作 410901 已废弃） */
export function listRates(): Promise<{ items: ShippingRate[] }> {
  return get<{ items: ShippingRate[] }>('/api/admin/shipping/rates')
}

// ---- 运费选项（zone × carrier_code × service_level） ----

export function listShippingOptions(): Promise<{ items: ShippingOption[] }> {
  return get<{ items: ShippingOption[] }>('/api/admin/shipping/options')
}

export function createShippingOption(body: ShippingOptionUpsert): Promise<ShippingOption> {
  return post<ShippingOption>('/api/admin/shipping/options', body)
}

export function updateShippingOption(id: number, body: ShippingOptionUpsert): Promise<ShippingOption> {
  return put<ShippingOption>(`/api/admin/shipping/options/${id}`, body)
}

export function setShippingOptionEnabled(id: number, enabled: boolean): Promise<ShippingOption> {
  return patch<ShippingOption>(`/api/admin/shipping/options/${id}/enabled`, { enabled })
}

export function deleteShippingOption(id: number): Promise<void> {
  return del<void>(`/api/admin/shipping/options/${id}`)
}

/** 运费/税费试算（USD 基准） */
export function previewShippingQuote(body: ShippingQuotePreviewRequest): Promise<ShippingQuotePreviewResponse> {
  return post<ShippingQuotePreviewResponse>('/api/admin/shipping/quote-preview', body)
}

/** ISO 国家列表（公开接口，含 zone / 州省字典） */
export function listCountries(): Promise<{ items: Country[] }> {
  return get<{ items: Country[] }>('/api/store/shipping/countries')
}
