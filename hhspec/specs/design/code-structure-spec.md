# 代码包结构规范 — identity（code-structure-spec.md）

> 由 l2_design_coordinator 基于 L1 领域边界 + tech-profile 生成（Bootstrap）。L3 编码目录定位依据。
> change: identity-auth-fullstack ｜ 三工程拓扑（decision 决策1）。

## 1. 顶层工程拓扑
```
<project-root>/
├── backend/                      # Spring Boot 多模块 single-jar（Gradle）
│   ├── common/                   # IDENTITY-COMMON + COMMON（领域核心 + 技术基础设施）
│   ├── store/                    # IDENTITY-STORE 表现层（/api/store/*, port 内部）
│   └── admin/                    # IDENTITY-ADMIN 表现层（/api/admin/*）
├── frontend/
│   ├── portal-store/             # Next.js 15 App Router（port 5173, EN/ES/FR）
│   └── portal-admin/             # Vue3 + Vite + Pinia（port 5174, 中文）
└── scripts/
```
依赖：store→common、admin→common（单向，无环）；store↔admin 互不依赖。

## 2. backend 包路径约定（com.dreamy.identity）
```
common/src/main/java/com/dreamy/identity/
├── domain/        # 聚合根/实体/值对象（User/UserIdentity/OtpCode/AuthConfig/AdminUser/Role/...）
├── domain/service/# 领域服务（IdentityService/SessionService/OtpService/MergeService/AuthConfigService/AdminService/RoleService/AuditService/RetentionScheduler）
├── repository/    # Repository 接口 + MyBatis-Plus Mapper（RM-* 方法）
├── repository/entity/  # MyBatis-Plus Entity（@TableName 对应 13 表）
├── infra/         # JWT 工具(双密钥)/JetCache 配置/SMTP 端口/OIDC 端口/脱敏日志/GlobalExceptionHandler
├── error/         # 异常类(EX-*)/错误码枚举/i18n messages
└── i18n/          # messages_{en,es,fr}.properties + email_template 加载

store/src/main/java/com/dreamy/identity/store/
├── controller/    # StoreAuthController/AccountController/StoreConfigController
├── dto/           # store 请求/响应 DTO
├── filter/        # store JWT 鉴权过滤器
└── config/        # store CORS(5173)/缓存配置

admin/src/main/java/com/dreamy/identity/admin/
├── controller/    # AdminAuthController/AdminController/RoleController/UserOpsController/AuthConfigController/OperationLogController
├── dto/
├── filter/        # admin JWT 鉴权过滤器 + RBAC 守卫
├── aspect/        # 操作审计 AOP 切面(FLOW-17)
└── config/        # admin CORS(5174)
```

## 3. 前端目录约定
```
frontend/portal-store/app/account/{login,page,settings,security}/   # PAGE-S01~04
frontend/portal-store/lib/                # api client(401→refresh)/authStore(zustand)/i18n(next-intl)/error code map
frontend/portal-store/components/         # COMP-S01~10

frontend/portal-admin/src/views/{Login,Customers,CustomerDetail,AdminList,RoleManagement,AuthSettings,OperationLogs}.vue  # PAGE-A01~07
frontend/portal-admin/src/stores/         # Pinia: auth/menu/users/admins/roles/logs
frontend/portal-admin/src/router/         # 守卫 GUARD-01~04(meta.permission)
frontend/portal-admin/src/components/     # COMP-A01~08
frontend/portal-admin/src/api/            # api client(中文错误)
```

## 4. 新增模块模板
- 新增表现层端点 → 落 store/ 或 admin/ controller，不得跨模块直接依赖（领域逻辑下沉 common/domain/service）。
- 新实体 → common/repository/entity + repository 接口 + domain。
- store/admin JWT 密钥、claims、过期策略保持独立。

## 5. 数据库
- DDL：design/data/identity-ddl.sql（13 表 + 种子：超管账户/超管角色/22 permission/auth_config 单例/12 email_template）。
- 迁移工具 L3 选型（Flyway/Liquibase 推断）。
