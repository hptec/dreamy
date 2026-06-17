package com.dreamy.domain.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.gateway.consts.GatewayConfigDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 外部网关配置实体（external_gateway_config 表）。
 * L2 TRACE: i18n-backend-data-detail.md §1.1 / 决策1/5 / FUNC-004~007/021。
 * 约束：uk_type_name 唯一、consecutive_failures 降级计数、model_list JSON 自动映射。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "external_gateway_config", comment = "外部网关配置")
@TableName(value = "external_gateway_config", autoResultMap = true)
public class ExternalGatewayConfig extends LongAuditableEntity {

    @Column(name = GatewayConfigDBConst.GATEWAY_TYPE, definition = "tinyint NOT NULL COMMENT '网关类型：AI(1)/LOGISTICS(2)/PAYMENT(3)'")
    private Integer gatewayType;

    @Column(name = "name", definition = "varchar(64) NOT NULL COMMENT '配置名称'")
    private String name;

    @Column(name = GatewayConfigDBConst.PROTOCOL, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '协议：openai(1)'")
    private Integer protocol;

    @Column(name = GatewayConfigDBConst.BASE_URL, definition = "varchar(255) NOT NULL COMMENT '网关地址'")
    private String baseUrl;

    /** API Key 密文（AES-256-GCM，IV+密文 base64，v1: 前缀版本标记） */
    @Column(name = GatewayConfigDBConst.API_KEY_ENCRYPTED, definition = "varchar(512) NOT NULL COMMENT 'API Key密文(AES-256-GCM, IV+密文 base64)'")
    private String apiKeyEncrypted;

    @Column(name = GatewayConfigDBConst.DEFAULT_MODEL, definition = "varchar(128) DEFAULT NULL COMMENT '全局默认模型'")
    private String defaultModel;

    /** JSON 字段（MyBatis-Plus 自动 TypeHandler，autoResultMap=true 已开启） */
    @Column(name = GatewayConfigDBConst.MODEL_LIST, definition = "json DEFAULT NULL COMMENT '可用模型列表缓存'")
    private String modelList;

    @Column(name = GatewayConfigDBConst.MODELS_SYNCED_AT, definition = "datetime DEFAULT NULL COMMENT '上次模型同步时间'")
    private LocalDateTime modelsSyncedAt;

    @Column(name = GatewayConfigDBConst.MODEL_REFRESH_STRATEGY, definition = "tinyint DEFAULT 1 COMMENT '刷新策略：manual(1)/scheduled(2)'")
    private Integer modelRefreshStrategy;

    @Column(name = GatewayConfigDBConst.MODEL_REFRESH_INTERVAL_MIN, definition = "int DEFAULT NULL COMMENT '定时刷新间隔(分钟)'")
    private Integer modelRefreshIntervalMin;

    /** L2 细化字段：连续失败 3 次降级 manual（决策5/EDGE-014） */
    @Column(name = GatewayConfigDBConst.CONSECUTIVE_FAILURES, definition = "int NOT NULL DEFAULT 0 COMMENT '模型同步连续失败次数(L2细化字段, 决策5降级计数)'")
    private Integer consecutiveFailures;

    @Column(name = GatewayConfigDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用'")
    private Boolean enabled;

    @Column(name = GatewayConfigDBConst.EXTRA_CONFIG, definition = "json DEFAULT NULL COMMENT '协议扩展配置'")
    private String extraConfig;

    @Column(name = GatewayConfigDBConst.DELETED_AT, definition = "datetime DEFAULT NULL COMMENT '逻辑删除时间'")
    private LocalDateTime deletedAt;
}
