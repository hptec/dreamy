---
phase: L1_decisions_done
challenger_rounds: 0
session_id: ""
change_name: "backend-refactor-module-entity"
codebase_path: "/Volumes/MAC/workspace/dreamy"
change_type: "compliance"
key_decisions:
  - id: D1
    title: "包名保持 com.dreamy.identity"
    answer: "不改 huihao. 前缀，避免全量重命名"
    source: "keyword"
  - id: D2
    title: "Gradle 合并为单工程"
    answer: "common/store/admin/app 四子模块 → 单工程，内部按 domain 分包，无独立 app 入口"
    source: "keyword"
  - id: D3
    title: "实体基类 LongAuditableEntity（Long 自增主键）"
    answer: "放弃 String 主键(IdType.INPUT)，全量迁移为 Long AUTO_INCREMENT"
    source: "keyword"
  - id: D4
    title: "Redis：huihao-redis 做锁 + 保留 JetCache"
    answer: "引入 huihao-redis 提供 IdLockSupport 分布式锁；JetCache 两级缓存 + StringRedisTemplate 频控保留"
    source: "keyword"
  - id: D5
    title: "huihao 库来源 Nexus 私仓"
    answer: "com.huihao:huihao-mysql/base/web:0.3.9.45-jdk25-SNAPSHOT @ http://116.62.167.155:8081"
    source: "keyword"
project_tech_stack: {}
detected_tech_specs:
  - "现状：4 个 Gradle 子模块按技术层切分(common 放全部实体/service，store/admin 放 controller)"
  - "现状：实体用 @TableName + 无基类 + OffsetDateTime + String 主键，未用 huihao-mysql 注解"
  - "现状：mysql 用社区 mybatis-plus-spring-boot3-starter，未用 @EnableMysql/huihao.mysql.*"
  - "现状：redis 用 JetCache(lettuce) + spring-data-redis StringRedisTemplate，未用 huihao-redis"
  - "权威样板：/Volumes/MAC/workspace/vmedi/vmedi-backend（JDK25 单工程 + huihao-mysql + domain 分包）"
has_ui: false
prototype_dir: ""
feature_map_path: ""
linked_features: []
requirement_ids:
  - "COMPLIANCE-001"
  - "COMPLIANCE-002"
  - "COMPLIANCE-003"
linked_prototype_snapshots: []
operation_paths_file: ""
operation_paths_files: {}
proto_is_frontend: false
field_inventory_path: ""
domain_data_model_path: ""
global_field_dict_path: ""
task_manifest_path: ""
backend_inference_path: ""
backend_requirements_resolved: true
l0_mode: ""
baseline_context_path: ""
l0_breaking_changes_count: 1
content_index_path: ""
content_index_paths: {}
content_index_missing_portals: []
page_diff_summary: []
open_questions: []
created_at: "2026-06-02T12:09:40Z"
updated_at: "2026-06-02T12:30:00Z"
---

<!-- 探索进展摘要 -->

## COMPLIANCE 重构（无原型，纯后端规范对齐）

三条规范缺口（用户提出）：
- **COMPLIANCE-001 模块/domain 划分**：4 子模块按技术层切分 → 合并单工程，按 domain 分包（domain/{user,role,auth,session,...}/{entity,repository,service,dto}）
- **COMPLIANCE-002 实体设计**：改用 huihao-mysql `@Table`/`@Column` + 继承 LongAuditableEntity + 配套 DBConst
- **COMPLIANCE-003 mysql/redis 用 huihao-***：引入 huihao-mysql(@EnableMysql DDL-auto) + huihao-redis(IdLockSupport)

关键 breaking change：主键 String→Long 自增（影响全部实体/外键/DTO/前端契约/IdGenerator）。

权威样板：vmedi-backend。决策详见 decision.md。
