package com.dreamy.domain.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.dto.GatewayDtos.GatewayConfigDto;
import com.dreamy.dto.GatewayDtos.GatewayConfigUpsert;
import com.dreamy.dto.GatewayDtos.GatewayTestResult;
import com.dreamy.enums.GatewayType;
import com.dreamy.enums.ModelRefreshStrategy;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import huihao.page.Paginated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 外部网关配置服务（CRUD + testConnection + syncModels）。
 * L2 TRACE: i18n-backend-api-detail.md §1 / FUNC-004~007/021 / 决策1/5/14 /
 * EDGE-006/007/008/012/013/014/023 / gateway_degradation.model_sync_failure（决策5）。
 */
@Service
public class GatewayConfigService {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigService.class);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    /** 决策5：连续失败 >= 3 次降级 manual + 禁用 */
    static final int DEGRADE_THRESHOLD = 3;

    private final ExternalGatewayConfigMapper mapper;
    private final GatewayCryptoService crypto;
    private final GatewayHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GatewayConfigService(ExternalGatewayConfigMapper mapper,
                                GatewayCryptoService crypto,
                                GatewayHttpClient httpClient,
                                ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    // ==================== 1.2 列表查询 ====================

    /** 分页列表（gateway_type 可选过滤，按 updated_at 降序，API Key 掩码，过滤删除）。 */
    public Paginated<GatewayConfigDto> list(Integer gatewayType, Integer page, Integer pageSize) {
        int p = page != null && page > 0 ? page : 1;
        int ps = pageSize != null && pageSize > 0 && pageSize <= 100 ? pageSize : 20;
        LambdaQueryWrapper<ExternalGatewayConfig> qw = new LambdaQueryWrapper<>();
        if (gatewayType != null) {
            qw.eq(ExternalGatewayConfig::getGatewayType, gatewayType);
        }
        qw.isNull(ExternalGatewayConfig::getDeletedAt);
        qw.orderByDesc(ExternalGatewayConfig::getUpdatedAt);
        Page<ExternalGatewayConfig> result = mapper.selectPage(new Page<>(p, ps), qw);
        List<GatewayConfigDto> items = result.getRecords().stream().map(this::toDto).toList();
        Paginated<GatewayConfigDto> paginated = new Paginated<>();
        paginated.setData(items);
        paginated.setTotalElements(result.getTotal());
        paginated.setPageNumber(p);
        paginated.setPageSize(ps);
        paginated.setNumberOfElements(items.size());
        paginated.setTotalPages(ps > 0 ? (int) Math.ceil((double) result.getTotal() / ps) : 0);
        return paginated;
    }

    // ==================== 1.3 详情查询 ====================

    /** 主键查询，不存在 → 404201。 */
    public GatewayConfigDto getById(Long id) {
        return toDto(loadOrThrow(id));
    }

    // ==================== 1.1 创建 ====================

    @Transactional
    public GatewayConfigDto create(GatewayConfigUpsert req) {
        validateUpsert(req, true);
        ensureNameUnique(req.gatewayType(), req.name(), null);
        ExternalGatewayConfig entity = new ExternalGatewayConfig();
        entity.setGatewayType(req.gatewayType());
        entity.setName(req.name());
        entity.setProtocol(req.protocol());
        entity.setBaseUrl(req.baseUrl());
        // 创建场景禁止掩码（EDGE-010 仅响应掩码，入参必须明文）
        if (crypto.isMasked(req.apiKey())) {
            throw new GatewayException(GatewayErrorCode.INVALID_API_KEY_FORMAT);
        }
        entity.setApiKeyEncrypted(crypto.encrypt(req.apiKey()));
        entity.setDefaultModel(req.defaultModel());
        entity.setModelRefreshStrategy(req.modelRefreshStrategy() != null
                ? req.modelRefreshStrategy() : ModelRefreshStrategy.MANUAL.getKey());
        entity.setModelRefreshIntervalMin(req.modelRefreshIntervalMin());
        entity.setConsecutiveFailures(0);
        entity.setEnabled(req.enabled());
        mapper.insert(entity);
        // 自动模型发现（仅 AI 网关，失败不影响保存，EDGE-014）
        if (GatewayType.AI.getKey().equals(entity.getGatewayType())) {
            tryRefreshModels(entity);
        }
        return toDto(mapper.selectById(entity.getId()));
    }

    // ==================== 1.4 更新 ====================

    @Transactional
    public GatewayConfigDto update(Long id, GatewayConfigUpsert req) {
        ExternalGatewayConfig entity = loadOrThrow(id);
        validateUpsert(req, false);
        // 乐观锁：updated_at 比对（EDGE-012）
        if (req.updatedAt() != null && !req.updatedAt().isBlank()) {
            String dbUpdatedAt = entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(ISO);
            if (dbUpdatedAt != null && !normalizeTs(req.updatedAt()).equals(normalizeTs(dbUpdatedAt))) {
                throw new GatewayException(GatewayErrorCode.GATEWAY_EDIT_CONFLICT);
            }
        }
        ensureNameUnique(req.gatewayType(), req.name(), id);
        boolean baseUrlChanged = !req.baseUrl().equals(entity.getBaseUrl());
        boolean apiKeyChanged = false;
        // 掩码 → 保持原密文；明文 → 重新加密
        if (!crypto.isMasked(req.apiKey())) {
            entity.setApiKeyEncrypted(crypto.encrypt(req.apiKey()));
            apiKeyChanged = true;
        }
        entity.setGatewayType(req.gatewayType());
        entity.setName(req.name());
        entity.setProtocol(req.protocol());
        entity.setBaseUrl(req.baseUrl());
        entity.setDefaultModel(req.defaultModel());
        if (req.modelRefreshStrategy() != null) {
            entity.setModelRefreshStrategy(req.modelRefreshStrategy());
        }
        entity.setModelRefreshIntervalMin(req.modelRefreshIntervalMin());
        entity.setEnabled(req.enabled());
        mapper.updateById(entity);
        // base_url 或 api_key 变更且为 AI 网关 → 重新拉取模型（失败不阻断，EDGE-014）
        if ((baseUrlChanged || apiKeyChanged) && GatewayType.AI.getKey().equals(entity.getGatewayType())) {
            tryRefreshModels(entity);
        }
        return toDto(mapper.selectById(id));
    }

    // ==================== 1.5 删除（逻辑删除）====================

    @Transactional
    public void delete(Long id) {
        ExternalGatewayConfig entity = loadOrThrow(id);
        // 逻辑删除：设置 deleted_at = now()，保留被翻译日志引用的记录
        ExternalGatewayConfig patch = new ExternalGatewayConfig();
        patch.setId(id);
        patch.setDeletedAt(LocalDateTime.now());
        mapper.updateById(patch);
    }

    // ==================== 1.6 手动同步模型 ====================

    /**
     * 手动同步模型。非 AI 网关 → 400201；调用 /v1/models 失败 → 502201/502202/504201（抛异常）。
     * 成功更新 model_list + models_synced_at + 清零 consecutive_failures。
     */
    @Transactional
    public GatewayConfigDto syncModels(Long id) {
        ExternalGatewayConfig entity = loadOrThrow(id);
        if (!GatewayType.AI.getKey().equals(entity.getGatewayType())) {
            throw new GatewayException(GatewayErrorCode.NON_AI_GATEWAY_NO_SYNC);
        }
        String apiKey = crypto.decrypt(entity.getApiKeyEncrypted());
        try {
            List<String> models = httpClient.listModels(entity.getBaseUrl(), apiKey);
            applySyncSuccess(entity, models);
        } catch (GatewayHttpClient.GatewayCallException ex) {
            applySyncFailure(entity);
            throw mapCallException(ex, true);
        }
        return toDto(mapper.selectById(id));
    }

    // ==================== 1.7 测试连接 ====================

    /**
     * 测试连接。非 AI 网关 → 501（GATEWAY_TYPE_NOT_TESTABLE）。
     * 调用 /v1/models（超时 10s），成功失败均封装 GatewayTestResult 返回（HTTP 200，§1.7 / EDGE-023）。
     * 不落库、不修改已保存配置。
     */
    public GatewayTestResult testConnection(Long id) {
        ExternalGatewayConfig entity = loadOrThrow(id);
        if (!GatewayType.AI.getKey().equals(entity.getGatewayType())) {
            throw new GatewayException(GatewayErrorCode.GATEWAY_TYPE_NOT_TESTABLE);
        }
        String apiKey = crypto.decrypt(entity.getApiKeyEncrypted());
        long start = System.currentTimeMillis();
        try {
            List<String> models = httpClient.listModels(entity.getBaseUrl(), apiKey);
            long latency = System.currentTimeMillis() - start;
            return new GatewayTestResult(true, models.size(), null, null, latency);
        } catch (GatewayHttpClient.GatewayCallException ex) {
            long latency = System.currentTimeMillis() - start;
            GatewayErrorCode code = switch (ex.getKind()) {
                case AUTH_FAILED -> GatewayErrorCode.GATEWAY_AUTH_FAILED;
                case TIMEOUT -> GatewayErrorCode.GATEWAY_TIMEOUT;
                default -> GatewayErrorCode.GATEWAY_UNREACHABLE;
            };
            return new GatewayTestResult(false, null, code.getCode(), code.getMessage(), latency);
        }
    }

    // ==================== 内部辅助 ====================

    /** 创建/更新后自动刷新模型（失败仅记 WARN + 降级计数，不抛异常，EDGE-014）。 */
    private void tryRefreshModels(ExternalGatewayConfig entity) {
        String apiKey = crypto.decrypt(entity.getApiKeyEncrypted());
        try {
            List<String> models = httpClient.listModels(entity.getBaseUrl(), apiKey);
            applySyncSuccess(entity, models);
        } catch (GatewayHttpClient.GatewayCallException ex) {
            log.warn("[GATEWAY] id={} 自动模型拉取失败 kind={}，配置已保存，model_list 保留",
                    entity.getId(), ex.getKind());
            applySyncFailure(entity);
        }
    }

    /** 同步成功：更新 model_list + models_synced_at，清零 consecutive_failures（决策5）。 */
    private void applySyncSuccess(ExternalGatewayConfig entity, List<String> models) {
        ExternalGatewayConfig patch = new ExternalGatewayConfig();
        patch.setId(entity.getId());
        patch.setModelList(toJson(models));
        patch.setModelsSyncedAt(LocalDateTime.now());
        patch.setConsecutiveFailures(0);
        mapper.updateById(patch);
        log.info("[GATEWAY] id={} 模型同步成功 count={}", entity.getId(), models.size());
    }

    /**
     * 同步失败：consecutive_failures += 1，models_synced_at 不更新，保留旧 model_list（决策5）。
     * 连续失败 >= 3 且 strategy=scheduled → 降级 manual + enabled=0（gateway_degradation）。
     */
    private void applySyncFailure(ExternalGatewayConfig entity) {
        int failures = (entity.getConsecutiveFailures() == null ? 0 : entity.getConsecutiveFailures()) + 1;
        ExternalGatewayConfig patch = new ExternalGatewayConfig();
        patch.setId(entity.getId());
        patch.setConsecutiveFailures(failures);
        if (failures >= DEGRADE_THRESHOLD
                && ModelRefreshStrategy.SCHEDULED.getKey().equals(entity.getModelRefreshStrategy())) {
            patch.setModelRefreshStrategy(ModelRefreshStrategy.MANUAL.getKey());
            patch.setEnabled(false);
            log.error("[GATEWAY] id={} 连续同步失败 {} 次，降级 manual + 自动禁用（决策5）",
                    entity.getId(), failures);
        } else {
            log.warn("[GATEWAY] id={} 模型同步失败 consecutive_failures={}", entity.getId(), failures);
        }
        mapper.updateById(patch);
        entity.setConsecutiveFailures(failures);
    }

    /** 调用异常 → 错误码映射（sync/translate 共用 gateway 域码）。 */
    private GatewayException mapCallException(GatewayHttpClient.GatewayCallException ex, boolean syncContext) {
        return switch (ex.getKind()) {
            case AUTH_FAILED -> new GatewayException(GatewayErrorCode.GATEWAY_AUTH_FAILED);
            case TIMEOUT -> new GatewayException(GatewayErrorCode.GATEWAY_TIMEOUT);
            default -> new GatewayException(GatewayErrorCode.GATEWAY_UNREACHABLE);
        };
    }

    private ExternalGatewayConfig loadOrThrow(Long id) {
        ExternalGatewayConfig entity = id == null ? null : mapper.selectById(id);
        if (entity == null || entity.getDeletedAt() != null) {
            throw new GatewayException(GatewayErrorCode.GATEWAY_NOT_FOUND);
        }
        return entity;
    }

    /** 字段校验：base_url 协议（EDGE-007）、strategy=2 时 interval 必填、enum 合法性（EDGE-006）。 */
    private void validateUpsert(GatewayConfigUpsert req, boolean create) {
        if (GatewayType.of(req.gatewayType()) == null) {
            throw GatewayException.fieldValidation(GatewayErrorCode.GATEWAY_VALIDATION,
                    Map.of("gateway_type", "invalid_enum"));
        }
        String url = req.baseUrl();
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new GatewayException(GatewayErrorCode.INVALID_BASE_URL);
        }
        if (req.modelRefreshStrategy() != null
                && ModelRefreshStrategy.SCHEDULED.getKey().equals(req.modelRefreshStrategy())
                && req.modelRefreshIntervalMin() == null) {
            throw GatewayException.fieldValidation(GatewayErrorCode.GATEWAY_VALIDATION,
                    Map.of("model_refresh_interval_min", "required_when_scheduled"));
        }
    }

    /** 同类型下 name 唯一（409201，EDGE-012），excludeId 用于更新时排除自身，排除已删除记录。 */
    private void ensureNameUnique(Integer gatewayType, String name, Long excludeId) {
        LambdaQueryWrapper<ExternalGatewayConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(ExternalGatewayConfig::getGatewayType, gatewayType)
                .eq(ExternalGatewayConfig::getName, name)
                .isNull(ExternalGatewayConfig::getDeletedAt);
        if (excludeId != null) {
            qw.ne(ExternalGatewayConfig::getId, excludeId);
        }
        if (mapper.selectCount(qw) > 0) {
            throw new GatewayException(GatewayErrorCode.GATEWAY_NAME_EXISTS);
        }
    }

    private GatewayConfigDto toDto(ExternalGatewayConfig e) {
        return new GatewayConfigDto(
                e.getId(),
                e.getGatewayType(),
                e.getName(),
                e.getProtocol(),
                e.getBaseUrl(),
                crypto.mask(e.getApiKeyEncrypted()),
                e.getDefaultModel(),
                fromJson(e.getModelList()),
                e.getModelRefreshStrategy(),
                e.getModelRefreshIntervalMin(),
                e.getModelsSyncedAt() == null ? null : e.getModelsSyncedAt().format(ISO),
                e.getConsecutiveFailures(),
                e.getEnabled(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().format(ISO),
                e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(ISO));
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalizeTs(String ts) {
        return ts == null ? "" : ts.replace("T", " ").trim();
    }
}

