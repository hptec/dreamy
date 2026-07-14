# i18n Backend 数据层详细设计

> 历史设计：`ai_translation_log` 与 `ai_translation_glossary` 已由
> `V20260617_drop_ai_translation_modules.sql` 删除。当前数据状态以 `i18n-runtime-status.md` 为准。

## 元信息

- 变更：i18n-complete-with-ai-assist
- 生成时间：2026-06-16T20:20:00Z
- 覆盖：6实体（3新增 + 3修改）+ Repository方法 + 索引 + 事务边界 + 降级状态机

---

## 1. 新增表DDL

### 1.1 external_gateway_config

```sql
CREATE TABLE external_gateway_config (
  id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  gateway_type              TINYINT      NOT NULL COMMENT '网关类型：AI(1)/LOGISTICS(2)/PAYMENT(3)',
  name                      VARCHAR(64)  NOT NULL COMMENT '配置名称',
  protocol                  TINYINT      NOT NULL DEFAULT 1 COMMENT '协议：openai(1)',
  base_url                  VARCHAR(255) NOT NULL COMMENT '网关地址',
  api_key_encrypted         VARCHAR(4096) NOT NULL COMMENT 'API Key密文(AES-256-GCM, IV+密文 base64)',
  default_model             VARCHAR(128) DEFAULT NULL COMMENT '全局默认模型',
  model_list                JSON         DEFAULT NULL COMMENT '可用模型列表缓存',
  model_refresh_strategy    TINYINT      DEFAULT 1 COMMENT '刷新策略：manual(1)/scheduled(2)',
  model_refresh_interval_min INT         DEFAULT NULL COMMENT '定时刷新间隔(分钟)',
  models_synced_at          DATETIME     DEFAULT NULL COMMENT '上次模型同步时间',
  consecutive_failures      INT          NOT NULL DEFAULT 0 COMMENT '模型同步连续失败次数(L2细化字段, 决策5降级计数)',
  enabled                   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用',
  extra_config              JSON         DEFAULT NULL COMMENT '协议扩展配置',
  version                   INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_name (gateway_type, name),
  KEY idx_type_enabled (gateway_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部网关配置';
```

### 1.2 ai_translation_log

```sql
CREATE TABLE ai_translation_log (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  gateway_config_id   BIGINT       NOT NULL COMMENT '关联网关配置',
  model               VARCHAR(128) NOT NULL COMMENT '实际调用模型',
  source_lang         VARCHAR(8)   NOT NULL COMMENT '源语言',
  target_lang         VARCHAR(8)   NOT NULL COMMENT '目标语言',
  source_text         TEXT         NOT NULL COMMENT '原文',
  translated_text     TEXT         DEFAULT NULL COMMENT '译文(失败时空)',
  custom_requirement  TEXT         DEFAULT NULL COMMENT '自定义要求',
  biz_type            VARCHAR(32)  DEFAULT NULL COMMENT '业务来源类型',
  biz_ref             VARCHAR(64)  DEFAULT NULL COMMENT '业务来源标识',
  status              TINYINT      NOT NULL COMMENT 'success(1)/failed(2)/timeout(3)/empty_result(4)/rate_limited(5)',
  error_message       VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  latency_ms          INT          DEFAULT NULL COMMENT '调用耗时ms',
  token_usage         JSON         DEFAULT NULL COMMENT 'token消耗',
  operator_id         BIGINT       DEFAULT NULL COMMENT '操作人',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_ref),
  KEY idx_created (created_at),
  KEY idx_gateway (gateway_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI翻译调用记录';
```

### 1.3 ai_translation_glossary

```sql
CREATE TABLE ai_translation_glossary (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  term_en     VARCHAR(128) NOT NULL COMMENT '英文术语',
  term_es     VARCHAR(128) DEFAULT NULL COMMENT '西语译法',
  term_fr     VARCHAR(128) DEFAULT NULL COMMENT '法语译法',
  category    VARCHAR(32)  DEFAULT NULL COMMENT '术语分类',
  enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_term_en (term_en),
  KEY idx_enabled_category (enabled, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译术语表';
```

---

## 2. 修改表DDL（ALTER）

```sql
-- 决策12: ProductTranslation增加designer_note
ALTER TABLE product_translation ADD COLUMN designer_note TEXT DEFAULT NULL 
  COMMENT '设计师备注译文(决策12, pick回退EN)' AFTER seo_description;

-- 决策13: User增加locale_pref
ALTER TABLE user ADD COLUMN locale_pref VARCHAR(8) DEFAULT NULL 
  COMMENT '用户语言偏好(en/es/fr, 邮件发信优先取值)' AFTER email;

-- 决策13: Order增加locale_snapshot
ALTER TABLE orders ADD COLUMN locale_snapshot VARCHAR(8) DEFAULT NULL 
  COMMENT '下单时locale快照(邮件发信第二优先级)' AFTER user_id;
```

---

## 3. Repository方法 (RM-NNN)

### gateway域

```java
// RM-001
Optional<ExternalGatewayConfig> findByGatewayTypeAndName(Integer gatewayType, String name);

// RM-002 (翻译时读取启用的AI网关)
Optional<ExternalGatewayConfig> findFirstByGatewayTypeAndEnabledTrueOrderByUpdatedAtDesc(Integer gatewayType);

// RM-003 (定时刷新扫描)
List<ExternalGatewayConfig> findByGatewayTypeAndModelRefreshStrategy(Integer gatewayType, Integer strategy);

// RM-004 (分页列表)
Page<ExternalGatewayConfig> findAll(Specification spec, Pageable pageable);
```

### ai_translation域

```java
// RM-005 (删除引用校验)
long countByGatewayConfigId(Long gatewayConfigId);

// RM-006 (日志分页查询)
Page<AiTranslationLog> findAll(Specification spec, Pageable pageable);

// RM-007 (90天清理 - 决策7)
@Modifying
@Query("DELETE FROM AiTranslationLog l WHERE l.createdAt < :cutoff")
int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
// 注意：实际用原生分批DELETE LIMIT 5000避免长事务
```

### glossary域

```java
// RM-008 (唯一性校验，不区分大小写)
Optional<AiTranslationGlossary> findByTermEnIgnoreCase(String termEn);

// RM-009 (翻译注入：所有启用术语)
List<AiTranslationGlossary> findByEnabledTrue();

// RM-010 (分页列表)
Page<AiTranslationGlossary> findAll(Specification spec, Pageable pageable);
```

### catalog增量

```java
// RM-011 (designer_note更新)
@Modifying
@Query("UPDATE ProductTranslation t SET t.designerNote = :note WHERE t.productId = :pid AND t.locale = :locale")
int updateDesignerNote(@Param("pid") Long productId, @Param("locale") String locale, @Param("note") String note);
```

---

## 4. DTO映射 (MAP-NNN)

| 映射ID | 源 | 目标 | 工具 | 特殊处理 |
|--------|----|----|------|---------|
| MAP-001 | GatewayConfigUpsert | ExternalGatewayConfig | MapStruct | api_key加密 |
| MAP-002 | ExternalGatewayConfig | GatewayConfigDetail | MapStruct | api_key掩码 |
| MAP-003 | ExternalGatewayConfig | GatewayConfigListItem | MapStruct | api_key掩码+精简字段 |
| MAP-004 | TranslateRequest | (内部调用参数) | 手动 | system prompt组装 |
| MAP-005 | AiTranslationLog | AiTranslationLogItem | MapStruct | source/translated_text截断 |
| MAP-006 | GlossaryTermUpsert | AiTranslationGlossary | MapStruct | - |
| MAP-007 | AiTranslationGlossary | GlossaryTerm | MapStruct | - |

**MAP-001/002关键**：api_key字段需自定义转换器（@Mapping with qualifiedByName="encryptApiKey"/"maskApiKey"），不能用默认映射。

---

## 5. 索引设计 (IDX-NNN)

| 索引ID | 表 | 索引 | 类型 | 用途 |
|--------|----|----|------|------|
| IDX-001 | external_gateway_config | uk_type_name (gateway_type, name) | UNIQUE | 同类型名称唯一约束 |
| IDX-002 | external_gateway_config | idx_type_enabled (gateway_type, enabled) | BTREE | RM-002查询启用AI网关 |
| IDX-003 | ai_translation_log | idx_biz (biz_type, biz_ref) | BTREE | 按业务溯源 |
| IDX-004 | ai_translation_log | idx_created (created_at) | BTREE | 时间倒序分页+90天清理 |
| IDX-005 | ai_translation_log | idx_gateway (gateway_config_id) | BTREE | RM-005引用计数 |
| IDX-006 | ai_translation_glossary | uk_term_en (term_en) | UNIQUE | 术语唯一 |
| IDX-007 | ai_translation_glossary | idx_enabled_category (enabled, category) | BTREE | RM-009注入+截断优先级 |

---

## 6. 事务边界 (TX-NNN)

| 事务ID | 操作 | 边界 | 隔离级别 | 说明 |
|--------|------|------|---------|------|
| TX-001 | 创建网关配置 | save + 模型拉取 | READ_COMMITTED | 模型拉取在事务外(避免外部调用占用事务)，save先提交 |
| TX-002 | 更新网关配置 | 单语句原子 CAS | READ_COMMITTED | UPDATE WHERE id/version 并 version+1 |
| TX-003 | 删除网关配置 | 引用count + delete | READ_COMMITTED | 同事务内校验+删除 |
| TX-004 | 翻译请求 | log写入 | REQUIRES_NEW | 日志独立事务，翻译失败也要记录 |
| TX-005 | 术语CRUD | 单表操作 | READ_COMMITTED | 标准事务 |
| TX-006 | 日志清理 | 分批DELETE | READ_COMMITTED | 每批5000条独立提交，避免长事务锁表 |

**TX-001关键**：模型拉取必须在主事务提交后执行（@TransactionalEventListener或手动分离），否则外部网关慢响应会长时间持有DB连接。

**TX-004关键**：ai_translation_log写入用REQUIRES_NEW，确保即使主流程因翻译失败回滚，调用日志仍持久化（决策7审计要求）。

---

## 7. 数据校验

| 校验项 | 实体 | 规则 | 实现位置 |
|--------|------|------|---------|
| API Key非空 | ExternalGatewayConfig | 请求字段 api_key 明文 1~512 (契约对齐) | @Valid DTO @Size(min=1,max=512) |
| URL格式 | ExternalGatewayConfig | ^https?:// | @Pattern |
| 枚举值域 | ExternalGatewayConfig | gateway_type∈[1,2,3] | @Min/@Max或自定义 |
| 术语唯一 | AiTranslationGlossary | term_en不区分大小写唯一 | RM-008查询+DB UNIQUE |
| locale值域 | User/Order | ∈['en','es','fr'] | @Pattern |
| status值域 | AiTranslationLog | ∈[1,2,3,4,5] (见§7.1枚举映射) | 枚举常量 |
| 失败计数值域 | ExternalGatewayConfig | consecutive_failures ≥ 0 | 业务逻辑维护 |
| 乐观锁版本 | ExternalGatewayConfig | version ≥ 0；配置编辑、模型成功/失败写入均 WHERE expected version 并单调 +1 | 原子 SQL |

### 7.1 ai_translation_log.status 枚举映射（ISS-005，对齐 L1 error-strategy）

| 值 | 枚举 | 触发场景 | 关联错误码 | 测试 |
|----|------|---------|-----------|------|
| 1 | success | 翻译成功返回非空译文 | - | TC-UNIT-I18N-0012 |
| 2 | failed | 网关 4xx/5xx 通用失败（含原 gateway_error） | 502301 | TC-RESILIENCE-I18N-0002 |
| 3 | timeout | 网关调用超时(>30s) | 504301 | TC-RESILIENCE-I18N-0001 |
| 4 | empty_result | 网关返回 content="" 空译文 | 502301 | TC-UNIT-I18N-0015 |
| 5 | rate_limited | 网关返回 429 限流 | 502301 | TC-RESILIENCE-I18N-0003 |

> **说明**：error-mapping.yml 中 TC-RESILIENCE-I18N-0002 原描述的 `status=gateway_error` 归并为 `failed(2)`，消除未编号态。所有 status 值与 test-skeleton 用到的语义一致（empty_result/timeout/rate_limited 均有对应编号）。

---

## 8. 模型同步连续失败降级状态机（ISS-004，决策5 / EDGE-014 / TC-RESILIENCE-I18N-0006）

`external_gateway_config.consecutive_failures` 字段驱动定时刷新（model_refresh_strategy=scheduled）的自动降级，对应 error-mapping.yml `gateway_degradation.model_sync_failure`（retry_count:3, fallback_strategy:manual）。

```
状态变量：consecutive_failures (INT, DEFAULT 0)

[定时刷新任务每次执行后]
  ├─ 同步成功
  │    → consecutive_failures = 0（清零）
  │    → 更新 model_list + models_synced_at
  │
  └─ 同步失败（GatewayException：不可达/鉴权/超时）
       → consecutive_failures += 1
       → 记 WARN 日志（models_synced_at 不更新，保留旧 model_list）
       → 判定：
            ├─ consecutive_failures < 3：保持 scheduled，下个周期重试
            └─ consecutive_failures >= 3：触发降级
                 → model_refresh_strategy = 1 (manual)
                 → enabled = 0（自动禁用，见 UNC-L2C-002 待确认）
                 → 发告警通知（运营介入）
                 → consecutive_failures 保持（运营手动刷新成功后清零）

[运营手动 sync-models 成功]
  → consecutive_failures = 0
  → 若此前被降级，运营可重新设回 scheduled + enabled
```

**状态转移表**：

| 当前 strategy | 事件 | consecutive_failures | 动作 | 新 strategy |
|------|------|------|------|------|
| scheduled | 同步成功 | →0 | 更新模型列表 | scheduled |
| scheduled | 同步失败 | 1 或 2 | 记 WARN，保留旧列表 | scheduled |
| scheduled | 同步失败 | →3 | 降级 manual + enabled=0 + 告警 | manual |
| manual | 手动同步成功 | →0 | 更新模型列表 | manual |

> **待确认（UNC-L2C-002，info 级）**：降级时 `enabled→false` 自动禁用会使该网关下所有翻译返回 400301，影响面较大。是否应仅降级 refresh_strategy 而不自动禁用 enabled，待用户/运营确认。当前按 L1 error-strategy 决策5 原文（enabled→false + 告警）实现，L3 可通过配置开关控制是否自动禁用。

---

## 9. 实体关系

```
ExternalGatewayConfig (1) ←--- (N) AiTranslationLog
  通过gateway_config_id逻辑外键（不建物理FK，便于配置删除前的引用校验）

AiTranslationGlossary  独立表，无外键

ProductTranslation (修改) ← product_id关联product主表（pick回退源）
User (修改) ← locale_pref独立字段
Order (修改) ← locale_snapshot独立字段
```

---

## 10. 需求追溯

- FUNC-005~007: external_gateway_config表+加密
- FUNC-012: ai_translation_log表+查询
- FUNC-022: ai_translation_glossary表
- FUNC-017: product_translation.designer_note
- FUNC-019: user.locale_pref
- FUNC-020: orders.locale_snapshot

---

**设计完成标记**：✅ Backend数据层详设已完成
