# i18n-complete-with-ai-assist L2 详细设计文档

> 统一设计文档（汇总视图）。各专家独立产出见同目录下 i18n-backend-*.md / i18n-*-frontend-detail.md / i18n-test-skeleton.md / i18n-ui-test-spec.yml。

## 1. 设计概述

### 1.1 范围

超大型全栈i18n变更，包含：
- **后端3新域**：gateway(网关配置)、ai_translation(翻译代理)、glossary(术语表)
- **后端3增量域**：catalog(designer_note)、identity(locale_pref)、trading(locale_snapshot)
- **前端portal-admin**：网关配置页 + AI翻译弹窗 + 术语表页 + 商品编辑增量
- **前端portal-store**：locale路由重构 + 50+组件i18n + SEO增强 + designerNote消费

### 1.2 复杂度度量

| 维度 | 值 |
|------|-----|
| 端点数 | 16 |
| 实体数 | 6 (3新+3改) |
| 错误码数 | 18 |
| 验收场景 | 22 FUNC |
| 边界场景 | 24 EDGE |
| 复杂度等级 | HIGH |

### 1.3 依赖图

```
portal-admin ──→ gateway/ai_translation/glossary API
                     │
ai_translation ──read──→ gateway(配置)
              ──read──→ glossary(术语注入)
                     │
catalog(商品编辑) ──call──→ ai_translation(翻译)
                     │
identity/trading ──data──→ email_service(locale选择)
                     │
portal-store ──→ catalog(designerNote) + i18n字典
              ──→ 外部AI网关(OpenAI-compatible)
```

### 1.4 共享契约声明

详见 shared-contracts.yml。关键约定：
- 命名：实体PascalCase，DTO camelCase，DB snake_case
- API Key：AES-256-GCM加密，掩码sk-****1234
- locale：['en','es','fr']，默认en
- 翻译超时30s，测试连接10s
- 术语注入上限50条

---

## 2. API详细设计

完整内容见 i18n-backend-api-detail.md。摘要：

### Gateway域(7端点)
- POST /configs：创建+自动模型发现(入参 api_key 明文)
- GET /configs：列表(掩码)
- GET /configs/{id}：详情
- PUT /configs/{id}：更新+乐观锁
- DELETE /configs/{id}：删除+引用校验
- POST /configs/{id}/sync-models：手动同步
- POST /configs/{id}/test：测试连接(GatewayTestResult，成功失败均返200)

### AI Translation域(2端点)
- POST /translate：后端代理翻译(读网关+注入术语+调用+记日志)
- GET /translation-logs：调用记录查询

### Glossary域(5端点)
- POST/GET列表/GET详情{id}/PUT/DELETE /terms：术语CRUD+详情

### 增量(2端点)
- PUT /catalog/products/{id}：designer_note字段
- PUT /api/consumer/auth/profile：locale_pref字段(StoreBearerAuth)

---

## 3. 数据层详细设计

完整内容见 i18n-backend-data-detail.md。摘要：

### 新增表(3)
- external_gateway_config：网关配置(uk_type_name唯一)
- ai_translation_log：调用记录(idx_biz/idx_created/idx_gateway)
- ai_translation_glossary：术语表(uk_term_en唯一)

### 修改表(3, ALTER)
- product_translation +designer_note
- user +locale_pref
- orders +locale_snapshot

### 关键事务
- TX-001：模型拉取在主事务外(避免外部调用占用DB连接)
- TX-004：日志写入REQUIRES_NEW(翻译失败也记录)
- TX-006：日志清理分批DELETE 5000条

---

## 4. 错误处理详细设计

完整内容见 i18n-backend-error-mapping.yml。摘要：

### 18个新错误码
- gateway域(域段2)：400201/404201/409201/409202/422201/422202/502201/502202/504201
- ai_translation域(域段3)：400301/400302/422301/502301/504301
- glossary域(域段4)：404401/409401/422401

### 外部网关降级矩阵
- 翻译失败：记log+返回502/504+允许继续保存(决策10)
- 模型同步连续失败3次：consecutive_failures计数→降级manual+enabled=0+告警(决策5, 见data-detail §8状态机)
- 邮件模板缺失：回退EN(决策13)

---

## 5. 测试设计

完整内容见 i18n-test-skeleton.md。摘要：

### 8层测试
单元/集成/契约/组件/快照/异步/API/韧性

### 业务流DAG(flow_groups)
- FG-001-translation：AI翻译完整流程
- FG-002-gateway-config：网关配置+模型发现
- FG-003-locale-routing：消费端路由
- FG-004-data-fallback：designerNote回退

### 24 EDGE场景覆盖
100%覆盖，见test-skeleton第9节矩阵

---

## 5B. UI测试设计

完整内容见 i18n-ui-test-spec.yml。摘要：

### 5个测试屏幕组
- admin网关配置(UI-GW-001~006)
- admin翻译弹窗(UI-AI-001~007)
- admin术语表(UI-GL-001~003)
- store locale路由(UI-ST-001~004)
- store i18n验证(UI-I18N-001~006)

---

## 5C. 前端详细设计

完整内容见 i18n-admin-frontend-detail.md + i18n-store-frontend-detail.md。摘要：

### portal-admin
- PAGE-001/002：网关配置/术语表页
- COMP-006：AI翻译弹窗(11处嵌入)
- STORE-001/002/003：Pinia状态管理

### portal-store
- 路由重构：app/→app/[locale]/
- middleware：locale检测(URL>cookie>Accept-Language>EN)
- messages.ts：30+命名空间，50+组件接入
- SEO：hreflang + 多语言sitemap
- pick回退：designerNote缺失回退EN

---

## 6. 跨领域一致性验证

完整内容见 conflict-report.yml。

### 冲突检测结果
- 18维度检测：17 PASS + 1 COMPATIBLE + 0 BLOCKING
- CFL-18(前端错误提示)：裁定按API优先，前端透传后端message

### 裁定记录
| 冲突 | 类型 | 裁定 |
|------|------|------|
| CFL-18 | COMPATIBLE | 前端admin透传后端message(中文)，不硬编码错误文案 |

**结论**：PASS，可进入L3。

---

## 7. 设计决策记录

继承L0的14个决策，L2层补充实现决策：
- API Key：AES-256-GCM(IV+密文base64存储)
- 乐观锁：updated_at比对
- 术语注入：命中过滤+50条上限+category优先级截断
- 模型拉取：主事务提交后异步执行
- locale路由：EN根路径，ES/FR前缀，301(旧链接)/302(无效locale)

---

## 8. 风险与待确认项

1. **路由重构影响面大**：portal-store 50+组件Link改造，需全量回归(store-frontend第9节)
2. **SSR/CSR locale一致性**：middleware cookie与客户端useLocale同步
3. **i18n方案确认**：需读取现有messages.ts确认next-intl或自研
4. **API Key密钥管理**：AES密钥的存储和轮转策略需运维确认(本设计假设环境变量注入)
5. **现有localStorage迁移**：决策11移除localStorage作为路由依据的兼容处理
6. **CFL-18轻微项**：前端错误文案透传方案需Frontend确认采纳

---

**L2设计完成**：✅ 统一设计文档已生成，8章节齐全
