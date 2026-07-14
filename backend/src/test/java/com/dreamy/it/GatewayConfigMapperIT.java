package com.dreamy.it;

import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigMapperIT extends AbstractIT {

    @Autowired ExternalGatewayConfigMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearGateways() {
        jdbcTemplate.update("DELETE FROM external_gateway_config");
    }

    @Test
    @DisplayName("真 MySQL：失败计数原子递增，第三次 scheduled 失败降级并推进 version")
    void failureIncrementAndThirdAttemptDegradationAreAtomic() {
        Long id = insertGateway("Atomic failures", 2, true, 0, 0);

        assertThat(mapper.incrementSyncFailure(id, 0, true)).isEqualTo(1);
        assertThat(mapper.incrementSyncFailure(id, 1, true)).isEqualTo(1);
        ExternalGatewayConfig twiceFailed = mapper.selectById(id);
        assertThat(twiceFailed.getConsecutiveFailures()).isEqualTo(2);
        assertThat(twiceFailed.getModelRefreshStrategy()).isEqualTo(2);
        assertThat(twiceFailed.getEnabled()).isTrue();
        assertThat(twiceFailed.getVersion()).isEqualTo(2);

        assertThat(mapper.incrementSyncFailure(id, 2, true)).isEqualTo(1);
        ExternalGatewayConfig degraded = mapper.selectById(id);
        assertThat(degraded.getConsecutiveFailures()).isEqualTo(3);
        assertThat(degraded.getModelRefreshStrategy()).isEqualTo(1);
        assertThat(degraded.getEnabled()).isFalse();
        assertThat(degraded.getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("真 MySQL：手动同步失败只计数，不自动降级 scheduled 配置")
    void manualFailureDoesNotAutoDegradeScheduledConfig() {
        Long id = insertGateway("Manual failure", 2, true, 2, 0);

        assertThat(mapper.incrementSyncFailure(id, 0, false)).isEqualTo(1);

        ExternalGatewayConfig failed = mapper.selectById(id);
        assertThat(failed.getConsecutiveFailures()).isEqualTo(3);
        assertThat(failed.getModelRefreshStrategy()).isEqualTo(2);
        assertThat(failed.getEnabled()).isTrue();
        assertThat(failed.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("真 MySQL：配置更新按 version CAS，模型同步写入也推进同一版本")
    void configCasAndSyncUpdatesShareVersion() {
        Long id = insertGateway("CAS", 1, true, 0, 4);
        ExternalGatewayConfig patch = mapper.selectById(id);
        patch.setName("CAS updated");

        assertThat(mapper.updateConfig(id, 3, patch)).isZero();
        assertThat(mapper.updateConfig(id, 4, patch)).isEqualTo(1);
        assertThat(mapper.updateSyncSuccess(id, 5, "[\"gpt-4o\"]")).isEqualTo(1);

        ExternalGatewayConfig updated = mapper.selectById(id);
        assertThat(updated.getName()).isEqualTo("CAS updated");
        assertThat(updated.getVersion()).isEqualTo(6);
        assertThat(updated.getModelList()).isEqualTo("[\"gpt-4o\"]");
        assertThat(updated.getConsecutiveFailures()).isZero();
        assertThat(updated.getModelsSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("真 MySQL：迟到的同步成功和失败均无法写入已编辑的新配置")
    void staleSyncResultsCannotOverwriteEditedConfig() {
        Long id = insertGateway("Stale sync", 2, true, 2, 4);
        ExternalGatewayConfig patch = mapper.selectById(id);
        patch.setBaseUrl("https://new-gateway.example.com");
        patch.setApiKeyEncrypted("new-ciphertext");
        assertThat(mapper.updateConfig(id, 4, patch)).isEqualTo(1);

        assertThat(mapper.updateSyncSuccess(id, 4, "[\"stale-model\"]")).isZero();
        assertThat(mapper.incrementSyncFailure(id, 4, true)).isZero();

        ExternalGatewayConfig current = mapper.selectById(id);
        assertThat(current.getBaseUrl()).isEqualTo("https://new-gateway.example.com");
        assertThat(current.getApiKeyEncrypted()).isEqualTo("new-ciphertext");
        assertThat(current.getModelList()).isNull();
        assertThat(current.getConsecutiveFailures()).isEqualTo(2);
        assertThat(current.getModelRefreshStrategy()).isEqualTo(2);
        assertThat(current.getEnabled()).isTrue();
        assertThat(current.getVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("真 MySQL：密文列和两个索引符合发布契约")
    void schemaSupportsMaxEncryptedKeyAndAuthoritativeIndexes() {
        Integer maxLength = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'external_gateway_config' "
                        + "AND COLUMN_NAME = 'api_key_encrypted'",
                Integer.class);
        assertThat(maxLength).isGreaterThanOrEqualTo(4096);

        Map<String, Integer> indexes = jdbcTemplate.query(
                "SELECT INDEX_NAME, COUNT(*) column_count FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'external_gateway_config' "
                        + "AND INDEX_NAME IN ('uk_type_name', 'idx_type_enabled') GROUP BY INDEX_NAME",
                rs -> {
                    Map<String, Integer> result = new java.util.HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("INDEX_NAME"), rs.getInt("column_count"));
                    }
                    return result;
                });
        assertThat(indexes).containsEntry("uk_type_name", 2).containsEntry("idx_type_enabled", 2);
    }

    private Long insertGateway(String name, int strategy, boolean enabled, int failures, int version) {
        jdbcTemplate.update("INSERT INTO external_gateway_config "
                        + "(gateway_type, name, protocol, base_url, api_key_encrypted, model_refresh_strategy, "
                        + "model_refresh_interval_min, consecutive_failures, enabled, version) "
                        + "VALUES (1, ?, 1, 'https://gateway.example.com', 'ciphertext', ?, 5, ?, ?, ?)",
                name, strategy, failures, enabled, version);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM external_gateway_config WHERE gateway_type = 1 AND name = ?", Long.class, name);
    }
}
