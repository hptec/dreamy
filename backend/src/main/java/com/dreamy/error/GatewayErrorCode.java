package com.dreamy.error;

import lombok.Getter;

/**
 * 网关/AI翻译领域错误码。
 * 码格式：高3位对应 HTTP 状态。admin 端固定中文。
 */
@Getter
public enum GatewayErrorCode {

    // ===== 404 不存在 =====
    GATEWAY_NOT_FOUND(404201, 404, "网关配置不存在"),

    // ===== 409 冲突 =====
    GATEWAY_NAME_EXISTS(409201, 409, "同类型网关下配置名称已存在"),
    GATEWAY_EDIT_CONFLICT(409202, 409, "网关配置已被他人修改，请刷新重试"),

    // ===== 422 参数错误 =====
    GATEWAY_VALIDATION(422201, 422, "请求参数校验失败"),
    INVALID_API_KEY_FORMAT(422202, 422, "API Key 格式非法"),
    INVALID_BASE_URL(422203, 422, "网关 URL 协议非法"),

    // ===== 403 业务禁止 =====
    GATEWAY_DISABLED(403201, 403, "网关配置已禁用"),
    NO_AI_GATEWAY_CONFIGURED(403202, 403, "未配置可用的 AI 网关"),

    // ===== 501 不支持 =====
    GATEWAY_TYPE_NOT_TESTABLE(501201, 501, "该网关类型暂不支持测试连接"),

    // ===== 400 业务请求错误（sync-models / translate）=====
    NON_AI_GATEWAY_NO_SYNC(400201, 400, "仅 AI 网关支持模型同步"),
    INVALID_MODEL(400302, 400, "请求的模型不在可用列表中"),

    // ===== 502 外部依赖错误 =====
    GATEWAY_UNREACHABLE(502201, 502, "网关不可达"),
    GATEWAY_AUTH_FAILED(502202, 502, "网关鉴权失败"),
    AI_GATEWAY_ERROR(502301, 502, "AI 网关返回错误"),
    AI_EMPTY_RESULT(502302, 502, "AI 返回空译文"),

    // ===== 504 超时 =====
    GATEWAY_TIMEOUT(504201, 504, "网关连接超时"),
    AI_GATEWAY_TIMEOUT(504301, 504, "AI 网关调用超时"),

    // ===== 429 限流 =====
    AI_RATE_LIMITED(429301, 429, "AI 网关限流，请稍后重试");

    private final Integer code;
    private final Integer httpStatus;
    private final String message;

    GatewayErrorCode(Integer code, Integer httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
