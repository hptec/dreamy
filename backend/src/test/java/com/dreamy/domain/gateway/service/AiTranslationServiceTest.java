package com.dreamy.domain.gateway.service;

import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.dto.AiTranslationDtos.TranslateRequest;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTranslationServiceTest {

    @Mock ExternalGatewayConfigMapper mapper;
    @Mock GatewayCryptoService crypto;
    @Mock GatewayHttpClient httpClient;

    private AiTranslationService service;

    @BeforeEach
    void setUp() {
        service = new AiTranslationService(mapper, crypto, httpClient, new ObjectMapper());
    }

    @Test
    @DisplayName("显式模型存在于 model_list 时允许翻译")
    void explicitModelMustExistInModelList() {
        stubGateway("[\"model-a\",\"model-b\"]");
        when(crypto.decrypt("encrypted-key")).thenReturn("plain-key");
        when(httpClient.chatCompletion(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("response");
        when(httpClient.parseChatContent("response")).thenReturn("Hola");

        var result = service.translate(request("model-b"));

        assertThat(result.model()).isEqualTo("model-b");
        assertThat(result.translatedText()).isEqualTo("Hola");
    }

    @ParameterizedTest(name = "model_list={0} 时拒绝显式模型")
    @NullSource
    @ValueSource(strings = {"", "null", "not-json", "{}"})
    @DisplayName("model_list 为空或损坏时拒绝显式模型")
    void emptyOrMalformedModelListRejectsExplicitModel(String modelList) {
        stubGateway(modelList);

        assertInvalidModel();
    }

    @Test
    @DisplayName("显式模型不在 model_list 时返回 400302")
    void unknownExplicitModelIsRejected() {
        stubGateway("[\"model-a\"]");

        assertInvalidModel();
    }

    @ParameterizedTest(name = "default_model={0} 时拒绝省略请求模型")
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("请求未指定模型且网关默认模型为空时返回 400302")
    void missingDefaultModelIsRejected(String defaultModel) {
        stubGateway("[\"model-a\"]", defaultModel);

        assertThatThrownBy(() -> service.translate(request(null)))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.INVALID_MODEL));
        verifyNoInteractions(crypto, httpClient);
    }

    @Test
    @DisplayName("服务直调空原文也统一返回 422201 字段错误")
    void blankSourceUsesReachableValidationCode() {
        assertThatThrownBy(() -> service.translate(new TranslateRequest("en", "es", " ", null, null)))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> {
                    GatewayException gateway = (GatewayException) ex;
                    assertThat(gateway.getErrorCode()).isEqualTo(GatewayErrorCode.GATEWAY_VALIDATION);
                    assertThat(gateway.getDetails())
                            .containsEntry("fields", Map.of("source_text", "required"));
                });

        verifyNoInteractions(mapper, crypto, httpClient);
    }

    private void assertInvalidModel() {
        assertThatThrownBy(() -> service.translate(request("model-b")))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).getErrorCode())
                        .isEqualTo(GatewayErrorCode.INVALID_MODEL));
        verifyNoInteractions(crypto, httpClient);
    }

    private void stubGateway(String modelList) {
        stubGateway(modelList, "model-a");
    }

    private void stubGateway(String modelList, String defaultModel) {
        ExternalGatewayConfig gateway = new ExternalGatewayConfig();
        gateway.setId(1L);
        gateway.setGatewayType(1);
        gateway.setEnabled(true);
        gateway.setBaseUrl("https://gateway.test/v1");
        gateway.setApiKeyEncrypted("encrypted-key");
        gateway.setDefaultModel(defaultModel);
        gateway.setModelList(modelList);
        when(mapper.selectOne(any())).thenReturn(gateway);
    }

    private TranslateRequest request(String model) {
        return new TranslateRequest("en", "es", "Hello", null, model);
    }
}
