package com.dreamy.domain.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.aitranslation.entity.AiTranslationLog;
import com.dreamy.domain.aitranslation.repository.AiTranslationLogMapper;
import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.domain.gateway.service.GatewayCryptoService;
import com.dreamy.domain.gateway.service.GatewayHttpClient;
import com.dreamy.domain.glossary.entity.AiTranslationGlossary;
import com.dreamy.domain.glossary.repository.AiTranslationGlossaryMapper;
import com.dreamy.dto.AiTranslationDtos.TranslateRequest;
import com.dreamy.dto.AiTranslationDtos.TranslateResult;
import com.dreamy.dto.AiTranslationDtos.TranslationLogDto;
import com.dreamy.enums.AiTranslationStatus;
import com.dreamy.enums.GatewayType;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import huihao.page.Paginated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AI 翻译服务（translate 后端代理 + listLogs）。
 * L2 TRACE: i18n-backend-api-detail.md §2 / FUNC-008~013 / 决策2/6/10/14 /
 * EDGE-001/002/003/004/005/013/015/016/017/024。
 *
 * 决策2：后端代理，API Key 不暴露浏览器；
 * 决策6：system prompt 婚纱礼服领域锁定 + 用户自定义要求；
 * 决策10：翻译失败落 log（status 标记）后抛 502/504，前端 toast 但允许继续保存；
 * 决策14/EDGE-024：术语表注入，仅命中术语，上限 50 条，按 category 优先级截断。
 */
@Service
public class AiTranslationService {

    private static final Logger log = LoggerFactory.getLogger(AiTranslationService.class);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    /** EDGE-024：注入术语上限 50 条 */
    static final int MAX_GLOSSARY_TERMS = 50;
    /** 日志列表译文/原文截断展示长度 */
    private static final int PREVIEW_LEN = 200;
    /** category 优先级：廓形 > 领型 > 面料 > 工艺 > 其他（决策14） */
    private static final List<String> CATEGORY_PRIORITY =
            List.of("silhouette", "neckline", "fabric", "craft");

    private static final String SYSTEM_PROMPT_PREFIX =
            "You are a professional translator for a bridal e-commerce platform (wedding dresses, "
            + "gowns, and bridal accessories). Translate the user's text accurately while preserving "
            + "the elegant, refined tone appropriate for the bridal industry. Keep brand names and "
            + "product codes unchanged. Output only the translation, without explanations.";

    private final ExternalGatewayConfigMapper gatewayMapper;
    private final AiTranslationGlossaryMapper glossaryMapper;
    private final AiTranslationLogMapper logMapper;
    private final GatewayCryptoService crypto;
    private final GatewayHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiTranslationService(ExternalGatewayConfigMapper gatewayMapper,
                                AiTranslationGlossaryMapper glossaryMapper,
                                AiTranslationLogMapper logMapper,
                                GatewayCryptoService crypto,
                                GatewayHttpClient httpClient,
                                ObjectMapper objectMapper) {
        this.gatewayMapper = gatewayMapper;
        this.glossaryMapper = glossaryMapper;
        this.logMapper = logMapper;
        this.crypto = crypto;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    // ==================== 2.1 翻译请求 ====================

    /**
     * 翻译。失败时按决策10落 log + 抛 502/504（前端允许继续保存）。
     * 注意：不加 @Transactional——失败也必须落 log（独立持久化），事务回滚会丢日志。
     */
    public TranslateResult translate(TranslateRequest req) {
        // STEP-1 校验原文（@Valid 已校验非空/超长，此处兜底显式语义码 EDGE-002）
        if (req.sourceText() == null || req.sourceText().isBlank()) {
            throw new GatewayException(GatewayErrorCode.SOURCE_TEXT_EMPTY);
        }
        // STEP-2 读取启用 AI 网关（无 → 400301 NO_AI_GATEWAY_CONFIGURED，EDGE-001/013）
        ExternalGatewayConfig gateway = loadEnabledAiGateway();

        // STEP-3 确定模型：优先 request.model（需在 model_list 中，否则 400302），否则 default_model
        String model = resolveModel(gateway, req.model());

        // STEP-4 读取术语表 + 组装 system prompt（决策6/14 / EDGE-024）
        String systemPrompt = buildSystemPrompt(req);

        // STEP-5 调用外部网关（30s 超时），STEP-6 落 log，STEP-7 返回
        String apiKey = crypto.decrypt(gateway.getApiKeyEncrypted());
        long start = System.currentTimeMillis();
        try {
            String body = httpClient.chatCompletion(gateway.getBaseUrl(), apiKey, model,
                    systemPrompt, req.sourceText());
            long latency = System.currentTimeMillis() - start;
            String translated = httpClient.parseChatContent(body);
            String tokenUsage = httpClient.parseTokenUsage(body);
            if (translated == null || translated.isBlank()) {
                // EDGE-003：AI 返回空译文 → status=empty_result + 502302
                writeLog(gateway, model, req, null, AiTranslationStatus.EMPTY_RESULT,
                        "AI 返回空译文", (int) latency, tokenUsage);
                throw new GatewayException(GatewayErrorCode.AI_EMPTY_RESULT);
            }
            writeLog(gateway, model, req, translated, AiTranslationStatus.SUCCESS,
                    null, (int) latency, tokenUsage);
            return new TranslateResult(translated, model, AiTranslationStatus.SUCCESS.getKey(), latency);
        } catch (GatewayHttpClient.GatewayCallException ex) {
            long latency = System.currentTimeMillis() - start;
            handleTranslateFailure(gateway, model, req, ex, (int) latency); // 落 log 后抛
            return null; // unreachable
        }
    }

    /** 翻译失败分支：按失败类型落 log（status）后抛对应错误码（决策10 / EDGE-015/016/017）。 */
    private void handleTranslateFailure(ExternalGatewayConfig gateway, String model, TranslateRequest req,
                                        GatewayHttpClient.GatewayCallException ex, int latency) {
        AiTranslationStatus status;
        GatewayErrorCode code;
        String errMsg;
        switch (ex.getKind()) {
            case TIMEOUT -> {
                status = AiTranslationStatus.TIMEOUT;
                code = GatewayErrorCode.AI_GATEWAY_TIMEOUT;
                errMsg = "AI 网关调用超时";
            }
            case RATE_LIMITED -> {
                status = AiTranslationStatus.RATE_LIMITED;
                code = GatewayErrorCode.AI_RATE_LIMITED;
                errMsg = "AI 网关限流(429)";
            }
            default -> {
                // UNREACHABLE / CLIENT_ERROR / SERVER_ERROR / AUTH_FAILED → failed + 502301
                status = AiTranslationStatus.FAILED;
                code = GatewayErrorCode.AI_GATEWAY_ERROR;
                errMsg = ex.getMessage();
            }
        }
        writeLog(gateway, model, req, null, status, errMsg, latency, null);
        log.warn("[AI-TRANSLATE] gateway={} model={} 翻译失败 status={} code={}",
                gateway.getId(), model, status.getKey(), code.getCode());
        throw new GatewayException(code);
    }

    // ==================== 2.2 调用记录查询 ====================

    /** 分页日志（biz_type/biz_ref/status 过滤，按 created_at 降序，原文/译文截断展示）。 */
    public Paginated<TranslationLogDto> listLogs(String bizType, String bizRef, Integer status,
                                                 Integer page, Integer pageSize) {
        int p = page != null && page > 0 ? page : 1;
        int ps = pageSize != null && pageSize > 0 && pageSize <= 100 ? pageSize : 20;
        LambdaQueryWrapper<AiTranslationLog> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq(AiTranslationLog::getBizType, bizType);
        }
        if (bizRef != null && !bizRef.isBlank()) {
            qw.eq(AiTranslationLog::getBizRef, bizRef);
        }
        if (status != null) {
            qw.eq(AiTranslationLog::getStatus, status);
        }
        qw.orderByDesc(AiTranslationLog::getCreatedAt);
        Page<AiTranslationLog> result = logMapper.selectPage(new Page<>(p, ps), qw);
        List<TranslationLogDto> items = result.getRecords().stream().map(this::toLogDto).toList();
        Paginated<TranslationLogDto> paginated = new Paginated<>();
        paginated.setData(items);
        paginated.setTotalElements(result.getTotal());
        paginated.setPageNumber(p);
        paginated.setPageSize(ps);
        paginated.setNumberOfElements(items.size());
        paginated.setTotalPages(ps > 0 ? (int) Math.ceil((double) result.getTotal() / ps) : 0);
        return paginated;
    }

    // ==================== 内部辅助 ====================

    /** 读取最近启用的 AI 网关（enabled=true，按 updated_at 降序取一条，排除已删除）。无 → 400301。 */
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

    /** request.model 须在 model_list 中（否则 400302）；未指定用 default_model。 */
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

    /**
     * 组装 system prompt：固定前缀 + 术语注入（仅命中 source_text 的 term_en，上限 50，
     * 超出按 category 优先级截断）+ custom_requirement（决策6/14 / EDGE-024）。
     */
    String buildSystemPrompt(TranslateRequest req) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_PREFIX);
        List<AiTranslationGlossary> hits = selectGlossaryHits(req.sourceText(), req.targetLang());
        if (!hits.isEmpty()) {
            sb.append("\n\nUse these standard bridal terms (English -> target):");
            for (AiTranslationGlossary term : hits) {
                String target = pickTermTranslation(term, req.targetLang());
                if (target != null && !target.isBlank()) {
                    sb.append("\n- ").append(term.getTermEn()).append(" -> ").append(target);
                }
            }
        }
        if (req.customRequirement() != null && !req.customRequirement().isBlank()) {
            sb.append("\n\nAdditional requirement: ").append(req.customRequirement());
        }
        sb.append("\n\nTranslate from ").append(req.sourceLang())
                .append(" to ").append(req.targetLang()).append(".");
        return sb.toString();
    }

    /** 查启用术语，仅保留 term_en 命中 source_text 的，按 category 优先级截断至 50 条。 */
    private List<AiTranslationGlossary> selectGlossaryHits(String sourceText, String targetLang) {
        LambdaQueryWrapper<AiTranslationGlossary> qw = new LambdaQueryWrapper<>();
        qw.eq(AiTranslationGlossary::getEnabled, true);
        List<AiTranslationGlossary> all = glossaryMapper.selectList(qw);
        String lowerSource = sourceText.toLowerCase();
        List<AiTranslationGlossary> hits = new ArrayList<>();
        for (AiTranslationGlossary term : all) {
            if (term.getTermEn() != null && lowerSource.contains(term.getTermEn().toLowerCase())) {
                hits.add(term);
            }
        }
        if (hits.size() > MAX_GLOSSARY_TERMS) {
            hits.sort(Comparator.comparingInt(t -> categoryRank(t.getCategory())));
            return hits.subList(0, MAX_GLOSSARY_TERMS);
        }
        return hits;
    }

    private int categoryRank(String category) {
        if (category == null) {
            return CATEGORY_PRIORITY.size();
        }
        int idx = CATEGORY_PRIORITY.indexOf(category);
        return idx >= 0 ? idx : CATEGORY_PRIORITY.size();
    }

    private String pickTermTranslation(AiTranslationGlossary term, String targetLang) {
        if ("fr".equalsIgnoreCase(targetLang)) {
            return term.getTermFr();
        }
        if ("es".equalsIgnoreCase(targetLang)) {
            return term.getTermEs();
        }
        return null; // 目标 en 或其它无对应译法
    }

    /** 落翻译 log（成功/失败统一入口，决策10）。 */
    private void writeLog(ExternalGatewayConfig gateway, String model, TranslateRequest req,
                          String translated, AiTranslationStatus status, String errorMessage,
                          Integer latencyMs, String tokenUsage) {
        AiTranslationLog logRow = new AiTranslationLog();
        logRow.setGatewayConfigId(gateway.getId());
        logRow.setModel(model);
        logRow.setSourceLang(req.sourceLang());
        logRow.setTargetLang(req.targetLang());
        logRow.setSourceText(req.sourceText());
        logRow.setTranslatedText(translated);
        logRow.setCustomRequirement(req.customRequirement());
        logRow.setBizType(req.bizType());
        logRow.setBizRef(req.bizRef());
        logRow.setStatus(status.getKey());
        logRow.setErrorMessage(truncate(errorMessage, 512));
        logRow.setLatencyMs(latencyMs);
        logRow.setTokenUsage(tokenUsage);
        logRow.setOperatorId(currentOperatorId());
        logMapper.insert(logRow);
    }

    private Long currentOperatorId() {
        AuthPrincipal p = AuthContext.get();
        if (p == null || p.subject() == null) {
            return null;
        }
        try {
            return Long.parseLong(p.subject());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private TranslationLogDto toLogDto(AiTranslationLog e) {
        return new TranslationLogDto(
                e.getId(),
                e.getGatewayConfigId(),
                e.getModel(),
                e.getSourceLang(),
                e.getTargetLang(),
                truncate(e.getSourceText(), PREVIEW_LEN),
                truncate(e.getTranslatedText(), PREVIEW_LEN),
                e.getCustomRequirement(),
                e.getBizType(),
                e.getBizRef(),
                e.getStatus(),
                e.getErrorMessage(),
                e.getLatencyMs(),
                e.getOperatorId(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().format(ISO));
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

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
