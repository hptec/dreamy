-- 枚举字段 varchar → tinyint migration
-- 执行前备份，执行后验证数据无 NULL

-- user.status: active=1, disabled=2, deleted=3, anonymized=4
-- user.tier: regular=1, vip=2
ALTER TABLE `user`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=已禁用 3=已删除 4=已匿名化',
  MODIFY COLUMN `tier`   tinyint NOT NULL DEFAULT 1 COMMENT '等级：1=常规 2=VIP';

UPDATE `user` SET `status` = CASE status_old
  WHEN 'active'     THEN 1
  WHEN 'disabled'   THEN 2
  WHEN 'deleted'    THEN 3
  WHEN 'anonymized' THEN 4
  ELSE 1 END,
  `tier` = CASE tier_old
  WHEN 'regular' THEN 1
  WHEN 'vip'     THEN 2
  ELSE 1 END;

-- 实际迁移步骤：先加新列，回填数据，再删旧列（避免 NOT NULL 约束失败）
-- 以下为简化的一步式迁移（适用于小数据量 / 停机窗口）：

-- Step 1: 临时允许 NULL，添加新 tinyint 列
ALTER TABLE `user`
  ADD COLUMN `status_new` tinyint NULL,
  ADD COLUMN `tier_new`   tinyint NULL;

UPDATE `user` SET
  `status_new` = CASE `status`
    WHEN 'active'     THEN 1
    WHEN 'disabled'   THEN 2
    WHEN 'deleted'    THEN 3
    WHEN 'anonymized' THEN 4
    ELSE 1 END,
  `tier_new` = CASE `tier`
    WHEN 'regular' THEN 1
    WHEN 'vip'     THEN 2
    ELSE 1 END;

ALTER TABLE `user`
  DROP COLUMN `status`,
  DROP COLUMN `tier`;

ALTER TABLE `user`
  CHANGE COLUMN `status_new` `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=已禁用 3=已删除 4=已匿名化',
  CHANGE COLUMN `tier_new`   `tier`   tinyint NOT NULL DEFAULT 1 COMMENT '等级：1=常规 2=VIP';

-- admin_user.status: active=1, disabled=2
ALTER TABLE `admin_user` ADD COLUMN `status_new` tinyint NULL;
UPDATE `admin_user` SET `status_new` = CASE `status` WHEN 'active' THEN 1 WHEN 'disabled' THEN 2 ELSE 1 END;
ALTER TABLE `admin_user` DROP COLUMN `status`;
ALTER TABLE `admin_user` CHANGE COLUMN `status_new` `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=已禁用';

-- role.type: preset=1, custom=2
ALTER TABLE `role` ADD COLUMN `type_new` tinyint NULL;
UPDATE `role` SET `type_new` = CASE `type` WHEN 'preset' THEN 1 WHEN 'custom' THEN 2 ELSE 2 END;
ALTER TABLE `role` DROP COLUMN `type`;
ALTER TABLE `role` CHANGE COLUMN `type_new` `type` tinyint NOT NULL DEFAULT 2 COMMENT '类型：1=系统预设 2=自定义';

-- otp_code.status: pending=1, consumed=2, expired=3, locked=4
ALTER TABLE `otp_code` ADD COLUMN `status_new` tinyint NULL;
UPDATE `otp_code` SET `status_new` = CASE `status`
  WHEN 'pending'  THEN 1
  WHEN 'consumed' THEN 2
  WHEN 'expired'  THEN 3
  WHEN 'locked'   THEN 4
  ELSE 1 END;
ALTER TABLE `otp_code` DROP COLUMN `status`;
ALTER TABLE `otp_code` CHANGE COLUMN `status_new` `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=待验证 2=已消耗 3=已过期 4=已锁定';

-- user_session.status: active=1, revoked=2
-- user_session.method: email=1, google=2, apple=3
ALTER TABLE `user_session`
  ADD COLUMN `status_new` tinyint NULL,
  ADD COLUMN `method_new` tinyint NULL;
UPDATE `user_session` SET
  `status_new` = CASE `status` WHEN 'active' THEN 1 WHEN 'revoked' THEN 2 ELSE 1 END,
  `method_new` = CASE `method` WHEN 'email' THEN 1 WHEN 'google' THEN 2 WHEN 'apple' THEN 3 ELSE 1 END;
ALTER TABLE `user_session` DROP COLUMN `status`, DROP COLUMN `method`;
ALTER TABLE `user_session`
  CHANGE COLUMN `status_new` `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=活跃 2=已撤销',
  CHANGE COLUMN `method_new` `method` tinyint NOT NULL COMMENT '登录方式：1=邮箱 2=Google 3=Apple';

-- admin_session.status: active=1, revoked=2
ALTER TABLE `admin_session` ADD COLUMN `status_new` tinyint NULL;
UPDATE `admin_session` SET `status_new` = CASE `status` WHEN 'active' THEN 1 WHEN 'revoked' THEN 2 ELSE 1 END;
ALTER TABLE `admin_session` DROP COLUMN `status`;
ALTER TABLE `admin_session` CHANGE COLUMN `status_new` `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=活跃 2=已撤销';

-- login_history.method: email=1, google=2, apple=3
-- login_history.result: success=1, failed=2
ALTER TABLE `login_history`
  ADD COLUMN `method_new` tinyint NULL,
  ADD COLUMN `result_new` tinyint NULL;
UPDATE `login_history` SET
  `method_new` = CASE `method` WHEN 'email' THEN 1 WHEN 'google' THEN 2 WHEN 'apple' THEN 3 ELSE 1 END,
  `result_new` = CASE `result` WHEN 'success' THEN 1 WHEN 'failed' THEN 2 ELSE 2 END;
ALTER TABLE `login_history` DROP COLUMN `method`, DROP COLUMN `result`;
ALTER TABLE `login_history`
  CHANGE COLUMN `method_new` `method` tinyint NOT NULL COMMENT '登录方式：1=邮箱 2=Google 3=Apple',
  CHANGE COLUMN `result_new` `result` tinyint NOT NULL COMMENT '登录结果：1=成功 2=失败';

-- user_identity.provider: email=1, google=2, apple=3
ALTER TABLE `user_identity` ADD COLUMN `provider_new` tinyint NULL;
UPDATE `user_identity` SET `provider_new` = CASE `provider`
  WHEN 'email'  THEN 1
  WHEN 'google' THEN 2
  WHEN 'apple'  THEN 3
  ELSE 1 END;
ALTER TABLE `user_identity` DROP COLUMN `provider`;
ALTER TABLE `user_identity` CHANGE COLUMN `provider_new` `provider` tinyint NOT NULL COMMENT '渠道：1=邮箱 2=Google 3=Apple';
