---
unit_id: unit_portal_admin
target: frontend
portal_id: portal-admin
tech: vue3-vite-ts-tailwind-headlessui-pinia
status: implemented
build_status: PASS
build_command: "pnpm build (vue-tsc -b && vite build)"
build_result: "✓ built — dist 产物生成，77 模块 transform，0 类型错误"
coverage: "7/7 身份页面 + 守卫 + Pinia store + api client 全覆盖"
created_at: "2026-06-01T00:45:00Z"
---

# Traceability Map — unit_portal_admin

> portal-admin 管理后台（Vue3 + Vite + TS + Tailwind + Headless UI + Pinia），原型复制+适配。
> 端口 5174，中文 UI，对接 backend /api/admin/*（admin JWT，access 8h 无 refresh）。
> build 已验证通过（vue-tsc 类型检查 + vite build）。

## 页面约束（PAGE-A*）

| 约束 ID | 页面 | 文件 | prototype_conformance | status |
|---------|------|------|----------------------|--------|
| PAGE-A01 | 管理员登录 | src/views/Login.vue | 复制原型 Login.vue，替换 mock 为 auth.login | implemented |
| PAGE-A02 | 用户列表 | src/views/Customers.vue | 复制原型 Customers.vue，对接 users store | implemented |
| PAGE-A03 | 用户详情+身份运营 | src/views/CustomerDetail.vue | 登录记录/强制下线/禁用，对接 users API | implemented |
| PAGE-A04 | 管理员 CRUD | src/views/AdminList.vue | 复制原型，对接 admins API（增删改/禁用/重置密码） | implemented |
| PAGE-A05 | 角色权限矩阵 | src/views/RoleManagement.vue | 复制原型，对接 roles API（权限矩阵保存） | implemented |
| PAGE-A06 | 登录与认证配置 | src/views/AuthSettings.vue | 复制原型，对接 authConfig API（读取/保存） | implemented |
| PAGE-A07 | 操作日志 | src/views/OperationLogs.vue | 复制原型，对接 logs API（筛选/详情/导出，只读） | implemented |

## 组件约束（COMP-A*）

| 约束 ID | 组件 | 文件 | status |
|---------|------|------|--------|
| COMP-A01 | 后台外壳（RBAC 菜单动态渲染） | src/components/AdminShell.vue | implemented |
| COMP-A02 | 页头 | src/components/PageHeader.vue | implemented |
| COMP-A03 | 分页 | src/components/Pagination.vue | implemented |
| COMP-A04 | 状态徽章 | src/components/StatusBadge.vue | implemented |
| COMP-A05 | 开关 | src/components/Toggle.vue | implemented |
| COMP-A06 | 空状态 | src/components/EmptyState.vue | implemented |
| COMP-A07 | Toast 宿主 | src/components/ToastHost.vue | implemented |
| COMP-A08 | 无权限页 | src/views/Forbidden.vue | implemented |

## 状态管理（Pinia STORE-A*）

| 约束 ID | Store | 文件 | status |
|---------|-------|------|--------|
| STORE-A01 | 认证（admin JWT/权限 key 集合） | src/stores/auth.ts | implemented |
| STORE-A02 | 菜单（RBAC 动态菜单） | src/stores/menu.ts | implemented |
| STORE-A03 | 用户运营 | src/stores/users.ts | implemented |
| STORE-A04 | 管理员管理 | src/stores/admins.ts | implemented |
| STORE-A05 | 角色权限 | src/stores/roles.ts | implemented |
| STORE-A06 | 操作日志 | src/stores/logs.ts | implemented |
| STORE-A07 | Toast 提示 | src/stores/toast.ts | implemented |

## 路由守卫（GUARD-*）

| 约束 ID | 内容 | 文件 | status |
|---------|------|------|--------|
| GUARD-01~04 | 登录态校验 + meta.permission 菜单级 RBAC 拦截 + 无权限跳 Forbidden | src/router/index.ts | implemented |

## API 客户端

| 约束 | 内容 | 文件 | status |
|------|------|------|--------|
| API-CLIENT | axios 实例，admin JWT 注入 Authorization、Accept-Language=zh、错误信封 {code,message,details}、401→跳登录、snake_case↔camelCase | src/api/client.ts | implemented |
| API-AUTH | 登录/登出/当前管理员权限 | src/api/auth.ts | implemented |
| API-ADMINS | 管理员 CRUD/禁用/重置密码 | src/api/admins.ts | implemented |
| API-ROLES | 角色 CRUD/权限矩阵 | src/api/roles.ts | implemented |
| API-USERS | 用户列表/详情/登录记录/强制下线/禁用 | src/api/users.ts | implemented |
| API-AUTHCONFIG | 认证配置读取/保存 | src/api/authConfig.ts | implemented |
| API-LOGS | 操作日志筛选/详情/导出 | src/api/logs.ts | implemented |
| API-TYPES | 类型定义（对齐 openapi schema） | src/api/types.ts | implemented |

## 关键设计落地

- **admin JWT 隔离**：token 持久化 localStorage（dreamy_admin_token），access 8h 无 refresh，过期 401→跳登录（STORE-A03）。
- **菜单级 RBAC**：登录后拉权限 key 集合，AdminShell 菜单按权限动态渲染，router 守卫拦截（GUARD）。
- **超管保护**：UI 上超管不可删/降权/禁用（按钮禁用 + 后端二次校验）。
- **Headless UI as prop**：Combobox/Menu 等传 class 时配 as prop，避免级联崩溃。
- **字段映射**：边界统一 snake_case(后端)↔camelCase(前端)。

## build 验证

```
pnpm build → vue-tsc -b（0 类型错误）+ vite build（77 模块，dist 产物全部生成）
BUILD SUCCESSFUL
```

## 风险项

- competitor-refs 品牌装饰图通过软链指向原型 public（非身份功能必需）。
- 非身份页面（Products/Orders/Dashboard 等）随原型骨架保留为占位路由，不在本变更功能范围，但保证 build 通过。
