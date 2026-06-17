---
phase: L1_decisions_done
challenger_rounds: 0
session_id: 7a07ad67-3cc9-44b1-88c3-fac64a3a1abf
change_name: i18n-complete-with-ai-assist
codebase_path: /Volumes/MAC/workspace/dreamy
change_type: greenfield
key_decisions:
- question: AI 网关配置存储
  answer: 数据库表(external_gateway_config), type 区分网关类型, config JSON, API Key 加密
- question: AI 翻译架构
  answer: 后端代理, 前端不接触 API Key
- question: 后台界面多语言
  answer: 不翻译, 后台保持中文, 仅翻译消费端内容
- question: AI 翻译失败处理
  answer: 错误提示 + 允许继续保存
- question: 消费端 i18n 范围
  answer: 全部补齐 50+ 组件
- question: 模型选择策略
  answer: 两级 - 弹窗默认全局模型, 可下拉切换
- question: 模型列表发现
  answer: 配置保存时自动拉取 + 手动刷新/定时刷新下拉选项
- question: 翻译弹窗交互
  answer: 用户可追加自定义要求, 系统 prompt 锁定婚纱礼服领域背景
- question: 调用记录
  answer: 新增 ai_translation_log 表记录每次翻译请求/响应/耗时/token/状态
project_tech_stack: {}
detected_tech_specs: []
has_ui: false
prototype_dir: ''
feature_map_path: ''
linked_features: []
requirement_ids:
- FUNC-001
- FUNC-002
- FUNC-003
- FUNC-004
- FUNC-005
- FUNC-006
- FUNC-007
- FUNC-008
- FUNC-009
- FUNC-010
- FUNC-011
- FUNC-012
- FUNC-013
- FUNC-014
- FUNC-015
- FUNC-016
- FUNC-017
- FUNC-018
- FUNC-019
- FUNC-020
- FUNC-021
- FUNC-022
linked_prototype_snapshots: []
operation_paths_file: ''
operation_paths_files: {}
proto_is_frontend: false
field_inventory_path: ''
domain_data_model_path: ''
global_field_dict_path: ''
task_manifest_path: ''
backend_inference_path: ''
backend_requirements_resolved: false
l0_mode: ''
baseline_context_path: ''
l0_breaking_changes_count: 0
content_index_path: ''
content_index_paths: {}
content_index_missing_portals: []
page_diff_summary: []
open_questions: []
created_at: '2026-06-16T07:32:43Z'
updated_at: '2026-06-16T16:07:58.638986'
---

<!-- 探索进展摘要（每轮更新） -->
