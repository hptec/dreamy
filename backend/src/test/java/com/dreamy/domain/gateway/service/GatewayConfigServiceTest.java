package com.dreamy.domain.gateway.service;

import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreamy.dto.GatewayDtos.GatewayConfigUpsert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayConfigServiceTest {

    @Mock ExternalGatewayConfigMapper mapper;
    @Mock GatewayCryptoService crypto;
    @Mock GatewayHttpClient httpClient;

    private GatewayConfigService service;

    @BeforeEach
    void setUp() {
        service = new GatewayConfigService(mapper, crypto, httpClient, new ObjectMapper());
    }

    @Test
    @DisplayName("网关配置无历史追溯需求，翻译日志退役后直接物理删除")
    void deleteUsesPhysicalDeleteWithoutRetiredLogGuard() {
        ExternalGatewayConfig config = new ExternalGatewayConfig();
        config.setId(8L);
        when(mapper.selectById(8L)).thenReturn(config);

        service.delete(8L);

        verify(mapper).deleteById(8L);
        verify(mapper, never()).updateById(any(ExternalGatewayConfig.class));
    }

    @Test
    @DisplayName("删除不存在网关配置返回 404201，不执行删除")
    void deleteMissingConfigDoesNotMutate() {
        when(mapper.selectById(8L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_NOT_FOUND));

        verify(mapper, never()).deleteById(any());
        verify(mapper, never()).updateById(any(ExternalGatewayConfig.class));
    }

    @Test
    @DisplayName("更新保留并返回 extra_config，掩码 API Key 不重写密文")
    void updatePersistsExtraConfigAndReturnsRuntimeShape() {
        ExternalGatewayConfig config = new ExternalGatewayConfig();
        config.setId(8L);
        config.setGatewayType(2);
        config.setName("Logistics");
        config.setProtocol(1);
        config.setBaseUrl("https://gateway.example.com");
        config.setApiKeyEncrypted("ciphertext");
        config.setModelList("[]");
        config.setModelRefreshStrategy(1);
        config.setConsecutiveFailures(0);
        config.setEnabled(true);
        config.setVersion(4);
        ExternalGatewayConfig saved = gateway(8L, 5);
        saved.setExtraConfig("{\"region\":\"us-east\"}");
        when(mapper.selectById(8L)).thenReturn(config, saved);
        when(crypto.isMasked("sk-****1234")).thenReturn(true);
        when(mapper.updateConfig(8L, 4, config)).thenReturn(1);

        GatewayConfigUpsert req = new GatewayConfigUpsert(2, "Logistics", 1,
                "https://gateway.example.com", "sk-****1234", null, 1, null, true,
                Map.of("region", "us-east"), 4);
        var result = service.update(8L, req);

        assertThat(config.getApiKeyEncrypted()).isEqualTo("ciphertext");
        assertThat(config.getExtraConfig()).contains("\"region\":\"us-east\"");
        assertThat(result.extraConfig()).containsEntry("region", "us-east");
        assertThat(result.version()).isEqualTo(5);
        verify(crypto, never()).encrypt(any());
        verify(mapper).updateConfig(8L, 4, config);
    }

    @Test
    @DisplayName("创建拒绝契约外的协议和模型刷新策略")
    void createRejectsInvalidProtocolAndRefreshStrategy() {
        GatewayConfigUpsert invalidProtocol = request(99, 1);
        GatewayConfigUpsert invalidStrategy = request(1, 99);

        assertInvalidEnum(() -> service.create(invalidProtocol), "protocol");
        assertInvalidEnum(() -> service.create(invalidStrategy), "model_refresh_strategy");

        verify(mapper, never()).insert(any(ExternalGatewayConfig.class));
    }

    @Test
    @DisplayName("更新拒绝契约外的协议和模型刷新策略")
    void updateRejectsInvalidProtocolAndRefreshStrategy() {
        ExternalGatewayConfig config = new ExternalGatewayConfig();
        config.setId(8L);
        config.setVersion(1);
        when(mapper.selectById(8L)).thenReturn(config);

        assertInvalidEnum(() -> service.update(8L, request(99, 1)), "protocol");
        assertInvalidEnum(() -> service.update(8L, request(1, 99)), "model_refresh_strategy");

        verify(mapper, never()).updateConfig(any(), any(), any(ExternalGatewayConfig.class));
    }

    @Test
    @DisplayName("更新 version 缺失或旧 version CAS 失败均拒绝覆盖")
    void updateRequiresVersionAndMapsStaleWrite() {
        ExternalGatewayConfig config = gateway(8L, 7);
        when(mapper.selectById(8L)).thenReturn(config);
        when(crypto.isMasked("sk-****1234")).thenReturn(true);

        GatewayConfigUpsert missingVersion = upsert(2, "Logistics", "sk-****1234", 1, null);
        assertThatThrownBy(() -> service.update(8L, missingVersion))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_VALIDATION));

        GatewayConfigUpsert stale = upsert(2, "Logistics", "sk-****1234", 1, 6);
        when(mapper.updateConfig(8L, 6, config)).thenReturn(0);
        assertThatThrownBy(() -> service.update(8L, stale))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_EDIT_CONFLICT));
    }

    @Test
    @DisplayName("数据库唯一索引竞态在创建和更新均映射 GATEWAY_NAME_EXISTS")
    void duplicateKeyMapsToNameExists() {
        GatewayConfigUpsert create = upsert(2, "Logistics", "plain-key", 1, null);
        when(crypto.isMasked("plain-key")).thenReturn(false);
        when(crypto.encrypt("plain-key")).thenReturn("ciphertext");
        when(mapper.insert(any(ExternalGatewayConfig.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for uk_type_name"));
        assertNameExists(() -> service.create(create));

        ExternalGatewayConfig config = gateway(8L, 2);
        when(mapper.selectById(8L)).thenReturn(config);
        when(crypto.isMasked("sk-****1234")).thenReturn(true);
        when(mapper.updateConfig(8L, 2, config))
                .thenThrow(new DuplicateKeyException("Duplicate entry for uk_type_name"));
        assertNameExists(() -> service.update(8L,
                upsert(2, "Logistics", "sk-****1234", 1, 2)));
    }

    @Test
    @DisplayName("syncModels 异常返回前执行原子失败计数，第三次降级由 Mapper 单语句完成")
    void syncFailurePersistsBeforeExceptionAndReadsDegradedState() {
        ExternalGatewayConfig initial = gateway(8L, 9);
        initial.setGatewayType(1);
        initial.setModelRefreshStrategy(2);
        initial.setConsecutiveFailures(2);
        when(crypto.decrypt("ciphertext")).thenReturn("plain-key");
        when(httpClient.listModels(any(), any())).thenThrow(callFailure());
        when(mapper.incrementSyncFailure(8L, 9, true)).thenReturn(1);
        ExternalGatewayConfig degraded = gateway(8L, 10);
        degraded.setConsecutiveFailures(3);
        degraded.setModelRefreshStrategy(1);
        degraded.setEnabled(false);
        when(mapper.selectById(8L)).thenReturn(initial, degraded);

        assertThatThrownBy(() -> service.syncModelsScheduled(8L))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_UNREACHABLE));
        verify(mapper).incrementSyncFailure(8L, 9, true);
        verify(mapper, times(2)).selectById(8L);
    }

    @Test
    @DisplayName("syncModels 成功用原子 SQL 写模型快照、清零计数并返回最新 version")
    void syncSuccessUsesAtomicMapperUpdate() {
        ExternalGatewayConfig initial = gateway(8L, 9);
        initial.setGatewayType(1);
        ExternalGatewayConfig updated = gateway(8L, 10);
        updated.setGatewayType(1);
        updated.setModelList("[\"gpt-4o\"]");
        when(mapper.selectById(8L)).thenReturn(initial, updated);
        when(crypto.decrypt("ciphertext")).thenReturn("plain-key");
        when(httpClient.listModels(any(), any())).thenReturn(List.of("gpt-4o"));
        when(mapper.updateSyncSuccess(8L, 9, "[\"gpt-4o\"]")).thenReturn(1);

        assertThat(service.syncModels(8L).version()).isEqualTo(10);
        verify(mapper).updateSyncSuccess(8L, 9, "[\"gpt-4o\"]");
    }

    @Test
    @DisplayName("syncModels 成功响应落库前 version 已变化时返回编辑冲突且不读取伪结果")
    void syncSuccessRejectsStaleGatewayVersion() {
        ExternalGatewayConfig initial = gateway(8L, 9);
        initial.setGatewayType(1);
        when(mapper.selectById(8L)).thenReturn(initial);
        when(crypto.decrypt("ciphertext")).thenReturn("plain-key");
        when(httpClient.listModels(any(), any())).thenReturn(List.of("stale-model"));
        when(mapper.updateSyncSuccess(8L, 9, "[\"stale-model\"]")).thenReturn(0);

        assertThatThrownBy(() -> service.syncModels(8L))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_EDIT_CONFLICT));
        verify(mapper).updateSyncSuccess(8L, 9, "[\"stale-model\"]");
        verify(mapper).selectById(8L);
    }

    @Test
    @DisplayName("syncModels 失败响应落库前 version 已变化时忽略旧失败计数")
    void syncFailureIgnoresStaleGatewayVersion() {
        ExternalGatewayConfig initial = gateway(8L, 9);
        initial.setGatewayType(1);
        when(mapper.selectById(8L)).thenReturn(initial);
        when(crypto.decrypt("ciphertext")).thenReturn("plain-key");
        when(httpClient.listModels(any(), any())).thenThrow(callFailure());
        when(mapper.incrementSyncFailure(8L, 9, false)).thenReturn(0);

        assertThatThrownBy(() -> service.syncModels(8L))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_UNREACHABLE));
        verify(mapper).incrementSyncFailure(8L, 9, false);
        verify(mapper).selectById(8L);
    }

    private static GatewayConfigUpsert request(Integer protocol, Integer strategy) {
        return new GatewayConfigUpsert(1, "AI Gateway", protocol,
                "https://gateway.example.com", "sk-test", null, strategy, null, true,
                null, 1);
    }

    private static GatewayConfigUpsert upsert(Integer type, String name, String apiKey,
                                               Integer strategy, Integer version) {
        return new GatewayConfigUpsert(type, name, 1, "https://gateway.example.com", apiKey,
                null, strategy, null, true, null, version);
    }

    private static ExternalGatewayConfig gateway(Long id, Integer version) {
        ExternalGatewayConfig config = new ExternalGatewayConfig();
        config.setId(id);
        config.setGatewayType(2);
        config.setName("Logistics");
        config.setProtocol(1);
        config.setBaseUrl("https://gateway.example.com");
        config.setApiKeyEncrypted("ciphertext");
        config.setModelList("[]");
        config.setModelRefreshStrategy(1);
        config.setConsecutiveFailures(0);
        config.setEnabled(true);
        config.setVersion(version);
        return config;
    }

    private static GatewayHttpClient.GatewayCallException callFailure() {
        return new GatewayHttpClient.GatewayCallException(
                GatewayHttpClient.FailureKind.UNREACHABLE, null, "unreachable", null);
    }

    private static void assertNameExists(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.GATEWAY_NAME_EXISTS));
    }

    @SuppressWarnings("unchecked")
    private static void assertInvalidEnum(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
                                          String field) {
        assertThatThrownBy(action)
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> {
                    GatewayException gatewayException = (GatewayException) ex;
                    assertThat(gatewayException.getErrorCode()).isEqualTo(GatewayErrorCode.GATEWAY_VALIDATION);
                    assertThat((Map<String, String>) gatewayException.getDetails().get("fields"))
                            .containsEntry(field, "invalid_enum");
                });
    }
}
