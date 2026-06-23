# REQ-site-decoration-fullstack：站点装修全栈接入

> 来源：alignment 类型变更，验收基准来自 acceptance.yml（非 Gherkin Scenario）
> domain_code: site_builder
> change: site-decoration-fullstack

## 需求概述

实现并连接管理后台"站点装修"的所有功能，包括管理端前后端代码和消费端相关前后端代码。

## 功能需求

### FUNC-001 首页区块装修（HomeBuilder）
- 管理员可可视化编排首页区块（拖拽排序 + 显示开关 + 属性编辑）
- 保存后立即生效（保存即发布，KD-4）
- 消费端首页动态渲染区块配置

### FUNC-002 导航与页脚配置（NavigationConfig）
- 管理员可配置主导航 + Mega Menu + 页脚四栏 + 顶部公告条
- 保存后立即生效
- 消费端 layout.tsx 读取导航配置渲染 header/footer

### FUNC-003 公告条独立域（Announcement）
- 公告条独立于导航配置（KD-7）
- 管理员可增删改公告内容
- 消费端顶部公告条动态渲染

### FUNC-004 Banner 管理扩展
- 已有 AdminBannerController（E-MKT-21~25）
- 本期扩展：Banner 实体新增 cta_text_secondary + cta_link_secondary（KD-14）
- BannerPosition.TOPBAR 废弃公告语义（KD-17）

### FUNC-005 发布中心保持现状
- Publish.vue 不改造（KD-1）
- HomeBuilder/NavigationConfig 保存后通过 in-process 缓存失效链触发生效

### FUNC-006 消费端首页动态渲染
- GET /api/store/content/home/sections 返回区块配置
- 按 sort_order 升序渲染 enabled=true 的区块
- Hero 区块从 Banner position=HERO 派生（KD-2）

### FUNC-007 消费端导航动态渲染
- GET /api/store/content/navigation 返回导航配置
- layout.tsx 渲染主导航 + Mega Menu + 页脚 + 公告条

### FUNC-008 Newsletter 订阅
- 扩展基线 POST /api/store/newsletter（KD-13）
- 新增 source=HOME_BLOCK(4)
- 消费端首页 Newsletter 区块渲染订阅表单

### FUNC-009 Hero 跨域读取 Banner
- HomeBuilder Hero 区块通过 BannerService.findByPosition(HERO) 跨域查询
- 不直接查 Banner 表（KD-BE-5）

### FUNC-010 Navigation 跨域引用 Taxonomy
- NavigationConfig 保存时通过 TaxonomyService 校验 taxonomy_id
- Mega Menu 列子链接由 TaxonomyService 派生

## 非功能需求

### PERF
- 消费端读取 API P95 < 200ms（JetCache 缓存命中）
- JetCache TTL = 300s（Caffeine + Redis 两级）
- 管理端保存 API P95 < 500ms

### SEC
- 管理端写入 API 需 admin JWT + RBAC 权限码
- 消费端读取 API 匿名可读
- 权限码：/site/home, /site/navigation, /site/announcement

### i18n
- 本期支持 EN 基准 + ZH 多语言
- 翻译表合并到主表 JSON 列（KD-16）

## 验收基准

→ 见 hhspec/changes/site-decoration-fullstack/acceptance-baseline.md
→ 见 hhspec/changes/site-decoration-fullstack/acceptance.yml（338 条场景）
