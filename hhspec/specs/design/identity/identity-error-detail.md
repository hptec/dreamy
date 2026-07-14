# identity 错误处理详细设计（L2）

> 角色: l2_error_designer ｜ change: identity-auth-fullstack ｜ domain: identity
> 五部分：异常分类与传播(EX/PATH) / 接口错误映射(含 MUST_TEST) / 降级与重试(RT/CB/DG) / 日志脱敏规范 / 错误响应国际化。
> 权威码表：architecture/error-strategy.md（25 码）；本文细化为可实现规范。

## 1. 异常分类与传播路径（EX/PATH）

### 异常层级（EX）
| 编号 | 异常类 | 层级 | 映射码 | 说明 |
|------|--------|------|--------|------|
| EX-01 | InvalidEmailException | 表示层校验 | 40001 | EDGE-001 |
| EX-02 | ValidationException | 表示层 | 40000 | 通用字段校验，details 字段级 |
| EX-03 | UnauthorizedException | 过滤器/应用 | 40100 | 无/无效 token、跨端误用 EDGE-024 |
| EX-04 | OtpInvalidException | 领域 | 40101 | details.remaining_attempts |
| EX-05 | RefreshInvalidException | 应用 | 40102 | FLOW-04 |
| EX-06 | CredentialsInvalidException | 应用 | 40103 | admin 凭据错（中文） |
| EX-07 | ForbiddenException | 应用 | 40300 | 跨用户撤销/无菜单权限 |
| EX-08 | AccountDisabledException | 领域 | 40301 | 消费端禁用 |
| EX-09 | AdminDisabledException | 应用 | 40302 | 后台禁用 |
| EX-10 | ProviderDisabledException | 应用 | 40303 | OAuth 入口关闭 |
| EX-11 | PrimaryEmailRequiredException | 领域 | 40304 | 解绑主邮箱 |
| EX-12 | MinMethodsRequiredException | 领域 | 40305 | 低于 min_methods |
| EX-13 | SuperAdminProtectedException | 应用 | 40306 | 超管保护 |
| EX-14 | CannotDeleteSelfException | 应用 | 40307 | 删自己 |
| EX-15 | RoleLockedException | 应用 | 40308 | 锁定角色改权限 |
| EX-16 | NotFoundException | 应用 | 40400 | 资源不存在 |
| EX-17 | EmailExistsException | 应用 | 40901 | 邮箱重复/换主占用 |
| EX-18 | EmailConflictUnverifiedException | 应用 | 40902 | 未验证冲突不合并 EDGE-017/021 |
| EX-19 | IdentityTakenException | 应用 | 40903 | provider_uid 占用 |
| EX-20 | RoleInUseException | 应用 | 40904 | 角色有成员 |
| EX-21 | OtpExpiredException | 领域 | 41001 | now>expires_at |
| EX-22 | OtpLockedException | 领域 | 41002 | attempts 达上限 |
| EX-23 | ResendTooSoonException | 应用 | 42901 | details.remaining_resend_seconds |
| EX-24 | RateLimitedException | 应用 | 42902 | 发码超频 EDGE-022 |
| EX-25 | ConfigOutOfRangeException | 应用 | 40002 | EDGE-019（中文） |
| EX-26 | DatabaseException | 基础设施 | 50001 | 不暴露 SQL |
| EX-27 | EmailSendFailedException | 基础设施 | 50002 | SMTP 重试仍失败 |
| EX-28 | OidcUnavailableException | 基础设施 | 50201 | 502 降级 |
| EX-29 | OidcTimeoutException | 基础设施 | 50401 | 504 降级 |
| EX-30 | InternalException | 兜底 | 50000 | 未预期，不暴露细节 |

### 传播路径（PATH）
- PATH-01 领域异常（EX-04/08/11/12/21/22）由聚合根抛 → 应用层不捕获透传 → 表示层 GlobalExceptionHandler 映射 {code,message,details}
- PATH-02 基础设施异常（EX-26~29）在基础设施层转换后向上抛，禁止泄漏底层堆栈
- PATH-03 鉴权过滤器异常（EX-03）在 Filter 链直接短路返回 401，不进入 Controller
- PATH-04 兜底：任何未分类异常 → EX-30 InternalException(50000)，记 ERROR 日志（含 traceId），响应不含细节

---

## 2. 接口错误映射表（含 MUST_TEST 标记）

| 端点 | 触发条件 | code | HTTP | 用户提示(脱敏后) | MUST_TEST |
|------|---------|------|------|------------------|-----------|
| sendOtp | 邮箱非法 | 40001 | 422 | "请输入有效邮箱" | ✓ |
| sendOtp | 重发过频 | 42901 | 429 | "请稍后再试"(+剩余秒) | ✓ |
| sendOtp | 发码超频 | 42902 | 429 | "请求过于频繁" | ✓ |
| verifyOtp | 验证码错 | 40101 | 401 | "验证码错误"(+剩余次数) | ✓ |
| verifyOtp | 验证码过期 | 41001 | 410 | "验证码已过期" | ✓ |
| verifyOtp | 锁定 | 41002 | 410 | "尝试过多，请重新获取" | ✓ |
| verifyOtp/oidc | 账户禁用 | 40301 | 403 | "账户已被禁用" | ✓ |
| oidcCallback | 方式关闭 | 40303 | 403 | "该登录方式不可用" | ✓ |
| oidcCallback | 未验证冲突 | 40902 | 409 | "请用原方式登录后绑定" | ✓ |
| oidcCallback | OIDC 超时 | 50401 | 504 | "改用邮箱验证码" | ✓ |
| oidcCallback | OIDC 不可达 | 50201 | 502 | "改用邮箱验证码" | ✓ |
| refreshToken | refresh 失效 | 40102 | 401 | "会话已失效，请重新登录" | ✓ |
| unbindIdentity | 解绑主邮箱 | 40304 | 403 | "主邮箱不可移除" | ✓ |
| unbindIdentity | 低于最小方式 | 40305 | 403 | "至少保留一种登录方式" | ✓ |
| revokeSession | 跨用户撤销 | 40300 | 403 | "无权限" | ✓ |
| bindIdentity | provider 占用 | 40903 | 409 | "该方式已被其他账户绑定" | ✓ |
| changePrimaryEmail | 邮箱被占用 | 40901 | 409 | "该邮箱已被使用" | ✓ |
| adminLogin | 凭据错 | 40103 | 401 | "邮箱或密码错误" | ✓ |
| adminLogin | 后台禁用 | 40302 | 403 | "管理员账户已被禁用" | ✓ |
| createAdmin | 邮箱重复 | 40901 | 409 | "邮箱已存在" | ✓ |
| deleteAdmin | 删自己 | 40307 | 403 | "不可删除当前登录账户" | ✓ |
| deleteAdmin/toggle | 超管保护 | 40306 | 403 | "超级管理员不可删除/禁用/降权" | ✓ |
| updateRole | 锁定角色 | 40308 | 403 | "预设锁定角色不可编辑权限" | ✓ |
| deleteRole | 角色有成员 | 40904 | 409 | "角色下仍有成员，请先迁移" | ✓ |
| updateAuthConfig | 配置越界 | 40002 | 422 | "认证配置数值超出合法区间" | ✓ |
| 跨端 token | store↔admin 误用 | 40100 | 401 | "未认证" | ✓ |
| 任意 | DB 错误 | 50001 | 500 | "数据操作失败" | — |
| 任意 | 未预期 | 50000 | 500 | "服务器内部错误" | — |

> 共 25 个 MUST_TEST 错误路径，全部纳入 test_designer 测试点。

---

## 3. 降级与重试（RT/CB/DG）
- RT-001 SMTP 发送（FLOW-15）：有限重试 3 次指数退避（1s/2s/4s）；仍失败 → EX-27(50002)，OTP 场景提示用户重试但不阻塞（OtpCode 已落库）
- RT-002 OIDC 验证（FLOW-03）：超时 5s，重试 1 次
- CB-001 OIDC 熔断：连续失败达阈值开启熔断窗口，期间快速失败 → 50201，统一引导 OTP（DG-001）
- DG-001 OIDC 不可用降级：前端收 502/504 自动切换 OTP 登录入口（REQ-008-05）
- DG-002 SMTP 沙箱无网络：切本地 stub（decision 5/15，stub_switch=true）
- DG-003 Redis 不可用：当前实例仍直接按 DB session/admin 状态鉴权；兼容键读写失败记告警，不产生继续授权窗口

---

## 4. 日志与脱敏规范
| 类别 | 规则 |
|------|------|
| OTP 明文 | 完全不记录，日志 [REDACTED]；仅存 code_hash |
| 密码 | 完全不记录；仅 password_hash |
| JWT/refresh | 仅记 jti/token_id，不记原文 |
| 邮箱 | 错误 message 不暴露原文（"该邮箱已被注册"）；日志可掩码 j***@email.com |
| Apple relay_email | 仅业务必要落库，日志脱敏 |
| 5xx | 不暴露 SQL/堆栈，仅 INTERNAL_ERROR |

- LOG-01 结构化日志关键路径：发码/校验/归并/强制下线/配置变更，含 traceId + 脱敏字段
- LOG-02 错误日志分级：4xx 业务异常 WARN/INFO；5xx ERROR + 告警
- 需脱敏的校验异常：InvalidEmailException 的 details 不回显完整邮箱原文

---

## 5. 国际化（i18n）
- store（/api/store/*）：按 Accept-Language 返回 en/es/fr，缺省 en；返回数字 code + 当前语言 message；前端按 code 映射本地化（未知 code 兜底 INTERNAL_ERROR 文案）
- admin（/api/admin/*）：固定中文 message_zh，不读 Accept-Language
- 邮件：EmailTemplate 按 (code, locale) 取三语模板，缺失回退默认 locale
- **ES/FR 占位翻译填充计划（I18N-PLAN）**：
  - error-strategy 中标注「部分为占位翻译」的 ES/FR message → 列入待校文案清单
  - 治理锚点：数字 code 稳定，文案变更不影响前端 code 映射逻辑
  - 落地：L3 集中维护 `messages_{en,es,fr}.properties` + email_template 12 条种子，专业翻译复核 ES/FR（标 [TRANSLATION_PENDING]）

## 6. 自检
- [x] 30 异常类映射 25 错误码，分层清晰
- [x] 25 MUST_TEST 错误路径标记
- [x] SMTP/OIDC 降级重试 + Redis 降级策略
- [x] 脱敏规则覆盖 OTP/密码/token/邮箱/relay
- [x] store 三语 + admin 中文 i18n 策略 + 占位翻译填充计划
