# Feature Map — Dreamy 婚纱礼服跨境外贸电商（总索引）

> 双门户功能地图总索引。详细功能分门户拆分到：
> - [feature-map-portal-store.md](feature-map-portal-store.md) — 消费者商城
> - [feature-map-portal-admin.md](feature-map-portal-admin.md) — 平台管理后台
>
> 创建日期：2026-05-29 | 来源：requirements-brief.md + 竞品分析（birdygrey / davidsbridal / kissprom）

## 门户划分

| 门户 ID | 名称 | 目标用户 | 功能数 | 安全隔离 |
|---------|------|---------|--------|---------|
| `portal-store` | Dreamy 消费者商城 | 准新娘 / 伴娘 / 晚宴客户 / 全球 C 端 | F-001 ~ F-058 | 与 admin 完全隔离，不互链 |
| `portal-admin` | Dreamy 运营管理后台 | 平台运营人员 | A-001 ~ A-040 | 与 store 完全隔离，独立鉴权 |

## 架构预留建议

- **前端组织**：pnpm workspace 双子包，各门户独立 Vite 工程、独立端口（store=5173，admin=5174）；共享设计 token 通过各自 tailwind.config 同源配置（手动对齐，不跨包引用）
- **后端 API 策略（预留）**：store 与 admin 共享同一后端，按角色鉴权拆分路由前缀 `/api/store/*` 与 `/api/admin/*`；admin 需要独立登录态与 RBAC
- **认证隔离**：store 用户态（消费者 JWT）与 admin 管理态（运营 RBAC）完全独立，互不复用 session
- **数据范围**：store 仅可见自身用户数据；admin 可见全平台数据，按角色（超管 / 商品运营 / 订单客服 / 内容编辑）限定可操作范围

## 门户数据范围对照表

| 数据域 | portal-store 视角 | portal-admin 视角 |
|--------|------------------|-------------------|
| 商品 | 只读、仅上架商品 | 增删改、含草稿/下架 |
| 订单 | 仅本人订单 | 全平台订单、可改状态 |
| 用户 | 仅本人资料 | 全用户、可禁用 |
| 内容 | 只读已发布 | 增删改、含草稿 |
| 营销 | 被动接收（看到优惠） | 主动配置优惠规则 |

## 功能模块总览

### portal-store（消费端）

| 模块 | 功能编号区间 | 关键功能 |
|------|------------|---------|
| M1 导航与首页 | F-001 ~ F-008 | 全局导航、Mega Menu、Hero、品类入口、推荐位、Footer |
| M2 商品浏览 | F-009 ~ F-020 | 品类列表、Filter、排序、分页、快速预览、Outdoor 主题页 |
| M3 商品详情 PDP | F-021 ~ F-031 | 图廊、Zoom、视频、SKU 选择、尺码表、色板、评价、Q&A、推荐、Outfit Builder |
| M4 购物车与结算 | F-032 ~ F-040 | Cart Drawer、购物车页、多步结算、多支付、多币种、物流 |
| M5 账户中心 | F-041 ~ F-049 | 登录注册、订单、跟踪、地址、Wishlist、浏览历史、评价、设置 |
| M6 内容栏目 | F-050 ~ F-055 | 灵感馆、Real Weddings、Blog、Wedding Guides、Lookbook |
| M7 营销与全局 | F-056 ~ F-058 | Newsletter 弹窗、Exit Intent、Cookie Notice、搜索、404 |

### portal-admin（后台）

| 模块 | 功能编号区间 | 关键功能 |
|------|------------|---------|
| AM1 工作台 | A-001 ~ A-003 | Dashboard 总览、待办、快捷入口 |
| AM2 商品管理 PIM | A-004 ~ A-012 | 商品列表、商品编辑、SKU 矩阵、尺码表、品类主题 |
| AM3 订单管理 OMS | A-013 ~ A-019 | 订单列表、订单详情、发货、退款审批 |
| AM4 用户管理 | A-020 ~ A-023 | 用户列表、用户详情、角色权限 |
| AM5 营销活动 | A-024 ~ A-029 | 优惠券、促销、Flash Sale、Banner、邮件营销 |
| AM6 数据看板 | A-030 ~ A-033 | GMV、流量、转化漏斗、商品热度 |
| AM7 CMS 内容 | A-034 ~ A-037 | Blog、Lookbook、Real Wedding、Guide 编辑 |
| AM8 物流与退款 | A-038 ~ A-039 | 承运配置、退款工单 |
| AM9 系统 | A-040 | 管理员账号、角色、操作日志 |

## Must Have / Should Have 分级

- **Must Have**：M1-M5 全部、M6 灵感馆+Real Weddings+Blog+Guides、M7 全部；AM1-AM7 全部
- **Should Have（本次纳入）**：M3 Outfit Builder、M6 Lookbook、AM8 物流退款、AM9 系统
- **本次纳入但简化**：多语言 ES（仅切换器，文本英文占位）、AR 入口（仅按钮无交互）

详细功能表见各门户 feature-map。
