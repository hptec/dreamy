# i18n Portal-Store 前端详细设计

## 元信息

- 变更：i18n-complete-with-ai-assist
- 生成时间：2026-06-16T20:35:00Z
- 技术栈：Next.js 15 App Router + next-intl/自研useI18n
- 覆盖：路由重构 + 50+组件i18n + SEO增强 + designerNote消费

---

## 1. 路由重构 (决策11)

### 1.1 目录结构变更

```
变更前：              变更后：
app/                 app/
├── page.tsx         ├── [locale]/
├── product/         │   ├── page.tsx          (首页)
├── collection/      │   ├── product/[slug]/
├── cart/            │   ├── collection/[handle]/
└── checkout/        │   ├── cart/
                     │   └── checkout/
                     ├── sitemap.ts            (多语言sitemap)
                     └── middleware.ts          (locale检测)
```

EN为根路径（无前缀），ES为/es/，FR为/fr/

### 1.2 middleware locale检测 (FUNC-014)

```typescript
// middleware.ts
export function middleware(request: NextRequest) {
  const pathname = request.nextUrl.pathname
  
  // 1. 提取URL路径前缀
  const pathLocale = extractLocaleFromPath(pathname)  // /es/xxx → 'es'
  
  // 2. 无效locale前缀（EDGE-018）
  if (pathLocale && !SUPPORTED_LOCALES.includes(pathLocale)) {
    // 非en/es/fr → 302临时重定向到EN
    return NextResponse.redirect(stripLocale(pathname), 302)
  }
  
  // 3. 有效locale前缀，放行
  if (pathLocale) {
    const response = NextResponse.next()
    response.cookies.set('NEXT_LOCALE', pathLocale)
    return response
  }
  
  // 4. 无前缀（旧链接兼容，EDGE-019）
  const detectedLocale = 
    request.cookies.get('NEXT_LOCALE')?.value ||
    parseAcceptLanguage(request.headers.get('Accept-Language')) ||
    'en'
  
  if (detectedLocale === 'en') {
    return NextResponse.next()  // EN保持根路径不重定向
  }
  
  // ES/FR → 301永久重定向到带前缀URL（SEO友好）
  return NextResponse.redirect(`/${detectedLocale}${pathname}`, 301)
}

const SUPPORTED_LOCALES = ['en', 'es', 'fr']
```

**检测优先级**：URL前缀 > cookie NEXT_LOCALE > Accept-Language > 默认EN

---

## 2. messages.ts扩展 (FUNC-002, 决策9)

### 2.1 命名空间结构（30+）

```typescript
// messages/en.ts (es.ts/fr.ts同结构)
export default {
  common: { ... },          // 通用按钮/标签
  layout: {                 // 布局chrome
    header: { ... },
    footer: { ... },
    nav: { ... },
  },
  home: { ... },            // 首页
  collection: {             // 集合页
    title, filter, sort, empty, loadMore
  },
  product: {                // PDP
    addToCart, sizeGuide, description, reviews, ...
  },
  cart: {                   // 购物车
    drawer: { ... },        // cart-drawer组件
    empty, subtotal, checkout
  },
  checkout: { ... },        // 结算
  marketing: { ... },       // 营销文案
  fabric: {                 // 决策12: 面料材质+层级
    materials: {            // 10种材质
      cotton, lace, satin, silk, tulle, chiffon, organza, ...
    },
    layers: {               // 4种层级
      shell, lining, interlining, trim
    }
  },
  cookieConsent: { ... },   // cookie-consent组件
  fabricCare: { ... },      // FabricCareSection组件
  error: { ... },           // 错误态
  empty: { ... },           // 空态
  // ...30+命名空间
}
```

### 2.2 缺失翻译回退EN (FUNC-003, EDGE-020)

```typescript
function t(key: string, locale: string): string {
  const value = getNestedValue(messages[locale], key)
  if (!value || value === '[TRANSLATION_PENDING]') {
    return getNestedValue(messages['en'], key) || key  // 回退EN，最终回退key名
  }
  return value
}
```

---

## 3. 50+组件i18n补齐 (FUNC-002)

### 3.1 改造清单（审计硬编码英文）

| 组件 | 命名空间 | 硬编码示例 |
|------|---------|-----------|
| Header | layout.header | "Search", "Account", "Cart" |
| Footer | layout.footer | "About Us", "Contact", ... |
| CartDrawer | cart.drawer | "Your Cart", "Subtotal", "Checkout" |
| CollectionView | collection | "Filter", "Sort by", "No products" |
| ProductDetail | product | "Add to Cart", "Size Guide" |
| FabricCareSection | fabricCare | "Fabric & Care", material names |
| CookieConsent | cookieConsent | "We use cookies..." |
| CheckoutForm | checkout | "Shipping", "Payment", ... |
| ... (50+) | ... | ... |

**改造模式**：
```tsx
// 改造前
<button>Add to Cart</button>
// 改造后
const { t } = useI18n()
<button>{t('product.addToCart')}</button>
```

### 3.2 面料材质i18n (决策12)

材质名（Cotton/Lace/Satin）+ 层级名（Shell/Lining）走fabric命名空间字典，**不入数据库**（有限枚举，10材质+4层级）

---

## 4. SEO增强

### 4.1 hreflang标签 (FUNC-015)

```tsx
// app/[locale]/layout.tsx generateMetadata
export async function generateMetadata({ params }): Promise<Metadata> {
  const { locale } = params
  return {
    alternates: {
      languages: {
        'en': `${BASE_URL}${pathWithoutLocale}`,
        'es': `${BASE_URL}/es${pathWithoutLocale}`,
        'fr': `${BASE_URL}/fr${pathWithoutLocale}`,
        'x-default': `${BASE_URL}${pathWithoutLocale}`,
      }
    }
  }
}
```

每个页面输出3语言+x-default的hreflang标签

### 4.2 多语言sitemap (FUNC-016)

```typescript
// app/sitemap.ts
export default async function sitemap(): MetadataRoute.Sitemap {
  const pages = await getAllPages()  // 产品/集合/静态页
  return pages.flatMap(page => 
    SUPPORTED_LOCALES.map(locale => ({
      url: locale === 'en' ? `${BASE_URL}${page.path}` : `${BASE_URL}/${locale}${page.path}`,
      lastModified: page.updatedAt,
      alternates: {
        languages: {
          en: `${BASE_URL}${page.path}`,
          es: `${BASE_URL}/es${page.path}`,
          fr: `${BASE_URL}/fr${page.path}`,
        }
      }
    }))
  )
}
```

按语言生成全量URL条目

---

## 5. designerNote消费端展示 (FUNC-018, EDGE-020, 决策12)

### 5.1 assembleDetail pick()回退

```typescript
// PDP数据组装
function assembleDetail(product, translation, locale): ProductDetail {
  return {
    ...
    designerNote: pick(
      translation?.designerNote,    // product_translation译文(ES/FR)
      product.designerNote,         // product主表EN值(回退源)
      ''                            // 最终空字符串
    ),
  }
}

function pick(...values): string {
  return values.find(v => v != null && v !== '') ?? ''
}
```

designerNote在PDP Description折叠区展示，ES/FR译文缺失时回退EN主表值（EDGE-020）

---

## 6. 内部链接locale前缀处理

```tsx
// 统一Link组件包装，自动附加locale前缀
function LocalizedLink({ href, ...props }) {
  const locale = useLocale()
  const localizedHref = locale === 'en' ? href : `/${locale}${href}`
  return <Link href={localizedHref} {...props} />
}
```

所有内部导航链接需通过LocalizedLink，保持locale一致性

---

## 7. 邮件locale消费 (FUNC-021, EDGE-021, 决策13)

注：邮件发送在后端，前端仅负责下单时传递locale快照
- 下单请求携带当前locale → 后端存orders.locale_snapshot
- 登录用户偏好 → user.locale_pref（通过 `PUT /api/consumer/auth/profile`，StoreBearerAuth；匿名用户仅写 cookie/localStorage 不调接口）

---

## 8. 需求追溯

- FUNC-001: middleware locale切换+UI文案
- FUNC-002: messages.ts 30+命名空间+50+组件
- FUNC-003: 回退EN逻辑
- FUNC-014: 路由前缀+middleware
- FUNC-015: hreflang标签
- FUNC-016: 多语言sitemap
- FUNC-018: designerNote pick回退
- FUNC-021: 邮件locale快照(下单时传递)

---

## 9. 关键风险与待确认

1. **路由重构影响面大**：50+组件的Link改造，需要全量回归测试
2. **SSR/CSR locale一致性**：middleware设置cookie与客户端useLocale需保持同步
3. **现有localStorage迁移**：决策11移除localStorage作为路由依据，仅作降级辅助
4. **next-intl vs自研**：需确认现有项目使用的i18n方案（建议读取现有messages.ts确认）

---

**设计完成标记**：✅ Portal-Store前端详设已完成
