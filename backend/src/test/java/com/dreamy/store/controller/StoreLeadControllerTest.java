package com.dreamy.store.controller;

import com.dreamy.controller.StoreLeadController;
import com.dreamy.domain.contact.service.ContactService;
import com.dreamy.domain.subscriber.service.NewsletterService;
import com.dreamy.error.MarketingException;
import com.dreamy.error.MarketingExceptionHandler;
import com.dreamy.i18n.MarketingMessageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StoreLeadController standalone MockMvc 测试（2026-07-18 退订端点）。
 * STUB_REASON: Web 切片边界，mock service 层隔离 DB。
 * STUB_SCOPE: service_io。
 */
class StoreLeadControllerTest {

    private NewsletterService newsletterService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        newsletterService = mock(NewsletterService.class);
        ContactService contactService = mock(ContactService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StoreLeadController(newsletterService, contactService))
                .setControllerAdvice(new MarketingExceptionHandler(new MarketingMessageResolver(),
                        new com.dreamy.i18n.SiteBuilderMessageResolver()))
                .build();
    }

    @Test
    @DisplayName("POST /api/store/newsletter/unsubscribe：成功 → 200 {unsubscribed:true}")
    void unsubscribeOk() throws Exception {
        mockMvc.perform(post("/api/store/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unsubscribed").value(true));
        verify(newsletterService).unsubscribe("valid-token");
    }

    @Test
    @DisplayName("POST /api/store/newsletter/unsubscribe：token 无效/过期/代际落后 → 422 + 422704 fields.token")
    void unsubscribeInvalidToken() throws Exception {
        doThrow(MarketingException.fieldValidation(Map.of("token", "invalid_or_expired")))
                .when(newsletterService).unsubscribe(anyString());
        mockMvc.perform(post("/api/store/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad-token\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422704))
                .andExpect(jsonPath("$.data.fields.token").value("invalid_or_expired"));
    }

    @Test
    @DisplayName("POST /api/store/newsletter：订阅仍恒 200 {subscribed:true}")
    void subscribeOk() throws Exception {
        mockMvc.perform(post("/api/store/newsletter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@dreamy.test\",\"source\":1,\"locale\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true));
        verify(newsletterService).subscribe("a@dreamy.test", 1, "en");
    }
}
