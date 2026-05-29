# 需求摘要 — Dreamy（婚纱礼服跨境外贸电商）

> 本文档汇总 Step 3 需求探索的全部澄清结果，作为下游 feature-map / 页面规划 / 原型生成的输入依据。
> 创建日期：2026-05-29

## 产品定位

面向美国 + 全球（CAD/AUD/GBP 覆盖）的高端婚纱、礼服、配饰跨境外贸电商站。
调性高端大气、明亮明媚（highlight），强调户外婚礼场景（草坪 / 海滩 / 森林 / 庄园 / 葡萄园），目标客户 25-35 岁中高购买力准新娘群体。

## 目标用户

| 优先级 | 角色 | 核心场景 |
|--------|------|---------|
| P0 | 美国准新娘 | 户外婚礼挑选婚纱；同步购买配饰 |
| P1 | 伴娘 / 母亲 / 花童 | 与新娘联动购买配套礼服 |
| P1 | Prom / 晚宴客户 | 毕业舞会、晚宴、鸡尾酒会场景的礼服 |
| P2 | 全球客户（CA/AU/UK） | 多币种 + 全球物流支持 |

## 竞品参考

| 竞品 | 借鉴侧重 |
|------|---------|
| birdygrey.com | 伴娘色板 Color Palette、Try-at-Home、订阅式品牌叙事、明亮淡雅调性 |
| davidsbridal.com | 完整电商功能矩阵、多用户角色、综合 SKU 管理、CMS Lookbook 与 Real Weddings |
| kissprom.com | 礼服 Filter 维度、移动端体验、SKU 视觉表达密度 |

**竞品资源使用方式**：抓取三家网站的商品图、Lookbook、文案、Filter 字段作内部 demo 用途（不公开 / 不商用 / 仅原型演示）。本地存放路径：`hhspec/prototype/assets/competitor-refs/{birdygrey, davidsbridal, kissprom}/`。

## 门户架构

双门户，互不链接、安全隔离：

| 门户 ID | 用途 | 目标用户 |
|---------|------|---------|
| `portal-store` | 消费者商城 | 准新娘 / 伴娘 / 晚宴客户 / 全球 C 端 |
| `portal-admin` | 平台运营管理后台 | 商品 / 订单 / 用户 / 营销 / 数据 / CMS / 物流 / 退款运营 |

## 核心功能（Must Have）

### portal-store 消费者商城

1. **商品域**
   - 三大品类：Wedding Dresses（婚纱）/ Special Occasion Dresses（小礼服：伴娘 / 母亲 / 晚礼 / Prom / 鸡尾酒会）/ Accessories（配饰：鞋 / 头饰 / 面纱 / 首饰）
   - Outdoor Weddings 独立主题页，含子主题：Beach、Garden、Boho、Forest、Vineyard
2. **商品详情页（PDP）**
   - 多角度静态图 + Hover Zoom 放大
   - Lifestyle 户外场景图
   - 模特走秀 / Walking 视频
   - 尺码表（含 US/UK/AU 三套尺码对照）
   - SKU 颜色 / 面料 swatch 切换
   - Color Palette 模块（婚纱页推荐对应伴娘色）
3. **完整跨境电商交易**
   - Cart Drawer + 全屏购物车页 + 多步结算（地址 → 物流 → 支付 → 确认）
   - 支付方式：Stripe Credit Card、PayPal、Apple Pay、Google Pay、Klarna / Afterpay 分期付款
   - 多币种切换器：USD / CAD / AUD / GBP
   - 多语言切换器：EN / ES（仅切换器结构与英文内容，ES 文本占位）
   - 全球物流：FedEx / UPS / DHL Express 国际选项
4. **账户中心（My Account）**
   - 订单列表 + 订单详情 + Order Tracking 物流跟踪
   - 收货地址簿
   - Wishlist
   - Recently Viewed 浏览历史
   - My Reviews
   - 账户设置 / 修改密码
5. **转化辅助**
   - 商品评价 Reviews + Q&A
   - Wishlist 加入与同步
   - Recently Viewed
   - Related Products 相关推荐
   - Outfit Builder 搭配推荐（婚纱+配饰）
   - Lookbook 主题画册
   - Real Wedding Gallery 真实婚礼故事
   - Bridesmaid Color Palette 伴娘色板
   - Wedding Vibes 主题选择（户外婚礼引导）
6. **内容栏目（CMS）**
   - Wedding Inspiration 灵感馆
   - Real Weddings 真实婚礼
   - Blog 婚礼策划文章
   - Wedding Planning Guides 婚礼准备流程指南（按筹备时间轴）
7. **营销触达**
   - 首次访问 Newsletter 弹窗
   - Exit Intent 退出意图弹窗
   - Add to Cart Drawer
   - 页脚 Newsletter 订阅
   - Cookie Notice

### portal-admin 平台管理后台

8. **PIM 商品管理**：商品列表 / 商品编辑（基础信息 / 媒体 / SKU 矩阵 / 尺码表 / 价格 / 库存 / SEO）/ 品类与主题管理
9. **OMS 订单管理**：订单列表 / 订单详情 / 发货操作 / 退款审批
10. **用户管理**：用户列表 / 用户详情 / 角色权限
11. **营销活动**：优惠券 / 促销规则 / Flash Sale / 首页 Banner 配置 / 邮件营销列表
12. **数据看板**：销售 GMV / 流量来源 / 转化漏斗 / 商品热度 / 用户画像
13. **CMS 内容编辑**：Blog / Lookbook / Real Wedding / Wedding Guide / Landing Page
14. **物流配置**：承运方 / 邮费表 / 国际运送规则
15. **退款管理**：退款工单 / 处理日志
16. **系统**：管理员账号 / 角色权限 / 操作日志

## 优先级

- **第一优先级（必须本次完成）**：消费端完整购物链路 + Outdoor Weddings 主题入口 + 后台 PIM / OMS / 用户 / 营销 / 数据 / CMS 模块
- **第二优先级**：账户中心完整模块、CMS Blog 与 Guides、后台物流 / 退款 / 系统
- **第三优先级**：弹窗 / Drawer / 邮件订阅等营销触达组件

## 设计参考

- 调性：高端大气 + 明亮明媚 highlight + 户外婚礼自然光
- 视觉风格候选：Modern Editorial（杂志感 + 大图 + 细衬线 + 米白底）/ Coastal Boho（自然手感 + 米白原木 + 柔和金光） — 具体在 Step 6.1 选定
- 色板倾向：浅米白 / 沙金 / 浅鼠尾草绿 / 玫瑰金 / 大留白
- 排版倾向：编辑级杂志风 + 大图 Hero + 留白 + 衬线主标题 + 无衬线正文

## 明确不包含（反目标）

| 反目标 | 原因 |
|--------|------|
| 社交登录（Facebook / Google / Apple） | 本次仅做邮箱 + 密码注册登录，简化原型 |
| 礼品卡 Gift Card | 边缘功能，与婚纱主流程关联弱 |
| Wedding Registry 婚礼礼物注册表 | 美国本地化深度太重，本次不纳入 |
| AR 虚拟试穿（交互） | 媒体资源不具备，仅保留入口开关 |
| 推荐返利 Referral / Affiliate | 营销高阶功能，下一迭代 |
| 直播购物 Live Shopping | 复杂度高，本次不纳入 |
| Bridesmaid Group 团组邀请 | 高级账户功能，本次不纳入 |
| Loyalty 会员积分体系 | 复杂度高，下一迭代 |
| 试穿 / 退换货独立模块 | 用户明确暂不做 |
| Live Chat 实时聊天 | 本次仅做营销弹窗与 Newsletter |

## 规模与生成策略

- 总页面数估测：**消费端 ≈ 25-28 页 + 后台 ≈ 12-15 页 = 总计 ≈ 37-43 页**
- 用户决策：**一次性生成全部页面**，质量与完整性优先于交付速度
- 实现策略：分批生成，每批 4-6 页，逐批通过 DOM 审计后写入 sync-status.yml；门户间 pnpm workspace 并行存放

## 待定事项

- 设计风格 ID 在 Step 6.1 选定（带 SAFE/RISK 标注）
- 商品 demo 数据量：每品类 12-20 个 SKU
- 部分页面流转细节在 Step 5 确认
- 后台用户角色权限矩阵（在 Step 4 feature-map 生成时细化）
