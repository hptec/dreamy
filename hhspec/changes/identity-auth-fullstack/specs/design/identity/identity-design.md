# identity L2 详细设计文档（统一）

> change: identity-auth-fullstack ｜ domain: identity ｜ change_type: full-stack ｜ bootstrap_mode: true
> 本文为汇总视图。各专家独立文件（L3 精确输入）：identity-api-detail.md / identity-data-detail.md / identity-error-detail.md / identity-test-detail.md / identity-frontend-detail.md / identity-ui-test-spec.yml。

## 1. 设计概述

### 1.1 范围
- 36 API 操作（store 14 + admin 22）｜ 13 实体 ｜ 25 错误码 ｜ 17 业务流程 ｜ 26 边界场景
- 两前端门户：portal-store(Next.js 15, 5173, EN/ES/FR) + portal-admin(Vue3+Pinia, 5174, 中文)
- 双 JWT 隔离（store 2h+refresh30d 可撤销 / admin 8h 无 refresh）｜ 三方 OIDC(Google/Apple) ｜ SMTP OTP
- JetCache 分级（会话/凭证仅 Redis 单级 TTL30s / 资料/配置两级）｜ 数据保留合规(GDPR/CCPA/PIPEDA)

### 1.2 复杂度度量
见 complexity-metrics.yml：评级 **high**，full-expert-parallel 模式。

### 1.3 依赖图
wave_1: [api, data, ui_test] → wave_2: [error] → wave_3: [frontend, test]（DFS 无环，见 task-allocation.yml）

### 1.4 共享契约声明
见 shared-contracts.yml：命名/类型映射/引用规则/错误信封/双JWT/缓存分级/频控/CORS/脱敏/分页。

## 2. API 详细设计
→ 完整见 **identity-api-detail.md**（36 操作四部分：V-NNN/STEP-NN/出参/错误映射）。关键事务端点：verifyOtp(FLOW-02 行锁)、oidcCallback(FLOW-03 归并单事务)、changePrimaryEmail、updateRole(全量重写)、forceLogout、deleteAccount。

## 3. 数据层详细设计
→ 完整见 **identity-data-detail.md**（RM/MAP/IDX/TX/CV）。复用已产出权威 DDL：identity-ddl.sql(13表) + identity-physical-schema.md + identity-retention-anonymization.md。无物理 FK（逻辑外键+应用层）；乐观锁于 user/otp_code/user_session。

## 4. 错误处理详细设计
→ 完整见 **identity-error-detail.md**（EX-01~30 异常 / 25 码接口映射含 25 MUST_TEST / RT/CB/DG 降级 / 脱敏 / 三语 i18n + 占位翻译填充计划）。

## 5. 测试设计
→ 完整见 **identity-test-detail.md**（UT/IT/CT/FCT/AT/RST/**NBT** 八层 + 数据工厂 + P0-P3）。网络边界 NBT-01~05 强制（CORS preflight/跨域/白名单拒绝/跨端隔离）。

## 5B. UI 测试设计
→ 完整见 **identity-ui-test-spec.yml**（11 原型页 Playwright 期望：视觉基线/交互穷举/字段断言/状态流转/WCAG-2.1-AA/性能预算）。

## 5C. 前端详细设计
→ 完整见 **identity-frontend-detail.md**（portal-store: PAGE-S/COMP-S/STORE-S/FORM-S；portal-admin: PAGE-A/COMP-A/STORE-A/GUARD/FORM-A，含 RBAC 路由守卫+菜单渲染）。

## 6. 跨领域一致性验证
→ 完整见 **conflict-report.yml**：18 维度全执行，16 COMPATIBLE + 2 GAP(CFL-17/18，P2 附最低补充要求，传递 L3) + 0 BLOCKING。
裁定原则：契约稳定优先、安全/合规不可降级、DDL 权威对齐。

## 7. 设计决策记录
- DR-01 双 JWT 独立密钥，禁止复用；跨端 token 误用 401（BE-DIM-6）
- DR-02 归并以 (provider,provider_uid) 唯一索引保证幂等；email_verified 一致才自动并，冲突即拒（R1）
- DR-03 会话有效性仅 Redis 单级 TTL30s 强一致全集群；写即失效（R5/BE-DIM-8）
- DR-04 枚举 VARCHAR+CHECK + Java enum 双保险（不用 MySQL ENUM）
- DR-05 操作日志只读不可删，注销不删审计（正当利益 EDGE-026）
- DR-06 角色重名用 40000 VALIDATION_ERROR 字段级，不新增码（CFL-17 裁定）
- DR-07 前端 Next 用 zustand、Vue 用 Pinia；边界 snake_case→camelCase 统一转换

## 8. 风险与待确认项
- RISK-01 (CFL-18) Redis 不可用会话校验降级查 DB，强制下线即时性在恢复前有窗口，需 L3 + 运维监控
- RISK-02 permission 22 项 key 清单由 portal-admin 路由补全（L3）
- RISK-03 超管全权限实现（显式写满 vs 应用层短路），L3 定夺
- RISK-04 ES/FR 占位翻译需专业复核（标 TRANSLATION_PENDING）
- RISK-05 CHAR(36) UUID 索引体积，千万级评估 BINARY(16)；operation_log 长保留分区演进
- RISK-06 Gradle/MyBatis-Plus/JWT 库/SMTP 实现 Bootstrap 推断，待 /pd:init 落地校验

## 9. 需求覆盖
→ requirements-traceability-matrix.yml：68 项(8 REQ+34 FUNC+26 EDGE) 覆盖率 **100%**，无未覆盖、无用户批准跳过。
