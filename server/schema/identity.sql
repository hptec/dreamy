-- dreamy_server 空库自举 DDL(v2.3,权威设计:docs/identity-schema-v2.md)
-- 来源:用户四轮讨论定稿;生成日期:2026-09-14;v2.3(2026-09-15)收编 operation_log/
-- email_template(原 identity 库共享过渡表,Java 业务侧改经 gRPC AuditGate/TemplateGate),
-- auth_config 增 admin 登录失败锁定两列。
-- 规则:仅 CREATE IF NOT EXISTS,幂等,绝不 DROP/COPY/ALTER 存量。
-- 注意:存量库加列/两表数据搬迁走 scripts/migrate-shared-tables.sh(自举只建表)。
-- 时序表(login_history/otp_code)初始只带 p202501 低界分区,其余月分区由写入路径
-- (通用机制 common::partition)按需动态建(Redis 拦截 + 1526 自愈,无计划任务,无 pmax 哨兵):
-- 时间越界 INSERT 显式报错重试,不静默兜底。
-- user/user_identity 去 pmax:水位预扩(注册后 id+10万 探测)+ 1526 自愈兜底。

-- ══════════ 路由层(KEY 哈希 25 区,容量 1 亿,满载每区 400 万) ══════════

CREATE TABLE IF NOT EXISTS `identity_email` (
  `email`      VARCHAR(255) NOT NULL COMMENT '归一化小写;全局唯一由本表强制',
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`email`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由:邮箱 → 主账号(邮箱注册/归并域)'
  PARTITION BY KEY (`email`) PARTITIONS 25;

CREATE TABLE IF NOT EXISTS `identity_google` (
  `google_sub` VARCHAR(255) NOT NULL COMMENT 'Google OIDC sub',
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`google_sub`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由:Google 凭证 → 主账号'
  PARTITION BY KEY (`google_sub`) PARTITIONS 25;

CREATE TABLE IF NOT EXISTS `identity_apple` (
  `apple_sub`  VARCHAR(255) NOT NULL COMMENT 'Apple OIDC sub',
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `relay_email` VARCHAR(255) NULL COMMENT '隐藏邮箱时的中继地址(展示用,不参与归并)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`apple_sub`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路由:Apple 凭证 → 主账号'
  PARTITION BY KEY (`apple_sub`) PARTITIONS 25;

-- ══════════ 主档层(RANGE(id) 400 万段,co-location) ══════════

CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`         VARCHAR(255) NOT NULL COMMENT '展示快照;唯一性由 identity_email 强制',
  `email_verified` TINYINT(1) NOT NULL DEFAULT 0,
  `locale_pref`   VARCHAR(8) DEFAULT NULL,
  `name`          VARCHAR(100) DEFAULT NULL,
  `phone`         VARCHAR(32) DEFAULT NULL,
  `tier`          TINYINT NOT NULL DEFAULT 1 COMMENT '等级：1=常规 2=VIP',
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=已禁用 3=已删除 4=已匿名化',
  `avatar`        VARCHAR(512) DEFAULT NULL,
  `joined_at`     DATETIME DEFAULT NULL,
  `deleted_at`    DATETIME DEFAULT NULL,
  `anonymized`    TINYINT(1) NOT NULL DEFAULT 0,
  `anonymized_at` DATETIME DEFAULT NULL,
  `version`       INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_email` (`email`),
  KEY `idx_created_at` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主账号(自然人)'
  PARTITION BY RANGE (`id`) (
    PARTITION p0 VALUES LESS THAN (4000000),
    PARTITION p1 VALUES LESS THAN (8000000),
    PARTITION p2 VALUES LESS THAN (12000000)
  );

CREATE TABLE IF NOT EXISTS `user_identity` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'REST 契约暴露(解绑路径参数)',
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `provider`     TINYINT NOT NULL COMMENT '1=邮箱 2=Google 3=Apple',
  `provider_uid` VARCHAR(255) NOT NULL COMMENT '档案冗余;全局唯一由路由表强制',
  `identifier`   VARCHAR(255) DEFAULT NULL,
  `is_primary`   TINYINT(1) NOT NULL DEFAULT 0,
  `verified`     TINYINT(1) NOT NULL DEFAULT 0,
  `connected`    TINYINT(1) NOT NULL DEFAULT 1,
  `hidden_email` TINYINT(1) NOT NULL DEFAULT 0,
  `relay_email`  VARCHAR(255) DEFAULT NULL,
  `relay_valid`  TINYINT(1) DEFAULT NULL,
  `bound_at`     DATETIME DEFAULT NULL,
  `last_login_at` DATETIME DEFAULT NULL,
  `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`, `user_id`),
  UNIQUE KEY `uk_user_provider` (`user_id`, `provider`),
  KEY `idx_provider_uid` (`provider`, `provider_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='凭证档案(登录方式元数据);与 user 分区边界一致(co-location)'
  PARTITION BY RANGE (`user_id`) (
    PARTITION p0 VALUES LESS THAN (4000000),
    PARTITION p1 VALUES LESS THAN (8000000),
    PARTITION p2 VALUES LESS THAN (12000000)
  );

-- ══════════ 时序层(月分区,pmax 哨兵;DROP 清理暂缓=v2.2 决策 12) ══════════

CREATE TABLE IF NOT EXISTS `login_history` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '弱引用可空(登录失败无 user)',
  `email`         VARCHAR(255) DEFAULT NULL,
  `method`        TINYINT NOT NULL,
  `ip`            VARCHAR(64) DEFAULT NULL,
  `device`        VARCHAR(255) DEFAULT NULL,
  `location`      VARCHAR(255) DEFAULT NULL,
  `result`        TINYINT NOT NULL COMMENT '1=成功 2=失败',
  `is_new_device` TINYINT(1) NOT NULL DEFAULT 0,
  `notified`      TINYINT(1) NOT NULL DEFAULT 0,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='登录历史(审计);月分区(v3 写入路径动态建,无 pmax;清理暂缓)'
  PARTITION BY RANGE COLUMNS (`created_at`) (
    PARTITION p202501 VALUES LESS THAN ('2025-02-01')
  );

CREATE TABLE IF NOT EXISTS `otp_code` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email`       VARCHAR(255) NOT NULL,
  `code_hash`   VARCHAR(100) NOT NULL COMMENT 'bcrypt',
  `length`      TINYINT NOT NULL,
  `expires_at`  DATETIME NOT NULL,
  `attempts`    INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '1=待验证 2=已消费 3=过期 4=锁定',
  `last_sent_at` DATETIME DEFAULT NULL,
  `version`     INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `created_at`),
  KEY `idx_email_status` (`email`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='邮箱验证码(DB 主存);月分区(v3 写入路径动态建,无 pmax;清理暂缓)——校验查询必须带 created_at 下界谓词强制分区裁剪'
  PARTITION BY RANGE COLUMNS (`created_at`) (
    PARTITION p202501 VALUES LESS THAN ('2025-02-01')
  );

-- ══════════ 会话冷备(普通表不分区;Redis 为权威主存) ══════════

CREATE TABLE IF NOT EXISTS `user_session` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'REST 契约数字 session_id 来源(登录时同步 INSERT 取回,写入 Redis 会话 JSON)',
  `user_id`          BIGINT UNSIGNED NOT NULL,
  `token_id`         VARCHAR(64) NOT NULL COMMENT 'JWT jti',
  `refresh_token_id` VARCHAR(64) DEFAULT NULL,
  `access_expires_at`  DATETIME DEFAULT NULL,
  `refresh_expires_at` DATETIME DEFAULT NULL,
  `device`           VARCHAR(255) DEFAULT NULL,
  `browser`          VARCHAR(255) DEFAULT NULL,
  `ip`               VARCHAR(64) DEFAULT NULL,
  `location`         VARCHAR(255) DEFAULT NULL,
  `is_new_device`    TINYINT(1) NOT NULL DEFAULT 0,
  `method`           TINYINT NOT NULL,
  `status`           TINYINT NOT NULL DEFAULT 1 COMMENT '1=有效 2=已撤销',
  `last_active_at`   DATETIME DEFAULT NULL,
  `version`          INT NOT NULL DEFAULT 0,
  `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='会话冷备/审计(Redis 为权威主存;Redis 故障降级 uk_token 点查;暂不清理=v2.2 决策 12)';

-- ══════════ admin/RBAC/配置(量级 ≤ 千行,不分区) ══════════

CREATE TABLE IF NOT EXISTS `admin_user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100) DEFAULT NULL,
  `email`         VARCHAR(255) NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  `role_id`       BIGINT NOT NULL,
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 2=已禁用',
  `last_login_at` DATETIME DEFAULT NULL,
  `version`       INT NOT NULL DEFAULT 0,
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员';

CREATE TABLE IF NOT EXISTS `admin_session` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id`      BIGINT NOT NULL,
  `token_id`      VARCHAR(64) NOT NULL,
  `ip`            VARCHAR(64) DEFAULT NULL,
  `device`        VARCHAR(255) DEFAULT NULL,
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1=有效 2=已撤销',
  `last_active_at` DATETIME DEFAULT NULL,
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_session_token` (`token_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员会话';

CREATE TABLE IF NOT EXISTS `role` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(40) NOT NULL COMMENT '角色名',
  `type`       TINYINT NOT NULL DEFAULT 2 COMMENT '1=系统预设 2=自定义',
  `is_locked`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '超管标记:锁定角色=全权限',
  `version`    INT NOT NULL DEFAULT 0,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS `permission` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `perm_code`  VARCHAR(64) NOT NULL COMMENT '权限码(/xxx 形式)',
  `group`      VARCHAR(64) NOT NULL COMMENT '分组',
  `label`      VARCHAR(100) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限字典';

CREATE TABLE IF NOT EXISTS `role_permission` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联';

CREATE TABLE IF NOT EXISTS `auth_config` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email_enabled`      TINYINT(1) NOT NULL DEFAULT 1,
  `google_enabled`     TINYINT(1) NOT NULL DEFAULT 1,
  `apple_enabled`      TINYINT(1) NOT NULL DEFAULT 1,
  `otp_length`         TINYINT NOT NULL DEFAULT 6 COMMENT '4/6/8',
  `otp_ttl_minutes`    INT NOT NULL DEFAULT 5 COMMENT '1-30',
  `otp_resend_seconds` INT NOT NULL DEFAULT 60 COMMENT '10-120',
  `otp_max_attempts`   INT NOT NULL DEFAULT 5 COMMENT '3-10',
  `min_methods`        TINYINT NOT NULL DEFAULT 1 COMMENT '1-3',
  `admin_login_max_attempts` INT NOT NULL DEFAULT 5 COMMENT 'admin 登录失败锁定阈值 3-10',
  `admin_login_lock_minutes` INT NOT NULL DEFAULT 15 COMMENT 'admin 登录锁定时长 5-60 分钟',
  `google_client_id`   VARCHAR(255) DEFAULT NULL,
  `apple_service_id`   VARCHAR(255) DEFAULT NULL,
  `created_at`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认证配置(单例 id=1)';

-- ══════════ 运营审计与邮件模板(自 identity 库收编;Java 业务侧经 gRPC 写读) ══════════

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `operator_id`   BIGINT DEFAULT NULL COMMENT '弱引用 admin_user.id,系统操作为 NULL',
  `operator_name` VARCHAR(100) DEFAULT NULL,
  `action`        VARCHAR(32) NOT NULL,
  `target`        VARCHAR(255) DEFAULT NULL,
  `ip`            VARCHAR(64) DEFAULT NULL,
  `user_agent`    VARCHAR(512) DEFAULT NULL,
  `changes`       TEXT COMMENT '变更前后对比 JSON {before,after}',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_oplog_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志(只增不删)';

CREATE TABLE IF NOT EXISTS `email_template` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(32) NOT NULL,
  `locale`     VARCHAR(8) NOT NULL,
  `subject`    VARCHAR(255) NOT NULL,
  `body`       TEXT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code_locale` (`code`,`locale`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板(种子由 Rust 启动自举:身份域 4 code + 业务域 10 code)';
