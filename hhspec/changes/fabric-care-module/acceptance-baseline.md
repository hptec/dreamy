# 验收基准：fabric-care-module

生成时间：2026-06-13T23:59:19Z

## FUNC — 功能验收

| 编号 | 场景标题 | 来源 |
|------|---------|------|
| FUNC-001 | 后台管理员编辑商品面料成分（Happy Path） | acceptance.yml #admin_edit_fabric_composition_happy |
| FUNC-002 | 后台管理员选择护理标签（Happy Path） | acceptance.yml #admin_select_care_instructions_happy |
| FUNC-003 | 消费端用户查看 PDP 面料和护理说明 | acceptance.yml #customer_view_fabric_care_pdp_happy |
| FUNC-004 | 系统管理员维护护理标签字典 | acceptance.yml #admin_maintain_care_instruction_dict_happy |

## EDGE — 边界/异常验收

| 编号 | 场景标题 | 来源 |
|------|---------|------|
| EDGE-001 | 百分比总和不等于 100% 时校验失败 | boundary-scenarios.yml #integrity |
| EDGE-002 | 护理标签 code 重复时唯一性校验失败 | boundary-scenarios.yml #integrity |
| EDGE-003 | 并发编辑面料成分时事务冲突处理 | boundary-scenarios.yml #concurrent |
| EDGE-004 | 非管理员用户无权限编辑面料成分 | boundary-scenarios.yml #auth |
| EDGE-005 | 消费端网络请求失败时显示加载失败提示 | boundary-scenarios.yml #network |

## 数据模型验收

**实体数量**：3 个新实体（ProductFabricComposition / CareInstructionDef / ProductCareInstruction）

**关键约束**：
1. ProductFabricComposition：每层百分比总和 = 100%（跨字段校验）
2. CareInstructionDef：code 唯一约束
3. ProductCareInstruction：(product_id, care_id) 复合唯一约束

## 业务流程验收

**核心流程**：4 个
1. admin_edit_fabric_composition：删除旧数据 + 批量插入 + 审计日志
2. admin_select_care_instructions：删除旧关联 + 批量插入 + 审计日志
3. customer_view_fabric_care_pdp：按 layer 分组 + 多语言翻译
4. admin_maintain_care_instruction_dict：code 唯一性校验 + 状态变更

**事务边界**：面料成分和护理标签的删除+批量插入操作必须在同一事务内

## UI 验收

**后台管理（Admin Portal）**：
- FabricCompositionEditor 组件：分层编辑器，实时百分比校验
- CareInstructionSelector 组件：分类多选，显示符号 + 说明
- ProductEdit.vue 集成：替换现有文本框

**消费端（Store Portal）**：
- ProductFabricCare 组件：分层展示 + 符号显示
- 集成到 product/[slug]/page.tsx（Description 之后）
- 移动端支持折叠
