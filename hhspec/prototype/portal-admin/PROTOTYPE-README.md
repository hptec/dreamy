# Dreamy 运营管理后台 — 原型说明

> portal-admin · pnpm + Vue3 + Vite + Tailwind + Headless UI

## 启动

```bash
# 方式 1：项目根脚本（推荐）
./scripts/prototype-portal-admin.sh

# 方式 2：pnpm workspace
pnpm -C hhspec/prototype/portal-admin dev

# 方式 3：进入目录
cd hhspec/prototype/portal-admin && pnpm install && pnpm dev
```

访问 http://localhost:5174 · 任意凭据即可登录（原型演示，无真实鉴权）。

## 定位

本后台是「**传统电商后台 + 全站内容可配置 + 静态站点发布器**」三合一：

1. **传统电商后台**（参考 macrozheng/mall-admin）：商品 PIM、订单 OMS、用户、营销、数据看板、退款、系统权限
2. **全站内容可配置（CMS）**：消费端首页 Hero/推荐位/主题卡、导航 Mega Menu、Footer、公告条、Banner、Blog/RealWedding/Lookbook/Guide 全部后台可视化编辑
3. **静态站点发布器（SSG）**：「发布中心」模拟 `next build`（消费端已是 `output: export`）流程——改动 diff → 构建日志 → 生成受影响 HTML 页面 → 发布历史 + 回滚

## 与消费端的关系

| | 消费端 portal-store | 管理后台 portal-admin |
|---|---|---|
| 技术栈 | Next.js 15 + TS | Vue3 + Vite + Headless UI |
| 端口 | 5173 | 5174 |
| 设计 token | editorial-luxe-coastal | **同源**（canvas/ink/gold/sage/blush） |
| 隔离 | 互不链接，独立鉴权 | 独立 RBAC |

> 技术割裂是用户有意的选择（消费端 React / 后台 Vue3）。两端通过同源设计 token 保持品牌视觉一致。

## 页面清单（22）

工作台 · 商品列表 · 商品编辑（6步向导）· 品类与主题 · 订单列表 · 订单详情 · 退款工单 · 用户列表 · 用户详情 · **首页装修** · **导航与页脚** · Banner 管理 · 优惠券与促销 · 邮件营销 · Blog 文章 · Real Weddings · Lookbook 与指南 · 数据看板 · **发布中心** · 物流配置 · 系统设置 · 后台登录

加粗为相比标准 mall-admin 的差异化亮点页面。

## 数据

所有数据为 Mock（`src/data/mock.js`），业务语义贴近 Dreamy 婚纱电商，商品/订单/用户字段与前台 `data/*.ts` 对齐。
