# 页面规划 — Dreamy 双门户

> 基于 feature-map 的"页面建议"细化。共 43 个核心页面（store 26 + admin 17）。
> 设计风格：`editorial-luxe-coastal`（自定义，详见 sync-status.yml）

## 设计系统（自主拍板）

### 配色（融合 editorial-luxe + warm-sand + sage-serenity）
| Token | 值 | 用途 |
|-------|-----|------|
| background | `#FAF7F2` | 暖米白主背景 |
| surface | `#FFFFFF` | 卡片/面板 |
| ink | `#2B2925` | 主文字（墨炭） |
| ink-soft | `#6B5D4F` | 次级文字 |
| gold (primary) | `#C19A6B` | 主品牌色/CTA 描边 |
| gold-deep | `#A07E52` | hover 态 |
| sage | `#8B9D83` | 辅助点缀（户外自然） |
| blush | `#D8A7A0` | 玫瑰金点缀（婚礼柔美） |
| border | `#E7DFD3` | 细线分隔 |
| muted | `#F0EBE3` | 浅底块 |

### 字体
- Display/标题：`Cormorant Garamond`（优雅高衬线，婚纱奢华感）
- 正文/UI：`Jost`（几何无衬线，现代清爽）
- 后台 UI：`Inter`（数据密集场景可读性）

### 视觉特征
大图 Hero + 大量留白 + 细金线描边 + 柔和阴影 + 自然光感图片 + 衬线大标题 + 字母间距拉开的小标签（letter-spacing）。圆角克制（card 4-8px，按钮 2px 近直角体现高级感）。

---

## portal-store 页面清单（端口 5173）

| # | 页面 | 组件 (src/views) | 路由 | 类型 | 包含功能 |
|---|------|------|------|------|---------|
| 1 | 首页 | Index.vue | / | 首页 | F-001~F-008 |
| 2 | 婚纱列表 | WeddingDresses.vue | /wedding-dresses | 列表 | F-009,F-012~F-017 |
| 3 | 小礼服列表 | SpecialOccasion.vue | /special-occasion | 列表 | F-010,F-012~F-017 |
| 4 | 配饰列表 | Accessories.vue | /accessories | 列表 | F-011,F-012~F-017 |
| 5 | Outdoor 主题 | OutdoorWeddings.vue | /outdoor-weddings | 列表 | F-018,F-019 |
| 6 | 婚纱 PDP | ProductDetail.vue | /product/aurelia-gown | 详情 | F-021~F-031 |
| 7 | 礼服 PDP | ProductBridesmaid.vue | /product/seabreeze-bridesmaid | 详情 | F-021~F-031 |
| 8 | 配饰 PDP | ProductVeil.vue | /product/cathedral-veil | 详情 | F-021~F-028 |
| 9 | 搜索结果 | SearchResults.vue | /search | 列表 | F-020,F-057 |
| 10 | 购物车 | Cart.vue | /cart | 列表 | F-033,F-034 |
| 11 | 结算 | Checkout.vue | /checkout | 表单 | F-035~F-038,F-040 |
| 12 | 下单成功 | OrderSuccess.vue | /order-success | 详情 | F-039 |
| 13 | 登录注册 | Auth.vue | /account/login | 表单 | F-041 |
| 14 | 账户总览 | AccountDashboard.vue | /account | 仪表盘 | F-042 |
| 15 | 订单列表 | AccountOrders.vue | /account/orders | 列表 | F-043 |
| 16 | 订单详情 | AccountOrderDetail.vue | /account/orders/1001 | 详情 | F-044,F-045 |
| 17 | 地址簿 | AccountAddresses.vue | /account/addresses | 列表 | F-046 |
| 18 | 收藏夹 | AccountWishlist.vue | /account/wishlist | 列表 | F-047,F-048 |
| 19 | 账户设置 | AccountSettings.vue | /account/settings | 设置 | F-049 |
| 20 | 灵感馆 | Inspiration.vue | /inspiration | 列表 | F-050 |
| 21 | Real Weddings 列表 | RealWeddings.vue | /real-weddings | 列表 | F-051 |
| 22 | Real Wedding 详情 | RealWeddingDetail.vue | /real-weddings/coastal-emma-james | 详情 | F-052 |
| 23 | Blog 列表 | Blog.vue | /blog | 列表 | F-053 |
| 24 | Blog 详情 | BlogPost.vue | /blog/outdoor-wedding-guide | 详情 | F-054 |
| 25 | 婚礼指南 | WeddingGuides.vue | /wedding-guides | 列表 | F-055 |
| 26 | 关于/联系/FAQ/404 | About.vue / Contact.vue / Faq.vue / NotFound.vue | /about,/contact,/faq,/:404 | 内容 | F-058 |

**全局组件 (src/components)**：NavBar.vue（含 MegaMenu/F-001~F-003）、SiteFooter.vue（F-008）、CartDrawer.vue（F-032）、NewsletterModal.vue（F-056）、CookieNotice.vue（F-057）、QuickViewModal.vue（F-015）、SizeGuideModal.vue（F-026）、ProductCard.vue、FilterSidebar.vue、CurrencyLangSwitcher.vue。

## portal-admin 页面清单（端口 5174）

| # | 页面 | 组件 | 路由 | 类型 | 包含功能 |
|---|------|------|------|------|---------|
| 1 | 后台登录 | AdminLogin.vue | /login | 表单 | 鉴权 |
| 2 | 工作台 | Dashboard.vue | / | 仪表盘 | A-001~A-003 |
| 3 | 商品列表 | Products.vue | /products | 列表 | A-004 |
| 4 | 商品编辑 | ProductEdit.vue | /products/edit/1 | 表单 | A-005~A-010 |
| 5 | 品类与主题 | Categories.vue | /categories | 列表 | A-011,A-012 |
| 6 | 订单列表 | Orders.vue | /orders | 列表 | A-013,A-019 |
| 7 | 订单详情 | OrderDetail.vue | /orders/1001 | 详情 | A-014~A-018 |
| 8 | 用户列表 | Customers.vue | /customers | 列表 | A-020 |
| 9 | 用户详情 | CustomerDetail.vue | /customers/1 | 详情 | A-021~A-023 |
| 10 | 优惠券与促销 | Promotions.vue | /promotions | 列表 | A-024~A-026,A-029 |
| 11 | Banner 配置 | Banners.vue | /banners | 列表 | A-027 |
| 12 | 邮件营销 | EmailMarketing.vue | /email-marketing | 列表 | A-028 |
| 13 | 数据看板 | Analytics.vue | /analytics | 仪表盘 | A-030~A-033 (Chart.js) |
| 14 | 内容管理 | Content.vue | /content | 列表 | A-034~A-037 |
| 15 | 物流配置 | Shipping.vue | /shipping | 设置 | A-038 |
| 16 | 退款工单 | Refunds.vue | /refunds | 列表 | A-039 |
| 17 | 系统设置 | Settings.vue | /settings | 设置 | A-040 |

**全局组件**：AdminSidebar.vue、AdminTopbar.vue、StatCard.vue、DataTable.vue、ChartCard.vue。

## 页面流转

### portal-store
```
[首页 Index]
  ├─ Mega Menu → [婚纱/小礼服/配饰列表] → [PDP] → CartDrawer → [购物车] → [结算] → [下单成功]
  ├─ [Outdoor 主题] → 子主题 → [列表] → [PDP]
  ├─ 搜索 → [搜索结果] → [PDP]
  ├─ Account 图标 → [登录] → [账户总览]
  │     └─ [订单列表] → [订单详情/物流] / [地址] / [收藏] / [设置]
  └─ Inspiration → [灵感馆] / [Real Weddings] → [详情] / [Blog] → [详情] / [婚礼指南]
  全局：NewsletterModal（首访）、CookieNotice、QuickView（列表）、SizeGuide（PDP）
```

### portal-admin
```
[AdminLogin] → [Dashboard]
  Sidebar 导航：
  ├─ 商品 → [商品列表] → [商品编辑] / [品类主题]
  ├─ 订单 → [订单列表] → [订单详情] → 退款 → [退款工单]
  ├─ 用户 → [用户列表] → [用户详情]
  ├─ 营销 → [优惠券促销] / [Banner] / [邮件营销]
  ├─ 数据 → [数据看板]
  ├─ 内容 → [内容管理]
  └─ 设置 → [物流配置] / [系统设置]
```

> 门户隔离：store 与 admin 之间无任何跳转链接。
