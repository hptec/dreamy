# REQ-IDENTITY-005 安全阈值、限流、会话通知与缓存策略

> 域：identity｜后端：/api/store/*、/api/admin/*｜跨页面横切约束
> 本 REQ 为非功能性 + 安全约束细化，落到所有认证相关接口。

## JWT 令牌策略

- **REQ-005-01 消费端 store JWT**：access token 有效期 **2h** + refresh token **30d** 滑动续期（每次刷新顺延）。refresh token 绑定 `UserSession.refresh_token_id`，可被撤销（登出/强制下线/账户禁用/注销即失效）。
- **REQ-005-02 后台 admin JWT**：单一 access token **8h**，**不设 refresh**，过期重新登录。
- **REQ-005-03 隔离**：store 与 admin JWT 独立签名密钥；任一类型 token 访问对方前缀接口返回 401。

## OTP 频控（默认值，AuthConfig 可调）

- **REQ-005-04 重发间隔**：同一 email 重发间隔 ≥ `otp_resend_seconds`（默认 30s），否则 429 RESEND_TOO_SOON。
- **REQ-005-05 发码频次**：单 email ≤ 5 次/小时 且 ≤ 5 次/天；单 IP ≤ 20 次/小时。超限 429 RATE_LIMITED。
- **REQ-005-06 校验失败**：单码失败达 `otp_max_attempts`（默认 5）锁定该码（locked）。
- **REQ-005-07 计数后端**：频控计数由 Redis 实现（非 DB 表），窗口到期自动清零。

## 会话安全与新设备通知

- **REQ-005-08 会话数**：单用户活跃会话数不设硬上限。
- **REQ-005-09 新设备通知**：登录成功且 `is_new_device=true`（设备指纹/IP 位置首次出现）时，发送登录提醒邮件，含设备/IP/时间 + 「不是本人？一键登出」链接（触发 signout_others）。`login_history.notified` 记录是否已通知。

## JetCache 缓存策略（消费端只读接口）

- **REQ-005-10 分级与级数**：会话兼容键仅远程 Redis 单级（不挂本地 Caffeine），TTL **30s**；当前实例的 token 有效性始终读取 DB session/admin 状态。资料/配置类（用户资料、登录方式列表、AuthConfig）本地 Caffeine + 远程 Redis 两级，TTL AuthConfig **10min**、用户资料/登录方式 **5min**。
- **REQ-005-11 写失效**：所有写操作（绑定/解绑、换主邮箱、会话撤销、账户禁用/注销、AuthConfig 保存）即时 `@CacheInvalidate`/`@CacheUpdate`。
- **REQ-005-12 强一致**：账户禁用/强制下线/注销提交 DB 状态后，所有当前实例后续请求均按 DB 权威状态立即拒绝；Redis 兼容键删除失败不形成授权窗口。
- **REQ-005-13 key 规范**：`store:authconfig`、`store:user:{userId}`、`store:identities:{userId}`（两级）；`store:sessions:{userId}`、`store:session:valid:{tokenId}`（仅 Redis）。

## 验收场景

```gherkin
Scenario: REQ-005-01 access 过期用 refresh 续期
  Given store access token 已过期但 refresh token 有效
  When 携带 refresh 请求续期
  Then 签发新 access token 并顺延 refresh 有效期

Scenario: REQ-005-05 单 email 发码超频
  Given 同一 email 1 小时内已发码 5 次
  When 第 6 次请求发码
  Then 返回 429 RATE_LIMITED

Scenario: REQ-005-09 新设备登录通知
  Given 用户从未在该设备/位置登录过
  When 登录成功
  Then 发送新设备提醒邮件且 login_history.notified=true

Scenario: REQ-005-12 强制下线全集群即时生效
  Given 所有当前实例均以 DB session/admin 状态作为会话授权依据
  When 管理员强制下线该会话
  Then DB 撤销提交后所有后端实例后续请求均判定会话已撤销，Redis 兼容键删除失败也无残留授权窗口

Scenario: REQ-005-11 解绑后缓存即时失效
  Given store:identities:{userId} 已缓存
  When 用户解绑 google
  Then 该缓存即时失效，下次读取返回最新登录方式列表
```
