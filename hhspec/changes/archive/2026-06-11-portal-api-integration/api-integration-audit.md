# Dreamy 前后端对接现状盘点报告

**报告日期**: 2026-06-10  
**扫描范围**: backend/src/main/java（所有Controller）、frontend/portal-store（Next.js）、frontend/portal-admin（Vue3）

---

## 1. 后端 API 清单

### 1.1 消费端 API（/api/store/*）

**路由前缀**: `/api/store`

#### A. StoreAuthController（认证与登录）
- `POST /api/store/auth/otp/send` — 发送OTP（FLOW-01 FUNC-001）
- `POST /api/store/auth/otp/verify` — 验证OTP（FLOW-02 FUNC-002）
- `POST /api/store/auth/oidc/{provider}/callback` — OIDC回调（Google/Apple）（FLOW-03 FUNC-004/005）
- `POST /api/store/auth/refresh` — 刷新token（FLOW-04 FUNC-030）
- `GET /api/store/auth/config` — 获取认证配置（FUNC-003）

#### B. AccountController（账户安全，需store JWT）
- `GET /api/store/account/profile` — 获取用户资料（FUNC-007）
- `GET /api/store/account/identities` — 列出登录方式（FUNC-010）
- `POST /api/store/account/identities/bind` — 绑定登录方式（FLOW-05 FUNC-008）
- `DELETE /api/store/account/identities/{identityId}` — 解绑登录方式（FLOW-05 FUNC-009）
- `POST /api/store/account/email/change-primary` — 换主邮箱（FLOW-06 FUNC-026）
- `POST /api/store/account/delete` — 注销账户（FLOW-08 FUNC-027）

**小计**: 11个端点，均已实现

---

### 1.2 管理端 API（/api/admin/*）

**路由前缀**: `/api/admin`

#### A. AdminAuthController（管理员认证与管理）
- `POST /api/admin/auth/login` — 管理员登录（FLOW-09 FUNC-014）
- `POST /api/admin/auth/logout` — 登出
- `GET /api/admin/auth/me` — 获取当前管理员（FUNC-021）
- `GET /api/admin/auth/permissions` — 获取实时权限（刷新即生效）

#### B. AdminAuthController 续（管理员CRUD）
- `GET /api/admin/admins` — 列出管理员（分页+筛选）
- `POST /api/admin/admins` — 创建管理员（FLOW-10 FUNC-015）
- `PUT /api/admin/admins/{id}` — 编辑管理员（FLOW-10 FUNC-016）
- `DELETE /api/admin/admins/{id}` — 删除管理员（FLOW-10 FUNC-017）
- `PATCH /api/admin/admins/{id}/status` — 禁用/启用管理员（EDGE-014）
- `PATCH /api/admin/admins/{id}/password` — 重置密码

#### C. UserOpsController（用户运营）
- `GET /api/admin/users` — 列出用户（分页+status/tier/email筛选）（RM-007）
- `GET /api/admin/users/{id}` — 获取用户详情（NP-001 防N+1）
- `PATCH /api/admin/users/{id}/status` — 切换用户状态（FLOW-12 FUNC-022）
- `POST /api/admin/users/{id}/sessions/force-logout` — 强制下线（FLOW-12 FUNC-022 EDGE-023）

#### D. RoleController（角色权限）
- `GET /api/admin/roles` — 列出所有角色（FUNC-018）
- `POST /api/admin/roles` — 创建角色（FUNC-018）
- `PUT /api/admin/roles/{id}` — 编辑角色权限（FLOW-11 FUNC-018/019）
- `DELETE /api/admin/roles/{id}` — 删除角色（FLOW-11 FUNC-020）
- `GET /api/admin/permissions` — 列出所有权限（RM-080 22项）

#### E. AuthConfigController（认证配置+操作日志）
- `GET /api/admin/auth-config` — 获取认证配置（FUNC-003）
- `PUT /api/admin/auth-config` — 更新认证配置（FLOW-13 FUNC-023）
- `GET /api/admin/operation-logs` — 列出操作日志（分页+筛选）（FUNC-024 EDGE-018）
- `GET /api/admin/operation-logs/export` — 导出操作日志CSV（RM-102）
- `DELETE /api/admin/operation-logs[/{id}]` — 返回405（日志只读）（EDGE-018）

**小计**: 24个端点，均已实现

---

## 2. 消费端对接现状（portal-store, Next.js）

### 2.1 API 客户端层
- **位置**: `lib/api/` 目录
  - `client.ts` — 核心请求方法，自动处理JWT续期、camelCase↔snake_case转换、错误处理
  - `auth-api.ts` — 认证相关端点封装（11个函数）
  - `types.ts` — API响应类型定义
  - `token-store.ts` — JWT本地存储管理
  - `case.ts` — camelCase↔snake_case转换工具

### 2.2 页面数据来源清单

| 页面路径 | 页面名称 | 数据来源 | 状态 | 说明 |
|---------|---------|---------|------|------|
| `/account/login` | 登录 | 真实API | ✅ 已对接 | sendOtp + verifyOtp + oidcCallback |
| `/account` | 账户主页 | 真实API | ✅ 已对接 | getProfile（来自authStore.user） |
| `/account/settings` | 账户设置 | 真实API | ✅ 已对接 | 用户资料只读（getProfile） |
| `/account/security` | 安全设置 | 真实API | ✅ 已对接 | listIdentities + bindIdentity + unbindIdentity + changePrimaryEmail |
| `/account/orders` | 订单列表 | Mock数据 | ❌ 未对接 | `data/account.ts` 中的hardcode orders数组 |
| `/account/orders/[id]` | 订单详情 | Mock数据 | ❌ 未对接 | 从orders数组中查询 |
| `/account/addresses` | 地址簿 | Mock数据 | ❌ 未对接 | `data/account.ts` 中的addresses数组 |
| `/account/wishlist` | 收藏清单 | 待查 | ❌ 未实现 | 组件未找到 |
| `/` | 首页 | 纯静态/组件 | 📄 静态 | 无API调用 |
| `/search` | 搜索 | 待查 | ❓ 未知 | 页面存在，实现待查 |
| `/product/[slug]` | 商品详情 | `data/products.ts` | 📄 静态 | 本地数据 |
| `/cart` | 购物车 | 本地状态 | 📄 静态 | useStore()管理 |
| `/checkout` | 结算 | 本地状态 | 📄 静态 | 暂无支付集成 |
| `/order-success` | 订单成功页 | 纯静态 | 📄 静态 | 无API |
| `/wedding-dresses` 等分类 | 分类浏览 | `data/products.ts` | 📄 静态 | 本地数据 |
| `/blog` | 博客列表 | 待查 | ❓ 未知 | 页面存在，实现待查 |
| `/blog/[slug]` | 博客详情 | 待查 | ❓ 未知 | 页面存在，实现待查 |
| `/real-weddings` | 真实婚礼 | 待查 | ❓ 未知 | 页面存在，实现待查 |
| `/faq` | 常见问题 | 待查 | ❓ 未知 | 页面存在，实现待查 |
| `/about`, `/contact` 等 | 信息页 | 纯静态 | 📄 静态 | 无API |

### 2.3 消费端对接总结

**已对接真实API（5个页面）**:
1. `/account/login` — 完整登录流程
2. `/account` — 用户资料展示
3. `/account/settings` — 账户设置
4. `/account/security` — 安全管理
5. 部分 `/account/*` 页面通过 AuthGuard 拉取profile

**使用Mock/本地数据（多个页面）**:
- 订单系统（orders, order-detail）— 完全Mock
- 地址簿（addresses）— 完全Mock  
- 商品/购物车 — 本地 `data/products.ts`

**纯静态页面**: 首页、分类页、信息页、支付相关（无后端）

---

## 3. 管理端对接现状（portal-admin, Vue3）

### 3.1 API 客户端层
- **位置**: `src/api/` 目录
  - `client.ts` — 核心请求方法，axios + token管理 + snake_case↔camelCase转换
  - `auth.ts` — 管理员登录、权限查询
  - `admins.ts` — 管理员CRUD
  - `users.ts` — 用户运营
  - `roles.ts` — 角色权限管理
  - `logs.ts` — 操作日志查询+导出
  - `authConfig.ts` — 认证配置
  - `types.ts` — API响应类型定义

### 3.2 页面数据来源清单

| 页面路径 | 页面名称 | 数据来源 | 状态 | 说明 |
|---------|---------|---------|------|------|
| `/login` | 管理员登录 | 真实API | ✅ 已对接 | adminLogin（/api/admin/auth/login） |
| `/` | 工作台 | Mock数据 | ❌ 未对接 | `data/mock.js` 中的kpis, todos等 |
| `/customers` | 用户列表 | 真实API | ✅ 已对接 | listUsers + status/tier/email筛选 |
| `/customers/[id]` | 用户详情 | 真实API | ✅ 已对接 | getUserDetail（含identities/sessions/loginHistory） |
| `/admin-list` | 管理员列表 | 真实API | ✅ 已对接 | listAdmins + CRUD操作 |
| `/role-management` | 角色权限矩阵 | 真实API | ✅ 已对接 | listRoles + listPermissions + updateRole |
| `/auth-settings` | 认证配置 | 真实API | ✅ 已对接 | getAuthConfig + updateAuthConfig |
| `/operation-logs` | 操作日志 | 真实API | ✅ 已对接 | listOperationLogs + export |
| `/products` | 商品列表 | Mock数据 | ❌ 未对接 | `data/mock.js` products数组 |
| `/products/[id]` | 商品编辑 | Mock数据 | ❌ 未对接 | 本地编辑，无持久化 |
| `/products/new` | 新增商品 | Mock数据 | ❌ 未对接 | 表单收集，无保存 |
| `/orders` | 订单列表 | Mock数据 | ❌ 未对接 | `data/mock.js` orders数组 |
| `/orders/[id]` | 订单详情 | Mock数据 | ❌ 未对接 | 订单跟踪、发货等 |
| `/refunds` | 退款管理 | Mock数据 | ❌ 未对接 | refunds数组 |
| `/categories` | 分类管理 | Mock数据 | ❌ 未对接 | 无API |
| `/promotions` | 优惠券/闪购 | Mock数据 | ❌ 未对接 | coupons/flashSales数组 |
| `/banners` | Banner管理 | Mock数据 | ❌ 未对接 | banners数组 |
| `/site/home` | 首页编辑 | Mock数据 | ❌ 未对接 | homeBlocks数组 |
| `/site/navigation` | 导航配置 | Mock数据 | ❌ 未对接 | navConfig |
| `/content/blog` | 博客管理 | Mock数据 | ❌ 未对接 | blogPosts |
| `/content/weddings` | 婚礼案例 | Mock数据 | ❌ 未对接 | realWeddings |
| `/content/lookbook` | 穿搭指南 | Mock数据 | ❌ 未对接 | lookbooks/guides |
| `/analytics` | 数据看板 | Mock数据 | ❌ 未对接 | gmvTrend/funnel/trafficSources |
| `/shipping` | 物流配置 | Mock数据 | ❌ 未对接 | carriers/shippingRates |
| `/email-marketing` | 邮件营销 | Mock数据 | ❌ 未对接 | emailSubscribers/campaigns |
| `/publish` | 站点发布 | Mock数据 | ❌ 未对接 | pendingChanges |

### 3.3 管理端对接总结

**已对接真实API（7个页面）**:
1. `/login` — 管理员登录
2. `/customers` — 用户列表
3. `/customers/[id]` — 用户详情
4. `/admin-list` — 管理员CRUD
5. `/role-management` — 角色权限矩阵
6. `/auth-settings` — 认证配置编辑
7. `/operation-logs` — 操作日志查询+导出

**使用Mock数据（18个页面）** — **这是重点对接缺口！**
- 商品管理（products, product-edit等）
- 订单管理（orders, order-detail, refunds）
- 营销模块（promotions, banners, email-marketing）
- 内容管理（blog, weddings, lookbook）
- 配置管理（categories, shipping, navigation）
- 工作台（dashboard, analytics）
- 发布系统（publish）

---

## 4. 交叉对比：三类清单

### 清单 A：后端已有 API 且前端已对接（✅ 完成）

| 功能模块 | 端点 | 消费端对接 | 管理端对接 |
|---------|------|---------|---------|
| **认证** | POST /api/store/auth/otp/send | ✅ sendOtp | - |
| | POST /api/store/auth/otp/verify | ✅ verifyOtp | - |
| | POST /api/store/auth/oidc/{provider}/callback | ✅ oidcCallback | - |
| | POST /api/store/auth/refresh | ✅ refreshTokens | - |
| | GET /api/store/auth/config | ✅ getStoreAuthConfig | - |
| **账户** | GET /api/store/account/profile | ✅ getProfile | - |
| | GET /api/store/account/identities | ✅ listIdentities | - |
| | POST /api/store/account/identities/bind | ✅ bindIdentity | - |
| | DELETE /api/store/account/identities/{id} | ✅ unbindIdentity | - |
| | POST /api/store/account/email/change-primary | ✅ changePrimaryEmail | - |
| | POST /api/store/account/delete | ✅ deleteAccount | - |
| **管理员认证** | POST /api/admin/auth/login | - | ✅ adminLogin |
| | POST /api/admin/auth/logout | - | ✅ adminLogout |
| | GET /api/admin/auth/me | - | ✅ adminMe |
| | GET /api/admin/auth/permissions | - | ✅ adminPermissions |
| **管理员管理** | GET /api/admin/admins | - | ✅ listAdmins |
| | POST /api/admin/admins | - | ✅ createAdmin |
| | PUT /api/admin/admins/{id} | - | ✅ updateAdmin |
| | DELETE /api/admin/admins/{id} | - | ✅ deleteAdmin |
| | PATCH /api/admin/admins/{id}/status | - | ✅ toggleAdminStatus |
| | PATCH /api/admin/admins/{id}/password | - | ✅ resetAdminPassword |
| **用户管理** | GET /api/admin/users | - | ✅ listUsers |
| | GET /api/admin/users/{id} | - | ✅ getUserDetail |
| | PATCH /api/admin/users/{id}/status | - | ✅ toggleUserStatus |
| | POST /api/admin/users/{id}/sessions/force-logout | - | ✅ forceLogout |
| **角色权限** | GET /api/admin/roles | - | ✅ listRoles |
| | POST /api/admin/roles | - | ✅ createRole |
| | PUT /api/admin/roles/{id} | - | ✅ updateRole |
| | DELETE /api/admin/roles/{id} | - | ✅ deleteRole |
| | GET /api/admin/permissions | - | ✅ listPermissions |
| **认证配置** | GET /api/admin/auth-config | - | ✅ getAuthConfig |
| | PUT /api/admin/auth-config | - | ✅ updateAuthConfig |
| **操作日志** | GET /api/admin/operation-logs | - | ✅ listOperationLogs |
| | GET /api/admin/operation-logs/export | - | ✅ exportOperationLogs |

**小计**: 35个API端点已完成对接

---

### 清单 B：后端已有 API 但前端仍用 Mock（❌ 可立即对接 —— 重点缺口！）

**当前无此情况。** 后端API已有的功能，前端要么已对接，要么是消费端功能（后端暂未提供订单/商品/支付等API）。

---

### 清单 C：前端页面需要数据但后端无对应 API（⚠️ 需先开发后端）

#### C1. 消费端缺口

| 功能 | 所需端点 | 前端现状 | 优先级 |
|------|--------|--------|------|
| **订单管理** | GET /api/store/orders | Mock数组 | 🔴 高 |
| | GET /api/store/orders/{id} | Mock查询 | 🔴 高 |
| | POST /api/store/orders | Mock状态 | 🔴 高 |
| **地址簿** | GET /api/store/addresses | Mock数组 | 🟡 中 |
| | POST /api/store/addresses | Mock | 🟡 中 |
| | PUT /api/store/addresses/{id} | Mock | 🟡 中 |
| | DELETE /api/store/addresses/{id} | Mock | 🟡 中 |
| **购物车** | GET /api/store/cart | 本地状态 | 🟡 中 |
| | POST /api/store/cart/items | 本地状态 | 🟡 中 |
| **收藏** | GET /api/store/wishlists | 本地状态 | 🟢 低 |
| | POST /api/store/wishlists/{productId} | 本地状态 | 🟢 低 |
| **商品** | GET /api/store/products | 本地 `data/products.ts` | 🔴 高 |
| | GET /api/store/products/{slug} | 本地 | 🔴 高 |
| | GET /api/store/products/search | 待实现 | 🔴 高 |
| **内容** | GET /api/store/blogs | Mock | 🟡 中 |
| | GET /api/store/weddings | Mock | 🟡 中 |

#### C2. 管理端缺口

| 功能 | 所需端点 | 现状 | 优先级 |
|------|--------|------|------|
| **商品管理** | GET /api/admin/products | Mock数组 | 🔴 高 |
| | POST /api/admin/products | Mock | 🔴 高 |
| | PUT /api/admin/products/{id} | Mock | 🔴 高 |
| | DELETE /api/admin/products/{id} | Mock | 🔴 高 |
| | GET /api/admin/categories | Mock | 🔴 高 |
| **订单管理** | GET /api/admin/orders | Mock数组 | 🔴 高 |
| | GET /api/admin/orders/{id} | Mock | 🔴 高 |
| | PATCH /api/admin/orders/{id}/status | Mock | 🔴 高 |
| | GET /api/admin/refunds | Mock | 🔴 高 |
| **内容管理** | GET /api/admin/content/blogs | Mock | 🟡 中 |
| | POST /api/admin/content/blogs | Mock | 🟡 中 |
| | GET /api/admin/content/weddings | Mock | 🟡 中 |
| **营销** | GET /api/admin/promotions | Mock | 🟡 中 |
| | POST /api/admin/promotions | Mock | 🟡 中 |
| | GET /api/admin/banners | Mock | 🟡 中 |
| **配置** | GET /api/admin/shipping | Mock | 🟡 中 |
| | PUT /api/admin/shipping | Mock | 🟡 中 |
| | GET /api/admin/navigation | Mock | 🟡 中 |
| **数据看板** | GET /api/admin/analytics/overview | Mock | 🟢 低 |
| | GET /api/admin/analytics/gmv-trend | Mock | 🟢 低 |

---

## 5. 缓存现状

### 5.1 JetCache 配置

**启用情况**: ✅ 已启用
- **配置文件**: `backend/src/main/resources/application.yml`
- **注解驱动**: `src/main/java/com/dreamy/identity/config/JetCacheConfig.java`

**配置详情**:
```yaml
jetcache:
  statIntervalMinutes: 0
  areaInCacheName: false
  local:
    default:
      type: caffeine
      keyConvertor: fastjson2
      expireAfterWriteInMillis: 300000  # 5分钟
  remote:
    default:
      type: redis.lettuce
      keyConvertor: fastjson2
      valueEncoder: java
      valueDecoder: java
      expireAfterWriteInMillis: 300000  # 5分钟
      uri: redis://localhost:6379
```

**缓存策略**: 两级缓存（本地Caffeine + 远程Redis）

### 5.2 @Cached 注解使用情况

| Service | 方法 | 缓存key | 缓存类型 | TTL | 说明 |
|---------|------|--------|--------|-----|------|
| **AuthConfigService** | `getConfig()` | `store:authconfig:singleton` | BOTH | 600s | 认证配置单例 |
| **IdentityService** | `getProfile(userId)` | `store:user:{userId}` | BOTH | 300s | 用户资料 |
| **IdentityService** | `listIdentities(userId)` | `store:identities:{userId}` | BOTH | 300s | 登录方式列表 |

### 5.3 @CacheInvalidate 注解使用情况

| Service | 方法 | 失效缓存key | 触发时机 |
|---------|------|-----------|--------|
| **AuthConfigService** | `updateConfig()` | `store:authconfig:singleton` | 更新认证配置时 |
| **IdentityService** | `bindIdentity()` | `store:identities:{userId}` | 绑定登录方式时 |
| **IdentityService** | `unbindIdentity()` | `store:identities:{userId}` | 解绑登录方式时 |
| **IdentityService** | `changePrimaryEmail()` | `store:identities:{userId}` | 换主邮箱时 |
| **IdentityService** | `deleteAccount()` | `store:identities:{userId}` | 注销账户时 |
| **IdentityService** | `updateProfile()` | `store:user:{userId}` | 更新资料时 |

### 5.4 Redis 配置

**启用情况**: ✅ 已启用
- **Host**: localhost
- **Port**: 6379
- **Database**: 0（默认）
- **驱动**: Redisson（分布式锁 + 健康检查）

**配置位置**: `application.yml` 中 `spring.data.redis` 和 `huihao.redis` 两处定义

---

## 6. 总结与建议

### 6.1 整体评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **后端 API 完整性** | ⭐⭐⭐⭐⭐ | 身份认证系统完全实现（35个端点） |
| **管理端对接度** | ⭐⭐⭐⭐ | 核心功能（7个页面）已对接；商业逻辑（18个页面）待后端支持 |
| **消费端对接度** | ⭐⭐⭐ | 认证系统完整；订单/商品/支付系统待实现 |
| **缓存策略** | ⭐⭐⭐⭐⭐ | 两级缓存配置完整，写失效机制完善 |

### 6.2 立即行动项（可在下一迭代完成）

#### 🔴 高优先级（消费端，直接影响用户功能）
1. **订单系统 API** — 需实现 `/api/store/orders*` 端点
   - 订单列表、详情、创建、追踪
   - 预计工作量: **2-3 天**

2. **商品 API** — 需实现 `/api/store/products*` 端点
   - 商品列表、搜索、详情、分类
   - 与管理端 `/api/admin/products` 共享后端数据
   - 预计工作量: **3-4 天**

3. **地址簿 API** — 需实现 `/api/store/addresses*` 端点
   - 预计工作量: **1 天**

#### 🔴 高优先级（管理端，直接影响运营功能）
1. **商品管理 API** — 需实现 `/api/admin/products*` 端点
   - 与消费端共享数据模型
   - 预计工作量: **3-4 天**

2. **订单管理 API** — 需实现 `/api/admin/orders*`, `/api/admin/refunds` 等
   - 预计工作量: **3-5 天**

3. **支付/金融 API** — 需实现支付相关端点
   - 预计工作量: **待评估**

#### 🟡 中优先级（丰富内容）
1. **内容管理 API** — `/api/admin/content/*`, `/api/store/content/*`
   - 博客、婚礼案例、穿搭指南
   - 预计工作量: **2-3 天**

2. **营销配置 API** — `/api/admin/promotions`, `/api/admin/banners` 等
   - 优惠券、闪购、Banner管理
   - 预计工作量: **2-3 天**

### 6.3 前端对接检查清单

```
消费端对接：
☑ 认证系统完整 (/account/login, security, settings)
☐ 订单系统对接（page: /account/orders/* ）
☐ 地址簿对接（page: /account/addresses）
☐ 商品API对接（data/products.ts → API）
☐ 支付集成（/checkout）

管理端对接：
☑ 身份认证系统完整 (admins, users, roles, auth-config, operation-logs)
☐ 商品管理对接（Dashboard.vue, Products.vue等）
☐ 订单管理对接（Orders.vue, OrderDetail.vue, Refunds.vue）
☐ 内容管理对接（ContentBlog.vue等）
☐ 营销配置对接（Promotions.vue, Banners.vue等）
☐ 数据看板对接（Analytics.vue, Dashboard.vue）
```

### 6.4 后端开发建议

1. **按优先级排队**: 订单 > 商品 > 支付 > 内容 > 营销
2. **复用现有模式**:
   - 使用 identity-api 的 R<T> 包络、错误处理、权限控制模式
   - 应用 JetCache 两级缓存（资料/配置类600s，交易类按业务）
   - 遵守 snake_case API 约定
3. **前后端协作**:
   - 定义 OpenAPI spec（如同identity-api.openapi.yml）
   - 前端 lib/api 同步编写包装层
4. **测试覆盖**:
   - 集成测试覆盖常见流程（如 identity 有 StoreAuthControllerTest）
   - API 契约测试确保前后端一致

---

## 7. 附录：文件清单

### 后端 Controllers
```
backend/src/main/java/com/dreamy/identity/controller/
├── StoreAuthController.java         (11个 /api/store/auth/* 端点)
├── AccountController.java           (6个 /api/store/account/* 端点)
├── AdminAuthController.java         (10个 /api/admin/auth*, /api/admin/admins* 端点)
├── UserOpsController.java           (4个 /api/admin/users/* 端点)
├── RoleController.java              (5个 /api/admin/roles, /api/admin/permissions 端点)
└── AuthConfigController.java        (5个 /api/admin/auth-config, /api/admin/operation-logs 端点)
```

### 消费端 API层
```
frontend/portal-store/lib/api/
├── client.ts                        (核心请求/续期/转换)
├── auth-api.ts                      (11个认证接口)
├── token-store.ts                   (JWT存储)
├── types.ts                         (响应类型)
└── case.ts                          (camelCase↔snake_case)
```

### 管理端 API层
```
frontend/portal-admin/src/api/
├── client.ts                        (axios + token + 转换)
├── auth.ts                          (4个认证接口)
├── admins.ts                        (6个管理员接口)
├── users.ts                         (4个用户接口)
├── roles.ts                         (5个角色权限接口)
├── logs.ts                          (2个日志接口)
├── authConfig.ts                    (2个认证配置接口)
└── types.ts                         (响应类型定义)
```

### 缓存配置
```
backend/
├── src/main/resources/application.yml   (JetCache + Redis配置)
└── src/main/java/com/dreamy/identity/config/JetCacheConfig.java
```

---

**报告完成于 2026-06-10**
