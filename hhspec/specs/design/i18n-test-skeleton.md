# i18n 测试设计骨架

> 历史测试骨架：translation-log 与 Glossary 用例已退役，不得纳入当前测试计划。当前范围见
> `i18n-runtime-status.md`。

## 元信息

- 变更：i18n-complete-with-ai-assist
- 生成时间：2026-06-16T20:40:00Z
- 覆盖：8层后端测试 + 24 EDGE场景 + 业务流DAG（含测试连接 §1.7 / 术语详情 §3.3）
- 依赖：i18n-backend-api-detail.md, i18n-backend-data-detail.md, i18n-backend-error-mapping.yml

---

## 1. 测试分层策略

| 层级 | 范围 | Mock策略 | 工具 |
|------|------|---------|------|
| 单元测试 | Service业务逻辑 | Mock GatewayClient/Repository | JUnit5 + Mockito |
| 集成测试 | Repository+DB | 零Mock，TestContainers MySQL | JUnit5 + TestContainers |
| 契约测试 | 外部网关调用 | WireMock模拟OpenAI API | WireMock |
| 组件测试 | Controller层 | MockMvc | Spring Boot Test |
| 快照测试 | DTO序列化 | JSON快照对比 | - |
| 异步测试 | 定时任务 | 时钟控制 | Awaitility |
| API测试 | 端到端HTTP | 真实容器 | RestAssured |
| 韧性测试 | 超时/限流/降级 | WireMock故障注入 | WireMock + Resilience4j |

---

## 2. 单元测试 (TC-UNIT-I18N-NNNN)

### 2.1 GatewayConfigService

```
TC-UNIT-I18N-0001: 创建配置-名称唯一性校验
  given: 已存在同type同name配置
  when: createConfig
  then: 抛GatewayNameExistsException(409201)

TC-UNIT-I18N-0002: API Key加密
  given: 明文API Key "sk-proj-abc123xyz"
  when: createConfig
  then: api_key_encrypted为base64(IV+密文), 非明文

TC-UNIT-I18N-0003: API Key掩码
  given: 加密的API Key
  when: toDetailResponse
  then: 返回"sk-****3xyz"格式

TC-UNIT-I18N-0004: 乐观锁冲突
  given: updated_at不匹配
  when: updateConfig
  then: 抛ConcurrentModificationException(409201) [EDGE-012]

TC-UNIT-I18N-0005: 删除引用校验
  given: ai_translation_log引用count>0
  when: deleteConfig
  then: 抛ResourceInUseException(409202)

TC-UNIT-I18N-0006: 非AI网关同步模型
  given: gateway_type=2(LOGISTICS)
  when: syncModels
  then: 抛NonAiGatewayException(400201)
```

### 2.2 AiTranslationService

```
TC-UNIT-I18N-0010: 无启用网关
  given: 无enabled=true的AI网关
  when: translate
  then: 抛NoEnabledGatewayException(400301) [EDGE-001/013]

TC-UNIT-I18N-0011: 模型非法
  given: request.model不在gateway.model_list
  when: translate
  then: 抛InvalidModelException(400302)

TC-UNIT-I18N-0012: system prompt组装
  given: 固定前缀+术语表+自定义要求
  when: buildSystemPrompt
  then: prompt含三部分且顺序正确

TC-UNIT-I18N-0013: 术语表注入命中过滤
  given: source_text="A-line dress", 术语表含A-line/ball-gown
  when: buildSystemPrompt
  then: 仅注入A-line(命中), 不注入ball-gown [EDGE-024]

TC-UNIT-I18N-0014: 术语表注入上限50条
  given: 命中术语>50条
  when: buildSystemPrompt
  then: 按category优先级截断至50条(廓形>领型>面料>工艺) [EDGE-024]

TC-UNIT-I18N-0015: 空译文处理
  given: 网关返回content=""
  when: translate
  then: 记log status=empty_result, 抛GatewayCallFailedException(502301) [EDGE-003]
```

### 2.3 GlossaryService

```
TC-UNIT-I18N-0020: 术语唯一性(不区分大小写)
  given: 已存在"A-line"
  when: createTerm("a-line")
  then: 抛NameExistsException(409401)

TC-UNIT-I18N-0021: 术语详情查询命中 [ISS-002]
  given: 已存在 id=1 术语"A-line"
  when: getTerm(1)
  then: 返回 GlossaryTerm(term_en="A-line", 含三语/category/enabled)

TC-UNIT-I18N-0022: 术语详情不存在 [ISS-002]
  given: id=999 不存在
  when: getTerm(999)
  then: 抛ResourceNotFoundException(404401)
```

### 2.4 GatewayConnectionTest (测试连接, ISS-001)

```
TC-UNIT-I18N-0030: 非AI网关测试连接返回501
  given: gateway_type=2(LOGISTICS)
  when: testConnection
  then: 返回 501 (NOT_IMPLEMENTED), 不调用外部网关
```

---

## 3. 集成测试 (TC-INT-I18N-NNNN)

```
TC-INT-I18N-0001: external_gateway_config CRUD
  TestContainers MySQL, 验证保存/查询/更新/删除

TC-INT-I18N-0002: 唯一索引uk_type_name
  given: 插入重复(type,name)
  then: DB抛DuplicateKeyException

TC-INT-I18N-0003: RM-002查询启用AI网关
  given: 多个AI网关配置, 部分enabled
  when: findFirstByGatewayTypeAndEnabledTrueOrderByUpdatedAtDesc(1)
  then: 返回最新的enabled=true配置

TC-INT-I18N-0004: ai_translation_log REQUIRES_NEW事务
  given: 翻译失败主流程回滚
  then: log仍持久化(独立事务) [TX-004]

TC-INT-I18N-0005: 90天日志清理
  given: 插入91天前的日志
  when: deleteOlderThan
  then: 旧日志被删除, 新日志保留 [决策7]

TC-INT-I18N-0006: designer_note ALTER列
  given: product_translation表
  then: designer_note列存在且可写入

TC-INT-I18N-0007: pick回退EN
  given: product_translation.designer_note为空, product.designer_note有值
  when: assembleDetail(locale=es)
  then: designerNote=product主表EN值 [EDGE-020]
```

---

## 4. 契约测试 (TC-CONTRACT-I18N-NNNN)

```
TC-CONTRACT-I18N-0001: /v1/chat/completions请求格式
  WireMock验证: POST body含{model, messages:[system,user], max_tokens}

TC-CONTRACT-I18N-0002: /v1/models响应解析
  WireMock返回模型列表, 验证解析为model_list

TC-CONTRACT-I18N-0003: OpenAPI契约一致性
  验证Controller响应符合gateway-api.openapi.yml schema
```

---

## 5. API测试 (TC-API-I18N-NNNN)

```
TC-API-I18N-0001: POST /configs创建成功
  RestAssured: 200 + 掩码api_key

TC-API-I18N-0002: 未登录调用translate
  无JWT → 401/40100 [EDGE-009]

TC-API-I18N-0003: 无权限访问gateways
  普通账户 → 403/40300 [EDGE-008]

TC-API-I18N-0004: API Key不回传明文
  GET /configs/{id} → 响应体api_key为sk-****1234 [EDGE-010]

TC-API-I18N-0005: 术语表权限隔离
  无/system/glossary权限 → 403 [EDGE-022]

TC-API-I18N-0006: 字段校验422
  缺少必填字段 → 422201/422301/422401 [EDGE-006/007/002]

TC-API-I18N-0007: GET /terms/{id} 术语详情成功 [ISS-002]
  given: 已存在术语 id=1
  RestAssured: GET /api/admin/glossary/terms/1 → 200 + GlossaryTerm payload

TC-API-I18N-0008: GET /terms/{id} 404 [ISS-002]
  given: id=999 不存在
  RestAssured: GET /api/admin/glossary/terms/999 → 404 + code=404401

TC-API-I18N-0009: POST /configs/{id}/test 成功 [ISS-001]
  given: 有效AI网关, WireMock /v1/models 返回47模型
  RestAssured: POST /configs/{id}/test → 200 + {reachable:true, available_models_count:47, latency_ms}

TC-API-I18N-0010: POST /configs/{id}/test 非AI网关501 [ISS-001]
  given: gateway_type=2
  RestAssured: POST /configs/{id}/test → 501

TC-API-I18N-0011: POST /configs/{id}/test 配置不存在404 [ISS-001]
  given: id=999
  RestAssured: POST /configs/999/test → 404 + code=404201
```

---

## 6. 韧性测试 (TC-RESILIENCE-I18N-NNNN)

```
TC-RESILIENCE-I18N-0001: 网关超时
  WireMock延迟35s → 504301, log status=timeout [EDGE-015]

TC-RESILIENCE-I18N-0002: 网关5xx
  WireMock返回500 → 502301, log status=failed(2) [EDGE-016]

TC-RESILIENCE-I18N-0003: 网关限流429
  WireMock返回429 → 502301, log status=rate_limited [EDGE-017]

TC-RESILIENCE-I18N-0004: 网关不可达
  WireMock拒绝连接 → 502201 [EDGE-023]

TC-RESILIENCE-I18N-0005: API Key鉴权失败
  WireMock返回401 → 502202 [EDGE-023]

TC-RESILIENCE-I18N-0006: 模型同步连续失败降级
  连续3次失败 → strategy降级manual+告警 [决策5]

TC-RESILIENCE-I18N-0007: 翻译失败允许继续
  翻译502 → 前端可继续保存(不阻塞) [决策10/EDGE-003/015/016]

### 测试连接 reachable=false 三种 error_code 封装 (ISS-001, EDGE-023)

TC-RESILIENCE-I18N-0008: 测试连接-网关不可达
  given: WireMock拒绝连接(DNS/ConnectException)
  when: POST /configs/{id}/test
  then: 200 + {reachable:false, error_code:502201, error_message含"不可达", latency_ms} [EDGE-023]

TC-RESILIENCE-I18N-0009: 测试连接-API Key鉴权失败
  given: WireMock /v1/models 返回401
  when: POST /configs/{id}/test
  then: 200 + {reachable:false, error_code:502202, error_message含"鉴权", latency_ms} [EDGE-023]

TC-RESILIENCE-I18N-0010: 测试连接-超时
  given: WireMock延迟>10s
  when: POST /configs/{id}/test
  then: 200 + {reachable:false, error_code:504201, error_message含"超时", latency_ms} [EDGE-023]
```

---

## 7. 异步测试 (TC-ASYNC-I18N-NNNN)

```
TC-ASYNC-I18N-0001: 定时模型刷新
  given: strategy=scheduled, interval=5min
  when: @Scheduled触发
  then: 按间隔调用/v1/models更新model_list [决策5]

TC-ASYNC-I18N-0002: 定时日志清理
  given: 每日3:00触发
  then: 分批DELETE 90天前日志 [决策7]
```

---

## 8. 业务流DAG (flow_groups)

```yaml
flow_groups:
  FG-001-translation:
    name: AI翻译完整流程
    flow: FLOW-I01
    dag:
      - 读取启用网关(RM-002)
      - 确定模型
      - 注入术语表(RM-009)
      - 组装prompt
      - 调用网关
      - 记录日志(TX-004)
    test_coverage: [TC-UNIT-0010~0015, TC-INT-0003/0004, TC-RESILIENCE-0001~0003]

  FG-002-gateway-config:
    name: 网关配置+模型发现
    flow: FLOW-I02
    dag:
      - 唯一性校验
      - 加密API Key
      - 持久化
      - 自动模型发现(TX-001事务外)
    test_coverage: [TC-UNIT-0001~0003, TC-INT-0001/0002, TC-CONTRACT-0002]

  FG-003-locale-routing:
    name: 消费端locale路由
    flow: FLOW-I07
    dag:
      - middleware检测
      - 重定向(301/302)
    test_coverage: [UI测试见ui-test-spec.yml EDGE-018/019]

  FG-004-data-fallback:
    name: designerNote数据回退
    flow: FLOW-I08
    dag:
      - 查询translation
      - pick回退EN
    test_coverage: [TC-INT-0007]
```

---

## 9. 24 EDGE场景覆盖矩阵

| EDGE | 测试用例 | 层级 |
|------|---------|------|
| EDGE-001 | TC-UNIT-0010, TC-API-0006 | 单元+API |
| EDGE-002 | TC-API-0006 | API+前端 |
| EDGE-003 | TC-UNIT-0015 | 单元 |
| EDGE-004 | TC-UNIT(截断) | 单元 |
| EDGE-005 | 前端maxlength | UI |
| EDGE-006 | TC-API-0006 | API |
| EDGE-007 | TC-API-0006 | API |
| EDGE-008 | TC-API-0003 | API |
| EDGE-009 | TC-API-0002 | API |
| EDGE-010 | TC-API-0004 | API |
| EDGE-011 | UI(防重复) | UI |
| EDGE-012 | TC-UNIT-0004 | 单元 |
| EDGE-013 | TC-UNIT-0010 | 单元 |
| EDGE-014 | TC-INT(模型失败保存) | 集成 |
| EDGE-015 | TC-RESILIENCE-0001 | 韧性 |
| EDGE-016 | TC-RESILIENCE-0002 | 韧性 |
| EDGE-017 | TC-RESILIENCE-0003 | 韧性 |
| EDGE-018 | UI(middleware 302) | UI |
| EDGE-019 | UI(middleware 301) | UI |
| EDGE-020 | TC-INT-0007 | 集成 |
| EDGE-021 | TC-INT(邮件回退) | 集成 |
| EDGE-022 | TC-API-0005 | API |
| EDGE-023 | TC-RESILIENCE-0004/0005 | 韧性 |
| EDGE-024 | TC-UNIT-0013/0014 | 单元 |

**覆盖率**：24/24 EDGE场景全覆盖

---

## 10. 数据工厂

```java
GatewayConfigFactory.aiGateway()  // 默认AI网关配置
GatewayConfigFactory.disabledGateway()  // 禁用配置
GlossaryTermFactory.aLine()  // A-line术语
AiTranslationLogFactory.success()/failed()  // 日志样本
```

---

## 11. 优先级矩阵

| 优先级 | 测试类型 | 占比 |
|--------|---------|------|
| P0 | 韧性(外部网关)+API(权限/掩码) | 必须通过 |
| P0 | 单元(加密/术语注入/错误码) | 必须通过 |
| P1 | 集成(CRUD/事务/回退) | 必须通过 |
| P2 | 契约+异步 | 应通过 |

---

**设计完成标记**：✅ 测试骨架已完成
