package com.dreamy.error;

import lombok.Getter;

/**
 * trading 域错误码枚举（19 码 + order-flow-complete 6 码，域段 6/9）。权威来源 error-strategy.md trading 段 + trading-api.openapi.yml info 码表。
 * 6 位码：HTTP(3) + 域段(1=6) + 序号(2)；identity 复用码（40100/40300/50000/50001）仍走 identity ErrorCode；
 * 跨域透传：404501（catalog）/ 422701~422703（marketing）由 TradingExceptionHandler 委托各域处理器口径输出。
 */
@Getter
public enum TradingErrorCode {

    // ===== 404（BE-DIM-6 防探测：跨用户/不存在同码） =====
    ORDER_NOT_FOUND(404601, 404, "error.404601"),
    ADDRESS_NOT_FOUND(404602, 404, "error.404602"),
    CART_ITEM_NOT_FOUND(404603, 404, "error.404603"),
    WISHLIST_ITEM_NOT_FOUND(404604, 404, "error.404604"),
    REFUND_NOT_FOUND(404605, 404, "error.404605"),

    // ===== 409 =====
    STOCK_INSUFFICIENT(409601, 409, "error.409601"),
    ORDER_STATE_INVALID(409602, 409, "error.409602"),
    DUPLICATE_SUBMISSION(409603, 409, "error.409603"),
    REFUND_STATE_INVALID(409604, 409, "error.409604"),
    REFUND_ALREADY_EXISTS(409605, 409, "error.409605"),
    /** order-flow-complete：发货锁冲突（trading:order-ship:{orderId} 获取失败） */
    SHIP_LOCK_CONFLICT(409906, 409, "error.409906"),
    /** order-flow-complete：已有挂起退款工单 / 状态不允许申请（CAS →REFUNDING affected=0） */
    REFUND_PENDING_EXISTS(409907, 409, "error.409907"),
    /** order-flow-complete：重复物流单号（uk order_id+carrier_code+tracking_no） */
    SHIPMENT_TRACKING_DUPLICATE(409908, 409, "error.409908"),

    // ===== 410 =====
    ORDER_EXPIRED(410601, 410, "error.410601"),

    // ===== 422 =====
    FIELD_VALIDATION_FAILED(422601, 422, "error.422601"),
    CUSTOM_ITEM_NOT_REFUNDABLE(422602, 422, "error.422602"),
    REFUND_AMOUNT_EXCEEDED(422603, 422, "error.422603"),
    SKU_REQUIRED(422604, 422, "error.422604"),
    CURRENCY_NOT_SUPPORTED(422605, 422, "error.422605"),
    /** order-flow-complete：超量发货（∑分配 > order_line.qty） */
    SHIPMENT_QTY_EXCEEDED(422906, 422, "error.422906"),
    /** order-flow-complete：税率生效窗口重叠 */
    TAX_RULE_OVERLAP(422907, 422, "error.422907"),
    /** order-flow-complete：退款累计超额（refunded_amount + amount > total_amount） */
    REFUND_TOTAL_EXCEEDED(422908, 422, "error.422908"),

    // ===== 401（webhook 安全第 1 条） =====
    WEBHOOK_SIGNATURE_INVALID(401601, 401, "error.401601"),

    // ===== 502/504（BE-DIM-5 降级矩阵） =====
    STRIPE_UNAVAILABLE(502601, 502, "error.502601"),
    STRIPE_TIMEOUT(504601, 504, "error.504601");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** i18n message key（trading-messages bundle） */
    private final String messageKey;

    TradingErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
