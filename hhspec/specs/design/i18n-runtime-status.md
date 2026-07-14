# i18n / AI 翻译当前运行状态（2026-07-14）

本文件覆盖 2026-06-16 初版设计中关于翻译日志和术语表的内容。

- 保留：外部网关配置；`POST /api/admin/ai/translate` 即时翻译代理；管理端 AI 翻译弹窗。
- 退役：`ai_translation_log`、`GET /api/admin/ai/translation-logs`、
  `ai_translation_glossary`、`/api/admin/glossary/**`、`/system/glossary`，以及术语注入 prompt。
- 数据迁移：`V20260617_drop_ai_translation_modules.sql` 使用 `DROP TABLE IF EXISTS` 删除两张退役表。
- 当前 prompt：婚纱电商固定前缀 + 可选 `custom_requirement` + 源/目标语言；不读取日志或术语表。
- 当前校验：请求字段错误统一返回 `422201`；显式 `model` 必须存在于网关的有效 `model_list`，
  列表为空、损坏或不包含该模型均返回 `400302`。
- 当前失败策略：直接返回网关域错误码，前端提示后仍可继续手工编辑和保存；不落成功或失败调用日志。
- 网关持久化：`api_key_encrypted VARCHAR(4096)` 可容纳 512 字符 Unicode 明文的 AES-GCM/Base64
  密文；`uk_type_name(gateway_type,name)` 和 `idx_type_enabled(gateway_type,enabled)` 是权威索引。
- 并发控制：配置更新必须回传整数 `version`，后端执行 `UPDATE ... WHERE id/version` 并原子 +1；
  `updated_at` 仅为信息字段。模型同步成功或失败计数也携带请求加载时的 expected version，只有
  配置未被并发编辑时才原子落库并推进 version；迟到结果不会覆盖新 URL、Key 或启用状态。
- 模型同步失败：失败计数提交后才向调用方返回异常；scheduled 任务第 3 次失败在同一 SQL 中
  降级为 manual 并 disabled，手动同步失败只计数，避免误禁用和并发丢计数。
- 部署迁移：先执行 `V20260714_01_physical_delete.sql`，再执行
  `V20260714_03_gateway_integrity.sql`；后者会 guard 残留 `deleted_at`、重复名称及错误同名索引。

`ai-translation-api.openapi.yml` 和 `glossary-api.openapi.yml` 是机器可读权威契约。其他初版 L2
文档若仍包含日志/Glossary 的实体、页面、测试或错误码，仅作为已归档设计背景，不代表活跃能力。
