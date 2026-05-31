# REQ-IDENTITY-008 集成、运维与数据保留合规

> 域：identity｜后端 /api/store/*、/api/admin/*
> 数据保留策略依据 GDPR（EU/UK）、CCPA/CPRA（加州）、PIPEDA（加拿大）、澳洲隐私法。

## 邮件集成

- **REQ-008-01 渠道**：通过 **SMTP** 发送邮件（通用，不绑定特定云厂商），配置 SMTP host/port/账号。沙箱无网络时可切本地 stub。
- **REQ-008-02 模板**：邮件模板（otp / new_device / change_primary / account_deleted）可配置，支持 **EN/ES/FR**；按用户语言选模板。
- **REQ-008-03 可靠性**：发送失败重试（有限次）+ 失败告警；不阻塞主流程（OTP 发送失败提示用户重试）。

## OIDC 集成

- **REQ-008-04 凭据管理**：Google Client ID / Apple Service ID 等从配置/密钥管理读取，不硬编码；后台「登录与认证」页只读展示配置状态。
- **REQ-008-05 失败降级**：OIDC 超时/不可达时返回明确错误码，引导用户改用邮箱 OTP。

## 可观测性

- **REQ-008-06 审计**：后台关键操作全量写 OperationLog（只读不可删）；登录成功/失败写 LoginHistory。
- **REQ-008-07 日志**：后端关键路径（发码、校验、归并、强制下线、配置变更）记录结构化日志（不含验证码明文/密码）。

## 数据保留与清理（合规，每日定时任务）

- **REQ-008-08 OTP**：已用/过期 OTP 在 24h 内清除（仅存哈希，无业务留存价值）。
- **REQ-008-09 会话**：`revoked` 会话保留 30 天后清除（安全取证）。
- **REQ-008-10 登录记录**：`login_history` 保留 1 年（安全监控/欺诈检测，GDPR Art.6(1)(f) 正当利益），到期清除。
- **REQ-008-11 账户注销（右被遗忘，GDPR Art.17）**：注销为 30 天软删除宽限期（支持用户反悔），**到期后必须不可逆匿名化 PII**（email/name/phone/identity 标识），而非永久保留软删除标记；匿名化后 `User.anonymized=true`、`anonymized_at` 记录。
- **REQ-008-12 审计日志保留**：OperationLog 保留 1–3 年（问责/合规审计），按机构合规要求设定，不随账户注销删除（正当利益，时间受限）。
- **REQ-008-13 书面策略**：保留期限形成可审计的书面保留计划（GDPR/CPRA 要求）。

## 验收场景

```gherkin
Scenario: REQ-008-11 注销超宽限期匿名化
  Given 用户 status=deleted 且 deleted_at 距今 > 30 天
  When 每日数据保留任务运行
  Then User.status=anonymized，PII 不可逆匿名化，anonymized_at 记录

Scenario: REQ-008-08 OTP 定时清理
  Given 存在已用/过期 OTP
  When 清理任务运行
  Then 24h 内的相关 OTP 记录被清除

Scenario: REQ-008-10 登录记录到期清理
  Given login_history 存在超 1 年的记录
  When 清理任务运行
  Then 超期记录被清除

Scenario: REQ-008-12 注销不删除审计日志
  Given 某用户已匿名化
  When 查询其相关 OperationLog
  Then 审计日志仍按保留期保留（正当利益，时间受限）

Scenario: REQ-008-01 SMTP 发送失败重试
  Given SMTP 临时不可达
  When 系统发送 OTP 邮件
  Then 有限次重试并在仍失败时告警，提示用户重试
```
