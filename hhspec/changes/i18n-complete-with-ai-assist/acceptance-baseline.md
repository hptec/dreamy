# 验收基准：i18n-complete-with-ai-assist

生成时间：2026-06-16T08:20:00Z

## FUNC — 功能验收

| 编号 | 场景标题 | 页面 | 来源 |
|------|---------|------|------|
| FUNC-001 | 消费端切换语言后 UI 文案随之变化 | portal-store/* | acceptance.yml #FUNC-001 |
| FUNC-002 | messages.ts 字典覆盖所有硬编码英文区域 | portal-store/* | acceptance.yml #FUNC-002 |
| FUNC-003 | 缺失翻译回退 EN | portal-store/* | acceptance.yml #FUNC-003 |
| FUNC-004 | 系统管理新增外部网关配置模块 | portal-admin /system/gateways | acceptance.yml #FUNC-004 |
| FUNC-005 | 配置 AI 网关并自动发现模型 | portal-admin /system/gateways | acceptance.yml #FUNC-005 |
| FUNC-006 | 模型列表刷新策略可配置 | portal-admin /system/gateways | acceptance.yml #FUNC-006 |
| FUNC-007 | API Key 加密存储且掩码展示 | portal-admin /system/gateways | acceptance.yml #FUNC-007 |
| FUNC-008 | 商品编辑页 AI 翻译按钮 | portal-admin ProductEdit | acceptance.yml #FUNC-008 |
| FUNC-009 | AI 翻译结果回写表单 | portal-admin ProductEdit | acceptance.yml #FUNC-009 |
| FUNC-010 | 翻译 prompt 锁定婚纱礼服领域 | portal-admin 翻译弹窗 | acceptance.yml #FUNC-010 |
| FUNC-011 | 11 张翻译表均有 AI 辅助按钮 | portal-admin 各内容管理页 | acceptance.yml #FUNC-011 |
| FUNC-012 | AI 调用记录可查询 | portal-admin 翻译日志 | acceptance.yml #FUNC-012 |
| FUNC-013 | 翻译失败允许继续保存 | portal-admin 翻译弹窗 | acceptance.yml #FUNC-013 |
| FUNC-014 | 消费端 locale 走 URL 路径前缀（SEO 可索引） | portal-store /[locale]/* | acceptance.yml #FUNC-014 |
| FUNC-015 | hreflang 标签与多语言 sitemap | portal-store | acceptance.yml #FUNC-015 |
| FUNC-016 | locale 偏好不依赖 localStorage 作路由来源 | portal-store | acceptance.yml #FUNC-016 |
| FUNC-017 | designerNote 纳入数据级翻译 | portal-admin ProductEdit / portal-store PDP | acceptance.yml #FUNC-017 |
| FUNC-018 | 面料材质/层级名走 i18n 字典 | portal-store FabricCareSection | acceptance.yml #FUNC-018 |
| FUNC-019 | 用户语言偏好持久化 | portal-store / 后端 | acceptance.yml #FUNC-019 |
| FUNC-020 | 后端按 locale 发送三语邮件 | 后端邮件服务 | acceptance.yml #FUNC-020 |
| FUNC-021 | 网关配置测试连接 | portal-admin /system/gateways | acceptance.yml #FUNC-021 |
| FUNC-022 | 婚纱领域术语表注入翻译 | portal-admin /system/glossary / 后端 | acceptance.yml #FUNC-022 |

## EDGE — 边界/异常验收

| 编号 | 类别 | 场景标题 | 来源 |
|------|------|---------|------|
| EDGE-001 | 空值缺失 | AI 网关未配置时点击翻译按钮 | boundary-scenarios.yml |
| EDGE-002 | 空值缺失 | EN 主字段为空时请求 AI 翻译 | boundary-scenarios.yml |
| EDGE-003 | 空值缺失 | AI 返回空译文 | boundary-scenarios.yml |
| EDGE-004 | 边界值 | 超长文本翻译 | boundary-scenarios.yml |
| EDGE-005 | 边界值 | 自定义要求文本超长 | boundary-scenarios.yml |
| EDGE-006 | 类型格式 | API Key 格式非法 | boundary-scenarios.yml |
| EDGE-007 | 类型格式 | 网关 URL 协议非法 | boundary-scenarios.yml |
| EDGE-008 | 权限认证 | 无系统管理权限访问网关配置 | boundary-scenarios.yml |
| EDGE-009 | 权限认证 | 翻译接口未登录调用 | boundary-scenarios.yml |
| EDGE-010 | 权限认证 | API Key 不回传前端 | boundary-scenarios.yml |
| EDGE-011 | 并发 | 同一字段连续点击翻译 | boundary-scenarios.yml |
| EDGE-012 | 并发 | 网关配置编辑并发冲突 | boundary-scenarios.yml |
| EDGE-013 | 状态流转 | 网关禁用后调用翻译 | boundary-scenarios.yml |
| EDGE-014 | 状态流转 | 模型列表刷新失败回退 | boundary-scenarios.yml |
| EDGE-015 | 外部依赖 | AI 网关超时 | boundary-scenarios.yml |
| EDGE-016 | 外部依赖 | AI 网关 5xx 错误 | boundary-scenarios.yml |
| EDGE-017 | 外部依赖 | AI 网关限流 429 | boundary-scenarios.yml |
| EDGE-018 | 类型格式 | 无效 locale 前缀访问 | boundary-scenarios.yml |
| EDGE-019 | 类型格式 | 无 locale 前缀的旧链接兼容 | boundary-scenarios.yml |
| EDGE-020 | 空值缺失 | designerNote 译文缺失回退 | boundary-scenarios.yml |
| EDGE-021 | 空值缺失 | 邮件模板对应语言缺失回退 | boundary-scenarios.yml |
| EDGE-022 | 权限认证 | 术语表管理需系统管理权限 | boundary-scenarios.yml |
| EDGE-023 | 外部依赖 | 测试连接失败明确反馈 | boundary-scenarios.yml |
| EDGE-024 | 边界值 | 术语表条目过多时 prompt 注入截断 | boundary-scenarios.yml |

## SEC — 安全要求

| 要求 | 说明 |
|------|------|
| API Key 加密存储 | `api_key_encrypted` 字段 AES 加密落库，响应体返回掩码（sk-****1234） |
| 后端代理隔离 | 翻译请求经 `/api/admin/ai/translate` 后端代理，API Key 不下发浏览器 |
| 接口鉴权 | 翻译/配置接口要求有效 admin JWT，未登录返回 40100 |
| 权限隔离 | `/system/gateways` 需 `/system/gateways` 权限点，路由守卫拦截 |

## 备注

- 本变更为 greenfield 类型，无原型快照绑定，故无 UI 验收节（消费端 i18n 改造的视觉验收并入 FUNC-001~003 的功能验收）。
- 性能基线（PERF）：AI 翻译为异步辅助操作，无硬性响应时间指标（前提假设 3：并发 < 5，无队列限流需求）。
