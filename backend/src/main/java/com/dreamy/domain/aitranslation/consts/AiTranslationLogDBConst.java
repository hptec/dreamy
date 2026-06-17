package com.dreamy.domain.aitranslation.consts;

/**
 * ai_translation_log 表字段常量。
 * L2 TRACE: i18n-backend-data-detail.md AiTranslationLog DDL。
 */
public final class AiTranslationLogDBConst {
    public static final String GATEWAY_CONFIG_ID = "gateway_config_id";
    public static final String MODEL = "model";
    public static final String SOURCE_LANG = "source_lang";
    public static final String TARGET_LANG = "target_lang";
    public static final String SOURCE_TEXT = "source_text";
    public static final String TRANSLATED_TEXT = "translated_text";
    public static final String CUSTOM_REQUIREMENT = "custom_requirement";
    public static final String BIZ_TYPE = "biz_type";
    public static final String BIZ_REF = "biz_ref";
    public static final String STATUS = "status";
    public static final String ERROR_MESSAGE = "error_message";
    public static final String LATENCY_MS = "latency_ms";
    public static final String TOKEN_USAGE = "token_usage";
    public static final String OPERATOR_ID = "operator_id";

    private AiTranslationLogDBConst() {}
}
