# 变更提案：登录方式开关全面可开闭（email OTP 解除恒开锁定）

- **日期**: 2026-09-20
- **来源**: 用户决策 —— 「邮箱验证码（Passwordless）这个我认为是可以打开关闭的」
- **状态**: 已实施

## 背景与决策

原设计（REQ-IDENTITY-004 / domain-model / identity-api.openapi / identity-ddl）规定
`auth_config.email_enabled` **恒开不可关**（email 为主登录方式，防止三种方式全关导致消费端被锁死）。

用户决策（2026-09-20）：

1. **email 验证码登录改为可开闭**，与 google/apple 同等对待（FUNC-006 的关闭语义扩展到 email）。
2. **允许全关、不加护栏**：不做「至少保留一种登录方式」的硬校验，管理员自行评估锁死风险。

## 变更内容

### 语义变化

| 项 | 原 | 新 |
|---|---|---|
| `email_enabled` | 恒 true，服务端强制，不可提交 | bool 必填，可开闭，允许 false |
| 全关（email+google+apple 均 false） | 不可能 | 允许保存；消费端登录页展示「登录方式暂不可用」提示 |
| `sendOtp` 当 `email_enabled=false` | 守卫不可达 | 返回 403 `40303 PROVIDER_DISABLED`（与 OIDC 关闭同码） |
| AuthSettings email 开关 | locked，title「主登录方式不可关闭」 | 可切换，无锁定标记 |
| seed/DDL 默认值 | 1 | 1（不变，默认仍为开） |

### 触达文件

**规范（hhspec/specs）**
- requirements/identity/REQ-IDENTITY-004：FUNC-023 描述
- architecture/domain-model.md：AuthConfig 不变量
- architecture/api-contracts/identity-api.openapi.yml：PUT /api/admin/auth-config 描述、AuthConfig/AuthConfigUpdate schema
- design/identity/identity-api-detail.md：STEP-04（email_enabled=false → 40303）、V-CFG
- design/identity/identity-error-detail.md：EX-10 语义、sendOtp 40303 映射
- design/identity/identity-frontend-detail.md：COMP-A07
- design/identity/identity-ui-test-spec.yml：UIS-A06
- design/identity/requirements-traceability-matrix.yml：FUNC-006 标题
- design/data/identity-physical-schema.md、identity-ddl.sql：email_enabled 注释、引用完整性

**实现**
- server/crates/identity：`AuthConfigUpdate` 增加 `email_enabled` 字段（update 应用 + admin handler 解析）
- backend（Java）：`AuthConfigUpdateRequest` 增加 `emailEnabled`（auth-config Controller/Service 已于 2026-09-17 移除，DTO 保持契约对齐）
- frontend/portal-admin：AuthSettings.vue（email 开关可切换、提交 emailEnabled）、api/types.ts、api/authConfig.ts
- frontend/portal-store：login-card.tsx（emailEnabled=false 隐藏邮箱表单；全关时展示提示）、i18n messages（en/es/fr 新增 `login.allMethodsDisabled`）

**原型（hhspec/prototype）**
- portal-admin/src/data/mock.js：email 方法 `locked:false`
- portal-admin/src/views/AuthSettings.vue：移除「主登录」锁定标记与禁用态

### 2026-09-20 晚补充修复

1. **登录页 email 门控缺口**：首轮实现只在「三种方式全关」时整体替换为提示，email 单独关闭时 `EmailStep` 仍渲染邮箱表单（后端 40303 拦截，但 UI 未隐藏）。已在 `EmailStep` 内对邮箱表单按 `emailEnabled` 条件渲染，关闭时展示 `t.login.emailDisabled`（en/es/fr）。
2. **服务重启与密钥**：首次重启用 `.env.deploy` 密钥导致既有浏览器令牌验签 401；已改回 `.env.backend.local`（本地原生直起的密钥来源，与旧进程一致）重启，E2E 复验 email 开关存库（0/1 往返）与登录页门控生效。
3. **消费端配置请求走错后端（根因 Bug 3 复现）**：`frontend/portal-store/.env.local` 配置了 `NEXT_PUBLIC_API_BASE_URL=http://localhost:18081`，浏览器绕过同源代理直连 Java 18081（identity 不在 Java 上，返回 40100），前端 `load()` 捕获后静默降级为 FALLBACK（`emailEnabled:true`），导致"开关全关但登录页仍显示表单"。已移除该变量（留空走同源 fetch，由 `middleware.ts` 把 `/api/store/auth|account` 重写到 `SERVER_ORIGIN`=Rust 18082）。`NEXT_PUBLIC_*` 是启动时烤进 bundle 的，改后必须重启 dev server。无头浏览器 Playwright 复验：`/api/store/auth/config` 200 且全 false，页面渲染「Sign-in is temporarily unavailable」全关提示，邮箱表单与 OAuth 按钮均不再出现。附带恢复：Java 18081 经 `scripts/backend-api.sh` 重启（此前被 SIGTERM 一并带下，曾致 exchange-rates 500）。
4. **移除配置降级 fallback（fail-closed）**：按用户要求干掉前端配置降级——`auth-config-store.ts` 删除 FALLBACK 常量，拉取失败时 `config` 保持 null 并置 `loadError`；`login-card.tsx` 的 `emailEnabled` 兜底从 `?? true` 改为 `?? false`（fail-closed），全关分支按 `loadError` 展示新增的 `t.login.configUnavailable`（en/es/fr）。验证：配置正常返回全 false → 全关提示、无邮箱表单；Playwright 拦截配置请求模拟失败 → 无邮箱表单、显示「Sign-in options could not be loaded. Please refresh the page and try again.」，故障可见。tsc ✅ vitest 22/22 ✅。其余 `fallback` 命中（Suspense 占位、商品色板兜底色、错误文案兜底）为普通 UI 模式，非配置降级，未改动。

### 明确不做（Future）

- 「至少保留一种登录方式」硬校验或二次确认对话框（用户明确选择不加护栏）
- store 原型（hhspec/prototype/app/account/login）的门控演示：该原型为纯视觉 mock，无 auth-config 模拟源；门控行为以真实前端（frontend/portal-store）为准

## 验收

- [x] 超管可在 AuthSettings 将 email 开关置为关并保存成功（写 action=认证配置变更 日志）
- [x] email 关闭后：消费端登录页不展示邮箱表单；直接调 `POST /api/store/auth/otp/send` 返回 403 40303
- [x] 三种方式全关可保存；登录页展示「登录方式暂不可用」（i18n：en/es/fr）
- [x] seed/DDL 默认 email_enabled=1 不变，存量环境无需数据迁移
