// 交易/物流/税费域业务错误码 → 中文提示（order-flow-complete J）
// 后端 Accept-Language=zh 已返回中文 message；此映射用于：① 前端需要比后端更具操作指引的文案 ② 网络层兜底。
// 用法：toast.error(describeError(e, '操作失败'))
import { BizError } from '@/api/client'

export const TRADING_ERROR_MESSAGE: Record<number, string> = {
  409602: '当前订单状态不允许该操作',
  409605: '该订单已有进行中的退款',
  409905: '当前为手工汇率模式，可在配置中切换为供应商模式后再刷新',
  409906: '该订单正在进行其他发货操作，请稍后重试',
  409907: '该订单已有待处理的退款工单',
  409908: '该物流单号已在此订单中登记',
  409909: '当前包裹状态不允许该操作',
  409903: '承运商编码已存在',
  409904: '同分区、承运商与服务等级的运费选项已存在',
  404903: '运费选项不存在，列表可能已变更',
  404906: '税率规则不存在，列表可能已变更',
  404907: '包裹不存在，可能已被作废',
  410901: '运费规则已迁移至「运费选项」，请在新面板维护',
  422601: '字段校验失败，请检查输入',
  422602: '定制商品已投产，不可退款',
  422603: '超出可退上限',
  422605: '币种不支持',
  422906: '发货数量超过订单行未发数量',
  422907: '税率生效窗口与现有规则重叠',
  422908: '退款金额超过剩余可退金额',
  502602: '汇率供应商暂不可用，已保留现有汇率',
}

/** 优先本地映射（更具指引），其次后端 message，最后 fallback */
export function describeError(e: unknown, fallback = '操作失败'): string {
  if (e instanceof BizError) {
    return TRADING_ERROR_MESSAGE[e.code] || e.message || fallback
  }
  if (e instanceof Error && e.message) return e.message
  return fallback
}
