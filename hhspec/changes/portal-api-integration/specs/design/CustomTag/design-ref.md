# design-ref: CustomTag

> 本实现单元域的 L2 设计宿主：`specs/design/catalog/`（七业务域聚合设计）。

> **处置说明**：CustomTag/CustomTagTranslation 已合并由 TagDimension/Tag/TagTranslation 承载（见 catalog-api-detail CustomTag 处置章节与 catalog-data-detail DDL 备注），不建孤表、不出端点。

## 关联任务
- **TASK-026**（modify_schema）：实现实体 CustomTag 的数据模型 → 设计章节见 `specs/design/catalog/task-allocation.yml` 中 TASK-026 条目

## 设计文档入口
- `specs/design/catalog/catalog-api-detail.md`
- `specs/design/catalog/catalog-data-detail.md`
- `specs/design/catalog/catalog-frontend-detail.md`
- `specs/design/catalog/catalog-test-detail.md`
- `specs/design/catalog/catalog-ui-test-spec.yml`
- `specs/design/catalog/task-allocation.yml`
