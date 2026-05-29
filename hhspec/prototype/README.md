# Maison Eden — 产品原型

> 面向美国市场的高端婚纱 + 小礼服外贸 DTC 站。基于对 JJ's House 与 Azazie 的深度竞品分析构建，吸收两家之长，视觉对标并上探至高定时装屋水准。

## 概述

Maison Eden 是一个完整的、可直接运行的高保真电商原型：定制 + 现货结合的经营模式，覆盖婚纱、晚礼/鸡尾酒小礼服、伴娘服、妈妈装、宾客礼服、花童与配饰，支持 **4 语言（EN/ES/FR/DE）× 5 币种（USD/EUR/GBP/CAD/AUD）实际切换**，含从浏览、商品详情、购物车、多步结算到账号中心的完整购物闭环。

## 预览

**开发模式**（热更新，干净 URL）：

1. `./scripts/prototype.sh`（推荐，一键启动，自动装依赖）
2. `pnpm run prototype:dev`
3. `cd hhspec/prototype && pnpm dev`

访问：**http://localhost:5173**（修改 `.vue` 自动热更新）

**静态发布版**（编译为单文件，可双击打开）：

```bash
pnpm run prototype:build      # 产物输出到 hhspec/prototype/dist/
```

产出 `dist/index.html`（JS+CSS+字体全内联，约 2.6MB）+ `dist/img/`（媒体）。
**直接双击 `dist/index.html` 即可在浏览器打开**（采用 hash 路由 + 单文件内联，无需任何服务器）。也可部署到任意静态托管（Vercel / Netlify / GitHub Pages / nginx / 对象存储）。

## 技术栈

- **原型实现**：pnpm + Vue 3 + Vite + Tailwind CSS + Headless UI + Pinia + vue-i18n + vue-router
- **设计风格**：Ferrari 编辑式（黑白明暗对比 · 极致留白 · 2px 锐利圆角 · 香槟金克制点缀）
- **字体**：Cormorant Garamond（高定衬线）+ Inter（功能无衬线），经 npmmirror 自托管
- **目标生产框架**：pnpm-vue3-headless（与 `hhspec/project.yml` 一致），组件可直接迁移为生产代码
- **数据**：前端 mock（`src/data/catalog.js`），34 件礼服 + 8 件配饰，含评价/问答/系列
- **媒体**：128 张竞品参考素材（`public/img/`，仅原型参考用）

## 功能范围（feature-map 共 104 项 F-编号）

### Must Have（核心，已全部实现）
- 全局框架：sticky 头部 + mega menu + 全屏搜索 + 币种/语言切换 + Mini Cart 抽屉 + 多列页脚
- 首页：全屏摄影 Hero + 精选系列 + 品类宫格 + 工艺价值 + 配色系统 + 买家秀 + Atelier 故事
- 列表页：多维筛选（颜色/面料/轮廓/领型/价格/现货）+ 排序 + 筛选 chip + 商品卡颜色切换 + Quick View + 加载更多
- PDP：多图画廊 + 灯箱 + 颜色色卡 + 长度/尺码 + 量体定制弹层 + 尺码指南 + 定制工期(标准/加急/急速) + 双 CTA + 评价(评分分布·按尺码筛选·买家图·商家回复) + 问答 + Often Bought With + You May Also Like + Recently Viewed
- 购物车 + 促销码 + 运费估算；3 步结算（地址→配送支付→复核）+ 订单确认
- 账号：登录/注册 + Dashboard + 资料 + 订单列表 + 订单详情(物流时间线) + 心愿单 + 地址簿 + 设置
- 内容：品牌故事 / Atelier 工艺 / 尺码指南 / 配送退换 / FAQ / 联系 / 真实买家墙 / Lookbook
- 国际化：语言切换（含 URL 行为）+ 币种按固定汇率实时换算 + 区域引导弹层
- 状态/辅助：404 / 空状态 / 邮件弹层 / Cookie 条 / Toast / 客服悬浮入口

## 页面清单（30 个独立视图）

| 区 | 视图 | 路由 |
|---|---|---|
| 浏览 | Home | `/` |
| 浏览 | WeddingDresses / EveningDresses / BridesmaidDresses / SpecialOccasions / Accessories | `/wedding-dresses` 等 |
| 浏览 | SearchResults | `/search` |
| 详情 | ProductDetail | `/products/:slug` |
| 购物 | Cart / CheckoutAddress / CheckoutPayment / CheckoutReview / OrderConfirmation | `/cart`、`/checkout/*` |
| 账号 | Auth / Dashboard / Profile / Orders / OrderDetail / Wishlist / Addresses / Settings | `/account/*` |
| 内容 | About / Atelier / SizeGuide / ShippingReturns / Faq / Contact / StyleGallery / Lookbook | `/about` 等 |
| 状态 | NotFound | `/:catchall` |

## 竞品分析

完整竞品分析见 [.research/competitor-analysis.md](.research/competitor-analysis.md)，原始抓取数据（结构 JSON / 截图 / 媒体）在 `.research/{azazie,jjshouse}/`。

## 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 视觉风格 | Ferrari 黑白编辑 + 香槟金 | 对标高定时装屋，原 Ferrari 赛车红替换为契合婚纱的香槟金 |
| 经营模式 | 定制 + 现货结合 | 高定心智 + 缓解定制等待焦虑 |
| 价格带 | US$99–2000+ 宽带 | 覆盖配饰到高级婚纱多层级客群 |
| 国际化 | 4 语言 5 币种实际切换 | 外贸站核心，币种按固定汇率真实换算 |
| 品牌名 | Maison Eden | 法式高定 + 伊甸园浪漫意象 |

## 下一步

- 继续细化原型 → 再次运行 `/pd:prototype`
- 转为需求文档 → 运行 `/pd:explore`
- 查看功能详情 → 阅读 [feature-map.md](feature-map.md)
- 替换媒体素材 → 将 `public/img/` 占位图替换为自有拍摄/已授权素材（上线前必做，见下方版权提示）

## ⚠️ 版权提示

`public/img/` 与 `.research/` 中的图片为竞品站抓取的参考素材，**仅适用于内部原型演示**。正式上线前必须替换为自有拍摄或已获授权的素材。
