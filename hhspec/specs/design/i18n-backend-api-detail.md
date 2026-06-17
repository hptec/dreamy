# i18n Backend API 详细设计（精简版）

## 元信息

- 变更：i18n-complete-with-ai-assist
- 生成时间：2026-06-16T20:15:00Z
- 覆盖：16端点（gateway 7 + ai_translation 2 + glossary 5 + 增量 2）
- 设计原则：伪代码级业务逻辑，不涉及框架语法

---

## 1. Gateway域 - 7个端点

### 1.1 POST /api/admin/gateway/configs - 创建网关配置

**入参验证**：gateway_type(必填,enum[1,2,3]) | name(必填,≤64) | base_url(必填,URL格式,≤255) | api_key(必填,明文,1~512) | protocol(必填,enum[1]) | enabled(必填,boolean) | default_model(可选,≤128) | model_refresh_strategy(可选,enum[1,2],默认1) | model_refresh_interval_min(strategy=2时必填,5~1440)

> **字段命名对齐（ISS-008）**：请求体字段为契约定义的 `api_key`（明文，1~512，对应 `GatewayConfigUpsert` schema），**非**数据库列名 `api_key_encrypted`。明文输入经 `encryptApiKey()`（MAP-001）加密后落 `api_key_encrypted` 列。更新场景若传掩码格式（sk-****xxxx）则保持原密文。

**业务逻辑**：
1. 唯一性校验：同gateway_type下name不重复，否则409201
2. API Key处理：前端提交明文→AES-256-GCM加密(IV+密文)，提交掩码→拒绝(创建场景不允许)
3. 持久化：ExternalGatewayConfig全字段保存
4. 自动模型发现(仅gateway_type=1)：解密Key→调用{base_url}/v1/models(超时10s)→更新model_list+models_synced_at，失败不影响配置保存
5. 返回：掩码API Key(sk-****后4位)

**错误码**：409201(名称冲突) | 422201(字段校验) | 502201(模型拉取失败但配置已保存，返回200带空model_list)

---

### 1.2 GET /api/admin/gateway/configs - 列表查询

**入参验证**：gateway_type(可选,enum) | page(≥0,默认0) | page_size(1~100,默认20)

**业务逻辑**：
1. 构造Spec查询：gateway_type过滤(可选)
2. 分页查询：按updated_at降序
3. 掩码处理：所有API Key掩码展示

**出参**：Paginated<GatewayConfigListItem>(含total_elements/page_number等标准分页字段)

---

### 1.3 GET /api/admin/gateway/configs/{id} - 详情查询

**入参验证**：id(path参数,Long)

**业务逻辑**：
1. 主键查询
2. 不存在→404201
3. 掩码API Key

**错误码**：404201(配置不存在)

---

### 1.4 PUT /api/admin/gateway/configs/{id} - 更新配置

**入参验证**：同POST（请求字段 `api_key` 明文 1~512，**非** api_key_encrypted），额外校验updated_at(乐观锁)

**业务逻辑**：
1. 主键查询，不存在→404201
2. 乐观锁校验：前端提交updated_at与DB比对，不一致→409201(并发冲突)
3. API Key处理：前端提交掩码→保持原密文，提交明文→重新加密
4. 更新字段(name/base_url/default_model/enabled等)
5. 若base_url或api_key变更且gateway_type=1→重新拉取模型列表
6. 返回掩码结果

**错误码**：404201(不存在) | 409201(乐观锁冲突或名称冲突) | 422201(字段校验)

---

### 1.5 DELETE /api/admin/gateway/configs/{id} - 删除配置

**入参验证**：id(path参数,Long)

**业务逻辑**：
1. 主键查询，不存在→404201
2. 引用校验：COUNT(ai_translation_log WHERE gateway_config_id=id) > 0 → 409202(配置已被引用，不可删除)
3. 物理删除

**错误码**：404201(不存在) | 409202(已被引用)

---

### 1.6 POST /api/admin/gateway/configs/{id}/sync-models - 手动同步模型

**入参验证**：id(path参数,Long)

**业务逻辑**：
1. 主键查询，不存在→404201
2. 类型校验：gateway_type != 1 → 400201(仅AI网关支持模型同步)
3. 解密Key→调用/v1/models(超时10s)→更新model_list+models_synced_at
4. 失败→502201(网关不可达/鉴权失败) | 504201(超时)

**错误码**：404201 | 400201(非AI网关) | 502201(调用失败) | 504201(超时)

---

### 1.7 POST /api/admin/gateway/configs/{id}/test - 测试网关连接

> 对照 `gateway-api.openapi.yml` lines 244-281（operationId: testGatewayConnection）+ GatewayTestResult schema(lines 497-520)。FUNC-021 / EDGE-023 / 决策14。

**入参验证**：id(path参数,Long)

**业务逻辑**：
1. 主键查询，不存在→404201
2. 类型校验：gateway_type != 1(AI) → 返回 **501**（该网关类型暂不支持测试，其他类型暂无测试逻辑）
3. 解密 api_key_encrypted → 调用 {base_url}/v1/models，**超时 10s**（与 sync-models 一致，区别于翻译 30s）
4. 封装 GatewayTestResult：
   - 成功：reachable=true, available_models_count=解析的模型数, latency_ms=请求耗时
   - 不可达(DNS/连接拒绝)：reachable=false, error_code=502201, error_message="网关不可达", latency_ms
   - 鉴权失败(HTTP 401/403)：reachable=false, error_code=502202, error_message="API Key鉴权失败（401 Unauthorized）", latency_ms
   - 超时(>10s)：reachable=false, error_code=504201, error_message="连接超时", latency_ms
5. **测试结果不落库、不修改已保存配置**（model_list/models_synced_at 均不更新）

**出参**：GatewayTestResult{reachable / available_models_count? / error_code? / error_message? / latency_ms} — **成功失败均返回 HTTP 200**，通过 reachable 字段区分。502201/502202/504201 在 result.error_code 内返回，不作为 HTTP 状态码。

**HTTP 状态约定**：
- 200：测试完成（reachable=true 或 false，业务失败码在 result 内）
- 404：配置不存在（404201，唯一作为 HTTP 状态返回的错误）
- 501：非 AI 网关类型暂不支持测试

**与 sync-models(§1.6) 的差异**：
| 维度 | 1.6 sync-models | 1.7 test |
|------|------|------|
| 非AI网关 | 400201（HTTP 400） | 501（HTTP 501） |
| 失败返回 | 抛异常 502201/504201（HTTP 502/504） | 200 + result.error_code 封装 |
| 副作用 | 更新 model_list + models_synced_at | 无副作用（不落库） |
| 超时 | 10s | 10s |

**错误码**：404201(配置不存在,HTTP 404) | 501(非AI网关) | result.error_code∈{502201不可达 / 502202鉴权失败 / 504201超时}（在 200 响应体内返回）

---

## 2. AI Translation域 - 2个端点

### 2.1 POST /api/admin/ai/translate - 翻译请求(后端代理)

**入参验证**：source_lang(必填,≤8) | target_lang(必填,≤8) | source_text(必填,≤10000) | custom_requirement(可选,≤500) | model(可选,≤128) | biz_type(必填,≤32) | biz_ref(必填,≤64)

**业务逻辑**：
1. 读取启用网关：SELECT * FROM external_gateway_config WHERE gateway_type=1 AND enabled=true ORDER BY updated_at DESC LIMIT 1，无结果→400301
2. 确定模型：优先用request.model(需在gateway.model_list中，否则400302)，否则用gateway.default_model
3. 读取术语表：SELECT * FROM ai_translation_glossary WHERE enabled=true，仅注入source_text中实际命中的术语(term_en出现在source_text中)，上限50条，超出按category优先级截断(廓形>领型>面料>工艺>其他)
4. 组装system prompt：固定前缀("You are a professional translator for a bridal e-commerce platform...") + 术语注入("Use these standard terms: A-line → línea A (ES)...") + custom_requirement
5. 调用外部网关：解密api_key_encrypted → POST {base_url}/v1/chat/completions { model, messages:[{role:system,content:prompt},{role:user,content:source_text}], max_tokens:2000 }，超时30s
6. 记录日志：INSERT INTO ai_translation_log (gateway_config_id, model, source_lang, target_lang, source_text, translated_text, custom_requirement, biz_type, biz_ref, status, latency_ms, token_usage, operator_id, created_at) VALUES (...)
7. 返回译文或错误

**错误码**：400301(无启用网关) | 400302(模型无效) | 422301(字段校验) | 502301(网关4xx/5xx/429) | 504301(超时)

**关键决策响应**：
- 决策2：后端代理，API Key不暴露给浏览器
- 决策6：system prompt锁定+用户自定义要求
- 决策10：翻译失败返回502/504，前端允许继续保存
- 决策14：术语表注入

---

### 2.2 GET /api/admin/ai/translation-logs - 调用记录查询

**入参验证**：biz_type(可选) | biz_ref(可选) | status(可选,enum[1,2]) | page | page_size

**业务逻辑**：
1. 构造Spec查询：biz_type/biz_ref/status过滤
2. 分页查询：按created_at降序
3. 掩码source_text/translated_text(超长截断展示前200字符)

**出参**：Paginated<AiTranslationLogItem>

---

## 3. Glossary域 - 5个端点

### 3.1 POST /api/admin/glossary/terms - 新增术语

**入参验证**：term_en(必填,≤128) | term_es(可选,≤128) | term_fr(可选,≤128) | category(可选,≤32) | enabled(必填,boolean)

**业务逻辑**：
1. 唯一性校验：SELECT * WHERE LOWER(term_en)=LOWER(request.term_en)，存在→409401
2. 持久化：AiTranslationGlossary全字段保存

**错误码**：409401(术语已存在) | 422401(字段校验)

---

### 3.2 GET /api/admin/glossary/terms - 术语列表

**入参验证**：category(可选) | enabled(可选,boolean) | page | page_size

**业务逻辑**：分页查询，按updated_at降序

---

### 3.3 GET /api/admin/glossary/terms/{id} - 术语详情

> 对照 `glossary-api.openapi.yml` lines 136-167（operationId: getGlossaryTerm）。

**入参验证**：id(path参数,Long)

**业务逻辑**：
1. 主键查询
2. 不存在→404401
3. MapStruct(MAP-007) → GlossaryTerm

**出参**：GlossaryTerm（含 id/term_en/term_es/term_fr/category/enabled/created_at/updated_at）

**错误码**：404401(术语不存在)

---

### 3.4 PUT /api/admin/glossary/terms/{id} - 更新术语

**入参验证**：同POST + updated_at(乐观锁)

**业务逻辑**：乐观锁 + term_en唯一性(排除自身) + 更新

**错误码**：404401 | 409401 | 422401

---

### 3.5 DELETE /api/admin/glossary/terms/{id} - 删除术语

**业务逻辑**：物理删除(无引用校验)

---

## 4. Catalog增量 - 1个端点

### 4.1 PUT /api/admin/catalog/products/{id} - 商品更新(增量designer_note字段)

**增量字段验证**：designer_note(可选,text,不限长度)

**业务逻辑**：
1. 更新product_translation表(locale=es/fr)的designer_note列
2. 若designer_note为空，消费端assembleDetail时用pick()回退读product.designer_note(EN主表)

**决策响应**：决策12(designerNote纳入翻译+pick回退)

---

## 5. Identity增量 - 1个端点

### 5.1 PUT /api/consumer/auth/profile - 用户Profile更新(增量locale_pref字段)

> 对照 `identity-api-incremental.md` lines 63-94（operationId: updateProfile）。**消费端路径**（非 admin），鉴权 **StoreBearerAuth**（非 Admin JWT）。FUNC-019 / 决策13。

**鉴权**：StoreBearerAuth（消费端登录态 JWT）

**入参验证**：ProfileUpdateRequest{ display_name(可选,≤64) | locale_pref(可选,enum['en','es','fr']) }

**业务逻辑**：
1. 从 StoreBearerAuth 解析当前登录用户 id（401→未登录）
2. 更新 user 表的 display_name / locale_pref 列（仅更新请求中提供的字段）
3. 返回更新后的 User（含 locale_pref）

**出参**：User（含 id/email/email_verified/display_name/locale_pref/status/created_at/updated_at）

**业务说明**：
- 前端 LanguageSwitcher 切换语言时，若用户已登录调用此接口持久化 locale_pref；匿名用户仅写 localStorage + cookie，不调用此接口
- locale_pref 用于邮件模板选择优先级：user.locale_pref > orders.locale_snapshot > 默认'en'

**错误码**：401(未登录,复用identity 40100) | 422(字段校验,复用identity 422校验)

**决策响应**：决策13(用户语言偏好持久化)

---

## 6. 共性设计模式

### 6.1 API Key加密/解密/掩码模式

**密钥管理方案（ISS-006）**：

| 维度 | 方案 |
|------|------|
| 密钥来源 | 环境变量 `DREAMY_GATEWAY_AES_KEY`（Base64 编码的 256-bit / 32 字节 key），Spring `@Value("${dreamy.gateway.aes-key}")` 注入 |
| 启动校验 | 应用启动时（`@PostConstruct` 或 `InitializingBean`）校验：非空 + Base64 解码后长度严格 == 32 字节；不合法则 **fail-fast 启动失败**（抛 IllegalStateException，禁止默认硬编码 key 兜底） |
| 密文格式 | `keyVersion || ':' || base64(IV + cipher)`，首版密钥版本前缀为 `v1:`（如 `v1:AAAA....`），为后续平滑轮转预留 |
| 轮转策略 | 首版单密钥。轮转时双密钥并存：解密按密文 `keyVersion` 前缀选择对应 key（旧密文用旧 key），新加密统一用新 key 写 `v2:` 前缀，存量密文随更新操作渐进重加密 |
| 解密失败 | 密文损坏或 key 不匹配 → 422202(API_KEY_DECRYPTION_FAILED)，记 ERROR 日志告警 |

> **待运维确认（info 级，不阻塞 L3）**：`DREAMY_GATEWAY_AES_KEY` 的运维注入方式（env / Vault / Spring Cloud Config）与轮转操作流程需运维确认（见 UNC-L2C-001）。L3 实现用环境变量占位 `DREAMY_GATEWAY_AES_KEY`，不阻塞编码。

```java
// 密钥加载（启动时，fail-fast）
@Value("${dreamy.gateway.aes-key:}")
String aesKeyBase64;
byte[] secretKey;  // 当前激活密钥
String keyVersion = "v1";

@PostConstruct
void initKey():
  if (isBlank(aesKeyBase64)) throw IllegalStateException("DREAMY_GATEWAY_AES_KEY 未配置，启动失败")
  secretKey = base64Decode(aesKeyBase64)
  if (secretKey.length != 32) throw IllegalStateException("DREAMY_GATEWAY_AES_KEY 长度非法(需256-bit/32字节)，启动失败")

// 加密（创建/更新时）
encryptApiKey(plainText):
  iv = random(12)  // AES-GCM 推荐 96-bit IV
  cipher = AES-256-GCM(plainText, secretKey, iv)  // 含 128-bit auth tag
  return keyVersion + ":" + base64(iv + cipher)

// 解密（调用时）—— 按密文版本前缀选 key（支持轮转双密钥并存）
decryptApiKey(encrypted):
  [version, payload] = encrypted.split(":", 2)
  key = resolveKeyByVersion(version)  // 轮转期：v1→旧key, v2→新key
  bytes = base64Decode(payload)
  iv = bytes[0:12]
  cipher = bytes[12:]
  return AES-256-GCM-decrypt(cipher, key, iv)  // 解密失败→422202

// 掩码（响应时）
maskApiKey(encrypted):
  plain = decryptApiKey(encrypted)
  if (plain.length <= 8) return "****"
  return plain.substring(0, 3) + "****" + plain.substring(plain.length - 4)
```

### 6.2 乐观锁并发控制模式

```java
update(id, request):
  entity = repository.findById(id).orElseThrow(404xxx)
  if (entity.getUpdatedAt() != request.updatedAt) {
    throw ConcurrentModificationException(409xxx, "数据已被修改，请刷新后重试")
  }
  // 更新字段...
  entity.setUpdatedAt(now())
  repository.save(entity)
```

### 6.3 外部网关调用韧性模式

```java
callGateway(url, apiKey, payload):
  request = RestTemplate.postForEntity(url, payload, timeout=30s)
  try {
    response = request.execute()
    if (response.status >= 400 && response.status < 500) {
      throw GatewayClientException(502301, "网关客户端错误: " + response.body)
    }
    if (response.status >= 500) {
      throw GatewayServerException(502301, "网关服务端错误: " + response.body)
    }
    return response.body
  } catch (SocketTimeoutException e) {
    throw GatewayTimeoutException(504301, "网关调用超时")
  } catch (ConnectException e) {
    throw GatewayUnavailableException(502201, "网关不可达")
  }
```

### 6.4 分页响应统一格式

```java
Paginated<T> {
  data: List<T>
  total_elements: Long
  page_number: Integer
  page_size: Integer
  number_of_elements: Integer
  total_pages: Integer
}
```

---

## 7. 错误码汇总表

| 错误码 | 标识 | HTTP | 域 | 触发场景 |
|--------|------|------|----|---------
| 400201 | NON_AI_GATEWAY_NO_SYNC | 400 | gateway | 非AI网关调用模型同步 |
| 400301 | NO_ENABLED_GATEWAY | 400 | ai_translation | 无启用AI网关 |
| 400302 | INVALID_MODEL | 400 | ai_translation | 模型不在可用列表 |
| 404201 | GATEWAY_CONFIG_NOT_FOUND | 404 | gateway | 网关配置不存在 |
| 404401 | TERM_NOT_FOUND | 404 | glossary | 术语不存在 |
| 409201 | GATEWAY_NAME_EXISTS | 409 | gateway | 配置名称冲突 |
| 409202 | GATEWAY_IN_USE | 409 | gateway | 配置被日志引用不可删 |
| 409401 | TERM_EN_EXISTS | 409 | glossary | 术语已存在 |
| 422201 | FIELD_VALIDATION_FAILED | 422 | gateway | 字段校验失败 |
| 422301 | FIELD_VALIDATION_FAILED | 422 | ai_translation | 字段校验失败 |
| 422401 | FIELD_VALIDATION_FAILED | 422 | glossary | 字段校验失败 |
| 502201 | GATEWAY_UNAVAILABLE | 502 | gateway | 测试连接/同步失败(测试连接时在200 result内返回) |
| 502202 | GATEWAY_AUTH_FAILED | 502 | gateway | API Key鉴权失败(测试连接时在200 result内返回) |
| 502301 | GATEWAY_CALL_FAILED | 502 | ai_translation | 翻译调用失败 |
| 504201 | GATEWAY_TIMEOUT | 504 | gateway | 测试连接/同步超时(测试连接时在200 result内返回) |
| 504301 | GATEWAY_TIMEOUT | 504 | ai_translation | 翻译调用超时 |
| 501 | NOT_IMPLEMENTED | 501 | gateway | 非AI网关类型测试连接(§1.7) |

---

## 8. 需求追溯

本API详设覆盖以下需求：

- FUNC-004~007: gateway域7个端点（含§1.7测试连接 FUNC-021）
- FUNC-008~013: ai_translation域2个端点
- FUNC-021: §1.7 POST /configs/{id}/test 网关测试连接
- FUNC-022: glossary域5个端点（含§3.3术语详情）
- FUNC-017~018: catalog增量designer_note
- FUNC-019: identity增量locale_pref（§5.1 PUT /api/consumer/auth/profile）

覆盖率：后端 API 覆盖 FUNC-004~013/017/019/021/022（其余为纯前端UI需求，由Frontend Designer覆盖）

---

**设计完成标记**：✅ Backend API详设已完成，产出i18n-backend-api-detail.md
