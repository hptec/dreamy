package com.dreamy.error;

import lombok.Getter;

/**
 * site_builder 域错误码枚举（23 码，域段 8）。
 * 6 位码：HTTP(3) + 域段(1=8) + 序号(2)。
 * 权威来源：error-strategy.md site_builder 段 + site-builder-api.openapi.yml info 码表。
 */
@Getter
public enum SiteBuilderErrorCode {

    // ===== 404 =====
    HOME_SECTION_NOT_FOUND(404801, 404, "error.404801"),
    NAVIGATION_NOT_FOUND(404802, 404, "error.404802"),
    FOOTER_NOT_FOUND(404803, 404, "error.404803"),
    ANNOUNCEMENT_NOT_FOUND(404804, 404, "error.404804"),
    TAXONOMY_NOT_FOUND(404805, 404, "error.404805"),
    HOME_RELEASE_NOT_FOUND(404806, 404, "error.404806"),
    HOME_PREVIEW_TOKEN_INVALID(404807, 404, "error.404807"),

    // ===== 409 =====
    HOME_SECTION_SORT_CONFLICT(409801, 409, "error.409801"),
    NAVIGATION_ITEM_CYCLE_DETECTED(409802, 409, "error.409802"),
    FOOTER_COLUMN_SORT_CONFLICT(409803, 409, "error.409803"),
    ANNOUNCEMENT_TIME_WINDOW_CONFLICT(409804, 409, "error.409804"),
    NAVIGATION_VERSION_CONFLICT(409805, 409, "error.409805"),
    HOME_RELEASE_NO_CHANGES(409806, 409, "error.409806"),
    HOME_RELEASE_DRAFT_CHANGED(409807, 409, "error.409807"),

    // ===== 422 =====
    HOME_SECTION_DATA_JSON_INVALID(422801, 422, "error.422801"),
    HOME_SECTION_TYPE_INVALID(422802, 422, "error.422802"),
    FOOTER_COLUMN_REF_INVALID(422803, 422, "error.422803"),
    FOOTER_LINK_URL_INVALID(422804, 422, "error.422804"),
    ANNOUNCEMENT_TIME_WINDOW_INVALID(422805, 422, "error.422805"),
    MEGA_MENU_CONFIG_INVALID(422806, 422, "error.422806"),
    I18N_JSON_INVALID(422807, 422, "error.422807"),
    SECTION_TYPE_DATA_MISMATCH(422808, 422, "error.422808"),
    HOME_RELEASE_VALIDATION_FAILED(422809, 422, "error.422809"),

    // ===== 500 =====
    SITE_BUILDER_INTERNAL_ERROR(500801, 500, "error.500801"),

    // ===== 502 跨域调用 =====
    BANNER_SERVICE_UNAVAILABLE(502801, 502, "error.502801"),
    TAXONOMY_SERVICE_UNAVAILABLE(502802, 502, "error.502802"),
    WEDDING_SERVICE_UNAVAILABLE(502803, 502, "error.502803"),
    PRODUCT_SERVICE_UNAVAILABLE(502804, 502, "error.502804");

    private final int code;
    private final int httpStatus;
    private final String messageKey;

    SiteBuilderErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
