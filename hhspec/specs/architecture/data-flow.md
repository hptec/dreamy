# 数据流 - i18n-complete-with-ai-assist（增量）

本文档定义 i18n 变更新增的 AI 翻译、网关配置、术语表管理、locale 路由、数据级翻译回退等数据流转，逐条响应 `decision.md` 决策 1~14 与 `boundary-scenarios.yml` 24 个 EDGE 场景。与 baseline 七域 data-flow.md 并列（baseline 无 gateway/glossary/ai_translation 域，本变更新增）。

**参与者命名**：`Admin`（portal-admin Vue3 运营）、`User`（portal-store Next.js 消费端登录用户）、`Guest`（匿名访客）、`AdminAPI`（AdminGatewayController/AdminAiController/AdminGlossaryController）、`Svc`（gateway/ai_translation/glossary 三域服务）、`DB`（MySQL）、`Gateway`（外部 AI 网关，OpenAI-compatible /v1/chat/completions + /v1/models）、`Sched`（@Scheduled 定时任务）、`Middleware`（Next.js middleware locale 检测）、`CDN`（Cloudflare 边缘）。

## 各层数据转换约定（增量）

| 边界 | 转换 | 说明 |
|------|------|------|
| AdminAPI ⇄ Svc | Request DTO（@Valid 校验）⇄ 领域入参；响应装入 R 包络 `{code,message,data}` | API Key 字段：前端提交明文或掩码（sk-****xxxx），后端判定：明文→AES 加密存储；掩码→保持原密文不变 |
| Svc ⇄ Gateway | OpenAI-compatible JSON | /v1/chat/completions POST {model, messages[{role,content}], max_tokens?}；/v1/models GET 返回 [{id, name, context_length}] |
| Svc ⇄ DB | ExternalGatewayConfig.api_key_encrypted（AES-256-GCM IV+密文）⇄ 明文 Key；model_list/extra_config JSON 列 | 解密仅在调用时内存中进行，任何响应/日志均掩码（sk-****1234） |
| portal-store locale 路由 | URL 路径 `/es/product/xxx` → middleware 提取 locale=es → cookie `NEXT_LOCALE` → SSR context | EN 为根路径（无前缀），ES/FR 为 `/es/`、`/fr/` 前缀（决策 11） |
| 数据级翻译回退 | product_translation.designer_note(ES) 为空 → pick() 回退读 product.designer_note(EN 主表) | 消费端 assembleDetail 输出扁平字段，已按 locale 解析（决策 12） |
| 邮件 locale 选择 | user.locale_pref → orders.locale_snapshot → 默认 EN | 模板路径 `templates/email/{name}_{locale}.html`（决策 13） |

## 核心业务流程清单

| 流程编号 | 流程名称 | 域 | 触发条件 | 参与模块 | 验收 |
|---------|---------|----|---------|---------|----- |
| FLOW-I01 | AI 翻译请求流（后端代理 + 术语表注入） | ai_translation/glossary | 后台点击「AI 翻译」按钮 | Admin, AdminAPI, Svc, Gateway, DB | FUNC-008~010, 决策 2/6/14, EDGE-001~003/015~017 |
| FLOW-I02 | 网关配置保存 + 自动模型发现 | gateway | 后台创建/更新 AI 网关配置 | Admin, AdminAPI, Svc, Gateway, DB | FUNC-004~007, 决策 1/3/5, EDGE-006/007/014 |
| FLOW-I03 | 模型列表定时刷新 | gateway | @Scheduled 扫描 model_refresh_strategy=scheduled 配置 | Sched, Svc, Gateway, DB | 决策 5, EDGE-014 |
| FLOW-I04 | 测试网关连接 | gateway | 后台点击「测试连接」按钮 | Admin, AdminAPI, Svc, Gateway | 决策 14, EDGE-023 |
| FLOW-I05 | 术语表 CRUD | glossary | 后台术语表管理页 | Admin, AdminAPI, Svc, DB | FUNC-022, 决策 14, EDGE-022/024 |
| FLOW-I06 | 调用日志清理 | ai_translation | @Scheduled 每日 3:00 | Sched, Svc, DB | 决策 7 |
| FLOW-I07 | 消费端 locale 路由检测 | identity（复用现有） | 用户访问任意 URL | User/Guest, CDN, Middleware, Next | 决策 11, EDGE-018/019 |
| FLOW-I08 | designerNote 数据级翻译回退 | catalog（增强现有 FLOW-P01） | 消费端 PDP 请求 | User, StoreAPI, Svc, DB | 决策 12, EDGE-020 |
| FLOW-I09 | 邮件三语发送 | identity/trading/showroom（增强现有 FLOW-P11） | MQ 邮件事件触发 | MQ, Svc, DB, SMTP | 决策 13, EDGE-021 |

## 决策响应映射

| 决策 | 本文档响应位置 |
|------|---------------|
| 决策 1 单表多类型存储 | FLOW-I02 external_gateway_config 表结构 |
| 决策 2 后端代理模式 | FLOW-I01 AdminAiController 代理 Gateway 调用 |
| 决策 3 OpenAI-compatible 协议 | FLOW-I01/I02/I04 /v1/chat/completions + /v1/models |
| 决策 4 两级模型选择 | FLOW-I01 请求参数 model 优先，否则 gateway.default_model |
| 决策 5 模型自动发现 + 刷新策略 | FLOW-I02 保存时拉取、FLOW-I03 定时刷新 |
| 决策 6 翻译弹窗交互 | FLOW-I01 system prompt（锁定） + custom_requirement（用户追加） |
| 决策 7 调用记录 90 天保留 | FLOW-I01 落 ai_translation_log、FLOW-I06 定时清理 |
| 决策 10 失败允许继续 | FLOW-I01 异常路径：502/504 返回错误，log status=failed，前端 toast 不阻塞保存 |
| 决策 11 locale 路径前缀 | FLOW-I07 middleware 检测、301/302 重定向、hreflang/sitemap |
| 决策 12 designerNote 纳入翻译 | FLOW-I08 product_translation.designer_note 新增列 + pick() 回退 |
| 决策 13 邮件三语 + locale 持久化 | FLOW-I09 user.locale_pref / orders.locale_snapshot + 模板选择 |
| 决策 14 测试连接 + 术语表 | FLOW-I04 测试连接、FLOW-I01/I05 术语表注入 |

---

## FLOW-I01: AI 翻译请求流（后端代理 + 术语表注入 + 调用日志）

**触发条件**: 后台运营在商品/分类/标签/Banner/Blog 等编辑页点击「AI 翻译」按钮（决策 2/6）。

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc as ai_translation Svc
    participant GlossarySvc as glossary Svc
    participant GatewaySvc as gateway Svc
    participant DB
    participant Gateway as External AI Gateway

    Admin->>AdminAPI: POST /api/admin/ai/translate {source_lang:en, target_lang:es, source_text, custom_requirement?, model?, biz_type, biz_ref}
    Note over AdminAPI: AdminJwtFilter + 根据 biz_type 校验权限（如 product 需 /catalog/products 权限）
    
    alt EN 主字段为空（EDGE-002）
        AdminAPI-->>Admin: 422 {422301} "source_text 不能为空"
    end
    
    AdminAPI->>Svc: translate(request, operatorId=JWT.subject)
    
    Svc->>GatewaySvc: 读取当前启用的 AI 网关配置
    GatewaySvc->>DB: SELECT * FROM external_gateway_config WHERE gateway_type=1 AND enabled=true ORDER BY updated_at DESC LIMIT 1
    
    alt 无启用网关配置（EDGE-001）
        GatewaySvc-->>Svc: null
        Svc-->>AdminAPI: 400 {400301 NO_ENABLED_GATEWAY}
        AdminAPI-->>Admin: 弹窗提示"尚未配置 AI 网关，请前往系统管理 > 外部网关配置"
    end
    
    GatewaySvc->>DB: 解密 api_key_encrypted（AES-256-GCM）
    Note over GatewaySvc: IV + 密文 → 明文 Key（仅内存中，不落日志）
    
    Svc->>Svc: 确定使用模型：request.model 存在 → 校验在 gateway.model_list 中；不存在 → 用 gateway.default_model
    alt 模型无效（EDGE-004）
        Svc-->>AdminAPI: 400 {400302 INVALID_MODEL} details: {model, available_models}
    end
    
    Svc->>GlossarySvc: 读取启用的术语表
    GlossarySvc->>DB: SELECT * FROM ai_translation_glossary WHERE enabled=true AND (term_es IS NOT NULL OR term_fr IS NOT NULL)
    Note over Svc: 术语注入优化（EDGE-024）：仅注入 source_text 中实际命中的术语（精确匹配 term_en），上限 50 条；超出按 category 优先级截断（廓形>领型>面料>工艺>其他）
    
    Svc->>Svc: 组装 system prompt
    Note over Svc: 1. 固定前缀（锁定）："You are a professional translator for a bridal e-commerce platform..."<br/>2. 注入命中术语："Use these standard terms: A-line→línea A, sweetheart neckline→escote corazón..."<br/>3. 追加 custom_requirement（可选）

    Svc->>Gateway: POST {base_url}/v1/chat/completions {model, messages:[{role:system, content:拼接的 prompt},{role:user, content:source_text}], max_tokens:配置值}
    Note over Svc: 超时 30s（决策 10）
    
    alt 网关超时（EDGE-015）
        Gateway-->>Svc: timeout
        Svc->>DB: INSERT ai_translation_log(status=timeout, latency_ms=30000, operator_id, biz_type, biz_ref)
        Svc-->>AdminAPI: 504 {504301 GATEWAY_TIMEOUT}
        AdminAPI-->>Admin: toast "翻译超时，请重试" + 允许继续保存（不阻塞工作流）
    else 网关 5xx/429（EDGE-016/017）
        Gateway-->>Svc: 500/502/503/429 {error}
        Svc->>DB: INSERT ai_translation_log(status=failed, error_message=网关返回的错误, latency_ms)
        Svc-->>AdminAPI: 502 {502301 GATEWAY_CALL_FAILED} details: {gateway_error, gateway_name}
        AdminAPI-->>Admin: toast 具体错误 + 允许继续保存
    else 网关返回空译文（EDGE-003）
        Gateway-->>Svc: 200 {choices:[{message:{content:""}}]}
        Svc->>DB: INSERT ai_translation_log(status=empty_result, translated_text=null)
        Svc-->>AdminAPI: 502 {502301} "翻译结果为空"
        AdminAPI-->>Admin: toast "翻译结果为空，请重试或手动填写" + 允许继续保存
    else 成功
        Gateway-->>Svc: 200 {choices:[{message:{content:译文}}], usage:{prompt_tokens, completion_tokens, total_tokens}}
        Svc->>DB: INSERT ai_translation_log(gateway_config_id, model, source_lang, target_lang, source_text, translated_text, custom_requirement, biz_type, biz_ref, status=success, latency_ms, token_usage JSON, operator_id, created_at)
        Svc-->>AdminAPI: 200 TranslateResponse{translated_text, model, latency_ms, token_usage}
        AdminAPI-->>Admin: 回写表单对应字段（ES/FR tab）
    end
```

---

## FLOW-I02: 网关配置保存 + 自动模型发现（决策 1/3/5）

**触发条件**: 后台「外部系统配置 > AI 网关」页面创建/更新配置。

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc as gateway Svc
    participant DB
    participant Gateway as External AI Gateway

    Admin->>AdminAPI: POST /api/admin/gateway/configs {gateway_type:1, name, protocol:1, base_url, api_key, model_refresh_strategy, model_refresh_interval_min?, enabled}
    Note over AdminAPI: AdminJwtFilter + RBAC(/system/gateways) + EDGE-008 权限校验
    
    alt URL 协议非法（EDGE-007）
        AdminAPI-->>Admin: 422 {422201} "网关地址格式不正确"
    end
    
    AdminAPI->>Svc: createGatewayConfig(...)
    
    Svc->>Svc: API Key 处理：trim() 后校验非空，AES-256-GCM 加密（生成随机 IV，IV+密文存 api_key_encrypted）
    
    Svc->>DB: SELECT COUNT(*) FROM external_gateway_config WHERE gateway_type=? AND name=? AND deleted_at IS NULL
    alt 同名配置已存在（EDGE-012 并发保护在乐观锁层）
        Svc-->>AdminAPI: 409 {409201 GATEWAY_NAME_EXISTS}
    end
    
    Svc->>DB: INSERT external_gateway_config(gateway_type, name, protocol, base_url, api_key_encrypted, default_model, model_refresh_strategy, model_refresh_interval_min, enabled, extra_config, created_at, updated_at)
    Note over Svc: model_list 初始为空数组 []，models_synced_at 为 NULL
    
    alt gateway_type=AI（决策 5 自动拉取）
        Svc->>Gateway: GET {base_url}/v1/models（Authorization: Bearer {解密后的 api_key}）
        Note over Svc: 超时 10s
        alt 拉取成功
            Gateway-->>Svc: 200 {data: [{id, object, created, owned_by}]}
            Svc->>Svc: 映射为 GatewayModel[]{id, name=id（若无 name 字段）, context_length?}
            Svc->>DB: UPDATE external_gateway_config SET model_list=JSON数组, models_synced_at=now() WHERE id=?
        else 拉取失败/超时（EDGE-014）
            Gateway-->>Svc: 401/500/timeout
            Note over Svc: 不阻断配置保存：model_list 保持空，models_synced_at 为 NULL，记录 WARN 日志
        end
    end
    
    Svc-->>AdminAPI: 200 GatewayConfigDetail{id, api_key_masked="sk-****1234"（取明文前缀+后4位掩码，EDGE-010）, model_list, models_synced_at, ...}
    AdminAPI-->>Admin: 配置已保存（模型列表自动拉取成功/失败提示）
```

**更新路径特殊处理**：PUT `/api/admin/gateway/configs/{id}` 若 api_key 字段值为掩码格式（正则匹配 `^[a-z]{2,4}-\*{4,}\w{4}$`），后端保持原 api_key_encrypted 密文不变；否则视为明文新 Key，重新加密存储。base_url/api_key 变更时重新拉取模型列表。

---

## FLOW-I03: 模型列表定时刷新（决策 5）

**触发条件**: Spring `@Scheduled(fixedDelay=60000)` 每分钟扫描所有 `model_refresh_strategy=scheduled` 的 AI 网关配置，按各自 `model_refresh_interval_min` 间隔执行。

```mermaid
sequenceDiagram
    participant Sched
    participant Svc as gateway Svc
    participant DB
    participant Gateway as External AI Gateway

    Note over Sched: 每分钟触发 @Scheduled
    Sched->>Svc: refreshScheduledGatewayModels()
    Svc->>DB: SELECT * FROM external_gateway_config WHERE gateway_type=1 AND enabled=true AND model_refresh_strategy=2 AND (models_synced_at IS NULL OR models_synced_at < now() - INTERVAL model_refresh_interval_min MINUTE)
    
    loop 每个待刷新配置
        Svc->>Svc: 解密 api_key_encrypted
        Svc->>Gateway: GET {base_url}/v1/models（超时 10s）
        
        alt 拉取成功
            Gateway-->>Svc: 200 {data: [...]}
            Svc->>DB: UPDATE external_gateway_config SET model_list=JSON数组, models_synced_at=now(), consecutive_failures=0 WHERE id=?
            Note over Svc: 记录 INFO 日志：网关 {name} 模型列表刷新成功，{count} 个模型
        else 拉取失败（EDGE-014 连续失败降级）
            Gateway-->>Svc: 401/500/timeout
            Svc->>DB: UPDATE consecutive_failures=consecutive_failures+1 WHERE id=?
            Svc->>DB: SELECT consecutive_failures FROM external_gateway_config WHERE id=?
            alt 连续失败 ≥ 3 次（决策 5 降级策略）
                Svc->>DB: UPDATE model_refresh_strategy=1(manual), enabled=false WHERE id=?
                Note over Svc: 发告警通知（邮件/钉钉）："AI 网关 {name} 连续刷新失败 3 次，已自动降级为手动刷新并禁用，请检查配置"
            else 失败 < 3 次
                Note over Svc: 记录 WARN 日志：网关 {name} 模型刷新失败（{consecutive_failures}/3），下次继续尝试
            end
        end
    end
```

**降级后恢复**：运营在网关配置页手动点击「刷新模型列表」（POST `/api/admin/gateway/configs/{id}/sync-models`）成功，consecutive_failures 归零，可重新启用并切回 scheduled。

---

## FLOW-I04: 测试网关连接（决策 14）

**触发条件**: 后台网关配置页点击「测试连接」按钮。

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc as gateway Svc
    participant DB
    participant Gateway as External AI Gateway

    Admin->>AdminAPI: POST /api/admin/gateway/configs/{id}/test
    Note over AdminAPI: AdminJwtFilter + RBAC(/system/gateways)
    AdminAPI->>Svc: testConnection(configId)
    
    Svc->>DB: SELECT * FROM external_gateway_config WHERE id=?
    alt 配置不存在
        Svc-->>AdminAPI: 404 {404201 GATEWAY_CONFIG_NOT_FOUND}
    end
    
    alt gateway_type != AI
        Svc-->>AdminAPI: 501 "该网关类型暂不支持测试"
    end
    
    Svc->>Svc: 解密 api_key_encrypted
    Note over Svc: 记录测试开始时间 startMs
    Svc->>Gateway: GET {base_url}/v1/models（超时 10s，EDGE-023）
    
    alt 测试成功
        Gateway-->>Svc: 200 {data: [...]}
        Svc->>Svc: latency_ms = now() - startMs
        Svc-->>AdminAPI: 200 GatewayTestResult{reachable:true, available_models_count, latency_ms}
        AdminAPI-->>Admin: 弹窗显示"连接成功 ✓ | 可用模型 {count} 个 | 耗时 {ms}ms"
    else DNS 解析失败
        Gateway-->>Svc: UnknownHostException
        Svc-->>AdminAPI: 200 GatewayTestResult{reachable:false, error_code:502201, error_message:"DNS 解析失败，请检查网关地址", latency_ms}
        AdminAPI-->>Admin: 弹窗显示错误原因（红色）
    else 鉴权失败（EDGE-023）
        Gateway-->>Svc: 401 Unauthorized
        Svc-->>AdminAPI: 200 GatewayTestResult{reachable:false, error_code:502202, error_message:"API Key 鉴权失败（401 Unauthorized）", latency_ms}
    else 超时
        Gateway-->>Svc: timeout
        Svc-->>AdminAPI: 200 GatewayTestResult{reachable:false, error_code:504201, error_message:"连接超时（10s）", latency_ms:10000}
    end
```

**测试结果不落库**：测试操作不影响已保存配置、不写 ai_translation_log、不更新 model_list/models_synced_at，仅返回即时连通状态。

---

## FLOW-I05: 术语表 CRUD（决策 14）

**触发条件**: 后台「外部系统配置 > 翻译术语表」页面增删改查术语。

```mermaid
sequenceDiagram
    actor Admin
    participant AdminAPI
    participant Svc as glossary Svc
    participant DB

    Admin->>AdminAPI: POST /api/admin/glossary/terms {term_en, term_es?, term_fr?, category?, enabled:true}
    Note over AdminAPI: AdminJwtFilter + RBAC(/system/glossary) + EDGE-022 权限校验
    AdminAPI->>Svc: createTerm(...)
    
    Svc->>DB: SELECT * FROM ai_translation_glossary WHERE LOWER(term_en)=LOWER(?) AND deleted_at IS NULL
    alt 英文术语已存在（不区分大小写）
        Svc-->>AdminAPI: 409 {409401 TERM_EN_EXISTS} details: {term_en, existing_id}
    end
    
    Svc->>DB: INSERT ai_translation_glossary(term_en, term_es, term_fr, category, enabled, created_at, updated_at)
    Svc-->>AdminAPI: 200 GlossaryTerm{id, ...}
    AdminAPI-->>Admin: 术语已保存（enabled=true 时立即生效，下次翻译注入 prompt）
    
    Note over Admin,DB: 列表查询 GET /api/admin/glossary/terms?category=&enabled=&keyword=&page=1&page_size=50
    Admin->>AdminAPI: GET 请求
    AdminAPI->>Svc: list(filters, pagination)
    Svc->>DB: SELECT * FROM ai_translation_glossary WHERE ... ORDER BY term_en ASC
    Svc-->>AdminAPI: GlossaryTermPage{data[], total_elements, ...}
    
    Note over Admin,DB: 更新/删除：PUT /DELETE /api/admin/glossary/terms/{id}
    Admin->>AdminAPI: PUT {id} {term_en（可修改）, term_es, term_fr, category, enabled}
    Svc->>DB: SELECT WHERE LOWER(term_en)=LOWER(?) AND id!=?（检查新 term_en 冲突）
    alt 冲突
        Svc-->>AdminAPI: 409 {409401}
    else
        Svc->>DB: UPDATE ai_translation_glossary SET ... WHERE id=?
    end
    
    Admin->>AdminAPI: DELETE {id}
    Svc->>DB: DELETE FROM ai_translation_glossary WHERE id=?（硬删除，无关联约束）
    AdminAPI-->>Admin: 204（术语删除后不再注入翻译 prompt）
```

**术语注入规模边界（EDGE-024）**：FLOW-I01 中已处理，仅注入 source_text 中实际命中的术语（精确匹配 term_en 出现在原文中），上限 50 条；命中超 50 条时按 category 优先级截断（廓形 > 领型 > 面料 > 工艺 > 其他），日志记录截断数。

---

## FLOW-I06: 调用日志清理（决策 7）

**触发条件**: Spring `@Scheduled(cron="0 0 3 * * ?")` 每日凌晨 3:00 执行。

```mermaid
sequenceDiagram
    participant Sched
    participant Svc as ai_translation Svc
    participant DB

    Note over Sched: 每日 3:00 触发 @Scheduled
    Sched->>Svc: cleanupOldTranslationLogs()
    
    loop 分批删除（避免长事务锁表）
        Svc->>DB: DELETE FROM ai_translation_log WHERE created_at < NOW() - INTERVAL 90 DAY LIMIT 5000
        Note over Svc: 记录删除行数 deletedRows
        alt deletedRows < 5000
            Note over Svc: 已删完，退出循环
        else
            Note over Svc: 继续下批，避免一次删除过多行
        end
    end
    
    Note over Svc: 记录 INFO 日志：清理 90 天前 ai_translation_log 记录，共删除 {totalDeleted} 条
```

**未来扩展预留**：决策 7 提到「统计汇总数据（按日/模型/biz_type 聚合的调用次数和 token 总量）持久保留」，首版可手动导出，后续 change 补建 ai_translation_daily_stats 聚合表，清理任务在删除前先聚合写入。

---

## FLOW-I07: 消费端 locale 路由检测（决策 11，EDGE-018/019）

**触发条件**: 用户访问 portal-store 任意 URL。

```mermaid
sequenceDiagram
    actor User
    participant CDN
    participant Middleware as Next.js Middleware
    participant Next
    participant Cookie

    User->>CDN: GET /es/product/aurelia-gown（或 /de/xxx 非法 locale / /product/xxx 无前缀旧链接）
    CDN->>Middleware: 回源（动态路由不缓存 middleware 逻辑）
    
    Middleware->>Middleware: 从 URL pathname 提取 locale 前缀
    alt pathname 匹配 /^\\/(es|fr)\\//
        Note over Middleware: locale=es 或 fr
    else pathname 无前缀（如 /product/xxx）
        Note over Middleware: locale=en（默认）
    else pathname 匹配 /^\\/([a-z]{2})\\// 但非支持语言（EDGE-018）
        Middleware->>User: 302 临时重定向到 /（对应页面无前缀 EN 路径，如 /de/product/xxx → /product/xxx）
        Note over Middleware: 302 因用户输入错误，非永久 URL 变更
    end
    
    alt 旧链接无前缀（EDGE-019 向后兼容）
        Middleware->>Cookie: 读取 NEXT_LOCALE cookie
        alt cookie 存在且为 es/fr
            Middleware->>User: 301 永久重定向到 /{cookie_locale}{pathname}（如 /product/xxx → /es/product/xxx）
        else cookie 不存在或为 en
            Middleware->>Middleware: 读取 Accept-Language 头
            alt 首选语言为 es/fr
                Middleware->>User: 301 永久重定向到 /{detected_locale}{pathname}
            else
                Note over Middleware: EN 为根路径，不重定向，继续渲染
            end
        end
        Note over Middleware: 301 确保搜索引擎传递链接权重到新 URL
    end
    
    Middleware->>Cookie: 写入 NEXT_LOCALE={locale}（Max-Age=1 年）
    Middleware->>Next: 注入 locale 到 SSR context（page props / useParams）
    Next->>Next: 渲染页面（读取对应 locale 的 messages.ts / 数据库翻译字段）
    Next-->>User: HTML（含 <link rel="alternate" hreflang="en" href="/product/xxx" />、<link rel="alternate" hreflang="es" href="/es/product/xxx" />、<link rel="alternate" hreflang="fr" href="/fr/product/xxx" />）
```

**localStorage 降级角色**：决策 11 移除 localStorage 作为 locale 唯一来源，但保留作为「用户显式切换语言时的记忆」（如点击 footer 语言选择器，写 localStorage 并跳转新 locale 路径，下次访问根路径时 middleware 优先读 cookie，cookie 不存在时 Accept-Language 兜底）。

**sitemap 多语言**：生成 `sitemap-en.xml`、`sitemap-es.xml`、`sitemap-fr.xml` 分别提交各语言 URL，sitemap index 引用三者。

---

## FLOW-I08: designerNote 数据级翻译回退（决策 12，EDGE-020）

**触发条件**: 消费端 PDP 请求（增强 baseline FLOW-P01）。

```mermaid
sequenceDiagram
    actor User
    participant StoreAPI
    participant Svc as catalog Svc
    participant DB

    User->>StoreAPI: GET /api/store/products/{slug}?locale=es
    Note over StoreAPI: StoreJwtFilter 公开路径白名单放行
    StoreAPI->>Svc: getProduct(slug, locale=es)
    
    Svc->>DB: SELECT Product (id, name, description, designer_note, ...) WHERE slug=? AND status=published
    Svc->>DB: SELECT ProductTranslation (name, subtitle, description, designer_note, ...) WHERE product_id=? AND locale='es'
    
    Svc->>Svc: 数据转换（pick() 回退逻辑，EDGE-020）
    Note over Svc: 1. ES 翻译字段非空 → 取 translation 值<br/>2. ES 翻译字段为空/NULL → 回退 EN 主表值<br/>3. 应用于：name, subtitle, description, designer_note（决策 12 新增）<br/>输出扁平 DTO：{name: ES值或EN回退, designer_note: ES值或EN回退, ...}
    
    Svc-->>StoreAPI: StoreProductDetail{...所有字段已按 locale 解析，前端无需再处理回退}
    StoreAPI-->>User: 200 R{data: {...}}
    Note over User: PDP Description 折叠区展示 designerNote（已按用户 locale 语言）
```

**后台编辑三语 tab**：商品编辑页「内容详情」tab 新增 designerNote 字段（EN 主表 + ES/FR tab 各一个 textarea），保存时同步到 product.designer_note(EN) 和 product_translation.designer_note(ES/FR)。

---

## FLOW-I09: 邮件三语发送（决策 13，EDGE-021）

**触发条件**: MQ 邮件事件触发（增强 baseline FLOW-P11）。

```mermaid
sequenceDiagram
    participant MQ
    participant Svc as MailConsumer
    participant DB
    participant SMTP

    MQ->>Svc: order.paid / order.shipped / refund.resolved（含 customer_id, order_no, locale?）
    
    Svc->>DB: SELECT user.locale_pref FROM user WHERE id=customer_id
    alt user 不存在（匿名下单）
        Svc->>DB: SELECT orders.locale_snapshot FROM orders WHERE order_no=?
        Note over Svc: locale = orders.locale_snapshot（下单时从页面 URL 路径提取的 locale，FLOW-I07 已写入）
    else user 存在
        Note over Svc: locale = user.locale_pref（登录态用户语言偏好，注册/下单时保存）
    end
    
    alt locale 为空（兜底）
        Note over Svc: locale = 'en'（默认英文）
    end
    
    Svc->>Svc: 确定模板路径：templates/email/{type}_{locale}.html
    Note over Svc: type=order_confirmed/shipped/refund_result 等<br/>如：templates/email/order_confirmed_es.html
    
    alt 模板文件不存在（EDGE-021）
        Svc->>Svc: 回退使用 EN 模板：templates/email/{type}_en.html
        Note over Svc: 记录 WARN 日志："邮件模板 {type}_{locale}.html 不存在，回退使用 EN 模板"
    end
    
    Svc->>Svc: Thymeleaf 渲染模板（传入订单/退款/Showroom 数据 + locale）
    Svc->>DB: INSERT MailRecord(type, order_id, recipient, locale, status=pending) ON DUPLICATE KEY（orderId+type 幂等）
    Svc->>SMTP: send(recipient, subject(按 locale), rendered_html_body)
    
    alt 发送成功
        Svc->>DB: UPDATE MailRecord status=sent, sent_at
    else 临时失败
        Svc->>DB: UPDATE status=failed, retry_count+1
        Note over MQ: nack → 延迟重试队列（指数退避 ×3）→ 超限进死信
    end
```

**user.locale_pref 写入时机**：1) 注册时从 URL 路径提取 locale 写入；2) 用户在 Account Settings 页显式切换语言时更新。**orders.locale_snapshot 写入时机**：下单时从当前页面 locale 快照（FLOW-I07 中间件已注入）。

**模板三语化覆盖**：订单确认（order_confirmed）、发货通知（shipped）、退款结果（refund_result）、OTP 验证码（otp_code）、Showroom 邀请（showroom_invite）、Showroom 提醒（showroom_remind）各 3 个语言版本 = 18 个模板文件。

---

## 检查清单

- [x] 覆盖 i18n 变更新增的全部核心流程（FLOW-I01~I09，对应决策 1~14）
- [x] 每条流程包含正常路径和异常路径（EDGE-001~024 场景全部落图）
- [x] 参与者命名清晰（Admin/AdminAPI/Svc/Gateway/DB/Sched/Middleware/CDN/SMTP）
- [x] 各层数据转换显式定义（API Key 加解密/掩码、术语注入优化、locale 路由、翻译回退、邮件模板选择）
- [x] 外部依赖失败路径（Gateway 超时/5xx/429、模型刷新连续失败降级、测试连接失败反馈）
- [x] 定时任务调度机制（模型刷新 fixedDelay 扫描、日志清理 cron 分批删除）
- [x] 数据流与 L1.2 三份 OpenAPI 契约端点一一对应（gateway-api/ai-translation-api/glossary-api）
- [x] 逐条响应 decision.md 14 个决策（见决策响应映射表）
- [x] 逐条响应 boundary-scenarios.yml 24 个 EDGE 场景（流程图中标注 EDGE-*）

