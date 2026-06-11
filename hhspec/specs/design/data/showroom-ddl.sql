-- ============================================================================
-- showroom 域 DDL（portal-api-integration / SHR-IMPL-ENTITY）
-- 权威来源：hhspec/changes/portal-api-integration/specs/design/showroom/showroom-data-detail.md §9
-- MySQL 8.0，utf8mb4_0900_ai_ci，InnoDB；与 huihao-mysql 注解建表（@EnableMysql auto=update）等价。
-- 5 表；设计派生列 3 个（invite_version / invite_token_prev / last_ordered_at，溯源见 data-detail §1.2）。
-- 无物理外键（CP-010 逻辑外键 + 应用层/事务维护引用完整性）；不启用逻辑删除（物理级联删除）。
-- ============================================================================

-- 1. showroom 协作空间（决策 20，F-066/F-068）
CREATE TABLE showroom (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  owner_id          BIGINT       NOT NULL COMMENT '逻辑外键 user.id（创建者新娘，JWT subject，BE-DIM-6 强隔离）',
  name              VARCHAR(64)  NOT NULL COMMENT '名称 trim 1..64（CV-SHR-001）',
  wedding_date      DATE         NULL COMMENT '婚期（F-077 结算自动带入，可空）',
  invite_token      VARCHAR(64)  NOT NULL COMMENT '不可猜 UUID 邀请 token（决策 20.2）',
  invite_token_prev VARCHAR(64)  NULL COMMENT '设计派生列：上一代 token（重置识别 410101，单代保留）',
  invite_version    INT          NOT NULL DEFAULT 1 COMMENT '设计派生列：邀请版本号（guest JWT inv_ver 等值校验，重置自增，CV-SHR-008）',
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_showroom_invite (invite_token),
  KEY idx_showroom_owner (owner_id, created_at),
  KEY idx_showroom_invite_prev (invite_token_prev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Showroom 协作空间（新娘创建，邀请伴娘团协作）';

-- 2. showroom_item 收藏款式（F-067）
CREATE TABLE showroom_item (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_id     BIGINT       NOT NULL COMMENT '逻辑外键 showroom.id',
  product_id      BIGINT       NOT NULL COMMENT '逻辑外键 product.id（写前经 CatalogSnapshotPort 校验）',
  color           VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '加入时选择的颜色；未选归一化空串（uk 三元唯一前提，CV-SHR-003）',
  last_ordered_at DATETIME(3)  NULL COMMENT '设计派生列：同房该款式最近已付订单时间（order.paid 消费回写，dye lot 24h 窗口源，决策 20.4）',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_si_room_product_color (showroom_id, product_id, color),
  KEY idx_si_product_ordered (product_id, last_ordered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Showroom 收藏款式（同房 product+color 唯一 409102）';

-- 3. showroom_member 成员（F-068/F-070，免注册访客 + 指派状态机）
CREATE TABLE showroom_member (
  id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_id        BIGINT       NOT NULL COMMENT '逻辑外键 showroom.id',
  nickname           VARCHAR(32)  NOT NULL COMMENT '昵称 trim 1..32，同房唯一（去重身份 409101）',
  email              VARCHAR(254) NULL COMMENT '提醒邮件收件地址（决策 20.5 新娘指派时填写；仅 owner 视图输出）',
  assigned_item_id   BIGINT       NULL COMMENT '逻辑外键 showroom_item.id（被指派款式；item 删除时清理 RM-SHR-039/040）',
  assign_status      VARCHAR(16)  NOT NULL DEFAULT 'unassigned' COMMENT 'unassigned|assigned|reminded|ordered（showroom_member_assignment 状态机，仅 CAS 推进）',
  linked_customer_id BIGINT       NULL COMMENT '逻辑外键 user.id（访客登录后绑定回填，决策 20.2；仅 owner 视图输出）',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sm_room_nickname (showroom_id, nickname),
  KEY idx_sm_linked (linked_customer_id),
  KEY idx_sm_assigned_item (assigned_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Showroom 成员（访客凭邀请 token+昵称参与；指派/提醒/下单状态机）';

-- 4. showroom_vote 款式投票（F-069，PUT 幂等覆盖）
CREATE TABLE showroom_vote (
  id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_item_id BIGINT      NOT NULL COMMENT '逻辑外键 showroom_item.id',
  member_id        BIGINT      NOT NULL COMMENT '逻辑外键 showroom_member.id（鉴权主体解析，不接收请求体）',
  vote             VARCHAR(8)  NOT NULL COMMENT 'like|dislike（重复投票覆盖原票）',
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sv_member_item (member_id, showroom_item_id),
  KEY idx_sv_item (showroom_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='款式投票（member+item 唯一，UPSERT 幂等）';

-- 5. showroom_comment 款式留言（F-069）
CREATE TABLE showroom_comment (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  showroom_item_id BIGINT       NOT NULL COMMENT '逻辑外键 showroom_item.id',
  member_id        BIGINT       NOT NULL COMMENT '逻辑外键 showroom_member.id（nickname 联表派生展示）',
  content          VARCHAR(500) NOT NULL COMMENT '留言 trim 1..500',
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '留言时间（契约 created_at，复用审计列）',
  updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_sc_item (showroom_item_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='款式留言（带昵称展示，提交即可见不审核）';

-- 备注：
-- ① 本域为 q.showroom 消费者，event_id 幂等采用 Redis SETNX（trading §6 口径），不落 processed_event 表；
-- ② 种子数据（决策 21）：prototype data/showrooms.ts 2 房样例 → 5 表种子行（ShowroomSeedInitializer，
--    invite_token 重新生成 UUID 不复用原型假值，幂等 showroom 表非空即跳过）；
-- ③ guest JWT 无会话表——无状态短期凭证，失效由 invite_version/行存在性承担（0.2-d），不扩展 user_session。
