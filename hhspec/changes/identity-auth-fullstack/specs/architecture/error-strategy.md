# 错误策略 - identity（身份认证与用户域）

本文档定义 identity 限界上下文的分层错误处理、统一数字错误码体系、三语 i18n 策略，以及频控/限流/审计/数据保留对应的错误响应。依据 decision.md 决策 13/14 与 REQ-IDENTITY-007/005/008。

## 错误响应格式（统一）

所有错误返回统一结构（REQ-007-01）：

```json
{
  "code": 41001,
  "message": "Verification code expired",
  "details": { "remaining_resend_seconds": 0 }
}
```

- `code`: **数字码**，高 3 位对应 HTTP 状态（如 409xx → HTTP 409，410xx → HTTP 410，429xx → HTTP 429）。集中码表维护。
- `message`: 当前语言默认文案（store 按 Accept-Language 返回 EN/ES/FR；admin 固定中文）。
- `details`: 可选，字段级错误或剩余次数/剩余尝试等上下文。

## 分层错误处理

| 层级 | 职责 | 错误类型 | 处理方式 |
|------|------|----------|----------|
| **表示层（StoreAPI / AdminAPI + 鉴权过滤器）** | 捕获所有异常，转为统一 `{code,message,details}`；按 Accept-Language 本地化（store）；跨端 token 误用直接 401 | InvalidEmail, Unauthorized, Forbidden, RateLimited | 映射 HTTP 状态码 + 数字码；记录脱敏访问日志 |
| **应用层（领域服务 Svc）** | 业务规则校验，抛领域异常 | AccountDisabled, EmailConflict, IdentityTaken, RoleInUse, SuperAdminProtected, ConfigOutOfRange | 抛领域异常，由表示层捕获映射 |
| **领域层（聚合根）** | 维护不变量，抛领域异常 | OtpExpired, OtpLocked, PrimaryEmailRequired, MinMethodsRequired, InvalidStateTransition | 抛领域异常，由应用层传播 |
| **基础设施层（Repository / 集成端口）** | 数据访问与外部集成错误 | DatabaseError, SmtpError, OidcTimeout, OidcUnavailable | 转基础设施异常；SMTP 走重试降级，OIDC 超时/不可达映射 504/502 |

## 错误码体系

```yaml
error_codes:
  # ===== 400 参数错误 =====
  INVALID_EMAIL:
    code: 40001
    http_status: 422
    message_en: "Invalid email address"
    message_es: "Dirección de correo no válida"
    message_fr: "Adresse e-mail invalide"
    message_zh: "邮箱格式非法"
    description: "邮箱格式校验失败（EDGE-001）"
  CONFIG_OUT_OF_RANGE:
    code: 40002
    http_status: 422
    message_zh: "认证配置数值超出合法区间"
    description: "OTP 长度/有效期/重发/最大尝试/min_methods 越界（EDGE-019，仅后台中文）"
  VALIDATION_ERROR:
    code: 40000
    http_status: 422
    message_en: "Validation failed"
    message_zh: "参数校验失败"
    description: "通用字段校验失败，details 含字段级错误"

  # ===== 401 未认证 =====
  UNAUTHORIZED:
    code: 40100
    http_status: 401
    message_en: "Authentication required"
    message_es: "Se requiere autenticación"
    message_fr: "Authentification requise"
    message_zh: "未认证"
    description: "无 token / token 无效 / 跨端 token 误用（store token 访问 /api/admin/* 或反之）"
  OTP_INVALID:
    code: 40101
    http_status: 401
    message_en: "Incorrect verification code"
    message_es: "Código de verificación incorrecto"
    message_fr: "Code de vérification incorrect"
    message_zh: "验证码错误"
    description: "OTP 错误未达上限，attempts+1（EDGE-002），details.remaining_attempts"
  REFRESH_INVALID:
    code: 40102
    http_status: 401
    message_en: "Session expired, please sign in again"
    message_es: "Sesión expirada, inicie sesión de nuevo"
    message_fr: "Session expirée, reconnectez-vous"
    message_zh: "会话已失效，请重新登录"
    description: "refresh token 已撤销/过期（FLOW-04）"
  CREDENTIALS_INVALID:
    code: 40103
    http_status: 401
    message_zh: "邮箱或密码错误"
    description: "后台管理员凭据错误（仅中文）"

  # ===== 403 无权限 / 业务禁止 =====
  FORBIDDEN:
    code: 40300
    http_status: 403
    message_en: "Permission denied"
    message_es: "Permiso denegado"
    message_fr: "Autorisation refusée"
    message_zh: "无权限"
    description: "跨用户撤销会话（EDGE-009）/ 后台无菜单权限路由守卫拦截（EDGE-016）"
  ACCOUNT_DISABLED:
    code: 40301
    http_status: 403
    message_en: "Your account has been disabled"
    message_es: "Su cuenta ha sido deshabilitada"
    message_fr: "Votre compte a été désactivé"
    message_zh: "账户已被禁用"
    description: "消费端账户禁用，不签发会话（EDGE-006）"
  ADMIN_DISABLED:
    code: 40302
    http_status: 403
    message_zh: "管理员账户已被禁用"
    description: "后台管理员禁用（EDGE-011，仅中文）"
  PROVIDER_DISABLED:
    code: 40303
    http_status: 403
    message_en: "This sign-in method is unavailable"
    message_es: "Este método de inicio de sesión no está disponible"
    message_fr: "Cette méthode de connexion est indisponible"
    message_zh: "该登录方式已关闭"
    description: "AuthConfig 关闭的第三方登录入口（FUNC-006）"
  PRIMARY_EMAIL_REQUIRED:
    code: 40304
    http_status: 403
    message_en: "Primary email cannot be removed"
    message_es: "El correo principal no se puede eliminar"
    message_fr: "L'e-mail principal ne peut pas être supprimé"
    message_zh: "主邮箱不可移除"
    description: "解绑主邮箱被拒（EDGE-007）"
  MIN_METHODS_REQUIRED:
    code: 40305
    http_status: 403
    message_en: "At least one sign-in method must remain"
    message_es: "Debe conservar al menos un método de inicio de sesión"
    message_fr: "Vous devez conserver au moins une méthode de connexion"
    message_zh: "至少保留一种登录方式"
    description: "解绑后低于 min_methods（EDGE-008）"
  SUPER_ADMIN_PROTECTED:
    code: 40306
    http_status: 403
    message_zh: "超级管理员不可删除/禁用/降权"
    description: "保护超管（EDGE-014，仅中文）"
  CANNOT_DELETE_SELF:
    code: 40307
    http_status: 403
    message_zh: "不可删除当前登录账户"
    description: "删除自己被拒（EDGE-013，仅中文）"
  ROLE_LOCKED:
    code: 40308
    http_status: 403
    message_zh: "预设锁定角色不可编辑权限"
    description: "超管角色 is_locked（FUNC-018，仅中文）"

  # ===== 404 不存在 =====
  NOT_FOUND:
    code: 40400
    http_status: 404
    message_en: "Resource not found"
    message_zh: "资源不存在"
    description: "目标资源不存在"

  # ===== 409 冲突 =====
  EMAIL_EXISTS:
    code: 40901
    http_status: 409
    message_en: "This email is already in use"
    message_es: "Este correo ya está en uso"
    message_fr: "Cet e-mail est déjà utilisé"
    message_zh: "邮箱已存在"
    description: "管理员邮箱重复（EDGE-012）/ 换主邮箱被占用（EDGE-020）"
  EMAIL_CONFLICT_UNVERIFIED:
    code: 40902
    http_status: 409
    message_en: "Email matches an existing unverified account. Sign in with your original method, then link this option."
    message_es: "El correo coincide con una cuenta no verificada. Inicie sesión con su método original y luego vincule esta opción."
    message_fr: "L'e-mail correspond à un compte non vérifié. Connectez-vous avec votre méthode d'origine, puis liez cette option."
    message_zh: "邮箱命中未验证账户，请用原方式登录后绑定"
    description: "未验证邮箱冲突，不静默合并（EDGE-017, FUNC-028）；注销账户再登录亦走此流（EDGE-021）"
  IDENTITY_TAKEN:
    code: 40903
    http_status: 409
    message_en: "This sign-in method is already linked to another account"
    message_es: "Este método ya está vinculado a otra cuenta"
    message_fr: "Cette méthode est déjà liée à un autre compte"
    message_zh: "该登录方式已被其他账户绑定"
    description: "绑定时 (provider,provider_uid) 已被占用（FUNC-008）"
  ROLE_IN_USE:
    code: 40904
    http_status: 409
    message_zh: "角色下仍有成员，请先迁移"
    description: "删除有成员角色被拒（EDGE-015，仅中文）"

  # ===== 410 失效 =====
  OTP_EXPIRED:
    code: 41001
    http_status: 410
    message_en: "Verification code expired"
    message_es: "El código de verificación ha expirado"
    message_fr: "Le code de vérification a expiré"
    message_zh: "验证码已过期"
    description: "now > expires_at（EDGE-004）"
  OTP_LOCKED:
    code: 41002
    http_status: 410
    message_en: "Too many attempts, please request a new code"
    message_es: "Demasiados intentos, solicite un nuevo código"
    message_fr: "Trop de tentatives, demandez un nouveau code"
    message_zh: "尝试次数过多，请重新获取验证码"
    description: "attempts 达 max_attempts 锁定（EDGE-003）"

  # ===== 429 限流 =====
  RESEND_TOO_SOON:
    code: 42901
    http_status: 429
    message_en: "Please wait before requesting another code"
    message_es: "Espere antes de solicitar otro código"
    message_fr: "Veuillez patienter avant de demander un autre code"
    message_zh: "重发过于频繁，请稍后再试"
    description: "重发间隔未到 otp_resend_seconds（EDGE-005），details.remaining_resend_seconds"
  RATE_LIMITED:
    code: 42902
    http_status: 429
    message_en: "Too many requests, please try again later"
    message_es: "Demasiadas solicitudes, inténtelo más tarde"
    message_fr: "Trop de requêtes, réessayez plus tard"
    message_zh: "请求过于频繁，请稍后再试"
    description: "发码超频：单 email 5/h&5/d，单 IP 20/h（EDGE-022, REQ-005-05）"

  # ===== 500 / 502 / 504 服务端与外部集成 =====
  INTERNAL_ERROR:
    code: 50000
    http_status: 500
    message_en: "Something went wrong, please try again"
    message_es: "Algo salió mal, inténtelo de nuevo"
    message_fr: "Une erreur est survenue, réessayez"
    message_zh: "服务器内部错误"
    description: "未预期错误，不暴露细节"
  DATABASE_ERROR:
    code: 50001
    http_status: 500
    message_zh: "数据操作失败"
    description: "DB 访问失败（向上转换，不暴露 SQL）"
  EMAIL_SEND_FAILED:
    code: 50002
    http_status: 500
    message_en: "Failed to send email, please retry"
    message_es: "No se pudo enviar el correo, reintente"
    message_fr: "Échec de l'envoi de l'e-mail, réessayez"
    message_zh: "邮件发送失败，请重试"
    description: "SMTP 重试仍失败（FUNC-034）；OTP 场景提示用户重试，不阻塞主流程"
  OIDC_UNAVAILABLE:
    code: 50201
    http_status: 502
    message_en: "Sign-in provider unavailable, try email code instead"
    message_es: "Proveedor no disponible, use el código por correo"
    message_fr: "Fournisseur indisponible, utilisez le code e-mail"
    message_zh: "第三方登录不可用，请改用邮箱验证码"
    description: "OIDC 不可达，降级引导 OTP（REQ-008-05）"
  OIDC_TIMEOUT:
    code: 50401
    http_status: 504
    message_en: "Sign-in provider timed out, try email code instead"
    message_es: "Tiempo de espera agotado, use el código por correo"
    message_fr: "Délai dépassé, utilisez le code e-mail"
    message_zh: "第三方登录超时，请改用邮箱验证码"
    description: "OIDC 超时降级（FLOW-03）"
```

### 码表完整性核对（REQ-007-03 要求登记项）

| 需求登记项 | 错误码 |
|-----------|--------|
| 邮箱格式非法 | 40001 INVALID_EMAIL |
| OTP 过期 | 41001 OTP_EXPIRED |
| OTP 锁定 | 41002 OTP_LOCKED |
| 重发过频 | 42901 RESEND_TOO_SOON |
| 限流 | 42902 RATE_LIMITED |
| 消费端账户禁用 | 40301 ACCOUNT_DISABLED |
| 后台账户禁用 | 40302 ADMIN_DISABLED |
| 邮箱已存在 | 40901 EMAIL_EXISTS |
| 未验证邮箱冲突 | 40902 EMAIL_CONFLICT_UNVERIFIED |
| 角色被占用 | 40904 ROLE_IN_USE |
| 无权限 | 40300 FORBIDDEN |
| 主邮箱不可解绑 | 40304 PRIMARY_EMAIL_REQUIRED |
| 低于最小登录方式数 | 40305 MIN_METHODS_REQUIRED |

全部 13 项已登记，且无重复码、分类与 HTTP 状态一致。

## 国际化策略（REQ-007-04~06）

- **消费端（/api/store/\*）**: 按 `Accept-Language` 头或用户偏好选语，缺省 `en`，支持 `en/es/fr`。后端返回数字 `code` + 当前语言默认 `message`；前端按 `code` 映射本地化文案，未知 code 兜底通用提示（`INTERNAL_ERROR` 文案）。ES/FR 文本已就位 key 与结构（部分为占位翻译）。
- **后台（/api/admin/\*）**: 固定中文（`message_zh`），不读 Accept-Language。
- **邮件文案**: EmailTemplate 按 `(code, locale)` 维度存储 EN/ES/FR 三语模板，按用户语言选模板（REQ-008-02）。
- **文案治理**: 数字码为契约稳定锚点；文案变更不影响前端 code 映射逻辑。

## 频控与限流响应（BE-DIM-8 关联，REQ-005-04~07）

| 限流维度 | 阈值 | 后端实现 | 超限响应 |
|---------|------|---------|---------|
| 重发间隔 | ≥ otp_resend_seconds（默认 30s） | Redis key `otp:resend:{email}` TTL 窗口 | 429 `42901 RESEND_TOO_SOON`（details.remaining_resend_seconds） |
| 单 email 发码 | ≤ 5/h 且 ≤ 5/d | Redis 滑动窗口 `otp:count:email:{email}:h/:d` | 429 `42902 RATE_LIMITED` |
| 单 IP 发码 | ≤ 20/h | Redis 窗口 `otp:count:ip:{ip}:h` | 429 `42902 RATE_LIMITED` |
| 单码校验失败 | 达 otp_max_attempts（默认 5） | OtpCode.attempts（行锁串行，BE-DIM-4） | 410 `41002 OTP_LOCKED` |

- 计数全部走 Redis 窗口（非 DB 表），窗口到期自动清零（REQ-005-07）。
- 降级：限流仅作用于 OTP 发码/校验，不影响已签发会话；OIDC 不可用时降级引导 OTP（50201/50401）。

## 审计与可观测性响应（BE-DIM-7，REQ-008-06/07）

- **OperationLog（后台审计）**: 由 AOP 切面统一记录（FLOW-17），含 `action` + `before/after` 变更对比。**只读不可删**，无 delete 接口（EDGE-018）。系统自动归并以 `operator_name=系统` 写入。
  - 错误级别记审计的操作：创建/编辑/删除/禁用管理员、重置密码、角色权限变更、强制下线、认证配置变更、账户合并、登录。
  - 保留期限：1–3 年，按机构合规配置；**注销不删除审计日志**（GDPR Art.6(1)(f) 正当利益，时间受限，EDGE-026）。
- **LoginHistory（登录审计）**: 登录成功/失败均落库（FLOW-02/03），保留 1 年后清理。
- **结构化日志**: 后端关键路径（发码/校验/归并/强制下线/配置变更）记结构化日志。

## 敏感数据脱敏（强制）

| 类别 | 脱敏规则 |
|------|---------|
| OTP 验证码明文 | 完全不记录（仅存 code_hash），日志中 `[REDACTED]` |
| 管理员密码 | 完全不记录（仅存 password_hash），日志中 `[REDACTED]` |
| JWT / refresh token | 日志仅记 jti / token_id，不记 token 原文 |
| 邮箱 | 错误 message 不暴露具体邮箱原文（如「该邮箱已被注册」而非「xxx@xx 已注册」）；日志可记掩码 `j***@email.com` |
| Apple relay_email | 仅业务必要落库，日志脱敏 |

**错误消息约束**：
- 错误：`手机号 13812345678 已被注册` → 正确：`该手机号已被注册`
- 5xx 服务端错误完全不暴露内部细节（SQL/堆栈），仅返回通用 `INTERNAL_ERROR`。

## 数据保留对应错误/状态（REQ-008-08~13）

| 数据 | 保留策略 | 状态/响应 |
|------|---------|-----------|
| OtpCode | 已用/过期 24h 内清 | 清理后再校验同码 → 410 OTP_EXPIRED |
| revoked 会话 | 保留 30d 后清 | 清理前后 token 均判失效 |
| LoginHistory | 1 年后清 | — |
| 注销账户 PII | 30d 软删宽限 → 不可逆匿名化 | 匿名化后 status=anonymized；再登录走 40902 冲突流（不复活） |
| OperationLog | 1–3 年（注销不删） | 查询匿名化用户仍可见其审计日志 |

## 错误处理流程

### 表示层
1. 鉴权过滤器：按路由前缀选 store/admin 密钥解析 JWT；跨端误用 → 401 `40100`。
2. 捕获领域/基础设施异常 → 映射数字码 + HTTP 状态。
3. store 按 Accept-Language 本地化 message；admin 取中文。
4. 记录脱敏访问/错误日志。

### 应用层
1. 执行业务规则校验（归并/解绑/超管保护/配置区间）。
2. 违反规则抛领域异常，不捕获基础设施异常（向上传播）。

### 领域层
1. 聚合根维护不变量（OTP 状态机、min_methods、is_primary 唯一）。
2. 违反不变量抛领域异常。

### 基础设施层
1. DB 异常 → DATABASE_ERROR（50001）。
2. SMTP 异常 → 重试降级（FLOW-15），仍失败 EMAIL_SEND_FAILED（50002）。
3. OIDC 异常 → OIDC_TIMEOUT(50401)/OIDC_UNAVAILABLE(50201)，降级引导 OTP。

## L2 设计要求

L2 Error Designer 须在详设中：
1. 明确敏感字段清单与脱敏规则（OTP 明文/密码/token 全 REDACTED）。
2. 接口错误映射表「用户提示」列确保无敏感数据原文。
3. 标注需脱敏的异常类型（含用户输入的校验异常）。
4. 落地 ES/FR 占位文案的正式翻译填充计划。

## 检查清单

- [x] 错误码体系完整（覆盖 400/401/403/404/409/410/429/500/502/504 全部场景）
- [x] 错误码无重复
- [x] 错误码分类合理（高 3 位对应 HTTP 状态）
- [x] 每个错误码有 message（含三语，后台中文）和 description
- [x] 分层错误处理职责清晰
- [x] 错误响应格式统一（{code,message,details}）
- [x] REQ-007-03 全部 13 项登记项已覆盖
- [x] 频控/限流/审计/数据保留响应已落地（BE-DIM-7/8）
- [x] 敏感数据脱敏规则明确
