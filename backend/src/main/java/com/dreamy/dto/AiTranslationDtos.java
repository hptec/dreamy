package com.dreamy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 翻译域 DTO 集（ai-translation-api.openapi.yml）。
 * L2 TRACE: i18n-backend-api-detail.md §2 / FUNC-008~013 / 决策2/6/10/14 / EDGE-001~005/015/016/017。
 */
public final class AiTranslationDtos {

    private AiTranslationDtos() {
    }

    /**
     * 翻译请求（TranslateRequest）。后端代理调用，API Key 不暴露给浏览器（决策2）。
     * source_text ≤ 10000（EDGE-004 截断由 max_tokens 兜底，超长 422301 SOURCE_TEXT_TOO_LONG）；
     * custom_requirement ≤ 500（EDGE-005）。
     */
    public record TranslateRequest(
            @NotBlank @Size(max = 8) String sourceLang,
            @NotBlank @Size(max = 8) String targetLang,
            @NotBlank @Size(max = 10000) String sourceText,
            @Size(max = 500) String customRequirement,
            @Size(max = 128) String model,
            @NotBlank @Size(max = 32) String bizType,
            @NotBlank @Size(max = 64) String bizRef) {
    }

    /**
     * 翻译结果（TranslateResult）。失败时 translatedText 为空，status 标记失败类型，
     * 由 ExceptionHandler 转 502/504（决策10：前端 toast 但允许继续保存）。
     */
    public record TranslateResult(
            String translatedText,
            String model,
            Integer status,
            Long latencyMs) {
    }

    /**
     * 翻译日志条目（AiTranslationLogItem）。source_text/translated_text 超长截断展示前 200 字符。
     */
    public record TranslationLogDto(
            Long id,
            Long gatewayConfigId,
            String model,
            String sourceLang,
            String targetLang,
            String sourceText,
            String translatedText,
            String customRequirement,
            String bizType,
            String bizRef,
            Integer status,
            String errorMessage,
            Integer latencyMs,
            Long operatorId,
            String createdAt) {
    }
}
