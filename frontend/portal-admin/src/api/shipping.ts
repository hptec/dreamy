// shipping 域 API（PAGE-SHP-01；E-SHP-01~09）
import { get, post, put, patch, del } from './client'
import type { Carrier, CarrierStatus, CarrierUpsert, ShippingRate, ShippingRateUpsert } from './types'

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

export function listRates(): Promise<{ items: ShippingRate[] }> {
  return get<{ items: ShippingRate[] }>('/api/admin/shipping/rates')
}

export function createRate(body: ShippingRateUpsert): Promise<ShippingRate> {
  return post<ShippingRate>('/api/admin/shipping/rates', body)
}

export function updateRate(id: number, body: ShippingRateUpsert): Promise<ShippingRate> {
  return put<ShippingRate>(`/api/admin/shipping/rates/${id}`, body)
}

export function deleteRate(id: number): Promise<void> {
  return del<void>(`/api/admin/shipping/rates/${id}`)
}
