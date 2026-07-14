-- =============================================================================
-- identity 限界上下文 物理库 DDL（MySQL 8.0+）
-- change: identity-auth-fullstack  domain: identity  mode: bootstrap
-- 引擎: InnoDB  字符集: utf8mb4  排序: utf8mb4_0900_ai_ci
-- 约定:
--   - UUID 主键: CHAR(36)；permission 业务主键 key；auth_config 单例 INT=1
--   - 枚举: VARCHAR + CHECK（不用 MySQL ENUM，利于演进）
--   - 时间: DATETIME(3) 存 UTC
--   - 外键: 全表无物理 FOREIGN KEY，引用完整性由应用层 + 事务维护
--   - 乐观锁: 并发竞争表含 version INT
-- 来源: er-diagram.yml（13 实体）/ domain-model.md / data-flow.md(FLOW-01~17)
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. user（自然人账户 / 聚合根）
-- -----------------------------------------------------------------------------
CREATE TABLE `user` (
  `id`             CHAR(36)     NOT NULL                       COMMENT '用户 UUID',
  `email`          VARCHAR(255) NULL                           COMMENT '主邮箱（匿名化后置 NULL）',
  `email_verified` TINYINT(1)   NOT NULL DEFAULT 0             COMMENT '主邮箱是否已验证（归并判定）',
  `name`           VARCHAR(80)  NULL                           COMMENT '姓名',
  `phone`          VARCHAR(32)  NULL                           COMMENT '电话',
  `tier`           VARCHAR(16)  NOT NULL DEFAULT 'regular'     COMMENT '会员等级 vip/regular',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'active'      COMMENT '账户状态 active/disabled/deleted/anonymized',
  `avatar`         VARCHAR(512) NULL                           COMMENT '头像 URL',
  `joined_at`      DATETIME(3)  NULL                           COMMENT '注册时间',
  `deleted_at`     DATETIME(3)  NULL                           COMMENT '软删除时间（注销）',
  `anonymized`     TINYINT(1)   NOT NULL DEFAULT 0             COMMENT '是否已匿名化',
  `anonymized_at`  DATETIME(3)  NULL                           COMMENT '匿名化时间',
  `version`        INT          NOT NULL DEFAULT 0             COMMENT '乐观锁',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_status` (`status`),
  KEY `idx_user_status_deleted_at` (`status`, `deleted_at`),
  KEY `idx_user_tier` (`tier`),
  CONSTRAINT `ck_user_tier`   CHECK (`tier`   IN ('vip','regular')),
  CONSTRAINT `ck_user_status` CHECK (`status` IN ('active','disabled','deleted','anonymized'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自然人账户（聚合根）';

-- -----------------------------------------------------------------------------
-- 2. user_identity（登录凭证）
-- -----------------------------------------------------------------------------
CREATE TABLE `user_identity` (
  `id`            CHAR(36)     NOT NULL                       COMMENT '凭证 UUID',
  `user_id`       CHAR(36)     NOT NULL                       COMMENT '所属用户（逻辑外键→user.id）',
  `provider`      VARCHAR(16)  NOT NULL                       COMMENT '渠道 email/google/apple',
  `provider_uid`  VARCHAR(255) NOT NULL                       COMMENT '渠道唯一标识（email=邮箱小写 / OIDC=sub）',
  `identifier`    VARCHAR(255) NULL                           COMMENT '展示用标识',
  `is_primary`    TINYINT(1)   NOT NULL DEFAULT 0             COMMENT '是否主邮箱凭证',
  `verified`      TINYINT(1)   NOT NULL DEFAULT 0             COMMENT '是否已验证',
  `connected`     TINYINT(1)   NOT NULL DEFAULT 1             COMMENT '是否已连接（解绑置 0）',
  `hidden_email`  TINYINT(1)   NULL     DEFAULT 0             COMMENT 'Apple Hide My Email',
  `relay_email`   VARCHAR(255) NULL                           COMMENT 'Apple relay 邮箱',
  `relay_valid`   TINYINT(1)   NULL     DEFAULT 1             COMMENT 'relay 是否有效（FUNC-029，失效不锁账户）',
  `bound_at`      DATETIME(3)  NULL                           COMMENT '绑定时间',
  `last_login_at` DATETIME(3)  NULL                           COMMENT '最近登录时间',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_provider_uid` (`provider`, `provider_uid`),
  KEY `idx_identity_user_id` (`user_id`),
  KEY `idx_identity_user_primary` (`user_id`, `is_primary`),
  CONSTRAINT `ck_identity_provider` CHECK (`provider` IN ('email','google','apple'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录凭证（(provider,provider_uid) 全局唯一）';

-- -----------------------------------------------------------------------------
-- 3. otp_code（一次性验证码）
-- -----------------------------------------------------------------------------
CREATE TABLE `otp_code` (
  `id`           CHAR(36)     NOT NULL                       COMMENT 'UUID',
  `email`        VARCHAR(255) NOT NULL                       COMMENT '目标邮箱',
  `code_hash`    VARCHAR(255) NOT NULL                       COMMENT 'OTP 哈希（绝不存明文）',
  `length`       TINYINT      NOT NULL                       COMMENT '验证码长度 4/6/8',
  `expires_at`   DATETIME(3)  NOT NULL                       COMMENT '过期时间',
  `attempts`     INT          NOT NULL DEFAULT 0             COMMENT '已尝试次数',
  `max_attempts` INT          NOT NULL                       COMMENT '最大尝试次数 3..10',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'pending'     COMMENT 'pending/consumed/expired/locked',
  `last_sent_at` DATETIME(3)  NULL                           COMMENT '最近发送时间（重发间隔判定）',
  `version`      INT          NOT NULL DEFAULT 0             COMMENT '乐观锁（防并发绕过 attempts）',
  `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_otp_email_status` (`email`, `status`),
  KEY `idx_otp_status_created` (`status`, `created_at`),
  CONSTRAINT `ck_otp_length`   CHECK (`length` IN (4,6,8)),
  CONSTRAINT `ck_otp_attempts` CHECK (`attempts` >= 0),
  CONSTRAINT `ck_otp_max`      CHECK (`max_attempts` BETWEEN 3 AND 10),
  CONSTRAINT `ck_otp_status`   CHECK (`status` IN ('pending','consumed','expired','locked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OTP 验证码（仅存哈希，24h 清理）';

-- -----------------------------------------------------------------------------
-- 4. user_session（消费端会话）
-- -----------------------------------------------------------------------------
CREATE TABLE `user_session` (
  `id`                 CHAR(36)     NOT NULL                  COMMENT 'UUID',
  `user_id`            CHAR(36)     NOT NULL                  COMMENT '所属用户（逻辑外键→user.id）',
  `token_id`           VARCHAR(64)  NOT NULL                  COMMENT 'access JWT jti',
  `refresh_token_id`   VARCHAR(64)  NULL                      COMMENT 'refresh JWT jti',
  `access_expires_at`  DATETIME(3)  NULL                      COMMENT 'access 过期（2h）',
  `refresh_expires_at` DATETIME(3)  NULL                      COMMENT 'refresh 过期（30d 滑动）',
  `device`             VARCHAR(128) NULL                      COMMENT '设备',
  `browser`            VARCHAR(128) NULL                      COMMENT '浏览器',
  `ip`                 VARCHAR(45)  NULL                      COMMENT 'IP（兼容 IPv6）',
  `location`           VARCHAR(128) NULL                      COMMENT '地理位置',
  `is_new_device`      TINYINT(1)   NULL     DEFAULT 0        COMMENT '是否新设备',
  `method`             VARCHAR(16)  NOT NULL                  COMMENT '登录方式 email/google/apple',
  `status`             VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active/revoked',
  `last_active_at`     DATETIME(3)  NULL                      COMMENT '最近活跃',
  `version`            INT          NOT NULL DEFAULT 0        COMMENT '乐观锁（滑动续期并发）',
  `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_token_id` (`token_id`),
  KEY `idx_session_user_status` (`user_id`, `status`),
  KEY `idx_session_refresh` (`refresh_token_id`),
  KEY `idx_session_status_created` (`status`, `created_at`),
  CONSTRAINT `ck_session_method` CHECK (`method` IN ('email','google','apple')),
  CONSTRAINT `ck_session_status` CHECK (`status` IN ('active','revoked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费端会话（revoked 30d 清理）';

-- -----------------------------------------------------------------------------
-- 5. login_history（登录记录，追加型，1 年保留）
-- -----------------------------------------------------------------------------
CREATE TABLE `login_history` (
  `id`            CHAR(36)     NOT NULL                  COMMENT 'UUID',
  `user_id`       CHAR(36)     NULL                      COMMENT '用户（弱引用→user.id，可空）',
  `email`         VARCHAR(255) NULL                      COMMENT '登录邮箱',
  `method`        VARCHAR(16)  NOT NULL                  COMMENT '登录方式 email/google/apple',
  `ip`            VARCHAR(45)  NULL                      COMMENT 'IP',
  `device`        VARCHAR(128) NULL                      COMMENT '设备',
  `location`      VARCHAR(128) NULL                      COMMENT '位置',
  `result`        VARCHAR(16)  NOT NULL                  COMMENT 'success/failed',
  `is_new_device` TINYINT(1)   NULL     DEFAULT 0        COMMENT '是否新设备',
  `notified`      TINYINT(1)   NULL     DEFAULT 0        COMMENT '新设备通知是否已发（唯一可更新字段）',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_user_created` (`user_id`, `created_at`),
  KEY `idx_login_created` (`created_at`),
  KEY `idx_login_email_created` (`email`, `created_at`),
  CONSTRAINT `ck_login_method` CHECK (`method` IN ('email','google','apple')),
  CONSTRAINT `ck_login_result` CHECK (`result` IN ('success','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录记录（追加型，1 年保留）';

-- -----------------------------------------------------------------------------
-- 7. role（角色 / 聚合根）—— 先于 admin_user 建（admin_user 逻辑引用 role）
-- -----------------------------------------------------------------------------
CREATE TABLE `role` (
  `id`         CHAR(36)    NOT NULL                    COMMENT 'UUID',
  `name`       VARCHAR(40) NOT NULL                    COMMENT '角色名',
  `type`       VARCHAR(16) NOT NULL DEFAULT 'custom'   COMMENT 'preset/custom',
  `is_locked`  TINYINT(1)  NOT NULL DEFAULT 0          COMMENT '锁定（超管=1，不可改/删/降权）',
  `version`    INT         NOT NULL DEFAULT 0          COMMENT '乐观锁',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_name` (`name`),
  CONSTRAINT `ck_role_type` CHECK (`type` IN ('preset','custom'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色（聚合根）';

-- -----------------------------------------------------------------------------
-- 6. admin_user（后台操作员 / 聚合根）
-- -----------------------------------------------------------------------------
CREATE TABLE `admin_user` (
  `id`            CHAR(36)     NOT NULL                    COMMENT 'UUID',
  `name`          VARCHAR(80)  NOT NULL                    COMMENT '姓名',
  `email`         VARCHAR(255) NOT NULL                    COMMENT '登录邮箱（唯一，创建后不可改）',
  `password_hash` VARCHAR(255) NOT NULL                    COMMENT '密码哈希（BCrypt）',
  `role_id`       CHAR(36)     NOT NULL                    COMMENT '所属角色（逻辑外键→role.id）',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'active'   COMMENT 'active/disabled',
  `last_login_at` DATETIME(3)  NULL                        COMMENT '最近登录',
  `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_email` (`email`),
  KEY `idx_admin_role` (`role_id`),
  KEY `idx_admin_status` (`status`),
  CONSTRAINT `ck_admin_status` CHECK (`status` IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作员（聚合根）';

-- -----------------------------------------------------------------------------
-- 8. permission（菜单权限点 / 字典；key、group 为保留字需反引号）
-- -----------------------------------------------------------------------------
CREATE TABLE `permission` (
  `key`   VARCHAR(64) NOT NULL                COMMENT '权限点 key（业务主键，如 /system/admins）',
  `group` VARCHAR(64) NOT NULL                COMMENT '权限分组',
  `label` VARCHAR(80) NOT NULL                COMMENT '显示名',
  PRIMARY KEY (`key`),
  KEY `idx_permission_group` (`group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限点字典（22 项）';

-- -----------------------------------------------------------------------------
-- 9. role_permission（角色-权限关联 / 复合主键防重复）
-- -----------------------------------------------------------------------------
CREATE TABLE `role_permission` (
  `role_id`        CHAR(36)    NOT NULL  COMMENT '角色（逻辑外键→role.id）',
  `permission_key` VARCHAR(64) NOT NULL  COMMENT '权限点（逻辑外键→permission.key）',
  PRIMARY KEY (`role_id`, `permission_key`),
  KEY `idx_rp_permission` (`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联（FLOW-11 全量重写）';

-- -----------------------------------------------------------------------------
-- 10. admin_session（后台会话 / access 8h 无 refresh）
-- -----------------------------------------------------------------------------
CREATE TABLE `admin_session` (
  `id`             CHAR(36)     NOT NULL                    COMMENT 'UUID',
  `admin_id`       CHAR(36)     NOT NULL                    COMMENT '所属管理员（逻辑外键→admin_user.id）',
  `token_id`       VARCHAR(64)  NOT NULL                    COMMENT 'JWT jti',
  `ip`             VARCHAR(45)  NULL                        COMMENT 'IP',
  `device`         VARCHAR(128) NULL                        COMMENT '设备',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'active'   COMMENT 'active/revoked',
  `last_active_at` DATETIME(3)  NULL                        COMMENT '最近活跃',
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_session_token` (`token_id`),
  KEY `idx_admin_session_admin_status` (`admin_id`, `status`),
  CONSTRAINT `ck_admin_session_status` CHECK (`status` IN ('active','revoked'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台会话（revoked 30d 清理）';

-- -----------------------------------------------------------------------------
-- 11. operation_log（操作日志，追加型，只读不可删，1-3 年保留）
-- 演进建议: 达百万行/月后改 PARTITION BY RANGE (TO_DAYS(created_at)) 按月分区
-- -----------------------------------------------------------------------------
CREATE TABLE `operation_log` (
  `id`            CHAR(36)     NOT NULL                  COMMENT 'UUID',
  `operator_id`   CHAR(36)     NULL                      COMMENT '操作者（弱引用→admin_user.id；系统=NULL）',
  `operator_name` VARCHAR(80)  NOT NULL                  COMMENT '操作者名（系统归并=系统）',
  `action`        VARCHAR(32)  NOT NULL                  COMMENT '操作类型（15 种枚举）',
  `target`        VARCHAR(255) NULL                      COMMENT '操作目标',
  `ip`            VARCHAR(45)  NULL                      COMMENT 'IP',
  `user_agent`    VARCHAR(512) NULL                      COMMENT 'UA',
  `changes`       JSON         NULL                      COMMENT '变更前后对比 {before,after}',
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_oplog_created` (`created_at`),
  KEY `idx_oplog_operator_created` (`operator_id`, `created_at`),
  KEY `idx_oplog_action_created` (`action`, `created_at`),
  CONSTRAINT `ck_oplog_action` CHECK (`action` IN (
    '登录','Google 登录','Apple 登录','创建管理员','编辑管理员','删除管理员',
    '禁用管理员','重置密码','创建角色','编辑角色','删除角色','权限变更',
    '账户合并','强制下线','认证配置变更'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志（只读不可删，1-3 年保留）';

-- -----------------------------------------------------------------------------
-- 12. auth_config（认证配置 / 单例 id=1）
-- -----------------------------------------------------------------------------
CREATE TABLE `auth_config` (
  `id`                 INT          NOT NULL                COMMENT '单例主键（固定=1）',
  `email_enabled`      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '邮箱登录（恒开不可关）',
  `google_enabled`     TINYINT(1)   NOT NULL DEFAULT 1      COMMENT 'Google 登录开关',
  `apple_enabled`      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT 'Apple 登录开关',
  `otp_length`         TINYINT      NOT NULL DEFAULT 6      COMMENT 'OTP 长度 4/6/8',
  `otp_ttl_minutes`    INT          NOT NULL DEFAULT 10     COMMENT 'OTP 有效期 1..30 分钟',
  `otp_resend_seconds` INT          NOT NULL DEFAULT 60     COMMENT '重发间隔 10..120 秒',
  `otp_max_attempts`   INT          NOT NULL DEFAULT 5      COMMENT '最大尝试 3..10',
  `min_methods`        INT          NOT NULL DEFAULT 1      COMMENT '最少连接方式 1..3',
  `google_client_id`   VARCHAR(255) NULL                    COMMENT 'Google 客户端 ID（只读展示）',
  `apple_service_id`   VARCHAR(255) NULL                    COMMENT 'Apple Service ID（只读展示）',
  `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_cfg_otp_length`   CHECK (`otp_length` IN (4,6,8)),
  CONSTRAINT `ck_cfg_otp_ttl`      CHECK (`otp_ttl_minutes` BETWEEN 1 AND 30),
  CONSTRAINT `ck_cfg_otp_resend`   CHECK (`otp_resend_seconds` BETWEEN 10 AND 120),
  CONSTRAINT `ck_cfg_otp_max`      CHECK (`otp_max_attempts` BETWEEN 3 AND 10),
  CONSTRAINT `ck_cfg_min_methods`  CHECK (`min_methods` BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认证配置（单例 id=1）';

-- -----------------------------------------------------------------------------
-- 13. email_template（邮件模板 / 三语 × 4 类）
-- -----------------------------------------------------------------------------
CREATE TABLE `email_template` (
  `id`         CHAR(36)     NOT NULL                COMMENT 'UUID',
  `code`       VARCHAR(32)  NOT NULL                COMMENT 'otp/new_device/change_primary/account_deleted',
  `locale`     VARCHAR(8)   NOT NULL                COMMENT 'en/es/fr',
  `subject`    VARCHAR(255) NOT NULL                COMMENT '邮件主题',
  `body`       TEXT         NOT NULL                COMMENT '邮件正文',
  `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code_locale` (`code`, `locale`),
  CONSTRAINT `ck_tpl_code`   CHECK (`code`   IN ('otp','new_device','change_primary','account_deleted')),
  CONSTRAINT `ck_tpl_locale` CHECK (`locale` IN ('en','es','fr'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板（三语 × 4 类）';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 种子数据（Bootstrap 初始化）
-- 注: UUID 用占位符，L3 落地时由迁移脚本生成。不在 DDL 中预置可登录账户或固定密码；
-- 新环境由运行时 DataInitializer 读取 DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD 显式创建首个超管。
-- =============================================================================

-- 12.seed auth_config 单例默认行
INSERT INTO `auth_config`
  (`id`,`email_enabled`,`google_enabled`,`apple_enabled`,`otp_length`,`otp_ttl_minutes`,`otp_resend_seconds`,`otp_max_attempts`,`min_methods`)
VALUES
  (1, 1, 1, 1, 6, 10, 60, 5, 1);

-- 7.seed 超级管理员角色（preset, locked）
INSERT INTO `role` (`id`,`name`,`type`,`is_locked`) VALUES
  ('00000000-0000-0000-0000-000000000001', '超级管理员', 'preset', 1);

-- 6.bootstrap 超级管理员账户：不写入静态 seed。
-- 必须在首次启动前显式配置 DREAMY_BOOTSTRAP_ADMIN_EMAIL 与
-- DREAMY_BOOTSTRAP_ADMIN_PASSWORD（至少 12 字符），由应用以 BCrypt 哈希后幂等创建。

-- 8.seed permission 菜单权限点字典（示例，22 项完整清单由 L3 按 portal-admin 路由补全）
INSERT INTO `permission` (`key`,`group`,`label`) VALUES
  ('/dashboard',        '概览',   '仪表盘'),
  ('/customers',        '用户',   '用户管理'),
  ('/system/admins',    '系统',   '管理员管理'),
  ('/system/roles',     '系统',   '角色权限'),
  ('/system/auth',      '系统',   '认证配置'),
  ('/system/oplogs',    '系统',   '操作日志');
  -- ... 其余 16 项菜单 key 由 L3 补全 ...

-- 9.seed 超管角色 × 全部权限（可选方案 a；若采用应用层短路则跳过）
-- INSERT INTO `role_permission` (`role_id`,`permission_key`)
--   SELECT '00000000-0000-0000-0000-000000000001', `key` FROM `permission`;

-- 13.seed 邮件模板（en 示例；es/fr 与三语全文案由 L3/内容方补全）
INSERT INTO `email_template` (`id`,`code`,`locale`,`subject`,`body`) VALUES
  ('00000000-0000-0000-0000-0000000000e1','otp','en','Your Dreamy verification code','Your code is {{code}}. It expires in {{ttl}} minutes.');
  -- ... 其余 11 条（otp/new_device/change_primary/account_deleted × en/es/fr）由 L3 补全 ...
