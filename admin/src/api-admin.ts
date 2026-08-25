// request() 已固定 Accept-Language: zh-CN（管理端不做双语），此处直接复用，不再包一层
import { request } from './api'

/** 管理端商品（镜像后端 AdminProductResponse，双语原始字段供编辑） */
export interface AdminProduct {
  id: number
  groupId: number
  nameZh: string
  nameEn: string | null
  descriptionZh: string | null
  descriptionEn: string | null
  /** 详情富文本HTML（中文），null=未配置 */
  detailZh: string | null
  /** 详情富文本HTML（英文），null=未配置 */
  detailEn: string | null
  badgeZh: string | null
  badgeEn: string | null
  accent: string
  priceCents: number
  imageUrl: string | null
  onSale: boolean
}

/** 商品新增/编辑请求体（镜像后端 AdminProductUpsertRequest） */
export interface AdminProductUpsert {
  groupId: number
  nameZh: string
  nameEn: string
  descriptionZh: string
  descriptionEn: string
  detailZh: string
  detailEn: string
  badgeZh: string
  badgeEn: string
  accent: string
  priceCents: number
  imageUrl: string
  onSale: boolean
}

/** 管理端分组（镜像后端 AdminGroupResponse） */
export interface AdminGroup {
  id: number
  nameZh: string
  nameEn: string | null
  sortOrder: number
  /** 组内商品数（含下架；非 0 不可删） */
  productCount: number
}

/** 分组新增/编辑请求体（镜像后端 AdminGroupUpsertRequest） */
export interface AdminGroupUpsert {
  nameZh: string
  nameEn: string
  sortOrder: number
}

/** 管理端订单列表项（镜像后端 AdminOrderItemResponse） */
export interface AdminOrderItem {
  orderNo: string
  productName: string
  buyerEmail: string | null
  quantity: number
  amountCents: number
  status: string
  statusLabel: string
  paymentProvider: string | null
  createdAt: string
  paidAt: string | null
}

/** 管理端用户列表项（镜像后端 AdminUserResponse） */
export interface AdminUser {
  id: number
  email: string
  nickname: string | null
  avatarUrl: string | null
  /** 角色：只读展示，改角色只能直接改库 */
  role: 'ADMIN' | 'USER'
  orderCount: number
  createdAt: string
}

/** 概览（镜像后端 AdminDashboardResponse） */
export interface AdminDashboard {
  totalRevenueCents: number
  totalOrderCount: number
  todayOrderCount: number
  todayRevenueCents: number
  userCount: number
  onSaleProductCount: number
  recentOrders: AdminOrderItem[]
}

/** 统一分页结构（镜像后端 PageResponse） */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 概览统计 + 最近订单 */
export function fetchAdminDashboard(): Promise<AdminDashboard> {
  return request<AdminDashboard>('/api/admin/dashboard')
}

/** 全部商品（含下架），可按分组过滤 */
export function fetchAdminProducts(groupId?: number): Promise<AdminProduct[]> {
  const query = groupId ? `?groupId=${groupId}` : ''
  return request<AdminProduct[]>(`/api/admin/products${query}`)
}

/** 新增商品 */
export function createAdminProduct(body: AdminProductUpsert): Promise<AdminProduct> {
  return request<AdminProduct>('/api/admin/products', { method: 'POST', body: JSON.stringify(body) })
}

/** 编辑商品 */
export function updateAdminProduct(id: number, body: AdminProductUpsert): Promise<AdminProduct> {
  return request<AdminProduct>(`/api/admin/products/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

/** 商品上/下架 */
export function setAdminProductOnSale(id: number, onSale: boolean): Promise<AdminProduct> {
  return request<AdminProduct>(`/api/admin/products/${id}/on-sale`, {
    method: 'PUT',
    body: JSON.stringify({ onSale }),
  })
}

/** 全部分组（含商品数） */
export function fetchAdminGroups(): Promise<AdminGroup[]> {
  return request<AdminGroup[]>('/api/admin/groups')
}

/** 新增分组 */
export function createAdminGroup(body: AdminGroupUpsert): Promise<AdminGroup> {
  return request<AdminGroup>('/api/admin/groups', { method: 'POST', body: JSON.stringify(body) })
}

/** 编辑分组 */
export function updateAdminGroup(id: number, body: AdminGroupUpsert): Promise<AdminGroup> {
  return request<AdminGroup>(`/api/admin/groups/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

/** 删除分组（仅空组） */
export function deleteAdminGroup(id: number): Promise<null> {
  return request<null>(`/api/admin/groups/${id}`, { method: 'DELETE' })
}

/** 订单分页：状态与订单号前缀可选 */
export function fetchAdminOrders(params: {
  page: number
  size: number
  status?: string
  keyword?: string
}): Promise<PageResult<AdminOrderItem>> {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status) {
    query.set('status', params.status)
  }
  if (params.keyword) {
    query.set('keyword', params.keyword)
  }
  return request<PageResult<AdminOrderItem>>(`/api/admin/orders?${query}`)
}

/** 用户分页 */
export function fetchAdminUsers(page: number, size: number): Promise<PageResult<AdminUser>> {
  return request<PageResult<AdminUser>>(`/api/admin/users?page=${page}&size=${size}`)
}

/** 管理端发货历史项（镜像后端 AdminShipmentItemResponse） */
export interface AdminShipmentItem {
  id: number
  content: string
  reason: string | null
  operatorEmail: string | null
  emailTo: string
  emailStatus: 'SENT' | 'FAILED'
  emailError: string | null
  shippedAt: string
}

/** 发货结果（镜像后端 AdminShipmentResponse） */
export interface AdminShipResult {
  shippedAt: string
  emailStatus: 'SENT' | 'FAILED'
  emailError: string | null
}

/** 某订单的发货历史（时间倒序） */
export function fetchAdminShipments(orderNo: string): Promise<AdminShipmentItem[]> {
  return request<AdminShipmentItem[]>(`/api/admin/orders/${orderNo}/shipments`)
}

/** 发货 / 重新发货（重新发货必须带 reason） */
export function shipAdminOrder(
  orderNo: string,
  body: { content: string; reason?: string },
): Promise<AdminShipResult> {
  return request<AdminShipResult>(`/api/admin/orders/${orderNo}/shipments`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
