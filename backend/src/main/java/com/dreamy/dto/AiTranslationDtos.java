package com.dreamy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 翻译域 DTO 集（ai-translation-api.openapi.yml）。
 * 当前契约：ai-translation-api.openapi.yml v2；翻译日志、业务溯源字段和 Glossary 均已退役。
 */
public final class AiTranslationDtos {

    private AiTranslationDtos() {
    }

    /**
     * 翻译请求（TranslateRequest）。后端代理调用，API Key 不暴露给浏览器（决策2）。
     * Bean Validation 字段错误统一由 GatewayExceptionHandler 映射为 422201；
     * source_text ≤ 10000，custom_requirement ≤ 500。
     */
    public record TranslateRequest(
            @NotBlank @Size(max = 8) String sourceLang,
            @NotBlank @Size(max = 8) String targetLang,
            @NotBlank @Size(max = 10000) String sourceText,
            @Size(max = 500) String customRequirement,
            @Size(max = 128) String model) {
    }

    /**
     * 翻译结果（TranslateResult）。失败时由 ExceptionHandler 转 502/504，前端 toast 但允许继续保存。
     */
    public record TranslateResult(
            String translatedText,
            String model,
            Integer status,
            Long latencyMs) {
    }
}
