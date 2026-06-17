package com.dreamy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 外部网关配置域 DTO 集（gateway-api.openapi.yml）。
 * L2 TRACE: i18n-backend-api-detail.md §1 / FUNC-004~007/021 / EDGE-006/007/010/012/014/023。
 * 命名遵循 shared-contracts：响应/请求字段 snake_case，DTO 字段 camelCase（Jackson 全局策略）。
 * 安全：响应 DTO 仅含掩码 api_key_masked，从不回传密文或明文（EDGE-010）。
 */
public final class GatewayDtos {

    private GatewayDtos() {
    }

    /**
     * 网关配置响应（GatewayConfig）。api_key 掩码展示（sk-****1234），密文不出域。
     */
    public record GatewayConfigDto(
            Long id,
            Integer gatewayType,
            String name,
            Integer protocol,
            String baseUrl,
            String apiKeyMasked,
            String defaultModel,
            List<String> modelList,
            Integer modelRefreshStrategy,
            Integer modelRefreshIntervalMin,
            String modelsSyncedAt,
            Integer consecutiveFailures,
            Boolean enabled,
            String createdAt,
            String updatedAt) {
    }

    /**
     * 创建/更新请求（GatewayConfigUpsert）。
     * 注意 apiKey 为明文入参（ISS-008，1~512），经 encryptApiKey 加密后落 api_key_encrypted 列；
     * 更新场景若传掩码（sk-****xxxx）则保持原密文不变。
     * 字段级校验对应 EDGE-006(api_key 非空)/EDGE-007(base_url 协议)。
     */
    public record GatewayConfigUpsert(
            @NotNull Integer gatewayType,
            @NotBlank @Size(max = 64) String name,
            @NotNull Integer protocol,
            @NotBlank @Size(max = 255) String baseUrl,
            @NotBlank @Size(max = 512) String apiKey,
            @Size(max = 128) String defaultModel,
            Integer modelRefreshStrategy,
            @Min(5) @Max(1440) Integer modelRefreshIntervalMin,
            @NotNull Boolean enabled,
            /** 乐观锁：更新时前端回传 DB updated_at，不一致 → 409201（EDGE-012）。创建时忽略。 */
            String updatedAt) {
    }

    /**
     * 测试连接结果（GatewayTestResult）。成功失败均 HTTP 200，通过 reachable 区分（§1.7）。
     * error_code ∈ {502201 不可达 / 502202 鉴权失败 / 504201 超时}，在 200 响应体内返回。
     */
    public record GatewayTestResult(
            Boolean reachable,
            Integer availableModelsCount,
            Integer errorCode,
            String errorMessage,
            Long latencyMs) {
    }
}
