// marketing 域 API（PAGE-MKT-A01~A05 消费端点 E-MKT-13~46；上传复用 catalog presign）
import { get, post, put, patch, del } from './client'
import type {
  Banner,
  BannerUpsert,
  BlogPost,
  BlogPostUpsert,
  Coupon,
  CouponUpsert,
  FlashSale,
  FlashSaleUpsert,
  Guide,
  GuideUpsert,
  Lookbook,
  LookbookUpsert,
  PageResult,
  RealWedding,
  RealWeddingUpsert,
} from './types'

// ===== 优惠券 E-MKT-13~16 =====

export function listCoupons(params: {
  page?: number
  pageSize?: number
  status?: string
  search?: string
}): Promise<PageResult<Coupon>> {
  return get<PageResult<Coupon>>('/api/admin/promotions/coupons', { params })
}

export function createCoupon(body: CouponUpsert): Promise<Coupon> {
  return post<Coupon>('/api/admin/promotions/coupons', body)
}

export function updateCoupon(id: number, body: CouponUpsert): Promise<Coupon> {
  return put<Coupon>(`/api/admin/promotions/coupons/${id}`, body)
}

export function deleteCoupon(id: number): Promise<void> {
  return del<void>(`/api/admin/promotions/coupons/${id}`)
}

// ===== 闪购 E-MKT-17~20 =====

export function listFlashSales(status?: string): Promise<{ items: FlashSale[] }> {
  return get<{ items: FlashSale[] }>('/api/admin/promotions/flash-sales', { params: { status } })
}

export function createFlashSale(body: FlashSaleUpsert): Promise<FlashSale> {
  return post<FlashSale>('/api/admin/promotions/flash-sales', body)
}

export function updateFlashSale(id: number, body: FlashSaleUpsert): Promise<FlashSale> {
  return put<FlashSale>(`/api/admin/promotions/flash-sales/${id}`, body)
}

export function deleteFlashSale(id: number): Promise<void> {
  return del<void>(`/api/admin/promotions/flash-sales/${id}`)
}

// ===== Banner E-MKT-21~25 =====

export function listBanners(position?: string): Promise<{ items: Banner[] }> {
  return get<{ items: Banner[] }>('/api/admin/banners', { params: { position } })
}

export function createBanner(body: BannerUpsert): Promise<Banner> {
  return post<Banner>('/api/admin/banners', body)
}

export function updateBanner(id: number, body: BannerUpsert): Promise<Banner> {
  return put<Banner>(`/api/admin/banners/${id}`, body)
}

export function deleteBanner(id: number): Promise<void> {
  return del<void>(`/api/admin/banners/${id}`)
}

export function toggleBannerStatus(id: number, status: string): Promise<Banner> {
  return patch<Banner>(`/api/admin/banners/${id}/status`, { status })
}

// ===== Blog E-MKT-26~31 =====

export function listBlogs(params: {
  page?: number
  pageSize?: number
  status?: string
  search?: string
}): Promise<PageResult<BlogPost>> {
  return get<PageResult<BlogPost>>('/api/admin/content/blogs', { params })
}

export function getBlog(id: number): Promise<BlogPost> {
  return get<BlogPost>(`/api/admin/content/blogs/${id}`)
}

export function createBlog(body: BlogPostUpsert): Promise<BlogPost> {
  return post<BlogPost>('/api/admin/content/blogs', body)
}

export function updateBlog(id: number, body: BlogPostUpsert): Promise<BlogPost> {
  return put<BlogPost>(`/api/admin/content/blogs/${id}`, body)
}

export function deleteBlog(id: number): Promise<void> {
  return del<void>(`/api/admin/content/blogs/${id}`)
}

export function patchBlogStatus(id: number, status: string): Promise<BlogPost> {
  return patch<BlogPost>(`/api/admin/content/blogs/${id}/status`, { status })
}

// ===== Real Weddings E-MKT-32~36 =====

export function listWeddings(params: {
  page?: number
  pageSize?: number
  status?: string
}): Promise<PageResult<RealWedding>> {
  return get<PageResult<RealWedding>>('/api/admin/content/weddings', { params })
}

export function createWedding(body: RealWeddingUpsert): Promise<RealWedding> {
  return post<RealWedding>('/api/admin/content/weddings', body)
}

export function updateWedding(id: number, body: RealWeddingUpsert): Promise<RealWedding> {
  return put<RealWedding>(`/api/admin/content/weddings/${id}`, body)
}

export function deleteWedding(id: number): Promise<void> {
  return del<void>(`/api/admin/content/weddings/${id}`)
}

export function patchWeddingStatus(id: number, status: string): Promise<RealWedding> {
  return patch<RealWedding>(`/api/admin/content/weddings/${id}/status`, { status })
}

// ===== Lookbook E-MKT-37~41 =====

export function listLookbooks(status?: string): Promise<{ items: Lookbook[] }> {
  return get<{ items: Lookbook[] }>('/api/admin/content/lookbooks', { params: { status } })
}

export function createLookbook(body: LookbookUpsert): Promise<Lookbook> {
  return post<Lookbook>('/api/admin/content/lookbooks', body)
}

export function updateLookbook(id: number, body: LookbookUpsert): Promise<Lookbook> {
  return put<Lookbook>(`/api/admin/content/lookbooks/${id}`, body)
}

export function deleteLookbook(id: number): Promise<void> {
  return del<void>(`/api/admin/content/lookbooks/${id}`)
}

export function patchLookbookStatus(id: number, status: string): Promise<Lookbook> {
  return patch<Lookbook>(`/api/admin/content/lookbooks/${id}/status`, { status })
}

// ===== Guide E-MKT-42~46 =====

export function listGuides(status?: string): Promise<{ items: Guide[] }> {
  return get<{ items: Guide[] }>('/api/admin/content/guides', { params: { status } })
}

export function createGuide(body: GuideUpsert): Promise<Guide> {
  return post<Guide>('/api/admin/content/guides', body)
}

export function updateGuide(id: number, body: GuideUpsert): Promise<Guide> {
  return put<Guide>(`/api/admin/content/guides/${id}`, body)
}

export function deleteGuide(id: number): Promise<void> {
  return del<void>(`/api/admin/content/guides/${id}`)
}

export function patchGuideStatus(id: number, status: string): Promise<Guide> {
  return patch<Guide>(`/api/admin/content/guides/${id}/status`, { status })
}
