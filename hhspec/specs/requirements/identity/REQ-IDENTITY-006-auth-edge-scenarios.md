# REQ-IDENTITY-006 认证边缘场景

> 域：identity｜门户：portal-store（后端 /api/store/account/*）
> 覆盖换主邮箱、账户注销/删除、未验证邮箱冲突、Apple relay 失效。

## 功能需求

- **FUNC-026 更换主邮箱**：用户在登录与安全页发起更换主邮箱 → 对新邮箱发 OTP → 验证通过且新邮箱未被他人占用 → 将 `is_primary` 迁移到新邮箱凭证 → 通知旧邮箱「主邮箱已变更」。
- **FUNC-027 账户注销/删除**：用户在账户设置发起注销 → 二次确认 → `User.status=deleted` 软删除（记 `deleted_at`）→ 撤销全部会话并清用户缓存 → 发注销确认邮件。数据按保留策略软删除，不物理清除。
- **FUNC-028 未验证邮箱冲突完整流**：第三方登录邮箱命中既有账户但 `email_verified=false` → **不静默合并** → 返回 409 EMAIL_CONFLICT_UNVERIFIED，提示用原方式登录后在安全页手动绑定该第三方 → 用户绑定成功后 identity connected。
- **FUNC-029 Apple relay 失效处理**：检测 relay 邮件退信/用户关闭转发 → 标记该 identity relay 失效 → 若用户有其他可达方式则提醒更新联系邮箱；若无其他可达方式，**保留 Apple sub 登录能力不阻断**（不锁死账户）。

## 关键约束

- 换主邮箱：原主邮箱凭证保留为普通已验证方式（除非用户另行解绑，仍受 min_methods 约束）。
- 注销为软删除，注销后该 email/provider_uid 的再次登录视为冲突，按未验证冲突流处理（不自动复活已删除账户）。
- Apple relay 失效不等于身份失效：`sub` 仍是稳定主键。

## 验收场景

```gherkin
Scenario: FUNC-026 更换主邮箱成功
  Given 用户已登录，新邮箱 new@email.com 未被占用
  When 用户对新邮箱完成 OTP 验证并确认更换
  Then is_primary 迁移到 new@email.com 凭证
  And 向旧邮箱发送主邮箱变更通知

Scenario: EDGE-020 新主邮箱被他人占用
  Given new@email.com 已是另一账户的已验证凭证
  When 用户尝试更换为该邮箱
  Then 返回 409 EMAIL_EXISTS，不变更

Scenario: FUNC-027 账户注销
  Given 用户已登录
  When 发起注销并二次确认
  Then User.status=deleted 且记 deleted_at
  And 撤销全部会话、清用户缓存、发注销确认邮件

Scenario: EDGE-021 注销账户再次登录
  Given 某 email 对应账户已 deleted
  When 该 email 再次请求登录
  Then 不复活原账户，按新账户/冲突流处理

Scenario: FUNC-028 未验证邮箱冲突
  Given Google 返回邮箱命中既有账户但 email_verified=false
  When OIDC 回调
  Then 返回 409 EMAIL_CONFLICT_UNVERIFIED，提示原方式登录后绑定
  And 不创建归并、不静默合并

Scenario: FUNC-029 Apple relay 失效但保留登录
  Given 用户仅用 Apple 登录且 relay 邮箱失效
  When relay 邮件退信
  Then 标记 relay 失效但保留 Apple sub 登录能力
  And 不锁死账户
```
