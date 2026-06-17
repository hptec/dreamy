package com.dreamy.domain.gateway.consts;

/**
 * external_gateway_config 表字段常量。
 * L2 TRACE: i18n-backend-data-detail.md ExternalGatewayConfig DDL。
 */
public final class GatewayConfigDBConst {
    public static final String GATEWAY_TYPE = "gateway_type";
    public static final String PROTOCOL = "protocol";
    public static final String BASE_URL = "base_url";
    public static final String API_KEY_ENCRYPTED = "api_key_encrypted";
    public static final String DEFAULT_MODEL = "default_model";
    public static final String MODEL_LIST = "model_list";
    public static final String MODELS_SYNCED_AT = "models_synced_at";
    public static final String MODEL_REFRESH_STRATEGY = "model_refresh_strategy";
    public static final String MODEL_REFRESH_INTERVAL_MIN = "model_refresh_interval_min";
    public static final String CONSECUTIVE_FAILURES = "consecutive_failures";
    public static final String EXTRA_CONFIG = "extra_config";
    public static final String ENABLED = "enabled";
    public static final String DELETED_AT = "deleted_at";

    private GatewayConfigDBConst() {}{}
}
