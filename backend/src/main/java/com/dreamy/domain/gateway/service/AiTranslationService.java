package com.dreamy.domain.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.dto.AiTranslationDtos.TranslateRequest;
import com.dreamy.dto.AiTranslationDtos.TranslateResult;
import com.dreamy.enums.GatewayType;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 翻译服务（瘦版，仅 translate 后端代理）。
 * 历史上曾包含翻译日志（ai_translation_log）和术语表注入（ai_translation_glossary），
 * 2026-06-17 简化方案移除这两个能力，仅保留外部 AI 网关代理调用。
 */
@Service
public class AiTranslationService {

    private static final Logger log = LoggerFactory.getLogger(AiTranslationService.class);

    /** 翻译成功状态码（沿用历史 1=success 约定，前端 TranslateResponse.status 仅展示用） */
    private static final int STATUS_SUCCESS = 1;

    private static final String SYSTEM_PROMPT_PREFIX =
            "You are a professional translator for a bridal e-commerce platform (wedding dresses, "
            + "gowns, and bridal accessories). Translate the user's text accurately while preserving "
            + "the elegant, refined tone appropriate for the bridal industry. Keep brand names and "
            + "product codes unchanged. Output only the translation, without explanations.";

    private final ExternalGatewayConfigMapper gatewayMapper;
    private final GatewayCryptoService crypto;
    private final GatewayHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiTranslationService(ExternalGatewayConfigMapper gatewayMapper,
                                GatewayCryptoService crypto,
                                GatewayHttpClient httpClient,
                                ObjectMapper objectMapper) {
        this.gatewayMapper = gatewayMapper;
        this.crypto = crypto;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** 翻译。失败抛 502/504（前端 toast 但允许继续保存）。 */
    public TranslateResult translate(TranslateRequest req) {
        if (req.sourceText() == null || req.sourceText().isBlank()) {
            throw new GatewayException(GatewayErrorCode.SOURCE_TEXT_EMPTY);
        }
        ExternalGatewayConfig gateway = loadEnabledAiGateway();
        String model = resolveModel(gateway, req.model());
        String systemPrompt = buildSystemPrompt(req);

        String apiKey = crypto.decrypt(gateway.getApiKeyEncrypted());
        long start = System.currentTimeMillis();
        try {
            String body = httpClient.chatCompletion(gateway.getBaseUrl(), apiKey, model,
                    systemPrompt, req.sourceText());
            long latency = System.currentTimeMillis() - start;
            String translated = httpClient.parseChatContent(body);
            if (translated == null || translated.isBlank()) {
                throw new GatewayException(GatewayErrorCode.AI_EMPTY_RESULT);
            }
            return new TranslateResult(translated, model, STATUS_SUCCESS, latency);
        } catch (GatewayHttpClient.GatewayCallException ex) {
            handleTranslateFailure(gateway, model, ex);
            return null; // unreachable
        }
    }

    private void handleTranslateFailure(ExternalGatewayConfig gateway, String model,
                                        GatewayHttpClient.GatewayCallException ex) {
        GatewayErrorCode code;
        switch (ex.getKind()) {
            case TIMEOUT -> code = GatewayErrorCode.AI_GATEWAY_TIMEOUT;
            case RATE_LIMITED -> code = GatewayErrorCode.AI_RATE_LIMITED;
            default -> code = GatewayErrorCode.AI_GATEWAY_ERROR;
        }
        log.warn("[AI-TRANSLATE] gateway={} model={} 翻译失败 kind={} code={}",
                gateway.getId(), model, ex.getKind(), code.getCode());
        throw new GatewayException(code);
    }

    private ExternalGatewayConfig loadEnabledAiGateway() {
        LambdaQueryWrapper<ExternalGatewayConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(ExternalGatewayConfig::getGatewayType, GatewayType.AI.getKey())
                .eq(ExternalGatewayConfig::getEnabled, true)
                .isNull(ExternalGatewayConfig::getDeletedAt)
                .orderByDesc(ExternalGatewayConfig::getUpdatedAt)
                .last("LIMIT 1");
        ExternalGatewayConfig gateway = gatewayMapper.selectOne(qw);
        if (gateway == null) {
            throw new GatewayException(GatewayErrorCode.NO_AI_GATEWAY_CONFIGURED);
        }
        return gateway;
    }

    private String resolveModel(ExternalGatewayConfig gateway, String requestModel) {
        if (requestModel != null && !requestModel.isBlank()) {
            List<String> models = parseModelList(gateway.getModelList());
            if (!models.isEmpty() && !models.contains(requestModel)) {
                throw new GatewayException(GatewayErrorCode.INVALID_MODEL);
            }
            return requestModel;
        }
        return gateway.getDefaultModel();
    }

    private String buildSystemPrompt(TranslateRequest req) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_PREFIX);
        if (req.customRequirement() != null && !req.customRequirement().isBlank()) {
            sb.append("\n\nAdditional requirement: ").append(req.customRequirement());
        }
        sb.append("\n\nTranslate from ").append(req.sourceLang())
                .append(" to ").append(req.targetLang()).append(".");
        return sb.toString();
    }

    private List<String> parseModelList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
