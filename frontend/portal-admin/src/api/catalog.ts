// catalog 域 API（PAGE-CAT-A01~A04 消费端点 E-CAT-08~35；复用 client.ts 拦截器：
// R 解包 / snake↔camel / admin JWT / 401 跳登录）
import { get, post, put, patch, del } from './client'
import { downloadCsv } from '@/utils/download'
import type {
  AdminCategoryNode,
  AdminCategoryUpsert,
  AdminProductDetail,
  AdminProductListItem,
  AdminProductUpsert,
  AttributeDef,
  AttributeDefUpsert,
  AttributeSet,
  AttributeSetUpsert,
  PageResult,
  PresignRequest,
  PresignResponse,
  ProductBatchAction,
  ProductBatchResult,
  ProductStatus,
  Tag,
  TagDimension,
  TagDimensionUpsert,
  TagUpsert,
} from './types'

// ===== 商品 E-CAT-08~14 =====

export function listProducts(params: {
  page?: number
  pageSize?: number
  status?: ProductStatus
  categoryId?: number
  search?: string
}): Promise<PageResult<AdminProductListItem>> {
  return get<PageResult<AdminProductListItem>>('/api/admin/products', { params })
}

export function getProduct(id: number): Promise<AdminProductDetail> {
  return get<AdminProductDetail>(`/api/admin/products/${id}`)
}

export function createProduct(body: AdminProductUpsert): Promise<AdminProductDetail> {
  return post<AdminProductDetail>('/api/admin/products', body)
}

export function updateProduct(id: number, body: AdminProductUpsert): Promise<AdminProductDetail> {
  return put<AdminProductDetail>(`/api/admin/products/${id}`, body)
}

export function deleteProduct(id: number): Promise<void> {
  return del<void>(`/api/admin/products/${id}`)
}

export function toggleProductStatus(id: number, status: ProductStatus): Promise<AdminProductListItem> {
  return patch<AdminProductListItem>(`/api/admin/products/${id}/status`, { status })
}

export function patchProductFlags(
  id: number,
  partial: { isNew?: boolean; isBest?: boolean; recommend?: boolean; sort?: number },
): Promise<AdminProductListItem> {
  return patch<AdminProductListItem>(`/api/admin/products/${id}/flags`, partial)
}

/**
 * FORM-CAT-P01（ALIGN-007，API-CAT-01）：批量操作（上架/下架/推荐/删除）。
 * 逐条容错语义：部分/全部失败仍 200，由调用方按 failures 展示明细（409509→已发布需先下架）。
 */
export function batchProducts(action: ProductBatchAction, ids: number[]): Promise<ProductBatchResult> {
  return post<ProductBatchResult>('/api/admin/products/batch', { action, ids })
}

export interface ProductExportQuery {
  status?: ProductStatus
  categoryId?: number
  search?: string
}

/**
 * FORM-CAT-P02（ALIGN-007，API-CAT-02）：导出商品 CSV。
 * 走原生下载（需带 Authorization，统一委托 utils/download.ts downloadCsv，FND-REUSE-001）；
 * 仅服务端筛选口径（search/category_id/status，不含「更多筛选」当前页过滤、不含分页）。
 * 文件名优先取后端 Content-Disposition（products-{yyyyMMdd}.csv），返回 X-Export-Truncated 截断标记。
 */
export function exportProducts(params: ProductExportQuery): Promise<{ truncated: boolean }> {
  const query = new URLSearchParams()
  if (params.status != null) query.set('status', String(params.status))
  if (params.categoryId != null) query.set('category_id', String(params.categoryId))
  if (params.search) query.set('search', params.search)
  return downloadCsv('/api/admin/products/export', query, 'products')
}

// ===== 分类 E-CAT-15~18 =====

export function listCategories(): Promise<{ items: AdminCategoryNode[] }> {
  return get<{ items: AdminCategoryNode[] }>('/api/admin/categories')
}

export function createCategory(body: AdminCategoryUpsert): Promise<AdminCategoryNode> {
  return post<AdminCategoryNode>('/api/admin/categories', body)
}

export function updateCategory(id: number, body: AdminCategoryUpsert): Promise<AdminCategoryNode> {
  return put<AdminCategoryNode>(`/api/admin/categories/${id}`, body)
}

export function deleteCategory(id: number): Promise<void> {
  return del<void>(`/api/admin/categories/${id}`)
}

// ===== 属性集 E-CAT-19~22 =====

export function listAttributeSets(): Promise<{ items: AttributeSet[] }> {
  return get<{ items: AttributeSet[] }>('/api/admin/attribute-sets')
}

export function createAttributeSet(body: AttributeSetUpsert): Promise<AttributeSet> {
  return post<AttributeSet>('/api/admin/attribute-sets', body)
}

export function updateAttributeSet(id: number, body: AttributeSetUpsert): Promise<AttributeSet> {
  return put<AttributeSet>(`/api/admin/attribute-sets/${id}`, body)
}

export function deleteAttributeSet(id: number): Promise<void> {
  return del<void>(`/api/admin/attribute-sets/${id}`)
}

// ===== 属性字典 E-CAT-23~26 =====

export function listAttributeDefs(): Promise<{ items: AttributeDef[] }> {
  return get<{ items: AttributeDef[] }>('/api/admin/attribute-defs')
}

export function createAttributeDef(body: AttributeDefUpsert): Promise<AttributeDef> {
  return post<AttributeDef>('/api/admin/attribute-defs', body)
}

export function updateAttributeDef(id: number, body: AttributeDefUpsert): Promise<AttributeDef> {
  return put<AttributeDef>(`/api/admin/attribute-defs/${id}`, body)
}

export function deleteAttributeDef(id: number, force?: boolean): Promise<void> {
  const params = force ? { force: 'true' } : undefined
  return del<void>(`/api/admin/attribute-defs/${id}`, { params })
}

// ===== 标签维度 E-CAT-27~30 =====

export function listTagDimensions(): Promise<{ items: TagDimension[] }> {
  return get<{ items: TagDimension[] }>('/api/admin/tag-dimensions')
}

export function createTagDimension(body: TagDimensionUpsert): Promise<TagDimension> {
  return post<TagDimension>('/api/admin/tag-dimensions', body)
}

export function updateTagDimension(id: number, body: TagDimensionUpsert): Promise<TagDimension> {
  return put<TagDimension>(`/api/admin/tag-dimensions/${id}`, body)
}

export function deleteTagDimension(id: number): Promise<void> {
  return del<void>(`/api/admin/tag-dimensions/${id}`)
}

// ===== 标签 E-CAT-31~34 =====

export function listTags(dimensionId?: number): Promise<{ items: Tag[] }> {
  return get<{ items: Tag[] }>('/api/admin/tags', { params: { dimensionId } })
}

export function createTag(body: TagUpsert): Promise<Tag> {
  return post<Tag>('/api/admin/tags', body)
}

export function updateTag(id: number, body: TagUpsert): Promise<Tag> {
  return put<Tag>(`/api/admin/tags/${id}`, body)
}

export function deleteTag(id: number): Promise<void> {
  return del<void>(`/api/admin/tags/${id}`)
}

// ===== 预签名上传 E-CAT-35（FLOW-P17 两步：presign → PUT 直传） =====

export function presignUpload(req: PresignRequest): Promise<PresignResponse> {
  return post<PresignResponse>('/api/admin/uploads/presign', req)
}

// ===== 护理标签字典 =====

/**
 * 获取护理标签列表（后台用于选择器）
 * 证据锚点: catalog-fabric-care-frontend-detail.md INTERACTION-FC-05
 */
export function listAdminCareInstructions(): Promise<import('./types').CareInstructionListResponse> {
  return get<import('./types').CareInstructionListResponse>('/api/admin/care-instructions')
}
