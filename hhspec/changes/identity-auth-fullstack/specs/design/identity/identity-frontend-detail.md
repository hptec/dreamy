# identity 前端详细设计（L2）

> 角色: l2_frontend_designer ｜ change: identity-auth-fullstack ｜ domain: identity
> 两套技术栈：portal-store(Next.js 15 App Router, port 5173, EN/ES/FR) + portal-admin(Vue3 + Pinia + Vite + Tailwind/Headless-UI, port 5174, 中文)。
> 编号：组件树(COMP) / 状态管理(STORE) / 页面路由(PAGE) / 表单交互(FORM)。伪代码级，不绑定框架细节。
> 原型对照：app/account/{login,page,settings,security}（4）+ portal-admin/src/views/{Login,Customers,CustomerDetail,AdminList,RoleManagement,AuthSettings,OperationLogs}（7）。

---

## A. portal-store（Next.js 15）

### A.1 页面路由（PAGE）
| 编号 | 路由 | 文件 | 渲染 | API |
|------|------|------|------|-----|
| PAGE-S01 | /account/login | app/account/login/page.tsx | client | sendOtp/verifyOtp/oidcCallback/getStoreAuthConfig |
| PAGE-S02 | /account | app/account/page.tsx | RSC+client | getProfile |
| PAGE-S03 | /account/settings | app/account/settings/page.tsx | client | getProfile(读) |
| PAGE-S04 | /account/security | app/account/security/page.tsx | client | listIdentities/bind/unbind/changePrimary/listSessions/revoke*/deleteAccount |

### A.2 状态管理（STORE，Next 用 zustand + React Context；不用 Pinia）
- STORE-S01 `authStore`：{ tokens, user, isAuthenticated, login(), logout(), refresh() }；refresh 失败→跳登录
- STORE-S02 `authConfigStore`：getStoreAuthConfig 缓存登录方式开关（控制 Google/Apple 按钮显隐，FUNC-003/006）
- STORE-S03 token 持久化：access 内存 + refresh httpOnly cookie/secure storage；API 客户端 401→自动 refresh→重放
- STORE-S04 i18n：next-intl，locale en/es/fr，错误 code→本地化文案映射表（消费 error code）

### A.3 组件树（COMP）
- COMP-S01 `<LoginCard>`（PAGE-S01）：两步 step='email'|'code'
  - COMP-S02 `<EmailStep>`：email 输入 + "Email me a code" + Google/Apple 按钮（按 authConfig 显隐）
  - COMP-S03 `<OtpStep>`：6 格 OTP 输入（自动跳格/退格聚焦，原型已有逻辑）+ 重发倒计时(resend_after_seconds) + 错误提示(remaining_attempts)
  - COMP-S04 `<OAuthButtons>`：GoogleIcon/AppleIcon（原型已有 SVG），点击走 OIDC 流；失败 502/504→提示改用 OTP（DG-001）
- COMP-S05 `<SecurityPanel>`（PAGE-S04）
  - COMP-S06 `<IdentityList>`：凭证卡片（provider 图标/identifier/is_primary 徽章/relay 失效提示）+ 绑定/解绑按钮
  - COMP-S07 `<SessionList>`：会话卡片（device/browser/location/is_new_device 徽章/current 标记）+ "登出此设备"/"登出所有其他设备"
  - COMP-S08 `<ChangePrimaryDialog>`：new_email + OTP 校验
  - COMP-S09 `<DeleteAccountDialog>`：危险操作二次确认（confirm:true）
- COMP-S10 `<ProfileView>`（PAGE-S02/03）：只读资料展示

### A.4 表单交互（FORM）
- FORM-S01 OTP 发送：email 前端正则预校验 → sendOtp → 切 code 步 + 启动倒计时；429→展示剩余秒禁用重发
- FORM-S02 OTP 校验：6 格满自动提交 verifyOtp；401 显剩余次数；410 锁定/过期→引导重新发码；成功→存 token 跳 /account
- FORM-S03 OIDC：弹授权→回调 id_token→oidcCallback；409 未验证冲突→提示原方式登录后绑定
- FORM-S04 解绑：is_primary 禁用解绑按钮（前端预判 EDGE-007）；403 40305→提示至少保留一种
- FORM-S05 换主邮箱：new_email→发 OTP→校验→提交；409 占用提示
- FORM-S06 注销：双重确认弹窗→deleteAccount→清 token 跳首页

---

## B. portal-admin（Vue3 + Pinia）

### B.1 页面路由（PAGE）
| 编号 | 路由 | 视图 | 权限 key | API |
|------|------|------|----------|-----|
| PAGE-A01 | /login | Login.vue | 公开 | adminLogin |
| PAGE-A02 | /customers | Customers.vue | /customers | listUsers |
| PAGE-A03 | /customers/:id | CustomerDetail.vue | /customers | getUserDetail/toggleUserStatus/forceLogout |
| PAGE-A04 | /system/admins | AdminList.vue | /system/admins | listAdmins/create/update/delete/toggle/resetPassword/listRoles |
| PAGE-A05 | /system/roles | RoleManagement.vue | /system/roles | listRoles/create/update/delete/listPermissions |
| PAGE-A06 | /system/auth | AuthSettings.vue | /system/auth | getAuthConfig/updateAuthConfig |
| PAGE-A07 | /system/logs | OperationLogs.vue | (审计查看) | listOperationLogs/export |

### B.2 状态管理（STORE，Pinia）
- STORE-A01 `useAuthStore`：{ token, admin, roleName, isSuper, permissionKeys[], login(), logout(), fetchMe() }
- STORE-A02 `useMenuStore`：基于 permissionKeys 计算可见菜单（菜单渲染过滤 FUNC-021）
- STORE-A03 token 持久化 localStorage（admin 8h 无 refresh，过期跳登录）
- STORE-A04 各页 list store（usersStore/adminsStore/rolesStore/logsStore）：分页/筛选/loading

### B.3 路由守卫（GUARD，FUNC-021/EDGE-016）
- GUARD-01 全局 beforeEach：无 token→/login；有 token→fetchMe（缓存 permissionKeys）
- GUARD-02 路由 meta.permission：当前路由 permission_key ∉ permissionKeys 且非超管 → 重定向 403 页（前端守卫，后端 40300 兜底）
- GUARD-03 菜单渲染：useMenuStore 按 permissionKeys 过滤（无权限菜单项不渲染）
- GUARD-04 超管 isSuper：短路放行全部路由/菜单（permissionKeys 含全 22 key）

### B.4 组件树（COMP）
- COMP-A01 `<AppLayout>`：侧边菜单(按权限过滤) + 顶栏(admin 信息/登出)
- COMP-A02 `<LoginForm>`（PAGE-A01）：email/password + redirect 参数；401/403 错误提示
- COMP-A03 `<CustomerTable>`（PAGE-A02）：分页表格 + status/tier/email 筛选
- COMP-A04 `<CustomerDetailPanel>`（PAGE-A03）：资料 + 凭证 + 会话 + 登录历史 tab；禁用/强制下线操作（二次确认）
- COMP-A05 `<AdminTable>` + `<AdminFormModal>`（PAGE-A04）：CRUD；超管行禁用删除/禁用按钮（前端预判 EDGE-014）；删自己按钮禁用（EDGE-013）
- COMP-A06 `<RolePanel>`（PAGE-A05，原型已有结构）：左角色列表 + 右权限矩阵（按 group 分组复选）；is_locked 角色矩阵只读 + 保存禁用（EDGE-019/FUNC-018）；hasUnsavedChanges 提示
- COMP-A07 `<AuthConfigForm>`（PAGE-A06）：开关 + OTP 数值输入（前端区间预校验，email_enabled 强制只读 on）；OAuth 凭据只读展示
- COMP-A08 `<OperationLogTable>`（PAGE-A07）：分页 + action/operator/时间筛选 + changes 详情展开 + 导出按钮（只读，无删除）

### B.5 表单交互（FORM）
- FORM-A01 登录：提交 adminLogin→存 token+permissionKeys→按 redirect 跳转
- FORM-A02 创建管理员：name/email/password/role_id 校验；409 邮箱重复提示
- FORM-A03 角色权限保存：组级/项级复选→permission_keys→updateRole；保存后 fetchMe 重渲菜单（FLOW-11）；is_locked 禁止编辑
- FORM-A04 删角色：有成员→409 40904 提示先迁移；is_locked→禁用删除
- FORM-A05 认证配置保存：前端区间预校验→422 40002 字段级错误回显
- FORM-A06 强制下线/禁用用户：二次确认→成功后刷新会话列表

## C. 跨端共性
- 错误处理：统一拦截 {code,message,details}；store 按 code 本地化(en/es/fr)，admin 直显中文 message
- 网络：前后端分离，跨域请求带 Authorization + Accept-Language（store），CORS 见 shared-contracts
- Headless-UI Vue 注意（项目记忆）：Combobox/Menu 等根组件传 class/style 必须配 `as` prop，否则级联崩溃

## D. 自检
- [x] 11 原型页全部映射 PAGE 编号
- [x] 两套技术栈分别给 STORE/COMP/PAGE/FORM（Next zustand / Vue Pinia）
- [x] RBAC 路由守卫 + 菜单渲染（FUNC-021/EDGE-016）
- [x] 错误 code→文案映射消费 error-detail
- [x] 危险操作二次确认 + 前端预判约束（超管/主邮箱/min_methods）
