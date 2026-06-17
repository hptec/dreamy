package com.dreamy.domain.aitranslation.service;

import com.dreamy.domain.aitranslation.repository.AiTranslationLogMapper;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.domain.gateway.service.GatewayCryptoService;
import com.dreamy.domain.gateway.service.GatewayHttpClient;
import com.dreamy.domain.glossary.entity.AiTranslationGlossary;
import com.dreamy.domain.glossary.repository.AiTranslationGlossaryMapper;
import com.dreamy.dto.AiTranslationDtos.TranslateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * AI 翻译 system prompt 组装单元测试（决策6/14 / EDGE-024）。
 * 覆盖：领域前缀锁定、命中术语注入、自定义要求追加、术语上限 50 条截断。
 */
@ExtendWith(MockitoExtension.class)
class AiTranslationServicePromptTest {

    @Mock
    ExternalGatewayConfigMapper gatewayMapper;
    @Mock
    AiTranslationGlossaryMapper glossaryMapper;
    @Mock
    AiTranslationLogMapper logMapper;
    @Mock
    GatewayCryptoService crypto;
    @Mock
    GatewayHttpClient httpClient;

    AiTranslationService service;

    @BeforeEach
    void setUp() {
        service = new AiTranslationService(gatewayMapper, glossaryMapper, logMapper,
                crypto, httpClient, new ObjectMapper());
    }

    private TranslateRequest req(String sourceText, String custom, String targetLang) {
        return new TranslateRequest("en", targetLang, sourceText, custom, null, "product", "1");
    }

    @Test
    @DisplayName("TC-UNIT-AI-001: prompt 含婚纱礼服领域固定前缀")
    void promptHasBridalPrefix() {
        lenient().when(glossaryMapper.selectList(any())).thenReturn(List.of());
        String prompt = service.buildSystemPrompt(req("A long flowing gown", null, "es"));
        assertThat(prompt).contains("bridal e-commerce");
        assertThat(prompt).contains("Translate from en to es");
    }

    @Test
    @DisplayName("TC-UNIT-AI-002: 仅命中 source_text 的术语被注入（决策14）")
    void onlyHitTermsInjected() {
        AiTranslationGlossary hit = term("A-line", "línea A", "silhouette");
        AiTranslationGlossary miss = term("mermaid", "sirena", "silhouette");
        lenient().when(glossaryMapper.selectList(any())).thenReturn(List.of(hit, miss));
        String prompt = service.buildSystemPrompt(req("An A-line wedding dress", null, "es"));
        assertThat(prompt).contains("A-line -> línea A");
        assertThat(prompt).doesNotContain("mermaid");
    }

    @Test
    @DisplayName("TC-UNIT-AI-003: 自定义要求追加到 prompt（决策6）")
    void customRequirementAppended() {
        lenient().when(glossaryMapper.selectList(any())).thenReturn(List.of());
        String prompt = service.buildSystemPrompt(req("Elegant gown", "Keep tone formal", "fr"));
        assertThat(prompt).contains("Additional requirement: Keep tone formal");
    }

    @Test
    @DisplayName("TC-UNIT-AI-004: 命中术语超过 50 条按 category 优先级截断（EDGE-024）")
    void glossaryTruncatedTo50() {
        List<AiTranslationGlossary> many = new ArrayList<>();
        // 60 个命中术语，其中前缀均出现在 source_text 中
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            String t = "term" + i;
            String category = i < 55 ? "other" : "silhouette"; // 5 个高优先级
            many.add(term(t, "tr" + i, category));
            source.append(t).append(' ');
        }
        lenient().when(glossaryMapper.selectList(any())).thenReturn(many);
        String prompt = service.buildSystemPrompt(req(source.toString(), null, "es"));
        long injected = prompt.lines().filter(l -> l.startsWith("- term")).count();
        assertThat(injected).isEqualTo(AiTranslationService.MAX_GLOSSARY_TERMS);
        // 高优先级 silhouette 术语应被保留
        assertThat(prompt).contains("term55");
    }

    private AiTranslationGlossary term(String en, String es, String category) {
        AiTranslationGlossary g = new AiTranslationGlossary();
        g.setTermEn(en);
        g.setTermEs(es);
        g.setCategory(category);
        g.setEnabled(true);
        return g;
    }
}
