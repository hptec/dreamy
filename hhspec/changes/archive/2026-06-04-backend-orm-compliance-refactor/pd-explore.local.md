---
phase: requirements_confirmed
challenger_rounds: 0
session_id: ""
change_name: "backend-orm-compliance-refactor"
codebase_path: "/Volumes/MAC/workspace/dreamy"
change_type: "greenfield"
key_decisions:
  - id: "DEC-001"
    title: "OTP 并发控制：Redis 分布式锁替代 DB 行锁"
    answer: "用 IdLockSupport 按 email 加 Redisson tryLock，删除 @Select FOR UPDATE，@Version 乐观锁作兜底"
    source: "user_decision"
    dimension: "BE-DIM-4"
  - id: "DEC-002"
    title: "OperationLogMapper 流式导出替代"
    answer: "删除 @Select(<script>) + ResultHandler，改分页轮询（每页1000条 LambdaQueryWrapper）"
    source: "user_decision"
    dimension: "BE-DIM-4"
  - id: "DEC-003"
    title: "DBConst 全量改造"
    answer: "迁包 repository→consts，建 CommonDBConst，代码中真正引用常量"
    source: "user_decision"
project_tech_stack:
  backend: java-gradle
  runtime: jdk25
  orm: mybatis-plus-latest
detected_tech_specs:
  - "huihao-mysql 0.3.9.45-jdk25-SNAPSHOT"
  - "OtpCodeEntity 已有 @Version 乐观锁"
  - "Redis 频控（5次/时）OTP 验证前已生效"
has_ui: false
prototype_dir: ""
feature_map_path: ""
linked_features: []
requirement_ids: []
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
l0_breaking_changes_count: 0
content_index_path: ""
content_index_paths: {}
content_index_missing_portals: []
page_diff_summary: []
open_questions: []
created_at: "2026-06-04T07:59:38Z"
updated_at: "2026-06-04T16:05:00+08:00"
---

<!-- 探索进展摘要 2026-06-04 第1轮 -->

## 已确认的改造范围

**A. Native SQL 消除（6处→0处）**
- A1 OTP 行锁：FOR UPDATE → Redis 分布式锁（IdLockSupport）+ @Version 乐观锁
- A2/A4/A5：简单 @Select → LambdaQueryWrapper
- A3：@Select JOIN → 分步查询 + 内存聚合
- A6 流式导出：@Select(<script>) → 分页轮询（每页1000条）

**B. 硬编码列名 Wrapper**
- B1：UpdateWrapper 硬编码 → LambdaUpdateWrapper

**D. DBConst 系统性改造**
- 迁包：13个文件 repository/ → consts/
- 新增：domain/consts/CommonDBConst.java
- 补全：role 聚合 3 个 DBConst 的基类列
- 引用：代码中真正引用常量

**E. 全表扫描修复**
- E2：selectList(null) → 显式条件

## 待 Phase 2 深度探索
- Q-OTP-LOCK-001：Redis 锁超时/降级/幂等策略
