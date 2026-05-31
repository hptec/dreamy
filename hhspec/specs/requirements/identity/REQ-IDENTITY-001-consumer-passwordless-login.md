# REQ-IDENTITY-001 消费端 Passwordless 登录与多渠道认证

> 门户：portal-store（Next.js）｜后端：/api/store/auth/*｜原型：app/account/login/page.tsx
> 域：identity｜变更：identity-auth-fullstack

## 背景与目标

消费端采用纯 passwordless 登录：邮箱一次性验证码（OTP）+ Continue with Google + Continue with Apple，**不设密码**。识别同一自然人靠 `(provider, provider_uid)` → user_id，不靠 email。Google/Apple 用 OIDC `sub` 作 provider_uid；邮箱用已验证 email。

## 功能需求

- **FUNC-001 发送邮箱验证码**：用户输入邮箱，系统校验邮箱格式后，按 AuthConfig（length/ttl/resend）生成 OTP，仅存哈希，并通过邮件服务发送。
- **FUNC-002 校验验证码登录**：用户输入验证码，正确且未过期未超次时签发 store JWT 并进入账户中心；按邮箱归并或新建 user。
- **FUNC-003 重发验证码**：距上次发送 ≥ `otp_resend_seconds` 才允许重发；重发覆盖旧码并重置 attempts。
- **FUNC-004 Google 登录**：跳转 Google OIDC，回调校验 `sub`，按 `(google, sub)` 定位或归并/新建 user 后签发会话。
- **FUNC-005 Apple 登录**：同上，支持 Hide My Email；以 Apple `sub` 为稳定主键，首次授权返回的邮箱/姓名首次即落库；relay 邮箱保存到 identity.relay_email。
- **FUNC-006 登录方式开关生效**：被 AuthConfig 关闭的第三方登录方式不在登录页展示且后端拒绝其入口。

## 验收场景（Gherkin）

```gherkin
Scenario: FUNC-001 发送验证码成功
  Given 用户在登录页输入合法邮箱 jane@email.com
  When 点击 "Email me a code"
  Then 系统生成一条 status=pending 的 OtpCode 并发送邮件
  And 页面进入验证码输入步骤

Scenario: EDGE-001 邮箱格式非法
  Given 用户输入 "not-an-email"
  When 点击发送验证码
  Then 返回 422 INVALID_EMAIL，不生成 OtpCode

Scenario: FUNC-002 验证码正确登录成功
  Given 存在 status=pending 且未过期的 OtpCode
  When 用户提交正确的 6 位验证码
  Then OtpCode 流转为 consumed
  And 系统签发 store JWT 并创建 active 的 UserSession
  And 跳转 /account

Scenario: EDGE-002 验证码错误未达上限
  Given OtpCode attempts=1 max_attempts=5
  When 用户提交错误验证码
  Then attempts 增至 2，OtpCode 仍为 pending
  And 记录一条 result=failed 的 LoginHistory

Scenario: EDGE-003 验证码错误达上限锁定
  Given OtpCode attempts=4 max_attempts=5
  When 用户再次提交错误验证码
  Then OtpCode 流转为 locked，需重新发码

Scenario: EDGE-004 验证码过期
  Given OtpCode now > expires_at
  When 用户提交验证码
  Then 返回 410 OTP_EXPIRED

Scenario: EDGE-005 重发间隔未到
  Given 距上次发送不足 otp_resend_seconds
  When 用户点击重发
  Then 返回 429 RESEND_TOO_SOON

Scenario: FUNC-004 Google 首次登录新建账户
  Given (google, sub=S) 不存在任何 UserIdentity
  And 该 Google 邮箱 email_verified=true 且系统无同邮箱账户
  When 用户完成 Google OIDC 授权
  Then 新建 User 与 UserIdentity(provider=google, provider_uid=S)
  And 签发 store JWT

Scenario: FUNC-005 Apple Hide My Email 登录
  Given 用户用 Apple 登录并选择隐藏邮箱
  When OIDC 回调返回 sub 与 relay 邮箱
  Then UserIdentity.hidden_email=true 且 relay_email 落库
  And 以 sub 作为稳定 provider_uid

Scenario: EDGE-006 被禁用账户登录被拒
  Given User.status=disabled
  When 用户通过任意方式验证成功
  Then 返回 403 ACCOUNT_DISABLED，不签发会话
```
