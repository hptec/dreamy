-- =====================================================================
-- review 域 DDL（portal-api-integration / L2 review-data-detail §9 权威 SQL）
-- MySQL 8.0, utf8mb4_0900_ai_ci, InnoDB；与 huihao-mysql 实体注解建表等价
-- （运行期由 @EnableMysql(auto=update) 按 com.dreamy.review.domain 实体自动管理，本文件为审阅基准）。
-- 实体：Review / ReviewImage / ProductQuestion（决策 2）；Long 自增主键（决策 12）；无物理 FK（CP-010）。
-- 备注：① processed_event 幂等表归 trading 域 DDL（本域为 review.moderated 生产者不落表）；
--      ② 种子数据（决策 21）：portal-admin mock.js reviews/productQuestions → 3 表种子行
--        （ReviewSeedInitializer，dev/staging 限定）；
--      ③ 官方回复内嵌主表（reply 三字段），不拆 review_reply 表——一对一、无独立生命周期。
-- =====================================================================

-- 1. review 商品评价（ALIGN-014 / s-756 / s-762）
CREATE TABLE review (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id    BIGINT        NOT NULL COMMENT '逻辑外键 product.id（写前经 CatalogSnapshotPort 校验）',
  user_id       BIGINT        NOT NULL COMMENT '逻辑外键 user.id（JWT subject，BE-DIM-6 强隔离）',
  customer_name VARCHAR(64)   NULL COMMENT '提交时用户姓名快照（store 输出脱敏 MAP-REV-001）',
  rating        TINYINT       NOT NULL COMMENT '评分 1..5（应用层校验 CV-REV-001）',
  content       TEXT          NULL COMMENT '评价内容 <=5000，trim 后空存 NULL；不做多语翻译',
  status        VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected（review_moderation）',
  featured      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '精选；不变量 status!=approved => 0（CV-REV-007）',
  submitted_at  DATETIME(3)   NOT NULL COMMENT '提交时间（业务时间，列表排序键）',
  reply_author  VARCHAR(64)   NULL COMMENT '官方回复署名（缺省 "Dreamy Team"，配置化）',
  reply_content VARCHAR(2000) NULL COMMENT '官方回复内容，trim 非空（CV-REV-006）',
  reply_time    DATETIME(3)   NULL COMMENT '官方回复时间（服务端生成）',
  created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_user_product (user_id, product_id),
  KEY idx_review_product_status (product_id, status, featured, submitted_at),
  KEY idx_review_status_submitted (status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价（消费端提交/后台审核/精选/官方回复）';

-- 2. review_image 买家秀图片（决策 9 预签名直传）
CREATE TABLE review_image (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  review_id  BIGINT       NOT NULL COMMENT '逻辑外键 review.id（仅经 TX-REV-001 聚合根写入）',
  url        VARCHAR(512) NOT NULL COMMENT '预签名上传 public_url（review/ 前缀，CV-REV-008）',
  rejected   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '驳回标记；1=前台不展示（review_image_visibility shown/rejected）',
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ri_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价买家秀图片（可单独驳回/恢复）';

-- 3. product_question 商品 Q&A
CREATE TABLE product_question (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id  BIGINT        NOT NULL COMMENT '逻辑外键 product.id',
  user_id     BIGINT        NOT NULL COMMENT '逻辑外键 user.id（设计派生列：BE-DIM-6 提交者强关联，不出契约 DTO）',
  asker       VARCHAR(64)   NULL COMMENT '提问者姓名快照（store 输出脱敏）',
  question    VARCHAR(1000) NOT NULL COMMENT '提问内容 trim 1..1000',
  asked_at    DATETIME(3)   NOT NULL COMMENT '提问时间（业务时间，列表排序键）',
  answer      VARCHAR(2000) NULL COMMENT '官方回答；NULL=unanswered（question_answer_flow）',
  answer_time DATETIME(3)   NULL COMMENT '回答时间（服务端生成）',
  visible     VARCHAR(16)   NOT NULL DEFAULT 'hidden' COMMENT 'visible|hidden；首次回答自动置 visible（E-REV-14）',
  created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_pq_product_visible (product_id, visible, asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品 Q&A（后台回答并控制前台可见性）';
