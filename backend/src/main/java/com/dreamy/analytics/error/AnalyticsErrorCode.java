package com.dreamy.analytics.error;

import lombok.Getter;

/**
 * analytics 域错误码枚举（3 码，域段 0，admin-only 固定中文文案）。
 * 权威来源 error-strategy.md analytics 段 + analytics-api.openapi.yml info 码表。
 * identity 复用码（40100/40300/50000/50001）仍走 identity ErrorCode。
 * 502001/504001 仅当 GA4 降级兜底链自身失效时触达（DEC-ANA-5 ⑤；常规降级走 200+source_status=unavailable）。
 */
@Getter
public enum AnalyticsErrorCode {

    // ===== 422 =====
    INVALID_RANGE(422001, 422, "时间范围参数非法"),

    // ===== 502 / 504 =====
    GA4_UNAVAILABLE(502001, 502, "流量数据服务不可用"),
    GA4_TIMEOUT(504001, 504, "流量数据服务超时");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** admin 端固定中文文案（本域无消费端，无三语 bundle） */
    private final String messageZh;

    AnalyticsErrorCode(int code, int httpStatus, String messageZh) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageZh = messageZh;
    }
}
