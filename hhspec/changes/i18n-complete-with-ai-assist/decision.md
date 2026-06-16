# 关键决策：i18n-complete-with-ai-assist

## 决策 1：外部网关配置存储模型
- **选择**：数据库单表多类型（`external_gateway_config`）
- **理由**：可扩展（AI/物流/支付网关共用一张表），支持 CRUD、审计、前端可视化管理；API Key 落库加密存储，前端不可见
- **备选**：环境变量（太轻量，不支持运行时修改和多模型列表）；每种网关独立表（扩展性差）

## 决策 2：AI 翻译请求架构
- **选择**：后端代理模式
- **理由**：API Key 不暴露给浏览器，后端统一控制超时/重试/日志记录
- **备选**：前端直连（安全风险，API Key 泄露）

## 决策 3：翻译协议
- **选择**：当前支持 OpenAI-compatible 协议（/v1/chat/completions + /v1/models），预留协议扩展点
- **理由**：市面主流 AI 网关（OpenRouter/One API/Azure）均兼容 OpenAI 格式
- **备选**：多协议同时支持（过早优化，当前无需求）

## 决策 4：模型选择策略
- **选择**：两级 — 全局默认模型 + 翻译弹窗可下拉切换
- **理由**：日常一键翻译用默认模型（快），偶尔切换特定模型（灵活）
- **备选**：仅全局默认（不够灵活）；每次必选（操作繁琐）

## 决策 5：模型列表自动发现
- **选择**：配置保存时自动拉取（/v1/models），配置页提供刷新策略下拉（手动刷新 / 定时 N 分钟刷新）
- **调度机制**：定时刷新由 Spring `@Scheduled` fixedDelay 实现，启动时扫描所有 `model_refresh_strategy=scheduled` 的配置，按各自 `model_refresh_interval_min` 间隔执行。单次失败不中断调度（记录 WARN 日志 + models_synced_at 不更新），连续失败 3 次后降级为 manual 并发告警通知
- **理由**：保存时拉取保证首次可用；下拉刷新策略覆盖"新增模型后即时可见"和"无人值守自动更新"两种场景
- **备选**：每次打开弹窗实时拉取（慢，每个翻译按钮都要等）

## 决策 6：翻译弹窗交互设计
- **选择**：弹窗内展示系统 prompt（只读/不可编辑，锁定婚纱礼服电商领域背景）+ 用户自定义要求输入框 + 模型选择下拉 + 翻译按钮
- **理由**：系统 prompt 确保翻译质量在领域内，用户要求提供灵活性（如"语气更正式"、"简洁些"）
- **备选**：无自定义输入（不够灵活，翻译结果不可控）

## 决策 7：调用记录
- **选择**：新增 `ai_translation_log` 表，记录每次翻译请求的：时间、操作人、源文本、译文、目标语言、使用模型、响应耗时、token 用量、状态（success/failed）、业务来源（biz_type/biz_ref）
- **数据保留策略**：保留 90 天明细，超过 90 天的记录由定时任务（Spring `@Scheduled` 每日凌晨 3:00）批量删除（`DELETE WHERE created_at < NOW() - INTERVAL 90 DAY LIMIT 5000` 分批执行，避免长事务锁表）。统计汇总数据（按日/模型/biz_type 聚合的调用次数和 token 总量）持久保留，由同一定时任务在删除前先聚合写入 `ai_translation_daily_stats`（非本次 scope，首版可手动导出，后续 change 补建聚合表）
- **理由**：便于追踪费用、排查问题、统计使用频率
- **备选**：不记录（无法追踪费用和问题）；永久保留（否决，text 字段占用大，90 天足够排查问题）

## 决策 8：后台界面多语言
- **选择**：不翻译，后台保持中文
- **理由**：运营团队全中文，无国际化需求
- **备选**：后台也做 vue-i18n（工作量巨大，当前无收益）

## 决策 9：消费端 i18n 范围
- **选择**：全部补齐，覆盖 50+ 组件的硬编码英文，新增 30+ 命名空间
- **理由**：一步到位，避免分阶段的协调成本
- **备选**：分阶段（主购物动线优先）

## 决策 10：AI 翻译失败处理
- **选择**：错误提示（toast/inline）+ 允许继续保存
- **理由**：不阻塞运营工作流，翻译失败可手动补
- **备选**：阻塞保存（过于严格）

## 决策 11：消费端 locale 策略（SEO 可索引）
- **选择**：URL 路径前缀（`/es/`、`/fr/`，EN 为无前缀根路径），配合 `hreflang` 标签 + 多语言 sitemap；**移除 localStorage 作为 locale 唯一来源**
- **理由**：跨境外贸的 ES/FR 自然搜索流量依赖搜索引擎可索引各语言版本。localStorage 客户端切换导致同一 URL 三语，搜索引擎只索引默认 EN，ES/FR 市场拿不到自然流量。路径前缀让每个语言有独立可索引 URL，是 Next.js App Router i18n 的标准做法（`app/[locale]/` 动态段）。用户语言偏好改由 URL + cookie（SSR 可读）承载，localStorage 仅作降级辅助（用户显式切换时的记忆，不作为路由依据）
- **备选**：
  - 维持 localStorage（最强理由：改动最小，无需重构路由）——但 SEO 不可索引是外贸电商的硬伤，否决
  - 子域名 `es.dreamy.com`（最强理由：地域信号更强）——但需要额外 DNS/证书/部署配置，运维成本高，且当前单部署架构不支持，否决
- **影响**：portal-store 路由重构（`app/` → `app/[locale]/`），所有内部链接需带 locale 前缀，middleware 做 locale 检测与重定向

## 决策 12：数据级翻译盲区补齐
- **选择**：
  1. `designerNote`（设计师备注）纳入 `product_translation` 表（新增 `designer_note` 列）+ 后台三语 tab 增加该字段 + 消费端 `assembleDetail` 用 `pick()` 回退
  2. 面料材质名（Cotton/Lace/Satin 等）+ 层级名（Shell/Lining 等）走消费端 i18n 字典（`messages.ts` 的 `fabric` 命空间），不走数据库——因为材质是有限枚举（10 种材质 + 4 种层级），非每商品不同
- **理由**：designerNote 在 PDP Description 折叠区展示给用户，属于商品个性化文案，归数据级翻译；面料材质名是固定术语集，归字典级翻译，两者性质不同需分别处理
- **备选**：面料材质也入库（否决，固定枚举入库是过度设计，且每商品冗余存储相同译文）

## 决策 13：后端发出内容多语言（邮件/通知）
- **选择**：
  1. 新增用户语言偏好持久化：`user` 表增加 `locale_pref` 字段（消费端登录态用户），匿名下单时从下单页 locale 快照到 `orders.locale_snapshot`
  2. 邮件模板三语化：订单确认/OTP/退款通知等模板按 locale 选择对应语言版本
  3. 后端发送邮件时按"用户偏好 locale → 订单快照 locale → 默认 EN"优先级选模板
- **模板存储方案**：classpath 资源文件（`resources/templates/email/{template_name}_{locale}.html`），Thymeleaf 渲染。理由：模板数量有限（3 类 × 3 语言 = 9 文件），变更频率低（随代码版本发布），不需要运行时编辑能力。若后续需要运营可编辑模板，再独立 change 迁移到 DB 模板表
- **理由**：订单/OTP/退款邮件由后端发送，后端此前拿不到用户语言（只在浏览器 localStorage）。外贸用户收到全英文邮件体验割裂。需要 locale 在下单/注册时落库，后端发信时可读
- **备选**：邮件只发 EN（否决，与三语商城体验不一致）；前端渲染邮件（否决，OTP/异步通知无前端上下文）；DB 模板表（否决，当前模板数量少且变更随版本走，DB 增加复杂度无收益）

## 决策 14：AI 辅助完整性增强
- **选择**：
  1. 网关配置「测试连接」按钮：保存前/后可点击，后端用最小请求（如列模型或一次 ping translate）验证 URL+Key 可用性，返回连通状态
  2. 婚纱领域术语表（glossary）：维护一份 EN→ES/FR 的专业术语对照（A-line/ball gown/sweetheart neckline 等），翻译时注入 system prompt，保证术语译法一致性
- **理由**：测试连接避免配错要等真实翻译才暴露；术语表保证 AI 多次翻译同一术语的一致性，这是婚纱垂直领域翻译质量的关键
- **备选**：不做测试连接（否决，配置体验差）；不做术语表（否决，术语译法漂移影响专业度）
- **未纳入本次**：AI 调用成本配额上限（用户未选，后续可独立 change 增强）

---

## 数据库新增表

> 字段以 er-diagram.yml 为权威定义，此处为可读摘要。

### external_gateway_config（实体 ExternalGatewayConfig）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 自增主键 |
| gateway_type | tinyint | 网关类型枚举：AI(1) / LOGISTICS(2) / PAYMENT(3) |
| name | varchar(64) | 配置名称（如"OpenRouter 翻译网关"） |
| protocol | tinyint | 协议枚举：openai(1)，预留扩展 |
| base_url | varchar(255) | 网关地址（OpenAI-compatible base） |
| api_key_encrypted | varchar(512) | AES 加密的 API Key（前端掩码） |
| default_model | varchar(128) | 全局默认模型（两级模型选择默认值） |
| model_list | json | 缓存的模型列表（/v1/models 拉取） |
| model_refresh_strategy | tinyint | 刷新策略：manual(1) / scheduled(2) |
| model_refresh_interval_min | int | 定时刷新间隔分钟（strategy=scheduled 生效） |
| models_synced_at | datetime | 模型列表最后刷新时间 |
| enabled | tinyint(1) | 是否启用 |
| extra_config | json | 协议扩展配置（预留非 OpenAI 字段） |
| created_at | datetime | |
| updated_at | datetime | |

唯一约束：`(gateway_type, name)`。

### ai_translation_log（实体 AiTranslationLog）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 自增主键 |
| gateway_config_id | bigint | 关联网关配置（逻辑外键） |
| model | varchar(128) | 实际调用的模型名 |
| source_lang | varchar(8) | 源语言（en 主字段） |
| target_lang | varchar(8) | 目标语言（es/fr） |
| source_text | text | 原文 |
| translated_text | text | 译文（失败时为空） |
| custom_requirement | text | 用户追加的自定义翻译要求 |
| biz_type | varchar(32) | 业务来源类型（product/category/tag/banner…） |
| biz_ref | varchar(64) | 业务来源标识（如 product_id，溯源用） |
| status | tinyint | success(1) / failed(2) |
| error_message | varchar(512) | 失败原因（status=failed 时） |
| latency_ms | int | 调用耗时毫秒 |
| token_usage | json | token 消耗（prompt/completion/total） |
| operator_id | bigint | 操作人（管理员 id） |
| created_at | datetime | |

索引：`(biz_type, biz_ref)` 业务溯源、`(created_at)` 时间倒序分页。

### ai_translation_glossary（实体 AiTranslationGlossary，决策 14）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 自增主键 |
| term_en | varchar(128) | 英文术语（如 A-line / sweetheart neckline） |
| term_es | varchar(128) | 西语标准译法 |
| term_fr | varchar(128) | 法语标准译法 |
| category | varchar(32) | 术语分类（廓形/领型/面料/工艺等，可选） |
| enabled | tinyint(1) | 是否启用（注入 prompt） |
| created_at | datetime | |
| updated_at | datetime | |

唯一约束：`(term_en)`。翻译时启用项注入 AI system prompt 保证术语一致性。

---

## 现有表字段变更（非新增表）

### product_translation（新增列，决策 12）
| 字段 | 类型 | 说明 |
|------|------|------|
| designer_note | text NULL | 设计师备注译文（ES/FR），消费端 `pick()` 回退 EN |

### user（新增列，决策 13）
| 字段 | 类型 | 说明 |
|------|------|------|
| locale_pref | varchar(8) NULL | 用户语言偏好（en/es/fr），登录态承载，邮件发信优先取此值 |

### orders（新增列，决策 13）
| 字段 | 类型 | 说明 |
|------|------|------|
| locale_snapshot | varchar(8) NULL | 下单时 locale 快照（匿名/兜底用），邮件发信第二优先级 |

---

## 影响范围

| 端 | 变更内容 |
|----|---------|
| 后端 | 新增 `gateway` domain（网关配置/AI 代理/术语表/调用日志）+ `AdminGatewayController` + `AdminAiController`（翻译代理 + 测试连接）+ `AdminGlossaryController`；邮件发送服务三语模板选择（决策 13）；user/orders 增 locale 字段 |
| portal-admin | 新增「外部系统配置」页面（含测试连接）+「翻译术语表」页面 + AI 翻译弹窗组件 + 11 处翻译表编辑区增加按钮 + 商品编辑「内容详情」增 designerNote 三语字段 |
| portal-store | **路由重构 `app/` → `app/[locale]/`（决策 11）** + middleware locale 检测 + hreflang/sitemap；扩展 messages.ts（30+ 命名空间 + fabric 材质/层级命名空间）+ 50+ 组件接入 useI18n；移除 localStorage 作为 locale 唯一来源 |
| 数据库 | 新增 3 张表（external_gateway_config / ai_translation_log / ai_translation_glossary）+ 3 处现有表加列（product_translation.designer_note / user.locale_pref / orders.locale_snapshot）|
| 邮件 | 订单确认/OTP/退款通知模板三语化（决策 13）|
