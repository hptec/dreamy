# Dreamy — 婚纱礼服跨境外贸电商原型

面向美国 + 全球的高端婚纱、小礼服、配饰跨境电商网站原型。调性高端大气、明亮明媚，主打户外婚礼场景。

## 概述

单门户消费者商城（高保真可交互原型）。基于 birdygrey / davidsbridal / kissprom 三个竞品的深度结构分析与真实商品媒体素材构建。

> 注：原计划含平台管理后台，已按用户要求移除，本次仅消费者前台。

## 预览

```bash
# 一键启动（推荐）
./scripts/prototype.sh

# 或从项目根
pnpm run prototype:dev

# 或手动
cd hhspec/prototype && pnpm dev
```

访问：**http://localhost:5173**

修改任意 `.tsx` 文件后浏览器自动热更新。

## 技术栈

- **框架**：Next.js 15（App Router，SSR/SSG，对 SEO 友好）
- **语言**：TypeScript
- **样式**：Tailwind CSS + shadcn 风格组件（lucide-react 图标）
- **状态**：React Context + localStorage（购物车 / 收藏 / 币种 / 浏览历史）
- **字体**：Cormorant Garamond（衬线标题）+ Jost（无衬线正文），经 Google Fonts CDN 加载
- **UI 风格**：editorial-luxe-coastal（暖米白 + 沙金 + 鼠尾草绿 + 玫瑰金 + 大留白）

## 功能范围（26 个页面）

### 商品浏览
- 首页（编辑式 Hero / Shop by Color 调色板 / 户外主题 / 推荐位 / Lookbook / Real Weddings / 价值主张）
- 婚纱 / 小礼服 / 配饰 三大品类列表（多维 Filter + 排序 + Quick View + 子类 Tab）
- Outdoor Weddings 主题页（Beach/Garden/Boho/Forest/Vineyard 子主题）
- 搜索结果页（实时搜索 + 空状态）

### 商品详情（PDP，3 个示例：婚纱 / 伴娘 / 面纱）
- 多角度图廊 + Hover Zoom + 走秀视频占位 + Lifestyle 场景图
- 色板切换 + 尺码选择（US/UK/AU 尺码表）+ 数量
- Add to Bag / Wishlist / Order Swatch / AR（占位）
- 信任承诺条 + 描述手风琴 + Complete the Look + Reviews + Q&A + 相关推荐

### 交易
- Cart Drawer（加购抽屉）+ 购物车页（改数量 / 优惠码 / 摘要）
- 4 步结算（地址 → 物流 → 支付 → 复核）+ 6 种支付（Stripe/PayPal/Apple/Google Pay/Klarna/Afterpay）
- 多币种切换（USD/CAD/AUD/GBP）+ 多语言切换器（EN/ES）
- 下单成功页

### 账户中心
- 登录 / 注册、Dashboard、订单列表、订单详情（物流时间轴）
- 地址簿、Wishlist + Recently Viewed、设置（资料/改密/我的评价/邮件偏好）

### 内容栏目
- Wedding Inspiration 灵感馆、Real Weddings（列表 + 详情 Shop the Look）
- Blog（列表 + 详情）、Wedding Planning Guides（筹备时间轴）

### 全局
- Newsletter 首访弹窗、Cookie Notice、Mega Menu 导航、搜索抽屉、页脚订阅

## 目录结构

```
hhspec/prototype/
├── app/                    # Next.js App Router 页面
│   ├── (各路由)/page.tsx
│   ├── layout.tsx          # 根布局（Header/Footer/弹窗/状态）
│   ├── globals.css         # 全局样式与设计 token
│   └── not-found.tsx       # 404
├── components/             # 共享组件（layout/product/cart/marketing/account/ui）
├── data/                   # 商品/内容/导航/账户 mock 数据（引用真实竞品图）
├── lib/utils.ts            # 工具（cn / 价格换算 / 分期）
├── public/competitor-refs/ # 52 张真实竞品商品图（内部 demo 用途）
├── tailwind.config.ts      # 设计系统配置
├── feature-map*.md         # 功能地图
├── requirements-brief.md   # 需求摘要
├── sync-status.yml         # 双向同步状态
└── .qa/                    # 视觉验证截图 + 一致性报告
```

## 质量验证

- ✅ `pnpm build` 成功，0 TypeScript 错误，28 路由编译（含 16 PDP / 3 blog / 3 real-wedding / 3 order SSG 预渲染）
- ✅ Playwright 全站验证：29 页 × 桌面/移动双视口，0 JS 错误，0 图片 404
- ✅ 设计一致性：统一字体 / 配色 token / 共享组件，lucide SVG 图标（无 emoji）
- 视觉验证截图见 `.qa/`

## 下一步

- 继续迭代原型 → 再次运行 `/pd:prototype`
- 接入真实后端 API → 替换 `data/` 下的 mock 数据
- 商品图替换 → `public/competitor-refs/` 当前为竞品 demo 图，正式上线需替换为自有版权素材
