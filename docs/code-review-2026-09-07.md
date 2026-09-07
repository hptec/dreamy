# Dreamy 代码深度回顾报告（待处理项）

> 审查日期：2026-09-07 ｜ 文档维护：已处理条目随修复移除，仅保留待处理项
> 审查基线：commit `659d108`
> 首轮已处理（2026-09-07，详见当日 git 提交）：H3 i18n 硬编码补全（~15 文件 + 三语词典 + /terms /privacy 新页）、H4/H5 wedding-plans/wishlist 三态、M3 AttributeSetService N+1 批查、M5 advice 登记、M6-M11 admin 双击/静默失败闭环、M12/M13 store 静默失败提示、M14 死链（社交图标移除 + Terms/Privacy 独立页）、M16/M17 契约死链、M18/M19 localhost 烤产物防护（seo fail-fast / server-fetch 生产告警 / ShowroomEventPublisher smtp+localhost fail-fast）、L10/L11 timer 清理与密码报错、L12-L15 死文档归档（docs/archive/）与构建垃圾清理

---

## 🔴 高风险（待处理）

### H1. RetentionScheduler 事务自调用失效，PII 匿名化可能半途而废且不可重跑

- 位置：`backend/src/main/java/.../domain/audit/service/RetentionScheduler.java:63-69,114,124,132`
- 问题：`runDailyRetention()` 经 `this.` 同类调用 `anonymizeExpiredDeletedUsers()` / `anonymizeUser()`，Spring 代理模式下两处 `@Transactional` 均不生效。
- 后果：匿名化需多表写（user PII 清空 + status 翻转 + user_identity 级联），无事务保护下中途失败会留下"已 anonymized 但凭证 PII 未清"状态；且 status 已变致重跑不再捞起，**PII 永久残留**，违反 RM-005/006/018 保留策略。
- 修复方向：自调用改经自注入代理 / 拆到独立 Service / TransactionTemplate 编程式事务。

### H2. Admin 登录零防暴力破解

- 位置：`backend/src/main/java/.../domain/admin/service/AdminService.java:56-89`、`security/AdminJwtFilter.java:92-94`
- 问题：仅密码比对，无失败计数/锁定/IP 限流；store 端 OTP 有完整 OtpRateLimiter 频控，admin 端零防护。
- 修复方向：复用 OtpRateLimiter 范式对 admin login 做失败计数 + 锁定（如 5 次失败锁 15 分钟）。

### H6. EmailMarketing.vue 整页是未闭环 mock 页

- 位置：`frontend/portal-admin/src/views/EmailMarketing.vue:5,16,19-22,40`
- 问题：import mock 数据、统计写死、按钮无 handler；路由 `/marketing/email` 可进入。
- 修复方向：产品决策——真做邮件营销模块，或先下架入口。

---

## 🟡 中风险（待处理）

### M1. SchemaBuilder 209 条 nullability 漂移

- 位置：`logs/identity.log`（2026-09-07 起）
- 问题：实体注解声明 NULL 而库列 NOT NULL（phone/state/name/last_login_at/options 等），`huihao.mysql.auto=update` 下 DDL 管理失控，重建库时列语义静默改变。
- 修复方向：批量对齐实体注解与库结构（一次性脚本核对 209 条），或引入显式 DDL 管理。

### M2. SalesWindowRefreshJob 无锁 + 单大事务 N+1

- 位置：`backend/src/main/java/.../mq/SalesWindowRefreshJob.java:44-66`
- 问题：全项目唯一无 Redisson tryLock 的 scheduler（多实例重复执行）；候选集无上限，单事务内逐商品 sumPaidQty 逐条 SQL + 回写。
- 同型小规模：`CatalogSalesEventConsumer.java:71-73`。
- 修复方向：补 tryLock；批量化 sumPaidQty（GROUP BY product_id 单条）。

### M4. Q&A P2 未闭环 + 无频控

- 位置：`backend/src/main/java/.../domain/question`
- 问题：无删除提问端点、无草稿自动保存（P2 产品决策项）；且 `StoreQuestionService.createQuestion`（:88-121）无每用户频控/条数上限，垃圾提问可无限堆入待答列表。

### M15. 联系页虚构信息

- 位置：`frontend/portal-store/app/[locale]/contact/page.tsx:76-79`
- 问题：假电话 `+1 (800) 555-DREAM`；"Live Chat" 无实现。需真实联系信息后补。

---

## 🟢 低风险（待处理）

### 后端

| # | 问题 | 位置 |
|---|------|------|
| L1 | demo seed 开启时超管密码硬编码 `Admin@123456`（恰 12 字符过长度校验）；生产误开 DEMO_SEED_ENABLED 即产生公开固定凭据 | `config/DataInitializer.java:217` |
| L2 | blog view 端点无鉴权无限流，可脚本刷阅读量（注释自认"信任调用"） | `controller/StoreContentController.java:114-118` |
| L3 | 优惠券 redeem 仅 CAS 总量，无每用户限领 | `domain/coupon/service/CouponDomainServiceImpl.java:88-102` |
| L4 | 真实 Google client-id 作缺省值入库（配置污染） | `application.yml:87` |

### Store 前端

| # | 问题 | 位置 |
|---|------|------|
| L5 | URL.createObjectURL 每次渲染重建且从不 revoke，图片多时内存泄漏 | `components/product/write-review-modal.tsx:159` |
| L6 | 死代码：data/content.ts、data/account.ts 无引用（mock 遗留） | `frontend/portal-store/data/` |
| L7 | cart 页 "Save for later" 中 removeLine 失败无提示 | `app/[locale]/cart/page.tsx` |
| L8 | 既有类型错误：tests/unsubscribe-confirm.spec.tsx:118,128 TS2554（ApiError 缺第 3 参 httpStatus） | `frontend/portal-store/tests/` |
| L9 | any 滥用：site-header.tsx:169、site-builder-server.ts:194 | — |

---

## 建议处置顺序（待处理项）

| 优先级 | 内容 | 理由 |
|---|---|---|
| P0 | H1 事务失效、H2 admin 防爆破 | 数据完整性 / 安全 |
| P1 | M4 Q&A 频控（部分产品决策）、M2 锁+N+1 | 垃圾数据防护 / 性能 |
| P2 | M1 Schema 漂移、L1-L4 后端低风险 | 质量债 / 上线前配置卫生 |
| P3 | H6 EmailMarketing、M15 联系页、L5-L9 | 产品决策 / 卫生 |

---

## 审查方法说明

四个方向并行深查：后端（未闭环/状态机并发/事务/安全/性能/异常处理）、store 前端（SSR 水合/API 三态/表单/类型安全）、admin 前端（错误处理/状态管理/表单/权限）、横切面（契约一致性/死代码/配置漂移）。全部问题均经代码位置核实，无猜测项。
