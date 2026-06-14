# identity 数据保留与匿名化方案（MySQL）

> 配套 `identity-physical-schema.md` / `identity-ddl.sql`。
> 来源：domain-model.md R6、data-flow.md FLOW-08/16、error-strategy.md「数据保留对应错误/状态」、REQ-IDENTITY-008。
> 合规依据：GDPR Art.6(1)(f) 正当利益（审计日志注销不删，时间受限，EDGE-026）。

## 1. 保留期总览

| 数据 | 表 | 保留策略 | 清理方式 | 来源 |
|------|----|---------|---------|------|
| OTP 验证码 | otp_code | 终态后 24h 清 | 物理 DELETE | FLOW-16 / REQ-008 |
| revoked 会话 | user_session | 30d 清 | 物理 DELETE | FLOW-16 / R6 |
| revoked 后台会话 | admin_session | 30d 清 [INFERRED] | 物理 DELETE | 复用会话策略 |
| 登录记录 | login_history | 1 年清 | 物理 DELETE | FLOW-16 / R6 |
| 操作日志 | operation_log | 1–3 年（注销不删） | 物理 DELETE（按合规配置） | error-strategy / EDGE-026 |
| 注销账户 PII | user + user_identity | 30d 软删宽限 → 不可逆匿名化 | UPDATE 匿名化 | FLOW-08 / R6 |

## 2. 软删 + 两阶段匿名化（user）

账户注销采用「软删 → 宽限 → 匿名化」两阶段，状态机：`active/disabled → deleted → anonymized`。

### 阶段 1：注销软删（FLOW-08，即时，事务内）

```
-- 领域服务事务（应用层执行，伪 SQL）
UPDATE user
   SET status = 'deleted', deleted_at = NOW(3), version = version + 1
 WHERE id = :userId AND status IN ('active','disabled');

UPDATE user_session
   SET status = 'revoked'
 WHERE user_id = :userId AND status = 'active';   -- 级联撤销全部会话
-- 同时清 Redis: store:user/identities/sessions/session:valid:* 全部 key
-- 发 account_deleted 邮件（FLOW-15）
```

此阶段 PII（email/name/phone/identity）**保留**，30 天内不可登录但数据在库（便于争议/合规追溯）。再次登录走 40902 EMAIL_CONFLICT_UNVERIFIED（不复活，EDGE-021）。

### 阶段 2：超 30 天不可逆匿名化（FLOW-16 定时任务，每日）

命中索引 `idx_user_status_deleted_at`：

```
-- 选取宽限期满的注销账户
SELECT id FROM user
 WHERE status = 'deleted' AND deleted_at < NOW(3) - INTERVAL 30 DAY;

-- 对每个命中 userId，单事务匿名化（不可逆）：
UPDATE user
   SET email = NULL,            -- 唯一索引允许多 NULL，互不冲突
       name = NULL,
       phone = NULL,
       avatar = NULL,
       status = 'anonymized',
       anonymized = 1,
       anonymized_at = NOW(3),
       version = version + 1
 WHERE id = :userId AND status = 'deleted';

-- 级联匿名化登录凭证 PII（保留行用于审计计数，但抹除可识别信息）
UPDATE user_identity
   SET provider_uid = CONCAT('anon:', id),   -- 维持唯一约束、抹除真实 sub/email
       identifier = NULL,
       relay_email = NULL,
       connected = 0
 WHERE user_id = :userId;
```

匿名化字段方案：
- **抹除（置 NULL）**：email、name、phone、avatar、identifier、relay_email。
- **替换（保唯一约束）**：user_identity.provider_uid → `anon:{identityId}`（避免 (provider,provider_uid) 唯一冲突，且不可逆）。
- **置位标记**：status=anonymized、anonymized=1、anonymized_at。
- **保留**：id（内部主键，无 PII）、tier、统计类时间戳。

> 不可逆性：原始 email/sub 不留备份，匿名化后无法还原；满足 GDPR 删除权与「不复活」要求。

## 3. 审计日志的特殊处理（正当利益）

- **operation_log**：注销/匿名化**不删除、不改写**。即使 operator 被匿名化，日志保留 `operator_name` 快照（写入时即固化）。查询匿名化用户仍可见其审计日志（error-strategy）。仅按 1–3 年合规期由清理任务删除。
- **login_history**：按 1 年保留期清理，不随注销即时删除（但 1 年内自然过期）。user_id 为弱引用可空，匿名化不回溯改写历史行。

## 4. 每日清理任务（FLOW-16）伪 SQL 与命中索引

| 任务 | 伪 SQL | 命中索引 |
|------|--------|---------|
| OTP 清理 | `DELETE FROM otp_code WHERE status IN ('consumed','expired','locked') AND created_at < NOW(3)-INTERVAL 24 HOUR` | idx_otp_status_created |
| 会话清理 | `DELETE FROM user_session WHERE status='revoked' AND created_at < NOW(3)-INTERVAL 30 DAY` | idx_session_status_created |
| 后台会话清理 [INFERRED] | `DELETE FROM admin_session WHERE status='revoked' AND created_at < NOW(3)-INTERVAL 30 DAY` | idx_admin_session_admin_status（status 前缀不命中；建议补 idx_admin_session_status_created，见下） |
| 登录历史清理 | `DELETE FROM login_history WHERE created_at < NOW(3)-INTERVAL 1 YEAR` | idx_login_created |
| 账户匿名化 | 见第 2 节阶段 2 | idx_user_status_deleted_at |
| 操作日志清理 | `DELETE FROM operation_log WHERE created_at < NOW(3)-INTERVAL :retain_years YEAR`（合规配置 1–3 年） | idx_oplog_created |

> 清理建议分批（`LIMIT N` 循环）避免大事务锁表；高写表（otp/session/login）尤需分批。

### 索引补充建议
- 若启用 admin_session 30d 清理，建议补充 `KEY idx_admin_session_status_created (status, created_at)` 以使清理走索引（当前 DDL 仅有 (admin_id,status)，status 非最左前缀）。本项标记 [INFERRED]，由 L3 按是否实际启用 admin_session 清理决定是否加索引。

## 5. 后续跟进事项

1. operation_log 保留年限（1 vs 3 年）取决于机构合规配置，应做成可配置参数（非硬编码）。
2. admin_session 是否纳入 30d 清理（domain R6 未单列）；若纳入需补索引（见第 4 节）。
3. 分批清理批大小与执行窗口（夜间低峰）由 L3/运维定。
4. 匿名化 provider_uid 替换为 `anon:{id}` 后，若同一自然人曾用多 provider，各 identity 行独立替换，不影响唯一性。
