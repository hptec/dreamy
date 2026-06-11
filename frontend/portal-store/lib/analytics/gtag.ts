/**
 * GA4 gtag 封装（决策 19 + 连带约束 Consent Mode v2）。
 * - measurement id 走 NEXT_PUBLIC_GA4_ID 环境变量；未配置时全部函数 no-op。
 * - Consent Mode v2：命令队列先压 consent default（analytics_storage/ad_* 全 denied），
 *   用户 granted 后才压 update + config 并由 CookieConsent 注入 gtag.js 脚本本体——
 *   拒绝/未选择时脚本不加载、事件不入队，前端不产生任何分析 Cookie（s-1042）。
 * - 标准电商事件（决策 19）：page_view（路由变化自动，CookieConsent 内监听）/
 *   view_item / add_to_cart / begin_checkout / purchase（items/value/currency 参数）。
 */

export const GA4_ID = process.env.NEXT_PUBLIC_GA4_ID ?? ''

export type ConsentChoice = 'granted' | 'denied'

/**
 * consent 持久化键。注意：不复用旧 `dreamy_cookie_ok`——旧 CookieNotice 的
 * Accept/Decline 都写同一标记（仅做关闭记忆），不构成有效 consent 信号，不迁移。
 */
const CONSENT_KEY = 'dreamy_cookie_consent'

declare global {
  interface Window {
    dataLayer?: unknown[]
  }
}

function isBrowser(): boolean {
  return typeof window !== 'undefined'
}

/** 读取已持久化的 consent 选择（未选择 → null，banner 据此弹出） */
export function getStoredConsent(): ConsentChoice | null {
  if (!isBrowser()) return null
  try {
    const v = localStorage.getItem(CONSENT_KEY)
    return v === 'granted' || v === 'denied' ? v : null
  } catch {
    return null
  }
}

/** 持久化 consent 选择（localStorage，非 Cookie——denied 路径全程零 Cookie 写入） */
export function storeConsent(choice: ConsentChoice): void {
  if (!isBrowser()) return
  try {
    localStorage.setItem(CONSENT_KEY, choice)
  } catch {
    /* 私密模式等存储不可用：本次会话内有效，下次重新询问 */
  }
}

/** gtag 命令入队（gtag.js 按 Arguments 对象消费 dataLayer，必须原样 push arguments） */
function gtag(..._args: unknown[]): void {
  void _args
  if (!isBrowser() || !GA4_ID) return
  window.dataLayer = window.dataLayer ?? []
  // eslint-disable-next-line prefer-rest-params
  window.dataLayer.push(arguments)
}

/** 事件可发条件：GA4 已配置 + 用户已 granted（denied/未选择 → no-op，s-1042） */
function analyticsEnabled(): boolean {
  return !!GA4_ID && isBrowser() && getStoredConsent() === 'granted'
}

/**
 * granted 后的 gtag 初始化（CookieConsent 注入脚本前调用一次）：
 * 命令先入 dataLayer 队列，gtag.js lazyOnload 装载后按序消费——
 * consent default 永远先于 config 被处理（Consent Mode v2 时序要求）。
 */
export function initGtagQueue(): void {
  if (!isBrowser() || !GA4_ID) return
  gtag('consent', 'default', {
    analytics_storage: 'denied',
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied'
  })
  gtag('consent', 'update', { analytics_storage: 'granted' })
  gtag('js', new Date())
  // send_page_view=false：page_view 统一由路由监听补发（首屏 + 客户端导航单一口径，避免双发）
  gtag('config', GA4_ID, { send_page_view: false })
}

/** GA4 电商 item 形状（snake_case 为 gtag 契约字段名，不走 API case 转换） */
export interface GtagItem {
  item_id: string
  item_name: string
  price?: number
  quantity?: number
  item_variant?: string
}

interface EcommerceParams {
  currency: string
  value: number
  items: GtagItem[]
}

/** page_view（路由变化自动，CookieConsent 内 usePathname 监听触发） */
export function trackPageView(path: string): void {
  if (!analyticsEnabled()) return
  gtag('event', 'page_view', {
    page_path: path,
    page_location: window.location.href,
    page_title: document.title
  })
}

/** view_item（PDP 进入） */
export function trackViewItem(params: EcommerceParams): void {
  if (!analyticsEnabled()) return
  gtag('event', 'view_item', params)
}

/** add_to_cart（加购成功后） */
export function trackAddToCart(params: EcommerceParams): void {
  if (!analyticsEnabled()) return
  gtag('event', 'add_to_cart', params)
}

/** begin_checkout（进入结算流程） */
export function trackBeginCheckout(params: EcommerceParams): void {
  if (!analyticsEnabled()) return
  gtag('event', 'begin_checkout', params)
}

/** purchase（order-success 落账确认；transaction_id=order_no） */
export function trackPurchase(params: EcommerceParams & { transactionId: string }): void {
  if (!analyticsEnabled()) return
  const { transactionId, ...rest } = params
  gtag('event', 'purchase', { transaction_id: transactionId, ...rest })
}
