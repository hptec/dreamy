package com.dreamy.domain.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.aitranslation.consts.AiTranslationLogDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 翻译调用日志实体（ai_translation_log 表）。
 * L2 TRACE: i18n-backend-data-detail.md §1.2 / 决策6/10 / FUNC-008~013 / EDGE-016/017。
 * 约束：status 5 值枚举、翻译失败允许继续、90 天后自动清理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "ai_translation_log", comment = "AI翻译调用记录")
@TableName(value = "ai_translation_log", autoResultMap = true)
public class AiTranslationLog extends LongAuditableEntity {

    @Column(name = AiTranslationLogDBConst.GATEWAY_CONFIG_ID, definition = "bigint NOT NULL COMMENT '关联网关配置'")
    private Long gatewayConfigId;

    @Column(name = AiTranslationLogDBConst.MODEL, definition = "varchar(128) NOT NULL COMMENT '实际调用模型'")
    private String model;

    @Column(name = AiTranslationLogDBConst.SOURCE_LANG, definition = "varchar(8) NOT NULL COMMENT '源语言'")
    private String sourceLang;

    @Column(name = AiTranslationLogDBConst.TARGET_LANG, definition = "varchar(8) NOT NULL COMMENT '目标语言'")
    private String targetLang;

    @Column(name = AiTranslationLogDBConst.SOURCE_TEXT, definition = "text NOT NULL COMMENT '原文'")
    private String sourceText;

    @Column(name = AiTranslationLogDBConst.TRANSLATED_TEXT, definition = "text DEFAULT NULL COMMENT '译文(失败时空)'")
    private String translatedText;

    @Column(name = AiTranslationLogDBConst.CUSTOM_REQUIREMENT, definition = "text DEFAULT NULL COMMENT '自定义要求'")
    private String customRequirement;

    @Column(name = AiTranslationLogDBConst.BIZ_TYPE, definition = "varchar(32) DEFAULT NULL COMMENT '业务来源类型'")
    private String bizType;

    @Column(name = AiTranslationLogDBConst.BIZ_REF, definition = "varchar(64) DEFAULT NULL COMMENT '业务来源标识'")
    private String bizRef;

    /** status: success(1)/failed(2)/timeout(3)/empty_result(4)/rate_limited(5) */
    @Column(name = AiTranslationLogDBConst.STATUS, definition = "tinyint NOT NULL COMMENT 'success(1)/failed(2)/timeout(3)/empty_result(4)/rate_limited(5)'")
    private Integer status;

    @Column(name = AiTranslationLogDBConst.ERROR_MESSAGE, definition = "varchar(512) DEFAULT NULL COMMENT '失败原因'")
    private String errorMessage;

    @Column(name = AiTranslationLogDBConst.LATENCY_MS, definition = "int DEFAULT NULL COMMENT '调用耗时ms'")
    private Integer latencyMs;

    /** JSON 字段 token 消耗 */
    @Column(name = AiTranslationLogDBConst.TOKEN_USAGE, definition = "json DEFAULT NULL COMMENT 'token消耗'")
    private String tokenUsage;

    @Column(name = AiTranslationLogDBConst.OPERATOR_ID, definition = "bigint DEFAULT NULL COMMENT '操作人'")
    private Long operatorId;
}
