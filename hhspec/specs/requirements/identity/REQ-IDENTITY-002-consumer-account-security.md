# REQ-IDENTITY-002 消费端账户安全：登录方式绑定与会话设备管理

> 门户：portal-store｜后端：/api/store/account/*｜原型：app/account/security/page.tsx、app/account/settings/page.tsx、app/account/page.tsx
> 域：identity

## 功能需求

- **FUNC-007 查看登录方式**：登录与安全页展示 email/google/apple 三种方式的绑定状态、是否主、是否已验证、最近使用。
- **FUNC-008 绑定登录方式**：未绑定方式可发起绑定（OIDC 校验或邮箱验证），同一 provider_uid 未被他人占用方可绑定。
- **FUNC-009 解绑登录方式**：已绑定方式可解绑，但主邮箱不可解绑，且解绑后剩余 connected 方式数必须 ≥ AuthConfig.min_methods。
- **FUNC-010 查看活跃会话**：展示当前设备与其他设备的会话（设备/浏览器/位置/登录方式/最近活跃）。
- **FUNC-011 登出单个设备**：撤销指定非当前会话（status → revoked）。
- **FUNC-012 登出其他全部设备**：保留当前会话，撤销其余全部会话。
- **FUNC-013 账户设置**：账户总览与设置页展示资料；设置页**不含修改密码**（passwordless）。

## 验收场景

```gherkin
Scenario: FUNC-009 解绑非主方式成功
  Given 用户已绑定 email(primary)+google+apple，min_methods=1
  When 解绑 google
  Then UserIdentity(google).connected=false

Scenario: EDGE-007 解绑主邮箱被拒
  Given email 为 is_primary 的方式
  When 用户尝试解绑 email
  Then 操作被拒绝并提示主邮箱不可移除

Scenario: EDGE-008 解绑导致低于最小方式数被拒
  Given 用户仅剩 1 种 connected 方式，min_methods=1
  When 用户尝试解绑该方式
  Then 操作被拒绝并提示至少保留一种登录方式

Scenario: FUNC-012 登出其他设备
  Given 用户有 3 个 active 会话，其一为当前设备
  When 点击 "Sign out other devices"
  Then 其余 2 个会话 status=revoked，当前会话保留

Scenario: EDGE-009 跨用户撤销会话被拒
  Given 会话 S 属于另一个 user
  When 当前用户尝试撤销 S
  Then 返回 403 FORBIDDEN

Scenario: EDGE-010 设置页无修改密码入口
  Given 消费端为 passwordless 账户
  When 用户打开账户设置页
  Then 页面不展示任何修改密码/设置密码入口
```
