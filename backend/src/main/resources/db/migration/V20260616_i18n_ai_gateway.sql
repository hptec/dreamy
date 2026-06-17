-- V20260616_i18n_ai_gateway.sql
-- i18n-complete-with-ai-assist 变更的 DDL：3 张新表 + 3 处增量加列
-- L2 TRACE: i18n-backend-data-detail.md §1 DDL / 决策1/5/6/12/13

-- ============================================================
-- 1. 新表：external_gateway_config
-- ============================================================
CREATE TABLE external_gateway_config (
  id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  gateway_type              TINYINT      NOT NULL COMMENT '网关类型：AI(1)/LOGISTICS(2)/PAYMENT(3)',
  name                      VARCHAR(64)  NOT NULL COMMENT '配置名称',
  protocol                  TINYINT      NOT NULL DEFAULT 1 COMMENT '协议：openai(1)/anthropic(2)/custom(3)',
  base_url                  VARCHAR(255) NOT NULL COMMENT '网关地址',
  api_key_encrypted         VARCHAR(512) NOT NULL COMMENT 'API Key密文(AES-256-GCM, v1:前缀+IV+密文 base64)',
  default_model             VARCHAR(128) DEFAULT NULL COMMENT '全局默认模型',
  model_list                JSON         DEFAULT NULL COMMENT '可用模型列表缓存',
  model_refresh_strategy    TINYINT      DEFAULT 1 COMMENT '刷新策略：manual(1)/scheduled(2)',
  model_refresh_interval_min INT         DEFAULT NULL COMMENT '定时刷新间隔(分钟)',
  models_synced_at          DATETIME     DEFAULT NULL COMMENT '上次模型同步时间',
  consecutive_failures      INT          NOT NULL DEFAULT 0 COMMENT '模型同步连续失败次数(决策5降级计数)',
  enabled                   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用',
  extra_config              JSON         DEFAULT NULL COMMENT '协议扩展配置',
  created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_name (gateway_type, name),
  KEY idx_type_enabled (gateway_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部网关配置';

-- ============================================================
-- 2. 新表：ai_translation_log
-- ============================================================
CREATE TABLE ai_translation_log (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  gateway_config_id   BIGINT       NOT NULL COMMENT '关联网关配置',
  model               VARCHAR(128) NOT NULL COMMENT '实际调用模型',
  source_lang         VARCHAR(8)   NOT NULL COMMENT '源语言(en/es/fr)',
  target_lang         VARCHAR(8)   NOT NULL COMMENT '目标语言(en/es/fr)',
  source_text         TEXT         NOT NULL COMMENT '原文',
  translated_text     TEXT         DEFAULT NULL COMMENT '译文(失败时空)',
  custom_requirement  TEXT         DEFAULT NULL COMMENT '自定义要求',
  biz_type            VARCHAR(32)  DEFAULT NULL COMMENT '业务来源类型(product/category/tag等)',
  biz_ref             VARCHAR(64)  DEFAULT NULL COMMENT '业务来源标识',
  status              TINYINT      NOT NULL COMMENT 'success(1)/failed(2)/timeout(3)/empty_result(4)/rate_limited(5)',
  error_message       VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  latency_ms          INT          DEFAULT NULL COMMENT '调用耗时ms',
  token_usage         JSON         DEFAULT NULL COMMENT 'token消耗{prompt_tokens,completion_tokens,total_tokens}',
  operator_id         BIGINT       DEFAULT NULL COMMENT '操作人',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_ref),
  KEY idx_created (created_at),
  KEY idx_gateway (gateway_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI翻译调用记录';

-- ============================================================
-- 3. 新表：ai_translation_glossary
-- ============================================================
CREATE TABLE ai_translation_glossary (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  term_en     VARCHAR(128) NOT NULL COMMENT '英文术语',
  term_es     VARCHAR(128) DEFAULT NULL COMMENT '西语译法',
  term_fr     VARCHAR(128) DEFAULT NULL COMMENT '法语译法',
  category    VARCHAR(32)  DEFAULT NULL COMMENT '术语分类(婚纱礼服领域/通用)',
  enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用(注入prompt)',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_term_en (term_en),
  KEY idx_enabled_category (enabled, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译术语表';

-- ============================================================
-- 4. 增量：product_translation 加 designer_note
-- ============================================================
ALTER TABLE product_translation
ADD COLUMN designer_note TEXT DEFAULT NULL COMMENT '设计师备注(三语独立,决策12/FUNC-017)' AFTER description;

-- ============================================================
-- 5. 增量：user 加 locale_pref
-- ============================================================
ALTER TABLE user
ADD COLUMN locale_pref VARCHAR(8) DEFAULT NULL COMMENT '用户偏好语言(en/es/fr,决策13/FUNC-019)' AFTER email;

-- ============================================================
-- 6. 增量：orders 加 locale_snapshot
-- ============================================================
ALTER TABLE orders
ADD COLUMN locale_snapshot VARCHAR(8) DEFAULT NULL COMMENT '下单时语言环境快照(决策13/FUNC-020,邮件三语用)' AFTER currency;
