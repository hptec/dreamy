-- dreamy_server 空库自举 DDL(11 张身份域表)
-- 来源:本地全栈验证库 identity(huihao-mysql auto-DDL 建表)mysqldump --no-data
-- 生成日期:2026-09-14 规则:仅 CREATE IF NOT EXISTS,已剥 AUTO_INCREMENT 当前值,绝不 DROP/ALTER
-- 注意:operation_log/email_template 留 identity 库(共享过渡表),不在本文件
CREATE TABLE IF NOT EXISTS `user` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `email` varchar(255) NOT NULL COMMENT '邮箱',
  `email_verified` tinyint(1) NOT NULL DEFAULT '0' COMMENT '邮箱已验证',
  `locale_pref` varchar(8) DEFAULT NULL COMMENT '用户偏好语言(en/es/fr,决策13/FUNC-019)',
  `name` varchar(100) DEFAULT NULL COMMENT '昵称',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号',
  `tier` tinyint NOT NULL DEFAULT '1' COMMENT '等级：1=常规 2=VIP',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=正常 2=已禁用 3=已删除 4=已匿名化',
  `avatar` varchar(512) DEFAULT NULL COMMENT '头像 URL',
  `joined_at` datetime DEFAULT NULL COMMENT '加入时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `anonymized` tinyint(1) NOT NULL DEFAULT '0' COMMENT '已匿名化',
  `anonymized_at` datetime DEFAULT NULL COMMENT '匿名化时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自然人账户';

CREATE TABLE IF NOT EXISTS `user_identity` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '外键 user.id',
  `provider` tinyint NOT NULL COMMENT '渠道：1=邮箱 2=Google 3=Apple',
  `provider_uid` varchar(255) NOT NULL COMMENT '渠道唯一标识 email=邮箱小写/OIDC=sub',
  `identifier` varchar(255) DEFAULT NULL COMMENT '展示标识',
  `is_primary` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否主身份',
  `verified` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已验证',
  `connected` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否已连接',
  `hidden_email` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否隐藏邮箱 Apple Hide My Email',
  `relay_email` varchar(255) DEFAULT NULL COMMENT '中继邮箱',
  `relay_valid` tinyint(1) DEFAULT NULL COMMENT '中继邮箱是否有效',
  `bound_at` datetime DEFAULT NULL COMMENT '绑定时间',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_provider_uid` (`provider`,`provider_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录凭证';

CREATE TABLE IF NOT EXISTS `user_session` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '关联用户 user.id',
  `token_id` varchar(64) NOT NULL COMMENT 'access JWT jti',
  `refresh_token_id` varchar(64) DEFAULT NULL COMMENT 'refresh JWT jti',
  `access_expires_at` datetime DEFAULT NULL COMMENT 'access 过期时间',
  `refresh_expires_at` datetime DEFAULT NULL COMMENT 'refresh 过期时间',
  `device` varchar(255) DEFAULT NULL COMMENT '设备信息',
  `browser` varchar(128) DEFAULT NULL COMMENT '浏览器信息',
  `ip` varchar(64) DEFAULT NULL COMMENT '登录 IP',
  `location` varchar(255) DEFAULT NULL COMMENT '登录地点',
  `is_new_device` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否新设备',
  `method` tinyint NOT NULL COMMENT '登录方式：1=邮箱 2=Google 3=Apple',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=活跃 2=已撤销',
  `last_active_at` datetime DEFAULT NULL COMMENT '最近活跃时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_token_id` (`token_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费端会话';

CREATE TABLE IF NOT EXISTS `otp_code` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `email` varchar(255) NOT NULL COMMENT '邮箱',
  `code_hash` varchar(255) NOT NULL COMMENT 'OTP 哈希（仅哈希）',
  `length` int NOT NULL COMMENT '验证码长度 4/6/8',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `max_attempts` int NOT NULL COMMENT '最大尝试次数 3..10',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=待验证 2=已消耗 3=已过期 4=已锁定',
  `last_sent_at` datetime DEFAULT NULL COMMENT '最近发送时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_otp_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次性验证码';

CREATE TABLE IF NOT EXISTS `auth_config` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `email_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '邮箱登录启用（恒 true）',
  `google_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Google 登录启用',
  `apple_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Apple 登录启用',
  `otp_length` int NOT NULL DEFAULT '6' COMMENT 'OTP 长度 4/6/8',
  `otp_ttl_minutes` int NOT NULL DEFAULT '5' COMMENT 'OTP 有效期 1..30 分钟',
  `otp_resend_seconds` int NOT NULL DEFAULT '60' COMMENT '重发间隔 10..120 秒',
  `otp_max_attempts` int NOT NULL DEFAULT '5' COMMENT '最大尝试 3..10',
  `min_methods` int NOT NULL DEFAULT '1' COMMENT '最少连接方式 1..3',
  `google_client_id` varchar(255) DEFAULT NULL COMMENT 'Google Client ID',
  `apple_service_id` varchar(255) DEFAULT NULL COMMENT 'Apple Service ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认证配置（单例 id=1）';

CREATE TABLE IF NOT EXISTS `login_history` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '弱引用 user.id，可空',
  `email` varchar(255) DEFAULT NULL COMMENT '登录邮箱',
  `method` tinyint NOT NULL COMMENT '登录方式：1=邮箱 2=Google 3=Apple',
  `ip` varchar(64) DEFAULT NULL COMMENT '登录 IP',
  `device` varchar(255) DEFAULT NULL COMMENT '设备信息',
  `location` varchar(255) DEFAULT NULL COMMENT '登录地点',
  `result` tinyint NOT NULL COMMENT '登录结果：1=成功 2=失败',
  `is_new_device` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否新设备',
  `notified` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已通知',
  PRIMARY KEY (`id`),
  KEY `idx_login_history_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录记录';

CREATE TABLE IF NOT EXISTS `admin_user` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) DEFAULT NULL COMMENT '操作员名称',
  `email` varchar(255) NOT NULL COMMENT '登录邮箱（uk_admin_email，创建后不可改）',
  `password_hash` varchar(255) NOT NULL COMMENT 'BCrypt 密码哈希',
  `role_id` bigint NOT NULL COMMENT '关联角色 role.id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=正常 2=已禁用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作员';

CREATE TABLE IF NOT EXISTS `admin_session` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `admin_id` bigint NOT NULL COMMENT '关联操作员 admin_user.id',
  `token_id` varchar(64) NOT NULL COMMENT 'JWT jti',
  `ip` varchar(64) DEFAULT NULL COMMENT '登录 IP',
  `device` varchar(255) DEFAULT NULL COMMENT '设备信息',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=活跃 2=已撤销',
  `last_active_at` datetime DEFAULT NULL COMMENT '最近活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_session_token` (`token_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台会话';

CREATE TABLE IF NOT EXISTS `role` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(64) NOT NULL COMMENT '角色名',
  `type` tinyint NOT NULL DEFAULT '2' COMMENT '类型：1=系统预设 2=自定义',
  `is_locked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '锁定（超管保护）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS `permission` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `perm_code` varchar(128) NOT NULL COMMENT '权限业务码（原 key）',
  `group` varchar(64) NOT NULL COMMENT '分组',
  `label` varchar(128) NOT NULL COMMENT '展示名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限点字典';

CREATE TABLE IF NOT EXISTS `role_permission` (
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色 id（FK role.id）',
  `permission_id` bigint NOT NULL COMMENT '权限 id（FK permission.id，原 permissionKey）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联';

