---
phase: init
challenger_rounds: 0
session_id: f15506d4-94e0-48e3-948d-6e5557731eba
change_name: "enum-refactor"
codebase_path: "/Volumes/MAC/workspace/dreamy"
change_type: "greenfield"
key_decisions:
  - "8 个枚举统一实现 huihao.enums IntEnum + Describable + @Enumable，key/desc 双字段模式（ACTIVE(1,\"正常\")）"
  - "实体枚举字段类型为枚举本身，DB 列为 tinyint，但缺 MyBatis-Plus enum↔tinyint TypeHandler 配置"
  - "权威样板 vmedi 在 application.yml 配 default-enum-type-handler: com.vmedi.config.IntEnumTypeHandler（手写 BaseTypeHandler，setInt(getKey)/getEnumByKey），Dreamy 缺失"
  - "Jackson 序列化未注册 TypeableEnumModule，枚举出参当前为 .name() 字符串而非 key 数字或 {key,desc}"
project_tech_stack: {}
detected_tech_specs: []
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
backend_requirements_resolved: false
l0_mode: ""
baseline_context_path: ""
l0_breaking_changes_count: 0
content_index_path: ""
content_index_paths: {}
content_index_missing_portals: []
page_diff_summary: []
open_questions: []
created_at: "2026-06-04T23:39:58Z"
updated_at: "2026-06-04T23:39:58Z"
---

<!-- 探索进展摘要（每轮更新） -->

## 枚举重构现状探索（iteration 4，2026-06-05）

### 现状盘点
- **8 个枚举**（`domain/enums/`）：AdminStatus、AuthProvider、LoginOutcome、OtpStatus、RoleType、SessionStatus、UserStatus、UserTier。统一写法：`@Enumable implements IntEnum, Describable` + `@Getter Integer key / String desc` + 构造函数，常量形如 `ACTIVE(1,"正常")`。
- **实体使用**：7 个实体的枚举字段类型为枚举本身，DB 列为 `tinyint`。
- **DTO/API**：DTO 字段直接引用枚举类型，无 `@JsonValue`/`@JsonFormat`。

### 核实确认的关键缺口（对照权威样板 vmedi）
1. **MyBatis-Plus 持久化未配置**（已核实）：`application.yml:43-46` 仅有 `id-type: auto`，**缺 `default-enum-type-handler`**。vmedi 在 `application.yml:25` 配了 `com.vmedi.config.IntEnumTypeHandler`（手写 `BaseTypeHandler<E extends Enum<E> & IntEnum>`，`setNonNullParameter` 用 `getKey()` 写 int，`getNullableResult` 按 key 反查）。Dreamy 无此 TypeHandler 类，枚举默认按 `.name()` 持久化，与 tinyint 列不匹配。
2. **Jackson 序列化未注册**（已核实）：Dreamy 无 ObjectMapper Bean / TypeableEnumModule 注册，`@SpringBootApplication(scanBasePackages="com.dreamy.identity")` 不会扫到 huihao-web 的 module → 枚举出参为 `.name()` 字符串，非 key 数字或 {key,desc}。
3. **huihao-enums 无自动配置**（已核实）：huihao-base/mysql 无 `*.imports`/`spring.factories` 自动注册 TypeHandler/Module，必须项目侧手动配置（故 vmedi 才需手写 + yml 配置）。

### 待澄清的需求决策（open questions）
- 枚举重构目标究竟是：(a) 补齐 IntEnumTypeHandler + yml 配置对齐 vmedi？(b) 同时引入 Jackson 序列化为 {key,desc}/key？(c) 把 `OperationLog.action`、`EmailTemplate.code` 等裸 String 提取为枚举？
- API 契约：前端期望枚举字段返回数字 key、字符串 name 还是 {key,desc} 对象？（影响是否引入 TypeableEnumModule）
