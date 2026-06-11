package com.dreamy.showroom.error;

import lombok.Getter;

/**
 * showroom 域错误码枚举（11 码，域段 1）。权威来源 error-strategy.md showroom 段 +
 * showroom-api.openapi.yml info 码表。6 位码：HTTP(3) + 域段(1=1) + 序号(2)。
 * identity 复用码（40100/50000/50001）仍走 identity ErrorCode；商品引用校验透传 catalog 404501
 * （CatalogException，ShowroomExceptionHandler 一并映射，review/trading 同先例）。
 * 401101 / 403102 由 StoreJwtFilter 过滤器层产出（识别码同号段，不经本枚举）。
 */
@Getter
public enum ShowroomErrorCode {

    // ===== 401 =====
    GUEST_TOKEN_INVALID(401101, 401, "error.401101"),

    // ===== 403 =====
    NOT_SHOWROOM_OWNER(403101, 403, "error.403101"),
    GUEST_SCOPE_EXCEEDED(403102, 403, "error.403102"),

    // ===== 404 =====
    SHOWROOM_NOT_FOUND(404101, 404, "error.404101"),
    SHOWROOM_ITEM_NOT_FOUND(404102, 404, "error.404102"),
    SHOWROOM_MEMBER_NOT_FOUND(404103, 404, "error.404103"),

    // ===== 409 =====
    NICKNAME_TAKEN(409101, 409, "error.409101"),
    ITEM_ALREADY_EXISTS(409102, 409, "error.409102"),
    MEMBER_STATE_INVALID(409103, 409, "error.409103"),

    // ===== 410 =====
    INVITE_TOKEN_REVOKED(410101, 410, "error.410101"),

    // ===== 422 =====
    FIELD_VALIDATION_FAILED(422101, 422, "error.422101");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** i18n message key（showroom-messages bundle） */
    private final String messageKey;

    ShowroomErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
