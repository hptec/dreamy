---
layer: L3
role: l3_implementer
change_name: identity-auth-fullstack
unit_id: unit_portal_store
unit_name: portal-store 消费端商城前端（Next.js 15 App Router）
codebase_path: /Volumes/MAC/workspace/dreamy
output_dir: frontend/portal-store
tech_stack: { framework: "Next.js 15.0.3 (App Router, output=export)", lang: "TypeScript 5.6", styling: "Tailwind 3.4", state: "zustand 5 + React Context", pkg_manager: "pnpm@10.32.1" }
backend_contract:
  base_url: "http://localhost:8080"
  api_prefix: "/api/store"
  json_naming: "snake_case (Jackson SNAKE_CASE 双向) ↔ camelCase (前端边界转换)"
  jwt: "access 2h(内存) + refresh 30d(localStorage 滑动续期)"
  auth_config_endpoint: "GET /api/store/auth/config (实测路径，非设计文档的 /config/auth)"
status: implemented
copy_mode: true
proto_is_frontend: true
coverage:
  total_constraints: 35
  implemented: 35
  blocked: 0
  percentage: 100
build_status:
  command: "pnpm install && pnpm build"
  install: pass
  build: pass
  routes_generated: 51
  account_pages_built: [/account, /account/login, /account/security, /account/settings]
  type_check: pass
  lint: pass
  exit_code: 0
---

# Traceability Map — unit_portal_store

> L3 Implementer 产出。原型复制+适配模式：整工程骨架复制自 `hhspec/prototype/`，
> 4 个 account 身份页 mock 数据替换为真实 `/api/store/*` 调用。其余商城页保持原型态可 build。

## 1. 页面路由（PAGE-S*）

| constraint_id | status | impl_location | description |
|---------------|--------|---------------|-------------|
| PAGE-S01 | implemented | frontend/portal-store/app/account/login/page.tsx:1-56 | /account/login 登录页；LoginCard + OIDC fragment 回调处理（sendOtp/verifyOtp/oidcCallback/getStoreAuthConfig） |
| PAGE-S02 | implemented | frontend/portal-store/app/account/page.tsx:1-38 | /account 主页；authStore.user 渲染资料（getProfile，AuthGuard hydrate 拉取）；未登录重定向 |
| PAGE-S03 | implemented | frontend/portal-store/app/account/settings/page.tsx:1-79 | /account/settings 设置；只读资料 + passwordless 说明（无改密入口）+ 删除账户入口 |
| PAGE-S04 | implemented | frontend/portal-store/app/account/security/page.tsx:1-12 | /account/security 安全；委托 SecurityPanel（凭证/会话/换主邮箱/登出设备） |

## 2. 组件树（COMP-S*）

| constraint_id | status | impl_location | description |
|---------------|--------|---------------|-------------|
| COMP-S01 | implemented | frontend/portal-store/components/account/login-card.tsx:25-179 | LoginCard 两步 step='email'\|'code' |
| COMP-S02 | implemented | frontend/portal-store/components/account/login-card.tsx:182-258 | EmailStep email 输入 + Email me a code + OAuth 按钮（按 authConfig 显隐） |
| COMP-S03 | implemented | frontend/portal-store/components/account/otp-input.tsx:1-93 | OtpStep 6 格 OTP（自动跳格/退格/粘贴/满位提交） + login-card CodeStep 倒计时/剩余次数 |
| COMP-S04 | implemented | frontend/portal-store/components/account/oauth-buttons.tsx:1-85 | OAuthButtons Google/Apple；502/504→onError 提示改 OTP（DG-001） |
| COMP-S05 | implemented | frontend/portal-store/components/account/security-panel.tsx:1-210 | SecurityPanel 组合凭证/会话/换主邮箱 |
| COMP-S06 | implemented | frontend/portal-store/components/account/identity-list.tsx:1-103 | IdentityList provider 图标/identifier/is_primary 徽章/relay 失效提示 + 解绑按钮 |
| COMP-S07 | implemented | frontend/portal-store/components/account/session-list.tsx:1-102 | SessionList device/browser/location/is_new_device/current + 登出此/其他设备 |
| COMP-S08 | implemented | frontend/portal-store/components/account/change-primary-dialog.tsx:1-165 | ChangePrimaryDialog new_email + OTP 校验 |
| COMP-S09 | implemented | frontend/portal-store/components/account/delete-account-dialog.tsx:1-95 | DeleteAccountDialog 危险二次确认（键入 DELETE，confirm:true） |
| COMP-S10 | implemented | frontend/portal-store/components/account/profile-view.tsx:1-72 | ProfileView 只读资料展示（name/email/tier/avatar/phone/joinedAt） |

## 3. 状态管理与 lib（STORE-S*）

| constraint_id | status | impl_location | description |
|---------------|--------|---------------|-------------|
| STORE-S01 | implemented | frontend/portal-store/lib/stores/auth-store.ts:1-98 | authStore { user, isAuthenticated, login(), logout(), refresh(), hydrate() }；refresh 失败→未认证态 |
| STORE-S02 | implemented | frontend/portal-store/lib/stores/auth-config-store.ts:1-46 | authConfigStore 缓存 getStoreAuthConfig；失败降级仅 email（隐藏 OAuth） |
| STORE-S03 | implemented | frontend/portal-store/lib/api/token-store.ts:1-118 ; frontend/portal-store/lib/api/client.ts:96-181 | access 内存 + refresh localStorage；401→自动 refresh→重放（单飞续期） |
| STORE-S04 | implemented | frontend/portal-store/lib/i18n/i18n-context.tsx:1-86 ; frontend/portal-store/lib/i18n/error-messages.ts:1-110 ; frontend/portal-store/lib/i18n/messages.ts:1-411 | next-intl 等价 i18n（Context，静态导出形态）；locale en/es/fr；错误 code→三语映射 |

<!-- section 4 -->

## 4. 表单交互（FORM-S*）

| constraint_id | status | impl_location | description |
|---------------|--------|---------------|-------------|
| FORM-S01 | implemented | frontend/portal-store/components/account/login-card.tsx:62-95 | OTP 发送：email 正则预校验(V-001)→sendOtp→切 code 步+倒计时；429 显剩余秒禁用重发 |
| FORM-S02 | implemented | frontend/portal-store/components/account/login-card.tsx:97-128 | OTP 校验：满位自动 verifyOtp；401 显剩余次数(error-text)；410 引导重发；成功存 token 跳 /account |
| FORM-S03 | implemented | frontend/portal-store/components/account/oauth-buttons.tsx:28-66 ; frontend/portal-store/app/account/login/page.tsx:16-43 | OIDC：授权跳转/stub + 回调 id_token→oidcCallback；409/502/504 回登录页引导 OTP |
| FORM-S04 | implemented | frontend/portal-store/components/account/identity-list.tsx:43-50 ; frontend/portal-store/components/account/security-panel.tsx:69-82 | 解绑：is_primary 前端禁用解绑按钮(EDGE-007 预判)；403 40305→提示至少保留一种 |
| FORM-S05 | implemented | frontend/portal-store/components/account/change-primary-dialog.tsx:64-110 | 换主邮箱：new_email→发 OTP→校验→changePrimaryEmail；409 40901 占用提示 |
| FORM-S06 | implemented | frontend/portal-store/components/account/delete-account-dialog.tsx:24-46 | 注销：键入 DELETE 二次确认→deleteAccount→清 token 跳首页 |

## 5. API 端点封装（对照 backend/store 真实路由）

| 后端端点（实测） | impl_location | 约束 |
|------------------|---------------|------|
| POST /api/store/auth/otp/send | frontend/portal-store/lib/api/auth-api.ts:18-23 | FUNC-001 sendOtp |
| POST /api/store/auth/otp/verify | frontend/portal-store/lib/api/auth-api.ts:26-31 | FUNC-002 verifyOtp |
| POST /api/store/auth/oidc/{provider}/callback | frontend/portal-store/lib/api/auth-api.ts:34-44 | FUNC-004/005 oidcCallback |
| POST /api/store/auth/refresh | frontend/portal-store/lib/api/client.ts:103-128 | FUNC-030 refreshToken |
| GET /api/store/auth/config | frontend/portal-store/lib/api/auth-api.ts:47-49 | FUNC-003 getStoreAuthConfig |
| GET /api/store/account/profile | frontend/portal-store/lib/api/auth-api.ts:54-56 | FUNC-007 getProfile |
| GET /api/store/account/identities | frontend/portal-store/lib/api/auth-api.ts:59-64 | FUNC-010 listIdentities |
| POST /api/store/account/identities/bind | frontend/portal-store/lib/api/auth-api.ts:67-80 | FUNC-008 bindIdentity |
| DELETE /api/store/account/identities/{id} | frontend/portal-store/lib/api/auth-api.ts:83-87 | FUNC-009 unbindIdentity |
| POST /api/store/account/email/change-primary | frontend/portal-store/lib/api/auth-api.ts:90-96 | FUNC-026 changePrimaryEmail |
| GET /api/store/account/sessions | frontend/portal-store/lib/api/auth-api.ts:99-104 | FUNC-011/013 listSessions |
| DELETE /api/store/account/sessions/{id} | frontend/portal-store/lib/api/auth-api.ts:107-111 | FUNC-012 revokeSession |
| DELETE /api/store/account/sessions/others | frontend/portal-store/lib/api/auth-api.ts:114-116 | FUNC-012 revokeOtherSessions |
| POST /api/store/account/delete | frontend/portal-store/lib/api/auth-api.ts:119-124 | FUNC-027 deleteAccount |

## 6. 边界转换与错误映射

| constraint_id | status | impl_location | description |
|---------------|--------|---------------|-------------|
| CASE-CONV | implemented | frontend/portal-store/lib/api/case.ts:1-66 | snake_case↔camelCase 深度转换（shared-contracts field_mapping_rule） |
| ERR-MAP | implemented | frontend/portal-store/lib/i18n/error-messages.ts ; frontend/portal-store/lib/i18n/error-text.ts | 24 错误码三语映射 + details 增强（40101 剩余次数/42901 剩余秒）；未知 code 兜底 50000 |
| ACCEPT-LANG | implemented | frontend/portal-store/lib/api/client.ts:48-58 | 每请求附 Accept-Language(en/es/fr)（shared-contracts cors allowed_headers） |
| AUTH-GUARD | implemented | frontend/portal-store/components/account/auth-guard.tsx:1-50 | 受保护页守卫：hydrate→未认证 router.replace(/account/login)（UIS-S02） |

## 7. UI 测试期望对照（identity-ui-test-spec.yml portal-store 4 页）

| ui_test_id | 关键断言 | impl 对应 | status |
|------------|---------|-----------|--------|
| UIS-S01 | email→code 步切换；6 格 OTP 聚焦首格；重发倒计时；非法邮箱报错不切步；OAuth 按 config 显隐；满 6 位提交 | login-card.tsx + otp-input.tsx + oauth-buttons.tsx | MATCH |
| UIS-S02 | 已登录展示 name/email/tier/avatar；未登录重定向 /account/login | page.tsx + profile-view.tsx + auth-guard.tsx | MATCH |
| UIS-S03 | 资料字段展示；form labels | settings/page.tsx | MATCH |
| UIS-S04 | 凭证列表(provider/identifier/primary 徽章/relay)；主邮箱禁解绑；至少一种(40305)；会话列表(current/new_device)；登出其他设备二次确认；换主邮箱 409；注销危险确认 | security-panel.tsx + identity-list.tsx + session-list.tsx + change-primary-dialog.tsx + delete-account-dialog.tsx + dialog.tsx | MATCH |

## 8. 无障碍（UIS accessibility 规则）

| 规则 | impl_location |
|------|---------------|
| email label / otp aria-label / error aria-live | login-card.tsx (htmlFor, role=alert aria-live) ; otp-input.tsx (aria-label per digit) |
| dialog role + focus-trap + Esc | dialog.tsx:1-110 (role=dialog aria-modal, Tab 陷阱, Esc 关闭) |
| 危险操作 confirm focus | delete-account-dialog.tsx (键入 DELETE 才启用) |

## 9. prototype_conformance（原型文案精确核对）

> content-index.json 不存在；以原型 EN 文案为基准，逐条核对复制后代码 EN 词典（lib/i18n/messages.ts en）。
> 所有页面文案通过 i18n 词典渲染，EN 基准值与原型严格一致。

```yaml
prototype_conformance:
  - component: login-card.tsx (PAGE-S01 from app/account/login/page.tsx)
    page_id: login
    prototype_file: hhspec/prototype/app/account/login/page.tsx
    items:
      - element_type: header
        prototype_text: "Sign in or create account"
        impl_text: "Sign in or create account"   # messages.ts en.login.signInTitle
        status: MATCH
      - element_type: text
        prototype_text: "Enter your email and we'll send you a 6-digit code. No password needed."
        impl_text: "Enter your email and we'll send you a 6-digit code. No password needed."
        status: MATCH
      - element_type: button
        prototype_text: "Continue with Google"
        impl_text: "Continue with Google"
        status: MATCH
      - element_type: button
        prototype_text: "Continue with Apple"
        impl_text: "Continue with Apple"
        status: MATCH
      - element_type: label
        prototype_text: "Email"
        impl_text: "Email"
        status: MATCH
      - element_type: button
        prototype_text: "Email me a code"
        impl_text: "Email me a code"
        status: MATCH
      - element_type: header
        prototype_text: "Check your email"
        impl_text: "Check your email"
        status: MATCH
      - element_type: button
        prototype_text: "Verify & continue"
        impl_text: "Verify & continue"
        status: MATCH
      - element_type: text
        prototype_text: "Apple may hide your email with a private relay address — you can still sign in."
        impl_text: "Apple may hide your email with a private relay address — you can still sign in."
        status: MATCH
  - component: security-panel.tsx (PAGE-S04 from app/account/security/page.tsx)
    page_id: security
    prototype_file: hhspec/prototype/app/account/security/page.tsx
    items:
      - element_type: header
        prototype_text: "Login & Security"
        impl_text: "Login & Security"
        status: MATCH
      - element_type: text
        prototype_text: "Manage how you sign in and the devices connected to your account."
        impl_text: "Manage how you sign in and the devices connected to your account."
        status: MATCH
      - element_type: header
        prototype_text: "Login methods"
        impl_text: "Login methods"
        status: MATCH
      - element_type: header
        prototype_text: "Devices & sessions"
        impl_text: "Devices & sessions"
        status: MATCH
      - element_type: button
        prototype_text: "Sign out other devices"
        impl_text: "Sign out other devices"
        status: MATCH
      - element_type: badge
        prototype_text: "This device"
        impl_text: "This device"
        status: MATCH
      - element_type: text
        prototype_text: "Keep at least one login method connected. Your primary email stays verified and cannot be removed."
        impl_text: "Keep at least one login method connected. Your primary email stays verified and cannot be removed."
        status: MATCH
  - component: settings/page.tsx (PAGE-S03)
    page_id: settings
    prototype_file: hhspec/prototype/app/account/settings/page.tsx
    items:
      - element_type: header
        prototype_text: "Settings"
        impl_text: "Settings"
        status: MATCH
      - element_type: label
        prototype_text: "Full Name"
        impl_text: "Full Name"
        status: MATCH
      - element_type: text
        prototype_text: "Changing your email requires re-verification with a one-time code."
        impl_text: "Changing your email requires re-verification with a one-time code."
        status: MATCH
      - element_type: text
        prototype_text: "Passwordless account"
        impl_text: "Passwordless account"
        status: MATCH
      - element_type: link
        prototype_text: "Login & Security"
        impl_text: "Login & Security"
        status: MATCH
      - element_type: section
        prototype_text: "(原型设置页无注销入口；本变更按 PAGE-S03/FUNC-027 新增 Danger zone 删除账户)"
        impl_text: "Delete account / Danger zone"
        status: ADDED_BY_DESIGN   # L2 PAGE-S03 要求含注销/删除，原型缺失，按设计补充
```

## 10. 设计偏差说明（适配决策，非自由发挥）

| 项 | 设计原文 | 实现 | 依据 |
|----|---------|------|------|
| authConfig 端点 | identity-api-detail 写 GET /api/store/config/auth | 用 GET /api/store/auth/config | backend/store StoreAuthController 实测路由（证据优先 E-01，以后端实现为准） |
| i18n 库 | A.2 STORE-S04 next-intl | React Context + 三语词典 | 工程为 output:export 静态 SPA，next-intl middleware 需 server；采用等价 Context 实现（设计允许"或等价"） |
| token 持久化 | A.2 STORE-S03 httpOnly cookie/secure storage | access 内存 + refresh localStorage | 后端以 JSON body 下发 token（非 Set-Cookie），故按"secure storage"分支实现 |
| OIDC stub | — | 无 OAuth client 配置时走 stub id_token | 后端 OIDC_MODE=stub（application.yml）；保证端到端可联调，不发明新接口 |
| settings 注销入口 | PAGE-S03 含账户注销/删除 | 新增 Danger zone + DeleteAccountDialog | 原型 settings 页无此入口，按 L2 PAGE-S03/FUNC-027 补齐 |

## 11. 验收核对（UI 4 页 FUNC 对接情况）

- 全部 4 个 account 页（PAGE-S01~04）均接入真实 `/api/store/*`，无 mock/setTimeout 占位。
- profile/identities/sessions 数据来自后端；login/verify/oidc/delete/unbind/revoke/change-primary 均为真实调用。
- 其余商城页（about/cart/checkout/product 等）保持原型态（mock 数据），不在本单元范围，仅保证 build 通过。
- 代码整洁无遗留标记；build exit 0；type-check + lint 通过。

## 12. 变更清单（added）

### 配置
- frontend/portal-store/package.json
- frontend/portal-store/.npmrc
- frontend/portal-store/.env.local
- frontend/portal-store/next.config.mjs
- frontend/portal-store/tsconfig.json (复制自原型)
- frontend/portal-store/tailwind.config.ts (复制)
- frontend/portal-store/postcss.config.mjs (复制)

### lib（适配新增）
- frontend/portal-store/lib/api/case.ts
- frontend/portal-store/lib/api/types.ts
- frontend/portal-store/lib/api/token-store.ts
- frontend/portal-store/lib/api/client.ts
- frontend/portal-store/lib/api/auth-api.ts
- frontend/portal-store/lib/auth/oauth.ts
- frontend/portal-store/lib/i18n/error-messages.ts
- frontend/portal-store/lib/i18n/error-text.ts
- frontend/portal-store/lib/i18n/messages.ts
- frontend/portal-store/lib/i18n/i18n-context.tsx
- frontend/portal-store/lib/stores/auth-store.ts
- frontend/portal-store/lib/stores/auth-config-store.ts

### components/account（适配新增）
- frontend/portal-store/components/account/login-card.tsx
- frontend/portal-store/components/account/otp-input.tsx
- frontend/portal-store/components/account/oauth-buttons.tsx
- frontend/portal-store/components/account/provider-icons.tsx
- frontend/portal-store/components/account/profile-view.tsx
- frontend/portal-store/components/account/auth-guard.tsx
- frontend/portal-store/components/account/dialog.tsx
- frontend/portal-store/components/account/identity-list.tsx
- frontend/portal-store/components/account/session-list.tsx
- frontend/portal-store/components/account/change-primary-dialog.tsx
- frontend/portal-store/components/account/delete-account-dialog.tsx
- frontend/portal-store/components/account/security-panel.tsx

### app（适配修改）
- frontend/portal-store/app/layout.tsx (注入 I18nProvider)
- frontend/portal-store/app/account/layout.tsx (注入 AuthGuard)
- frontend/portal-store/app/account/login/page.tsx (LoginCard + OIDC 回调)
- frontend/portal-store/app/account/page.tsx (ProfileView)
- frontend/portal-store/app/account/settings/page.tsx (只读资料 + 删除账户)
- frontend/portal-store/app/account/security/page.tsx (SecurityPanel)
- frontend/portal-store/components/account/account-sidebar.tsx (真实 user + logout)

### 原型骨架复制（保持可 build，非身份页不接后端）
- frontend/portal-store/app/** (其余商城页)
- frontend/portal-store/components/** (layout/cart/marketing/product/ui/store-provider)
- frontend/portal-store/data/** , lib/utils.ts , public/**

